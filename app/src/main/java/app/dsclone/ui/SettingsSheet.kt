package com.yuyuyu.deepseek.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuyuyu.deepseek.data.AppearanceOptions
import com.yuyuyu.deepseek.data.LanguageOptions
import com.yuyuyu.deepseek.data.Settings
import com.yuyuyu.deepseek.data.Store
import com.yuyuyu.deepseek.data.VoiceOptions
import com.yuyuyu.deepseek.data.appearanceLabel
import com.yuyuyu.deepseek.data.languageLabel
import com.yuyuyu.deepseek.ui.theme.DsAppearance
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.LocalDsColors
import kotlinx.coroutines.delay

/**
 * 「设置」页：1:1 对齐官方 com.deepseek.chat 2.9.0（SettingsPage.kt）的结构。
 *
 * 官方结构（真机 1600x2176 @2.0 逐屏核对）：
 *   账户  → 账号管理 / 数据管理
 *   应用  → 语言(右侧当前值) / 外观(右侧当前值) / 字体大小 / 个性化
 *   语音  → 朗读音色
 *   关于  → 检查更新(右侧版本号) / 服务协议
 *   [独立卡] 帮助与反馈
 *   [独立卡] 退出登录
 *   [页脚] 模型名称 / 备案号 / 浙ICP / AI 生成提示
 *
 * 我们的 API / 模型 / 温度 等开发向参数收进「账户」组第一条「API 与模型」子页面，
 * 功能一个不丢（见 SettingsSub.kt）。
 *
 * 尺寸来自像素测量：卡片左右 40dp、行高 45dp、圆角 14dp、图标 24dp 内缩 15dp、
 * 标题 12sp #8E9094、条目 16sp #0F0F0F、右侧值 16sp #8E9094、chevron 20dp #B9BABB。
 */

// ===== 实测尺寸常量 =====
private val MainMargin = 40.dp
private val NarrowMargin = 15.dp
private val RowH = 45.dp
private val CardR = 14.dp
private val IconInset = 15.dp
private val IconBox = 24.dp
private val IconGap = 8.dp
private val EndPad = 12.dp
private val ChevronBox = 20.dp
private val HeadTop = 26.5.dp
private val HeadBottom = 7.5.dp
private val FirstTop = 9.5.dp
private val SectionGap = 34.dp

private val TitleSize = 16.sp
private val ItemSize = 16.sp
private val ValueSize = 16.sp
private val HeadSize = 12.sp
private val FootSize = 12.sp

/** 子页面 key */
object SettingsPage {
    const val API = "api"
    const val ACCOUNT = "account"
    const val DATA = "data"
    const val FONT = "font"
    const val PERSONAL = "personal"
    const val VOICE = "voice"
    const val TERMS = "terms"
    const val DEVICES = "devices"
    const val HELP = "help"
    const val LEGAL = "legal"
    const val OPEN_SOURCE = "opensource"
    const val DOC_TERMS = "doc:terms"
    const val DOC_PRIVACY = "doc:privacy"
    const val DOC_SDK = "doc:sdk"
    const val DOC_INFO = "doc:info"
    const val DOC_PERM = "doc:perm"
    const val DOC_SHARE = "doc:share"
    const val DOC_EXPORT = "doc:export"
}

/** 弹窗 key */
object SettingsDialog {
    const val LANGUAGE = "language"
    const val APPEARANCE = "appearance"
    const val VOICE = "voice"
    const val LOGOUT = "logout"
    const val DELETE_CHATS = "deleteChats"
    const val MINOR = "minor"
    const val DELETE_ACCOUNT = "deleteAccount"
    const val ACCOUNT_ENTRY = "accountEntry"
}

/** 设置页面的 key（page 为 null = 主设置页）。用来记「上一页」，做手写交叉过渡。 */
private data class SettingsPageEntry(val page: String?)

internal fun settingsPageTitle(page: String): String = when (page) {
    SettingsPage.API -> "API 与模型"
    SettingsPage.ACCOUNT -> "账号管理"
    SettingsPage.DATA -> "数据管理"
    SettingsPage.FONT -> "字体大小"
    SettingsPage.PERSONAL -> "个性化"
    SettingsPage.VOICE -> "朗读音色"
    SettingsPage.TERMS -> "服务协议"
    SettingsPage.DEVICES -> "登录设备管理"
    SettingsPage.HELP -> "帮助与反馈"
    SettingsPage.LEGAL -> "版权与免责声明"
        SettingsPage.OPEN_SOURCE -> "开源项目"
    SettingsPage.DOC_TERMS -> "用户协议"
    SettingsPage.DOC_PRIVACY -> "隐私政策"
    SettingsPage.DOC_SDK -> "第三方信息共享清单"
    SettingsPage.DOC_INFO -> "个人信息收集清单"
    SettingsPage.DOC_PERM -> "应用权限"
    SettingsPage.DOC_SHARE -> "我共享的链接"
    SettingsPage.DOC_EXPORT -> "导出所有历史对话"
    else -> "设置"
}

