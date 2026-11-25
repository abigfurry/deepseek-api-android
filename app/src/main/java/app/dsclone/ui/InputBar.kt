package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInteropFilter
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yuyuyu.deepseek.R
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.DsLayout
import com.yuyuyu.deepseek.ui.theme.DsMetric
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

@Composable
fun ToggleChip(text: String, on: Boolean, onClick: () -> Unit) {
    Chip(icon = 0, text = text, on = on, onClick = onClick, tapWidth = 114.29.dp)
}

/**
 * 官方 chip：药丸高 30.10dp、圆角 15.05dp，点击区 47.62dp 高。
 * 选中态：底 #EDF3FE、图标 #426EFE、文字 #191B23；未选中：白底 + 1dp #EDEDED 描边。
 */
@Composable
private fun Chip(
    icon: Int,
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    tapWidth: androidx.compose.ui.unit.Dp,
    lottieRes: Int = 0,
    lottieOnRes: Int = 0,
    enabled: Boolean = true,
) {
    val ds = LocalDsColors.current
    // 官方 chip：药丸高 30dp、圆角 15dp（胶囊）—— 实测 audit-chat.png mask bbox y 2002..2061px
    // 窄屏（640dp）官方 2.8.0 实测：药丸 80×26、圆角 13、字号 ≈14sp、图标 13
    val shape = RoundedCornerShape(DsMetric.chipRadius.dp)
    // ── 官方逐像素实测（audit-chat.png / audit-dark-chat.png，1600×2176 @density2，px÷2=dp）──
    //   药丸视觉盒 93.5×30dp（bbox x 126..313px、y 2002..2061px）、圆角=高/2（胶囊）、1dp 描边
    //   选中浅：#EDF3FE 底 / #D3E2FF 描边 / #426EFE 图标+文字（药丸内取色）
    //   选中深：#283142 底 / #33436B 描边 / #6799FE 图标+文字（audit-dark-chat.png 同 bbox）
    //   未选中：白底 / 1dp #EDEDED / 图标 #8E9094 / 文字 #191B23
    //           （出处 OFFICIAL-UI-SPEC.md §4、HANDOVER.md；本轮截图两 chip 均为选中态）
    //   文字：4 个汉字墨迹 50.5dp、字符起点差 13.0dp → **13sp**（原先 14sp，比官方大约 7%）
    //   图标：墨迹 13.5dp；原先 17.52dp 盒实际渲染 15.5dp，改成 16dp 盒后≈14.2dp
    //   内容在药丸内**水平居中**：官方左留白 12.5dp / 右留白 12dp，
    //   原先 start=7.61dp 左对齐 → 视觉上偏左
    val darkTheme = ds.cardBg.luminance() < 0.5f
    // 官方未选中态图标与文字同色（a11-off-10-empty 实测：浅色近黑 #191B23；
    // a11-off-darkchat2 实测：深色近白 #F8F8F8）。之前浅色用灰 #8E9094 是错的。
    val chipOffIcon = if (darkTheme) Color(0xFFF5F6F8) else Color(0xFF191B23)
    val chipDisabled = Color(0xFFB9BABB)  // 官方禁用态图标/文字色，来源同上
    val fill by androidx.compose.animation.animateColorAsState(
        targetValue = if (on) ds.chipOn else ds.cardBg,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "chipFill",
    )
    val borderCol by androidx.compose.animation.animateColorAsState(
        targetValue = if (on) ds.chipOnBorder else ds.chipBorder,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "chipBorder",
    )
    val iconCol by androidx.compose.animation.animateColorAsState(
        targetValue = if (!enabled) chipDisabled else if (on) ds.chipIcon else chipOffIcon,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "chipIconColor",
    )
    val textCol by androidx.compose.animation.animateColorAsState(
        targetValue = if (!enabled) chipDisabled else if (on) ds.chipIcon else ds.chipText,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "chipTextColor",
    )
    // 官方图标墨迹 14dp（audit-chat.png chip1 图标墨迹 x150..177 = 28px）。
    // 本工程 lottie/矢量资产的墨迹/盒比 ≈ 0.9375 → 15dp 盒渲染 14.06dp 墨迹。
    val iconSize = DsMetric.chipIcon
    // 官方 pill：宽 93.5dp（实测 x 126..313px = 63..156.5dp）、高 30dp，两颗之间 8dp，
    // 内容水平居中（官方图标墨迹左缘距药丸左 12.5dp、文字右缘距药丸右 12dp）。
    // 字体大小档位 = 官方 density+fontScale 语义：density 变大的同时 fontScale 可能再放大（最大档 1.35），
    // 所以药丸不能写死宽度，否则 chip 文字会被 clip 到药丸外（用户实测：调大字体后「深度思考」画到外面）。
    // 官方 chip 是内容自适应（icon + gap + text + 水平内边距），因此这里用 widthIn(min=实测宽度) + 内边距，
    // 文字仍单行不换行（maxLines=1 / softWrap=false），大字体时药丸整体变宽而不是裁字。
    Row(
        Modifier
            .widthIn(min = if (DsLayout.narrow) DsMetric.chipW.dp else tapWidth)
            .height(DsMetric.chipH.dp)
            .clip(shape)
            .border(1.dp, borderCol, shape)
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
            if (lottieRes != 0) {
                // 两个状态各预载一份 composition，切换时不再出现"图标消失一下"的空档。
                // 颜色问题：lottie-compose 6.6 的 LottieAnimation 没有 colorFilter 参数，
                // 官方原始资产里的颜色是 OFF #0F1115（近黑）/ ON #426EFE，
                // 而官方 chip 实际是 OFF 图标 #8E9094、ON 浅 #426EFE / 深 #6799FE（深色截图实测），
                // 所以这里在解析前把 JSON 里的颜色三元组替换成当前主题色（见 recolorLottieColor）。
                val ctx = androidx.compose.ui.platform.LocalContext.current
                // 浅色近黑 #191B23 → 0.098,0.106,0.137；深色近白 #F8F8F8 → 0.973,0.973,0.973
                // #6799FE → 0.404,0.600,0.996；#426EFE → 0.259,0.431,0.996
                val offHex = if (darkTheme) "0.973,0.973,0.973" else "0.098,0.106,0.137"
                val onHex = if (darkTheme) "0.404,0.600,0.996" else "0.259,0.431,0.996"
                val specOff = remember(lottieRes, darkTheme) {
                    com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(
                        recolorLottieColor(ctx, lottieRes, "0.059,0.067,0.082", offHex)
                    )
                }
                val specOn = remember(lottieRes, lottieOnRes, darkTheme) {
                    com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(
                        recolorLottieColor(
                            ctx,
                            if (lottieOnRes != 0) lottieOnRes else lottieRes,
                            "0.259,0.431,0.996",
                            onHex,
                        )
                    )
                }
                val compOff by com.airbnb.lottie.compose.rememberLottieComposition(specOff)
                val compOn by com.airbnb.lottie.compose.rememberLottieComposition(specOn)
                val play = remember { androidx.compose.animation.core.Animatable(1f) }
                val dur = if (lottieRes == R.raw.think) 433 else 500
                androidx.compose.runtime.LaunchedEffect(on) {
                    play.snapTo(0f)
                    play.animateTo(1f, androidx.compose.animation.core.tween(dur))
                }
                // 与文字同一条 180ms 曲线做颜色交叉淡入，避免"图标先蓝、文字后蓝"
                val tintMix by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (on) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(180),
                    label = "chipIconTintMix",
                )
                Box(Modifier.size(iconSize.dp)) {
                    if (compOff != null) {
                        com.airbnb.lottie.compose.LottieAnimation(
                            composition = compOff,
                            progress = { play.value },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(1f - tintMix),
                        )
                    }
                    if (compOn != null) {
                        com.airbnb.lottie.compose.LottieAnimation(
                            composition = compOn,
                            progress = { play.value },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(tintMix),
                        )
                    }
                }
                Spacer(Modifier.width(DsMetric.chipIconGap.dp))
            } else if (icon != 0) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = iconCol,
                    modifier = Modifier.size(iconSize.dp),
                )
                Spacer(Modifier.width(DsMetric.chipIconGap.dp))
            }
        Text(
            text = text,
            // 官方实测：字符起点差 13.0dp、4 字墨迹 50.5dp → 13sp（原 14sp 偏大）
            fontSize = DsMetric.chipTextSize.sp,
            lineHeight = DsMetric.chipLineH.sp,
            // 官方 chip 文字墨迹覆盖率比 Normal 多约 29%（928 vs 719）→ Medium(500)
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            color = textCol,
            // 官方墨迹 y 2020..2044、覆盖率 928.25（与 Medium(500) 完全一致，Normal 只有 719.4）
        )
    }
}

