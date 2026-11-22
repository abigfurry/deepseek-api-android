package com.yuyuyu.deepseek.ui

import android.net.Uri
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuyuyu.deepseek.R
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 官方「拍照提问」相机复刻：CameraX 预览 + 自定义快门 + 变焦 + 点按对焦。
 *
 * 参数来源（反编译 apk_analysis/decoded_full）：
 * - 点按对焦框：smali:cxa（TapToFocusOverlay.kt:41）
 *   圆环直径 36dp（`const/high16 #36.0f` -> `LocalDensity.L0` 换 px）、描边 1.5dp
 *   （`Lq17;->p(Modifier, 1.5f, color, shape)`），颜色 `Lcxa;->a` = 0xFFFFC67B、
 *   `Lcxa;->b` = 0x80FFC67B（半透明态），动画用 `Lwca;->n(72.0f)` 的触控/缩放盒。
 * - 变焦：smali:b08（CameraZoomControl.kt:81 / ZoomLevelSelector.kt:218 / ZoomChip.kt:297）
 *   档位选择器 + 双指捏合（`CameraZoomControl.kt:348` rememberPinchZoomDetector），
 *   chip 高 32dp（`#32.0f`）、水平内边距 12dp、圆角 16dp（0.5*32）、文字 12sp/FontWeight 500。
 * - 变焦标尺：smali:v37（ZoomRuler.kt:167）padding 24dp，画笔 `Loac;->a` 透明度。
 */
