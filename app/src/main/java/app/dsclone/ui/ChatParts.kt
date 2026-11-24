package com.yuyuyu.deepseek.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yuyuyu.deepseek.R
import com.yuyuyu.deepseek.data.Msg
import com.yuyuyu.deepseek.ui.theme.DsLayout
import com.yuyuyu.deepseek.ui.theme.DsMetric
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

/* ------------------------------------------------- 正文列宽（按栏宽自适应） */

/**
 * 正文列左右内边距（官方真机 1600×2176 @density2 截图逐像素实测）：
 *
 *  · **单栏** 800dp 窗口（`audit-chat.png`）：AI 正文左墨迹 x=127px、右墨迹最大 1466px
 *    → 左右内边距 **62.5dp**，正文列宽 675dp 水平居中（=800−2×62.5）。
 *  · **两栏** 内容栏 pane=494dp（`audit-drawer.png`：抽屉 305dp + 1dp 分隔线，
 *    内容栏从 x=612px 起）：AI 正文左墨迹 x=689px → **38.5dp**；
 *    正文右墨迹最大 1516px（右留白 ≈39dp）→ 正文列宽 ≈417.5dp。
 *    注：任务里说的「pane 左 + 99dp」是 809px 那条 **用户气泡里的文字**左缘，
 *    不是 AI 正文左缘（AI 正文/列表符号都在 689~690px）。
 *  · 两组合成一条**连续**公式（正文列最大宽 675dp 居中、左右最少留白 38.5dp）：
 *        pad = max(38.5, (pane − 675) / 2)
 *    800dp → 62.5 ✓；494dp → 38.5 ✓。
 *    用连续公式而不是 600dp 硬断点，是为了抽屉 240ms 开合动画里 pane 连续变化时
 *    留白也跟着连续变化，不会在动画中途跳一下（那段动画本来就在 reflow）。
 */
private val BODY_COL_MAX_W = 675.dp
private val BODY_PAD_MIN = 38.5.dp

/** 正文列左右内边距；[pane] = 当前内容栏（消息列表）可用宽度。 */
private fun bodyPadX(pane: Dp): Dp = maxOf(BODY_PAD_MIN, (pane - BODY_COL_MAX_W) / 2f)

/**
 * 官方用户气泡（`audit-chat.png` / `audit-drawer.png` 实测）：
 *  · 单栏：气泡 bbox x=215…1489px = 107.5…744.5dp（正文列 62.5…737.5）→
 *    右缘 = 正文列右缘 + 7.5dp（744.5 vs 737.5）。
 *  · 两栏：气泡 bbox x=777…1539px（pane 左 612px）→ 右缘 769.5dp，正文列右缘 761dp；
 *    右缘同样 = 正文列右缘 + 8.5dp ✓ 同一套规则。
 *  → 气泡右内边距 = pad − 7.5（单栏 62.5−7.5=55，正是原来的 BUBBLE_RIGHT；两栏 31）。
 *    左内边距（60dp）是「气泡最宽能到哪」的上限，官方没量到封顶，先保持原值不动。
 */
private fun bubbleEndPad(pad: Dp): Dp = maxOf(0.dp, pad - 7.5.dp)

/* ------------------------------------------------------------------ 顶栏 */

@Composable
fun TopBar(
    title: String,
    onMenu: () -> Unit,
    onMute: () -> Unit,
    onNewChat: () -> Unit,
    autoRead: Boolean = false,
) {
    val ds = LocalDsColors.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    Row(
        Modifier
            .fillMaxWidth()
            // 官方：顶栏 = 内容行 45dp + 上留白 6dp（状态栏 24dp 以下共 51dp）—— 实测 audit-chat.png
            // 窄屏（640dp）官方 2.8.0 实测：内容行 39 + 上留白 3.5（a11-off-10-empty：chat_top_bar 22..64.5）
            .padding(start = DsMetric.topBarPadStart.dp, top = DsMetric.topBarTopPad.dp)
            .height(DsMetric.topBarH.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(DsMetric.topIconBox.dp)
                .clip(CircleShape)
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_menu_outline_24),
                contentDescription = "打开侧边栏",
                tint = ds.bodyText,
                modifier = Modifier.size(DsMetric.topIcon.dp),
            )
        }
        if (title.isNotBlank()) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    // 官方：标题 x=57dp（汉堡盒 2.5+45=47.5，间隔 9.5）—— ui-official-chatclosed.xml
                    // 窄屏：盒右缘 41 → 标题墨迹 49.5（间隔 8.5）
                    .padding(start = DsMetric.topTitlePadStart.dp, end = 6.dp),
                // 官方：16sp、节点高 20dp、Medium~SemiBold —— 实测 audit-chat.png
                fontSize = 16.sp,
                lineHeight = if (DsLayout.narrow) 21.sp else 20.sp,
                fontWeight = FontWeight.Medium,
                color = ds.bodyText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Box(
            Modifier
                .size(DsMetric.topIconBox.dp)
                .clip(CircleShape)
                .clickable(onClick = onMute),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (autoRead) R.drawable.ic_volume else R.drawable.ic_mute_audio_outline_24,
                ),
                contentDescription = if (autoRead) "关闭自动朗读" else "开启自动朗读",
                tint = if (autoRead) DsBlue else ds.bodyText,
                modifier = Modifier.size(DsMetric.topIcon.dp),
            )
        }
        // 官方：两个右按钮间距 4dp，新对话按钮右缘贴屏幕右边 —— ui-official-chatclosed.xml
        Spacer(Modifier.width(DsMetric.topIconGap.dp))
        Box(
            Modifier
                .size(DsMetric.topIconBox.dp)
                .clip(CircleShape)
                .clickable(onClick = onNewChat),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_new_chat_outline_24),
                contentDescription = "开启新对话",
                tint = ds.bodyText,
                modifier = Modifier.size(DsMetric.topIcon.dp),
            )
        }
    }
    }
}

/* ------------------------------------------------------------ 消息列表 */

/**
 * 流式输出的「实时文本」旁路。
 *
 * 为什么不再每个 token 写进 `sessions`：sessions 是 DsApp 顶层读的 SnapshotStateList，
 * 每写一次，DsApp 整棵子树（DrawerContent 侧栏、TopBar、InputBar、MessageList）都会重组
 * 并且整屏重新测量。服务端 token 频率可达上百 Hz，主线程被重组吃满 ——
 * gfxinfo 基线实测：流式时 p90 46ms / 99th 133ms、10.79% janky、平均只有 31fps。
 *
 * 现在 token 只写这个对象：
 *   · DsApp 顶层**不读**它 → 不重组；
 *   · 只有 MessageList 里「正在流式那条消息」的 item 读它 → 只重组那一行；
 *   · 流式结束由 DsApp 一次性并入 sessions（内容与旧实现逐字一致）。
 */
