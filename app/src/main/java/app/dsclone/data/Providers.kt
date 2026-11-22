package com.yuyuyu.deepseek.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/*
 * 多 API 提供商（19 家）的预设表 + 模型列表拉取 + 余额查询。
 *
 * 约定（与 _work/PROVIDER-SPEC.md §1 一致）：
 *  - baseUrl 是「根地址」，不含 /chat/completions；聊天端点 = baseUrl + chatPath。
 *  - modelsPath 相对 baseUrl；以 http(s) 开头的才是绝对地址（Ollama 的 /api/tags）。
 *  - 余额端点相对「origin」= scheme://host[:port]（DeepSeek 的 /user/balance 不在 /v1 下）；
 *    OpenRouter 用绝对 URL。
 *  - 本文件不做任何 UI，也不持有 Android Context；网络一律 OkHttp + org.json。
 */

/** 鉴权方式：Bearer / x-api-key + anthropic-version / URL ?key= / 无鉴权 */
enum class AuthStyle { Bearer, AnthropicKey, QueryKey, None }

/** 模型列表响应的形状；Tolerant = 上面几种都试一遍 */
enum class ModelsKind { OpenAi, Anthropic, Gemini, Ollama, Tolerant }

/** 余额接口的响应形状；None = 该家没有公开的余额查询接口 */
enum class BalanceKind { DeepSeek, OpenRouterKey, OpenRouterCredits, SiliconFlow, Moonshot, None }

/**
 * 余额端点。
 * path 以 http(s) 开头 = 绝对 URL；否则拼在 baseUrl 的 origin 后面。
 * needsAdminKey = 该接口需要 management key（OpenRouter /credits），普通推理 key 会 401。
 */
data class BalanceSpec(
    val path: String,
    val kind: BalanceKind,
    val needsAdminKey: Boolean = false,
)

/**
 * 一家提供商的预设（纯数据）。
 * 预设表写在代码常量里（NextChat / Cherry Studio / Lobe 都是这么做的）：
 * 解析器分支、默认模型、文档链接放 JSON 资源反而难维护。
 */
data class ProviderPreset(
    val id: String,
    val name: String,
    /** 根地址，不带 /chat/completions */
    val baseUrl: String,
    /** 聊天路径，相对 baseUrl（全表都是 /chat/completions，走各家 OpenAI 兼容层） */
    val chatPath: String = "/chat/completions",
    val auth: AuthStyle = AuthStyle.Bearer,
    /** 模型列表路径（相对 baseUrl），null = 不提供模型列表接口 */
    val modelsPath: String? = "/models",
    val modelsKind: ModelsKind = ModelsKind.OpenAi,
    val balance: BalanceSpec? = null,
    val supportsTemperature: Boolean = true,
    val supportsTopP: Boolean = true,
    val supportsStreamUsage: Boolean = true,
    val supportsReasoningEffort: Boolean = false,
    /** 额外请求头（OpenRouter 的 HTTP-Referer / X-Title、Anthropic 的 anthropic-version） */
    val extraHeaders: Map<String, String> = emptyMap(),
    /** 拉取失败 / 无接口时的兜底模型目录（也是老 UI 的静态模型列表） */
    val fallbackModels: List<String> = emptyList(),
    val keyUrl: String = "",
    val docsUrl: String = "",
    /** 本地 provider（Ollama / LM Studio）：不做余额、不校验 key 格式 */
    val local: Boolean = false,
    /** 是否 OpenAI 兼容；false 时只能提示「仅基础对话」 */
    val openAiCompatible: Boolean = true,
)

const val DefaultProviderId = "deepseek"
const val CustomProviderId = "custom"

/** 单家 provider 的模型缓存上限：避免 settings.json 被 OpenRouter 的 300+ 模型撑爆 */
const val MaxCachedModels = 500

