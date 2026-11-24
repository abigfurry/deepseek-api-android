package com.yuyuyu.deepseek.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 官方 App 导出包 / 其它第三方聊天记录的**容错导入适配器**。
 *
 * 两种输入：
 *
 * 1. **官方 App「设置 → 数据管理 → 导出所有历史对话」下载的 ZIP**（2026-11 实测 v3 格式）：
 *    ```
 *    manifest.json              {"export_format_version":3}
 *    user.json                  账号信息（不用）
 *    chat_session_index.json    [{id,title,pinned,message_count,inserted_at,updated_at}]
 *    chat_sessions/<uuid>.json  {id,title,pinned,current_message_id,inserted_at,updated_at,
 *                                messages:[{id,parent_id,role:USER|ASSISTANT,status,inserted_at,
 *                                  fragments:[
 *                                    {type:REQUEST,content}, {type:RESPONSE,content},
 *                                    {type:THINK,content,elapsed_secs}, {type:TEMPLATE_RESPONSE,content},
 *                                    {type:TOOL_SEARCH,queries,results}, {type:TOOL_OPEN,url,title,...},
 *                                    {type:TOOL_FIND,pattern}, {type:FILE,files},
 *                                    {type:CONTENT_RISK}, {type:TIP} ...]}]}
 *    chat_sessions/<uuid>_files/...   附件本体（205MB 量级，**不导入**）
 *    ```
 *    映射：REQUEST→user、RESPONSE/TEMPLATE_RESPONSE→回答、THINK→思考（elapsed_secs→用时）、
 *    TOOL_*→联网步骤、FILE→附件名。**只导当前分支**：官方导出里包含「重新生成」被丢弃的分支
 *    （样本里 26346 条消息中只有 23390 条在当前链上），沿 current_message_id → parent_id 回溯，
 *    被丢弃的分支不导入（它们正是官方界面里‹2/2›里没有显示的那些）。
 *
 * 2. **任意结构的 JSON**（[parse]）：不依赖具体 schema，递归找到"看起来像会话数组"的东西，
 *    再把每条消息里常见的字段名映射过来 —— 给第三方记录留的后路。
 *
 * 内存：样本 261MB ZIP / 354MB 解压 / 1235 会话 / 26k 消息 / 4500 万字正文。
 * 所以全程流式：ZIP 一条一条读、一条会话一条会话转，绝不把整包读成 String；
 * 同时有 [Limits] 上限（单条消息、单会话、总字符数、会话数），避免把 512MB 堆吃穿。
 */
object OfficialImport {

    /**
     * 导入上限（防止 OOM）。
     *
     * 按 UTF-16 算，字符串内存 ≈ 字符数 × 2 字节，再叠上 org.json 树和 JSON 序列化时的临时对象，
     * 最坏约为字符数的 3~4 倍。样本的「当前分支正文」约 4530 万字符（≈90MB 纯文本），
     * 默认值能完整装下而不至于把 App 的正常界面挤爆。
     */
    data class Limits(
        /** 单条消息正文（回答/用户提问）上限，超出截断并计数 */
        val maxMessageChars: Int = 200_000,
        /** 单条消息思考内容上限 */
        val maxReasoningChars: Int = 100_000,
        /** 单个会话正文上限，超出后该会话后面的消息不再导入（样本最大会话 285 万字符） */
        val maxSessionChars: Int = 3_000_000,
        /** 整包正文总上限（由调用方按堆大小算，见 DsApp） */
        val maxTotalChars: Int = 80_000_000,
        /** 会话说上限 */
        val maxSessions: Int = 5_000,
        /** 单条 ZIP 条目解压上限（最大会话 JSON 8.7MB，留余量） */
        val maxEntryBytes: Int = 32 * 1024 * 1024,
    )