@androidx.compose.runtime.Stable
class StreamUi {
    /** 正在流式的那条消息的实时快照（id 与 sessions 里的 bot 消息相同）；null = 当前没有流式内容。 */
    var live by mutableStateOf<Msg?>(null)
}

@Composable
fun MessageList(
    messages: List<Msg>,
    onCopy: (String) -> Unit,
    onEdit: (Msg) -> Unit,
    onRegenerateFrom: (Msg) -> Unit,
    onSwitchVariant: (Msg, Int) -> Unit,
    onDeleteFrom: (Msg) -> Unit,
    onShowContext: () -> Unit,
    state: LazyListState = rememberLazyListState(),
    stream: StreamUi = remember { StreamUi() },
    scrollToMessageId: Long = -1L,
    onScrolledToMessage: () -> Unit = {},
) {
    val last = messages.lastOrNull()

    // 搜索点进来要定位到那条消息（官方 resolveSessionForNavigation 做的事）
    // 搜索跳转：等一帧让新会话的消息先组合出来，再滚；滚完通知父级把 pending 清掉
    LaunchedEffect(scrollToMessageId) {
        if (scrollToMessageId <= 0L) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        val idx = messages.indexOfFirst { it.id == scrollToMessageId }
        if (idx >= 0) runCatching { state.scrollToItem(idx) }
        onScrolledToMessage()
    }

    // 自动滚到底部：只在「没有待跳转」时生效；用 rememberUpdatedState 读，
    // 这样父级把 pending 清成 -1 不会重新触发滚底（之前就是这么把跳转覆盖掉的）
    val pendingJump = androidx.compose.runtime.rememberUpdatedState(scrollToMessageId)

    // 注意：搜索跳转进来时不能让「滚到底部」这两个效果把位置覆盖掉
    LaunchedEffect(messages.size) {
        if (pendingJump.value > 0 || !messages.isNotEmpty()) return@LaunchedEffect
        state.scrollToItem(messages.lastIndex)
    }
    LaunchedEffect(last?.text?.length, last?.reasoning?.length) {
        if (pendingJump.value > 0) return@LaunchedEffect
        if (messages.isNotEmpty()) {
            val visibleLast = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (visibleLast >= messages.size - 2) state.scrollToItem(messages.lastIndex)
        }
    }

    // 流式跟随：流式文本（StreamUi）不写 sessions，所以上面的 effect 不会触发。
    // 这里用 snapshotFlow 观察「正在流式的文本长度」，只在快贴底时滚到底。
    // 注意：snapshotFlow 的读取发生在协程里，不是组合期，不会让 MessageList 重组。
    LaunchedEffect(stream, messages) {
        snapshotFlow {
            val live = stream.live
            if (live == null) null else live.id to (live.text.length + live.reasoning.length)
        }.collect { v ->
            if (v == null) return@collect
            if (pendingJump.value > 0) return@collect
            if (messages.isNotEmpty()) {
                val visibleLast = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (visibleLast >= messages.size - 2) state.scrollToItem(messages.lastIndex)
            }
        }
    }

    // 官方是「会话级唯一选中」：同一时刻只有一条消息处于选择态
    var selectingId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        // 窄屏：官方第一条消息气泡顶 = 82.5（顶栏底 64.5 之后 20）
        contentPadding = PaddingValues(top = if (DsLayout.narrow) 20.dp else 12.dp, bottom = 8.dp),
    ) {
        items(messages, key = { it.id }) { m ->
            // 正在流式的那一条：把 StreamUi 里的实时文本叠加进来。
            // 这一步只在「本 item 的组合作用域」里读 stream.live —— 每 33ms 一次，
            // 只会重组这一行，侧栏/顶栏/输入卡/其它消息都不动。
            val live = stream.live
            val shown = if (live != null && live.id == m.id) live else m
            MessageRow(
                m = shown,
                isLast = m.id == last?.id,
                onCopy = onCopy,
                onEdit = onEdit,
                onRegenerateFrom = onRegenerateFrom,
                onSwitchVariant = onSwitchVariant,
                onDeleteFrom = onDeleteFrom,
                onShowContext = onShowContext,
                selecting = selectingId == m.id,
                anySelecting = selectingId != null,
                onStartSelect = { selectingId = m.id },
                onStopSelect = { selectingId = null },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    m: Msg,
    isLast: Boolean,
    onCopy: (String) -> Unit,
    onEdit: (Msg) -> Unit,
    onRegenerateFrom: (Msg) -> Unit,
    onSwitchVariant: (Msg, Int) -> Unit,
    onDeleteFrom: (Msg) -> Unit,
    onShowContext: () -> Unit,
    selecting: Boolean = false,
    anySelecting: Boolean = false,
    onStartSelect: () -> Unit = {},
    onStopSelect: () -> Unit = {},
) {
    val ds = LocalDsColors.current
    var menu by remember { mutableStateOf(false) }
    // 「N 个网页」药丸点开 → 出处列表（官方：reference_list 内嵌页；我们用 sheet + 系统浏览器）
    var showCitations by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // 手势给的 off 是相对本条消息的坐标，必须加上消息块自身在窗口里的位置，
    // 否则菜单只会跑到消息左上角（就是之前的「乱飞」）
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val density = LocalDensity.current
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // 掉帧点（2026-11-23 修）：旧实现 `.onGloballyPositioned { origin = it.positionInWindow() }`
    // 在**每次布局**都往 mutableState 写值，而 origin 又在组合期被读（selectAtY）——
    // 滚动/抽屉开合/流式长高时每一帧每一条可见消息都会「写 state → 重组 → 再布局」，
    // 白白烧主线程（实测抽屉宽度动画每帧 85ms 就来自这类重复重组）。
    // 现在坐标只存进一个普通数组（不是 State，不触发重组），只在真正长按时取一次。
    val coordsRef = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }

    // 正文列宽按当前内容栏宽度自适应（官方两栏 pane=494dp 时正文列 417.5dp，
    // 比原来的 494−2×62.5=369.5dp 宽 48dp —— 也就是用户嫌窄的那段）。见 bodyPadX()。
    // 手机（窗口 < 768dp，单栏、抽屉盖在内容上）保持原来的 62.5dp，不动手机观感。
    val wideWindow = LocalConfiguration.current.screenWidthDp >= 768
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { c -> coordsRef[0] = c }
            // 进入「选择文本」后必须让出长按，否则外层手势把长按吃掉，
            // SelectionContainer 永远收不到事件，就又变成选不了
            .then(
                if (selecting) Modifier else Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        // 官方：选中状态下点空白（也就是别的消息）直接消失
                        onTap = { if (anySelecting && !selecting) onStopSelect() },
                        onLongPress = { off ->
                            // MsgMenu 的 Popup 是 message-relative（没有 alignment），
                            // 所以这里只能给手势的相对坐标；origin 是本条消息在窗口里的位置
                            // （长按这一刻取一次，菜单/选字定位只用这一次的值）
                            origin = coordsRef[0]?.positionInWindow()
                                ?: androidx.compose.ui.geometry.Offset.Zero
                            anchor = off
                            menu = true
                        },
                    )
                }
            )
    ) {
        val paneW = maxWidth
        // 窄屏（640dp 单栏）官方 2.8.0 实测：正文列左右内边距 32.5dp（正文墨迹 33、右缘 607.5），
        // 用户气泡右内边距 26.5dp（气泡右缘 613.5）—— 见 DsMetric.msgPadX / bubbleEndPad。
        // 宽屏仍走 bodyPadX()（800dp→62.5、双栏 pane 494→38.5）。
        val bodyPad = if (DsLayout.narrow) DsMetric.msgPadX.dp
        else if (wideWindow) bodyPadX(paneW) else DsMetric.MSG_PAD_X.dp
        // 气泡右内边距跟着正文列右缘走：单栏仍是 55dp（= 原来的 BUBBLE_RIGHT），两栏 31dp。
        val bubbleEnd = if (DsLayout.narrow) DsMetric.bubbleEndPad.dp
        else if (wideWindow) bubbleEndPad(bodyPad) else DsMetric.BUBBLE_RIGHT.dp
        DsSelectionToolbarHost(messageText = m.text) {
        if (m.role == "user") {
            Row(
                Modifier
                    .fillMaxWidth()
                    // 官方：单栏气泡右缘 x=745（距屏幕右 55dp）、与下一条间距 13dp —— 实测 audit-chat.png；
                    // 两栏气泡右缘 769.5dp（距 pane 右 30.5dp）。右内边距由 bubbleEndPad 推出来。
                    .padding(start = 60.dp, end = bubbleEnd, bottom = DsMetric.msgGapY.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (m.files.isNotEmpty()) {
                        MessageFiles(m.files)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (m.images.isNotEmpty()) {
                        MessageImages(m.images)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (m.text.isNotBlank()) {
                        Surface(
                            color = ds.userBubble,
                            // 官方：用户气泡圆角 ≈20.5dp（拟合 20.46，取 20）—— 实测 audit-chat.png
                            shape = RoundedCornerShape(
                                topStart = DsMetric.bubbleRadius.dp,
                                topEnd = DsMetric.bubbleRadius.dp,
                                bottomEnd = DsMetric.bubbleRadius.dp,
                                bottomStart = DsMetric.bubbleRadius.dp,
                            ),
                        ) {
                            MaybeSelect(selecting) {
                                Text(
                                    text = m.text,
                                    // 官方：内边距 水平 15 / 垂直 9.5 —— ui (245,215)-(1460,607)
                                    modifier = Modifier.padding(
                                        horizontal = DsMetric.bubblePadH.dp,
                                        vertical = DsMetric.bubblePadV.dp,
                                    ),
                                    // 官方：17sp / 26sp、字重 Normal —— kt:UserChatMessageBubble.kt:214
                                    fontSize = DsMetric.BODY_SIZE.sp,
                                    lineHeight = DsMetric.LABEL_LINE.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = ds.bubbleText,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    // 官方：单栏 AI 正文 62.5…737.5dp（正文列 675dp 居中）；两栏 pane=494dp 时
                    // 38.5…761dp（正文列 417.5dp）—— 见文件顶部 bodyPadX 的实测说明。
                    .padding(
                        start = bodyPad,
                        end = bodyPad,
                        top = DsMetric.msgGapY.dp,
                    )
            ) {
                if (m.reasoning.isNotBlank() || m.steps.isNotEmpty() || (m.streaming && m.text.isBlank())) {
                    ThinkingRow(
                        reasoning = m.reasoning,
                        thinking = m.streaming && m.text.isBlank(),
                        thinkSeconds = m.thinkSeconds,
                        hasImages = m.hasImages,
                        steps = m.steps,
                    )
                }
                if (m.text.isNotBlank()) {
                    MarkdownView(m.text, selectionMode = selecting, selectAtY = origin.y + anchor.y)
                    Spacer(Modifier.height(2.dp))
                }
                    if (m.error) {
                    Text(
                        text = "请求失败，请检查 API Key / 网络 / 模型名",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                if (m.citations.isNotEmpty()) {
                    // 官方：回答下方一个药丸，左边一排网站小圆标，右边「N 个网页」
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .clickable { showCitations = true }
                            .clip(RoundedCornerShape(13.33.dp))
                            .background(ds.tileBg)
                            .padding(start = 8.00.dp, end = 10.16.dp, top = 4.57.dp, bottom = 4.57.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SourceAvatars(m.citations, 14.00.dp)
                        Spacer(Modifier.width(6.10.dp))
                        Text(
                            text = "${m.citations.size} 个网页",
                            fontSize = 13.33.sp,
                            color = ds.thoughtText,
                        )
                    }
                    if (showCitations) {
                        SourceListSheet(citationsOf(m)) { showCitations = false }
                    }
                }
                if (!m.streaming && (m.text.isNotBlank() || m.variants.size > 1)) {
                    // 官方：变体切换和操作图标在**同一行**（「上一条 / n/N / 下一条」在行最左），
                    // 不再单独占一行掉到正文下面 —— 实测 audit-chat.png 墨迹 63.5…110dp
                    val vi = if (m.variants.isEmpty()) 0 else m.variantIndex.coerceIn(0, m.variants.lastIndex)
                    ActionRow(
                        msgId = m.id,
                        variantIndex = vi,
                        variantCount = m.variants.size,
                        onPrevVariant = { onSwitchVariant(m, vi - 1) },
                        onNextVariant = { onSwitchVariant(m, vi + 1) },
                        onCopy = { onCopy(m.text) },
                        onRead = { DsTts.onRead(m.id, m.text) },
                        onShare = { shareGeneratedImage(ctx, "", m.text) },
                        onRegenerate = { onRegenerateFrom(m) },
                    )
                }
            }
        }

        if (menu) {
            MsgMenu(
                role = m.role,
                onDismiss = { menu = false },
                anchor = anchor,
                onCopy = { onCopy(m.text); menu = false },
                onEdit = { onEdit(m); menu = false },
                onRegenerate = { onRegenerateFrom(m); menu = false },
                onDelete = { onDeleteFrom(m); menu = false },
                onContext = { onShowContext(); menu = false },
                onShare = { shareGeneratedImage(ctx, "", m.text); menu = false },
                onSelectText = { onStartSelect(); menu = false },
            )
        }
        }
    }
}

/* --------------------------------------------------------------- 思考行 */

/**
 * 官方思考链 —— 逐函数对照反编译（DeepSeek 2.9.0，包 com.deepseek.chat）：
 *
 *  - `AssistantThinkingItem.kt:29`      思考行头部（「已思考（用时 N 秒）」+ chevron），行高 45dp
 *  - `AssistantThinkingContent.kt:127`  `ThinkingContentColumn`：一整根连续竖线
 *  - `AssistantThinkingContent.kt:157`  `drawThinkingDot`：段落圆点（圆点 + 页面底色遮罩）
 *  - `AssistantThinkingContent.kt:190`  `drawSingleLineLeadingIconBackground`：工具步骤图标背景带
 *  - `AssistantThinkingContent.kt:210`  `recordThinkingDecoration`：记录每行 leading 装饰的位置
 *  - `AssistantThinkingContent.kt:52`   `AssistantThinkingMarkdown`：正文样式
 *  - `StreamingTextLoadingIndicator.kt:42/114`：思考开始前的三点（PulsatingDot）
 *
 * 线与点的几何关系（smali 常量 + official-chain.png 1600×2176@2x 逐像素实测，px÷2=dp）：
 *  · 竖线    x=8dp（圆心/线中心同一根轴）、宽 1dp、StrokeCap.Round、上下各内缩 3dp；
 *            浅色 #E5E5E5（xp3.b.b.f = pz7.e），深色 #474849（Theme.kt 深色方案 xp3.b.b.f）
 *            实测：线占 px 139…140（=69.5…70.5dp），正文列左 62.5dp → 62.5+8=70.5 吻合
 *  · 圆点    直径 6dp（r=3dp），圆心 (8dp, 12dp)：12dp = 首行行高 24dp 的一半，即与首行垂直居中；
 *            浅色 #A2A4A6（xp3.a.b = pz7.k），深色 #8E9094；实测圆心 px(139.5,345) 直径 12px=6dp
 *  · 圆点遮罩 16dp×24dp 页面底色矩形，从行首 y=0 起 —— 先把竖线盖掉 24dp 再点圆点，
 *            所以视觉上竖线在每个段落圆点前后各断约 12dp/9dp（实测断开 45px≈22.5dp）
 *  · 步骤图标 13dp 居中在 x=8dp 轴上；背景带 16dp 宽 × (行高+6dp) 页面底色，上下各外扩 3dp，
 *            把竖线在图标处断开（实测图标行断开 54px=27dp，图标墨迹 13dp）
 *  · 正文    x=24dp（圆点右缘 11dp → 文字留 13dp），15sp/24sp，浅色 #7D7F85 / 深色 #B9BABB
 *  · 块间距  段落块/步骤块之间 10dp（实测块间隙 17~20px ≈ 8.5~10dp，取 10dp）
 */
private const val THINK_LINE_X = 8f        // 竖线 / 圆点 / 图标 共用的中心 x
private const val THINK_MARK_W = 16f       // 遮罩、图标背景带的宽度
private const val THINK_TEXT_X = 24f       // 正文左缩进
private const val THINK_DOT_R = 3f         // 圆点半径 → 直径 6dp
private const val THINK_DOT_CY = 12f       // 圆点圆心 y = 首行行高 24dp / 2
private const val THINK_HALO_H = 24f       // 圆点遮罩高 = 一行行高
private const val THINK_ICON = 13f         // 工具步骤图标边长
private const val THINK_ICON_BAND = 3f     // 图标背景带上下外扩
private const val THINK_BLOCK_GAP = 10f    // 块间距
private const val THINK_BODY_SIZE = 15f    // 思考正文 15sp
private const val THINK_BODY_LINE = 24f    // 思考正文 24sp 行高
private const val THINK_DOT_COUNT = 3      // 三点的点数（StreamingTextLoadingIndicator）
private const val THINK_DOT_SIZE = 6f      // 三点直径 6dp
private const val THINK_DOT_SPACING = 4.5f // 三点间距 4.5dp（Arrangement.spacedBy(4.5.dp)）
private const val THINK_DOT_CYCLE = 1100f  // 三点周期 1100ms（keyframes durationMillis=0x44c）
private const val THINK_DOT_STAGGER = 100f // 每点错峰 100ms（startOffset -100 / -200）

private data class ThinkPalette(
    val line: Color,
    val dot: Color,
    val halo: Color,
    val dots: Color,
)

@Composable
private fun thinkPalette(): ThinkPalette {
    val ds = LocalDsColors.current
    // ChatParts 只能改自己：用卡片底色亮度判断深浅色（浅色 #FFFFFF / 深色 #242424）
    val dark = ds.cardBg.luminance() < 0.5f
    return if (dark) {
        ThinkPalette(
            line = Color(0xFF474849),   // xp3.b.b.f 深色（浅色是 #E5E5E5）
            dot = Color(0xFF8E9094),    // xp3.a.b 深色（浅色是 #A2A4A6）
            halo = Color(0xFF0F0F0F),   // up3.c = 聊天页底色深色
            dots = Color(0xFF5E6063),   // xp3.a.g 深色（浅色是 #D4D4D4）
        )
    } else {
        ThinkPalette(
            line = Color(0xFFE5E5E5),
            dot = Color(0xFFA2A4A6),
            halo = Color(0xFFFFFFFF),
            dots = Color(0xFFD4D4D4),
        )
    }
}

/**
 * 官方思考行三态（AssistantThinkingItem.kt + AssistantThinkingContent.kt）：
 *  ① 刚发出、还没有任何 reasoning → 三点（StreamingTextLoadingIndicator）
 *  ② 流式中（有 reasoning）        → 「正在思考」+ 展开图标，正文一直显示
 *  ③ 结束后                        → 「已思考（用时 N 秒）」+ chevron，展开才显示正文
 * ②③ 的正文是同一根竖线 + 圆点/图标，见 ThinkingBody。
 */
@Composable
private fun ThinkingRow(
    reasoning: String,
    thinking: Boolean,
    thinkSeconds: Int,
    hasImages: Boolean = false,
    steps: List<com.yuyuyu.deepseek.data.Step> = emptyList(),
) {
    val thinkingNow = thinking || com.yuyuyu.deepseek.DsDebug.forceThinking
    var expanded by remember { mutableStateOf(false) }
    // 官方 AssistantChatMessageContent.kt:165：还没有首 token（reasoning/正文都空）时显示三点
    val waiting = thinkingNow && reasoning.isBlank() && steps.isEmpty()
    val hasBody = reasoning.isNotBlank() || steps.isNotEmpty()
    val showBody = hasBody && (thinkingNow || expanded)
    val label = when {
        thinkingNow && hasImages && reasoning.isBlank() -> "正在分析图片"
        thinkingNow -> "正在思考"
        thinkSeconds > 0 -> "已思考（用时 $thinkSeconds 秒）"
        else -> "已思考"
    }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        // 三点 → 「正在思考」：交叉淡入淡出（高度都是 45dp 行，不跳）
        AnimatedVisibility(
            visible = waiting,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) { TypingDotsRow() }
        AnimatedVisibility(
            visible = !waiting,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                ThinkingHeaderRow(
                    label = label,
                    thinkingNow = thinkingNow,
                    expandable = hasBody,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                )
                // 正文：淡入 + 高度展开（官方展开/收起也是高度动画 + chevron 旋转）
                AnimatedVisibility(
                    visible = showBody,
                    enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
                ) { ThinkingBody(reasoning = reasoning, steps = steps) }
            }
        }
    }
}

/** 头部行（AssistantThinkingItem.kt:29）：行高 45dp，文案 17sp、chevron 19.5dp、间距 4dp。 */
@Composable
private fun ThinkingHeaderRow(
    label: String,
    thinkingNow: Boolean,
    expandable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val ds = LocalDsColors.current
    // chevron 旋转 0°→90°（右箭头 → 下箭头），跟官方一样是连续动画而不是换图
    val chevronRotate by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200),
        label = "thinkChevron",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(DsMetric.THINK_ROW_H.dp)
            .clickable(enabled = !thinkingNow && expandable, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thinkingNow) {
            // 官方：思考中「正在思考」有 1.5s 扫光，字号 16.75
            val shimmer = androidx.compose.animation.core.rememberInfiniteTransition(label = "thinkTitle")
            val sx by shimmer.animateFloat(
                initialValue = -0.6f,
                targetValue = 1.6f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        1500,
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                ),
                label = "shimmerX",
            )
            Text(
                text = label,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 16.75.sp,
                    lineHeight = DsMetric.LABEL_LINE.sp,
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            ds.subText,
                            Color(0xFFD9DADC),
                            ds.subText,
                        ),
                        start = androidx.compose.ui.geometry.Offset(sx * 260f, 0f),
                        end = androidx.compose.ui.geometry.Offset(sx * 260f + 120f, 0f),
                    ),
                ),
            )
            // 流式中：官方用上下展开图标（不是 chevron）
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_expand_vertical_outline_20),
                contentDescription = "思考中",
                tint = ds.subText,
                modifier = Modifier.size(DsMetric.THINK_CHEVRON.dp),
            )
        } else {
            // 官方：17sp（墨迹高 17.5dp）、#7D7F85 / 深色 #B9BABB = ds.subText —— 实测 official-chain.png
            Text(
                text = label,
                color = ds.subText,
                fontSize = DsMetric.BODY_SIZE.sp,
                lineHeight = DsMetric.LABEL_LINE.sp,
            )
            // 官方 chevron 盒 19.5dp、距文字 4dp —— ui (414,678)-(453,715)
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_outline_20),
                contentDescription = if (expanded) "折叠" else "展开",
                tint = ds.subText,
                modifier = Modifier
                    .size(DsMetric.THINK_CHEVRON.dp)
                    .rotate(chevronRotate),
            )
        }
    }
}