// =====================================================================================
//  入口
// =====================================================================================

@Composable
fun SettingsSheet(
    initial: Settings,
    onDismiss: () -> Unit,
    onSave: (Settings) -> Unit,
    // 「数据管理 → 导入 / 导出聊天记录」用：由 DsApp 提供当前会话的 JSON 与合并回调
    sessionsJson: () -> String = { "[]" },
    onImportSessions: (String) -> Int = { 0 },
) {
    val ds = LocalDsColors.current
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(initial) }
    val stack = remember { mutableStateListOf<String>() }
    var dialog by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1600)
            toast = null
        }
    }
    // 音色接到 DsTts（不动 DsTts.kt，只读它的公开 var）
    LaunchedEffect(Unit) { DsTts.voiceId = initial.voiceId.ifBlank { "stella" } }

    // 官方进设置页是从右侧滑入 + 淡入，退出去是向右滑出 + 淡出；进出对称 220ms，
    // 缓动统一 FastOutSlowInEasing（Material 推荐曲线）。
    // 关键：驱动方式改成 rememberFrameAnim（withFrameNanos 逐帧累计），不再用 Animatable。
    // Animatable 的时长会乘系统「动画时长缩放」（部分 ROM 的「移除动画」会把它直接设成 0），
    // 缩放=0 时设置页会一帧跳出来；逐帧驱动自己算时间，不受这个缩放影响。
    var closing by remember { mutableStateOf(false) }
    val enterProgress = rememberFrameAnim(
        durationMs = 220,
        trigger = closing,
        onFinished = { if (closing) onSave(s) },
    )
    // closing=false：进度 0→1 当入场；closing=true：进度重新从 0 跑，映射成 1→0 当退场。
    // 关闭那一帧进度还是 0 → enter 仍是 1（全可见），不会闪一下。
    // 注意：进度只在下面 graphicsLayer 的 block 里读（layout 阶段），
    // 每帧只重算图层，不会每帧重组整个设置页内容。
    val commit: () -> Unit = { if (!closing) closing = true }

    // 二级页面 push / pop：手写「from/to 两个 page key + 一个逐帧进度」的交叉过渡
    // （原来用 AnimatedContent：它的时钟由 Compose 动画系统 / MotionDurationScale 驱动，
    //  系统「动画时长缩放=0」时会直接跳变）。
    //   push（进子页）：新页从右 12% 滑入 + 淡入，旧页向左 4% 滑出 + 淡出
    //   pop（返回）：当前页向右 12% 滑出 + 淡出，上一页从左侧 4% 淡入
    // 时长/曲线与原来一致：220ms / FastOutSlowInEasing。
    // 用 (当前页面, 栈深度) 做 key：page 为 null 表示主设置页；深度变化决定 push/pop 方向。
    val pageState: Pair<String?, Int> = stack.lastOrNull() to stack.size
    var prevEntry by remember { mutableStateOf<SettingsPageEntry?>(null) }
    var pushDir by remember { mutableStateOf(true) }
    // 过渡是否已经跑完：只在开始/结束各触发一次重组，进度本身在 graphicsLayer 里读。
    var transitionDone by remember { mutableStateOf(true) }
    val pageProgress = rememberFrameAnim(
        durationMs = 220,
        trigger = pageState,
        startAtEnd = true,
        onFinished = { transitionDone = true },
    )
    // 入栈 / 出栈都从这里走：切页那一瞬间同步记下「旧页」，交叉过渡的退场页就是它。
    val navigate: (String) -> Unit = { target ->
        prevEntry = SettingsPageEntry(stack.lastOrNull())
        pushDir = true
        transitionDone = false
        stack.add(target)
    }
    val dismissOrPop: () -> Unit = {
        if (stack.isNotEmpty()) {
            prevEntry = SettingsPageEntry(stack.last())
            pushDir = false
            transitionDone = false
            stack.removeAt(stack.lastIndex)
        } else {
            commit()
        }
    }
    val showToast: (String) -> Unit = { toast = it }

    val versionText = remember {
        runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            "${pi.versionName}($code)"
        }.getOrElse { "2.3.0(25)" }
    }

    Dialog(
        onDismissRequest = dismissOrPop,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 从右侧滑入 20% 宽 + 淡入（官方进设置是有过渡的）
                    val t = frameEase(enterProgress.value)
                    val v = if (closing) 1f - t else t
                    alpha = v
                    translationX = (1f - v) * size.width * 0.20f
                }
                .background(ds.pageBg)
        ) {
            SettingsTopBar(
                title = if (stack.isEmpty()) "设置" else settingsPageTitle(stack.last()),
                onBack = dismissOrPop,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
            ) {
                // 逐帧驱动的交叉过渡（见 FrameAnim.kt）：旧页 / 新页同时在场，按进度插值。
                // AnimatedContent 做不到这点 —— 它的时钟由 Compose 动画系统驱动，
                // 系统「动画时长缩放=0」时过渡会被缩成 0，一帧就切完了。
                // 进度只在 graphicsLayer 的 block 里读，避免每帧重组两个页面的内容。
                val entering = pageState.first
                // 注意：page 为 null 是合法的「主设置页」，所以用 prevEntry 是否为 null 判断有没有旧页，
                // 不能只看 leavingPage 是否为 null。
                val leavingPage = prevEntry?.page
                val showLeaving = !transitionDone && prevEntry != null && leavingPage != entering
                // 每页各自的滚动位置：退场页会被重新组合到另一个 slot，
                // 不把 ScrollState 存起来的话翻转瞬间会跳回顶部。
                val pageScroll = remember { HashMap<String?, ScrollState>() }
                // 页面内容：page 为 null 表示主设置页。
                val pageContent: @Composable (String?) -> Unit = { page ->
                val onSChange: (Settings) -> Unit = { ns ->
                    // 外观 / 字体 / 音色 立刻生效（不用等整页保存）
                    if (ns.appearance != s.appearance) {
                        DsAppearance.apply(ns.appearance, ns.fontSizeLevel, ns.followSystemFont)
                        Store.saveAppearance(ctx, ns.appearance)
                    }
                    if (ns.fontSizeLevel != s.fontSizeLevel || ns.followSystemFont != s.followSystemFont) {
                        DsAppearance.apply(ns.appearance, ns.fontSizeLevel, ns.followSystemFont)
                    }
                    if (ns.voiceId != s.voiceId) DsTts.voiceId = ns.voiceId
                    s = ns
                }
                if (page != null && (page == SettingsPage.ACCOUNT || page == SettingsPage.FONT)) {
                    // 账号管理 / 字体大小：官方把「注销账号」「字体滑块卡片」压在屏幕最底部，这两页不滚动
                    SettingsSubPage(
                        page = page,
                        s = s,
                        onChange = onSChange,
                        onNavigate = navigate,
                        onDialog = { dialog = it },
                        onToast = showToast,
                        sessionsJson = sessionsJson,
                        onImportSessions = onImportSessions,
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(pageScroll.getOrPut(page) { ScrollState(0) }),
                    ) {
                        if (page == null) {
                            MainSettingsPage(
                                s = s,
                                versionText = versionText,
                                onNavigate = navigate,
                                onDialog = { dialog = it },
                                onToast = showToast,
                            )
                        } else {
                            SettingsSubPage(
                                page = page,
                                s = s,
                                onChange = onSChange,
                                onNavigate = navigate,
                                onDialog = { dialog = it },
                                onToast = showToast,
                                sessionsJson = sessionsJson,
                                onImportSessions = onImportSessions,
                            )
                        }
                    }
                }
                }
                // 旧页（退场）：淡出 + 位移（push 时向左 4%，pop 时向右 12%）
                if (showLeaving) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = frameEase(pageProgress.value)
                                alpha = 1f - t
                                translationX = (if (pushDir) -0.04f else 0.12f) * size.width * t
                            },
                    ) {
                        pageContent(leavingPage)
                    }
                }
                // 新页（入场）：淡入 + 位移（push 时从右 12%，pop 时从左侧 4%）
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val t = frameEase(pageProgress.value)
                            alpha = t
                            translationX = (if (pushDir) 0.12f else -0.04f) * size.width * (1f - t)
                        },
                ) {
                    pageContent(entering)
                }
                if (toast != null) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ds.toastBg)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(toast ?: "", fontSize = 14.sp, color = ds.toastText)
                    }
                }
            }
        }

        when (dialog) {
            SettingsDialog.LANGUAGE -> ChoiceDialog(
                title = "语言",
                icon = "globe",
                options = LanguageOptions.map { it.id to it.label },
                selected = s.appLanguage,
                onConfirm = { id ->
                    s = s.copy(appLanguage = id)
                    dialog = null
                    showToast("已切换为 ${languageLabel(id)}")
                },
                onDismiss = { dialog = null },
            )
            SettingsDialog.APPEARANCE -> ChoiceDialog(
                title = "外观",
                icon = null,
                options = AppearanceOptions.map { it.id to it.label },
                selected = s.appearance,
                onConfirm = { id ->
                    s = s.copy(appearance = id)
                    DsAppearance.apply(id, s.fontSizeLevel, s.followSystemFont)
                    Store.saveAppearance(ctx, id)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
            SettingsDialog.VOICE -> VoiceDialog(
                selected = s.voiceId,
                onConfirm = { id ->
                    s = s.copy(voiceId = id)
                    DsTts.voiceId = id
                    dialog = null
                    val name = VoiceOptions.firstOrNull { it.id == id }?.name ?: id
                    showToast("朗读音色已切换为 $name")
                },
                onDismiss = { dialog = null },
            )
            SettingsDialog.LOGOUT -> AlertDialog2(
                title = "确认退出登录？",
                message = "退出登录不会丢失任何数据，你仍可以登录此账号。",
                confirm = "退出登录",
                onConfirm = {
                    dialog = null
                    // 退出登录 = 清掉本地保存的 Key；下次启动会回到登录页
                    s = s.copy(keys = emptyMap())
                    Toast.makeText(ctx, "已退出登录", Toast.LENGTH_SHORT).show()
                    onSave(s)
                },
                onDismiss = { dialog = null },
            )
            SettingsDialog.DELETE_CHATS -> AlertDialog2(
                title = "删除所有历史对话？",
                message = "删除后，对话将不可恢复，由该对话生成的分享链接也将失效。",
                confirm = "删除",
                destructive = true,
                onConfirm = {
                    dialog = null
                    showToast("本地版请在侧边栏逐条删除对话")
                },
                onDismiss = { dialog = null },
            )
            SettingsDialog.MINOR -> AlertDialog2(
                title = "未成年人模式",
                message = "本地复刻版未接入未成年人模式，账号相关能力需登录官方账号后使用。",
                confirm = "我知道了",
                showCancel = false,
                onConfirm = { dialog = null },
                onDismiss = { dialog = null },
            )
            SettingsDialog.DELETE_ACCOUNT -> AlertDialog2(
                title = "确认注销我的账号？",
                message = "注销后账号将无法找回，本地复刻版不提供注销能力。",
                confirm = "我知道了",
                confirmColor = DsBlue,
                showCancel = false,
                onConfirm = { dialog = null },
                onDismiss = { dialog = null },
            )
            SettingsDialog.ACCOUNT_ENTRY -> AlertDialog2(
                title = "账号信息",
                message = "本地复刻版未接入 DeepSeek 账号体系，手机号 / 邮箱 / 微信绑定需在官方 App 中管理。",
                confirm = "我知道了",
                showCancel = false,
                onConfirm = { dialog = null },
                onDismiss = { dialog = null },
            )
        }
    }
}

