package com.yuyuyu.deepseek.net

import com.yuyuyu.deepseek.data.AuthStyle
import com.yuyuyu.deepseek.data.Msg
import com.yuyuyu.deepseek.data.ProviderApi
import com.yuyuyu.deepseek.data.ProviderPreset
import com.yuyuyu.deepseek.data.Settings
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

object Client {

    private val JSON = "application/json".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 请求体 messages 清洗（2026-11-22 修 HTTP 400）。
     *
     * 服务端只接受「user/assistant 严格交替、且第一条必须是 user」的历史，否则返回
     *   HTTP 400 {"error":{"message":"an assistant message must follow a user message",...}}
     * （已用 curl 对同一 history 复现，见 _work/sys400/final_verify.py）
     *
     * 旧实现是 `if (m.text.isBlank()) continue`：会话里「只发图片/只发附件」的那一条 user
     * 正文是空的，被整个跳过之后，它前后的两条 assistant 就贴在一起；如果被跳过的是会话的
     * 第一条 user，请求体就直接以 assistant 开头，这一整轮对话从此每次都 400。
     * （本机 sessions.json 里 91 条历史中有 7 条正文为空的 user，属于真实会出现的形态。）
     *
     * 清洗规则：
     *  1. 只保留 user / assistant；
     *  2. error 消息排除出请求体（UI 照常显示错误气泡）；
     *  3. 正文为空的 assistant 丢掉（失败/「重新生成」留下的空候选；只带 reasoning 的也不发，
     *     reasoning_content 不回传）。当前正在生成的那条本来就不在传给 chat 的 history 里；
     *  4. 正文为空的 user 保留成 content:""，这样历史里的「图片轮」不会破坏交替；
     *  5. 连续同类合并（user 用 \n 拼；assistant 用 \n\n 拼），保证严格交替；
     *  6. 开头不是 user 的丢掉，结尾不是 user 的丢掉
     *     （否则服务端会报 the last message is an assistant message）；
     *  7. assistant 回传只发纯文本，不带 server_tool_calls / tool_calls。
     *     实测（2026-11-22, model=deepseek-flash）：带 server_tool_calls 回传也是 200，
     *     剥离后同样 200；选剥离 —— 不把上一轮的检索步骤重放给服务端，更省 token，
     *     也避开「带工具的 assistant 后面必须跟 tool 结果」这类校验（部分 OpenAI 兼容服务端会拒）。
     *  8. 最后一条正文为空时补占位文案：服务端要求最后一条内容非空
     *     （`messages[N]: all messages must have non-empty content`，实测只查最后一条；历史中间的空 user 接受）。
     */
    private fun cleanHistory(history: List<Msg>): List<Msg> {
        val out = ArrayList<Msg>(history.size)
        for (m in history) {
            if (m.role != "user" && m.role != "assistant") continue
            if (m.role == "assistant" && (m.error || m.text.isBlank())) continue
            val last = out.lastOrNull()
            if (last != null && last.role == m.role) {
                // 连续同类：合并成一条，避免服务端校验失败
                val merged = if (m.role == "user") {
                    listOf(last.text, m.text).filter { it.isNotBlank() }.joinToString("\n")
                } else {
                    listOf(last.text, m.text).filter { it.isNotBlank() }.joinToString("\n\n")
                }
                out[out.lastIndex] = last.copy(text = merged)
                continue
            }
            out.add(if (m.role == "assistant") m.copy(serverToolCalls = "") else m)
        }
        while (out.isNotEmpty() && out.first().role != "user") out.removeAt(0)
        while (out.isNotEmpty() && out.last().role != "user") out.removeAt(out.lastIndex)
        return out
    }

    /**
     * 当前请求体：按「接口类型」分发。
     *   chat      → /chat/completions 的 {model, messages, stream:true, ...}（与改造前逐字段一致）
     *   responses → /responses 的 {model, input, instructions, stream:true}
     * ui/ContextSheet.kt 的「当前请求体」预览也走这里，所以选 Responses 时看到的就是真实请求体。
     */
    fun payloadJson(
        settings: Settings,
        history: List<Msg>,
        imageDataUrls: List<String> = emptyList(),
    ): JSONObject = if (settings.apiStyle(settings.providerId) == "responses") {
        responsesPayloadJson(settings, history, imageDataUrls)
    } else {
        chatPayloadJson(settings, history, imageDataUrls)
    }

