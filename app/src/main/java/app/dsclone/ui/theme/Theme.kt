package com.yuyuyu.deepseek.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.yuyuyu.deepseek.data.Store

/**
 * 官方 2.9.0 取色（从真机截图按主色统计得出）：
 *  浅色：主蓝 #426EFE / 正文 #191B23 / 气泡文字 #0F0F0F
 *  思考灰 #7D7F85 / 操作图标 #8E9094 / 占位符 #B9BABB
 *  气泡与选中 chip 底色 #EDF3FE
 *  深色（真机 1600x2176 @2.0 截图统计）：页面底 #0F0F0F / 正文与气泡文字 #E1E2EC /
 *  用户气泡 #333333 / 输入卡 #242424 / 分隔线 #333333 / 占位符 #74767B /
 *  chip 选中态 填充 #283142 + 描边 #33436B + 文字图标 #6799FE
 */
val DsBlue = Color(0xFF426EFE)

/**
 * 窄屏（A7 / Android 11 / 640×876dp 单栏）专用度量开关。
 *
 * 为什么需要：平板上对齐的官方 2.9.0 参数，在 640dp 单栏窗口下官方 2.8.0 渲染出来的
 * 实际尺寸是另一套（实测 official 2.8.0 on A7；见 _work/shots/a11-off-*.png）。
 * 这里用「是否窄屏」做分支：宽屏（pane ≥ 768dp 的平板/双栏）继续用原来的平板参数，
 * 窄屏走下面各文件里的 narrow 分支，两套互不影响。
 *
 * 由 [DsTheme] 在每次组合时按 LocalConfiguration.screenWidthDp 写入。
 * 旋转屏幕会重建 Activity（manifest 未声明 configChanges），所以普通变量足够。
 */
object DsLayout {
    /** 窗口宽 < 768dp → 单栏窄屏（官方在 640dp 屏上的实测值生效） */
    @Volatile
    var narrow: Boolean = false
        private set

    internal fun update(windowWidthDp: Int) {
        narrow = windowWidthDp < 768
    }
}

/** 官方实体尺寸常量（dp），全部来自真机 uiautomator + 像素测量 */
object DsMetric {
    // 顶栏（官方 ui-official-chatclosed.xml (5,60)-(95,150)、实测 audit-chat.png）
    /** 顶栏内容行 45dp；上面再留 6dp（状态栏 24dp 以下共 51dp）——实测 audit-chat.png */
    const val TOP_BAR_H = 45f
    /** 内容行上留白 6dp（顶栏总高 51dp - 45dp）——实测 audit-chat.png 列扫描 */
    const val TOP_BAR_TOP_PAD = 6f
    /** 汉堡点击盒起点 x=2.5dp —— 官方：ui-official-chatclosed.xml (5,60) */
    const val TOP_BAR_PAD_START = 2.5f
    /** 图标触控盒 45×45 —— 官方：ui-official-chatclosed.xml */
    const val TOP_ICON_BOX = 45f
    /** 图标盒 22.5dp（墨迹 17.5×14.5）—— 官方：ui-official-chatclosed.xml (27,82)-(72,127) */
    const val TOP_ICON = 22.5f
    /** 右两个按钮间距 4dp —— 官方：ui-official-chatclosed.xml (751→755) */
    const val TOP_ICON_GAP = 4f
    /** 标题文字 x=57dp —— 官方：ui-official-chatclosed.xml (114,85) */
    const val TOP_TITLE_X = 57f
    /** 自动朗读按钮盒 x=706 —— 官方：ui-official-chatclosed.xml (1412,60) */
    const val TOP_MUTE_X = 706f
    /** 开启新对话按钮盒 x=755，右缘贴屏幕右边 —— 官方：ui-official-chatclosed.xml (1510,60) */
    const val TOP_NEW_X = 755f

