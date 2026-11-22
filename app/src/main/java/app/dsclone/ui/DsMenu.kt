package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

data class DsMenuItem(
    val label: String,
    val icon: Int? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * DeepSeek 风格的弹出菜单：圆角面板 + 阴影，条目 46dp 高、15sp。
 * 不是 Material 默认 DropdownMenu 的外观。
 */
@Composable
fun DsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<DsMenuItem>,
    offset: IntOffset = IntOffset.Zero,
) {
    if (!expanded) return
    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        DsMenuSurface(items = items, onDismiss = onDismiss)
    }
}

/**
 * 挂在某个图标下面展开的菜单（侧边栏「⋯」、对话行长按都用它）。
 * 官方不是 Material DropdownMenu，所以自己用 Popup 定位。
 * offsetY：给靠近屏幕底部的行做「向上翻」微调（官方菜单会自动避开屏幕边缘）。
 */
@Composable
fun DsAnchorMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<DsMenuItem>,
    offsetY: Int = 0,
) {
    if (!expanded) return
    val gap = with(LocalDensity.current) { 22.dp.roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, gap + offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        DsMenuSurface(items = items, onDismiss = onDismiss, alignEnd = true)
    }
}

@Composable
private fun DsMenuSurface(
    items: List<DsMenuItem>,
    onDismiss: () -> Unit,
    alignEnd: Boolean = false,
) {
    val ds = LocalDsColors.current
    // 官方深色弹层实测 #3C3C3D（audit 真机：对话行菜单 / 消息长按菜单面板同一色），
    // 比页面 #0F0F0F 亮一档；浅色弹层纯白。之前用 ds.cardBg(#242424) 偏暗，
    // 和官方截图对比能明显看出来「面板发灰、不像浮在上面」。
    val dark = ds.pageBg.luminance() < 0.5f
    val panel = if (dark) androidx.compose.ui.graphics.Color(0xFF3C3C3D) else androidx.compose.ui.graphics.Color.White
    // 官方深色菜单文字/图标是 #F8F8F8（不是正文那种偏蓝的 #E1E2EC），浅色 #0F0F0F。
    val fg = if (dark) androidx.compose.ui.graphics.Color(0xFFF8F8F8) else androidx.compose.ui.graphics.Color(0xFF0F0F0F)
    // 官方菜单是「从锚点那一侧弹出 + 轻微放大淡入」，不是直接出现
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.90f,
        // 官方弹层 spring(dampingRatio = 0.9f, stiffness = 800f)
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 800f),
        label = "menuScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 800f),
        label = "menuAlpha",
    )
    Box(
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
            transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)
        }
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            // 官方菜单没有描边，靠投影和面板色和页面区分；深色下画描边反而会变成一圈亮边。
            color = panel,
            shadowElevation = 12.dp,
        ) {
            Column(
                Modifier
                    .widthIn(min = 168.dp)
                    // 官方：对话行 ⋯ 菜单面板宽 195dp（真机 x=202…591px @2x）
                    .width(if (alignEnd) 195.dp else 168.dp),
            ) {
                items.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // 官方：菜单项行高 46dp（4 项面板高 184dp，无上下留白）
                            .height(46.dp)
                            .clickable {
                                onDismiss()
                                item.onClick()
                            }
                            // 官方：图标墨迹 16dp、距面板左 16.5、文字 x=45.5
                            .padding(horizontal = 16.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = null,
                                tint = if (item.danger) ds.destructive else fg,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(13.dp))
                        }
                        Text(
                            text = item.label,
                            // 官方：菜单文字 15sp
                            fontSize = 15.sp,
                            lineHeight = 22.5.sp,
                            color = if (item.danger) ds.destructive else fg,
                        )
                    }
                }
            }
        }
    }
}