/** 19 家预设表（顺序即设置页 / 选择器里的展示顺序） */
val ProviderPresets: List<ProviderPreset> = listOf(
    ProviderPreset(
        id = DefaultProviderId,
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        chatPath = "/chat/completions",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        modelsKind = ModelsKind.OpenAi,
        balance = BalanceSpec("/user/balance", BalanceKind.DeepSeek),
        supportsReasoningEffort = true,
        // 与旧 Providers 里 deepseek 的静态列表保持完全一致（默认模型 deepseek-flash 排第一）
        fallbackModels = listOf("deepseek-flash", "deepseek-chat", "deepseek-reasoner"),
        keyUrl = "https://platform.deepseek.com/api_keys",
        docsUrl = "https://api-docs.deepseek.com/",
    ),
    ProviderPreset(
        id = "openai",
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        supportsReasoningEffort = true,
        fallbackModels = listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4.1"),
        keyUrl = "https://platform.openai.com/api-keys",
        docsUrl = "https://platform.openai.com/docs/api-reference",
    ),
    ProviderPreset(
        id = "anthropic",
        name = "Anthropic Claude",
        baseUrl = "https://api.anthropic.com/v1",
        auth = AuthStyle.AnthropicKey,
        modelsPath = "/models",
        modelsKind = ModelsKind.Anthropic,
        extraHeaders = mapOf("anthropic-version" to "2023-06-01"),
        fallbackModels = listOf("claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5"),
        keyUrl = "https://console.anthropic.com/settings/keys",
        docsUrl = "https://platform.claude.com/docs/zh-CN/cli-sdks-libraries/libraries/openai-sdk",
    ),
    ProviderPreset(
        id = "gemini",
        name = "Google Gemini",
        // 官方 OpenAI 兼容层；鉴权走 Bearer（原生接口才用 ?key=，这里统一走兼容层）
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        modelsKind = ModelsKind.OpenAi,
        fallbackModels = listOf("gemini-2.5-pro", "gemini-2.5-flash"),
        keyUrl = "https://aistudio.google.com/apikey",
        docsUrl = "https://ai.google.dev/gemini-api/docs/openai",
    ),
    ProviderPreset(
        id = "openrouter",
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        balance = BalanceSpec("https://openrouter.ai/api/v1/key", BalanceKind.OpenRouterKey),
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://github.com/",
            "X-Title" to "DSClone",
        ),
        fallbackModels = listOf("openai/gpt-4o-mini", "anthropic/claude-sonnet-4.5"),
        keyUrl = "https://openrouter.ai/keys",
        docsUrl = "https://openrouter.ai/docs",
    ),
    ProviderPreset(
        id = "siliconflow",
        name = "硅基流动 SiliconFlow",
        baseUrl = "https://api.siliconflow.cn/v1",
        auth = AuthStyle.Bearer,
        modelsPath = "/models?sub_type=chat",
        modelsKind = ModelsKind.OpenAi,
        balance = BalanceSpec("/v1/user/info", BalanceKind.SiliconFlow),
        fallbackModels = listOf("Qwen/Qwen3-8B", "deepseek-ai/DeepSeek-V3"),
        keyUrl = "https://cloud.siliconflow.cn/account/ak",
        docsUrl = "https://docs.siliconflow.com/",
    ),
    ProviderPreset(
        id = "moonshot",
        name = "月之暗面 Kimi",
        baseUrl = "https://api.moonshot.cn/v1",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        balance = BalanceSpec("/v1/users/me/balance", BalanceKind.Moonshot),
        fallbackModels = listOf("moonshot-v1-8k", "moonshot-v1-32k"),
        keyUrl = "https://platform.moonshot.cn/console/api-keys",
        docsUrl = "https://platform.kimi.com/docs/",
    ),
    ProviderPreset(
        id = "zhipu",
        name = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        // 有的端点回 data[].id，有的回 models[].slug，用宽容解析
        modelsKind = ModelsKind.Tolerant,
        fallbackModels = listOf("glm-4.6", "glm-4-plus", "glm-4-flash"),
        keyUrl = "https://open.bigmodel.cn/usercenter/apikeys",
        docsUrl = "https://open.bigmodel.cn/dev/api",
    ),
    ProviderPreset(
        id = "dashscope",
        name = "阿里云百炼（通义千问）",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        modelsKind = ModelsKind.Tolerant,
        fallbackModels = listOf("qwen-plus", "qwen-max", "qwen-turbo"),
        keyUrl = "https://bailian.console.aliyun.com/",
        docsUrl = "https://help.aliyun.com/zh/model-studio/",
    ),
    ProviderPreset(
        id = "ark",
        name = "字节火山方舟 Ark",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        modelsKind = ModelsKind.Tolerant,
        fallbackModels = listOf("doubao-seed-1-6-250615", "doubao-1-5-pro-32k-250115"),
        keyUrl = "https://console.volcengine.com/ark",
        docsUrl = "https://docs.volcengine.com/docs/ark",
    ),
    ProviderPreset(
        id = "qianfan",
        name = "百度千帆",
        baseUrl = "https://qianfan.baidubce.com/v2",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        // 结构未完全公开：data[] / result[] 都试
        modelsKind = ModelsKind.Tolerant,
        fallbackModels = listOf("ernie-4.5-turbo-128k", "ernie-4.0-8k"),
        keyUrl = "https://console.bce.baidu.com/iam/#/iam/apikey/list",
        docsUrl = "https://cloud.baidu.com/doc/qianfan-api/",
    ),
    ProviderPreset(
        id = "hunyuan",
        name = "腾讯混元",
        baseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        modelsKind = ModelsKind.Tolerant,
        fallbackModels = listOf("hunyuan-turbos-latest", "hunyuan-large"),
        keyUrl = "https://console.cloud.tencent.com/hunyuan/api-key",
        docsUrl = "https://cloud.tencent.com/document/product/1729",
    ),
    ProviderPreset(
        id = "groq",
        name = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        fallbackModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
        keyUrl = "https://console.groq.com/keys",
        docsUrl = "https://console.groq.com/docs",
    ),
    ProviderPreset(
        id = "mistral",
        name = "Mistral",
        baseUrl = "https://api.mistral.ai/v1",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        fallbackModels = listOf("mistral-large-latest", "mistral-small-latest"),
        keyUrl = "https://console.mistral.ai/api-keys/",
        docsUrl = "https://docs.mistral.ai/",
    ),
    ProviderPreset(
        id = "xai",
        name = "xAI Grok",
        baseUrl = "https://api.x.ai/v1",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        fallbackModels = listOf("grok-4", "grok-3-mini"),
        keyUrl = "https://console.x.ai/",
        docsUrl = "https://docs.x.ai/",
    ),
    ProviderPreset(
        id = "ollama",
        name = "Ollama（本地）",
        baseUrl = "http://127.0.0.1:11434/v1",
        auth = AuthStyle.None,
        // 原生 tags 接口最稳（新版 /v1/models 不一定在）
        modelsPath = "http://127.0.0.1:11434/api/tags",
        modelsKind = ModelsKind.Ollama,
        local = true,
        fallbackModels = listOf("llama3.2", "qwen2.5"),
    ),
    ProviderPreset(
        id = "lmstudio",
        name = "LM Studio（本地）",
        baseUrl = "http://127.0.0.1:1234/v1",
        auth = AuthStyle.None,
        modelsPath = "/models",
        modelsKind = ModelsKind.OpenAi,
        local = true,
        fallbackModels = listOf("local-model"),
    ),
    ProviderPreset(
        id = "together",
        name = "Together AI",
        baseUrl = "https://api.together.xyz/v1",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        fallbackModels = listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3"),
        keyUrl = "https://api.together.ai/settings/api-keys",
        docsUrl = "https://docs.together.ai/",
    ),
    ProviderPreset(
        id = CustomProviderId,
        name = "自定义",
        baseUrl = "",
        chatPath = "/chat/completions",
        auth = AuthStyle.Bearer,
        modelsPath = "/models",
        modelsKind = ModelsKind.Tolerant,
        fallbackModels = emptyList(),
    ),
)

