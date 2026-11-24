package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.R
import com.yuyuyu.deepseek.data.Msg
import com.yuyuyu.deepseek.data.Session
import com.yuyuyu.deepseek.data.Settings
import androidx.compose.ui.graphics.Color
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.DsLayout
import com.yuyuyu.deepseek.ui.theme.DsMetric
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

/**
 * 官方侧边栏（真机 1600×2176 @2.0 → 800×1088dp，实测 audit-drawer.png）：
 *  - 抽屉宽 305（0…305；右缘 1dp 分隔线在 x=305…305.5）
 *  - 搜索胶囊：x 15…290.5（宽 275.5），高 45，圆角 22.5，填充 #F5F5F5 / 深色 #292929
 *    放大镜墨迹 16.5 @x 28，占位文字 17sp #8E9094 @x 53
 *  - 分组头「今天」15sp #8E9094 @x 20.5 / 行高 18（ui 节点 19）；右侧「多选」图标 17
 *  - 会话行文字 17sp #0F0F0F @x 21、行高 45；选中态 #EDF3FE 胶囊 x 9.5…296、圆角 15
 *  - 底部账号：行高 58、头像 ≈30 圆 @15、昵称 17sp @54.5，右侧「设置」19 @x 271.5
 */
@Composable
fun DrawerContent(
    sessions: List<Session>,
    currentId: String?,
    settings: Settings,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onDelete: (Session) -> Unit,
    onRename: (Session, String) -> Unit,
    onPin: (Session, Boolean) -> Unit,
    onSettings: () -> Unit,
    onModel: () -> Unit,
    onClose: () -> Unit,
    onJump: (String, Long) -> Unit = { _, _ -> },
) {
    val ds = LocalDsColors.current
    val dsDark = ds.pageBg.luminance() < 0.5f
    // ==== 多选管理模式（官方 ChatSessionBatchSelection.kt）====
    // 点分组头右侧的「多选」图标 / 会话行 ⋯ 菜单里的「多选」进入；
    // 每行左侧出现 19dp 圆形勾选，头行「选择对话 / 已选择 N 个对话」+ 右侧关闭，
    // 底部是「置顶 / 删除」两个等宽操作 + 竖向分隔线（官方深色实测 #8E9094 文字图标）。
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var batchPinConfirm by remember { mutableStateOf<Boolean?>(null) } // null=无 / false=置顶 / true=取消置顶
    val exitSelection = {
        selecting = false
        selectedIds = emptySet()
    }
    // 官方：抽屉宽 min(326dp, 0.8×窗口宽)（kt:ChatNavigationDrawerContent.kt:801），
    // 但平板 800dp 窗口实测只有 305dp（实测 audit-drawer.png x=610px/2、分隔线 x=610…611），
    // 以实测 305dp 为上限；公式只对极窄窗口（0.8×W < 305，即 W < 381dp）生效。
    val windowWDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val drawerW = DsMetric.drawerWidthDp(windowWDp).dp
    Column(
        Modifier
            .width(drawerW)
            .fillMaxHeight()
            // 官方实测：抽屉底色和右侧聊天区**完全同色**（浅色都 #FFFFFF、深色都 #0F0F0F），
            // 只有中间一条 1dp 分隔线。之前用 pageBg(#F8F8F8) 会和白色聊天区拼出左右两块，
            // 一眼就能看出「左右颜色不一样」。
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // 官方：搜索胶囊距抽屉顶 39.5dp（状态栏 24dp 之后 15.5dp）—— 实测 audit-drawer.png
        // 窄屏：官方搜索胶囊顶 ≈32.25（状态栏 22 之后 10）
        Spacer(Modifier.height(DsMetric.drawerSearchTop.dp))

        // 搜索胶囊：点击后进入搜索模式，下面列表被替换成结果
        var searchMode by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf(TextFieldValue("")) }
        val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(searchMode) {
            if (searchMode) {
                // 关键：不请求焦点的话，打的字会进底部聊天输入框，列表一直是会话列表
                runCatching { kotlinx.coroutines.delay(60); searchFocus.requestFocus() }
            }
        }
        val results = rememberFullTextSearch(sessions, query.text)

        if (selecting) {
            BatchSelectionHeader(
                count = selectedIds.size,
                onClose = { exitSelection() },
            )
        } else {
        Row(
            Modifier
                .fillMaxWidth()
                // 官方：搜索胶囊 x=15…290.5（宽 275.5）、高 45dp、圆角 22.5（胶囊）
                // —— 实测 audit-drawer.png mask(#F5F5F5) bbox
                .padding(start = DsMetric.drawerSearchX.dp, end = DsMetric.drawerSearchX.dp)
                .height(DsMetric.drawerSearchH.dp)
                .clip(RoundedCornerShape(DsMetric.drawerSearchRadius.dp))
                .background(ds.tileBg)
                .clickable { searchMode = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 官方：搜索图标墨迹 16.5dp，距胶囊左 13dp（x=28 - 15）—— 实测 audit-drawer.png
            Spacer(Modifier.width(13.dp))
            if (searchMode) {
                // 官方：搜索态左侧是返回箭头，点它退出搜索
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right_outline_20),
                    contentDescription = "退出搜索",
                    tint = ds.bodyText,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(180f)
                        .clickable {
                            query = TextFieldValue("")
                            searchMode = false
                        },
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_search_outline_16),
                    contentDescription = null,
                    tint = ds.iconGray,
                    modifier = Modifier.size(if (DsLayout.narrow) 14.3.dp else 16.5.dp),
                )
            }
            // 官方：占位文字 x=53dp（=15 胶囊左 + 13 + 16.5 图标 + 8.5 间距）—— 实测 audit-drawer.png
            Spacer(Modifier.width(if (DsLayout.narrow) 5.2.dp else 8.5.dp))
            if (searchMode) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).focusRequester(searchFocus),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = ds.bodyText),
                )
            } else {
                Text(
                    // search_hint：搜索对话内容... / Search chat content...
                    text = I18n.t("搜索对话内容...", "Search chat content..."),
                    // 官方：搜索占位 17sp、色 #8E9094 —— 实测 audit-drawer.png / ui (105,102)
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    color = ds.iconGray,
                )
            }
            if (searchMode && query.text.isNotEmpty()) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_outline_20),
                    contentDescription = "清除",
                    tint = ds.iconGray,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { query = TextFieldValue("") },
                )
                Spacer(Modifier.width(15.dp))
            }
        }

        // 官方：搜索胶囊底 84.5dp → 首个分组头行盒顶 101.5dp（ui (41,203) → 101.5dp）：
        // 5.5 + 分组行 paddingTop 11.43 = 16.93 ≈ 17dp —— 实测 audit-drawer.png
        Spacer(Modifier.height(if (DsLayout.narrow) 3.5.dp else 5.5.dp))
        }

        // 官方：历史按 今天 / 昨天 / 7 天内 / 更早 分组，组与组之间有分隔线
        // 不能用 remember(sessions)：sessions 是 SnapshotStateList，对象身份不变，
        // 置顶/重命名后不会重算分组，表现就是「必须重启 App 才生效」。
        val grouped = run {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val today = cal.timeInMillis
            val day = 24L * 60 * 60 * 1000
            val pinnedList = sessions.filter { it.pinned }
            val rest = sessions.filter { !it.pinned }
            buildList {
                if (pinnedList.isNotEmpty()) add(I18n.t("置顶", "Pinned") to pinnedList)
                addAll(rest.groupBy { s ->
                when {
                    // session_history_timeline_label_today / _yesterday：今天 / Today、昨天 / Yesterday
                    s.updatedAt >= today -> I18n.t("今天", "Today")
                    s.updatedAt >= today - day -> I18n.t("昨天", "Yesterday")
                    s.updatedAt >= today - 7 * day -> I18n.t("7 天内", "Last 7 days")
                    else -> I18n.t("更早", "Earlier")
                }
            }.toList())
            }
        }

        if (searchMode && query.text.isNotBlank()) {
            LazyColumn(Modifier.weight(1f)) {
                if (false) {
                    item {
                        Text(
                            text = "搜索对话内容",
                            fontSize = 14.sp,
                            color = ds.thoughtText,
                            modifier = Modifier.padding(start = 21.00.dp, top = 12.dp),
                        )
                    }
                } else if (results.isEmpty()) {
                    item {
                        Text(
                            // search_empty_result：没有找到相关内容 / No relevant results
                            text = I18n.t("没有找到相关内容", "No relevant results"),
                            fontSize = 14.sp,
                            color = ds.thoughtText,
                            modifier = Modifier.padding(start = 21.00.dp, top = 12.dp),
                        )
                    }
                } else {
                    items(results, key = { it.session.id + "-" + it.msgId }) { hit ->
                        FullTextSearchResultRow(
                            hit = hit,
                            onSelect = {
                                // 官方 resolveSessionForNavigation：切会话 + 定位到那条消息
                                onJump(hit.session.id, hit.msgId)
                                query = TextFieldValue("")
                                searchMode = false
                            },
                        )
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                grouped.forEachIndexed { gi, (name, list) ->
                    item(key = "h-$name") {
                        Column {
                            if (gi > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 21.00.dp, end = 21.00.dp, top = 11.43.dp, bottom = 4.57.dp)
                                        .height(1.dp)
                                        .background(ds.divider)
                                )
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    // 官方：分组头文字 x=20.5dp、行高 18dp（ui 节点高 19dp）—— ui (41,203)-(95,241) / kt:SessionGroupHeader.kt:111
                                    .padding(start = DsMetric.drawerGroupTextX.dp, end = 21.78.dp, top = 11.43.dp, bottom = if (DsLayout.narrow) 3.dp else 3.81.dp)
                                    .height(DsMetric.drawerGroupH.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = name,
                                    modifier = Modifier.weight(1f),
                                    // 官方：分组头 15sp、色 #8E9094 —— ui + 实测 audit-drawer.png
                                    fontSize = 15.sp,
                                    lineHeight = 19.43.sp,
                                    color = ds.secondary,
                                )
                                if (gi == 0 && !selecting) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_checklist_outline_16),
                                        contentDescription = "多选",
                                        tint = ds.iconGray,
                                        // 官方：分组多选图标 17dp —— ui (534,206)-(568,240)
                                        modifier = Modifier
                                            .size(17.dp)
                                            // 官方 SessionGroupHeader.kt:104 SelectionEntryIcon：
                                            // 点它进入多选管理模式（不是普通菜单）
                                            .clickable {
                                                selecting = true
                                                selectedIds = emptySet()
                                            },
                                    )
                                }
                            }
                        }
                    }
                    items(list, key = { it.id }) { s ->
                        SessionRow(
                            session = s,
                            selected = s.id == currentId,
                            selectionMode = selecting,
                            checked = selectedIds.contains(s.id),
                            onToggleSelect = {
                                selectedIds = if (selectedIds.contains(s.id)) {
                                    selectedIds - s.id
                                } else {
                                    selectedIds + s.id
                                }
                            },
                            onEnterSelection = {
                                selecting = true
                                selectedIds = setOf(s.id)
                            },
                            onSelect = { onSelect(s.id) },
                            onDelete = { onDelete(s) },
                            onRename = { onRename(s, it) },
                            onPin = { onPin(s, it) },
                        )
                    }
                }
            }
        }

        // 底部账号行：官方整行点击区高 58dp（ui (0,2000)-(611,2116)）、
        // 昵称 x=54.5dp、17sp、#0F0F0F，行尾设置图标 19dp 位 x=271.5…290.5 —— 实测 audit-drawer.png
        if (selecting) {
            // 官方 ChatSessionBatchSelection.kt:129 BatchSelectionFooter：
            // 高 61dp、顶部 1dp 分隔线、中间 1dp 竖线，两个等宽「置顶/取消置顶」「删除」，17sp #8E9094。
            BatchSelectionFooter(
                count = selectedIds.size,
                allPinned = selectedIds.isNotEmpty() && sessions.filter { selectedIds.contains(it.id) }.all { it.pinned },
                onPinToggle = {
                    if (selectedIds.isNotEmpty()) {
                        val sel = sessions.filter { selectedIds.contains(it.id) }
                        batchPinConfirm = sel.isNotEmpty() && sel.all { it.pinned }
                    }
                },
                onDelete = {
                    if (selectedIds.isNotEmpty()) confirmBatchDelete = true
                },
            )
        } else {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = DsMetric.drawerSearchX.dp, end = 14.5.dp, bottom = 6.dp)
                .height(DsMetric.drawerAccountH.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像 + 昵称（在 设置 → 账号管理 → 个人资料 里改）
            // 官方：头像直径按 15 左边距、昵称 54.5 反推 ≈30dp —— 实测 audit-drawer.png
            Box(Modifier.clickable(onClick = onSettings)) {
                AvatarView(path = settings.avatarPath, size = DsMetric.drawerAvatar.dp)
            }
            Text(
                // 本地账号 / Local account（官方此处显示登录昵称，本地版没有账号体系）
                text = settings.nickName.ifBlank { I18n.t("本地账号", "Local account") },
                modifier = Modifier
                    .weight(1f)
                    // 官方：昵称 x=54.5dp（15 + 30 头像 + 9.5）—— 官方：ui (109,2036)
                    .padding(start = 9.5.dp)
                    .clickable(onClick = onSettings),
                fontSize = 17.sp,
                lineHeight = 22.86.sp,
                color = ds.rowText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                // 官方：点这个「⋯」直接进设置，不弹菜单
                Icon(
                    painter = painterResource(R.drawable.ic_ellipsis_outline),
                    contentDescription = "设置",
                    tint = ds.iconGray,
                    modifier = Modifier
                        .size(DsMetric.DRAWER_TRAIL_ICON.dp)
                        .clickable(onClick = onSettings),
                )
            }
        }
        }
    }

    // ==== 多选：批量删除确认弹窗（官方 BatchDeleteConfirmDialog.kt:22 / 实测深色弹层 #333333）====
    if (confirmBatchDelete) {
        val n = selectedIds.size
        val panel = if (dsDark) Color(0xFF333333) else Color.White
        val fg = if (dsDark) Color(0xFFF8F8F8) else Color(0xFF0F0F0F)
        val sub = if (dsDark) Color(0xFFB9BABB) else Color(0xFF7D7F85)
        Dialog(
            onDismissRequest = { confirmBatchDelete = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .width(357.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(panel)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 官方文案：删除选择的 %d 个对话？/ 删除后，对话将不可恢复，由该对话生成的分享链接也将失效。
                Text(
                    text = "删除选择的 $n 个对话？",
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "删除后，对话将不可恢复，由该对话生成的分享链接也将失效。",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = sub,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                // 官方：整宽红色胶囊按钮（高 44、圆角 22、#F25A5A / 浅色 #EF4444）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(ds.destructive)
                        .clickable {
                            val targets = sessions.filter { selectedIds.contains(it.id) }
                            confirmBatchDelete = false
                            exitSelection()
                            targets.forEach { onDelete(it) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("删除", fontSize = 17.sp, color = Color.White)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "取消",
                    fontSize = 17.sp,
                    color = sub,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { confirmBatchDelete = false }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }

    // ==== 多选：批量置顶/取消置顶确认（官方 BatchPinConfirmDialog.kt:23）====
    batchPinConfirm?.let { unpin ->
        val n = selectedIds.size
        val panel = if (dsDark) Color(0xFF333333) else Color.White
        val fg = if (dsDark) Color(0xFFF8F8F8) else Color(0xFF0F0F0F)
        val sub = if (dsDark) Color(0xFFB9BABB) else Color(0xFF7D7F85)
        val actionColor = ds.chipIcon
        val title = if (unpin) "将选择的 $n 个对话取消置顶？" else "将选择的 $n 个对话置顶？"
        val actionLabel = if (unpin) "取消置顶" else "置顶"
        Dialog(
            onDismissRequest = { batchPinConfirm = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .width(357.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(panel)
                    .padding(top = 22.dp, bottom = 6.dp),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    color = fg,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "保留",
                        fontSize = 16.sp,
                        color = sub,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { batchPinConfirm = null }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = actionLabel,
                        fontSize = 16.sp,
                        color = actionColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val pin = !unpin
                                val targets = sessions.filter { selectedIds.contains(it.id) }
                                batchPinConfirm = null
                                exitSelection()
                                targets.forEach { onPin(it, pin) }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
            }
        }
    }
}


/** 多选模式头行（官方 ChatSessionBatchSelection.kt:62 BatchSelectionHeader，高 48dp）。 */
@Composable
private fun BatchSelectionHeader(count: Int, onClose: () -> Unit) {
    val ds = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            // 官方：标题 x=20.5dp；右侧关闭圆钮 34dp、右缘距抽屉右 13dp（ui [532,105]-[570,143]）
            .padding(start = 20.5.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // 官方文案：select_chat_title「选择对话」/ session_batch_selected_count「已选择 %d 个对话」
            text = if (count <= 0) "选择对话" else "已选择 $count 个对话",
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            lineHeight = 26.sp,
            color = ds.inputIcon,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                // 官方深色圆钮底实测 #292929（= ds.tileBg），图标 20dp #E5E5E5
                .background(ds.tileBg)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close_outline_20),
                contentDescription = "取消",
                tint = ds.bodyText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 多选模式底部操作条（官方 ChatSessionBatchSelection.kt:129 BatchSelectionFooter，高 61dp）。 */
@Composable
private fun BatchSelectionFooter(
    count: Int,
    allPinned: Boolean,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val ds = LocalDsColors.current
    val dark = ds.pageBg.luminance() < 0.5f
    val line = if (dark) Color(0xFF3C3C3D) else ds.divider
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        Row(Modifier.fillMaxWidth().height(61.dp)) {
            BatchFooterAction(
                icon = if (allPinned) R.drawable.ic_unpin_outline_20 else R.drawable.ic_pin_outline_20,
                label = if (allPinned) "取消置顶" else "置顶",
                onClick = onPinToggle,
                modifier = Modifier.weight(1f),
            )
            // 官方：中间 1dp 竖分隔线（深色实测 #3C3C3D）
            Box(Modifier.width(1.dp).fillMaxHeight().background(line))
            BatchFooterAction(
                icon = R.drawable.ic_trash_outline_20,
                label = "删除",
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BatchFooterAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ds = LocalDsColors.current
    Row(
        modifier
            .fillMaxHeight()
            // 官方：图标 18dp + 8dp 间距 + 17sp 文字，居中于各自半栏；文字/图标 #8E9094
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ds.iconGray,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            color = ds.iconGray,
        )
    }
}

/** 多选模式每行左侧的圆形勾选（官方：19dp、未选描边 #474849 / 选中填充 #6799FE + 白勾）。 */
@Composable
private fun SelectionCircle(checked: Boolean) {
    val ds = LocalDsColors.current
    val dark = ds.pageBg.luminance() < 0.5f
    val on = ds.chipIcon
    val ring = if (dark) Color(0xFF474849) else Color(0xFFC7C7CC)
    Canvas(Modifier.size(19.dp)) {
        val r = size.minDimension / 2f
        if (checked) {
            drawCircle(color = on, radius = r)
            val stroke = 1.9.dp.toPx()
            val p = Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.52f)
                lineTo(size.width * 0.43f, size.height * 0.70f)
                lineTo(size.width * 0.75f, size.height * 0.32f)
            }
            drawPath(
                path = p,
                color = Color.White,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            drawCircle(
                color = ring,
                radius = r - 0.6.dp.toPx(),
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }
    }
}

data class FullTextSearchHit(
    val session: Session,
    val msgId: Long,
    val role: String,
    val text: String,
    val start: Int,
    val end: Int,
)

@Composable
private fun rememberFullTextSearch(sessions: List<Session>, query: String): List<FullTextSearchHit> {
    var results by remember { mutableStateOf(emptyList<FullTextSearchHit>()) }
    LaunchedEffect(query, sessions) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(250)
        results = withContext(Dispatchers.Default) {
            val q = query.lowercase()
            val out = ArrayList<FullTextSearchHit>()
            for (s in sessions) {
                for (m in s.messages) {
                    val text = m.text
                    if (text.isEmpty()) continue
                    val idx = text.lowercase().indexOf(q)
                    if (idx >= 0) {
                        out.add(FullTextSearchHit(s, m.id, m.role, text, idx, idx + q.length))
                    }
                }
            }
            out
        }
    }
    return results
}

@Composable
private fun FullTextSearchResultRow(hit: FullTextSearchHit, onSelect: () -> Unit) {
    val ds = LocalDsColors.current
    // 官方 FullTextSearchItemRow：左头像 39dp，标题 17sp + 右上日期 13sp，第二行命中片段 14sp 两行
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 21.dp, end = 21.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(39.dp)
                .clip(CircleShape)
                .background(Color(0xFFF4F5F7)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (hit.role == "user") R.drawable.ic_person else R.drawable.ic_whale_fg,
                ),
                contentDescription = null,
                tint = Color(0xFF787880),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hit.session.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    color = ds.bodyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatSearchDate(hit.session.updatedAt),
                    fontSize = 13.sp,
                    color = ds.thoughtText,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = highlightedText(hit.text, hit.start, hit.end),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ds.thoughtText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 官方右上角日期：11月20日 / 昨天 之类，这里用最接近的「M月d日」 */
private fun formatSearchDate(ts: Long): String {
    if (ts <= 0L) return ""
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val now = java.util.Calendar.getInstance()
    val sameDay = c.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
        c.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay) return "今天"
    val y = c.get(java.util.Calendar.YEAR)
    val m = c.get(java.util.Calendar.MONTH) + 1
    val d = c.get(java.util.Calendar.DAY_OF_MONTH)
    return if (y == now.get(java.util.Calendar.YEAR)) "${m}月${d}日" else "${y}年${m}月${d}日"
}

private fun highlightedText(text: String, start: Int, end: Int): AnnotatedString {
    val ctx = 20
    val from = (start - ctx).coerceAtLeast(0)
    val to = (end + ctx).coerceAtMost(text.length)
    val before = text.substring(from, start)
    val match = text.substring(start, end)
    val after = text.substring(end, to)
    return buildAnnotatedString {
        if (from > 0) append("…")
        append(before)
        withStyle(SpanStyle(background = Color(0xFFD0E0FC))) { append(match) }
        append(after)
        if (to < text.length) append("…")
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: Session,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    checked: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onPin: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var draft by remember(session.id) { mutableStateOf(session.title) }
    val dsRow = LocalDsColors.current
    val density = LocalDensity.current
    val screenH = LocalConfiguration.current.screenHeightDp
    // 菜单打开时按行在屏幕中的位置做「向下/向上」自适应，避免靠近底部的行菜单被屏幕裁掉
    var rowTopPx by remember { mutableStateOf(0f) }
    val menuTopPx = with(density) { 22.dp.roundToPx() }
    val menuHpx = with(density) { (46 * 4).dp.roundToPx() }
    val screenHpx = with(density) { screenH.dp.roundToPx() }
    val menuOffsetY = if (rowTopPx + menuTopPx + menuHpx > screenHpx - 8) {
        -(menuHpx + menuTopPx + 8)
    } else 0

    // 官方会话行：文字 17sp、x=21dp、行高 45dp；选中态是 #EDF3FE 胶囊
    // 胶囊 x=9.5…296（宽 286.5）、圆角≈15dp —— 实测 audit-drawer.png；行高 45 —— ui (0,249)-(611,339)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (DsLayout.narrow) 8.2.dp else DsMetric.DRAWER_SELECT_INSET.dp)
            .clip(RoundedCornerShape(if (DsLayout.narrow) 13.dp else DsMetric.DRAWER_SELECT_RADIUS.dp))
            // 官方多选态每行没有高亮胶囊，只靠左侧勾选 + 蓝色文字表示选中
            .background(if (!selectionMode && selected) dsRow.chipOn else Color.Transparent)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onSelect() },
                onLongClick = { if (selectionMode) onToggleSelect() else menu = true },
            )
            .height(DsMetric.drawerRowH.dp)
            // 文字 x=21 → 21 - 9.5(胶囊左) = 11.5；行尾图标右缘 x=290.5 → 295.5 - 290.5 = 5
            .padding(start = if (DsLayout.narrow) 9.8.dp else 11.5.dp, end = 5.dp)
            .then(
                if (menu) {
                    Modifier.onGloballyPositioned {
                        rowTopPx = it.positionInWindow().y
                    }
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            // 官方：勾选圈 19dp 起 x=21dp，文字 x=49dp（19 + 9 间距）
            SelectionCircle(checked = checked)
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = session.title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 官方：会话行 17sp、未选中 #0F0F0F、选中 #426EFE —— ui (42,271)-(569,316) + 实测
            // 多选态：选中项 #6799FE 蓝、未选 #E1E2EC —— 官方深色实测
            fontSize = 17.sp,
            lineHeight = 22.86.sp,
            color = when {
                selectionMode && checked -> dsRow.chipIcon
                selectionMode -> dsRow.rowText
                selected -> DsBlue
                else -> dsRow.rowText
            },
        )
        if (!selectionMode) {
            // 官方：每行右侧一个「⋯」（19dp），跟长按是同一个菜单
            Icon(
                painter = painterResource(R.drawable.ic_ellipsis_outline),
                contentDescription = "更多",
                tint = dsRow.iconGray,
                modifier = Modifier
                    .size(DsMetric.DRAWER_TRAIL_ICON.dp)
                    .clickable { menu = true },
            )
            DsAnchorMenu(
                expanded = menu,
                onDismiss = { menu = false },
                offsetY = menuOffsetY,
                // 官方菜单顺序：重命名 / 置顶 / 多选 / 删除 —— 实测官方深色长按菜单截图
                items = listOf(
                    DsMenuItem(
                        label = "重命名",
                        icon = R.drawable.ic_edit_outline_20,
                        onClick = { draft = session.title; renaming = true },
                    ),
                    DsMenuItem(
                        label = if (session.pinned) "取消置顶" else "置顶",
                        icon = R.drawable.ic_pin_outline_20,
                        onClick = { onPin(!session.pinned) },
                    ),
                    DsMenuItem(
                        label = "多选",
                        icon = R.drawable.ic_checklist_outline_16,
                        onClick = onEnterSelection,
                    ),
                    DsMenuItem(
                        label = "删除",
                        icon = R.drawable.ic_trash_outline_20,
                        danger = true,
                        onClick = onDelete,
                    ),
                ),
            )
        }
    }

    if (renaming) {
        // 官方重命名不是 Material AlertDialog：白底圆角面板 + 浅灰输入框 + 底部两个文字按钮
        Dialog(onDismissRequest = { renaming = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LocalDsColors.current.cardBg)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Text("重命名对话", fontSize = 17.sp, color = LocalDsColors.current.bodyText)
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        color = LocalDsColors.current.bodyText,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(DsBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "取消",
                        fontSize = 16.sp,
                        color = LocalDsColors.current.subText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { renaming = false }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "保存",
                        fontSize = 16.sp,
                        color = DsBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                renaming = false
                                if (draft.isNotBlank()) onRename(draft.trim())
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
