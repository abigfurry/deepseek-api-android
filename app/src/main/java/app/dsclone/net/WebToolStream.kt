package com.yuyuyu.deepseek.net

import com.yuyuyu.deepseek.data.Source
import com.yuyuyu.deepseek.data.Step
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** optString 遇到 JSON null 会返回 "null"，这里统一兜底成空串 */
internal fun JSONObject.stringValue(key: String): String {
    if (!has(key) || isNull(key)) return ""
    val v = optString(key, "")
    return if (v == "null") "" else v
}

/**
 * 一次联网工具调用（server_tool_event 的 call / server_tool_calls[*].calls[*] 的裁剪版）。
 *
 * 官方 SSE 里的完整结构（实测 2026-11-22，见 _work/tmp/sse_s0.txt / sse_s1.txt）：
 *   choice.server_tool_event = {"type":"call_started","step":0,"call":{...}}        过程：开始
 *   choice.server_tool_event = {"type":"call_completed","call":{"id":...,"status":..,"sources"|"page"}}
 *   最后一个 delta（或 message）里：server_tool_calls = [ {index, reasoning_content, calls:[...]}, ... ]
 * 其中 calls[*] = {index,id,type,kind,action:{type,queries|url},status,sources|page}，
 * action.type ∈ search / open_page / find_in_page（kind = search / deep_search）。
 */
private class ToolCall(
    val index: Int = 0,
    val id: String = "",
    val type: String = "",
    var kind: String = "",
    val action: String = "",
    var queries: List<String> = emptyList(),
    var url: String = "",
    var status: String = "",
    var sources: List<Source> = emptyList(),
    var page: Source? = null,
)

/**
 * 汇总 Chat Completions 的联网工具信息（官方「全家桶应用 API」，2026-11-22 实测）。
 *
 * ## 官方「步骤」生成规则（smali 出处）
 * - 官方 App 的 assistant 消息体是 `fragments` 数组（smali/ky.smali、smali/s96.smali 的
 *   "fragments" / "BATCH" / "THINK" / "TOOL_SEARCH"），联网过程片段为
 *   TOOL_SEARCH / TOOL_OPEN / TOOL_FIND；官方在 `Lvbb;->x..y..z`（smali/vbb.smali:9700-10480）
 *   把「连续同类」的工具片段合并成一个 merged 片段（MergedToolSearch/Open/FindFragment，
 *   smali/s74.smali / smali/lma.smali / smali/o74.smali），遇到 THINK（思考文本）等其它片段时 flush。
 * - merged 行的文案：`已搜索到 %1$d 个网页`（res/values-b+zh+Hans/strings.xml:298-299，
 *   ParameterizedStringRes.mergeToolSearchResults，smali/ic1.smali）/
 *   `已浏览 %1$d 个页面`（strings.xml:295-296，mergeToolOpenResults，smali/azc.smali）/
 *   `在页面内查找`（tool find 片段，smali/x60.smali 的 "TOOL_FIND"）。
 * - 因此这里把调用按「步骤 index 升序 + 步骤内 call.index 升序」排好后，把**连续同 action 的 call
 *   合并成一个 Step**（search 合并 queries，sources 按去 #fragment 的 url 去重；open_page 按 page.url
 *   去重），Step.count = 去重后的网页/页面数。不同 action 或中间夹着 reasoning_content（THINK 片段）
 *   就另起一个 Step。
 * - 「思考」不是工具步骤：官方是 THINK 片段，渲染成线+点的正文段落（AssistantThinkingContent.kt:157，
 *   smali/f70.smali），没有图标行。本 App 的对应物是 `Msg.reasoning`（DsApp 把 delta.reasoning_content
 *   全量追加进 Msg.reasoning），所以这里**不再把 reasoning 写进 Step.reasoning**——否则
 *   ui/ChatParts.kt 的 ThinkingBody 会先渲染 steps 里的 reasoning、再渲染 Msg.reasoning，同一段话出现两遍。
 * - 失败的调用（status=failed / 没有 sources、page）count=0：官方会显示「浏览页面失败 / 联网搜索暂不可用」
 *   （strings.xml:294、673），但当前 ui/ChatParts.kt 的 ThinkStepRow 还没有失败分支，count=0 时不渲染该行，
 *   不会把失败误显示成成功；status 仍保留在 Step.status 里供 UI 后续使用。
 *
 * ## 流式
 * 过程事件一到就更新 buckets 并让 steps() 变化（Client 会回调 onSteps 刷新 Msg.steps，思考链实时长出来）；
 * 最后的 server_tool_calls 是全量权威数据，到达后 steps() 改用它（并保留 rawCalls 供 Store 落盘/重放）。
 */