/** 按 id 取预设；未知 id 回落到「自定义」（而不是 DeepSeek，避免误走 DeepSeek 专属参数） */
fun presetOf(id: String): ProviderPreset =
    ProviderPresets.firstOrNull { it.id == id }
        ?: ProviderPresets.firstOrNull { it.id == CustomProviderId }
        ?: ProviderPresets.first()

/**
 * 这家 provider 的模型候选（UI 选择器直接渲染这个）：
 *   1. 手工填的模型名（customModels）永远置顶；
 *   2. 然后是最近一次拉取成功的缓存（providerModels）；
 *   3. 最后是预设的兜底目录。
 * 拉取失败不会动 [Settings.providerModels]，所以这里的顺序天然满足「失败不清空」。
 */
fun mergedModels(settings: Settings, providerId: String = settings.providerId): List<String> {
    val out = LinkedHashSet<String>()
    fun addAll(list: List<String>?) {
        for (m in list.orEmpty()) {
            val s = m.trim()
            if (s.isNotEmpty()) out.add(s)
        }
    }
    addAll(settings.customModels[providerId])
    addAll(settings.providerModels[providerId])
    addAll(presetOf(providerId).fallbackModels)
    return out.toList()
}

/** 远端模型的一项 */
data class RemoteModel(
    val id: String,
    val displayName: String = "",
    val contextLength: Int = 0,
)