/**
 * 思考正文 = 一整根连续竖线 + 每块的 leading 装饰（ThinkingContentColumn）：
 * 竖线由父级 drawBehind 画（x=8dp），每个块再用「页面底色」把该断的地方盖掉，
 * 所以线看起来是分段连续的 —— 官方就是这么做的，不是每段各画一截。
 */
@Composable
private fun ThinkingBody(reasoning: String, steps: List<com.yuyuyu.deepseek.data.Step>) {
    val pal = thinkPalette()
    Column(
        Modifier
            .fillMaxWidth()
            // 官方 ThinkingContentColumn（AssistantThinkingContent.kt:127）：
            // 一条连续竖线，y 从 3dp 到 内容高-3dp，1dp 粗、圆头
            .drawBehind {
                val x = THINK_LINE_X.dp.toPx()
                drawLine(
                    color = pal.line,
                    start = Offset(x, 3.dp.toPx()),
                    end = Offset(x, size.height - 3.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(THINK_BLOCK_GAP.dp),
    ) {
        fun paragraphs(text: String) =
            text.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }

        // 官方是「逐步交错」：每步自己的思考 → 该步的搜索/浏览行 → … → 最后一步的正文
        // （message.reasoning_content 只有最后一步的思考，前面的在各 step 里）
        for (st in steps) {
            for (t in paragraphs(st.reasoning)) ThinkParagraph(t, pal)
            if (st.count > 0) ThinkStepRow(st, pal)
        }
        for (t in paragraphs(reasoning)) ThinkParagraph(t, pal)
    }
}

/**
 * 段落块：圆点（drawThinkingDot，AssistantThinkingContent.kt:157）
 *  · 16dp×24dp 页面底色遮罩把竖线盖掉（圆点前后各留白），再画 r=3dp 圆点于 (8dp,12dp)
 *  · 正文左缩进 24dp，15sp/24sp
 */
@Composable
private fun ThinkParagraph(text: String, pal: ThinkPalette) {
    val ds = LocalDsColors.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(THINK_TEXT_X.dp).fillMaxHeight()) {
            Canvas(Modifier.matchParentSize()) {
                // 遮罩：从行首 0 起 16dp×24dp（官方 rect 尺寸写死 16×24，不是整行高）
                drawRect(
                    color = pal.halo,
                    topLeft = Offset(0f, 0f),
                    size = Size(THINK_MARK_W.dp.toPx(), THINK_HALO_H.dp.toPx()),
                )
                // 圆点：圆心压在竖线上
                drawCircle(
                    color = pal.dot,
                    radius = THINK_DOT_R.dp.toPx(),
                    center = Offset(THINK_LINE_X.dp.toPx(), THINK_DOT_CY.dp.toPx()),
                )
            }
        }
        Box(Modifier.weight(1f)) {
            MarkdownView(text, textColor = ds.thoughtText, compact = true)
        }
    }
}