    private fun chatPayloadJson(
        settings: Settings,
        history: List<Msg>,
        imageDataUrls: List<String> = emptyList(),
    ): JSONObject {
        val msgs = JSONArray()
        val cleaned = cleanHistory(history)
        // 图片只挂在最后一条 user 上（也就是这次新发的那条）
        val lastUserIndex = cleaned.indexOfLast { it.role == "user" }
        cleaned.forEachIndexed { i, m ->
            if (i == lastUserIndex && imageDataUrls.isNotEmpty()) {
                val parts = JSONArray()
                if (m.text.isNotBlank()) {
                    parts.put(JSONObject().put("type", "text").put("text", m.text))
                }
                imageDataUrls.forEach { url ->
                    parts.put(
                        JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", url))
                    )
                }
                msgs.put(JSONObject().put("role", m.role).put("content", parts))
            } else {
                // 服务端还要求最后一条消息内容非空：
                //    messages[2]: all messages must have non-empty content
                // 只发文件（非图片）附件、没打字时正文是空的，这里兜底成占位文案，避免 400。
                // （历史中间的空 user 服务端是接受的，实测 200，不用处理。）
                val text = if (i == cleaned.lastIndex && m.text.isBlank()) "（附件）" else m.text
                msgs.put(JSONObject().put("role", m.role).put("content", text))
            }
        }
        // 当前 provider 的预设：决定端点、鉴权方式和请求体能力位。
        // 注意：providerId == "deepseek" 的三处专属分支保持原样，DeepSeek 是回归第一优先级。
        val preset = settings.preset
        val isDeepSeek = settings.providerId == "deepseek"
        val effectiveModel = modelFor(settings, imageDataUrls)
        val payload = JSONObject()
            .put("model", effectiveModel)
            .put("messages", msgs)
            .put("stream", true)
        // 能力位：DeepSeek 三个都是 true，所以下面的输出与改造前逐字段一致；
        // 不支持的兼容网关（部分厂商不认 stream_options / reasoning_effort）会 400，按能力位跳过。
        if (preset.supportsTemperature) payload.put("temperature", settings.temperature.toDouble())
        if (preset.supportsTopP) payload.put("top_p", settings.topP.toDouble())
        if (preset.supportsStreamUsage) {
            payload.put("stream_options", JSONObject().put("include_usage", settings.includeUsage))
        }
    if (settings.maxTokens > 0) payload.put("max_tokens", settings.maxTokens)
    if (settings.stop.isNotBlank()) payload.put("stop", settings.stop)
    if (settings.jsonMode) payload.put("response_format", JSONObject().put("type", "json_object"))
    // user_id / title / safety_check / thinking / tools 都是 DeepSeek「全家桶应用 API」的字段，
    // 别的 provider 送了可能 400，所以只对 deepseek 发（DeepSeek 下输出与改造前完全一致）。
    if (isDeepSeek && settings.userId.isNotBlank()) payload.put("user_id", settings.userId)
    if (isDeepSeek && settings.titleEnabled) payload.put("title", JSONObject().put("enabled", true))
    if (isDeepSeek && settings.safety) payload.put("safety_check", JSONObject().put("level", "high"))
    if (preset.supportsReasoningEffort && settings.reasoningEffort != "high" && !settings.thinking) {
        payload.put("reasoning_effort", settings.reasoningEffort)
    }
    if (settings.systemPrompt.isNotBlank()) {
        // system 消息放最前面：有的 OpenAI 兼容服务端不允许多条 system
        msgs.put(0, JSONObject().put("role", "system").put("content", settings.systemPrompt.trim()))
    }
        if (isDeepSeek) {
            payload.put("thinking", JSONObject().put("type", if (settings.thinking) "enabled" else "disabled"))
        }
        if (isDeepSeek) {
            // 官方「全家桶应用 API」的内置联网工具（实测 2026-11-22，model=deepseek-flash）：
            //   tools:[{"type":"deepseek_web_search"},{"type":"deepseek_read_url"}] 两个名称都被接受（HTTP 200）。
            //   服务端自己检索，返回的 delta/message 里带 annotations（url_citation 引用）和
            //   server_tool_calls（search/open_page 步骤 + sources/site_name/site_icon）。
            //   实测思考/非思考模式都支持；按次计费（搜索/抓取 各 5 元/千次）。
            // 「智能搜索」关闭时不带任何 tools —— 这就是开关的全部语义。
            if (settings.search) {
                payload.put(
                    "tools",
                    JSONArray()
                        .put(JSONObject().put("type", "deepseek_web_search"))
                        .put(JSONObject().put("type", "deepseek_read_url")),
                )
            }
            // 对外服务需开启安全校验；本地自用也开着更稳
            payload.put("safety_check", JSONObject().put("level", "high"))
        }
        return payload
    }