/** 拉模型结果：error == null 表示成功（此时 models 非空） */
data class ModelsResult(
    val models: List<RemoteModel> = emptyList(),
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

/** 余额查询的三态（NotSupported 是正常形态，UI 显示「—」而不是报错） */
sealed class BalanceResult {
    /** amount + 币种 + 明细行（如 赠送/充值） */
    data class Money(
        val amount: Double,
        val currency: String = "CNY",
        val lines: List<Pair<String, String>> = emptyList(),
    ) : BalanceResult()

    /** 该服务商没有公开的余额查询接口（不是失败，不要重试） */
    data class NotSupported(val hint: String = "该提供商无余额接口") : BalanceResult()

    /** 网络/鉴权/解析失败 */
    data class Failed(val message: String) : BalanceResult()
}

/** settings.json 里 balanceCache 的单条记录 */
data class BalanceCacheEntry(
    val timeMillis: Long,
    val text: String,
    val detail: String = "",
)

/**
 * 模型列表 / 余额查询的网络实现（纯数据层，不碰 UI）。
 * 两个公开入口都是同步阻塞调用，调用方自己放 Dispatchers.IO。
 */
object ProviderApi {

    /** 短超时：拉列表/查余额不值得让用户等（要求 10 秒） */
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    // ---------------------------------------------------------------- URL / Header

    /** 去掉尾部 /，并把用户可能粘进来的完整 .../chat/completions 还原成根地址 */
    fun normalizeBaseUrl(url: String): String {
        var b = url.trim()
        if (b.endsWith("#")) return b // join() 里处理「# 结尾 = 完整地址」约定
        val suffix = "/chat/completions"
        if (b.lowercase(Locale.US).endsWith(suffix)) b = b.dropLast(suffix.length)
        return b.trimEnd('/')
    }

    /**
     * 拼 URL：
     *  - path 以 http(s) 开头 → 直接用绝对地址；
     *  - baseUrl 以 '#' 结尾 → 删掉 '#' 后直接接 path（Cherry Studio 同款约定）；
     *  - 否则 baseUrl.trimEnd('/') + "/" + path.trimStart('/')。
     */
    fun join(baseUrl: String, path: String): String {
        if (path.startsWith("http://", true) || path.startsWith("https://", true)) return path
        val b = normalizeBaseUrl(baseUrl)
        if (b.isEmpty()) return path.trimStart('/')
        if (b.endsWith("#")) return b.removeSuffix("#") + path
        return b.trimEnd('/') + "/" + path.trimStart('/')
    }

    /** scheme://host[:port]，DeepSeek 的 /user/balance 必须按这个拼（不在 /v1 下） */
    fun originOf(url: String): String {
        val u = try {
            normalizeBaseUrl(url).toHttpUrlOrNull()
        } catch (e: Exception) {
            null
        } ?: return normalizeBaseUrl(url)
        val port = if (u.port == 80 || u.port == 443) "" else ":" + u.port
        return u.scheme + "://" + u.host + port
    }

    /** 给 URL 追加 ?key=（Gemini 原生风格；已有 query 时用 &） */
    fun appendQueryKey(url: String, key: String): String {
        val k = key.trim()
        if (k.isEmpty()) return url
        val parsed = try {
            url.toHttpUrlOrNull()
        } catch (e: Exception) {
            null
        }
        return if (parsed != null) {
            parsed.newBuilder().addQueryParameter("key", k).build().toString()
        } else if (url.contains("?")) {
            url + "&key=" + k
        } else {
            url + "?key=" + k
        }
    }

    /** 聊天端点：老行为（用户直接填了 .../chat/completions 就原样用）+ 新预设的 chatPath */
    fun chatUrl(preset: ProviderPreset, baseUrl: String, apiKey: String): String {
        val base = baseUrl.trim()
        val url = if (base.lowercase(Locale.US).endsWith("/chat/completions")) {
            base
        } else {
            join(base, preset.chatPath)
        }
        return if (preset.auth == AuthStyle.QueryKey) appendQueryKey(url, apiKey) else url
    }

    /**
     * 聊天请求的鉴权头。
     * 注意：Bearer 分支即使 key 为空也照样发 `Authorization: Bearer `，与改造前的
     * `Client.chat()` 完全一致（DeepSeek 回归第一优先，保持字节级相同的头）。
     */
    fun authHeaders(preset: ProviderPreset, apiKey: String): Map<String, String> {
        val key = apiKey.trim()
        val m = LinkedHashMap<String, String>()
        when (preset.auth) {
            AuthStyle.Bearer -> m["Authorization"] = "Bearer " + key
            AuthStyle.AnthropicKey -> {
                // Anthropic 兼容层也能吃 Bearer，但 x-api-key 更稳；两个一起发反而可能 401
                m["x-api-key"] = key
                m["anthropic-version"] = "2023-06-01"
            }
            AuthStyle.QueryKey -> Unit // 由 chatUrl 追加 ?key=
            AuthStyle.None -> Unit
        }
        m.putAll(preset.extraHeaders)
        return m
    }

    /** REST（拉模型/查余额）的鉴权头：key 为空就不发鉴权头（OpenRouter 的 /models 是公开的） */
    private fun fetchHeaders(preset: ProviderPreset, apiKey: String): Map<String, String> {
        val key = apiKey.trim()
        val m = LinkedHashMap<String, String>()
        when (preset.auth) {
            AuthStyle.Bearer -> if (key.isNotEmpty()) m["Authorization"] = "Bearer " + key
            AuthStyle.AnthropicKey -> {
                if (key.isNotEmpty()) m["x-api-key"] = key
                m["anthropic-version"] = "2023-06-01"
            }
            AuthStyle.QueryKey, AuthStyle.None -> Unit
        }
        m.putAll(preset.extraHeaders)
        return m
    }

    // ---------------------------------------------------------------- 模型列表

    /**
     * GET {baseUrl}{modelsPath}，兼容 OpenAI / Anthropic / Gemini / Ollama / data|models|result 各种形状。
     * 失败返回 ModelsResult(error=...)，**不会**动已有的 providerModels（是否落盘由调用方决定）。
     */
    fun fetchModels(preset: ProviderPreset, apiKey: String, baseUrl: String): ModelsResult {
        val path = preset.modelsPath
        if (path.isNullOrBlank()) return ModelsResult(error = "该服务商没有模型列表接口")
        val absolute = path.startsWith("http://", true) || path.startsWith("https://", true)
        if (!absolute && normalizeBaseUrl(baseUrl).isEmpty()) {
            return ModelsResult(error = "未填写 API 地址")
        }
        val key = apiKey.trim()
        val url0 = if (absolute) path else join(baseUrl, path)
        val url = if (preset.auth == AuthStyle.QueryKey) appendQueryKey(url0, key) else url0
        return try {
            val builder = Request.Builder().url(url).get().header("Accept", "application/json")
            for ((k, v) in fetchHeaders(preset, key)) builder.header(k, v)
            http.newCall(builder.build()).execute().use { r ->
                val body = try {
                    r.body.string()
                } catch (e: Exception) {
                    ""
                }
                if (!r.isSuccessful) return ModelsResult(error = httpError(r.code, body))
                val list = try {
                    parseModelsBody(body, preset.modelsKind)
                } catch (e: Exception) {
                    return ModelsResult(error = notJsonHint(body, e))
                }
                if (list.isEmpty()) {
                    ModelsResult(error = "未识别到模型列表（返回格式不匹配）")
                } else {
                    ModelsResult(models = list)
                }
            }
        } catch (e: Exception) {
            ModelsResult(error = netError(e))
        }
    }

    /** 宽容解析模型列表（公开以便单测/调试）。空 body 返回空列表。 */
    fun parseModelsBody(body: String, kind: ModelsKind = ModelsKind.Tolerant): List<RemoteModel> {
        val text = body.trim()
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<RemoteModel>()
        val stripGeminiPrefix = kind == ModelsKind.Gemini || kind == ModelsKind.Tolerant || kind == ModelsKind.Anthropic
        // 顶层直接是数组的少数服务商
        if (text.startsWith("[")) {
            val arr = JSONArray(text)
            collectModels(arr, out, stripGeminiPrefix)
            return out.distinctBy { it.id }
        }
        val root = JSONObject(text)
        val arrays = ArrayList<JSONArray>()
        root.optJSONArray("data")?.let { arrays.add(it) }
        root.optJSONArray("models")?.let { arrays.add(it) }
        root.optJSONArray("result")?.let { arrays.add(it) }
        root.optJSONArray("items")?.let { arrays.add(it) }
        // 嵌套形态：{"data":{"data":[...]}} / {"result":{"data":[...]}}
        for (wrapKey in listOf("data", "result", "response")) {
            val wrap = root.optJSONObject(wrapKey) ?: continue
            wrap.optJSONArray("data")?.let { arrays.add(it) }
            wrap.optJSONArray("models")?.let { arrays.add(it) }
            wrap.optJSONArray("result")?.let { arrays.add(it) }
            if (arrays.isEmpty() && out.isEmpty()) addModelFromObject(wrap, out, stripGeminiPrefix)
        }
        for (arr in arrays) collectModels(arr, out, stripGeminiPrefix)
        // 单对象形态 {"id":"xxx"}（极少见）
        if (out.isEmpty()) addModelFromObject(root, out, stripGeminiPrefix)
        return out.distinctBy { it.id }
    }

    private fun collectModels(arr: JSONArray, out: MutableList<RemoteModel>, stripGeminiPrefix: Boolean) {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            addModelFromObject(o, out, stripGeminiPrefix)
        }
    }

    private fun addModelFromObject(o: JSONObject, out: MutableList<RemoteModel>, stripGeminiPrefix: Boolean) {
        var id = firstString(o, "id", "model", "name", "slug", "model_id", "modelId")
        if (id.isBlank()) return
        id = id.trim()
        if (stripGeminiPrefix && id.startsWith("models/")) id = id.removePrefix("models/")
        if (id.isBlank()) return
        val display = firstString(o, "display_name", "displayName", "display", "title")
        val ctx = firstInt(
            o,
            "context_length", "context_window", "contextWindow",
            "max_context_length", "maxContextLength", "inputTokenLimit", "input_token_limit",
        )
        out.add(RemoteModel(id = id, displayName = display.trim(), contextLength = ctx))
    }

    // ---------------------------------------------------------------- 余额

    /**
     * 查余额。没有公开接口的 provider 返回 [BalanceResult.NotSupported]（正常形态，可缓存展示）。
     * 网络失败/鉴权失败返回 Failed，绝不抛异常。
     */
    fun fetchBalance(preset: ProviderPreset, apiKey: String, baseUrl: String): BalanceResult {
        val spec = preset.balance
        if (spec == null || spec.kind == BalanceKind.None) {
            return BalanceResult.NotSupported()
        }
        val key = apiKey.trim()
        if (spec.needsAdminKey && key.isEmpty()) {
            return BalanceResult.Failed("该接口需要 management key")
        }
        if (preset.auth != AuthStyle.None && key.isEmpty() && !preset.local) {
            return BalanceResult.Failed("未填写 API Key")
        }
        val absolute = spec.path.startsWith("http://", true) || spec.path.startsWith("https://", true)
        val url = if (absolute) {
            spec.path
        } else {
            val origin = originOf(baseUrl)
            if (origin.isEmpty()) return BalanceResult.Failed("未填写 API 地址")
            origin + spec.path
        }
        return try {
            val builder = Request.Builder().url(url).get().header("Accept", "application/json")
            for ((k, v) in fetchHeaders(preset, key)) builder.header(k, v)
            http.newCall(builder.build()).execute().use { r ->
                val body = try {
                    r.body.string()
                } catch (e: Exception) {
                    ""
                }
                if (!r.isSuccessful) return BalanceResult.Failed(httpError(r.code, body))
                val root = try {
                    JSONObject(body)
                } catch (e: Exception) {
                    return BalanceResult.Failed(notJsonHint(body, e))
                }
                when (spec.kind) {
                    BalanceKind.DeepSeek -> parseDeepSeekBalance(root)
                    BalanceKind.OpenRouterKey -> parseOpenRouterKey(root)
                    BalanceKind.OpenRouterCredits -> parseOpenRouterCredits(root)
                    BalanceKind.SiliconFlow -> parseSiliconFlowBalance(root)
                    BalanceKind.Moonshot -> parseMoonshotBalance(root, moonshotCurrency(baseUrl))
                    BalanceKind.None -> BalanceResult.NotSupported()
                }
            }
        } catch (e: Exception) {
            BalanceResult.Failed(netError(e))
        }
    }

    private fun parseDeepSeekBalance(root: JSONObject): BalanceResult {
        val infos = root.optJSONArray("balance_infos")
        val first = if (infos != null && infos.length() > 0) infos.optJSONObject(0) else null
        if (first == null) return BalanceResult.Failed("返回中没有 balance_infos")
        val total = num(first, "total_balance") ?: return BalanceResult.Failed("返回中没有 total_balance")
        val currency = first.optString("currency", "CNY").ifBlank { "CNY" }
        val lines = ArrayList<Pair<String, String>>()
        num(first, "granted_balance")?.let { lines.add("赠送余额" to moneyText(it, currency)) }
        num(first, "topped_up_balance")?.let { lines.add("充值余额" to moneyText(it, currency)) }
        if (root.has("is_available") && !root.optBoolean("is_available", true)) {
            lines.add("账户状态" to "不可用")
        }
        return BalanceResult.Money(total, currency, lines)
    }

    private fun parseOpenRouterKey(root: JSONObject): BalanceResult {
        val data = root.optJSONObject("data") ?: return BalanceResult.Failed("返回中没有 data")
        val limit = num(data, "limit")
        val usage = num(data, "usage")
        val remaining = num(data, "limit_remaining")
        val lines = ArrayList<Pair<String, String>>()
        limit?.let { lines.add("Key 限额" to moneyText(it, "USD")) }
        usage?.let { lines.add("已用" to moneyText(it, "USD")) }
        if (data.has("is_free_tier")) lines.add("免费档" to if (data.optBoolean("is_free_tier")) "是" else "否")
        if (limit == null) lines.add("说明" to "该 Key 未设限额")
        val amount = when {
            remaining != null -> remaining
            limit != null -> limit - (usage ?: 0.0)
            else -> 0.0
        }
        return BalanceResult.Money(amount, "USD", lines)
    }

    private fun parseOpenRouterCredits(root: JSONObject): BalanceResult {
        val data = root.optJSONObject("data") ?: return BalanceResult.Failed("返回中没有 data")
        val credits = num(data, "total_credits") ?: return BalanceResult.Failed("返回中没有 total_credits")
        val usage = num(data, "total_usage") ?: 0.0
        val lines = ArrayList<Pair<String, String>>()
        lines.add("总充值" to moneyText(credits, "USD"))
        lines.add("已用" to moneyText(usage, "USD"))
        return BalanceResult.Money(credits - usage, "USD", lines)
    }

    private fun parseSiliconFlowBalance(root: JSONObject): BalanceResult {
        val code = root.optInt("code", -1)
        if (code != 20000 && !root.optBoolean("status", false)) {
            val msg = root.optString("message", "").take(120)
            return BalanceResult.Failed("接口返回异常：code=" + code + (if (msg.isBlank()) "" else " " + msg))
        }
        val data = root.optJSONObject("data") ?: return BalanceResult.Failed("返回中没有 data")
        // 金额是字符串
        val total = num(data, "totalBalance") ?: num(data, "balance") ?: num(data, "chargeBalance")
            ?: return BalanceResult.Failed("返回中没有余额字段")
        val lines = ArrayList<Pair<String, String>>()
        num(data, "chargeBalance")?.let { lines.add("充值余额" to moneyText(it, "CNY")) }
        num(data, "balance")?.let { lines.add("可用余额" to moneyText(it, "CNY")) }
        return BalanceResult.Money(total, "CNY", lines)
    }

    private fun parseMoonshotBalance(root: JSONObject, currency: String): BalanceResult {
        val code = root.optInt("code", 0)
        if (code != 0) return BalanceResult.Failed("接口返回异常：code=" + code)
        val data = root.optJSONObject("data") ?: return BalanceResult.Failed("返回中没有 data")
        val available = num(data, "available_balance")
            ?: return BalanceResult.Failed("返回中没有 available_balance")
        val lines = ArrayList<Pair<String, String>>()
        num(data, "voucher_balance")?.let { lines.add("赠送余额" to moneyText(it, currency)) }
        num(data, "cash_balance")?.let { lines.add("现金余额" to moneyText(it, currency)) }
        return BalanceResult.Money(available, currency, lines)
    }

    /** Moonshot 国际站（.ai）是 USD，国内站（.cn）是 CNY */
    private fun moonshotCurrency(baseUrl: String): String =
        if (normalizeBaseUrl(baseUrl).contains("moonshot.ai")) "USD" else "CNY"

    // ---------------------------------------------------------------- JSON / 文案小工具

    private fun num(o: JSONObject?, key: String): Double? {
        if (o == null || !o.has(key) || o.isNull(key)) return null
        return when (val v = o.opt(key)) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun firstString(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            when (val v = o.opt(k)) {
                is String -> if (v.isNotBlank()) return v
                is Number -> return v.toString()
            }
        }
        return ""
    }

    private fun firstInt(o: JSONObject, vararg keys: String): Int {
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            when (val v = o.opt(k)) {
                is Number -> return v.toInt()
                is String -> v.trim().toIntOrNull()?.let { return it }
            }
        }
        return 0
    }

    private fun httpError(code: Int, body: String): String {
        val snippet = body.replace('\n', ' ').replace('\r', ' ').trim().take(200)
        val hint = when (code) {
            401 -> "API Key 无效（401）"
            402 -> "余额不足或无权限（402）"
            403 -> "无权限（403）"
            404 -> "接口路径不对：检查 API 地址（404）"
            429 -> "请求过于频繁（429）"
            else -> "请求失败（HTTP " + code + "）"
        }
        return if (snippet.isEmpty()) hint else hint + "：" + snippet
    }

    private fun netError(e: Exception): String = when (e) {
        is java.net.SocketTimeoutException -> "请求超时（10 秒）"
        is java.net.UnknownHostException -> "域名解析失败：检查网络或 API 地址"
        is javax.net.ssl.SSLException -> "TLS 握手失败：检查 API 地址"
        else -> e.message?.takeIf { it.isNotBlank() } ?: "网络请求失败"
    }

    private fun notJsonHint(body: String, e: Exception): String {
        val t = body.trim()
        if (t.startsWith("<")) return "返回的不是 JSON：地址可能填成了官网首页"
        val snippet = t.replace('\n', ' ').take(160)
        if (snippet.isEmpty()) return "返回内容为空" + (e.message?.let { "：" + it } ?: "")
        return "返回格式无法识别：" + snippet
    }
}