// =====================================================================================
//  顶栏
// =====================================================================================

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    val ds = LocalDsColors.current
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(45.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .size(45.dp)
                .clip(CircleShape)
                .background(ds.backCircle)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            DsIcon("back", 19.dp, ds.rowText)
        }
        androidx.compose.animation.Crossfade(
            targetState = title,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 220,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
            modifier = Modifier.align(Alignment.Center),
            label = "settingsTitle",
        ) { t ->
            Text(
                text = t,
                fontSize = TitleSize,
                fontWeight = FontWeight.Medium,
                color = ds.rowText,
            )
        }
    }
}

// =====================================================================================
//  主页面
// =====================================================================================

@Composable
private fun MainSettingsPage(
    s: Settings,
    versionText: String,
    onNavigate: (String) -> Unit,
    onDialog: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    val ds = LocalDsColors.current
    Column(Modifier.fillMaxWidth()) {
        DsGroupHeader("账户", topGap = FirstTop)
        DsCard {
            SettingRow(label = "API 与模型", icon = "api", chevron = true, onClick = { onNavigate(SettingsPage.API) })
            SettingRow(label = "账号管理", icon = "user", chevron = true, onClick = { onNavigate(SettingsPage.ACCOUNT) })
            SettingRow(label = "数据管理", icon = "data", chevron = true, onClick = { onNavigate(SettingsPage.DATA) })
        }

        DsGroupHeader("应用")
        DsCard {
            SettingRow(
                label = "语言",
                icon = "globe",
                value = if (s.appLanguage == "system") "中文 (简体中文)" else languageLabel(s.appLanguage),
                chevron = true,
                onClick = { onDialog(SettingsDialog.LANGUAGE) },
            )
            SettingRow(
                label = "外观",
                icon = "sun",
                value = appearanceLabel(s.appearance),
                chevron = true,
                onClick = { onDialog(SettingsDialog.APPEARANCE) },
            )
            SettingRow(label = "字体大小", icon = "aa", chevron = true, onClick = { onNavigate(SettingsPage.FONT) })
            SettingRow(label = "个性化", icon = "magic", chevron = true, onClick = { onNavigate(SettingsPage.PERSONAL) })
        }

        DsGroupHeader("语音")
        DsCard {
            // 官方：朗读音色是一个独立子页（两张音色卡片），不再是弹窗
            SettingRow(
                label = "朗读音色",
                icon = "speaker",
                value = VoiceOptions.firstOrNull { it.id == s.voiceId }?.name ?: "星光",
                chevron = true,
                onClick = { onNavigate(SettingsPage.VOICE) },
            )
        }

        DsGroupHeader("关于")
        DsCard {
            SettingRow(
                label = "关于",
                icon = "info",
                value = "非官方客户端",
                chevron = false,
                onClick = { onToast("DeepSeek 非官方客户端 · $versionText") },
            )
            SettingRow(
                label = "检查更新",
                icon = "info",
                value = versionText,
                chevron = true,
                onClick = { onToast("当前已是最新版本") },
            )
            SettingRow(label = "服务协议", icon = "doc", chevron = true, onClick = { onNavigate(SettingsPage.TERMS) })
            // 防侵权 / 免责：完整声明页（ui/Legal.kt 的 LEGAL_TEXT）
            SettingRow(
                label = "版权与免责声明",
                icon = "doc",
                chevron = true,
                onClick = { onNavigate(SettingsPage.LEGAL) },
            )
            // 开源：仓库地址 / Releases / 协议 / 第三方组件（OpenSourcePage.kt）
            SettingRow(
                label = "开源项目",
                icon = "info",
                value = "MIT",
                chevron = true,
                onClick = { onNavigate(SettingsPage.OPEN_SOURCE) },
            )
        }

        DsCard(topGap = SectionGap) {
            SettingRow(label = "帮助与反馈", icon = "help", chevron = true, onClick = { onNavigate(SettingsPage.HELP) })
        }
        DsCard(topGap = SectionGap) {
            SettingRow(label = "退出登录", icon = "logout", chevron = false, onClick = { onDialog(SettingsDialog.LOGOUT) })
        }

        Text(
            // 官方页脚里的「模型名称 / 备案号 / 浙ICP备…」是 DeepSeek 官方的备案信息，
            // 我们不是官方、也没有那个备案号，照抄属于冒用 → 已全部删掉，换成自己的说明。
            text = "DeepSeekAPI版本 · 非官方客户端\n" +
                "内容由 AI 生成，请仔细甄别，并合法使用\n" +
                "联系邮箱：" + com.yuyuyu.deepseek.ui.LEGAL_CONTACT_EMAIL,
            fontSize = FootSize,
            lineHeight = 19.sp,
            color = ds.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MainMargin, end = MainMargin, top = 26.5.dp, bottom = 24.dp),
        )
    }
}