class WebToolStream {

    private class Bucket(val index: Int) {
        var reasoning = StringBuilder()
        val calls = ArrayList<ToolCall>()
    }

    private val buckets = LinkedHashMap<Int, Bucket>()
    private var pending = StringBuilder()

    /** 合并后的完整步骤数组（按 index 排序），持久化 / 多轮回传都用这份 */
    private val mergedSteps = LinkedHashMap<Int, JSONObject>()

    /** 引用按 url 去重；官方「N 个网页」按页面数算 */
    private val citationMap = LinkedHashMap<String, Source>()

    var rawCalls: String = ""
        private set
    var citations: List<Source> = emptyList()
        private set

    private var authoritative: List<Step> = emptyList()

    /** 当前已解析出的步骤（过程事件阶段是实时合并版，末包到达后是权威全量版） */
    fun steps(): List<Step> {
        if (authoritative.isNotEmpty()) return authoritative
        val ordered = buckets.values.sortedBy { it.index }
        return mergeToolSteps(ordered.map { b -> b.reasoning.toString().trim() to b.calls })
    }

    /** @return 是否有变化需要刷新 UI */
    fun accept(choice: JSONObject, delta: JSONObject?, reasoning: String): Boolean {
        var changed = false

        // 1) 过程事件（choice 上与 delta 并列）
        val ev = choice.optJSONObject("server_tool_event")
        if (ev != null) {
            when (ev.stringValue("type")) {
                "step_reasoning_done" -> {
                    // 这一段 reasoning 属于 step 之前的 THINK 片段：作为合并的分隔标记存起来，
                    // 本身不产生工具行（见类注释）。
                    val step = ev.optInt("step", -1)
                    if (step >= 0) {
                        bucket(step).reasoning.append(pending)
                        pending = StringBuilder()
                        changed = true
                    }
                }

                "call_started" -> {
                    val step = ev.optInt("step", 0)
                    val call = ev.optJSONObject("call")
                    if (call != null) {
                        val action = call.optJSONObject("action")
                        val id = call.stringValue("id")
                        val b = bucket(step)
                        if (b.calls.none { it.id == id }) {
                            b.calls.add(
                                ToolCall(
                                    index = call.optInt("index", b.calls.size),
                                    id = id,
                                    type = call.stringValue("type"),
                                    kind = call.stringValue("kind"),
                                    action = action?.stringValue("type").orEmpty(),
                                    queries = action?.optJSONArray("queries").stringList().orEmpty(),
                                    url = action?.stringValue("url").orEmpty(),
                                    status = "running",
                                )
                            )
                        }
                        changed = true
                    }
                }

                "call_completed" -> {
                    val call = ev.optJSONObject("call")
                    if (call != null) {
                        val id = call.stringValue("id")
                        for (b in buckets.values) {
                            val c = b.calls.firstOrNull { it.id == id } ?: continue
                            c.status = call.stringValue("status").ifBlank { "completed" }
                            c.sources = call.optJSONArray("sources").sources()
                            val page = call.optJSONObject("page")
                            if (page != null) {
                                c.page = page.toSource()
                                if (c.url.isBlank()) c.url = page.stringValue("url")
                            }
                            changed = true
                        }
                    }
                }
            }
        }

        // 2) 最后一个 chunk 里的完整数据（实测一次性下发；仍按可分批累加处理）
        if (delta != null) {
            val arr = delta.optJSONArray("server_tool_calls")
            if (arr != null && arr.length() > 0 && mergeServerToolCalls(arr)) changed = true
            val ann = delta.optJSONArray("annotations")
            if (ann != null && ann.length() > 0 && addCitations(ann)) changed = true
        }
        // 2b) 非流式/末包 variant：官方偶尔把完整数据放在 choice.message 而不是 delta，
        //     这里一并兜住（services 端两种形态都见过）。
        choice.optJSONObject("message")?.let { msg ->
            val arr = msg.optJSONArray("server_tool_calls")
            if (arr != null && arr.length() > 0 && mergeServerToolCalls(arr)) changed = true
            val ann = msg.optJSONArray("annotations")
            if (ann != null && ann.length() > 0 && addCitations(ann)) changed = true
        }

        // 3) 还在流式输出的思考：先攒着，等 step_reasoning_done / 结束归属
        if (reasoning.isNotEmpty()) pending.append(reasoning)
        return changed
    }