// -------------------------------------------------------------------- 金额 / 缓存 / Settings 小工具

/** 金额文案：¥110.00 / $8.50 / 12.00 EUR */
fun moneyText(amount: Double, currency: String = "CNY"): String {
    val cur = currency.trim().uppercase(Locale.US)
    val symbol = when (cur) {
        "CNY", "RMB" -> "¥"
        "USD" -> "$"
        "EUR" -> "€"
        "JPY" -> "¥"
        else -> ""
    }
    val n = "%.2f".format(Locale.US, amount)
    return if (symbol.isEmpty()) (n + " " + cur).trim() else symbol + n
}

/** 余额一行文案：DeepSeek 显示「¥110.00」 */
fun BalanceResult.Money.displayText(): String = moneyText(amount, currency)

/** 余额短文案（缓存里存这个）：Money → ¥110.00；NotSupported → 提示；Failed → 查询失败：xxx */
fun balanceDisplayText(result: BalanceResult): String = when (result) {
    is BalanceResult.Money -> result.displayText()
    is BalanceResult.NotSupported -> result.hint
    is BalanceResult.Failed -> "查询失败：" + result.message
}

/** 余额明细行（赠送/充值/限额…），Non-Money 返回空 */
fun balanceDetailLines(result: BalanceResult): List<Pair<String, String>> =
    (result as? BalanceResult.Money)?.lines ?: emptyList()