    /** 一次导入的结果；[sessions] 已按 chat_session_index.json 的顺序排好。 */
    data class Result(
        val sessions: List<Session> = emptyList(),
        /** 索引里的会话总数（没有索引时 = 实际读到的会话数） */
        val totalSessions: Int = 0,
        /** 因上限 / 解析失败被跳过的会话数 */
        val skippedSessions: Int = 0,
        /** 被截断的消息数 */
        val truncatedMessages: Int = 0,
        /** 被截断的会话数（单会话超上限） */
        val truncatedSessions: Int = 0,
        /** 实际导入的正文总字符数 */
        val totalChars: Int = 0,
    ) {
        val isEmpty: Boolean get() = sessions.isEmpty()
    }

    /** ZIP 魔数（PK\x03\x04）；官方导出包按 ZIP 判定，不看后缀。 */
    fun isZip(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            (head[2] == 0x03.toByte() || head[2] == 0x05.toByte() || head[2] == 0x07.toByte())

    // ── 官方导出 ZIP ────────────────────────────────────────────────────────

    private class IndexEntry(
        val id: String,
        val title: String,
        val pinned: Boolean,
        val updatedAt: Long,
    )

    private class Budget {
        var chars = 0
        var truncatedMessages = 0
        var truncatedSessions = 0
        var skipped = 0
    }

    /**
     * 解析官方导出 ZIP（先由调用方把 SAF Uri 拷到 cacheDir 的临时文件）。
     *
     * 用 [java.util.zip.ZipFile] 而不是流式 ZipInputStream 的原因：官方包 261MB 里约 205MB 是
     * 附件本体（chat_sessions/<id>_files/ 目录下的 webp 等），ZipInputStream 即使只是「跳过」一个条目也必须
     * 把它完整解压才能走到下一条；ZipFile 走中央目录，只解我们真正要读的 1235 个 JSON（约 148MB）。
     *
     * @param onProgress (已解析会话数, 总会话数)
     */
    fun parseZip(
        file: java.io.File,
        limits: Limits = Limits(),
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result {
        val byId = LinkedHashMap<String, Session>()
        val index = ArrayList<IndexEntry>()
        val rank = HashMap<String, Int>()
        val budget = Budget()
        var sawManifest = false

        val zf = java.util.zip.ZipFile(file)
        try {
            val sessionEntries = ArrayList<java.util.zip.ZipEntry>()
            val allNames = HashSet<String>()
            val it = zf.entries()
            while (it.hasMoreElements()) {
                val e = it.nextElement()
                val name = e.name.trimStart('/').replace('\\', '/')
                allNames.add(name)
                if (isSessionEntry(name)) sessionEntries.add(e)
            }
            sawManifest = allNames.contains("manifest.json")
            val indexEntry = zf.getEntry("chat_session_index.json")
            if (indexEntry != null) {
                readEntry(zf, indexEntry, 8 * 1024 * 1024)?.let { readIndex(it, index, rank) }
            }
            // 有索引时按索引顺序（官方侧栏的顺序）；没有索引就按文件名
            if (index.isNotEmpty()) {
                sessionEntries.sortBy { e ->
                    rank[SESSION_PREFIX + sessionIdOf(e.name)] ?: Int.MAX_VALUE
                }
            } else {
                sessionEntries.sortBy { it.name }
            }
            val total = if (index.isNotEmpty()) index.size else sessionEntries.size
            var done = 0
            for (e in sessionEntries) {
                if (byId.size >= limits.maxSessions || budget.chars >= limits.maxTotalChars) {
                    budget.skipped++
                    continue
                }
                val bytes = readEntry(zf, e, limits.maxEntryBytes)
                if (bytes == null) {
                    budget.skipped++
                } else {
                    val o = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull()
                    if (o == null) {
                        budget.skipped++
                    } else {
                        val parsed = sessionOf(o, limits, budget)
                        if (parsed == null) {
                            budget.skipped++
                        } else if (budget.chars + parsed.second > limits.maxTotalChars) {
                            // 单个会话本身超总预算时整条跳过（比留半截更不容易误导）
                            budget.skipped++
                        } else {
                            budget.chars += parsed.second
                            byId[parsed.first.id] = parsed.first
                        }
                    }
                }
                done++
                if (done % 20 == 0 || done == sessionEntries.size) onProgress(done, total)
            }
        } finally {
            runCatching { zf.close() }
        }

        // 用索引补标题/置顶/更新时间，并按索引顺序排序
        for (e in index) {
            val key = SESSION_PREFIX + e.id
            val s = byId[key] ?: continue
            byId[key] = s.copy(
                title = e.title.ifBlank { s.title },
                pinned = e.pinned || s.pinned,
                updatedAt = if (e.updatedAt > 0) e.updatedAt else s.updatedAt,
            )
        }
        val ordered = byId.values.sortedWith(
            compareBy<Session> { rank[it.id] ?: Int.MAX_VALUE }
        )

        // 不是官方包（既没有 manifest 也没有索引、也没有 chat_sessions/*）→ 返回空，交给上层报错
        val looksOfficial = sawManifest || index.isNotEmpty() || ordered.isNotEmpty()
        return Result(
            sessions = if (looksOfficial) ordered else emptyList(),
            totalSessions = if (index.isNotEmpty()) index.size else byId.size,
            skippedSessions = budget.skipped,
            truncatedMessages = budget.truncatedMessages,
            truncatedSessions = budget.truncatedSessions,
            totalChars = budget.chars,
        )
    }

    private fun sessionIdOf(name: String): String = name.substringAfterLast('/').removeSuffix(".json")

    private fun isSessionEntry(name: String): Boolean =
        name.startsWith("chat_sessions/") &&
            name.count { it == '/' } == 1 &&
            name.endsWith(".json", ignoreCase = true) &&
            !name.contains("_files/")

    private fun readEntry(
        zf: java.util.zip.ZipFile,
        entry: java.util.zip.ZipEntry,
        max: Int,
    ): ByteArray? = zf.getInputStream(entry).use { readLimited(it, max) }

    private fun readLimited(ins: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream(minOf(max, 256 * 1024))
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = ins.read(buf)
            if (n <= 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun readIndex(bytes: ByteArray, index: MutableList<IndexEntry>, rank: MutableMap<String, Int>) {
        val arr = runCatching { JSONArray(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = str(o, "id").trim()
            if (id.isEmpty()) continue
            index.add(
                IndexEntry(
                    id = id,
                    title = str(o, "title").trim(),
                    pinned = o.optBoolean("pinned", false),
                    updatedAt = parseIso(str(o, "updated_at")) ?: 0L,
                )
            )
            rank[SESSION_PREFIX + id] = index.lastIndex
            if (index.size >= 20_000) break
        }
    }

    private const val SESSION_PREFIX = "official:"
    private const val TRUNC_MARK = "\n\n…（导入时超出长度上限，已截断）"

    /** 一条会话 + 它占用的正文字符数；解析不出内容返回 null。 */
    private fun sessionOf(o: JSONObject, limits: Limits, budget: Budget): Pair<Session, Int>? {
        val rawId = str(o, "id").trim()
        if (rawId.isEmpty()) return null
        val arr = o.optJSONArray("messages") ?: return null
        if (arr.length() == 0) return null

        // 官方消息 id 是 1..N 的整数，parent_id 指向父消息；先建索引
        val byMsgId = HashMap<Long, JSONObject>(arr.length() * 2)
        val sequence = ArrayList<JSONObject>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val id = m.optLong("id", (i + 1).toLong())
            byMsgId[id] = m
            sequence.add(m)
        }
        if (sequence.isEmpty()) return null

        // 官方导出包含「重新生成」被丢弃的分支，只取 current_message_id 回溯出的当前链
        val chain = activeChain(o, byMsgId) ?: sequence

        val msgs = ArrayList<Msg>(chain.size)
        val usedIds = HashSet<Long>()
        var chars = 0
        var truncatedMsgs = 0
        var truncatedSession = false
        for ((i, m) in chain.withIndex()) {
            val conv = messageOf(m, i, limits) ?: continue
            if (chars + conv.second > limits.maxSessionChars) {
                truncatedSession = true
                break
            }
            var msg = conv.first
            if (!usedIds.add(msg.id)) msg = msg.copy(id = -(i + 1).toLong() - 1_000_000L)
            msgs.add(msg)
            chars += conv.second
            if (conv.third) truncatedMsgs++
        }
        if (msgs.isEmpty()) return null

        budget.truncatedMessages += truncatedMsgs
        if (truncatedSession) budget.truncatedSessions++

        val title = str(o, "title").trim().ifBlank {
            msgs.firstOrNull { it.role == "user" }?.text?.lineSequence()?.firstOrNull()
                ?.trim()?.take(30).orEmpty()
        }.ifBlank { "导入的对话" }
        val updated = parseIso(str(o, "updated_at")) ?: System.currentTimeMillis()
        return Session(
            id = SESSION_PREFIX + rawId,
            title = title,
            updatedAt = updated,
            messages = msgs,
            pinned = o.optBoolean("pinned", false),
        ) to chars
    }

    /** 沿 current_message_id → parent_id 回溯，反转成时间顺序；数据不完整时返回 null（调用方用原顺序）。 */
    private fun activeChain(
        o: JSONObject,
        byMsgId: Map<Long, JSONObject>,
    ): List<JSONObject>? {
        if (!o.has("current_message_id") || o.isNull("current_message_id")) return null
        var cur = o.optLong("current_message_id", -1L)
        if (cur == -1L || !byMsgId.containsKey(cur)) return null
        val rev = ArrayList<JSONObject>()
        val seen = HashSet<Long>()
        while (true) {
            if (!seen.add(cur)) break
            val m = byMsgId[cur] ?: break
            rev.add(m)
            if (!m.has("parent_id") || m.isNull("parent_id")) break
            val parent = m.optLong("parent_id", -1L)
            if (parent == -1L) break
            cur = parent
        }
        if (rev.isEmpty()) return null
        // 回溯只走到 parent 不存在的消息为止；正常数据会一直走到根
        rev.reverse()
        return rev
    }

    /**
     * 一条官方消息 → 本 App 的 [Msg]。
     * @return (消息, 占用字符数, 是否发生截断)
     */
    private fun messageOf(m: JSONObject, seq: Int, limits: Limits): Triple<Msg, Int, Boolean>? {
        val frags = m.optJSONArray("fragments") ?: return null
        if (frags.length() == 0) return null
        val role = if (str(m, "role").equals("USER", ignoreCase = true)) "user" else "assistant"

        val text = StringBuilder()
        val reasoning = StringBuilder()
        var thinkSeconds = 0
        var hasImages = false
        val files = ArrayList<MsgFile>()
        val steps = ArrayList<Step>()
        val cites = LinkedHashMap<String, Source>()

        fun appendText(sb: StringBuilder, s: String) {
            if (s.isBlank()) return
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(s)
        }

        for (i in 0 until frags.length()) {
            val f = frags.optJSONObject(i) ?: continue
            when (str(f, "type").uppercase(Locale.US)) {
                "REQUEST", "RESPONSE", "TEMPLATE_RESPONSE" ->
                    appendText(text, str(f, "content").trimEnd())

                "THINK" -> {
                    appendText(reasoning, str(f, "content").trimEnd())
                    thinkSeconds += f.optInt("elapsed_secs", 0).coerceAtLeast(0)
                }

                "TOOL_SEARCH", "TOOL_IMAGE_SEARCH" -> {
                    val results = f.optJSONArray("results")
                    val sources = sourcesOf(results)
                    val queries = stringList(f.optJSONArray("queries"))
                    if (sources.isNotEmpty() || queries.isNotEmpty()) {
                        steps.add(
                            Step(
                                kind = "search",
                                action = "search",
                                queries = queries,
                                count = sources.size,
                                sources = sources,
                                status = "completed",
                            )
                        )
                    }
                    for (s in sources) if (s.url.isNotBlank()) cites.putIfAbsent(s.url, s)
                }

                "TOOL_OPEN", "READ_LINK" -> {
                    val url = str(f, "url").trim()
                    val page = Source(
                        url = url,
                        title = str(f, "title").trim(),
                        siteName = str(f, "site_name").trim(),
                        siteIcon = str(f, "site_icon").trim(),
                        snippet = str(f, "snippet").trim(),
                        publishTime = str(f, "published_at").trim(),
                    )
                    if (url.isNotBlank()) {
                        steps.add(
                            Step(
                                kind = "open_page",
                                action = "open_page",
                                url = url,
                                count = 1,
                                sources = listOf(page),
                                status = "completed",
                            )
                        )
                        cites.putIfAbsent(url, page)
                    }
                }

                "TOOL_FIND", "PARTIAL_FILE_READ" -> {
                    val url = str(f, "url").trim()
                    steps.add(
                        Step(
                            kind = "find_in_page",
                            action = "find_in_page",
                            url = url,
                            count = if (url.isNotBlank()) 1 else 0,
                            queries = stringList(f.optJSONArray("queries"))
                                .ifEmpty { listOfNotNull(str(f, "pattern").trim().ifBlank { null }) },
                            status = "completed",
                        )
                    )
                }

                "FILE" -> {
                    val fa = f.optJSONArray("files") ?: continue
                    for (j in 0 until fa.length()) {
                        val fo = fa.optJSONObject(j) ?: continue
                        val name = str(fo, "file_name").ifBlank {
                            str(fo, "name").ifBlank { str(fo, "file_id") }
                        }
                        if (name.isNotBlank()) files.add(MsgFile(name = name, size = fo.optLong("file_size", 0L)))
                        if (fo.optBoolean("is_image", false)) hasImages = true
                    }
                }
                // CONTENT_RISK / TIP 是官方自己加的提示，本 App 有对应文案，不导入
                else -> Unit
            }
        }

        var outText = text.toString()
        var outReason = reasoning.toString()
        var truncated = false
        if (outText.length > limits.maxMessageChars) {
            outText = outText.take(limits.maxMessageChars) + TRUNC_MARK
            truncated = true
        }
        if (outReason.length > limits.maxReasoningChars) {
            outReason = outReason.take(limits.maxReasoningChars) + TRUNC_MARK
            truncated = true
        }
        if (outText.isBlank() && outReason.isBlank() && steps.isEmpty() && files.isEmpty()) return null

        val id = m.optLong("id", (seq + 1).toLong()).let { if (it == 0L) (seq + 1).toLong() else it }
        val chars = outText.length + outReason.length +
            steps.sumOf { st -> st.queries.sumOf { it.length } } +
            cites.values.sumOf { it.title.length + it.snippet.length }
        return Triple(
            Msg(
                id = id,
                role = role,
                text = outText,
                reasoning = outReason,
                thinkSeconds = thinkSeconds,
                hasImages = hasImages,
                files = files,
                steps = steps,
                citations = cites.values.toList(),
            ),
            chars,
            truncated,
        )
    }

    private fun sourcesOf(arr: JSONArray?): List<Source> {
        if (arr == null) return emptyList()
        val out = ArrayList<Source>(arr.length())
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val url = str(r, "url").trim()
            if (url.isBlank() || !seen.add(url)) continue
            out.add(
                Source(
                    url = url,
                    title = str(r, "title").trim(),
                    siteName = str(r, "site_name").trim(),
                    siteIcon = str(r, "site_icon").trim(),
                    snippet = str(r, "snippet").trim(),
                    publishTime = str(r, "published_at").trim(),
                )
            )
        }
        return out
    }

    private fun stringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }

    /** JSONObject.optString 遇到 JSON null 会返回 "null"，统一兜底过滤。 */
    private fun str(o: JSONObject, key: String): String {
        if (!o.has(key) || o.isNull(key)) return ""
        val v = o.optString(key, "")
        return if (v == "null") "" else v
    }

    private val ISO_FORMATS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
    )

    private fun parseIso(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        s.toLongOrNull()?.let { return normalizeTime(it) }
        for (fmt in ISO_FORMATS) {
            val t = runCatching {
                SimpleDateFormat(fmt, Locale.US).apply {
                    timeZone = if (fmt.endsWith("XXX")) TimeZone.getDefault()
                    else TimeZone.getTimeZone("UTC")
                }.parse(s)?.time
            }.getOrNull()
            if (t != null) return t
        }
        return null
    }

    // ── 通用容错 JSON（第三方 / 未知格式） ──────────────────────────────────

    private val SESSION_ARRAY_KEYS = listOf(
        "sessions", "conversations", "chats", "chat_sessions", "conversation_list",
        "history", "chat_history", "data", "items", "list", "records", "threads",
    )
    private val MESSAGE_ARRAY_KEYS = listOf(
        "messages", "conversation", "chat_messages", "msgs", "turns", "dialog", "records", "history",
    )
    private val ROLE_KEYS = listOf("role", "author", "sender", "from", "type", "speaker")
    private val TEXT_KEYS = listOf(
        "text", "content", "message", "body", "value", "answer", "question", "prompt", "reply",
    )
    private val REASON_KEYS = listOf(
        "reasoning", "thinking", "reasoning_content", "think", "thought", "chain_of_thought",
    )
    private val TITLE_KEYS = listOf("title", "name", "topic", "summary")
    private val ID_KEYS = listOf("id", "uuid", "conversation_id", "session_id", "chat_id", "key")
    private val TIME_KEYS = listOf(
        "updated_at", "update_time", "updatedAt", "created_at", "create_time", "createdAt",
        "timestamp", "time", "inserted_at",
    )

    /** 尝试把任意结构的 JSON 文本解析成会话列表；失败返回空。 */
    fun parse(json: String): List<Session> {
        val root = runCatching {
            val t = json.trim()
            if (t.startsWith("[")) JSONArray(t) else JSONObject(t)
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<Session>()
        collect(root, out, 0)
        // 去重 + 丢掉空会话
        return out
            .filter { it.messages.any { m -> m.text.isNotBlank() || m.reasoning.isNotBlank() } }
            .distinctBy { it.id }
    }

    // ── 递归收集 ────────────────────────────────────────────────────────────

    private fun collect(node: Any?, out: MutableList<Session>, depth: Int) {
        if (depth > 6) return
        when (node) {
            is JSONArray -> {
                // 数组里如果像"会话"就整批转，否则继续往里挖
                val asSessions = runCatching { arrayToSessions(node) }.getOrDefault(emptyList())
                if (asSessions.isNotEmpty()) {
                    out.addAll(asSessions)
                } else {
                    for (i in 0 until node.length()) collect(node.opt(i), out, depth + 1)
                }
            }
            is JSONObject -> {
                // 优先找已知的会话数组键
                SESSION_ARRAY_KEYS.forEach { k ->
                    val arr = node.optJSONArray(k)
                    if (arr != null) {
                        val asSessions = runCatching { arrayToSessions(arr) }.getOrDefault(emptyList())
                        if (asSessions.isNotEmpty()) out.addAll(asSessions) else collect(arr, out, depth + 1)
                    }
                }
                // 再递归所有子对象
                node.keys().forEach { k ->
                    if (k in SESSION_ARRAY_KEYS) return@forEach
                    when (val v = node.opt(k)) {
                        is JSONObject, is JSONArray -> collect(v, out, depth + 1)
                    }
                }
            }
        }
    }

    private fun arrayToSessions(arr: JSONArray): List<Session> {
        val list = ArrayList<Session>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val msgs = messagesOf(o)
            if (msgs.isEmpty()) continue
            val id = pickString(o, ID_KEYS) ?: "import-${System.nanoTime()}-$i"
            val title = pickString(o, TITLE_KEYS)
                ?: msgs.firstOrNull { it.role == "user" }?.text?.take(24)?.replace("\n", " ")
                ?: "导入的对话"
            list.add(
                Session(
                    id = "official:$id",
                    title = title,
                    updatedAt = pickLong(o, TIME_KEYS) ?: System.currentTimeMillis(),
                    messages = msgs,
                )
            )
        }
        return list
    }

    private fun messagesOf(o: JSONObject): List<Msg> {
        var arr: JSONArray? = null
        for (k in MESSAGE_ARRAY_KEYS) {
            arr = o.optJSONArray(k)
            if (arr != null) break
        }
        if (arr == null) {
            // 有些格式是 {messages: {0:{...},1:{...}}}
            val nested = o.optJSONObject("messages")
            if (nested != null) {
                val tmp = JSONArray()
                nested.keys().forEach { k -> nested.optJSONObject(k)?.let { tmp.put(it) } }
                arr = tmp
            }
        }
        arr ?: return emptyList()

        val out = ArrayList<Msg>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val roleRaw = pickString(m, ROLE_KEYS) ?: ""
            val role = normalizeRole(roleRaw)
            val text = textOf(m)
            val reasoning = pickString(m, REASON_KEYS) ?: ""
            if (text.isBlank() && reasoning.isBlank()) continue
            out.add(
                Msg(
                    id = System.nanoTime() + i,
                    role = role,
                    text = text,
                    reasoning = reasoning,
                )
            )
        }
        return out
    }