// =====================================================================================
//  通用组件（SettingsSub.kt 也用）
// =====================================================================================

@Composable
internal fun DsGroupHeader(text: String, margin: Dp = MainMargin, topGap: Dp = HeadTop) {
    val ds = LocalDsColors.current
    Text(
        text = text,
        fontSize = HeadSize,
        lineHeight = 17.sp,
        color = ds.secondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = margin + IconInset, end = margin, top = topGap, bottom = HeadBottom),
    )
}

@Composable
internal fun DsCard(
    margin: Dp = MainMargin,
    topGap: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ds = LocalDsColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = margin, end = margin, top = topGap)
            .clip(RoundedCornerShape(CardR))
            .background(ds.cardBg),
        content = content,
    )
}

@Composable
internal fun SettingRow(
    label: String,
    icon: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    labelColor: Color? = null,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ds = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowH)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = IconInset, end = EndPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            DsIcon(icon, IconBox, labelColor ?: ds.rowText)
            Spacer(Modifier.width(IconGap))
        }
        Text(
            text = label,
            fontSize = ItemSize,
            color = labelColor ?: ds.rowText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = ValueSize,
                color = valueColor ?: ds.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(if (value != null || trailing != null) 4.dp else 0.dp))
            DsIcon("chevron", ChevronBox, ds.chevron)
        }
    }
}