/** 解析 settings.json 里 balanceCache 的一条记录（json: {"t":...,"text":...,"detail":...}） */
fun balanceCacheEntry(raw: String?): BalanceCacheEntry? {
    if (raw.isNullOrBlank()) return null
    return try {
        val o = JSONObject(raw)
        val text = o.optString("text", "")
        if (text.isBlank()) null
        else BalanceCacheEntry(
            timeMillis = o.optLong("t", 0L),
            text = text,
            detail = o.optString("detail", ""),
        )
    } catch (e: Exception) {
        null
    }
}

/** 生成 balanceCache 的一条记录 */
fun balanceCacheJson(
    text: String,
    detail: String = "",
    timeMillis: Long = System.currentTimeMillis(),
): String = JSONObject()
    .put("t", timeMillis)
    .put("text", text)
    .put("detail", detail)
    .toString()

/** 设置当前 provider 的独立 baseUrl（空 = 回落到预设默认）；同时同步旧的全局 baseUrl 字段 */
fun Settings.withBaseUrl(url: String): Settings {
    val u = url.trim()
    val presetDefault = preset.baseUrl
    val map = if (u.isEmpty() || u == presetDefault) {
        baseUrls - providerId
    } else {
        baseUrls + (providerId to u)
    }
    return copy(baseUrls = map, baseUrl = if (u.isEmpty()) presetDefault else u)
}

