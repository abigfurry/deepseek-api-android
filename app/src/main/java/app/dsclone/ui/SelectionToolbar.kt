package com.yuyuyu.deepseek.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.yuyuyu.deepseek.ui.theme.LocalDsColors
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * 官方选择工具条反编译实测（com.deepseek.chat.ui.markdown.selection.TextSelectionToolbar.kt，
 * smali: q3b.a = ProvideTextSelectionToolbar:100 / q3b.d = SelectionToolbarPopup:278 /
 * q3b.b = SelectionToolbarCard:356 / q3b.c = SelectionToolbarItem:410，定位器 jv9.s()）：
 *
 * 卡片（q3b.b:356）
 *   shape      = RoundedCornerShape(16dp)                    (0x41800000 = 16.0f)
 *   elevation  = 16dp，ambient/spot 均为 black @ 12%          (Le4a(F=16, 0.12f, …))
 *   padding    = start16 / top8 / end16 / bottom24            (q3b.a = 8f + 16f = 24f)
 *   再 padding = 6dp 四边
 *   Row 间距   = 4dp
 *
 * 按钮（q3b.c:410）
 *   shape     = RoundedCornerShape(12dp)
 *   widthIn   = min 58dp / max 100dp                          (wca.t(58f, 100f))
 *   padding   = horizontal 4dp / vertical 6dp                 (b08.S(4f, 6f))
 *   icon      = 20dp（painterResource(item.iconRes)）
 *   icon→文本 = 3dp（Spacer height 3dp）
 *   文本      = 10.5sp（v37.B(10.5)），行高 15sp、字距 0.5sp（v37.C(0xf)、B(0.5)）
 *
 * 定位（jv9.s，TextSelectionToolbar.kt 的 PopupPositionProvider 实现）
 *   选区 rect 已在 SelectionManager 中做过:
 *     rect = selectedRegion.intersect(visibleBounds).translate(containerPositionInRoot)
 *   即“根坐标(窗口坐标)”，PopupPositionProvider 的返回值同样要求窗口坐标 —— 直接返回绝对坐标，
 *   不要再去减 anchorBounds（旧实现减了 anchorBounds.left/top，是位置乱飘的根因）。
 *   x: (sel.left + sel.right)/2 - popupW/2，再 clamp 到 [clip.left, max(clip.left, clip.right-popupW)]
 *   y: 先候选上方 top = sel.top - popupH + 16dp（jv9.d = round(24dp)-round(8dp)，卡片底部 24dp
 *      内边距使得正文视觉间距 = 8dp）；若 top >= clip.top + 8dp 用上方；
 *      否则若 sel.bottom + popupH <= clip.bottom - 8dp 放下方（top = sel.bottom），
 *      再不行 top = clip.top + 8dp；最后把 y clamp 进可见区。
 *   jv9.b = 8dp（gap），jv9.d = 16dp（overlap），clip = 可见内容区（这里退化为窗口）。
 *
 * 出现/消失动画：官方走 Lj00(animationSpec) 分支（q3b_b:388 处），成功反混淆出的
 * 具体曲线常数被 R8 混淆无法确定（spec 对象字段为 4.0f/tween 类），此处用 120ms tween
 * 的 alpha 0→1 + scale 0.92→1 近似。
 *
 * 交互修正：Popup 改为 focusable=true + dismissOnClickOutside/dismissOnBackPress，
 * 点击空白/返回键即 onDismissRequest -> TextToolbar.hide()，解决“点空白不消失”。
 *
 * 对外 API 不变：DsSelectionToolbarHost(messageText, content)。
 */

private val CARD_RADIUS = 16.dp
private val CARD_PAD_H = 16.dp
private val CARD_PAD_TOP = 8.dp
private val CARD_PAD_BOTTOM = 24.dp
private val CARD_INNER = 6.dp
private val ITEM_GAP = 4.dp
private val ITEM_MIN_W = 58.dp
private val ITEM_MAX_W = 100.dp
private val ITEM_RADIUS = 12.dp
private val ITEM_PAD_H = 4.dp
private val ITEM_PAD_V = 6.dp
private val ICON_SIZE = 20.dp
private val ICON_LABEL_GAP = 3.dp
private val LABEL_SIZE = 10.5.sp
private const val POPUP_OVERLAP_DP = 16f
private const val POPUP_GAP_DP = 8f
private const val ENTER_MS = 120

/** TextToolbar 只是回调宿主，真正的 UI 由 Compose 读这个 state 画出来 */
class DsSelectionToolbarState {
    var visible by mutableStateOf(false)
    var rect by mutableStateOf(Rect.Zero)
    var copy: (() -> Unit)? = null
    var selectAll: (() -> Unit)? = null
    var paste: (() -> Unit)? = null

    fun hide() {
        visible = false
    }
}

private class DsTextToolbar(private val st: DsSelectionToolbarState) : TextToolbar {
    override val status: TextToolbarStatus
        get() = if (st.visible) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        // SelectionManager 有时会传入退化 rect（空选区 / 不可见）；此时不弹工具条
        if (!rect.width.isFinite() || !rect.height.isFinite() ||
            rect.left.isNaN() || rect.top.isNaN() ||
            rect.width <= 0f || rect.height <= 0f
        ) {
            st.hide()
            return
        }
        st.rect = rect
        st.copy = onCopyRequested
        st.paste = onPasteRequested
        st.selectAll = onSelectAllRequested
        st.visible = true
    }

    override fun hide() {
        st.hide()
    }
}

