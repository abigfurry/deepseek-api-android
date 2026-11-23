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
     * 把 sessions.json 的 JSON 文本解析成会话列表。
     * loadSessions 与「导入聊天记录」共用这一份解析逻辑，保证导入的文件格式和磁盘上完全一致。
     */
    fun parseSessions(json: String): List<Session> {
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<Session>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val msgs = ArrayList<Msg>()
                val ma = o.optJSONArray("messages") ?: JSONArray()
                for (j in 0 until ma.length()) {
                    val m = ma.optJSONObject(j) ?: continue
                    // 分支候选：老 JSON 里没有，读出空列表即可（此时直接看 text 字段）
                    val variants = runCatching {
                        val va = m.optJSONArray("variants") ?: org.json.JSONArray()
                        (0 until va.length()).mapNotNull { vi ->
                            va.optJSONObject(vi)?.let {
                                MsgVariant(
                                    text = str(it, "text"),
                                    reasoning = str(it, "reasoning"),
                                    thinkSeconds = it.optInt("thinkSeconds", 0),
                                )
                            }
                        }
                    }.getOrElse { emptyList() }
                    msgs.add(
                        Msg(
                            id = m.optLong("id", System.nanoTime()),
                            role = str(m, "role", "user"),
                            text = str(m, "text"),
                            reasoning = str(m, "reasoning"),
                            error = m.optBoolean("error", false),
                            thinkSeconds = m.optInt("thinkSeconds", 0),
                            hasImages = m.optBoolean("hasImages", false),
                            files = runCatching {
                                val fa = m.optJSONArray("files") ?: org.json.JSONArray()
                                (0 until fa.length()).mapNotNull { fi ->
                                    fa.optJSONObject(fi)?.let {
                                        MsgFile(name = str(it, "name"), size = it.optLong("size", 0L))
                                    }
                                }
                            }.getOrElse { emptyList() },
                            images = runCatching {
                                val ia = m.optJSONArray("images") ?: org.json.JSONArray()
                                (0 until ia.length()).mapNotNull { ii -> ia.optString(ii).takeIf { it.isNotBlank() } }
                            }.getOrElse { emptyList() },
                            serverToolCalls = str(m, "serverToolCalls"),
                            steps = runCatching {
                                com.yuyuyu.deepseek.net.parseCallsArray(org.json.JSONArray(str(m, "serverToolCalls")))
                            }.getOrElse { emptyList() },
                            citations = runCatching {
                                val ca = m.optJSONArray("citations") ?: org.json.JSONArray()
                                (0 until ca.length()).mapNotNull { ci ->
                                    ca.optJSONObject(ci)?.let {
                                        Source(url = str(it, "url"), title = str(it, "title"), siteName = str(it, "siteName"), siteIcon = str(it, "siteIcon"))
                                    }
                                }
                            }.getOrElse { emptyList() },
                            variants = variants,
                            variantIndex = if (variants.isEmpty()) 0
                            else m.optInt("variantIndex", 0).coerceIn(0, variants.lastIndex),
                        )
                    )
                }
                out.add(
                    Session(
                        id = o.optString("id", System.nanoTime().toString()),
                        title = str(o, "title", "新对话"),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        messages = msgs,
                        pinned = o.optBoolean("pinned", false),
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun loadSessions(ctx: Context): List<Session> {
        val f = file(ctx, "sessions.json")
        if (!f.exists()) return emptyList()
        return try {
            parseSessions(f.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 会话列表 → sessions.json 的 JSON 文本（「导出聊天记录」直接写这份文本） */
    fun sessionsJson(list: List<Session>): String {
        val arr = JSONArray()
        for (s in list) {
            val ma = JSONArray()
            for (m in s.messages) {
                ma.put(
                    JSONObject()
                        .put("id", m.id)
                        .put("role", m.role)
                        .put("text", m.text)
                        .put("reasoning", m.reasoning)
                        .put("error", m.error)
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
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("updatedAt", s.updatedAt)
                    .put("pinned", s.pinned)
                    .put("messages", ma)
            )
        }
        return arr.toString()
    }

    fun saveSessions(ctx: Context, list: List<Session>) {
        file(ctx, "sessions.json").writeText(sessionsJson(list))
    }
}