/** 设置当前模型名 */
fun Settings.withModel(model: String): Settings = copy(model = model.trim())

/** 保存「拉取成功」的模型列表（空列表 = 清除缓存；最多 [MaxCachedModels] 条，避免 json 膨胀） */
fun Settings.withFetchedModels(models: List<String>): Settings {
    val list = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MaxCachedModels)
    return if (list.isEmpty()) {
        copy(providerModels = providerModels - providerId)
    } else {
        copy(providerModels = providerModels + (providerId to list))
    }
}

/** 手工添加模型名（永远置顶展示） */
fun Settings.withCustomModel(model: String): Settings {
    val m = model.trim()
    if (m.isEmpty()) return this
    val list = (customModels[providerId].orEmpty() + m).distinct()
    return copy(customModels = customModels + (providerId to list))
}

/** 删掉一个手工模型名 */
fun Settings.withoutCustomModel(model: String): Settings {
    val m = model.trim()
    val list = (customModels[providerId].orEmpty() - m)
    return if (list.isEmpty()) {
        copy(customModels = customModels - providerId)
    } else {
        copy(customModels = customModels + (providerId to list))
    }
}

/** 写当前 provider 的余额展示缓存（text 为空 = 清除） */
fun Settings.withBalanceCache(text: String, detail: String = ""): Settings {
    val t = text.trim()
    return if (t.isEmpty()) {
        copy(balanceCache = balanceCache - providerId)
    } else {
        copy(balanceCache = balanceCache + (providerId to balanceCacheJson(t, detail)))
    }
}
