package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 官方全屏看图（smali: com.deepseek.chat.ui.components.image.FullScreenImageOverlay /
 * FullScreenImage / PlaceholderTransformImage）：
 *  - 整屏黑底，图片按屏幕铺满（不是塞在一个小窄弹窗里，所以不会有黑边）
 *  - 双指缩放 + 平移，双击在 1x / 2.5x 之间切换
 *  - 底部「n/N」张数指示，左上角关闭
 */
@Composable
fun FullScreenImageViewer(
    paths: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    if (paths.isEmpty()) return
    val index by remember { mutableStateOf(startIndex.coerceIn(0, paths.lastIndex)) }
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val uri = remember(index) { android.net.Uri.parse(paths[index]) }
            val bmp = rememberAttachmentBitmap(uri, maxSide = 2400)

            val state = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 6f)
                offset = if (scale > 1f) {
                    // 缩放后最多把图片边缘拖到屏幕边缘，别让它拖飞
                    val maxX = wPx * (scale - 1f) / 2f
                    val maxY = hPx * (scale - 1f) / 2f
                    Offset(
                        (offset.x + panChange.x).coerceIn(-maxX, maxX),
                        (offset.y + panChange.y).coerceIn(-maxY, maxY),
                    )
                } else {
                    Offset.Zero
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .transformable(state)
                    .pointerInput(index) {
                        detectTapGestures(
                            onTap = { onDismiss() },
                            onDoubleTap = {
                                if (scale > 1.01f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y,
                            ),
                    )
                }
            }

            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x33000000))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(com.yuyuyu.deepseek.R.drawable.ic_close_outline_20),
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (paths.size > 1) {
                Text(
                    text = "${index + 1}/${paths.size}",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}