    /** 当前 provider 下实际发给服务端的模型名（chat 与 responses 共用，避免两条路径选模型漂移） */
    private fun modelFor(settings: Settings, imageDataUrls: List<String>): String {
        val preset = settings.preset
        val isDeepSeek = settings.providerId == "deepseek"
        val model = settings.model.ifBlank {
            if (isDeepSeek) "deepseek-flash" else preset.fallbackModels.firstOrNull().orEmpty()
        }
        return if (isDeepSeek &&
            (settings.search || imageDataUrls.isNotEmpty() ||
                model in setOf("deepseek-chat", "deepseek-reasoner", "deepseek-v4-flash-vision-exp"))
        ) "deepseek-flash" else model
    }

    /**
     * Responses API（/v1/responses）请求体。只发官方受支持的字段，避免第三方中转站 400：
     *   {"model":..., "input":[{"role":"user","content":[{"type":"input_text","text":"..."}]}],
     *    "stream":true, "instructions":"<系统提示词>"}
     *
     * 与 /chat/completions 的差异：
     *  - 历史放在 `input` 数组里，不是 `messages`；assistant 历史的内容块类型是 output_text，
     *    user 是 input_text（官方 Responses 规范）；
     *  - 系统提示词走顶层 `instructions`（不进 input 数组），没有 system 消息角色；
     *  - 图片挂在最后一条 user 上：{"type":"input_image","image_url":"data:image/...;base64,..."}；
     *  - 输出上限字段叫 max_output_tokens（chat 是 max_tokens）；
     *  - thinking / tools / stream_options / stop / reasoning_effort / user_id / title /
     *    safety_check / response_format 这些「全家桶应用 API」或 chat 专属字段一律不发。
     *    温度/top_p 只在预设声明支持时发（Responses 官方支持这两个字段）。
     */
    private fun responsesPayloadJson(
        settings: Settings,
        history: List<Msg>,
        imageDataUrls: List<String> = emptyList(),
    ): JSONObject {
        val cleaned = cleanHistory(history)
        val lastUserIndex = cleaned.indexOfLast { it.role == "user" }
        val input = JSONArray()
        cleaned.forEachIndexed { i, m ->
            val parts = JSONArray()
            // Responses 对空 content 数组不友好，空正文兜底成占位文案（chat 路径只兜底最后一条）
            val text = m.text.ifBlank { "（附件）" }
            parts.put(
                JSONObject()
                    .put("type", if (m.role == "assistant") "output_text" else "input_text")
                    .put("text", text),
            )
            if (i == lastUserIndex) {
                imageDataUrls.forEach { url ->
                    parts.put(JSONObject().put("type", "input_image").put("image_url", url))
                }
            }
            input.put(JSONObject().put("role", m.role).put("content", parts))
        }
        val preset = settings.preset
        val payload = JSONObject()
            .put("model", modelFor(settings, imageDataUrls))
            .put("input", input)
            .put("stream", true)
        if (settings.systemPrompt.isNotBlank()) {
            payload.put("instructions", settings.systemPrompt.trim())
        }
        // 只发 Responses 官方字段；thinking/tools 等 chat 专属字段一律不带，避免 400
        if (preset.supportsTemperature) payload.put("temperature", settings.temperature.toDouble())
        if (preset.supportsTopP) payload.put("top_p", settings.topP.toDouble())
        if (settings.maxTokens > 0) payload.put("max_output_tokens", settings.maxTokens)
        return payload
    }

