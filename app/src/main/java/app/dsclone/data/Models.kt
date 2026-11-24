package com.yuyuyu.deepseek.data

/**
 * 一条联网引用/搜索来源（官方 MessageSearchResult 的裁剪版）。
 * 官方 JSON 字段：url / title / snippet / cite_index / published_at / site_name / site_icon /
 * query_indexes / provider；引用（annotation.url_citation）只有 url/title，
 * 其余字段由 WebToolStream 从同一次响应的 search sources 回填（见 net/WebToolStream.kt）。
 *
 * 注意：snippet/publishTime 是 2026-11 补的字段，Store 里旧 sessions.json 没有它们（读回默认空），
 * 属于「只影响重启后来源列表的摘要/时间展示」的可接受降级，不影响 URL 打开。
 */
data class Source(
    val url: String = "",
    val title: String = "",
    val siteName: String = "",
    val siteIcon: String = "",
    /** 搜索结果的摘要（官方 MessageSearchResult.snippet；url_citation 没有） */
    val snippet: String = "",
    /** 发布时间原文（官方 MessageSearchResult.published_at，可能是 "2026-11-20" 或 RFC3339） */
    val publishTime: String = "",
)

/**
 * 联网工具的一步（官方思考链里的「已搜索到 N 个网页 / 已浏览 N 个页面」节点）。
 *
 * 由 net/WebToolStream.kt 从 server_tool_calls 生成：官方把连续同类型的工具片段合并成
 * 一个 merged 片段（smali/vbb.smali 的合并器），这里同样合并——所以一个 Step 可能对应多次
 * 同 action 的调用。字段约定：
 *  - action: search / open_page / find_in_page
 *  - queries: search 合并后的查询词（去重、按出现顺序）
 *  - count: 去重后的网页/页面数（url 去掉 #fragment 后计数；失败为 0）
 *  - sources: 该步搜索/浏览到的网页（与 Msg.citations 相互独立，不重复计数）
 *  - reasoning: 目前恒为空——官方把思考文本作为独立的 THINK 片段（正文段落，不是图标行），
 *    本 App 的对应物是 Msg.reasoning（见 WebToolStream 类注释）
 *  - status: completed / failed / running
 */
data class Step(
    val kind: String = "",
    val action: String = "",
    val queries: List<String> = emptyList(),
    val url: String = "",
    val count: Int = 0,
    val sources: List<Source> = emptyList(),
    val reasoning: String = "",
    val status: String = "",
)

/** 用户消息里附带的文件（不是图片）：官方气泡里显示文件名 + 大小 */
data class MsgFile(
    val name: String = "",
    val size: Long = 0L,
)

/**
 * 一条 assistant 消息的一个「候选回答」。
 * 官方答完后点「重新生成」不会丢旧回答，而是把新答案追加成下一个候选，
 * 回答下方出现 `‹ n/N ›` 让用户左右切换。
 */
data class MsgVariant(
    val text: String = "",
    val reasoning: String = "",
    val thinkSeconds: Int = 0,
)

data class Msg(
    val id: Long = System.nanoTime(),
    val role: String,
    val text: String = "",
    val reasoning: String = "",
    val error: Boolean = false,
    /** 用户点了「停止生成」中断的回答（可显示「继续生成」） */
    val stopped: Boolean = false,
    val streaming: Boolean = false,
    val thinkSeconds: Int = 0,
    /** 这条 user 消息是否带图片，用来显示「正在分析图片」 */
    val hasImages: Boolean = false,
    /** 这条消息里的图片（content:// 或 file:// URI 字符串），存在气泡里直接显示 */
    val images: List<String> = emptyList(),
    /** 这条消息里的文件附件 */
    val files: List<MsgFile> = emptyList(),
    /** 联网/阅读步骤（官方思考链里的「搜索到 N 个网页」那一行） */
    val steps: List<Step> = emptyList(),
    val serverToolCalls: String = "",
    val citations: List<Source> = emptyList(),
    /**
     * 重新生成出来的多个候选回答（官方回答下方的 `‹ n/N ›`）。
     * 老数据没有这个字段：此时 variants 为空、variantIndex 为 0，直接用 text/reasoning/thinkSeconds。
     */
    val variants: List<MsgVariant> = emptyList(),
    /** 当前显示的候选序号，text/reasoning/thinkSeconds 始终等于 variants[variantIndex] */
    val variantIndex: Int = 0,
)

/** 当前 text/reasoning/thinkSeconds 写回 variants[variantIndex]（流式结束/出错时调用，保持两者一致） */
fun Msg.syncActiveVariant(): Msg {
    if (variants.isEmpty()) return this
    val i = variantIndex.coerceIn(0, variants.lastIndex)
    val list = variants.toMutableList()
    list[i] = MsgVariant(text = text, reasoning = reasoning, thinkSeconds = thinkSeconds)
    return copy(variants = list, variantIndex = i)
}