    private fun bucket(index: Int): Bucket =
        buckets.getOrPut(index) { Bucket(index) }

    // -------------------------------------------------- server_tool_calls 累加

    /**
     * 合并 delta.server_tool_calls（可能分批）：
     * 步骤按 index 合并、同一 call 按 id 合并（后到的覆盖先到的），返回是否有变化。
     */
    private fun mergeServerToolCalls(arr: JSONArray): Boolean {
        var changed = false
        for (i in 0 until arr.length()) {
            val step = arr.optJSONObject(i) ?: continue
            val index = step.optInt("index", i)
            val old = mergedSteps[index]
            if (old == null) {
                mergedSteps[index] = JSONObject(step.toString())
                changed = true
            } else if (mergeStep(old, step)) {
                changed = true
            }
        }
        if (!changed) return false
        val all = JSONArray()
        for (index in mergedSteps.keys.sorted()) all.put(mergedSteps[index])
        rawCalls = all.toString()
        authoritative = parseCallsArray(all)
        return true
    }

    private fun mergeStep(dst: JSONObject, src: JSONObject): Boolean {
        var changed = false
        // 步骤自己的思考：取更完整的那份（分批时后到的通常更全）
        val newReasoning = src.stringValue("reasoning_content")
        if (newReasoning.length > dst.stringValue("reasoning_content").length) {
            dst.put("reasoning_content", newReasoning)
            changed = true
        }
        val srcCalls = src.optJSONArray("calls") ?: return changed
        val dstCalls = dst.optJSONArray("calls") ?: JSONArray().also {
            dst.put("calls", it)
            changed = true
        }
        for (j in 0 until srcCalls.length()) {
            val srcCall = srcCalls.optJSONObject(j) ?: continue
            val id = srcCall.stringValue("id")
            var pos = -1
            for (k in 0 until dstCalls.length()) {
                val cand = dstCalls.optJSONObject(k) ?: continue
                val sameId = id.isNotEmpty() && cand.stringValue("id") == id
                val sameIndex = id.isEmpty() && cand.optInt("index", -1) == srcCall.optInt("index", -2)
                if (sameId || sameIndex) {
                    pos = k
                    break
                }
            }
            if (pos < 0) {
                dstCalls.put(JSONObject(srcCall.toString()))
                changed = true
            } else {
                val dstCall = dstCalls.optJSONObject(pos) ?: continue
                if (mergeCall(dstCall, srcCall)) {
                    dstCalls.put(pos, dstCall)
                    changed = true
                }
            }
        }
        return changed
    }

    /** call 字段合并：sources/page 用非空的新值覆盖，其余字段新的非空值生效 */
    private fun mergeCall(dst: JSONObject, src: JSONObject): Boolean {
        var changed = false
        for (key in src.keys()) {
            if (key == "sources") {
                val arr = src.optJSONArray(key)
                if (arr != null && arr.length() > 0 && dst.optJSONArray(key)?.toString() != arr.toString()) {
                    dst.put(key, JSONArray(arr.toString()))
                    changed = true
                }
                continue
            }
            if (key == "page") {
                val page = src.optJSONObject(key)
                val old = dst.optJSONObject(key)
                if (page != null) {
                    val merged = if (old != null) JSONObject(old.toString()) else JSONObject()
                    for (pk in page.keys()) {
                        val v = page.opt(pk)
                        if (v != null) merged.put(pk, v)
                    }
                    if (old == null || merged.toString() != old.toString()) {
                        dst.put(key, merged)
                        changed = true
                    }
                }
                continue
            }
            val v = src.opt(key) ?: continue
            if (dst.opt(key)?.toString() != v.toString()) {
                dst.put(key, v)
                changed = true
            }
        }
        return changed
    }