    // 消息（官方 800dp 窗口实测：AI 正文左 62.5、正文列宽 675 居中、操作行 49.5…750.5）
    /** AI 正文左边界 x=62.5dp —— 官方：ui 所有 AI 文本节点 x=125px */
    const val MSG_PAD_X = 62.5f
    /** AI 正文右内边距 62.5dp（800-737.5）—— 实测 audit-chat.png */
    const val MSG_PAD_END = 62.5f
    /** 正文列最大宽 675dp（800 窗口下居中）—— 实测 audit-chat.png / ui 操作行盒 */
    const val MSG_COL_W = 675f
    /** 用户气泡内边距 水平15 / 垂直9.5 —— 官方：ui (245,215)-(1460,607) 对 气泡 215,196 */
    const val BUBBLE_PAD_H = 15f
    const val BUBBLE_PAD_V = 9.5f
    /** 用户气泡右缘距屏幕右 55dp（气泡右缘 x=745）—— 实测 audit-chat.png */
    const val BUBBLE_RIGHT = 55f
    /** 用户气泡圆角 ≈20.5dp（拟合 20.46）—— 实测 audit-chat.png 左上角弧形拟合 */
    const val BUBBLE_RADIUS = 20f
    /** 消息块上下间距 13dp（气泡底 313 → 思考行顶 326）—— 实测 audit-chat.png */
    const val MSG_GAP_Y = 13f
    /** 正文字号/行高 17sp/26sp —— 官方：kt:MarkdownTypography.kt:84、kt:UserChatMessageBubble.kt:214 */
    const val BODY_SIZE = 17f
    const val BODY_LINE = 26f
    /** 正文/气泡/思考行行高 26sp —— 官方：kt:MarkdownTypography.kt:84 */
    const val LABEL_LINE = 26f

    // 思考行（官方：ui 盒 (125,652)-(1479,742) 高 45dp）
    const val THINK_ROW_H = 45f
    /** 展开 chevron 19.5dp —— 官方：ui (414,678)-(453,715) */
    const val THINK_CHEVRON = 19.5f

    // 操作行（官方：ui 盒 y=743.5…788.5 高 45dp；单元格宽 38、图标 19）
    const val ACTION_CELL = 38f
    const val ACTION_H = 45f
    const val ACTION_ICON = 19f
    /** 操作行比正文列左右各多出 13dp（49.5 vs 62.5）—— 官方：ui-official-chatclosed.xml */
    const val ACTION_PAD_START = 13f
    const val ACTION_PAD_END = 13f
    /** 声明与操作行间距 4.5dp —— 官方：ui 739 → 743.5 */
    const val ACTION_TOP = 4.5f

    // 输入卡（官方实测：x=51.5…748.5 宽 697、y=942.5…1042.5 高 100、圆角≈24.8）
    /** 窄窗（<600dp）左右外边距（官方手机 411dp → 卡片 386.7 = 411-24.3）—— 实测 */
    const val CARD_MARGIN_X = 12.20f
    /** 输入卡最大宽 697dp（=800 窗口下 51.5 外边距）—— 实测 audit-chat.png 行/列扫描 */
    const val CARD_MAX_W = 697f
    const val CARD_TOP = 942.5f
    const val CARD_BOTTOM = 1042.5f
    const val CARD_H = 100f
    /** 圆角 ≈24.8（拟合 24.83，取 25）—— 实测 audit-chat.png 卡顶左角 */
    const val CARD_RADIUS = 25f
    /** 占位符距卡左 16dp（x=67.5 / 卡左 51.5）—— 实测 audit-chat.png + ui (135,1916) */
    const val CARD_PAD_H = 16f
    /** chip 药丸高 30dp、圆角 15dp（胶囊）—— 实测 audit-chat.png mask bbox y 2002..2061px */
    const val CHIP_H = 30f
    /** chip 点击区高 47dp —— 官方：ui (104,1968)-(329,2062) */
    const val CHIP_TAP = 47f
    /** chip 圆角 15dp —— 实测 audit-chat.png mask 圆角=高/2 */
    const val CHIP_RADIUS = 15f

    // 抽屉（官方实测：audit-drawer.png 面板 0…305dp，分隔线 x=610…611px）
    const val DRAWER_W = 305f
    /** 搜索胶囊 x=15…290.5，宽 275.5 —— 实测 audit-drawer.png mask(#F5F5F5) bbox */
    const val DRAWER_SEARCH_X = 15f
    const val DRAWER_SEARCH_W = 275.5f
    /** 搜索胶囊高 45dp、圆角 22.5（胶囊）—— 实测 audit-drawer.png */
    const val DRAWER_SEARCH_H = 45f
    const val DRAWER_SEARCH_RADIUS = 22.5f
    /** 会话行高 45dp —— 官方：ui (0,249)-(611,339)，行距 45.5 —— 实测 audit-drawer.png */
    const val DRAWER_ROW_H = 45f
    /** 会话行文字 x=21dp —— 官方：ui (42,271) */
    const val DRAWER_ROW_TEXT_X = 21f
    /** 分组头文字 x=20.5dp、字号 15sp —— 官方：ui (41,203)-(95,241) */
    const val DRAWER_GROUP_TEXT_X = 20.5f
    /** 分组头行高 19dp（ui 节点高 19；smali 候选 18）—— 官方：ui (41,203)-(95,241)、kt:SessionGroupHeader.kt:111 */
    const val DRAWER_GROUP_H = 19f
    /** 选中行高亮胶囊 x=9.5…296（宽 286.5）、圆角≈15dp —— 实测 audit-drawer.png */
    const val DRAWER_SELECT_INSET = 9.5f
    const val DRAWER_SELECT_RADIUS = 15f
    /** 行尾「⋯/多选」图标 19dp —— 官方：ui (543,2039)-(581,2077)、实测 x=271.5 */
    const val DRAWER_TRAIL_ICON = 19f
    /** 底部账号行高 58dp —— 官方：ui (0,2000)-(611,2116) */
    const val DRAWER_ACCOUNT_H = 58f
    /** 账号昵称 x=54.5dp（=15 左边距 + 30 头像 + 9.5 间距）—— 官方：ui (109,2036) */
    const val DRAWER_ACCOUNT_TEXT_X = 54.5f
    /** 头像直径 ≈30dp（按 15 左边距、昵称 54.5 反推）—— 实测 audit-drawer.png */
    const val DRAWER_AVATAR = 30f