@Composable
internal fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    margin: Dp = MainMargin,
) {
    val ds = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowH)
            .clickable { onCheckedChange(!checked) }
            .padding(start = IconInset, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = ItemSize,
            color = ds.rowText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DsSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 官方开关：轨道 48x30dp、圆角 15dp、白色 26dp 圆点，开启色 #22C55E */
@Composable
internal fun DsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val ds = LocalDsColors.current
    val t by animateFloatAsState(targetValue = if (checked) 1f else 0f, label = "switch")
    Canvas(Modifier.size(48.dp, 30.dp).clickable { onCheckedChange(!checked) }) {
        val w = size.width
        val h = size.height
        val r = h / 2f
        drawRoundRect(
            color = if (checked) ds.switchOn else ds.switchOff,
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = CornerRadius(r, r),
        )
        val cx = h / 2f + t * (w - h)
        val thumb = r - 2.dp.toPx()
        drawCircle(Color(0x1A000000), radius = thumb, center = Offset(cx, h / 2f + 1.dp.toPx()))
        drawCircle(Color.White, radius = thumb, center = Offset(cx, h / 2f))
    }
}

/** 单行输入框（卡片内），高度 45dp，占位符 #B9BABB */
@Composable
internal fun FieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    minHeight: Dp = RowH,
) {
    val ds = LocalDsColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(start = IconInset, end = EndPad),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = ItemSize, color = ds.placeholder)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = ItemSize, color = ds.rowText),
            singleLine = minHeight <= RowH,
            cursorBrush = SolidColor(DsBlue),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }
}

/** 卡片里的滑块行（温度 / top_p） */
@Composable
internal fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    val ds = LocalDsColors.current
    Column(Modifier.fillMaxWidth().padding(start = IconInset, end = EndPad, top = 10.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = ItemSize, color = ds.rowText, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 15.sp, color = DsBlue)
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = DsBlue,
                activeTrackColor = DsBlue,
                inactiveTrackColor = ds.switchOff,
            ),
        )
    }
}

