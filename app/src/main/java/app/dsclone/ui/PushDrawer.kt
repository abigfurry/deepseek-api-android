package com.yuyuyu.deepseek.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 官方窄屏（手机 / compact，A7 640×876dp 单栏）modal 抽屉 —— 按官方 2.9.0 反编译实现：
 *
 *  官方代码出处：
 *   · 组件：DSModalNavigationDrawer（ModalNavigationDrawer.kt:284，smali Lmm7;->b）
 *     + ContentScrim（ModalNavigationDrawer.kt:463，smali Lmm7;->a）
 *   · 动画 spec：`spring(dampingRatio = 0.9f)`（`smali/mm7.smali:23` const 0x3f666666=0.9f，
 *     走 `Ll06;->W(FFLjava/lang/Object;I)` = Compose spring，stiffness 用默认 1500f）
 *   · 进度函数：`smali/mm7.smali:4931` `c(FF)F` = clamp((offset + width)/width, 0, 1)
 *     —— 即抽屉面板按 **translationX** 平移，不是宽度动画。
 *
 *  同机实测（官方 2.8.0 on A7，_work/shots/a11-off-01-chat.png 关闭 vs a11-off-02-drawer.png 打开）：
 *   · 打开态内容整体**右移 265dp**（平移，不是按 375dp 重排）：
 *     关闭态 AI 正文墨迹 x=65px、打开态 x=65+530=595px；标题、欢迎语（居中元素）同样 +530px。
 *     把关闭态整幅右移 530px 再叠 scrim 0.70 与打开态逐像素回归，mean|diff| = 0.93 灰阶 → 平移成立。
 *   · scrim 只盖内容栏（抽屉右侧），不盖抽屉：
 *     浅色 = 白 0.70（正文最暗 25→186、输入卡描边 185→234 两点解出 S=255,a=0.70）；
 *     深色 = #292929 0.53（深色欢迎页底 15→29、亮字 248→138 两点解出 S≈41,a≈0.53）。
 *   · 抽屉右缘有内容层投下的阴影（x=471..529px，255→239），≈18dp elevation；无模糊。
 *   · 官方 2.8.0/2.9.0 在 640dp 窗口下抽屉宽 265dp（uiautomator 关闭层 [530,0][1280,1752]）。
 *
 * 平板（≥768dp）走 DsApp.kt 的 wide 两栏分支，不使用本文件。
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
    val dark = isSystemInDarkTheme()
    // 官方：横屏（手机被强制竖屏，这里只是兜底）时抽屉不能拖动，只能点左上角按钮
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val widthPx = with(density) { drawerWidth.toPx() }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(open) {
        progress.animateTo(
            targetValue = if (open) 1f else 0f,
            // 官方 DSModalNavigationDrawer：spring(dampingRatio = 0.9f)（smali mm7 <clinit> 0.9f，默认 1500f）
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 1500f),
        )
    }

    var dragging by remember { mutableStateOf(false) }
    val p = progress.value
    // 官方 scrim：浅色 白 0.70、深色 #292929 0.53（同机像素反解，见文件头）
    val scrimColor = if (dark) Color(0xFF292929) else MaterialTheme.colorScheme.background
    val scrimAlpha = if (dark) 0.53f else 0.70f
    // 官方「部分设备才有圆角」的条件（2.9.0 反编译）：
    //   · rememberWindowCornerRadii（WindowCornerRadii.kt:51，smali Lke;->r(Ld45;)La5c;）：
    //       Build.VERSION.SDK_INT >= 31（0x1f）且 view.rootWindowInsets != null 时，
    //       读 WindowInsets.getRoundedCorner(0=TOP_LEFT / 1=TOP_RIGHT / 3=BOTTOM_RIGHT / 2=BOTTOM_LEFT).radius（px）；
    //       否则四个半径全为 0。→ 圆角来自【物理屏幕圆角】，不是宽度类/手势导航/机型分支。
    //   · 内容层 shape（ModalNavigationDrawer.kt 的 ContentScrim，smali Lmm7;->a 内 mm7.smali:793）：
    //       RoundedCornerShape(topStart = min(36.dp, 左上圆角.toDp()), topEnd = 0, bottomEnd = 0, bottomStart = 同左)
    //       —— 只圆 start（左）两角，半径上限 36dp；半径为 0 时就是矩形。
    //   · A7 是 API 30（<31）→ getRoundedCorner 不可用 → 半径 0 → 官方在这台机器上没有圆角。
    val windowCorner = rememberWindowCornerRadiusDp()
    val contentShape: Shape = if (windowCorner > 0.dp) {
        RoundedCornerShape(topStart = windowCorner, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = windowCorner)
    } else {
        RectangleShape
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 抽屉面板：整块从 -drawerWidth 平移到 0（官方 translationX，不是宽度动画）
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

        // 内容层：整体右移 drawerWidth × 进度（官方实测：打开态内容平移 265dp，不重排）
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
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
                    // 官方：内容层左缘往抽屉上投阴影（实测 255→239，宽约 29.5dp，无模糊）
                    .shadow(elevation = 18.dp * p, shape = contentShape, clip = false)
                    .background(MaterialTheme.colorScheme.background, contentShape)
                    // 官方：有物理圆角的设备（API≥31）内容层按 RoundedCornerShape 裁切，左两角圆角
                    .clip(contentShape)
            ) {
                content()
            }
            // 官方 ContentScrim：只盖内容层（不盖抽屉），颜色/alpha 见上，随进度淡入；点一下关闭。
            // 关闭态（open=false）不挂 clickable —— 否则透明 scrim 会挡住顶栏汉堡/正文里的所有点击。
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha * p }
                    .background(scrimColor)
                    .then(
                        if (open) Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onClose() } else Modifier
                    )
            )
        }
    }
}

/**
 * 官方 `rememberWindowCornerRadii`（WindowCornerRadii.kt:51；smali `Lke;->r(Ld45;)La5c;` + `La5c;` 四 int 字段
 * = (topLeft, topRight, bottomRight, bottomLeft) 的**物理屏幕圆角半径，单位 px**）的等价实现：
 *
 *  - `Build.VERSION.SDK_INT >= 31` 且 `rootWindowInsets != null` 才能取到，
 *    否则（如本机 A7 = API 30）四角半径全部为 0；
 *  - 官方在抽屉内容层把半径按 `min(36.dp, 左上圆角.toDp())` 用作 start 两角的圆角半径。
 *
 * 所以「官方侧边栏部分机器才有圆角」= 取决于设备**屏幕是否有圆角**且系统能上报（API 31+），
 * 与 compact/medium/expanded 宽度类、edge-to-edge、手势导航、RTL 都无关。
 */
@Composable
private fun rememberWindowCornerRadiusDp(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        if (Build.VERSION.SDK_INT < 31) {
            0.dp
        } else {
            val px = view.rootWindowInsets
                ?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                ?.radius ?: 0
            with(density) { minOf(36.dp, px.toDp()) }
        }
    }
}