    // 抽屉断点公式（官方 ChatNavigationDrawerContent.kt:801 = min(326, 0.8×窗口宽)；
    // 但平板 800dp 窗口实测只有 305dp（audit-drawer.png），故以 305 为上限，公式只用于极窄窗口）
    fun drawerWidthDp(windowWidthDp: Int): Float =
        if (DsLayout.narrow) minOf(265f, windowWidthDp * 0.8f)
        else minOf(DRAWER_W, windowWidthDp * 0.8f)

    // 设置页（真机 1600x2176 @2.0 实测）
    const val SET_MARGIN = 40f          // 卡片左右外边距
    const val SET_MARGIN_NARROW = 15f   // 账号管理页卡片外边距
    const val SET_ROW_H = 45f           // 行高 90px
    const val SET_RADIUS = 14f          // 卡片圆角 28px
    const val SET_ICON_INSET = 15f      // 图标离卡片左边
    const val SET_ICON = 24f
    const val SET_ICON_GAP = 8f
    const val SET_END_PAD = 12f
    const val SET_HEAD_TOP = 26.5f
    const val SET_HEAD_BOTTOM = 7.5f
    const val SET_FIRST_TOP = 9.5f

    // ==========================================================================
    //  窄屏分支（A7 / Android 11 / 640×876dp 单栏，官方 com.deepseek.chat 2.8.0 同机实测）
    //  出处：_work/shots/a11-off-*.png + uiautomator bounds（px÷2=dp）
    //  宽屏仍返回上面那套平板实测值，平板布局一个数都不变。
    // ==========================================================================

    /** 顶栏内容行：官方窄屏 39（chat_top_bar 22..64.5 = 3.5 留白 + 39 行） */
    val topBarH: Float get() = if (DsLayout.narrow) 39f else TOP_BAR_H
    /** 顶栏内容上留白：官方窄屏 1.5（图标墨迹 y 33.5..53 → 盒 23.5..62.5） */
    val topBarTopPad: Float get() = if (DsLayout.narrow) 1.5f else TOP_BAR_TOP_PAD
    /** 汉堡盒起点：官方窄屏 2（盒 2..41，墨迹 12..31.5） */
    val topBarPadStart: Float get() = if (DsLayout.narrow) 2f else TOP_BAR_PAD_START
    /** 顶栏图标触控盒：官方窄屏 39（ui 盒 558.5..597.5） */
    val topIconBox: Float get() = if (DsLayout.narrow) 39f else TOP_ICON_BOX
    /** 顶栏图标墨迹：官方窄屏 19.5（ui 墨迹 12..31.5） */
    val topIcon: Float get() = if (DsLayout.narrow) 19.5f else TOP_ICON
    /** 右两按钮间距：官方窄屏 3.5（597.5 → 601） */
    val topIconGap: Float get() = if (DsLayout.narrow) 3.5f else TOP_ICON_GAP
    /** 标题左内边距：官方窄屏 8.5（盒右缘 41 → 标题墨迹 49.5） */
    val topTitlePadStart: Float get() = if (DsLayout.narrow) 8.5f else 9.5f

