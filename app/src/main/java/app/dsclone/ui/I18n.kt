package com.yuyuyu.deepseek.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.yuyuyu.deepseek.data.Store
import java.util.Locale

/**
 * 轻量中/英双语（不抽 strings.xml）。
 *
 * 官方客户端有 70+ 语言，本工程只做「中文 + English」两种（用户要求）。
 * 界面里的高频文案直接写成 `I18n.t("中文", "English")`：
 *  - 英文原文尽量取官方 2.9.0 的 `res/values/strings.xml`（官方默认 values = 英文），
 *    名字见各调用点注释（如 profile_title = Settings）。
 *  - 语言状态是 Compose 的 MutableState：文案在**组合期**被读取，
 *    在设置里切换语言时 state 一变，所有读了它的界面立刻重组换语言，不需要重建 Activity。
 *
 * 语言来源复用现有设置字段 [com.yuyuyu.deepseek.data.Settings.appLanguage]
 * （"system" / "zh-Hans" / "en" ...），不新造字段。
 */

/** 应用内生效语言（SYSTEM 只是档位，实际生效的永远解析成 ZH 或 EN）。 */
enum class AppLang { SYSTEM, ZH, EN }

object I18n {

    /** 当前生效语言。在组合里读它 → 切换语言时自动重组。 */
    private val state = mutableStateOf(AppLang.SYSTEM)

    /** 当前生效语言（组合里读这个值即可订阅语言变化）。 */
    val lang: AppLang get() = state.value

    /** 当前是否中文。 */
    val isZh: Boolean get() = state.value == AppLang.ZH

    /** 启动时由 MainActivity 在 setContent 之前调用（传磁盘上已保存的 appLanguage）。 */
    fun init(setting: String) {
        state.value = parse(setting)
    }

    /** 设置里切换语言时立刻调用：界面即时换语言（不等 Activity 重建 / 退出设置页）。 */
    fun set(setting: String) {
        state.value = parse(setting)
    }

    /** 非 Composable 场景兜底：重新从磁盘读一次设置。 */
    fun sync(ctx: Context) {
        runCatching { state.value = parse(Store.loadSettings(ctx).appLanguage) }
    }

    /**
     * 设置值 → 生效语言。
     * "system" 跟随系统 Locale：系统语言是中文就中文，其它一律英文（只做中/英两种）。
     */
    fun parse(setting: String): AppLang = when (setting) {
        "zh-Hans", "zh-Hant", "zh" -> AppLang.ZH
        "en" -> AppLang.EN
        else -> if (Locale.getDefault().language == "zh") AppLang.ZH else AppLang.EN
    }

    /** 中/英二选一。 */
    fun t(zh: String, en: String): String = if (state.value == AppLang.ZH) zh else en
}

/**
 * 组合里取一个 `(中文, English) -> 当前语言文案` 的函数。
 *
 * 注意：绝大多数地方直接写 `I18n.t(...)` 就够了（组合期读 state 同样会订阅变化）；
 * 这个包装给「想把 t 传进普通 lambda / 子组件」的场景用。
 */
@Composable
fun rememberI18n(): (String, String) -> String {
    val lang = I18n.lang
    return remember(lang) { { zh: String, en: String -> if (lang == AppLang.ZH) zh else en } }
}