/** 纯本地切换候选：只把第 index 个候选写回 text/reasoning，不发任何请求 */
fun Msg.switchVariant(index: Int): Msg {
    val v = variants.getOrNull(index) ?: return this
    return copy(
        text = v.text,
        reasoning = v.reasoning,
        thinkSeconds = v.thinkSeconds,
        variantIndex = index,
    )
}

data class Session(
    val id: String,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<Msg> = emptyList(),
    /** 官方侧边栏的「置顶」分组 */
    val pinned: Boolean = false,
    /**
     * 消息是否已经加载进内存。启动只读索引时是 false（秒开），
     * 打开该会话 / 后台预加载后才变 true。不写进磁盘。
     */
    val loaded: Boolean = true,
    /** 索引里记录的消息条数（未加载时用于统计；已加载时 = messages.size） */
    val msgCount: Int = 0,
)

/**
 * 旧版 provider 视图（ui/ModelSheet.kt、ui/SettingsSub.kt、ui/ContextSheet.kt 还在用它）。
 * 现在由 data/Providers.kt 的 [ProviderPresets] 派生，不再单独维护一份硬编码表：
 *  - name / baseUrl / models(=preset.fallbackModels) 与预设表保持一致；
 *  - 19 家 provider 会自动出现在旧 UI 的列表里，不需要改旧 UI 也能用到新的服务商。
 * 新代码请直接用 [ProviderPreset] / [presetOf]。
 */
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val models: List<String>,
)

val Providers: List<Provider> = ProviderPresets.map { p ->
    Provider(id = p.id, name = p.name, baseUrl = p.baseUrl, models = p.fallbackModels)
}

/**
 * 官方「设置」页的多选项数据（文案取自官方 2.9.0 strings.xml）：
 *  app_color_scheme_system/light/dark = 系统/浅色/深色
 *  app_language_system = 系统；其余为各语言自称
 *  voice_switch_bottom_sheet_title = 朗读音色
 */
data class AppearanceOption(val id: String, val label: String)

val AppearanceOptions = listOf(
    AppearanceOption("system", "系统"),
    AppearanceOption("light", "浅色"),
    AppearanceOption("dark", "深色"),
)

fun appearanceLabel(id: String): String =
    AppearanceOptions.firstOrNull { it.id == id }?.label ?: "系统"

data class LanguageOption(val id: String, val label: String)

/** 官方语言选择弹窗里的语言自称列表（顺序与官方一致：系统在最前） */
val LanguageOptions = listOf(
    LanguageOption("system", "系统"),
    LanguageOption("zh-Hans", "中文 (简体中文)"),
    LanguageOption("en", "English"),
    LanguageOption("ja", "日本語"),
    LanguageOption("ko", "한국어"),
    LanguageOption("es", "Español"),
    LanguageOption("fr", "Français"),
    LanguageOption("de", "Deutsch"),
    LanguageOption("ru", "Русский"),
    LanguageOption("ar", "العربية"),
)

fun languageLabel(id: String): String =
    LanguageOptions.firstOrNull { it.id == id }?.label ?: "中文 (简体中文)"

data class VoiceOption(val id: String, val name: String, val desc: String)

/**
 * 官方 /v1/tools/tts 只支持两个 voice_id：stella（女声，官方默认）/ tide（男声）。
 * 设置页「朗读音色」卡片按这里的顺序竖排展示。
 */
val VoiceOptions = listOf(
    VoiceOption("stella", "星光", "温暖女声"),
    VoiceOption("tide", "潮汐", "低沉男声"),
)