    /** AI 正文列左右内边距：官方窄屏 32.5（正文墨迹 x=33、右缘 607.5，640−2×32.5） */
    val msgPadX: Float get() = if (DsLayout.narrow) 32.5f else MSG_PAD_X
    /** 用户气泡右内边距：官方窄屏 26.5（气泡 bbox 右缘 613.5） */
    val bubbleEndPad: Float get() = if (DsLayout.narrow) 26.5f else BUBBLE_RIGHT
    /** 气泡水平内边距：官方窄屏 12.65（气泡 52.5 宽 = 2 字 27.2 + 2×12.65） */
    val bubblePadH: Float get() = if (DsLayout.narrow) 12.65f else BUBBLE_PAD_H
    /** 气泡垂直内边距：官方窄屏 8.2（气泡 37 高 = 行高 20.8 + 2×8.2） */
    val bubblePadV: Float get() = if (DsLayout.narrow) 8.2f else BUBBLE_PAD_V
    /** 气泡圆角：官方窄屏 17.3 */
    val bubbleRadius: Float get() = if (DsLayout.narrow) 17.3f else BUBBLE_RADIUS
    /** 消息块纵向间距：官方窄屏 10.5（用户气泡底 119.5 → AI 块顶 130） */
    val msgGapY: Float get() = if (DsLayout.narrow) 10.5f else MSG_GAP_Y

    /** 操作行单元格：官方窄屏 32.5（ui 点击盒 23..55.5、行高 39） */
    val actionCell: Float get() = if (DsLayout.narrow) 32.5f else ACTION_CELL
    /** 操作行高：官方窄屏 39（ui 点击盒 y 167.5..206.5） */
    val actionH: Float get() = if (DsLayout.narrow) 39f else ACTION_H
    /** 操作行图标：官方窄屏 16.5（ui 墨迹 16.5） */
    val actionIcon: Float get() = if (DsLayout.narrow) 16.5f else ACTION_ICON
    /** 操作行左右外扩：官方窄屏 11.25（行盒 21.25..618.75 vs 正文 32.5..607.5） */
    val actionPadStart: Float get() = if (DsLayout.narrow) 11.25f else ACTION_PAD_START
    val actionPadEnd: Float get() = if (DsLayout.narrow) 11.25f else ACTION_PAD_END
    /** 操作行左侧内缩：官方窄屏 5.25（首个图标墨迹 35 = 21.25 + 5.25 + 8 居中偏移） */
    val actionLeadIn: Float get() = if (DsLayout.narrow) 5.25f else 0f
    /** 操作行整体右移补偿：实测行内容整体比官方左 11dp（含右侧「重新生成」），这里补 11 */
    val actionShift: Float get() = if (DsLayout.narrow) 11f else 0f
    /** 「重新生成」单元格：官方窄屏 39（ui 盒 580..619） */
    val actionRegenCell: Float get() = if (DsLayout.narrow) 39f else 45f

    /** 输入卡左右外边距：官方窄屏 18（卡 bbox 18..622 = 604 宽） */
    val cardMarginX: Float get() = if (DsLayout.narrow) 18f else CARD_MARGIN_X
    /** 输入卡圆角：官方窄屏 21（卡顶左角拟合） */
    val cardRadius: Float get() = if (DsLayout.narrow) 21f else CARD_RADIUS
    /** 输入卡内容水平内边距：官方窄屏 13（占位符墨迹 32 = 卡 18 + 描边 1 + 13） */
    val cardPadH: Float get() = if (DsLayout.narrow) 13f else CARD_PAD_H
    /** 输入行高：官方窄屏 41（卡内 768.5..810.5） */
    val cardInputRowH: Float get() = if (DsLayout.narrow) 41f else 46f
    /** 输入行上/下内边距：官方窄屏 12.25/8.25（文字节点 781..801.5） */
    val cardInputPadTop: Float get() = if (DsLayout.narrow) 12.25f else 11.3f
    val cardInputPadBottom: Float get() = if (DsLayout.narrow) 8.25f else 8.7f
    /** 输入文字最小行盒：官方窄屏 20.5（占位符节点高 20.5） */
    val cardTextFieldMinH: Float get() = if (DsLayout.narrow) 20.5f else 26f
    /** 操作行高：官方窄屏 43.5（卡内 811.5..854） */
    val cardActionRowH: Float get() = if (DsLayout.narrow) 43.5f else 51f
    /** chip 药丸：官方窄屏 80×26、圆角 13（mask bbox x28..108 / y818.5..844） */
    val chipW: Float get() = if (DsLayout.narrow) 80f else 94f
    val chipH: Float get() = if (DsLayout.narrow) 26f else CHIP_H
    val chipRadius: Float get() = chipH / 2f
    val chipIcon: Float get() = if (DsLayout.narrow) 13f else 15f
    val chipIconGap: Float get() = if (DsLayout.narrow) 3.55f else 4.1f
    val chipTextSize: Float get() = if (DsLayout.narrow) 14f else 13f
    val chipLineH: Float get() = if (DsLayout.narrow) 18f else 19.43f
    /** chip 起始内缩：官方窄屏 9（药丸左 28 = 卡 18 + 描边 1 + 9） */
    val chipStartPad: Float get() = if (DsLayout.narrow) 9f else 10.5f
    /** chip 间距：官方窄屏 7（108 → 115） */
    val chipGap: Float get() = if (DsLayout.narrow) 7f else 7.5f
    /** ⊕/麦克风盒：官方窄屏 22（墨迹 20.5~21） */
    val inputIconBox: Float get() = if (DsLayout.narrow) 22f else 25f
    val inputIconSize: Float get() = if (DsLayout.narrow) 21.5f else 24.6f
    /** ⊕ 与麦克风间距：官方窄屏 14.5（⊕ 墨迹右 571.5 → 麦克风墨迹左 587） */
    val inputIconGap: Float get() = if (DsLayout.narrow) 14.5f else 16.5f
    /** 右侧图标组离卡内容右缘：官方窄屏 13（麦克风墨迹右 608 = 621 − 13） */
    val inputIconEndPad: Float get() = if (DsLayout.narrow) 13f else 15f
    /** 发送圆钮：官方窄屏 24.8 */
    val sendBtnSize: Float get() = if (DsLayout.narrow) 24.8f else 28.57f
    /** 底部免责文案容器：官方窄屏 卡底 855 → 屏底 876 共 21（3 顶 + 18 高） */
    val footerTopPad: Float get() = 3f
    val footerH: Float get() = if (DsLayout.narrow) 18f else 12.5f