    // -------------------------------------------------- annotations 累加 + 回填站点

    /**
     * annotations 累加：同一个 url 只算一个网页（官方「N 个网页」按页面数）。
     * 引用本身只有 url/title/start_index/end_index，站点名、favicon、摘要、发布时间
     * 从同一响应里的 search sources 回填，找不到就退化成 URL 的域名。
     */
    private fun addCitations(arr: JSONArray): Boolean {
        val index = sourceIndex()
        var changed = false
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.stringValue("type") != "url_citation") continue
            val c = item.optJSONObject("url_citation") ?: continue
            val url = c.stringValue("url")
            if (url.isEmpty() || citationMap.containsKey(url)) continue
            val hit = index[url] ?: index[normalizeUrl(url)] ?: index[hostKey(url)]
            val title = c.stringValue("title")
            citationMap[url] = Source(
                url = url,
                title = title.ifBlank { hit?.title.orEmpty() },
                siteName = hit?.siteName.orEmpty().ifBlank { hostOf(url) },
                siteIcon = hit?.siteIcon.orEmpty(),
                snippet = hit?.snippet.orEmpty(),
                publishTime = c.stringValue("published_at")
                    .ifBlank { c.stringValue("publish_time") }
                    .ifBlank { hit?.publishTime.orEmpty() },
            )
            changed = true
        }
        if (changed) citations = citationMap.values.toList()
        return changed
    }

    /** url / 去掉 #fragment 的 url / host 三种键都能查到站点信息 */
    private fun sourceIndex(): Map<String, Source> {
        val out = LinkedHashMap<String, Source>()
        fun add(source: Source) {
            if (source.url.isBlank()) return
            out[source.url] = source
            out[normalizeUrl(source.url)] = source
            val host = hostOf(source.url)
            if (host.isNotEmpty()) out.putIfAbsent(hostKey(source.url), source)
        }
        for (step in steps()) for (s in step.sources) add(s)
        return out
    }

    private fun normalizeUrl(url: String): String = url.substringBefore('#').trimEnd('/')

    private fun hostOf(url: String): String = runCatching {
        URI(url).host.orEmpty().removePrefix("www.")
    }.getOrDefault("")

    private fun hostKey(url: String): String = "host:" + hostOf(url)
}

// ------------------------------------------------------ 步骤合并（流式与落盘重放共用）

/**
 * 把「每个步骤的 reasoning_content + calls」序列合并成 Msg.steps。
 *
 * 规则（对应官方 merged tool 片段，见类注释）：
 *  - 按步骤顺序、步骤内 call.index 顺序遍历；
 *  - 连续同 action 的 call 合并成一个 Step（search 合并 queries；不同 action 另起一行）；
 *  - reasoning_content 非空 = 官方 THINK 片段，作为分隔：它本身不产生工具行，但会把前后两次
 *    同类调用隔开（与官方「遇到非工具片段就 flush 合并缓冲」一致）；
 *  - Step.count = 去重（url 去掉 #fragment）后的 sources/page 数；搜索失败没有 sources 时为 0。
 */