/** 分组说明文字（卡片下方，官方 12sp #8E9094） */
@Composable
internal fun DsSupporting(text: String, margin: Dp = MainMargin) {
    val ds = LocalDsColors.current
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = ds.secondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = margin + IconInset, end = margin, top = 7.5.dp),
    )
}

// =====================================================================================
//  弹窗
// =====================================================================================

/** 官方单选弹窗：标题居中、分隔线、单选行、底部整行「确认」（#426EFE） */
@Composable
private fun ChoiceDialog(
    title: String,
    icon: String?,
    options: List<Pair<String, String>>,
    selected: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ds = LocalDsColors.current
    var pick by remember { mutableStateOf(selected) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(356.dp)
                    .clip(RoundedCornerShape(CardR))
                    .background(ds.cardBg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (icon != null) {
                    DsIcon(icon, 26.dp, ds.rowText, Modifier.padding(top = 18.dp))
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = ds.rowText,
                    modifier = Modifier.padding(top = if (icon == null) 18.dp else 0.dp, bottom = 16.dp),
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(ds.divider))
                Column(
                    Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for ((id, label) in options) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(46.5.dp)
                                .clickable { pick = id }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioDot(on = pick == id)
                            Spacer(Modifier.width(12.dp))
                            Text(label, fontSize = 16.sp, color = ds.rowText)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(ds.divider))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clickable { onConfirm(pick) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("确认", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DsBlue)
                }
            }
        }
    }
}

/** 官方单选圆点：选中=黑环+黑点，未选=灰环 */
@Composable
private fun RadioDot(on: Boolean) {
    val ds = LocalDsColors.current
    Canvas(Modifier.size(20.dp)) {
        val r = size.minDimension / 2f - 1.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = if (on) ds.rowText else Color(0xFFCDCDCD),
            radius = r,
            center = c,
            style = Stroke(width = 1.6.dp.toPx()),
        )
        if (on) {
            drawCircle(color = ds.rowText, radius = r * 0.55f, center = c)
        }
    }
}

/** 官方二次确认弹窗 */
@Composable
private fun AlertDialog2(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    showCancel: Boolean = true,
    confirmColor: Color? = null,
) {
    val ds = LocalDsColors.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(CardR))
                    .background(ds.cardBg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = ds.rowText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp),
                )
                Text(
                    text = message,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = ds.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 18.dp),
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(ds.divider))
                Row(Modifier.fillMaxWidth().height(46.dp)) {
                    if (showCancel) {
                        Box(
                            Modifier.weight(1f).fillMaxSize().clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("取消", fontSize = 16.sp, color = ds.secondary)
                        }
                        Box(Modifier.width(0.5.dp).fillMaxSize().background(ds.divider))
                    }
                    Box(
                        Modifier.weight(1f).fillMaxSize().clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = confirm,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = confirmColor ?: if (destructive) ds.destructive else DsBlue,
                        )
                    }
                }
            }
        }
    }
}

/** 官方「朗读音色」大弹窗：音色预览（水波）+ 名称 + 说明 + 圆点 + 蓝色确认按钮 */
@Composable
private fun VoiceDialog(
    selected: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ds = LocalDsColors.current
    val start = VoiceOptions.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start, pageCount = { VoiceOptions.size })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth(0.84f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ds.cardBg),
            ) {
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    Text(
                        text = "朗读音色",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = ds.rowText,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 14.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(ds.backCircle)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        DsIcon("close", 14.dp, ds.rowText)
                    }
                }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().height(268.dp),
                ) { index ->
                    val v = VoiceOptions[index]
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        VoiceBlob()
                        Spacer(Modifier.height(26.dp))
                        Text(v.name, fontSize = 16.sp, color = ds.rowText)
                        Spacer(Modifier.height(6.dp))
                        Text(v.desc, fontSize = 13.sp, color = ds.secondary)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in VoiceOptions.indices) {
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (i == pager.currentPage) DsBlue else Color(0xFFD9D9D9)),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DsBlue)
                        .clickable {
                            onConfirm(VoiceOptions[pager.currentPage.coerceIn(0, VoiceOptions.lastIndex)].id)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("确认", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun VoiceBlob() {
    Box(
        Modifier
            .width(232.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(52.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFE6F1FF), Color(0xFFB9D6FF), Color(0xFFDCEBFF)),
                ),
            ),
    )
}

// =====================================================================================
//  图标（全部用 Canvas 画，官方是 24dp 线性图标）
// =====================================================================================