/**
 * 工具步骤块（搜索/浏览）：图标居中在竖线轴上，背景带把竖线在该行断开
 * （drawSingleLineLeadingIconBackground，AssistantThinkingContent.kt:190）。
 */
@Composable
private fun ThinkStepRow(st: com.yuyuyu.deepseek.data.Step, pal: ThinkPalette) {
    val ds = LocalDsColors.current
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(THINK_TEXT_X.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                // 背景带：16dp 宽 × (行高 + 6dp)，上下各外扩 3dp
                drawRect(
                    color = pal.halo,
                    topLeft = Offset(0f, -THINK_ICON_BAND.dp.toPx()),
                    size = Size(
                        THINK_MARK_W.dp.toPx(),
                        size.height + 2 * THINK_ICON_BAND.dp.toPx(),
                    ),
                )
            }
            Icon(
                painter = painterResource(
                    if (st.action == "search") R.drawable.ic_search_outline_16
                    else R.drawable.ic_browse_outline_16
                ),
                contentDescription = null,
                tint = ds.thoughtText,
                // 24dp 槽的几何中心在 12dp，图标要落在 8dp 轴上 → 左移 4dp
                modifier = Modifier
                    .size(THINK_ICON.dp)
                    .offset(x = (THINK_LINE_X - THINK_TEXT_X / 2).dp),
            )
        }
        Text(
            text = when {
                st.action == "search" -> "搜索到 ${st.count} 个网页"
                st.action == "find_in_page" -> "在页面内查找"
                else -> "已浏览 ${st.count} 个页面"
            },
            fontSize = THINK_BODY_SIZE.sp,
            lineHeight = THINK_BODY_LINE.sp,
            color = ds.thoughtText,
        )
        if (st.sources.isNotEmpty()) {
            Spacer(Modifier.width(6.10.dp))
            SourceAvatars(st.sources, 13.33.dp)
        }
    }
}