@Composable
fun CameraCapture(
    onClose: () -> Unit,
    onCaptured: (Uri) -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // 官方相机：右上角闪光灯开关，右下角前后摄切换
    var front by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var rebind by remember { mutableStateOf(0) }

    // ---- 变焦状态（官方 CameraZoomControl / ZoomRuler） ----
    var zoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }

    // ---- 点按对焦状态（官方 TapToFocusOverlay） ----
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusProgress = remember { Animatable(1f) }

    LaunchedEffect(rebind) {
        try {
            val provider = ProcessCameraProvider.getInstance(ctx).get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            provider.unbindAll()
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            hasFlash = cam.cameraInfo.hasFlashUnit()
            cam.cameraControl.enableTorch(flashOn && hasFlash && !front)
            imageCapture = capture
            capture.flashMode = if (flashOn && hasFlash && !front) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            // 官方相机每次切镜头/重绑都回到 1x
            zoom = 1f
            maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio?.coerceAtLeast(1f) ?: 1f
            runCatching { cam.cameraControl.setZoomRatio(1f) }
            camera = cam
        } catch (e: Exception) {
            error = e.message ?: "相机不可用"
        }
    }

    val zoomLevels = remember(maxZoom) {
        val candidates = listOf(1f, 2f, 5f, 10f)
        candidates.filter { it <= maxZoom + 0.01f }.ifEmpty { listOf(1f) }
    }
    val zoomHintAlpha by animateFloatAsState(
        targetValue = if (abs(zoom - 1f) > 0.02f) 1f else 0f,
        animationSpec = tween(160),
        label = "zoomHint",
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // 点按对焦 + 双指变焦 + 对焦框动画（覆盖在预览上，位于所有控件之下）
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { p ->
                        val c = camera ?: return@detectTapGestures
                        focusPoint = p
                        // 官方：TapToFocusOverlay 内 GestureDetector -> startFocusAndMetering
                        runCatching {
                            val point = previewView.meteringPointFactory.createPoint(p.x, p.y)
                            c.cameraControl.startFocusAndMetering(
                                FocusMeteringAction.Builder(
                                    point,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                ).build()
                            )
                        }
                        scope.launch {
                            focusProgress.snapTo(0f)
                            focusProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(1600, easing = LinearEasing)
                            )
                            focusPoint = null
                        }
                    }
                }
                .pointerInput(camera, maxZoom) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        val c = camera ?: return@detectTransformGestures
                        if (maxZoom <= 1.01f) return@detectTransformGestures
                        val next = (zoom * gestureZoom).coerceIn(1f, maxZoom)
                        if (abs(next - zoom) > 0.001f) {
                            zoom = next
                            runCatching { c.cameraControl.setZoomRatio(next) }
                        }
                    }
                }
        ) {
            val p = focusPoint ?: return@Canvas
            val progress = focusProgress.value.coerceIn(0f, 1f)
            // 36dp 圆环，出现瞬间放大 ~1.4 倍然后回到 1 倍并淡出（官方 TapToFocusOverlay 动画）
            val appear = (progress / 0.15f).coerceIn(0f, 1f)
            val scale = 1f + 0.4f * (1f - appear)
            val alpha = if (progress < 0.6f) 1f else ((1f - progress) / 0.4f).coerceIn(0f, 1f)
            val radius = 18.dp.toPx() * scale
            val color = Color(0xFFFFC67B).copy(alpha = 0.85f * alpha)
            drawCircle(
                color = color,
                radius = radius,
                center = p,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawCircle(
                color = color.copy(alpha = 0.9f * alpha),
                radius = 2.dp.toPx(),
                center = p
            )
        }

        Row(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (hasFlash && !front) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (flashOn) Color.White else Color.Black.copy(alpha = 0.35f))
                        .clickable { flashOn = !flashOn; rebind++ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_flash_outline_20),
                        contentDescription = if (flashOn) "关闭闪光灯" else "打开闪光灯",
                        tint = if (flashOn) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            error?.let {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(it, color = Color.White)
                }
            }
        }

        // ---- 变焦倍数提示（官方 zoomDialAlpha：变焦时显示当前倍数） ----
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 232.dp)
                .alpha(zoomHintAlpha)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatZoom(zoom),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // ---- 变焦标尺（官方 ZoomRuler.kt:167）：底部当前变焦位置 ----
        if (maxZoom > 1.01f) {
            ZoomRuler(
                zoom = zoom,
                maxZoom = maxZoom,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 196.dp)
                    .width(240.dp)
                    .height(24.dp)
            )
        }

        // ---- 变焦档位 chip（官方 ZoomLevelSelector / ZoomChip：高 32dp、圆角 16dp） ----
        if (maxZoom > 1.01f) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 132.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                zoomLevels.forEach { level ->
                    val selected = abs(zoom - level) <= 0.05f
                    Box(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) Color.White else Color.Black.copy(alpha = 0.35f)
                            )
                            .clickable {
                                zoom = level
                                camera?.let { c ->
                                    runCatching { c.cameraControl.setZoomRatio(level) }
                                }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatZoomLevel(level),
                            color = if (selected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // 前后摄切换（右下角，官方在这个位置）
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 32.dp, bottom = 52.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { front = !front; flashOn = false; rebind++ },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_camera_flip_outline_24),
                contentDescription = "切换前后摄像头",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        // 快门
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(72.dp)
                .border(3.dp, Color.White, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable {
                    val capture = imageCapture ?: return@clickable
                    val file = File(ctx.cacheDir, "ds_cam_${System.currentTimeMillis()}.jpg")
                    val options = ImageCapture.OutputFileOptions.Builder(file).build()
                    capture.takePicture(
                        options,
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onCaptured(Uri.fromFile(file))
                            }

                            override fun onError(exception: ImageCaptureException) {
                                error = exception.message
                            }
                        },
                    )
                }
        )
    }
}

/** 官方 ZoomChip 文案：1x / 2x / 5x（整数档位不带小数点） */
private fun formatZoomLevel(level: Float): String =
    if (abs(level - level.roundToInt()) < 0.01f) "${level.roundToInt()}x" else String.format("%.1fx", level)

/** 当前变焦倍数提示：1.0x -> "1.0x" */
private fun formatZoom(zoom: Float): String = String.format("%.1fx", zoom)

/**
 * 官方 ZoomRuler（ZoomRuler.kt:167）：24dp padding 内的水平刻度尺 + 当前变焦滑点。
 * 刻度范围 1x..maxZoom，官方尺子为细线 + 圆点（透明度 smali:v37 中取 Loac;->a）。
 */
@Composable
private fun ZoomRuler(
    zoom: Float,
    maxZoom: Float,
    modifier: Modifier = Modifier,
) {
    val frac = ((zoom - 1f) / (maxZoom - 1f).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    Canvas(modifier) {
        val y = size.height / 2f
        val left = 0f
        val right = size.width
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val ticks = 5
        for (i in 0 until ticks) {
            val x = left + (right - left) * i / (ticks - 1)
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(x, y - 4.dp.toPx()),
                end = Offset(x, y + 4.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = Offset(left + (right - left) * frac, y),
        )
    }
}