@Composable
internal fun DsIcon(kind: String, box: Dp = IconBox, tint: Color? = null, modifier: Modifier = Modifier) {
    val ds = LocalDsColors.current
    val c = tint ?: ds.rowText
    Canvas(modifier.size(box)) {
        val u = size.minDimension / 24f
        val sw = 1.7f * u
        when (kind) {
            "user" -> {
                drawCircle(c, 3.4f * u, Offset(12f * u, 7.8f * u), style = Stroke(sw))
                drawArc(
                    color = c,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(4.6f * u, 13.6f * u),
                    size = Size(14.8f * u, 11.4f * u),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
            "data" -> {
                drawRoundRect(c, Offset(3.8f * u, 5.2f * u), Size(16.4f * u, 6.0f * u), CornerRadius(2f * u), style = Stroke(sw))
                drawRoundRect(c, Offset(3.8f * u, 13.0f * u), Size(16.4f * u, 6.0f * u), CornerRadius(2f * u), style = Stroke(sw))
                drawCircle(c, 1.1f * u, Offset(7.0f * u, 8.2f * u))
                drawCircle(c, 1.1f * u, Offset(7.0f * u, 16.0f * u))
            }
            "globe" -> {
                drawCircle(c, 8.4f * u, Offset(12f * u, 12f * u), style = Stroke(sw))
                drawOval(c, Offset(8.2f * u, 3.6f * u), Size(7.6f * u, 16.8f * u), style = Stroke(sw))
                drawLine(c, Offset(3.6f * u, 12f * u), Offset(20.4f * u, 12f * u), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "sun" -> {
                drawCircle(c, 3.9f * u, Offset(12f * u, 12f * u), style = Stroke(sw))
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble())
                    val sx = 12f + 6.2f * Math.cos(a).toFloat()
                    val sy = 12f + 6.2f * Math.sin(a).toFloat()
                    val ex = 12f + 8.4f * Math.cos(a).toFloat()
                    val ey = 12f + 8.4f * Math.sin(a).toFloat()
                    drawLine(c, Offset(sx * u, sy * u), Offset(ex * u, ey * u), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }
            "aa" -> {
                // 大 A
                drawLine(c, Offset(5.0f * u, 17.0f * u), Offset(9.2f * u, 7.0f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(9.2f * u, 7.0f * u), Offset(13.4f * u, 17.0f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(6.9f * u, 13.4f * u), Offset(11.5f * u, 13.4f * u), strokeWidth = sw, cap = StrokeCap.Round)
                // 小 A
                drawLine(c, Offset(14.6f * u, 18.0f * u), Offset(17.2f * u, 11.4f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(17.2f * u, 11.4f * u), Offset(19.8f * u, 18.0f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(15.6f * u, 15.6f * u), Offset(18.8f * u, 15.6f * u), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "magic" -> {
                drawLine(c, Offset(4.6f * u, 19.4f * u), Offset(15.6f * u, 8.4f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(13.0f * u, 6.0f * u), Offset(17.6f * u, 10.6f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(18.6f * u, 3.6f * u), Offset(18.6f * u, 7.2f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(16.8f * u, 5.4f * u), Offset(20.4f * u, 5.4f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(7.6f * u, 4.8f * u), Offset(7.6f * u, 8.4f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(5.8f * u, 6.6f * u), Offset(9.4f * u, 6.6f * u), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "speaker" -> {
                val p = Path().apply {
                    moveTo(3.8f * u, 9.6f * u)
                    lineTo(7.0f * u, 9.6f * u)
                    lineTo(10.8f * u, 5.8f * u)
                    lineTo(10.8f * u, 18.2f * u)
                    lineTo(7.0f * u, 14.4f * u)
                    lineTo(3.8f * u, 14.4f * u)
                    close()
                }
                drawPath(p, c, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawArc(c, -55f, 110f, false, Offset(12.4f * u, 9.0f * u), Size(5.0f * u, 6.0f * u), style = Stroke(sw))
                drawArc(c, -60f, 120f, false, Offset(14.4f * u, 6.4f * u), Size(6.6f * u, 11.2f * u), style = Stroke(sw))
            }
            "api" -> {
                // 钥匙
                drawCircle(c, 4.2f * u, Offset(8.4f * u, 12f * u), style = Stroke(sw))
                drawLine(c, Offset(12.6f * u, 12f * u), Offset(20.4f * u, 12f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(17.4f * u, 12f * u), Offset(17.4f * u, 15.6f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(20.2f * u, 12f * u), Offset(20.2f * u, 15.0f * u), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "info" -> {
                drawCircle(c, 8.4f * u, Offset(12f * u, 12f * u), style = Stroke(sw))
                drawCircle(c, 1.0f * u, Offset(12f * u, 7.9f * u))
                drawLine(c, Offset(12f * u, 11.2f * u), Offset(12f * u, 16.4f * u), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "doc" -> {
                drawRoundRect(c, Offset(4.6f * u, 3.8f * u), Size(14.8f * u, 16.4f * u), CornerRadius(2.2f * u), style = Stroke(sw))
                drawLine(c, Offset(8.0f * u, 9.0f * u), Offset(16.0f * u, 9.0f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(8.0f * u, 12.4f * u), Offset(16.0f * u, 12.4f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(8.0f * u, 15.8f * u), Offset(13.0f * u, 15.8f * u), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "help" -> {
                drawCircle(c, 8.4f * u, Offset(12f * u, 12f * u), style = Stroke(sw))
                drawArc(c, 180f, 150f, false, Offset(8.6f * u, 7.2f * u), Size(6.8f * u, 6.4f * u), style = Stroke(sw))
                drawLine(c, Offset(12f * u, 13.8f * u), Offset(12f * u, 14.8f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawCircle(c, 1.0f * u, Offset(12f * u, 17.4f * u))
            }
            "logout" -> {
                drawLine(c, Offset(13.2f * u, 4.4f * u), Offset(6.6f * u, 4.4f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(6.6f * u, 4.4f * u), Offset(6.6f * u, 19.6f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(6.6f * u, 19.6f * u), Offset(13.2f * u, 19.6f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(11.4f * u, 12f * u), Offset(20.4f * u, 12f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(17.2f * u, 8.8f * u), Offset(20.4f * u, 12f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(20.4f * u, 12f * u), Offset(17.2f * u, 15.2f * u), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "phone" -> {
                val p = Path().apply {
                    moveTo(6.8f * u, 4.2f * u)
                    lineTo(8.6f * u, 4.2f * u)
                    lineTo(9.7f * u, 8.3f * u)
                    lineTo(7.8f * u, 9.6f * u)
                    cubicTo(8.8f * u, 13.4f * u, 11.2f * u, 15.8f * u, 15.0f * u, 16.8f * u)
                    lineTo(16.3f * u, 14.9f * u)
                    lineTo(20.4f * u, 16.0f * u)
                    lineTo(20.4f * u, 17.8f * u)
                    cubicTo(20.4f * u, 18.8f * u, 19.6f * u, 19.7f * u, 18.6f * u, 19.7f * u)
                    cubicTo(10.4f * u, 19.7f * u, 4.3f * u, 13.6f * u, 4.3f * u, 5.4f * u)
                    cubicTo(4.3f * u, 4.4f * u, 5.2f * u, 4.2f * u, 6.8f * u, 4.2f * u)
                    close()
                }
                drawPath(p, c, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "mail" -> {
                drawRoundRect(c, Offset(3.6f * u, 5.4f * u), Size(16.8f * u, 13.2f * u), CornerRadius(2.2f * u), style = Stroke(sw))
                drawLine(c, Offset(4.4f * u, 7.0f * u), Offset(12f * u, 13.0f * u), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(19.6f * u, 7.0f * u), Offset(12f * u, 13.0f * u), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "wechat" -> {
                val g = Color(0xFF07C160)
                drawOval(g, Offset(2.6f * u, 4.0f * u), Size(13.6f * u, 11.2f * u))
                drawOval(g, Offset(9.4f * u, 10.2f * u), Size(12.0f * u, 9.8f * u))
                drawCircle(Color.White, 1.0f * u, Offset(7.6f * u, 9.2f * u))
                drawCircle(Color.White, 1.0f * u, Offset(11.2f * u, 9.2f * u))
                drawCircle(Color.White, 0.9f * u, Offset(13.6f * u, 14.6f * u))
                drawCircle(Color.White, 0.9f * u, Offset(16.6f * u, 14.6f * u))
            }
            "device" -> {
                drawRoundRect(c, Offset(6.6f * u, 2.8f * u), Size(10.8f * u, 18.4f * u), CornerRadius(2.4f * u), style = Stroke(sw))
                drawLine(c, Offset(10.4f * u, 18.4f * u), Offset(13.6f * u, 18.4f * u), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "back" -> {
                drawLine(c, Offset(14.8f * u, 4.6f * u), Offset(8.4f * u, 12f * u), strokeWidth = 2.0f * u, cap = StrokeCap.Round)
                drawLine(c, Offset(8.4f * u, 12f * u), Offset(14.8f * u, 19.4f * u), strokeWidth = 2.0f * u, cap = StrokeCap.Round)
            }
            "chevron" -> {
                drawLine(c, Offset(9.6f * u, 5.8f * u), Offset(16.2f * u, 12f * u), strokeWidth = 1.9f * u, cap = StrokeCap.Round)
                drawLine(c, Offset(16.2f * u, 12f * u), Offset(9.6f * u, 18.2f * u), strokeWidth = 1.9f * u, cap = StrokeCap.Round)
            }
            "close" -> {
                drawLine(c, Offset(6.6f * u, 6.6f * u), Offset(17.4f * u, 17.4f * u), strokeWidth = 1.9f * u, cap = StrokeCap.Round)
                drawLine(c, Offset(17.4f * u, 6.6f * u), Offset(6.6f * u, 17.4f * u), strokeWidth = 1.9f * u, cap = StrokeCap.Round)
            }
            "check" -> {
                drawLine(c, Offset(5.4f * u, 12.4f * u), Offset(10.2f * u, 17.2f * u), strokeWidth = 2.0f * u, cap = StrokeCap.Round)
                drawLine(c, Offset(10.2f * u, 17.2f * u), Offset(18.6f * u, 7.2f * u), strokeWidth = 2.0f * u, cap = StrokeCap.Round)
            }
        }
    }
}