/**
 * 思考开始前的三点（StreamingTextLoadingIndicator.kt:42 + PulsatingDot.kt:114）：
 *  · 3 个直径 6dp 的圆点，间距 4.5dp，颜色浅色 #D4D4D4 / 深色 #5E6063（xp3.a.g）
 *  · 透明度关键帧（durationMillis 1100）：0ms→1.0、300ms→0.3、800ms→0.3、1100ms→1.0，循环 Restart
 *  · 每点错峰 100ms（Dot1/Dot2/Dot3 的 initialStartOffset 0 / -100 / -200）
 *  · 行高与思考行一致 45dp，切换时不跳高度
 */
@Composable
private fun TypingDotsRow() {
    val pal = thinkPalette()
    val transition = rememberInfiniteTransition(label = "PulsatingDotsInfiniteTransition")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = THINK_DOT_CYCLE.toInt(),
                easing = androidx.compose.animation.core.LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "DotPhase",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(DsMetric.THINK_ROW_H.dp),
        verticalAlignment = Alignment.CenterVertically,
        // 官方 Arrangement.spacedBy(4.5.dp)
        horizontalArrangement = Arrangement.spacedBy(THINK_DOT_SPACING.dp),
    ) {
        for (i in 0 until THINK_DOT_COUNT) {
            // 错峰：第 i 个点相位超前 i×100ms
            val p = (phase + i * THINK_DOT_STAGGER / THINK_DOT_CYCLE) % 1f
            Canvas(Modifier.size(THINK_DOT_SIZE.dp)) {
                drawCircle(
                    color = pal.dots,
                    radius = size.minDimension / 2f,
                    alpha = thinkDotAlpha(p),
                )
            }
        }
    }
}

