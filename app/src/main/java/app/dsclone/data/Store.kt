package com.yuyuyu.deepseek.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Store {

    /** JSONObject.optString 遇到 JSON null 会返回字符串 "null"，这里统一兜底 */
    private fun str(o: JSONObject, key: String, def: String = ""): String {
        val v = o.optString(key, def)
        return if (v == "null") def else v
    }

    // ---- 多 provider（19 家）新增的 4 个 Map 字段的手写序列化 ----
    // settings.json 是手写序列化，load 与 save 两处必须同时维护，否则「改了重启就丢」。

    /** 读 {"key":"value"} 形态的 Map（跳过空值） */
    private fun readStringMap(o: JSONObject?): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        if (o == null) return out
        for (k in o.keys()) {
            val v = o.opt(k)
            if (v is String && v.isNotBlank()) out[k] = v
        }
        return out
    }

    /** 读 {"key":["a","b"]} 形态的 Map（跳过空数组/空串） */
    private fun readListMap(o: JSONObject?): LinkedHashMap<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        if (o == null) return out
        for (k in o.keys()) {
            val arr = o.optJSONArray(k) ?: continue
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val v = arr.optString(i, "").trim()
                if (v.isNotEmpty()) list.add(v)
            }
            if (list.isNotEmpty()) out[k] = list
        }
        return out
    }

    private fun writeStringMap(map: Map<String, String>): JSONObject {
        val o = JSONObject()
        for ((k, v) in map) if (v.isNotBlank()) o.put(k, v)
        return o
    }

    private fun writeListMap(map: Map<String, List<String>>): JSONObject {
        val o = JSONObject()
        for ((k, v) in map) {
            if (v.isEmpty()) continue
            o.put(k, JSONArray().also { arr -> for (m in v) arr.put(m) })
        }
        return o
    }


    private fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    fun loadSettings(ctx: Context): Settings {
        val f = file(ctx, "settings.json")
        if (!f.exists()) return Settings()
        return try {
            val o = JSONObject(f.readText())
            val keys = LinkedHashMap<String, String>()
            val ko = o.optJSONObject("keys")
            if (ko != null) {
                for (k in ko.keys()) keys[k] = str(ko, k)
            }
            val pid = str(o, "providerId", "deepseek")
            val legacyBaseUrl = str(o, "baseUrl", "https://api.deepseek.com")
            val legacyBaseUrlPresent = o.has("baseUrl") && !o.isNull("baseUrl")
            val readBaseUrls = readStringMap(o.optJSONObject("baseUrls"))
            // 迁移（老 json 只有单个全局 baseUrl）：只有「用户确实改过（与当前 provider 的预设默认值
            // 不同）」时才搬进 baseUrls[providerId]，否则升级后手填的代理地址会丢。
            val baseUrls = if (
                readBaseUrls.isEmpty() &&
                legacyBaseUrlPresent &&
                legacyBaseUrl.isNotBlank() &&
                legacyBaseUrl != presetOf(pid).baseUrl
            ) {
                linkedMapOf(pid to legacyBaseUrl)
            } else {
                readBaseUrls
            }
            Settings(
                providerId = pid,
                baseUrl = legacyBaseUrl,
                model = str(o, "model", "deepseek-flash"),
                thinking = o.optBoolean("thinking", false),
                search = o.optBoolean("search", false),
                keys = keys,
                temperature = o.optDouble("temperature", 1.0).toFloat(),
                topP = o.optDouble("topP", 1.0).toFloat(),
                maxTokens = o.optInt("maxTokens", 0),
                stop = str(o, "stop"),
                jsonMode = o.optBoolean("jsonMode", false),
                systemPrompt = str(o, "systemPrompt"),
                reasoningEffort = str(o, "reasoningEffort", "high"),
                titleEnabled = o.optBoolean("titleEnabled", true),
                safety = o.optBoolean("safety", true),
                userId = str(o, "userId"),
                nickName = str(o, "nickName"),
                avatarPath = str(o, "avatarPath"),
                includeUsage = o.optBoolean("includeUsage", true),
                // 官方设置页新增项：全部带默认值，老 settings.json 直接兼容
                appearance = str(o, "appearance", "system"),
                fontSizeLevel = o.optInt("fontSizeLevel", 2),
                followSystemFont = o.optBoolean("followSystemFont", true),
                autoFoldThinking = o.optBoolean("autoFoldThinking", true),
                trainOnData = o.optBoolean("trainOnData", true),
                preloadHistory = o.optBoolean("preloadHistory", false),
                appLanguage = str(o, "appLanguage", "system"),
                // 朗读音色：新字段是 voiceId；老版本写的是 voice，两个都认，缺省 stella
                voiceId = str(
                    o,
                    "voiceId",
                    str(o, "voice", "stella"),
                ).ifBlank { "stella" },
                // 多 provider 新增的 4 个 Map（老 json 没有 → 空 map）
                baseUrls = baseUrls,
                providerModels = readListMap(o.optJSONObject("providerModels")),
                customModels = readListMap(o.optJSONObject("customModels")),
                balanceCache = readStringMap(o.optJSONObject("balanceCache")),
                // 接口类型：providerId -> "chat" | "responses"（老 json 没有 → 空 map = 全部 chat）
                apiStyles = readStringMap(o.optJSONObject("apiStyles")),
            )
        } catch (e: Exception) {
            Settings()
        }
    }

    /** 只读外观，供 DsTheme 在 Compose 之外尽早拿到（不必反序列化全部字段） */
    fun loadAppearance(ctx: Context): String {
        val f = file(ctx, "settings.json")
        if (!f.exists()) return "system"
        return try {
            val v = JSONObject(f.readText()).optString("appearance", "system")
            if (v == "light" || v == "dark") v else "system"
        } catch (e: Exception) {
            "system"
        }
    }

    /** 只改 appearance 一个键，其它字段保持磁盘上的原样（避免覆盖并发的设置写入） */
    fun saveAppearance(ctx: Context, mode: String) {
        val f = file(ctx, "settings.json")
        val o = try {
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        try {
            o.put("appearance", if (mode == "light" || mode == "dark") mode else "system")
            f.writeText(o.toString())
        } catch (e: Exception) {
            // 忽略：下次整份保存时会再写一遍
        }
    }

    fun saveSettings(ctx: Context, s: Settings) {
        val keys = JSONObject()
        for ((k, v) in s.keys) keys.put(k, v)
        val o = JSONObject()
            .put("providerId", s.providerId)
            .put("baseUrl", s.baseUrl)
            .put("model", s.model)
            .put("thinking", s.thinking)
            .put("search", s.search)
            .put("keys", keys)
            .put("temperature", s.temperature.toDouble())
            .put("topP", s.topP.toDouble())
            .put("maxTokens", s.maxTokens)
            .put("stop", s.stop)
            .put("jsonMode", s.jsonMode)
            .put("systemPrompt", s.systemPrompt)
            .put("reasoningEffort", s.reasoningEffort)
            .put("titleEnabled", s.titleEnabled)
            .put("safety", s.safety)
            .put("userId", s.userId)
            .put("nickName", s.nickName)
            .put("avatarPath", s.avatarPath)
            .put("includeUsage", s.includeUsage)
            .put("appearance", s.appearance)
            .put("fontSizeLevel", s.fontSizeLevel)
            .put("followSystemFont", s.followSystemFont)
            .put("autoFoldThinking", s.autoFoldThinking)
            .put("trainOnData", s.trainOnData)
            .put("preloadHistory", s.preloadHistory)
            .put("appLanguage", s.appLanguage)
            .put("voiceId", s.voiceId)
            // 兼容老版本：老代码只认 voice
            .put("voice", s.voiceId)
            // 多 provider：每家独立 baseUrl / 模型缓存 / 手填模型 / 余额缓存
            .put("baseUrls", writeStringMap(s.baseUrls))
            .put("providerModels", writeListMap(s.providerModels))
            .put("customModels", writeListMap(s.customModels))
            .put("balanceCache", writeStringMap(s.balanceCache))
            // 接口类型：providerId -> "chat" | "responses"
            .put("apiStyles", writeStringMap(s.apiStyles))
        file(ctx, "settings.json").writeText(o.toString())
    }

    /**
     * 把 sessions.json 的 JSON 文本解析成会话列表（小数据量用；磁盘 / 导入请用 InputStream 版）。
     */
    fun parseSessions(json: String): List<Session> =
        runCatching { parseSessions(json.byteInputStream()) }.getOrDefault(emptyList())

    /**
     * **流式**解析本 App 的 sessions.json，不把整个文件读成 String。
     *
     * 为什么必须流式：导入官方导出包后这份文件能到 100MB（4510 万字符正文）。
     * `readText()` 先要一个 200MB 的 UTF-16 String，再让 org.json 建一棵同样大的树，
     * 实测启动峰值 371MB —— 512MB 堆勉强够，256MB 堆必 OOM。
     * [android.util.JsonReader] 边读边建，峰值只剩最终对象图（实测约 200MB 以内）。
     *
     * @param onSession 每读完一个会话回调一次（导入时可以边读边合并）
     */
    fun parseSessions(input: java.io.InputStream, onSession: ((Session) -> Unit)? = null): List<Session> {
        val out = ArrayList<Session>()
        val reader = android.util.JsonReader(java.io.InputStreamReader(input, Charsets.UTF_8))
        try {
            when (reader.peek()) {
                android.util.JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        readSession(reader)?.let { out.add(it); onSession?.invoke(it) }
                    }
                    reader.endArray()
                }
                android.util.JsonToken.BEGIN_OBJECT -> {
                    readSession(reader)?.let { out.add(it); onSession?.invoke(it) }
                }
                else -> Unit
            }
        } finally {
            runCatching { reader.close() }
        }
        return out
    }

    private fun readSession(r: android.util.JsonReader): Session? {
        var id = ""
        var title = "新对话"
        var updatedAt = System.currentTimeMillis()
        var pinned = false
        var messages: List<Msg> = emptyList()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "id" -> id = readString(r)
                "title" -> title = readString(r).ifBlank { "新对话" }
                "updatedAt" -> updatedAt = readLong(r, updatedAt)
                "pinned" -> pinned = readBool(r, false)
                "messages" -> messages = readMessages(r)
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (id.isBlank()) return null
        return Session(id = id, title = title, updatedAt = updatedAt, messages = messages, pinned = pinned)
    }

    private fun readMessages(r: android.util.JsonReader): List<Msg> {
        val out = ArrayList<Msg>()
        r.beginArray()
        while (r.hasNext()) out.add(readMessage(r))
        r.endArray()
        return out
    }

    private fun readMessage(r: android.util.JsonReader): Msg {
        var id = System.nanoTime()
        var role = "user"
        var text = ""
        var reasoning = ""
        var error = false
        var stopped = false
        var thinkSeconds = 0
        var hasImages = false
        var images: List<String> = emptyList()
        var files: List<MsgFile> = emptyList()
        var serverToolCalls = ""
        var citations: List<Source> = emptyList()
        var variants: List<MsgVariant> = emptyList()
        var variantIndex = 0
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "id" -> id = readLong(r, id)
                "role" -> role = readString(r).ifBlank { "user" }
                "text" -> text = readString(r)
                "reasoning" -> reasoning = readString(r)
                "error" -> error = readBool(r, false)
                "stopped" -> stopped = readBool(r, false)
                "thinkSeconds" -> thinkSeconds = readLong(r, 0L).toInt()
                "hasImages" -> hasImages = readBool(r, false)
                "images" -> images = readStringArray(r)
                "files" -> files = readFiles(r)
                "serverToolCalls" -> serverToolCalls = readString(r)
                "citations" -> citations = readCitations(r)
                "variants" -> variants = readVariants(r)
                "variantIndex" -> variantIndex = readLong(r, 0L).toInt()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Msg(
            id = id,
            role = role,
            text = text,
            reasoning = reasoning,
            error = error,
            stopped = stopped,
            thinkSeconds = thinkSeconds,
            hasImages = hasImages,
            images = images,
            files = files,
            serverToolCalls = serverToolCalls,
            steps = runCatching {
                com.yuyuyu.deepseek.net.parseCallsArray(JSONArray(serverToolCalls))
            }.getOrElse { emptyList() },
            citations = citations,
            variants = variants,
            variantIndex = if (variants.isEmpty()) 0
            else variantIndex.coerceIn(0, variants.lastIndex),
        )
    }

    private fun readFiles(r: android.util.JsonReader): List<MsgFile> {
        val out = ArrayList<MsgFile>()
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) {
                r.skipValue()
                continue
            }
            var name = ""
            var size = 0L
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "name" -> name = readString(r)
                    "size" -> size = readLong(r, 0L)
                    else -> r.skipValue()
                }
            }
            r.endObject()
            if (name.isNotBlank()) out.add(MsgFile(name = name, size = size))
        }
        r.endArray()
        return out
    }

    private fun readCitations(r: android.util.JsonReader): List<Source> {
        val out = ArrayList<Source>()
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) {
                r.skipValue()
                continue
            }
            var url = ""
            var title = ""
            var siteName = ""
            var siteIcon = ""
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "url" -> url = readString(r)
                    "title" -> title = readString(r)
                    "siteName" -> siteName = readString(r)
                    "siteIcon" -> siteIcon = readString(r)
                    else -> r.skipValue()
                }
            }
            r.endObject()
            out.add(Source(url = url, title = title, siteName = siteName, siteIcon = siteIcon))
        }
        r.endArray()
        return out
    }

    private fun readVariants(r: android.util.JsonReader): List<MsgVariant> {
        val out = ArrayList<MsgVariant>()
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() != android.util.JsonToken.BEGIN_OBJECT) {
                r.skipValue()
                continue
            }
            var text = ""
            var reasoning = ""
            var thinkSeconds = 0
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "text" -> text = readString(r)
                    "reasoning" -> reasoning = readString(r)
                    "thinkSeconds" -> thinkSeconds = readLong(r, 0L).toInt()
                    else -> r.skipValue()
                }
            }
            r.endObject()
            out.add(MsgVariant(text = text, reasoning = reasoning, thinkSeconds = thinkSeconds))
        }
        r.endArray()
        return out
    }

    private fun readStringArray(r: android.util.JsonReader): List<String> {
        val out = ArrayList<String>()
        r.beginArray()
        while (r.hasNext()) {
            val s = readString(r)
            if (s.isNotBlank()) out.add(s)
        }
        r.endArray()
        return out
    }

    private fun readString(r: android.util.JsonReader): String = when (r.peek()) {
        android.util.JsonToken.NULL -> {
            r.nextNull()
            ""
        }
        android.util.JsonToken.STRING, android.util.JsonToken.NUMBER -> r.nextString()
        else -> {
            r.skipValue()
            ""
        }
    }

    private fun readLong(r: android.util.JsonReader, def: Long): Long = when (r.peek()) {
        android.util.JsonToken.NULL -> {
            r.nextNull()
            def
        }
        android.util.JsonToken.NUMBER -> r.nextLong()
        android.util.JsonToken.STRING -> r.nextString().toLongOrNull() ?: def
        else -> {
            r.skipValue()
            def
        }
    }

    private fun readBool(r: android.util.JsonReader, def: Boolean): Boolean = when (r.peek()) {
        android.util.JsonToken.NULL -> {
            r.nextNull()
            def
        }
        android.util.JsonToken.BOOLEAN -> r.nextBoolean()
        else -> {
            r.skipValue()
            def
        }
    }

    fun loadSessions(ctx: Context): List<Session> {
        val f = file(ctx, "sessions.json")
        if (!f.exists()) return emptyList()
        return try {
            f.inputStream().use { parseSessions(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 一个会话 → JSONObject（sessions.json 的数组元素）。 */
    private fun sessionJson(s: Session): JSONObject {
        val ma = JSONArray()
        for (m in s.messages) {
            ma.put(
                JSONObject()
                    .put("id", m.id)
                    .put("role", m.role)
                    .put("text", m.text)
                    .put("reasoning", m.reasoning)
                    .put("error", m.error)
                    .put("stopped", m.stopped)
                    .put("thinkSeconds", m.thinkSeconds)
                    .put("hasImages", m.hasImages)
                    .put("images", JSONArray().also { ia -> for (i in m.images) ia.put(i) })
                    .put("files", JSONArray().also { fa ->
                        for (f in m.files) fa.put(JSONObject().put("name", f.name).put("size", f.size))
                    })
                    .put("serverToolCalls", m.serverToolCalls)
                    .put("variants", JSONArray().also { va ->
                        for (v in m.variants) {
                            va.put(
                                JSONObject()
                                    .put("text", v.text)
                                    .put("reasoning", v.reasoning)
                                    .put("thinkSeconds", v.thinkSeconds)
                            )
                        }
                    })
                    .put("variantIndex", m.variantIndex)
                    .put("citations", JSONArray().also { ca ->
                        for (c in m.citations) {
                            ca.put(JSONObject().put("url", c.url).put("title", c.title)
                                .put("siteName", c.siteName).put("siteIcon", c.siteIcon))
                        }
                    })
            )
        }
        return JSONObject()
            .put("id", s.id)
            .put("title", s.title)
            .put("updatedAt", s.updatedAt)
            .put("pinned", s.pinned)
            .put("messages", ma)
    }

    /** 会话列表 → sessions.json 的 JSON 文本（小数据量 / 兼容旧调用；导出请用 [writeSessionsTo]） */
    fun sessionsJson(list: List<Session>): String {
        val arr = JSONArray()
        for (s in list) arr.put(sessionJson(s))
        return arr.toString()
    }

    /**
     * 把会话列表**流式**写成 JSON 数组。
     *
     * 官方导出包导入后 sessions.json 能到 50~60MB：如果先拼成一个 String 再写，
     * 光这份文本就要 100MB+ 堆内存（String 是 UTF-16），导入后第一次保存就会 OOM。
     * 这里一个会话一个会话地序列化写出，峰值只有「单个会话的 JSON 文本」。
     */
    fun writeSessionsTo(out: java.io.OutputStream, list: List<Session>) {
        val w = java.io.BufferedWriter(
            java.io.OutputStreamWriter(out, Charsets.UTF_8),
            128 * 1024,
        )
        w.write("[")
        for ((i, s) in list.withIndex()) {
            if (i > 0) w.write(",")
            w.write(sessionJson(s).toString())
        }
        w.write("]")
        w.flush()
    }

    /** 导出聊天记录（SAF 给的 OutputStream）：和本机 sessions.json 内容完全一致。 */
    fun exportSessions(out: java.io.OutputStream, list: List<Session>) = writeSessionsTo(out, list)

    /**
     * 保存 sessions.json：先写 .tmp 再 rename。
     * 导入 50MB 级别数据时写盘要一两秒，中途被系统杀掉也不会把原文件写坏。
     */
    fun saveSessions(ctx: Context, list: List<Session>) {
        val dir = ctx.filesDir
        val dst = File(dir, "sessions.json")
        val tmp = File(dir, "sessions.json.tmp")
        try {
            tmp.outputStream().buffered(128 * 1024).use { writeSessionsTo(it, list) }
            if (!tmp.renameTo(dst)) {
                dst.delete()
                if (!tmp.renameTo(dst)) {
                    // 极端情况下 rename 失败：退回流式覆盖（内容仍然完整，且不把整份读进内存）
                    tmp.inputStream().use { ins -> dst.outputStream().use { ins.copyTo(it) } }
                    tmp.delete()
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    // =================================================================================
    //  拆分存储（索引 + 每个会话一个文件）—— 让启动只读几百 KB 索引，打开会话再读消息
    // =================================================================================

    private fun sessionsDir(ctx: Context): File =
        File(ctx.filesDir, "sessions").also { runCatching { it.mkdirs() } }

    private fun indexFile(ctx: Context): File = File(sessionsDir(ctx), "index.json")

    /** 会话 id → 安全文件名（id 里可能有 ':'、'/' 等；加哈希避免清洗后撞名）。 */
    private fun sessionFile(ctx: Context, id: String): File {
        val safe = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val hash = Integer.toHexString(id.hashCode())
        return File(sessionsDir(ctx), "$safe-$hash.json")
    }

    private fun legacySessionsFile(ctx: Context): File = File(ctx.filesDir, "sessions.json")

    /** 旧版单文件 → 拆分存储（只在第一次启动执行；原文件保留为 sessions.json.bak）。 */
    fun migrateToSplit(ctx: Context) {
        val idx = indexFile(ctx)
        val legacy = legacySessionsFile(ctx)
        if (idx.exists()) return
        if (!legacy.exists()) {
            runCatching { idx.writeText("[]") }
            return
        }
        val list = runCatching { legacy.inputStream().use { parseSessions(it) } }.getOrDefault(emptyList())
        for (s in list) runCatching { saveSessionFile(ctx, s) }
        saveSplitIndex(ctx, list)
        runCatching { legacy.renameTo(File(ctx.filesDir, "sessions.json.bak")) }
    }

    /** 只读索引：会话标题/置顶/更新时间/条数，不带消息。 */
    fun loadSplitIndex(ctx: Context): List<Session> {
        val f = indexFile(ctx)
        if (!f.exists()) return emptyList()
        val arr = runCatching { JSONArray(f.readText()) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Session>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isEmpty()) continue
            out.add(
                Session(
                    id = id,
                    title = o.optString("title", "新对话").let { if (it == "null") "新对话" else it },
                    updatedAt = o.optLong("updatedAt", 0L),
                    messages = emptyList(),
                    pinned = o.optBoolean("pinned", false),
                    loaded = false,
                    msgCount = o.optInt("count", 0),
                )
            )
        }
        return out
    }

    /** 读单个会话的消息（文件是「只有一个元素的数组」，复用同一套流式解析）。 */
    fun loadSessionMessages(ctx: Context, id: String): List<Msg> {
        val f = sessionFile(ctx, id)
        if (!f.exists()) return emptyList()
        return runCatching {
            f.inputStream().use { parseSessions(it) }.firstOrNull()?.messages ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun loadAllMessages(ctx: Context, list: List<Session>): List<Session> =
        list.map { s ->
            if (s.loaded) s else s.copy(messages = loadSessionMessages(ctx, s.id), loaded = true)
        }

    /** 写单个会话文件（先写 .tmp 再 rename）。 */
    fun saveSessionFile(ctx: Context, s: Session) {
        val dst = sessionFile(ctx, s.id)
        val tmp = File(sessionsDir(ctx), dst.name + ".tmp")
        try {
            tmp.outputStream().buffered(64 * 1024).use { out ->
                val w = java.io.BufferedWriter(java.io.OutputStreamWriter(out, Charsets.UTF_8), 64 * 1024)
                w.write("[")
                w.write(sessionJson(s).toString())
                w.write("]")
                w.flush()
            }
            if (!tmp.renameTo(dst)) {
                dst.delete()
                if (!tmp.renameTo(dst)) {
                    tmp.inputStream().use { ins -> dst.outputStream().use { ins.copyTo(it) } }
                    tmp.delete()
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    /** 写索引（很小，直接覆盖）。 */
    fun saveSplitIndex(ctx: Context, list: List<Session>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("updatedAt", s.updatedAt)
                    .put("pinned", s.pinned)
                    .put("count", if (s.loaded) s.messages.size else s.msgCount)
            )
        }
        val f = indexFile(ctx)
        val tmp = File(f.parentFile, f.name + ".tmp")
        runCatching {
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }

    fun deleteSessionFile(ctx: Context, id: String) {
        runCatching { sessionFile(ctx, id).delete() }
    }

    /** 删除全部会话文件（「删除所有历史对话」用）。 */
    fun clearSplitStorage(ctx: Context) {
        val dir = sessionsDir(ctx)
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        runCatching { indexFile(ctx).writeText("[]") }
    }

    /**
     * 全量写盘（导入 / 首次保存）：每个**已加载**会话一个文件 + 索引；顺手删掉孤立文件。
     * 未加载（懒加载还没读消息）的会话不会被空消息覆盖，它的旧文件原样保留。
     */
    fun saveAllSplit(ctx: Context, list: List<Session>) {
        val dir = sessionsDir(ctx)
        val keep = HashSet<String>()
        for (s in list) {
            if (s.loaded) saveSessionFile(ctx, s)
            keep.add(sessionFile(ctx, s.id).name)
        }
        runCatching {
            dir.listFiles()?.forEach { f ->
                if (f.name != "index.json" && f.name !in keep && !f.name.endsWith(".tmp")) f.delete()
            }
        }
        saveSplitIndex(ctx, list)
    }

    /** 导出聊天记录：从拆分存储按会话流式写出（未加载的消息现场读文件，不把整库读进内存）。 */
    fun exportSplitSessions(ctx: Context, out: java.io.OutputStream, list: List<Session>) {
        val w = java.io.BufferedWriter(java.io.OutputStreamWriter(out, Charsets.UTF_8), 128 * 1024)
        w.write("[")
        for ((i, s) in list.withIndex()) {
            if (i > 0) w.write(",")
            val full = if (s.loaded) s else s.copy(messages = loadSessionMessages(ctx, s.id), loaded = true)
            w.write(sessionJson(full).toString())
        }
        w.write("]")
        w.flush()
    }
}