/**
 * 官方 jv9 的定位算法：水平居中于选区并钳到可见区；优先放在选区上方，
 * 上方放不下再翻到下方。返回的是窗口绝对坐标（Compose PopupPositionProvider 约定）。
 */
private class SelectionToolbarPositioner(
    private val selection: Rect,
    private val gapPx: Float,
    private val overlapPx: Float,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val winW = windowSize.width
        val winH = windowSize.height
        if (w <= 0 || h <= 0 || winW <= 0 || winH <= 0) return IntOffset.Zero

        val clipL = 0
        val clipT = 0
        val clipR = winW
        val clipB = winH
        val gap = gapPx.roundToInt().coerceAtLeast(0)

        // ---- x：选区中心 ----（jv9: (x1+x2)/2 - popupW/2，clamp 到 [clipL, clipR-popupW]）
        val center = (selection.left + selection.right) / 2f
        var x = (center - w / 2f).roundToInt()
        val maxX = max(clipL, clipR - w)
        x = x.coerceIn(clipL, maxX)

        // ---- y：上方优先 + 翻转 + 贴边钳制 ----（jv9）
        val aboveTop = (selection.top - h + overlapPx).roundToInt()
        val topLimit = clipT + gap
        val y: Int = if (aboveTop >= topLimit) {
            aboveTop
        } else {
            val belowTop = selection.bottom.roundToInt()
            if (belowTop + h <= clipB - gap) belowTop else topLimit
        }
        val maxY = max(topLimit, clipB - h - gap)
        val yClamped = y.coerceIn(topLimit, maxY)

        return IntOffset(x, yClamped)
    }
}

/**
 * 把整条消息包进来：提供官方样式工具条，并把它按官方算法摆到选区上方/下方。
 * 对外 API 与旧实现完全一致。
 */
@Composable
fun DsSelectionToolbarHost(
    messageText: String,
    content: @Composable () -> Unit,
) {
    val st = remember { DsSelectionToolbarState() }
    val toolbar = remember(st) { DsTextToolbar(st) }
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val gapPx = with(density) { POPUP_GAP_DP.dp.toPx() }
    val overlapPx = with(density) { POPUP_OVERLAP_DP.dp.toPx() }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()
        if (st.visible) {
            Popup(
                popupPositionProvider = SelectionToolbarPositioner(
                    selection = st.rect,
                    gapPx = gapPx,
                    overlapPx = overlapPx,
                ),
                onDismissRequest = { toolbar.hide() },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = false,
                ),
            ) {
                SelectionToolbarCard(messageText = messageText, st = st, ctx = ctx)
            }
        }
    }
}

@Composable
private fun SelectionToolbarCard(
    messageText: String,
    st: DsSelectionToolbarState,
    ctx: android.content.Context,
) {
    val ds = LocalDsColors.current
    val shape = RoundedCornerShape(CARD_RADIUS)

    // 出现动画：alpha 0→1 + scale 0.92→1（官方参数被混淆，取近似值）
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = ENTER_MS),
        label = "selToolbarAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.92f,
        animationSpec = tween(durationMillis = ENTER_MS),
        label = "selToolbarScale",
    )

    Row(
        Modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = Color.Black.copy(alpha = 0.12f),
            )
            .clip(shape)
            .background(ds.cardBg)
            .padding(start = CARD_PAD_H, top = CARD_PAD_TOP, end = CARD_PAD_H, bottom = CARD_PAD_BOTTOM)
            .padding(CARD_INNER),
        horizontalArrangement = Arrangement.spacedBy(ITEM_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 官方按钮顺序：Copy → SelectAll（全选后 SelectionManager 会传 null 自动隐藏）
        st.copy?.let { doCopy ->
            ToolbarItem(com.yuyuyu.deepseek.R.drawable.ic_copy_outline, "复制", ds.chipText) {
                doCopy()
                st.hide()
            }
        }
        st.selectAll?.let { doSelectAll ->
            ToolbarItem(com.yuyuyu.deepseek.R.drawable.ic_select_outline, "全选", ds.chipText) {
                // 官方：全选后工具条保留，SelectionManager 会用新选区再次 showMenu
                doSelectAll()
            }
        }
        // App 追加：分享整条消息
        ToolbarItem(com.yuyuyu.deepseek.R.drawable.ic_share_outline_20, "分享", ds.chipText) {
            shareText(ctx, messageText)
            st.hide()
        }
    }
}

@Composable
private fun ToolbarItem(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .widthIn(min = ITEM_MIN_W, max = ITEM_MAX_W)
            .clip(RoundedCornerShape(ITEM_RADIUS))
            .clickable(onClick = onClick)
            .padding(horizontal = ITEM_PAD_H, vertical = ITEM_PAD_V),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(ICON_SIZE),
        )
        Spacer(Modifier.height(ICON_LABEL_GAP))
        Text(
            text = label,
            fontSize = LABEL_SIZE,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    }
}