/** 三点的透明度关键帧（对应官方 keyframes：1.0 → 0.3 → 0.3 → 1.0）。 */
private fun thinkDotAlpha(phase: Float): Float {
    val ms = (phase % 1f) * THINK_DOT_CYCLE
    return when {
        ms < 300f -> 1f - 0.7f * (ms / 300f)
        ms < 800f -> 0.3f
        else -> 0.3f + 0.7f * ((ms - 800f) / 300f)
    }
}

/* --------------------------------------------------------------- 操作行 */

/**
 * 操作行（官方 ui-official-chatclosed.xml + audit-chat.png 实测）：
 *  · 行盒 x=**49.5…750.5**（宽 701dp）、高 45dp —— 比正文列（62.5…737.5，675dp）左右各外扩 **13dp**；
 *    所以这里用 [expandHorizontal] 让行比父列宽 26dp 并左移 13dp（负外边距效果）
 *  · 单元格 **38×45dp**、图标 **19dp**；实测图标墨迹起点 146.5 / 184.5 / 222.5 / 260.5 / 298.5（间距 38dp）
 *  · 「重新生成」贴右缘：单元格 45dp = x 705.5…750.5
 *  · 「上一条 / n/N / 下一条」在**同一行最左**（实测墨迹：箭头 63.5…68.5、页码 82…110），
 *    不再单独占一行（原来掉到下面去了）
 */
@Composable
private fun ActionRow(
    msgId: Long,
    variantIndex: Int,
    variantCount: Int,
    onPrevVariant: () -> Unit,
    onNextVariant: () -> Unit,
    onCopy: () -> Unit,
    onRead: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        Modifier
            // 官方：操作行左右各外扩 13dp —— 49.5 / 750.5 vs 正文 62.5 / 737.5
            // 窄屏（640dp）官方 2.8.0 实测：外扩 11.25、再右缩 5.25（首图标墨迹 35 ≈ 正文墨迹 33；行盒 21.25..618.75）
            .expandHorizontal(if (DsLayout.narrow) DsMetric.actionPadStart.dp else DsMetric.ACTION_PAD_START.dp)
            .fillMaxWidth()
            // 官方：AI 声明与操作行间距 4.5dp —— ui 739 → 743.5
            .padding(start = DsMetric.actionLeadIn.dp)
            .padding(top = DsMetric.ACTION_TOP.dp)
            .height(DsMetric.actionH.dp)
            .offset(x = DsMetric.actionShift.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (variantCount > 1) {
            VariantControls(
                index = variantIndex,
                count = variantCount,
                onPrev = onPrevVariant,
                onNext = onNextVariant,
            )
            // 官方「上一条/n/N/下一条」组到第一个操作图标的间隔（实测约 4.5dp）
            Spacer(Modifier.width(4.5.dp))
        }
        ActionCell(R.drawable.ic_copy_outline, I18n.t("复制", "Copy"), onClick = onCopy)
        TtsActionCell(msgId, onRead)
        ActionCell(R.drawable.ic_share_outline_20, I18n.t("分享", "Share"), onClick = onShare)
        Spacer(Modifier.weight(1f))
        // 官方「重新生成」单元格 45dp、贴右缘（x=705.5…750.5）
        ActionCell(R.drawable.ic_refresh_outline_20, I18n.t("重新生成", "Regenerate"), w = DsMetric.actionRegenCell.dp, onClick = onRegenerate)
    }
}

/**
 * 让本节点的测量宽度比父约束多 2×[by]，并整体向左偏移 [by]（Compose 没有负 padding，
 * 用一次性 layout 实现「负外边距」）。官方操作行就是比正文列左右各宽 13dp。
 */
private fun Modifier.expandHorizontal(by: Dp): Modifier = this.layout { measurable, constraints ->
    val extra = by.roundToPx()
    val maxW = constraints.maxWidth
    // 父约束无界时不做外扩，避免 Infinity 约束
    if (maxW == Constraints.Infinity) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val targetW = maxW + extra * 2
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = targetW))
    layout(placeable.width, placeable.height) { placeable.place(-extra, 0) }
}

@Composable
private fun ActionCell(icon: Int, label: String, w: Dp = DsMetric.actionCell.dp, onClick: () -> Unit) {
    val ds = LocalDsColors.current
    Box(
        Modifier
            // 官方：操作行单元格 38×45dp、图标 19dp —— ui-official-chatclosed.xml
            .size(w, DsMetric.actionH.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = ds.iconGray,
            modifier = Modifier.size(DsMetric.actionIcon.dp),
        )
    }
}