/**
 * 读取 raw 里的 Lottie JSON，并把颜色三元组 `from` 替换成 `to` 后交给
 * LottieCompositionSpec.JsonString 解析。
 *
 * 为什么不用 colorFilter：lottie-compose 6.6.2 的 LottieAnimation / rememberLottiePainter
 * 签名里都没有 colorFilter（javap 确认），只能改 JSON。
 * 官方资产颜色：think.json / globe.json = 0.059,0.067,0.082（#0F1115）；
 * think_on.json / globe_on.json = 0.259,0.431,0.996（#426EFE）。
 */
private fun recolorLottieColor(
    ctx: android.content.Context,
    resId: Int,
    from: String,
    to: String,
): String {
    val raw = ctx.resources.openRawResource(resId).bufferedReader().use { it.readText() }
    // 两个 raw 里颜色只以无空格形式出现（"0.059,0.067,0.082,1"）
    return if (raw.contains(from)) raw.replace(from, to) else raw
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    attachments: List<Attachment>,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    sending: Boolean,
    thinking: Boolean,
    search: Boolean,
    editing: Boolean,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onCancelEdit: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    attachOpen: Boolean = false,
    showFooter: Boolean = true,
    voiceMode: Boolean = false,
    voiceListening: Boolean = false,
    /** 正在按住说话（卡片内容换成语音遮罩，高度不变） */
    voiceHolding: Boolean = false,
    /** 当前电平 0..1 */
    voiceLevel: Float = 0f,
    /** 55 段幅度（官方 waveformMagnitudesProducer 是 55 条） */
    voiceBands: FloatArray = FloatArray(55),
    onToggleVoice: () -> Unit = {},
    onVoiceStart: () -> Unit = {},
    onVoiceStop: () -> Unit = {},
    /** 输入框获得焦点（键盘要弹出）时回调：用来收起已经打开的附件面板 */
    onFieldFocused: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
    onVoiceCancelArm: (Boolean) -> Unit = {},
    // 上下文用量圈（「+」左边 22dp 细环）：已用 tokens / 上限 tokens，调用方在 DsApp 里算好传进来
    contextUsed: Int = 0,
    contextLimit: Int = 131072,
) {
    val ds = LocalDsColors.current
    // 官方输入区底部（ChatSessionBottomBar.kt:1441 SafeAreaBottomSpacer）：
    // 键盘收起时是 systemBars.bottom + 4dp 的留白；键盘弹出时换成 ime inset + 说明文字。
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    // ── 焦点 / IME 相关状态（只碰这些，不改任何卡片几何）──────────────────────
    val fieldFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyScope = androidx.compose.runtime.rememberCoroutineScope()
    // 主动「要焦点 + 要键盘」——修「返回键收起 IME 后再点输入框弹不出来」的核心：
    // 系统返回键收起 IME 后 BasicTextField 仍然持有焦点，此时单独的 requestFocus()
    // 是 no-op（早就是 focused），keyboardController.show() 在部分 ROM 上也不会把
    // 已隐藏的输入会话重新拉起来（要切后台再回来才好，就是因为那时系统清掉了焦点）。
    // 所以「键盘不在」时先强制清一次焦点、让输入会话彻底结束，下一帧再重新
    // requestFocus() + show()，等价于给输入框一次全新的输入会话。
    val requestKeyboard: () -> Unit = {
        keyScope.launch {
            if (!imeVisible) {
                runCatching { focusManager.clearFocus(force = true) }
                // 让「失焦」先落地一帧，否则紧接着的 requestFocus 可能被合并成 no-op
                androidx.compose.runtime.withFrameNanos { }
            }
            runCatching { fieldFocus.requestFocus() }
            androidx.compose.runtime.withFrameNanos { }
            keyboard?.show()
        }
    }
    // 发送/清空之后：键盘没显示时不要把「有焦点、没键盘」的假焦点留在输入框上，
    // 否则下一次点输入框的 requestFocus() 会是 no-op（本 bug 的另一条触发路径）。
    val dropFakeFocusIfImeHidden: () -> Unit = {
        if (!imeVisible) runCatching { focusManager.clearFocus(force = true) }
    }
    // 进/出语音态都把焦点与 IME 复位：语音态里 BasicTextField 不在树上（被「按住说话」
    // 占位替换），旧焦点和没收起的键盘会一直留着；回到文字态后第一次点击又会因为
    // 「已聚焦」变成 no-op，键盘弹不出来。
    LaunchedEffect(voiceMode) {
        runCatching { keyboard?.hide() }
        runCatching { focusManager.clearFocus(force = true) }
    }
    // 返回键把键盘收起时，输入框会留下「有焦点、没键盘」的假焦点 —— 主动放掉，
    // 下一次点击才能重新建立输入会话。
    LaunchedEffect(imeVisible) {
        if (!imeVisible) runCatching { focusManager.clearFocus(force = true) }
    }
    // ── 与正文列对齐（官方 audit-chat/audit-dark-chat 逐像素实测）────────────────
    //   · 正文列左/右内边距 62.5dp（ChatParts：MSG_PAD_X / MSG_PAD_END = 62.5），
    //     即正文左缘 = pane 左 + 62.5、正文右缘 = pane 右 − 62.5；
    //   · 800dp 单栏时官方卡 x 51.5..748，但 chip 视觉盒左缘 63.0dp、正文首字墨迹 63.5dp
    //     —— 两者在同一竖线上（差 0.5dp = 1 个物理像素）；
    //   · ⊕ 墨迹 x 667..690、麦克风墨迹 x 708.5..731.5，正文右缘≈736.5。
    // 卡片本身可以比正文列宽（官方也宽约 11dp），但 chip 起点 / 右侧图标必须回到正文列两缘。
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        val paneW = maxWidth
        // 官方单栏实测（audit-chat.png）：卡外框 x 103..1496px = 51.5..748.5dp、宽 697dp；
        // 双栏（pane 494dp）实测卡 x 646..1565px = 323..783dp、宽 460dp、两侧各 17dp。
        // 反推：卡宽 = min(697, pane − 2×17)，且居中 → 单栏 (800−697)/2 = 51.5 ✓、双栏 (494−460)/2 = 17 ✓。
        // 所以外侧总边距必须恰好 17dp（不是 12.2+5=17.2，否则双栏卡宽 459.6dp，卡边会差 1px）。
        // 窄屏：官方卡 bbox 18..622（宽 604 = 640-36）
        val outerPad = DsMetric.cardMarginX.dp
        // 与 Surface 里原有逻辑保持一致：以**窗口**宽判断是否平板（两栏时 pane 可能 <600 但窗口 >600）
        val sidePad = if (DsLayout.narrow || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) 0.dp else 5.dp
        // 卡片始终在 pane 内水平居中 → 卡左 = (paneW − cardW)/2
        val cardW = minOf(DsMetric.CARD_MAX_W.dp, paneW - (outerPad + sidePad) * 2f)
        // 卡片外框有 1dp 描边，官方内容（行）整体再内缩 1dp：卡外框 y 1885..2085px=100dp，
        // 内容区 y 1887..2083px=98dp；水平方向同理（chip 左缘 63dp = 卡左 51.5+1+10.5）。
        // chip 起点固定为「卡片内容左缘 + 10.5dp」（单栏 51.5+1+10.5=63 ✓、双栏 323+1+10.5=334.5 ✓），
        // 不再按正文列反推 —— 双栏时正文列 38.5dp 与 chip 起点不是同一条线（官方 chip 在卡内固定 10.5dp）。
        val chipStartPad = DsMetric.chipStartPad.dp
        // 右侧图标组：官方 ⊕ 墨迹 23.5dp、麦克风墨迹 23.5dp；⊕ 墨迹中心 678.5dp、
        // 麦克风墨迹中心 720dp（单栏），双栏 713 / 754.5dp —— 两栏都满足：
        //   麦克风盒（25dp）右缘 = 卡内容右缘 − 15dp；⊕ 盒右缘 = 麦克风盒左缘 − 16.5dp。
        val iconEndPad = DsMetric.inputIconEndPad.dp
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = outerPad)
        ) {
        if (editing) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "正在编辑已发送的消息",
                    fontSize = 12.sp,
                    color = ds.subText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "取消",
                    fontSize = 12.sp,
                    color = DsBlue,
                    modifier = Modifier
                        .clickable {
                            onCancelEdit()
                            // 「取消编辑」会把输入框清空，同样不能留下假焦点
                            dropFakeFocusIfImeHidden()
                        }
                        .padding(6.dp),
                )
            }
        }
        // 输入卡宽度要跟着「可用栏宽」拉长拉短：抽屉展开时栏宽 494dp → 卡片 460dp，
        // 收起抽屉后栏宽 800dp → 卡片跟着变宽（官方就是这样，不是写死 460dp）。
        // 注意外层已经有一层 12dp 边距，这里只补差额：平板补 5dp（12+5=17 → 494-34=460 实测），
        // 手机补 0（411-24=386.7 实测）。sidePad / cardW / 对齐 inset 已在上面的 BoxWithConstraints 里算好。
        Surface(
            // 官方 InputCard 实测（1600x2176 @2x，中屏双栏 494dp 宽）：
            //   卡片 x 646..1565 = 460dp，左右各 34px = 17dp；外框再套 12dp 边距、
            //   正文宽度上限 460dp 居中 —— 手机(411dp)实测卡片宽 386.7dp 也正好等于 411-24。
            //   卡片 y 1885..2084 = 200px = 100dp，圆角按 1/4 圆弧拟合 r≈24.8dp。
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                // 官方 smali 实测：内容/输入栏宽度上限（ChatSessionBottomBar.kt:269 的 768dp 是外层容器，
                // 单栏 800dp 实测卡片只有 697dp，故上限取 697）。
                // 顺序必须是 padding → widthIn(max) → fillMaxWidth，卡片宽度才等于
                // min(可用栏宽 - 两侧间距, 697dp)，且收在 697 时居中。
                .padding(horizontal = sidePad)
                // 官方：输入卡最大宽 697dp（800dp 窗口下左右各 51.5dp 居中）—— 实测 audit-chat.png 行扫描
                .widthIn(max = DsMetric.CARD_MAX_W.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(DsMetric.cardRadius.dp),
                    clip = false,
                    // Compose/RenderNode 会把颜色 alpha 再乘一个很低的系数（实测 0x0E → 只压暗 2 灰阶），
                    // 这里按实测比例放大到 0x5C，目标是与官方一致：底边往下 255→241、拖尾 20dp。
                    // 深色模式下黑色投影几乎不可见且会脏，这里按明暗切换
                    ambientColor = Color(if (ds.cardBg.luminance() > 0.5f) 0x44000000 else 0x1A000000),
                    spotColor = Color(if (ds.cardBg.luminance() > 0.5f) 0x44000000 else 0x1A000000),
                ),
                // 圆角 ≈24.8dp（拟合 24.83，取 25）—— 实测 audit-chat.png 卡顶左角
            shape = RoundedCornerShape(DsMetric.cardRadius.dp),
            color = ds.cardBg,
            // 官方浅色输入卡 1dp #F0F0F0 边框（实测 audit-chat.png 列扫描 x=800 卡顶/卡底边框行）；
            // 深色用主题里的 inputCardBorder(#333333)，不能写死浅色，否则深色下是一圈白框。
            border = androidx.compose.foundation.BorderStroke(1.dp, ds.inputCardBorder),
            // 官方阴影实测（x=1200 竖切）：卡片底边往下 255-241=14 灰阶、拖尾约 20dp；
            // 卡片上方几乎没有（255→253 只有 7px）。Compose 默认 elevation 阴影太黑太窄
            // （2dp 时 8px 内就到 202），所以用大半径 + 低不透明度近似。
            shadowElevation = 0.dp,
        ) {
            // 官方卡有 1dp 描边，内容整体再内缩 1dp（卡外框 100dp = 1 + 98 + 1）
            Column(Modifier.fillMaxWidth().padding(1.dp)) {
                AttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)
                // ── 输入行：官方 46dp（卡内 y 1887..1979px = 46dp，语音态分隔线 y1979..1980）────
                // 行高 = 行盒 26dp + 上 11.3 + 下 8.7 = 46dp；
                // 占位符墨迹实测 y 1920..1950px（=960..975dp），即内容顶 943.5 + 11.3 + 行内偏移 5.2。
                // 占位符字号实测：官方 8 字墨迹 247px=123.5dp（字符推进 31.1px=15.55dp）
                //   → 16sp + 字距 −0.4sp（本机同字体 17sp 时推进 34px，16sp+−0.8px/字 = 31.2px ✓）
                // 占位符左缘：官方墨迹 x136px=68dp = 卡左 51.5 + 描边 1 + 内边距 16（−0.5 字形左留白）✓
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = DsMetric.cardInputRowH.dp)
                        .padding(
                            // 官方占位符墨迹左缘 136px=68dp：卡左 51.5 + 描边 1 + 内边距 15 + 字形留白 0.5
                            // 窄屏：占位符墨迹 32 = 卡 18 + 描边 1 + 13
                            start = DsMetric.cardPadH.dp,
                            end = DsMetric.cardPadH.dp,
                            top = DsMetric.cardInputPadTop.dp,
                            bottom = DsMetric.cardInputPadBottom.dp,
                        ),
                ) {
                    if (voiceMode) {
                        // 官方 InputFieldVoicePlaceholder（ChatInputField.kt:263）：居中纯文字。
                        // 墨迹实测 x 1045..1167px = 61.5dp（4 字，推进 30.75px）、y 1921..1951；
                        // 颜色 (25,27,35)=#191B23（= ds.bodyText），不是灰色占位色。
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = DsMetric.cardTextFieldMinH.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "按住说话",
                                fontSize = 16.sp,
                                letterSpacing = (-0.4).sp,
                                lineHeight = 26.sp,
                                // 官方墨迹覆盖率 1288.7，本工程 Normal 只有 989.3（比值 1.30 = Normal→Medium）
                                fontWeight = FontWeight.Medium,
                                color = ds.bodyText,
                                // 官方墨迹 x1045..1167 y1921..1950；居中后偏左 1px → x +0.5dp
                                modifier = Modifier.offset(x = 0.5.dp),
                            )
                        }
                    } else {
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(fieldFocus)
                                .onFocusChanged { st ->
                                    // 附件面板开着时点输入框：键盘要出来，先把面板收起（否则两层叠着）
                                    if (st.isFocused) onFieldFocused()
                                }
                                .heightIn(min = DsMetric.cardTextFieldMinH.dp),
                            // 输入正文官方 17sp / 26sp / 字重 Normal —— kt:ChatInputField.kt:105
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = DsMetric.BODY_SIZE.sp,
                                lineHeight = DsMetric.LABEL_LINE.sp,
                                fontWeight = FontWeight.Normal,
                                color = ds.bodyText,
                            ),
                            cursorBrush = SolidColor(DsBlue),
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    onSend()
                                    // 用 IME 的发送键发完也别留下假焦点
                                    dropFakeFocusIfImeHidden()
                                }
                            ),
                            decorationBox = { inner ->
                                if (value.isEmpty()) {
                                    Text(
                                        // chat_input_placeholder_voice：发消息或按住说话 / Type a message or hold to speak
                                        text = I18n.t("发消息或按住说话", "Type a message or hold to speak"),
                                        color = ds.placeholder,
                                        // 官方占位符实测：墨迹 247×31px、色 #B9BABB（深色 #7D7F85）
                                        fontSize = 16.sp,
                                        letterSpacing = (-0.4).sp,
                                        lineHeight = 26.sp,
                                        fontWeight = FontWeight.Normal,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                    if (voiceMode || value.isEmpty() || !imeVisible) {
                        // 透明热区：吃掉所有触摸，输入框不抢焦点、不弹键盘。
                        // 语音态按下即录；文字态短按 requestFocus + show 弹键盘、按住 300ms 直接录。
                        // 「有文字 + 键盘已收起」时也要铺一层：输入框对「已聚焦」的点击不会再拉起
                        // 键盘（正是本 bug 的现场），必须先由热区把输入会话重建起来。
                        // 这一层只负责「点一下弹键盘」，不武装长按录音（有文字时本来就是纯文字输入）。
                        val hVoiceGesture = voiceMode || value.isEmpty()
                        val hScope = androidx.compose.runtime.rememberCoroutineScope()
                        var hJob by remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
                        var hHolding by remember { androidx.compose.runtime.mutableStateOf(false) }
                        var hDownY by remember { androidx.compose.runtime.mutableStateOf(0f) }
                        val hCancelPx = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.toPx() }
                        Box(
                            Modifier
                                .matchParentSize()
                                .pointerInteropFilter { ev ->
                                    when (ev.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            hDownY = ev.rawY
                                            when {
                                                !hVoiceGesture -> Unit // 纯文字：抬手时再弹键盘
                                                voiceMode -> {
                                                    hHolding = true
                                                    runCatching { onVoiceStart() }
                                                    onVoiceCancelArm(false)
                                                }
                                                else -> {
                                                    hJob = hScope.launch {
                                                        kotlinx.coroutines.delay(300)
                                                        hHolding = true
                                                        runCatching { onVoiceStart() }
                                                        onVoiceCancelArm(false)
                                                    }
                                                }
                                            }
                                            true
                                        }

                                        MotionEvent.ACTION_MOVE -> {
                                            if (hHolding) onVoiceCancelArm((hDownY - ev.rawY) > hCancelPx)
                                            true
                                        }

                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                            hJob?.cancel()
                                            hJob = null
                                            when {
                                                hHolding -> {
                                                    hHolding = false
                                                    val up = hDownY - ev.rawY
                                                    if (up > hCancelPx || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                                                        onVoiceCancel()
                                                    } else {
                                                        onVoiceStop()
                                                    }
                                                }
                                                // 文字态（含「有文字但键盘已收起」）抬手：主动把焦点和键盘要回来
                                                !voiceMode -> requestKeyboard()
                                            }
                                            true
                                        }

                                        else -> true
                                    }
                                },
                        )
                    }
                }
                // ── 分隔线：官方 1dp，横贯整卡（x 646..1565），只有语音态可见 ──
                // 两种状态行高完全一致（y 1979..1980），所以这里恒定占位、仅换颜色。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        // 官方语音态分隔线实测 #F0F0F0（ovoice.png y1979..1980 整卡横贯）；
                        // 深色用主题边框色 #333333，不能写死浅色（否则深色是一条亮白线）。
                        .background(if (voiceMode || voiceHolding) ds.inputCardBorder else Color.Transparent),
                )
                // ── 操作行：官方 51dp（卡内 y 1981..2083px = 51dp）；pill 30dp 垂直居中（y 2002..2061）──
                // 官方（800dp 单栏）：chip 药丸 x 126..313 / 329..516（94dp 宽、7.5dp 间距、左缘=内容左+10.5dp）；
                // ⊕ 墨迹 x 1334..1380（23.5dp）、麦克风墨迹 x 1417..1463（23.5dp），
                //   麦克风盒右缘 = 内容右缘 − 15dp、⊕ 盒右缘 = 麦克风盒左缘 − 16.5dp（双栏同规律）。
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(DsMetric.cardActionRowH.dp)
                        .padding(start = chipStartPad, end = iconEndPad),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Chip(
                        // message_r1_button：深度思考 / Think
                        R.drawable.ic_think_outline_16, I18n.t("深度思考", "Think"), thinking, onToggleThinking, 94.dp,
                        lottieRes = R.raw.think, lottieOnRes = R.raw.think_on,
                    )
                    Spacer(Modifier.width(7.5.dp))
                    Chip(
                        // message_search_button：智能搜索 / Search
                        R.drawable.ic_search_web, I18n.t("智能搜索", "Search"), search, onToggleSearch, 94.dp,
                        lottieRes = R.raw.globe, lottieOnRes = R.raw.globe_on,
                    )
                    Spacer(Modifier.weight(1f))
                    // 上下文用量圈：官方 audit-chat / audit-dark-chat / audit-drawer / ovoice 四张
                    // 参考图里 ⊕ 左边都是空的，没有任何圆环/百分比。之前这组会把 ⊕、麦克风整体
                    // 往左挤 40+dp，是「右缘对不上官方墨迹」的主因，所以这里不画（保留函数备查）。
                    // ⊕：盒 25dp（矢量墨迹/盒 ≈0.9375 → 墨迹 23.4dp）；官方墨迹 x 1334..1380 / 1403..1449
                    Box(
                        Modifier
                            .width(DsMetric.inputIconBox.dp)
                            .fillMaxHeight()
                            .clickable(onClick = onAddAttachment),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (attachOpen) R.drawable.ic_add_close_circle_26 else R.drawable.ic_add_outline_26
                            ),
                            contentDescription = if (attachOpen) "关闭" else "上传文件",
                            tint = ds.inputIcon,
                            modifier = Modifier.size(DsMetric.inputIconSize.dp),
                        )
                    }
                    Spacer(Modifier.width(DsMetric.inputIconGap.dp))
                    // 发送 / 麦克风：盒 25dp，中心 = 内容右缘 − 15 − 12.5 = 内容右 − 27.5dp
                    // （单栏 747.5−27.5 = 720dp ✓ 官方墨迹中心；双栏 782−27.5 = 754.5dp ✓）
                    Box(
                        Modifier
                            .width(DsMetric.inputIconBox.dp)
                            .fillMaxHeight()
                            .clickable {
                                if (sending) onStop()
                                else if (value.isNotBlank() || attachments.isNotEmpty()) {
                                    onSend()
                                    // 发送后文本被清空；键盘本来不在就别留下「有焦点、没键盘」的假焦点
                                    dropFakeFocusIfImeHidden()
                                } else Unit
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val showSend = sending || value.isNotBlank() || attachments.isNotEmpty()
                        if (showSend) {
                            // 发送态没有官方截图可对照，保持原有蓝色圆钮（28.57dp，居中在同一个 25dp 盒里）
                            Box(
                                Modifier
                                    // requiredSize：父盒只有 25dp 宽，用 size() 会被压成 25×28.57 的椭圆；
                                    // requiredSize 不受父约束，才是正圆（官方就是 28.57dp 圆钮居中在 25dp 槽里）
                                    .requiredSize(DsMetric.sendBtnSize.dp)
                                    .clip(CircleShape)
                                    .background(DsBlue)
                                    .clickable {
                                        if (sending) onStop() else {
                                            onSend()
                                            dropFakeFocusIfImeHidden()
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(if (sending) R.drawable.ic_stop_fill_20 else R.drawable.ic_send_fill_20),
                                    contentDescription = if (sending) "停止" else "发送",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (sending) 13.14.dp else 15.00.dp),
                                )
                            }
                        } else {
                            // 官方：短按切「按住说话」，长按直接录音
                            val scope = androidx.compose.runtime.rememberCoroutineScope()
                            var pressJob by remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
                            var longPressing by remember { androidx.compose.runtime.mutableStateOf(false) }
                            var downY by remember { androidx.compose.runtime.mutableStateOf(0f) }
                            val cancelPx = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.toPx() }
                            Box(
                                Modifier
                                    .size(DsMetric.inputIconBox.dp)
                                    .pointerInteropFilter { ev ->
                                        when (ev.actionMasked) {
                                            MotionEvent.ACTION_DOWN -> {
                                                downY = ev.rawY
                                                pressJob = scope.launch {
                                                    kotlinx.coroutines.delay(260)
                                                    longPressing = true
                                                    runCatching { onVoiceStart() }
                                                    onVoiceCancelArm(false)
                                                }
                                                true
                                            }

                                            MotionEvent.ACTION_MOVE -> {
                                                if (longPressing) onVoiceCancelArm((downY - ev.rawY) > cancelPx)
                                                longPressing
                                            }

                                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                pressJob?.cancel()
                                                if (longPressing) {
                                                    longPressing = false
                                                    val up = downY - ev.rawY
                                                    if (up > cancelPx || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                                                        onVoiceCancel()
                                                    } else {
                                                        onVoiceStop()
                                                    }
                                                } else {
                                                    onToggleVoice()
                                                }
                                                true
                                            }

                                            else -> false
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (voiceMode) R.drawable.ic_keyboard_outline_26 else R.drawable.ic_voice_outline_26
                                    ),
                                    contentDescription = if (voiceMode) "切换至键盘" else "按住说话",
                                    tint = ds.inputIcon,
                                    modifier = Modifier.size(DsMetric.inputIconSize.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        // 官方四张参考图（audit-chat / audit-dark-chat / audit-drawer / ovoice）键盘收起时
        // 卡片下面**都**有「回答由 AI 生成，仅供参考」：卡底 1042.5dp → 容器顶 +4dp、高 11.5dp，
        // 容器底正好压在系统导航栏上沿（1042.5+15.5 = 1058dp = 2116px）。
        // 官方免责文案实测：墨迹 2093..2115px（y1046.5..1057.5）、宽 248px=124dp、色 #DDDDDD / #454647
        //   → 11sp / 行高 11.5sp（本机同字体推进 22px=11dp ✓）。
        //
        // 2026-11-24 重新对官方 2.9.0 反编译核实（string/样式来源）：
        //   · 文案 = res/values-b+zh+Hans/strings.xml 的 input_a_i_alert =「回答由 AI 生成，仅供参考」，
        //     英文默认值（values/strings.xml）同一条 = "Generated by DeepSeek AI"；
        //     smali 里 0x7f0f017d（= public.xml 的 input_a_i_alert）只被
        //     ChatSessionBottomBarKt.DisclaimerLabel 使用（ChatSessionBottomBar.kt:1412），
        //     就是输入卡下方这一行，不是另有一句「本回答由 AI 生成，内容仅供参考，请仔细甄别」
        //     （全 APK 所有 strings/*.xml + smali 资源引用都没有这句）。
        //   · TextStyle = MaterialTheme.typography.l（= 官方自定义 Typography 的第 l 项，
        //     由 Typography displaySmall 换字体而来）再 copy：
        //     fontSize = 12.sp、lineHeight = 12.sp、letterSpacing 取默认、fontWeight 取默认（400 / Normal）、
        //     fontStyle 取默认（Normal，**不是 Italic**）；颜色 = colorSchemeV2 的 footer 灰（#DDDDDD / #454647）。
        //   · smali 里没有 fontStyle=Italic、也没有 Italic 的 FontStyle 实例被传进这条 Text；
        //     设备实测（2176×1600 截图，官方 2.9.0）这行是正体灰字，不是斜体。
        //   下面保持既有几何不动，只把文字样式钉成官方值（12sp / 行高12sp / Normal / 非斜体 / #DDDDDD）。
        if (showFooter) {
            Box(
                Modifier
                    .fillMaxWidth()
                    // 卡底 2085px → 免责墨迹顶 2093px（4dp）、容器底 2116px（导航栏上沿）
                    .padding(top = DsMetric.footerTopPad.dp)
                    .height(DsMetric.footerH.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    // 官方原文：string input_a_i_alert（zh-Hans「回答由 AI 生成，仅供参考」/ en "Generated by DeepSeek AI"）
                    text = I18n.t("回答由 AI 生成，仅供参考", "Generated by DeepSeek AI"),
                    modifier = Modifier.fillMaxWidth(),
                    // 官方墨迹 248×23px：本机 12sp 时 CJK 推进 24px、字距 −0.8sp(−1.6px) → 22.4px，
                    // 整串墨迹 ≈248px ✓；行高 12sp 让墨迹顶正好落在容器顶（2093px）。
                    fontSize = 12.sp,
                    letterSpacing = (-0.8).sp,
                    lineHeight = 12.sp,
                    // 官方 smali：这条 Text 没覆盖 fontWeight → 用 typography.l 的默认 400（Normal）。
                    // 官方 2.9.0 截图墨迹比 Light(300) 多约 17%，与 Normal 吻合。
                    fontWeight = FontWeight.Normal,
                    // 官方 smali：fontStyle 取默认（Normal），不是 Italic；显式钉住避免主题继承出斜体。
                    fontStyle = FontStyle.Normal,
                    textAlign = TextAlign.Center,
                    color = ds.footer,
                )
            }
        } else if (attachOpen) {
            // 附件面板展开：官方卡底 913dp → 三块面板顶 932dp = 19dp（audit-plus.png）
            Spacer(Modifier.height(19.dp))
        } else {
            Spacer(Modifier.height(15.5.dp))
        }
        }
    }
}

/**
 * 官方附件菜单：输入卡下方的三块方形按钮（拍照 / 相册 / 文件）。
 * 官方：各 222×79dp、圆角 ≈14.4dp、间距 7.5dp、第一卡 x=59dp、底 #F5F5F5 —— 实测 audit-plus.png
 */
@Composable
fun AttachmentTileRow(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
    recentImages: List<android.net.Uri> = emptyList(),
    selectedUris: Set<String> = emptySet(),
    onPickRecent: (android.net.Uri) -> Unit = {},
) {
    val ds = LocalDsColors.current
    // 官方 800dp 窗口下三卡总宽 222×3+7.5×2=681，居中 → 左右各 59dp。
    // 窄屏（640dp）：官方 2.8.0 打开态实测面板 [11,1248][1269,1733]（density 1.875 → 我们 density 2.0 的等价 dp）：
    //   缩略图/三块方块的左缘同为 49px = 26.1 官方dp = **24.5 本工程dp**（面板左缘 5.5 + 内边距 19.0），右缘对称，
    //   所以最近图片条与三块方块共用同一个水平 inset，保证第一条缩略图与「拍照」块左缘在同一条竖线上。
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
    val hInset = if (wide) 59.dp else 24.5.dp
    // 必须用 Column 包起来：AttachmentTileRow 在 DsApp 的 AnimatedVisibility 里不是 ColumnScope，
    // 直接并列 emit 两个 layout 节点会**重叠**（快捷图片条盖在三块方块上）。
    Column(Modifier.fillMaxWidth()) {
        // 官方附件面板顶部 = 输入卡下方「最近图片」横滑快捷条（UploadPanelImageScroller）：
        // 75dp 缩略图 + 24dp 勾选圈，点一张直接加为图片附件（快捷上传）。无权限/无图时不显示。
        RecentImageStrip(
            images = recentImages,
            selected = selectedUris,
            onToggle = onPickRecent,
            horizontalInset = hInset,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = hInset,
                    end = hInset,
                    top = 0.dp,
                    // 官方：面板底 2022px=1011dp → 导航栏上沿 2116px=1058dp，空 47dp
                    bottom = 47.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(7.5.dp),
        ) {
            // 官方三块图标墨迹：相机 37×33、相册 34×30、回形针 31×35px —— 本工程三个矢量的墨迹/盒比不同，
            // 所以每个图标单独给盒尺寸（相册图标本身墨迹偏大 → 18.7dp）。
            AttachTile(R.drawable.ic_camera_outline_20, "拍照", Modifier.weight(1f), onCamera, 20.5.dp)
            AttachTile(R.drawable.ic_photo_outline_20, "相册", Modifier.weight(1f), onGallery, 19.4.dp)
            AttachTile(R.drawable.ic_paperclip_outline_20, "文件", Modifier.weight(1f), onFile, 20.5.dp)
        }
    }
}

@Composable
private fun AttachTile(
    icon: Int,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp = 20.5.dp,
) {
    val ds = LocalDsColors.current
    Column(
        modifier
            // 官方：附件卡高 79dp、圆角 ≈14.4dp、底 #F5F5F5 —— 实测 audit-plus.png
            .height(79.dp)
            .clip(RoundedCornerShape(14.4.dp))
            .background(ds.tileBg)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = ds.inputIcon,
            // 官方 tile 图标墨迹 37×33px = 18.5×16.5dp（audit-plus.png tile1 1902..1934/322..358）
            // 本工程图标墨迹/盒比 ≈ 0.875 → 21dp 盒（墨迹 18.4×16.6dp）
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.height(4.dp))
        // 官方：标签文字节点 29×21dp、色 #0F0F0F —— 实测 audit-plus.png
        // 官方 tile 标签墨迹面积 615，Normal 只有 505（比值 1.22）→ Medium(500)
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            color = ds.rowText,
        )
    }
}