    /** 抽屉：官方窄屏 265 宽（scrim 从 x=265 开始） */
    val drawerW: Float get() = if (DsLayout.narrow) 265f else DRAWER_W
    /** 抽屉搜索胶囊：官方窄屏 13 左、高 39、圆角 19.5 */
    val drawerSearchX: Float get() = if (DsLayout.narrow) 13f else DRAWER_SEARCH_X
    val drawerSearchH: Float get() = if (DsLayout.narrow) 39f else DRAWER_SEARCH_H
    val drawerSearchRadius: Float get() = if (DsLayout.narrow) 19.5f else DRAWER_SEARCH_RADIUS
    /** 抽屉搜索区上留白：官方窄屏 10（胶囊顶 ≈32.25，状态栏 22 以下） */
    val drawerSearchTop: Float get() = if (DsLayout.narrow) 10f else 15.5f
    /** 会话行高：官方窄屏 39（行距 115→154→193 = 39） */
    val drawerRowH: Float get() = if (DsLayout.narrow) 39f else DRAWER_ROW_H
    /** 会话行文字 x：官方窄屏 18 */
    val drawerRowTextX: Float get() = if (DsLayout.narrow) 18f else DRAWER_ROW_TEXT_X
    /** 分组头文字 x / 行高：官方窄屏 18 / 16.5 */
    val drawerGroupTextX: Float get() = if (DsLayout.narrow) 18f else DRAWER_GROUP_TEXT_X
    val drawerGroupH: Float get() = if (DsLayout.narrow) 16.5f else DRAWER_GROUP_H
    /** 底部账号行：官方窄屏 50.3、头像 26 */
    val drawerAccountH: Float get() = if (DsLayout.narrow) 50.3f else DRAWER_ACCOUNT_H
    val drawerAvatar: Float get() = if (DsLayout.narrow) 26f else DRAWER_AVATAR
    val drawerAccountTextX: Float get() = if (DsLayout.narrow) 46.5f else DRAWER_ACCOUNT_TEXT_X

    // 设置页窄屏分支：官方窄屏 卡片外边距 15、行高 39、圆角 12（a11-off-13-settings-dark.xml）
    val setMargin: Float get() = if (DsLayout.narrow) 13f else SET_MARGIN
    val setRowH: Float get() = if (DsLayout.narrow) 39f else SET_ROW_H
    val setRadius: Float get() = if (DsLayout.narrow) 12f else SET_RADIUS
    val setIconInset: Float get() = if (DsLayout.narrow) 13f else SET_ICON_INSET
    val setIcon: Float get() = if (DsLayout.narrow) 21f else SET_ICON
    val setIconGap: Float get() = if (DsLayout.narrow) 7f else SET_ICON_GAP
    val setEndPad: Float get() = if (DsLayout.narrow) 12f else SET_END_PAD
    val setHeadTop: Float get() = if (DsLayout.narrow) 24.5f else SET_HEAD_TOP
    val setHeadBottom: Float get() = if (DsLayout.narrow) 6.5f else SET_HEAD_BOTTOM
    val setFirstTop: Float get() = if (DsLayout.narrow) 8f else SET_FIRST_TOP
    val setSectionGap: Float get() = if (DsLayout.narrow) 29.5f else 34f
    /** 设置行/分组标题/顶栏标题的行高（官方窄屏节点高 17 / 15 / 15 → sp：17/0.8≈21、15/0.8≈18.75） */
    val setItemLineH: Float get() = if (DsLayout.narrow) 21f else 26f
    val setHeadLineH: Float get() = if (DsLayout.narrow) 18.75f else 17f
    val setTitleLineH: Float get() = if (DsLayout.narrow) 18.75f else 26f
}