/**
 * 多候选回答的「上一条 / n/N / 下一条」控件（官方直接放在操作行最左侧同一行）：
 *  · 两个箭头各占 34dp 宽（实测第一根箭头墨迹 x=63.5…68.5，操作行左缘 49.5 → 中心 66 ≈ 49.5+17）
 *  · 页码 13sp、`n/N`，色 #7D7F85；到头的箭头 #C6C8CC 且不可点
 *  · 总宽 34 + 页码 + 34 ≈ 102dp，随后才是操作图标
 */
@Composable
private fun VariantControls(
    index: Int,
    count: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val gray = Color(0xFF7D7F85)
    val grayDisabled = Color(0xFFC6C8CC)
    val canPrev = index > 0
    val canNext = index < count - 1
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp, DsMetric.actionH.dp)
                .clip(CircleShape)
                .clickable(enabled = canPrev, onClick = onPrev),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_outline_20),
                contentDescription = "上一版回答",
                tint = if (canPrev) gray else grayDisabled,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(180f),
            )
        }
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "${index + 1}/$count",
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = gray,
            )
        }
        Box(
            Modifier
                .size(34.dp, DsMetric.actionH.dp)
                .clip(CircleShape)
                .clickable(enabled = canNext, onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_outline_20),
                contentDescription = "下一版回答",
                tint = if (canNext) gray else grayDisabled,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/* --------------------------------------------------------------- 长按菜单 */

@Composable
private fun MsgMenu(
    role: String,
    anchor: androidx.compose.ui.geometry.Offset,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onContext: () -> Unit,
    onShare: () -> Unit,
    onSelectText: () -> Unit,
) {
    val ds = LocalDsColors.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // 官方消息长按菜单面板宽 195dp（x=202.5…397.5）—— 实测 audit-menu.png / ui
    val w = 195.dp
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val xDp = (anchor.x / density.density).coerceIn(8f, (screenW - 203f).coerceAtLeast(8f))
    val yDp = (anchor.y / density.density).coerceIn(60f, (screenH - 320f).coerceAtLeast(60f))
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    shown.targetState = true

    androidx.compose.ui.window.Popup(
        offset = androidx.compose.ui.unit.IntOffset(
            (xDp * density.density).toInt(),
            (yDp * density.density).toInt(),
        ),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visibleState = shown,
            enter = androidx.compose.animation.fadeIn(
                androidx.compose.animation.core.tween(140)
            ) + androidx.compose.animation.scaleIn(
                initialScale = 0.88f,
                animationSpec = androidx.compose.animation.core.tween(160),
            ),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)),
        ) {
            androidx.compose.material3.Surface(
                // 官方深色消息长按菜单面板实测 #3C3C3D（浅色纯白），
                // 之前写死 Color.White：深色模式下是整块白板 + 浅色文字，几乎看不清。
                color = if (ds.pageBg.luminance() < 0.5f) Color(0xFF3C3C3D) else Color.White,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 12.dp,
                modifier = Modifier.width(w),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // 官方英文：copy / edit / share / message_select_text / message_press_regenerate / delete
                    MenuItem(R.drawable.ic_copy_outline, I18n.t("复制", "Copy"), onCopy)
                    if (role == "user") MenuItem(R.drawable.ic_edit_outline_20, I18n.t("修改", "Edit"), onEdit)
                    MenuItem(R.drawable.ic_share_outline_20, I18n.t("分享", "Share"), onShare)
                    MenuItem(R.drawable.ic_text_select, I18n.t("选择文本", "Select text"), onSelectText)
                    if (role != "user") MenuItem(R.drawable.ic_refresh_outline_20, I18n.t("重新生成", "Regenerate"), onRegenerate)
                    MenuItem(R.drawable.ic_trash_outline_20, I18n.t("删除", "Delete"), onDelete, danger = true)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: Int, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val ds = LocalDsColors.current
    // 官方：危险项 #F25A5A（深色）/ #EF4444（浅色），普通项文字/图标 #F8F8F8（深色）/#0F0F0F（浅色）
    // —— 实测官方深色长按菜单截图（面板 #3C3C3D、文字图标 #F8F8F8）
    val normal = if (ds.pageBg.luminance() < 0.5f) Color(0xFFF8F8F8) else Color(0xFF0F0F0F)
    val tint = if (danger) ds.destructive else normal
    Row(
        Modifier
            .fillMaxWidth()
            // 官方：菜单项行高 45.5dp、图标墨迹 16dp（距面板左 16.5）、文字 x=248（距面板左 45.5）
            // —— 官方：ui (405,840)-(795,931)、实测 audit-menu.png
            .height(45.5.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        // 文字 x=248dp - 面板左 202.5dp = 距面板左 45.5dp；45.5 - 16.5(pad) - 16(icon) = 13
        Spacer(Modifier.width(13.dp))
        // 官方：菜单项文字节点高 22.5dp（行高 22.5）—— ui (496,863)-(556,908)
        Text(label, fontSize = 16.sp, lineHeight = 22.5.sp, color = tint)
    }
}

/* --------------------------------------------------------------- 引用小圆标 */

/** 取一条消息的「出处」：优先 citations，老会话没有就回退到步骤里的搜索来源。 */
private fun citationsOf(m: Msg): List<com.yuyuyu.deepseek.data.Source> =
    m.citations.ifEmpty {
        m.steps.flatMap { it.sources }.distinctBy { it.url }.filter { it.url.isNotBlank() }
    }

@Composable
fun SourceAvatars(sources: List<com.yuyuyu.deepseek.data.Source>, size: androidx.compose.ui.unit.Dp = 14.00.dp) {
    val palette = listOf(
        Color(0xFF4C8DF6), Color(0xFF34A853), Color(0xFFF5A623), Color(0xFFE0507A),
        Color(0xFF7B61FF), Color(0xFF00A6A6), Color(0xFFEA6B3E), Color(0xFF5F6368),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        sources.take(5).forEachIndexed { i, src ->
            val key = src.siteName.ifBlank {
                src.url.substringAfter("://").substringBefore('/').removePrefix("www.")
            }
            val idx = (key.hashCode().let { if (it < 0) -it else it }) % palette.size
            val letter = key.firstOrNull()?.uppercaseChar()?.toString() ?: "•"
            Box(
                Modifier
                    .then(if (i == 0) Modifier else Modifier.offset(x = (-4.00).dp))
                    .size(size)
                    .clip(CircleShape)
                    .background(palette[idx])
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // 官方：真实站点 favicon（site_icon 是 cdn.deepseek.com 上的 png）
                if (src.siteIcon.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = src.siteIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = letter,
                        color = Color.White,
                        fontSize = (size.value * 0.62f).sp,
                        lineHeight = (size.value * 0.62f).sp,
                    )
                }
            }
        }
    }
}

/* --------------------------------------------------------------- 转至底部 */

@Composable
fun ScrollToBottomButton(onClick: () -> Unit) {
    val ds = LocalDsColors.current
    Box(
        Modifier
            .size(45.71.dp)
            .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(ds.cardBg)
            .border(
                width = 1.5.dp,
                brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                    listOf(
                        DsBlue.copy(alpha = 0.55f),
                        DsBlue.copy(alpha = 0.10f),
                        DsBlue.copy(alpha = 0.55f),
                    )
                ),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down_outline_20),
            contentDescription = "转至底部",
            tint = ds.bodyText,
            modifier = Modifier.size(19.05.dp),
        )
    }
}