// =====================================================================================
//  上下文用量圈 + 百分比
// =====================================================================================

/**
 * 22dp 细环（2.5dp 描边）：先画整圈轨道，再按进度从 -90° 顺时针画前景弧，右侧跟 12sp 百分比文字。
 *
 * ⚠ 官方参数出处说明（2026-11-22）：**官方 2.9.0 APK / 本轮官方截图里没有这个组件**
 * （decoded_full 全量 smali、res 各语言 values 目录的 strings.xml 均无 token/context usage 组件与
 * 「上下文/已用/剩余」文案；audit-chat / audit-dark-chat / audit-plus / audit-voice / off-chat
 * 输入卡里只有 chip + ⊕ + 麦克风）。用户所说的「官方有」应是更新版官方 App，本轮拿不到截图，
 * 因此下面这些 B 组尺寸/颜色**不是官方实测**，只是沿用官方色板：
 *   · 环 22dp、描边 2.5dp、起点 -90°、顺时针          —— 本工程既有实现，待新版官方截图复核
 *   · 轨道 = 官方描边灰（浅 #EDEDED / 深 #333333，同 chip 未选中描边）
 *   · 进度 = 官方主色（浅 #426EFE / 深 #6799FE，同 chip 选中态图标/文字）
 *   · 接近满：>90% #FF9F0A、>98% #FF5757               —— 沿用既有实现，官方值未知
 *   · 百分比文字 12sp / Normal / #8E9094（官方 secondary 灰），与环间距 4dp、
 *     环在左、百分比在右，位置在 chip 行右侧、⊕ 左边（与正文右缘对齐）
 * 点一下圆圈弹出小气泡（点外面消失），圆圈本身 clickable，不影响「+」的点击。
 */