    /**
     * Responses 端点：{effectiveBaseUrl}/responses。
     * baseUrl 可能已含 /v1（openai 预设是 https://api.openai.com/v1）→ https://api.openai.com/v1/responses；
     * 用户把完整 .../chat/completions 粘进来时 ProviderApi.normalizeBaseUrl 会先还原成根地址；
     * 已经直接填了 .../responses 的原样使用。
     */
    fun responsesUrl(preset: ProviderPreset, baseUrl: String, apiKey: String): String {
        val base = baseUrl.trim()
        val lower = base.lowercase(Locale.US)
        val url = if (lower.endsWith("/responses")) base else ProviderApi.join(base, "/responses")
        return if (preset.auth == AuthStyle.QueryKey) ProviderApi.appendQueryKey(url, apiKey) else url
    }

    fun chat(
        settings: Settings,
        history: List<Msg>,
        onDelta: (content: String, reasoning: String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
        onSteps: (List<com.yuyuyu.deepseek.data.Step>, String, List<com.yuyuyu.deepseek.data.Source>) -> Unit = { _, _, _ -> },
        imageDataUrls: List<String> = emptyList(),
    ): Call {
        val payload = payloadJson(settings, history, imageDataUrls)
        val preset = settings.preset
        // 接口类型：chat（默认）= /chat/completions，responses = /responses。请求体已由 payloadJson 按同
        // 一个设置分发，这里只决定端点。chat 分支的 URL/头/解析与改造前逐字符一致。
        val isResponses = settings.apiStyle(settings.providerId) == "responses"
        val url = if (isResponses) {
            responsesUrl(preset, settings.effectiveBaseUrl, settings.apiKey)
        } else {
            // 端点 = 用户覆盖的 baseUrl / 预设默认 baseUrl + 预设 chatPath。
            // DeepSeek 预设是 https://api.deepseek.com + /chat/completions，与改造前逐字符一致；
            // 用户把完整 .../chat/completions 粘进 baseUrl 的老行为也保留（ProviderApi.chatUrl 里处理）。
            ProviderApi.chatUrl(preset, settings.effectiveBaseUrl, settings.apiKey)
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
        // 鉴权按 provider 的 authStyle 生成：
        //   Bearer        → Authorization: Bearer <key>（DeepSeek 与原实现完全相同）
        //   AnthropicKey  → x-api-key + anthropic-version: 2023-06-01（不再同时发 Bearer）
        //   QueryKey      → 无头，chatUrl 已追加 ?key=
        //   None          → 无鉴权（Ollama / LM Studio）
        for ((name, value) in ProviderApi.authHeaders(preset, settings.apiKey)) {
            requestBuilder.header(name, value)
        }
        val request = requestBuilder.post(payload.toString().toRequestBody(JSON)).build()

        val call = http.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(friendlyNetworkError(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    if (!r.isSuccessful) {
                        val body = try {
                            r.body.string()
                        } catch (e: Exception) {
                            ""
                        }
                        // 友好化：不再把整段原始 JSON 当正文显示，取 error.message / error.code。
                        onError(friendlyHttpError(r.code, body))
                        return
                    }
                    try {
                        if (isResponses) {
                            readResponsesStream(r, onDelta, onDone, onError)
                        } else {
                            readChatStream(r, onDelta, onDone, onError, onSteps)
                        }
                    } catch (e: Exception) {
                        onError(friendlyNetworkError(e))
                    }
                }
            }
        })
        return call
    }

