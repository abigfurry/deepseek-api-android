package com.yuyuyu.deepseek.net

import com.yuyuyu.deepseek.data.Msg
import com.yuyuyu.deepseek.data.ProviderApi
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

    fun payloadJson(
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
        val model = settings.model.ifBlank {
            if (isDeepSeek) "deepseek-flash" else preset.fallbackModels.firstOrNull().orEmpty()
        }
        val effectiveModel = if (isDeepSeek &&
            (settings.search || imageDataUrls.isNotEmpty() || model in setOf("deepseek-chat", "deepseek-reasoner", "deepseek-v4-flash-vision-exp"))
        ) "deepseek-flash" else model
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
        // 端点 = 用户覆盖的 baseUrl / 预设默认 baseUrl + 预设 chatPath。
        // DeepSeek 预设是 https://api.deepseek.com + /chat/completions，与改造前逐字符一致；
        // 用户把完整 .../chat/completions 粘进 baseUrl 的老行为也保留（ProviderApi.chatUrl 里处理）。
        val requestBuilder = Request.Builder()
            .url(ProviderApi.chatUrl(preset, settings.effectiveBaseUrl, settings.apiKey))
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
                onError(e.message ?: "网络连接失败")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    if (!r.isSuccessful) {
                        val body = try {
                            r.body.string()
                        } catch (e: Exception) {
                            ""
                        }
                        onError("HTTP " + r.code + " " + body.take(400))
                        return
                    }
                    try {
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
                                onError(it.optString("message", "生成中断"))
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
                    } catch (e: Exception) {
                        onError(e.message ?: "读取响应失败")
                    }
                }
            }
        })
        return call
    }
}