@Composable
private fun ContextUsageRing(
    used: Int,
    limit: Int,
    modifier: Modifier = Modifier,
) {
    val ds = LocalDsColors.current
    val progress = if (limit <= 0) 0f else (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    // 官方色板：轨道用 chip 描边灰、进度用官方主蓝（浅 #426EFE / 深 #6799FE）
    val track = ds.chipBorder
    val fg = when {
        progress > 0.98f -> Color(0xFFFF5757)
        progress > 0.90f -> Color(0xFFFF9F0A)
        else -> ds.chipIcon
    }
    var open by remember { mutableStateOf(false) }
    // 气泡定位：环高 22dp + 8dp 间距，气泡贴在圆环正上方
    val bubbleLift = with(LocalDensity.current) { 34.dp.roundToPx() }

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 2.5.dp.toPx()
                val inset = sw / 2f
                val arcSize = Size(size.width - sw, size.height - sw)
                val topLeft = Offset(inset, inset)
                // 整圈轨道（官方 chip 描边灰：浅 #EDEDED / 深 #333333）
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = sw),
                )
                if (progress > 0f) {
                    if (progress >= 1f) {
                        // 满环不再用圆头，避免首尾叠出一个凸起
                        drawArc(
                            color = fg,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = sw),
                        )
                    } else {
                        drawArc(
                            color = fg,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = sw, cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
        if (open) {
            Popup(
                alignment = Alignment.BottomEnd,
                offset = IntOffset(0, -bubbleLift),
                onDismissRequest = { open = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                ContextUsageBubble(used = used, limit = limit)
            }
        }
    }
}

/** 圆环点开的小气泡：「已用 12.3k / 128k」「剩余 115.7k」 */
@Composable
private fun ContextUsageBubble(used: Int, limit: Int) {
    val ds = LocalDsColors.current
    val remaining = (limit - used).coerceAtLeast(0)
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ds.cardBg)
            .border(1.dp, ds.chipBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "已用 ${formatTokenCount(used)} / ${formatTokenCount(limit)}",
            fontSize = 13.sp,
            color = ds.rowText,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "剩余 ${formatTokenCount(remaining)}",
            fontSize = 12.sp,
            color = ds.secondary,
            maxLines = 1,
        )
    }
}