    /**
     * /chat/completions 的 SSE 解析（原 chat() 内的循环原样搬出来，逻辑逐行保持不变）。
     */
    private fun readChatStream(
        r: Response,
        onDelta: (content: String, reasoning: String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
        onSteps: (List<com.yuyuyu.deepseek.data.Step>, String, List<com.yuyuyu.deepseek.data.Source>) -> Unit,
    ) {
        val src = r.body.source()
        // 服务端联网工具：过程事件在 choice.server_tool_event，
        // 完整的 server_tool_calls / annotations 在最后一个 delta；
        // WebToolStream 内部按 index/id 合并、按 url 累加，分批下发也不会丢
        val web = WebToolStream()
        while (true) {
            val line = src.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.substring(5).trim()
            if (data.isEmpty()) continue
            if (data == "[DONE]") break
            val chunk = JSONObject(data)
            chunk.optJSONObject("error")?.let {
                onError(it.optString("message", "生成中断") + styleHint(it.optString("message", "")))
                return
            }
            val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: continue
            val delta = choice.optJSONObject("delta")
            val content = delta?.stringValue("content").orEmpty()
            val reasoning = delta?.stringValue("reasoning_content").orEmpty()
            // 过程事件 / 末包里的 server_tool_calls 一有变化就回调，
            // DsApp 会写进 Msg.steps，思考链实时长出「搜索到 N 个网页」
            if (web.accept(choice, delta, reasoning)) {
                val steps = web.steps()
                // 诊断：adb logcat -s DsSteps（每次步骤变化一行，action:count）
                android.util.Log.d("DsSteps", "steps=" + steps.size + " " +
                    steps.joinToString(" ") { it.action + ":" + it.count })
                onSteps(steps, web.rawCalls, web.citations)
            }
            if (content.isNotEmpty() || reasoning.isNotEmpty()) onDelta(content, reasoning)
        }
        // 收尾兜底：末包只更新了 citations、或者最后一次合并没触发回调时，
        // 这里再刷一次，保证结束前 Msg.steps / Msg.citations 与解析结果一致。
        onSteps(web.steps(), web.rawCalls, web.citations)
        onDone()
    }

    /**
     * /responses 的 SSE 解析（OpenAI Responses API 事件流）：
     *   event: response.output_text.delta              data: {"type":"...","delta":"..."}          → 正文
     *   event: response.reasoning_summary_text.delta   data: {"type":"...","delta":"..."}          → 推理
     *   event: response.reasoning_text.delta           data: {...}                                 → 推理
     *   event: response.output_text.done               data: {"text":"..."}                        → 兜底整段（无 delta 时）
     *   event: response.completed                      data: {...}                                 → 正常结束
     *   event: response.failed / type:"error"          data: {"error":{...}} / {"message":...}      → 报错
     * 没收到 response.completed 而连接关闭时也按完成处理（部分中转站不发完成事件）。
     */
    private fun readResponsesStream(
        r: Response,
        onDelta: (content: String, reasoning: String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val src = r.body.source()
        var gotText = false
        var gotReason = false
        while (true) {
            val line = src.readUtf8Line() ?: break
            // Responses 的 SSE 同时有 "event: xxx" 与 "data: {...}"，事件类型以 data 里的 type 为准
            if (!line.startsWith("data:")) continue
            val data = line.substring(5).trim()
            if (data.isEmpty()) continue
            if (data == "[DONE]") break
            val obj = try {
                JSONObject(data)
            } catch (e: Exception) {
                continue
            }
            when (obj.optString("type")) {
                "response.output_text.delta" -> {
                    val d = obj.optString("delta")
                    if (d.isNotEmpty()) {
                        gotText = true
                        onDelta(d, "")
                    }
                }
                "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> {
                    val d = obj.optString("delta")
                    if (d.isNotEmpty()) {
                        gotReason = true
                        onDelta("", d)
                    }
                }
                // 兜底：有的中转站不发 delta 只发 done，只在完全没收到 delta 时补整段，避免重复
                "response.output_text.done" -> {
                    val t = obj.optString("text")
                    if (!gotText && t.isNotEmpty()) {
                        gotText = true
                        onDelta(t, "")
                    }
                }
                "response.reasoning_summary_text.done", "response.reasoning_text.done" -> {
                    val t = obj.optString("text")
                    if (!gotReason && t.isNotEmpty()) {
                        gotReason = true
                        onDelta("", t)
                    }
                }
                "response.completed", "response.incomplete" -> {
                    onDone()
                    return
                }
                "response.failed" -> {
                    onError(friendlyResponseError(obj))
                    return
                }
                "error" -> {
                    onError(friendlyResponseError(obj))
                    return
                }
            }
            // 少数网关把错误塞在顶层 error 对象里、type 却是别的值
            if (obj.has("error") && obj.optJSONObject("error") != null) {
                onError(friendlyResponseError(obj))
                return
            }
        }
        onDone()
    }

    // ------------------------------------------------------------------ 错误文案（友好化）

    /**
     * 非 2xx 的友好错误：
     *   - 有 error.message → `请求失败（HTTP <code>）：<message>`；
     *   - 没有 message 但有 error.code → 用 code 代替 message；
     *   - 完全没有 → `HTTP <code> <body 前 120 字>`（不把整段 JSON 塞进气泡）。
     * 如果 message 提示要换接口（含 /v1/responses、Responses、chat/completions 等字样），
     * 追加一行「去设置里切 Responses」的指引。
     */
    /**
     * 网络层异常 → 用户能看懂的中文提示。
     * 以前直接把 e.message 显示出来（例如 "Unable to resolve host \"api.deepseek.com\": No address associated with hostname"），
     * 用户看到的就是一串英文日志；这里按异常类型映射成一句话。
     */
    fun friendlyNetworkError(e: Throwable): String {
        val msg = (e.message ?: "").lowercase()
        val name = e.javaClass.simpleName.lowercase()
        return when {
            e is java.net.UnknownHostException || "unable to resolve host" in msg || "no address associated" in msg ->
                "网络未连接或域名解析失败，请检查网络后重试"
            e is java.net.ConnectException || "connection refused" in msg || "failed to connect" in msg ->
                "无法连接到服务器，请检查网络或代理设置"
            e is java.net.SocketTimeoutException || "timeout" in msg || "timed out" in msg ->
                "请求超时，请检查网络后重试"
            e is javax.net.ssl.SSLException || "ssl" in msg || "certificate" in msg ->
                "安全连接失败（证书/SSL 问题），请检查网络代理"
            "network is unreachable" in msg || "no route to host" in msg ->
                "网络不可用，请检查是否已联网"
            name.contains("cleartexthttptraffic") || "cleartext" in msg ->
                "该地址使用了不安全的 http，请改用 https"
            else -> "网络请求失败，请检查网络后重试"
        }
    }

    fun friendlyHttpError(code: Int, body: String): String {
        val text = body.trim()
        val err = try {
            if (text.startsWith("{")) JSONObject(text).optJSONObject("error") else null
        } catch (e: Exception) {
            null
        }
        val message = err?.let { stringOf(it, "message") }.orEmpty()
            .ifBlank { err?.let { stringOf(it, "code") }.orEmpty() }
        if (message.isBlank()) {
            val head = text.replace(Regex("\\s+"), " ").take(120)
            return "HTTP $code " + head + styleHint(head)
        }
        return "请求失败（HTTP $code）：" + message + styleHint(message)
    }

    /** response.failed / type:"error" 事件的错误文案 */
    private fun friendlyResponseError(obj: JSONObject): String {
        val respErr = obj.optJSONObject("response")?.optJSONObject("error")
        val err = obj.optJSONObject("error") ?: respErr
        val message = err?.let { stringOf(it, "message") }.orEmpty()
            .ifBlank { obj.optString("message") }
            .ifBlank { err?.let { stringOf(it, "code") }.orEmpty() }
        val brief = message.ifBlank { "生成中断：" + obj.toString().replace(Regex("\\s+"), " ").take(120) }
        return "请求失败：" + brief + styleHint(brief)
    }

    /** JSONObject 取字符串，兼容 JSON null / 非字符串值 */
    private fun stringOf(o: JSONObject, key: String): String {
        val v = o.opt(key) ?: return ""
        return if (v is String) if (v == "null") "" else v else v.toString()
    }

    /** 服务端提示「该用 Responses API」时，追加操作指引（错误气泡里的第二行） */
    private fun styleHint(text: String): String {
        val t = text.lowercase(Locale.US)
        val needsResponses = t.contains("/v1/responses") || t.contains("responses") ||
            t.contains("chat/completions")
        return if (needsResponses) {
            "\n→ 该服务商要求切换「接口类型」为 Responses API，请在 设置 → API 与模型 里改。"
        } else {
            ""
        }
    }
}