data class DsColors(
    val bubble: Color,
    val bubbleText: Color,
    val codeBg: Color,
    val codeHeader: Color,
    val thoughtBg: Color,
    val thoughtText: Color,
    val chipBg: Color,
    val chipText: Color,
    val divider: Color,
    val subText: Color,
    val userBubble: Color,
    val bodyText: Color,
    val iconGray: Color,
    val placeholder: Color,
    val footer: Color,
    val chipIcon: Color,
    val chipBorder: Color,
    val tileBg: Color,
    // ==== 设置页 ====
    val pageBg: Color,
    val cardBg: Color,
    val rowText: Color,
    val secondary: Color,
    val chevron: Color,
    val destructive: Color,
    val switchOn: Color,
    val switchOff: Color,
    val backCircle: Color,
    val toastBg: Color,
    val toastText: Color,
    val fieldBg: Color,
    // ==== 深浅两套差异极大的组件色（官方深色模式实测）====
    /** chip 选中态填充：浅色 #EDF3FE / 深色 #283142 */
    val chipOn: Color,
    /** chip 选中态描边：浅色 #D3E2FF / 深色 #33436B */
    val chipOnBorder: Color,
    /** 输入卡 1dp 边框：官方浅色 #F0F0F0（实测 audit-chat.png 列扫描 x=800） */
    val inputCardBorder: Color,
    /** 输入卡里的 ⊕/麦克风/发送图标色：官方浅色 #0F0F0F、深色 #F8F8F8（实测 audit-chat.png / audit-dark-chat.png） */
    val inputIcon: Color,
    /** 设置页页面底色：官方浅色 #F8F8F8 / 深色 #1E1E1E（A7 官方 2.8.0 实测 a11-off-13-settings-dark.png (160,1200)） */
    val settingsPage: Color,
    /** 设置页分组卡片底色：官方浅色 #FFFFFF / 深色 #292929（同上 (320,640)） */
    val settingsCard: Color,
)

private val LightDs = DsColors(
    bubble = Color(0xFFEDF3FE),
    bubbleText = Color(0xFF0F0F0F),
    codeBg = Color(0xFFF7F7F7),
    codeHeader = Color(0xFFF0F0F0),
    thoughtBg = Color(0xFFFFFFFF),
    thoughtText = Color(0xFF7D7F85),
    chipBg = Color(0xFFFFFFFF),
    chipText = Color(0xFF191B23),
    // 官方浅色分隔线实测 #EDEDED（audit-drawer.png 分组分隔 y=644px）
    divider = Color(0xFFEDEDED),
    subText = Color(0xFF7D7F85),
    userBubble = Color(0xFFEDF3FE),
    bodyText = Color(0xFF191B23),
    iconGray = Color(0xFF8E9094),
    placeholder = Color(0xFFB9BABB),
    // 官方浅色底部免责文案实测 #DDDDDD（audit-chat.png 80..1520,2093..2116）
    footer = Color(0xFFDDDDDD),
    chipIcon = Color(0xFF426EFE),
    chipBorder = Color(0xFFEDEDED),
    tileBg = Color(0xFFF5F5F5),
    pageBg = Color(0xFFF8F8F8),
    cardBg = Color(0xFFFFFFFF),
    rowText = Color(0xFF0F0F0F),
    // 官方浅色分组标题/次级文字实测 #8E9094（audit-settings.png 标题、audit-drawer.png 分组头）
    secondary = Color(0xFF8E9094),
    chevron = Color(0xFFB9BABB),
    destructive = Color(0xFFEF4444),
    switchOn = Color(0xFF22C55E),
    switchOff = Color(0xFFE5E5E5),
    backCircle = Color(0xFFEDEDED),
    toastBg = Color(0xFF333333),
    toastText = Color(0xFFF5F5F5),
    fieldBg = Color(0xFFF5F5F5),
    chipOn = Color(0xFFEDF3FE),
    chipOnBorder = Color(0xFFD3E2FF),
    // 官方浅色输入卡边框 1dp #F0F0F0（实测 audit-chat.png 列扫描 x=800 卡顶/卡底边框行）
    inputCardBorder = Color(0xFFF0F0F0),
    inputIcon = Color(0xFF0F0F0F),
    settingsPage = Color(0xFFF8F8F8),
    settingsCard = Color(0xFFFFFFFF),
)