/** token 数字格式化：>= 1000 用 k，保留 1 位小数（128.0k 显示成 128k） */
private fun formatTokenCount(n: Int): String {
    if (n < 1000) return n.toString()
    var s = String.format(java.util.Locale.US, "%.1f", n / 1000.0)
    if (s.endsWith(".0")) s = s.dropLast(2)
    return s + "k"
}

/** 卡片内的语音波形：55 条竖条，颜色随时间在蓝色之间流动（官方 animatedWaveformColor） */
@Composable
private fun VoiceWaveInCard(level: Float, bands: FloatArray, canceling: Boolean) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "waveColor")
    val t by tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "waveT",
    )
    val c1 = Color(0xFF4C8DF6)
    val c2 = Color(0xFF9DBBFA)
    val mix = (0.5f + 0.5f * kotlin.math.sin(t * 2f * Math.PI.toFloat()).toFloat())
    val barColor = if (canceling) Color(0xFFFF5757)
    else androidx.compose.ui.graphics.lerp(c1, c2, (mix * 0.6f).coerceIn(0f, 1f))
    val n = 55
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DsMetric.CARD_PAD_H.dp)
            .height(24.76.dp),
        horizontalArrangement = Arrangement.spacedBy(1.6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until n) {
            // 中间高两边低的包络 + 实际幅度
            val d = kotlin.math.abs(i - (n - 1) / 2f) / ((n - 1) / 2f)
            val env = 0.35f + 0.65f * (1f - d * d)
            val lv = bands.getOrElse(i) { level }.coerceIn(0f, 1f)
            val hh = (3f + 21f * lv) * env
            Box(
                Modifier
                    .weight(1f)
                    .height(hh.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor),
            )
        }
    }
}