/* --------------------------------------------------- 分享 / 选择文本 */

/**
 * 分享：官方是「分享成图片/链接」，链接对我们没意义，这里直接把正文丢进系统分享面板。
 */
fun shareText(ctx: android.content.Context, text: String) {
    if (text.isBlank()) return
    runCatching {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(android.content.Intent.createChooser(send, "分享"))
    }
}

/* ------------------------------------------------------- 原地选中 / 气泡里的图片 */

/** 官方不弹窗：长按正文直接拖选，系统工具条浮在文字上方 */
@Composable
private fun MaybeSelect(enabled: Boolean, content: @Composable () -> Unit) {
    val state = androidx.compose.foundation.text.selection.rememberSelectionState()
    if (enabled) {
        androidx.compose.foundation.text.selection.SelectionContainer(state = state) { content() }
    } else content()
}

/**
 * 用户消息里的图片（官方 UserMessageImage.kt / calculateImageMetrics）：
 *  - 单图：宽 = 240dp（屏宽 ≥ 600dp 时 320dp），比例钳在 0.25..4，宽图封宽、高图封高
 *  - 2~4 张：80dp 方块横排，间距 10dp
 *  - 圆角 16dp + 1dp 描边
 */
@Composable
private fun MessageImages(images: List<String>) {
    var previewIdx by remember { mutableStateOf(-1) }
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val maxW = if (screenW >= 600) 320f else 240f
    val shp = RoundedCornerShape(16.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        images.take(4).forEachIndexed { i, path ->
            val uri = remember(path) { android.net.Uri.parse(path) }
            val bmp = rememberAttachmentBitmap(uri)
            val ratio = if (bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height else 1f
            val single = images.size == 1
            val w: Float
            val h: Float
            if (single) {
                val r = ratio.coerceIn(0.25f, 4f)
                if (ratio > 1f) { w = maxW; h = maxW / r } else { w = maxW * r; h = maxW }
            } else {
                w = 80f; h = 80f
            }
            Box(
                Modifier
                    .size(w.dp, h.dp)
                    .clip(shp)
                    .background(Color(0xFFF1F3F5))
                    .border(1.dp, Color(0x14000000), shp)
                    .clickable { previewIdx = i },
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
    if (previewIdx in images.indices) {
        FullScreenImageViewer(images, previewIdx) { previewIdx = -1 }
    }
}

/** 用户消息里的文件附件卡片（官方 UserMessageFileItems）：圆角卡片 + 文件图标 + 文件名 + 大小 */
@Composable
private fun MessageFiles(files: List<com.yuyuyu.deepseek.data.MsgFile>) {
    val ds = LocalDsColors.current
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.take(4).forEach { f ->
            Row(
                Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ds.cardBg)
                    .border(1.dp, ds.chipBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEAF1FE)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = f.name.substringAfterLast('.', "").take(4).uppercase().ifBlank { "FILE" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DsBlue,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = f.name,
                        fontSize = 14.sp,
                        color = ds.bodyText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (f.size > 0) {
                        Text(
                            text = formatFileSize(f.size),
                            fontSize = 12.sp,
                            color = Color(0xFF8A8F99),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 官方朗读按钮三态（smali: AssistantTtsButton / AssistantTtsLoadingIndicator /
 * AssistantTtsPlayingIndicator）：
 *  Idle    → 喇叭图标
 *  Loading → 圆形扫光（2s 循环，官方是水平 sweep）
 *  Playing → 几根小竖条随播放时间起伏
 * 只高亮「正在读的那一条」消息的按钮。
 */
@Composable
private fun TtsActionCell(msgId: Long, onClick: () -> Unit) {
    val ds = LocalDsColors.current
    val active = DsTts.activeMessageId == msgId
    val phase = if (active) DsTts.phase else DsTts.Phase.Idle
    Box(
        Modifier
            // 官方：朗读单元格与其它操作行一致 38×45dp、图标 19dp —— ui-official-chatclosed.xml
            .size(DsMetric.actionCell.dp, DsMetric.actionH.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (phase) {
            DsTts.Phase.Loading -> {
                val tr = rememberInfiniteTransition(label = "ttsLoading")
                val sweep by tr.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800, easing = androidx.compose.animation.core.LinearEasing)),
                    label = "sweep",
                )
                Canvas(Modifier.size(DsMetric.actionIcon.dp)) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val r = w / 2f
                    // 先画一个浅灰圆环底
                    drawCircle(
                        color = androidx.compose.ui.graphics.Color(0xFFE3E5E9),
                        radius = r,
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.16f),
                    )
                    // 扫光弧段
                    drawArc(
                        color = DsBlue,
                        startAngle = -90f + sweep * 360f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.08f),
                        size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.84f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = w * 0.16f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                    )
                }
            }
            DsTts.Phase.Playing -> {
                val tr = rememberInfiniteTransition(label = "ttsWave")
                val t by tr.animateFloat(
                    initialValue = 0f,
                    targetValue = (2f * Math.PI).toFloat(),
                    animationSpec = infiniteRepeatable(tween(1600, easing = androidx.compose.animation.core.LinearEasing)),
                    label = "wave",
                )
                Row(
                    Modifier.size(DsMetric.actionIcon.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0 until 4) {
                        val ph = t + i * 0.9f
                        val hh = 5f + 9f * (0.5f + 0.5f * kotlin.math.sin(ph))
                        Box(
                            Modifier
                                .width(2.4.dp)
                                .height(hh.dp)
                                .clip(RoundedCornerShape(1.2.dp))
                                .background(DsBlue),
                        )
                    }
                    Spacer(Modifier.width(0.dp))
                }
            }
            else -> {
                Icon(
                    painter = painterResource(R.drawable.ic_play_media_outline_20),
                    // read_aloud_start_label：朗读 / Read aloud
                    contentDescription = I18n.t("朗读", "Read aloud"),
                    tint = ds.iconGray,
                    modifier = Modifier.size(DsMetric.actionIcon.dp),
                )
            }
        }
    }
}