private val DarkDs = DsColors(
    bubble = Color(0xFF2C2C2E),
    // 官方深色正文/气泡文字实测 #E1E2EC
    bubbleText = Color(0xFFE1E2EC),
    codeBg = Color(0xFF1B1B1C),
    codeHeader = Color(0xFF2C2C2E),
    thoughtBg = Color(0xFF151517),
    // 官方深色次级/已思考行实测 #B9BABB（audit-dark-chat.png 已思考行最亮）
    thoughtText = Color(0xFFB9BABB),
    chipBg = Color(0xFF1B1B1C),
    chipText = Color(0xFFCFD3D6),
    divider = Color(0xFF333333),
    // 官方深色次级文字实测 #B9BABB（audit-dark-chat.png）
    subText = Color(0xFFB9BABB),
    // 官方深色用户消息气泡实测 #333333（浅色模式那个 #EDF3FE 只在浅色下成立）
    userBubble = Color(0xFF333333),
    bodyText = Color(0xFFE1E2EC),
    iconGray = Color(0xFF8E9094),
    // 官方深色输入框占位符实测 #7D7F85（audit-dark-chat.png 输入占位最亮）
    placeholder = Color(0xFF7D7F85),
    // 官方深色底部免责文案实测 #454647（audit-dark-chat.png 2093..2116 最亮）
    footer = Color(0xFF454647),
    // 官方深色 chip 选中图标/文字实测 #6799FE
    chipIcon = Color(0xFF6799FE),
    chipBorder = Color(0xFF333333),
    // 官方深色抽屉搜索胶囊/附件卡底实测 #292929（audit-dark-drawer.png (300,120)）
    tileBg = Color(0xFF292929),
    // 官方深色页面底实测 #0F0F0F
    pageBg = Color(0xFF0F0F0F),
    // 官方深色输入卡底实测 #242424
    cardBg = Color(0xFF242424),
    rowText = Color(0xFFE1E2EC),
    // 官方深色分组标题/次级文字实测 #8E9094（audit-dark-settings.png）
    secondary = Color(0xFF8E9094),
    chevron = Color(0xFF6B6E74),
    destructive = Color(0xFFF25A5A),
    switchOn = Color(0xFF22C55E),
    switchOff = Color(0xFF3A3A3C),
    backCircle = Color(0xFF2C2C2E),
    toastBg = Color(0xFF333333),
    toastText = Color(0xFFF5F5F5),
    fieldBg = Color(0xFF2C2C2E),
    // 官方深色 chip 选中态实测：填充 #283142、描边 #33436B
    chipOn = Color(0xFF283142),
    chipOnBorder = Color(0xFF33436B),
    // 官方深色输入卡边框：沿用深色分隔线 #333333（深色截图实测卡边与 #242424 底可分）
    inputCardBorder = Color(0xFF333333),
    // 官方深色输入卡图标是接近纯白的 #F8F8F8，不是正文那种偏蓝的 #E1E2EC（实测差值用户能看出来）
    inputIcon = Color(0xFFF8F8F8),
    // 官方深色设置页：页面 #1E1E1E、卡片 #292929（A7 官方 2.8.0 实测 a11-off-13-settings-dark.png）
    settingsPage = Color(0xFF1E1E1E),
    settingsCard = Color(0xFF292929),
)

val LocalDsColors = staticCompositionLocalOf { LightDs }

/** 官方字体大小滑块的 5 档（跟随系统开启时用系统 fontScale） */
val DsFontScales = floatArrayOf(0.85f, 0.93f, 1.00f, 1.12f, 1.25f)
val DsFontLevelNames = arrayOf("特小", "小", "标准", "大", "特大")

/**
 * 全局外观状态：设置页「外观 / 字体大小」改动后立刻生效（不改 DsApp 调用点）。
 * mode: system | light | dark；fontLevel: 0..4；followSystemFont: 跟随系统字号
 */
object DsAppearance {
    val mode = mutableStateOf("system")
    val fontLevel = mutableStateOf(2)
    val followSystemFont = mutableStateOf(true)