    private fun normalizeRole(raw: String): String {
        val r = raw.lowercase()
        return when {
            r.contains("user") || r.contains("human") || r.contains("question") || r == "u" || r.contains("用户") -> "user"
            else -> "assistant"
        }
    }

    /** 文本字段可能是 string，也可能是 [{type:"text", text:"..."}] 这种分段数组。 */
    private fun textOf(m: JSONObject): String {
        for (k in TEXT_KEYS) {
            when (val v = m.opt(k)) {
                is String -> if (v.isNotBlank()) return v
                is JSONArray -> {
                    val sb = StringBuilder()
                    for (i in 0 until v.length()) {
                        when (val e = v.opt(i)) {
                            is String -> sb.append(e)
                            is JSONObject -> {
                                val t = e.optString("text").ifBlank {
                                    e.optString("content").ifBlank { e.optString("value") }
                                }
                                if (t.isNotBlank()) {
                                    if (sb.isNotEmpty()) sb.append('\n')
                                    sb.append(t)
                                }
                            }
                        }
                    }
                    if (sb.isNotBlank()) return sb.toString()
                }
                is JSONObject -> {
                    val t = v.optString("text").ifBlank {
                        v.optString("content").ifBlank { v.optString("value") }
                    }
                    if (t.isNotBlank()) return t
                }
            }
        }
        return ""
    }

    private fun pickString(o: JSONObject, keys: List<String>): String? {
        for (k in keys) {
            val v = o.opt(k)
            if (v is String && v.isNotBlank()) return v
        }
        return null
    }

    private fun pickLong(o: JSONObject, keys: List<String>): Long? {
        for (k in keys) {
            val v = o.opt(k)
            when (v) {
                is Long -> if (v > 0) return normalizeTime(v)
                is Int -> if (v > 0) return normalizeTime(v.toLong())
                is Double -> if (v > 0) return normalizeTime(v.toLong())
                is String -> {
                    // 可能是毫秒/秒时间戳，也可能是 ISO 时间
                    val l = v.toLongOrNull()
                    if (l != null && l > 0) return normalizeTime(l)
                    parseIso(v)?.let { return it }
                }
            }
        }
        return null
    }

    /** 秒级时间戳 → 毫秒；像 2026 年的毫秒值原样保留。 */
    private fun normalizeTime(v: Long): Long = if (v < 100_000_000_000L) v * 1000 else v
}