data class Settings(
    val providerId: String = "deepseek",
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-flash",
    val thinking: Boolean = false,
    val search: Boolean = false,
    val keys: Map<String, String> = emptyMap(),
    // DeepSeek Chat Completions 的请求参数，设置页里都能调
    val temperature: Float = 1f,
    val topP: Float = 1f,
    val maxTokens: Int = 0,
    val stop: String = "",
    val jsonMode: Boolean = false,
    val systemPrompt: String = "",
    val reasoningEffort: String = "high",
    val titleEnabled: Boolean = true,
    val safety: Boolean = true,
    val userId: String = "",
    /** 本机资料：昵称 / 头像文件路径（只存本地，不进任何请求） */
    val nickName: String = "",
    val avatarPath: String = "",
    val includeUsage: Boolean = true,
    // ==== 官方「设置」页新增项（老数据用默认值兜底）====
    /** 外观：system / light / dark（官方 app_color_scheme_*） */
    val appearance: String = "system",
    /** 字体大小档位 0..4，2 = 标准（官方 default_font_size） */
    val fontSizeLevel: Int = 2,
    /** 字体大小-跟随系统（官方 font_size 页里的开关） */
    val followSystemFont: Boolean = true,
    /** 个性化-思考内容自动折叠（官方 thinking_auto_fold，默认开） */
    val autoFoldThinking: Boolean = true,
    /** 自定义人格：开启后所有对话都带这段系统提示词（在数据管理里设置） */
    val personaEnabled: Boolean = false,
    val personaPrompt: String = "",
    /** 数据管理-数据用于优化体验（官方 profile_imporve_model，默认开） */
    val trainOnData: Boolean = true,
    /**
     * 数据管理：启动时是否预加载**全部**历史消息。
     * false（默认）= 只读索引秒开，打开会话/搜索时再加载；
     * true = 和旧版一样，启动时把全部消息读进内存（搜索最快，但历史大时进入要等几秒）。
     */
    val preloadHistory: Boolean = false,
    /** 语言（官方 app_language，默认跟随系统） */
    val appLanguage: String = "system",
    /**
     * 朗读音色：官方 /v1/tools/tts 的 voice_id，只有 stella（女声，默认）/ tide（男声）。
     * 老 settings.json 里存的是 voice 字段，Store 读取时会自动兼容。
     */
    val voiceId: String = "stella",
    // ==== 多 provider（19 家）新增项：老 settings.json 用默认值兜底 ====
    /**
     * 每家 provider 独立覆盖的 baseUrl（key = providerId），空 = 用预设默认值。
     * 旧版本只有一个全局 [baseUrl]，Store.loadSettings 会把它迁移进这个 map（见 Store 的迁移逻辑）。
     */
    val baseUrls: Map<String, String> = emptyMap(),
    /** 每家 provider「最近一次拉取成功」的模型列表（缓存）；空 = 用预设 fallbackModels */
    val providerModels: Map<String, List<String>> = emptyMap(),
    /** 每家 provider 手工填的模型名，展示时永远置顶（与拉取缓存分开存） */
    val customModels: Map<String, List<String>> = emptyMap(),
    /** 每家 provider 的余额展示缓存：json {"t":时间戳,"text":"¥110.00","detail":"..."} */
    val balanceCache: Map<String, String> = emptyMap(),
    /**
     * 每家 provider 的「接口类型」（key = providerId）：
     *  - "chat"（默认，缺失也按 chat）：POST {baseUrl}/chat/completions
     *  - "responses"：POST {baseUrl}/responses（OpenAI Responses API 风格）
     * 手写 JSON，Store.loadSettings / saveSettings 两处都要维护。
     */
    val apiStyles: Map<String, String> = emptyMap(),
) {
    val apiKey: String
        get() = keys[providerId] ?: ""

    /** 接口类型：providerId → "chat" | "responses"（没有记录 = "chat"） */
    fun apiStyle(providerId: String): String = apiStyles[providerId] ?: "chat"

    /** 旧 UI 用的静态 provider 视图（由预设表派生，见 [Providers]） */
    val provider: Provider
        get() = Providers.firstOrNull { it.id == providerId } ?: Providers.first()

    /** 新代码用：当前 provider 的完整预设（鉴权/路径/能力位/余额接口） */
    val preset: ProviderPreset
        get() = presetOf(providerId)

    /**
     * 当前生效的 baseUrl：用户覆盖 > 旧全局 baseUrl（仅当用户改过、与预设默认不同）> 预设默认。
     * 这样老 settings.json 里手填的代理地址不会丢，同时预设表更新（比如换了新默认地址）也能生效。
     */
    val effectiveBaseUrl: String
        get() {
            val override = baseUrls[providerId]?.trim().orEmpty()
            if (override.isNotEmpty()) return override
            val def = preset.baseUrl
            val legacy = baseUrl.trim()
            if (legacy.isEmpty()) return def
            if (legacy != def) return legacy
            return def
        }

    /** 当前 provider 的模型候选：手填模型置顶 → 拉取缓存 → 预设兜底，去重 */
    val models: List<String>
        get() = mergedModels(this, providerId)

    fun withKey(key: String): Settings = copy(keys = keys + (providerId to key.trim()))
}

fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    var cjk = 0
    var other = 0
    for (ch in text) {
        if (ch.code in 0x4E00..0x9FFF || ch.code in 0x3040..0x30FF || ch.code in 0xAC00..0xD7AF) {
            cjk++
        } else {
            other++
        }
    }
    return cjk + other / 4 + 1
}