    fun apply(mode: String, fontLevel: Int, followSystemFont: Boolean) {
        this.mode.value = if (mode == "light" || mode == "dark") mode else "system"
        this.fontLevel.value = fontLevel.coerceIn(0, 4)
        this.followSystemFont.value = followSystemFont
    }
}

private fun dsTypography() = Typography(
    // 官方 defaultBodyTextStyle = 17sp/26sp、字距 0 —— 官方：kt:MarkdownTypography.kt:84
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    // 官方 defaultBodySmallTextStyle = 15sp/24sp（思考正文/说明文字）—— 官方：kt:MarkdownTypography.kt:108
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    // 官方底部免责文案 ≈12sp（audit-chat.png 2093..2116 节点高 11.5dp）—— 实测
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun DsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // 窄屏度量开关：读 LocalConfiguration 让 DsTheme 在配置变化（旋转/分屏）时重组并刷新
    DsLayout.update(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(ctx) {
        if (!loaded) {
            val s = Store.loadSettings(ctx)
            DsAppearance.apply(s.appearance, s.fontSizeLevel, s.followSystemFont)
            loaded = true
        }
    }

    val mode = DsAppearance.mode.value
    val darkOn = when (mode) {
        "dark" -> true
        "light" -> false
        else -> dark
    }
    // 系统栏必须跟着 App 自己的明暗走，否则在「系统浅色 + App 深色」时，
    // 底部导航栏还是系统那层浅色遮罩、状态栏图标也还是深色 —— 就是用户说的界面割裂。
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.navigationBarColor = (if (darkOn) Color(0xFF0F0F0F) else Color(0xFFF8F8F8)).toArgb()
        window.statusBarColor = Color.Transparent.toArgb()
        // 关键：manifests 里 Theme.DS 的 windowBackground 是写死的白色 (#FFFFFFFF)，
        // 深色模式下只要有一瞬间露出窗口底（抽屉开合动画的缝隙、页面切换的空帧），
        // 就会看到一条白边。这里按当前明暗把窗口底色一起换掉。
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                (if (darkOn) Color(0xFF0F0F0F) else Color(0xFFFFFFFF)).toArgb()
            )
        )
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkOn
            isAppearanceLightNavigationBars = !darkOn
        }
    }
    val scheme = if (darkOn) {
        darkColorScheme(
            primary = Color(0xFF6E92FE),
            onPrimary = Color.White,
            background = Color(0xFF0F0F0F),
            onBackground = Color(0xFFE1E2EC),
            surface = Color(0xFF0F0F0F),
            onSurface = Color(0xFFE1E2EC),
            surfaceVariant = Color(0xFF2C2C2E),
            onSurfaceVariant = Color(0xFFCFD3D6),
            outline = Color(0xFF333333),
            error = Color(0xFFF25A5A),
        )
    } else {
        lightColorScheme(
            primary = DsBlue,
            onPrimary = Color.White,
            background = Color.White,
            onBackground = Color(0xFF191B23),
            surface = Color.White,
            onSurface = Color(0xFF191B23),
            surfaceVariant = Color(0xFFF7F7F7),
            onSurfaceVariant = Color(0xFF7D7F85),
            outline = Color(0x1A000000),
            error = Color(0xFFEC1313),
        )
    }

    // 字体大小：跟随系统时用系统 fontScale；否则用 5 档固定比例
    val base = LocalDensity.current
    val follow = DsAppearance.followSystemFont.value
    // 跟随系统时这里必须是 1f：系统缩放已经由 base.fontScale 带上了；
    // 若这里再取 base.fontScale，会和下面的 base.fontScale * fs 相乘，
    // 等于把系统字号平方放大（1.3 → 1.69），字直接爆炸。
    val fs = if (follow) {
        1f
    } else {
        DsFontScales[DsAppearance.fontLevel.value.coerceIn(0, 4)]
    }

    CompositionLocalProvider(
        LocalDsColors provides if (darkOn) DarkDs else LightDs,
        // 去掉 Material 默认的水波纹：官方点按钮没有那层「浅黑」按压遮罩，
        // 我们这里默认 ripple 是 12% 黑色，点一下整块按钮都会暗一下，看着像 bug。
        androidx.compose.material3.LocalRippleConfiguration provides null,
        // 必须乘上系统自身的 fontScale，否则「跟随系统」会被这一层抹平
        LocalDensity provides Density(base.density, base.fontScale * fs),
    ) {
        MaterialTheme(colorScheme = scheme, typography = dsTypography(), content = content)
    }
}