private fun mergeToolSteps(steps: List<Pair<String, List<ToolCall>>>): List<Step> {
    val out = ArrayList<Step>()
    var run = ArrayList<ToolCall>()

    fun flushRun() {
        if (run.isEmpty()) return
        val first = run.first()
        val queries = LinkedHashSet<String>()
        val pages = LinkedHashMap<String, Source>()
        var url = ""
        var failed = false
        var allCompleted = true
        for (c in run) {
            for (q in c.queries) if (q.isNotBlank()) queries.add(q)
            if (url.isBlank()) url = c.url
            if (c.status == "failed") failed = true
            if (c.status != "completed") allCompleted = false
            for (s in c.sources) putSource(pages, s)
            c.page?.let { putSource(pages, it) }
        }
        val count = when (first.action) {
            // 官方 merge_tool_search_results：「已搜索到 N 个网页」
            "search" -> pages.size
            // find_in_page 没有结果列表，有 url 就显示一行「在页面内查找」
            "find_in_page" -> if (url.isNotBlank()) 1 else 0
            // 官方 merge_tool_open_results：「已浏览 N 个页面」
            else -> pages.size
        }
        out.add(
            Step(
                kind = first.kind,
                action = first.action,
                queries = queries.toList(),
                url = url,
                count = count,
                sources = pages.values.toList(),
                // 思考文本已在 Msg.reasoning 里（见类注释），这里留空避免 UI 重复渲染
                reasoning = "",
                status = when {
                    failed -> "failed"
                    allCompleted -> "completed"
                    else -> "running"
                },
            )
        )
        run = ArrayList()
    }

    for ((reasoning, calls) in steps) {
        if (reasoning.isNotBlank()) flushRun()
        for (c in calls) {
            if (run.isNotEmpty() && run.first().action != c.action) flushRun()
            run.add(c)
        }
    }
    flushRun()
    return out
}

/** 把 server_tool_calls（Store 重放历史时也走这里）解析成步骤 */
fun parseCallsArray(arr: JSONArray): List<Step> {
    val steps = ArrayList<Pair<String, List<ToolCall>>>()
    val indexed = ArrayList<Pair<Int, JSONObject>>()
    for (i in 0 until arr.length()) {
        val step = arr.optJSONObject(i) ?: continue
        indexed.add(step.optInt("index", i) to step)
    }
    indexed.sortBy { it.first }
    for ((_, step) in indexed) {
        val calls = ArrayList<ToolCall>()
        val arrCalls = step.optJSONArray("calls") ?: JSONArray()
        for (j in 0 until arrCalls.length()) {
            val call = arrCalls.optJSONObject(j) ?: continue
            val action = call.optJSONObject("action")
            val page = call.optJSONObject("page")
            calls.add(
                ToolCall(
                    index = call.optInt("index", j),
                    id = call.stringValue("id"),
                    type = call.stringValue("type"),
                    kind = call.stringValue("kind"),
                    action = action?.stringValue("type").orEmpty(),
                    queries = action?.optJSONArray("queries").stringList().orEmpty(),
                    url = action?.stringValue("url").orEmpty()
                        .ifBlank { page?.stringValue("url").orEmpty() },
                    status = call.stringValue("status"),
                    sources = call.optJSONArray("sources").sources(),
                    page = page?.toSource(),
                )
            )
        }
        calls.sortBy { it.index }
        steps.add(step.stringValue("reasoning_content") to calls)
    }
    return mergeToolSteps(steps)
}

/** 同一 url 只算一个网页；先到的字段为准，后到的只补空字段（来源与页面可能分批到达） */
private fun putSource(map: LinkedHashMap<String, Source>, s: Source) {
    val url = s.url.trim()
    if (url.isEmpty()) return
    val key = url.substringBefore('#').trimEnd('/')
    val old = map[key]
    map[key] = if (old == null) {
        s
    } else {
        old.copy(
            title = old.title.ifBlank { s.title },
            siteName = old.siteName.ifBlank { s.siteName },
            siteIcon = old.siteIcon.ifBlank { s.siteIcon },
            snippet = old.snippet.ifBlank { s.snippet },
            publishTime = old.publishTime.ifBlank { s.publishTime },
        )
    }
}

private fun JSONObject.toSource(): Source = Source(
    url = stringValue("url"),
    title = stringValue("title"),
    siteName = stringValue("site_name").ifBlank { stringValue("siteName") },
    siteIcon = stringValue("site_icon").ifBlank { stringValue("siteIcon") },
    snippet = stringValue("snippet").ifBlank { stringValue("content") },
    publishTime = stringValue("published_at")
        .ifBlank { stringValue("publish_time") }
        .ifBlank { stringValue("publishTime") },
)

private fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val v = optString(i, "")
        if (v.isNotEmpty() && v != "null") out.add(v)
    }
    return out
}

private fun JSONArray?.sources(): List<Source> {
    if (this == null) return emptyList()
    val out = ArrayList<Source>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        out.add(o.toSource())
    }
    return out
}
