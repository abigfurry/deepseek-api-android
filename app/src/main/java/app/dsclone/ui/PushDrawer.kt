package com.yuyuyu.deepseek.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 对应官方 com.deepseek.chat.ui.components.drawer.DSNavigationDrawer：
 *  - 抽屉宽度 = min(326dp, 0.8 × 屏宽)（官方 NavigationDrawerDefaults.minimumContentWidth = 326f）
 *  - 内容随进度右移 drawerWidth、缩放到 1 - 0.0504p、圆角 36dp × p
 *  - 支持手指拖动跟手，松手按位置/速度吸附
 *  - 抽屉侧有 .drawerMask 遮罩层：只拦点击，不压暗（官方 open 时 opacity 归 0）
 */
@Composable
fun PushDrawer(
    open: Boolean,
    onClose: () -> Unit,
    drawerWidth: Dp,
    drawer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // 官方：横屏时抽屉不能拖动，只能点左上角按钮
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val widthPx = with(density) { drawerWidth.toPx() }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(open) {
        progress.animateTo(
            targetValue = if (open) 1f else 0f,
            // 官方 DSModalNavigationDrawer：spring(dampingRatio = 0.9f, stiffness = 1500f)
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 1500f),
        )
    }

    var dragging by remember { mutableStateOf(false) }
    val p = progress.value

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 抽屉本体：跟着内容一起从左边划出来（官方是整体平移，不是被内容"揭开"）
        Box(
            Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = widthPx * (p - 1f)
                }
        ) {
            drawer()
        }

        // 内容层：跟手位移 + 缩放 + 圆角
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 官方：侧边栏打开时对话内容整体右移，不缩放、不变圆角（实测左边缘是直边）
                    translationX = widthPx * p
                }
                .then(if (isLandscape) Modifier else Modifier.draggable(
                    orientation = Orientation.Horizontal,
                    startDragImmediately = false,
                    onDragStarted = { dragging = true },
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            progress.snapTo((progress.value + delta / widthPx).coerceIn(0f, 1f))
                        }
                    },
                    onDragStopped = { velocity ->
                        dragging = false
                        val settleOpen = progress.value > 0.5f || velocity > 900f
                        scope.launch {
                            if (settleOpen) {
                                progress.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 1500f))
                            } else {
                                progress.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 1500f))
                                onClose()
                            }
                        }
                    },
                ))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // 官方：内容层左边缘投一条 #EFEFEF→#FDFDFD 的阴影到抽屉上，但边缘本身是直的
                    .shadow(elevation = 24.dp * p, shape = RectangleShape, clip = false)
                    // 官方：缓慢拖动抽屉时，对话层会随位移逐渐模糊
                    .blur(radius = 3.dp * p)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content()
            }
            // .drawerMask：只在打开态拦截点击，视觉透明
            if (p > 0.001f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onClose() }
                )
            }
        }
    }
}
