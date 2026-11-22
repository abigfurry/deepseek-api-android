package com.yuyuyu.deepseek.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.R
import kotlin.math.abs

/**
 * 官方语音浮层：直接跑 APK 里的 `res/raw/voice_mask.agsl`（RuntimeShader）。
 *
 * 可见的蓝色band 完全由 shader 自己裁：`vmArcHeight` 把屏幕纵向映射成 0..1，
 * `arcY <= 0.605` 直接透明，再加 `audioLift = 0.22 * breathLevel` 往上抬 ——
 * 所以「呼吸」就是音量让band 从 39.5% 长到 61.5% 屏高，不是画一个固定高度的框。
 * 之前按 37% 固定高度画是错的，那个 0.605 常量在 smali 的 q47.L 里也出现过。
 */
@Composable
fun VoiceOverlay(
    active: Boolean,
    canceling: Boolean,
    level: () -> Float,
    pressOffset: Offset? = null,
    recognizing: Boolean = false,
    bands: FloatArray = FloatArray(32),
) {
    if (!active) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VoiceOverlayShader(
            canceling = canceling,
            level = level,
            pressOffset = pressOffset,
            recognizing = recognizing,
            bands = bands,
        )
    } else {
        // API < 33（如 Android 11）：没有 RuntimeShader / AGSL，走纯 Canvas 降级，
        // 但声波条、文案、几何位置必须和上面的 shader 分支共用同一份实现。
        VoiceOverlayFallback(
            canceling = canceling,
            level = level,
            recognizing = recognizing,
            bands = bands,
        )
    }
}

/**
 * 官方三段状态（反编译 o47.e，smali_classes3/o47.smali:3407）：
 *   · 录音中   —— 声浪 + 音条，文案 `recording_label`「松手发送，上滑取消」
 *   · 上滑取消 —— 文案 `record_swiped_up_label`「松手取消」，整片转红
 *   · 识别中   —— 文案 `recognizing_label`「识别中」，此时浮层不能消失
 * 另外 `Lea2` 状态还带 `recordRemainingSeconds`（还能说几秒）的倒计时。
 */
private fun voiceLabel(canceling: Boolean, recognizing: Boolean): String = when {
    canceling -> "松开 取消"
    recognizing -> "识别中"
    else -> "松手发送，上滑取消"
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun VoiceOverlayShader(
    canceling: Boolean,
    level: () -> Float,
    pressOffset: Offset?,
    recognizing: Boolean = false,
    bands: FloatArray = FloatArray(32),
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current

    val source = remember {
        runCatching { ctx.resources.openRawResource(R.raw.voice_mask).bufferedReader().use { it.readText() } }
            .getOrDefault("")
    }
    // 原始的 .agsl 里有 GBK 乱码注释，AGSL 编译器对非法 UTF-8 很敏感：
    // 先把注释和所有非 ASCII 字符清掉，再编译。
    val clean = remember(source) {
        source.lineSequence()
            .map { it.substringBefore("//") }
            .filter { it.isNotBlank() }
            .joinToString("\n") { line -> line.filter { ch -> ch.code in 32..126 } }
    }
    val shader = remember(clean) {
        if (clean.isBlank()) null
        else runCatching { RuntimeShader(clean) }
            .onFailure {
                runCatching {
                    ctx.openFileOutput("shader_error.txt", android.content.Context.MODE_PRIVATE)
                        .use { f -> f.write((it.message ?: it.toString()).toByteArray()) }
                }
            }
            .getOrNull()
    }

    // 这个 LaunchedEffect 只跑一次，canceling 必须走 rememberUpdatedState，
    // 否则闭包里永远是首次组合时的 false —— 上滑永远不会变红。
    val cancelNow by rememberUpdatedState(canceling)
    // 同一个坑：LaunchedEffect(Unit) 只跑一次，level 这个 lambda 会把首次组合时的
    // voiceHolding 闭包住。第一次用时它是 true 所以会跳，之后浮层从「识别中」那一屏
    // 复用时闭包里是 false，level() 永远返回 0 —— 表现就是「开始能跳，后来越改越不动」。
    val levelNow by rememberUpdatedState(level)
    val fx = remember { VoiceFx() }
    // 每帧把驱动算出来的值推进 state，drawBehind 里再读。
    var frame by remember { mutableStateOf(FloatArray(6)) }
    // 音条（官方 o47.C 里维护一个浮点数组列表，就是最近若干帧的电平）
    var bars by remember { mutableStateOf(FloatArray(60)) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                // 官方是 x = clamp((sqrt(mean) - 0.04)/0.4, 0, 1) 再 x²(3-2x)。
                // 那个 0.04 / 0.4 的平移缩放绑定的是他们那路麦克风的量纲；我们的 level 已经在
                // 上游归一成「相对峰值 dB」，本来就是 0..1，再减 0.04 除 0.4 会瞬间饱和成 1
                // （浮层按下去就满、没有起伏），所以这里只保留官方的 smoothstep 形状。
                val lin = levelNow().coerceIn(0f, 1f)
                val x = lin
                val shaped = x * x * (3f - 2f * x)
                fx.advance(now, shaped, cancelNow)
                run {
                    val prev = bars
                    val next = FloatArray(prev.size)
                    System.arraycopy(prev, 1, next, 0, prev.size - 1)
                    next[prev.size - 1] = fx.audioLevel
                    bars = next
                }
                // 临时诊断：看驱动到底有没有被声音推起来
                if (now / 100_000_000L % 4L == 0L) {
                    runCatching {
                        ctx.openFileOutput("voice_fx.txt", android.content.Context.MODE_APPEND).use { f ->
                            f.write(
                                ("lin=" + String.format("%.4f", lin) +
                                    " shaped=" + String.format("%.4f", shaped) +
                                    " aL=" + String.format("%.4f", fx.audioLevel) +
                                    " bL=" + String.format("%.4f", fx.breathLevel) +
                                    " eS=" + String.format("%.3f", fx.entranceScale) +
                                    " t=" + String.format("%.2f", fx.time) + "\n").toByteArray()
                            )
                        }
                    }
                }
                frame = floatArrayOf(
                    fx.entranceScale, fx.breathLevel, fx.audioLevel,
                    fx.glow, fx.cancelMix, fx.time,
                )
            }
        }
    }
    val entranceScale = frame[0]
    val breathLevel = frame[1]
    val audioLevel = frame[2]
    val glow = frame[3]
    val cancelMix = frame[4]
    val time = frame[5]

    val pxScale = density.density
    val touchX = pressOffset?.x ?: 0f
    val touchY = pressOffset?.y ?: 0f

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    // 第一帧 frame 数组还是全 0，entranceScale=0 会让 arcY 恒等于 1.0，
                    // 整块矩形按满 alpha 画一帧 —— 这就是「按下去闪一下」。
                    // 等驱动算出真实值再画。
                    if (time <= 0f) return@drawBehind
                    val s = shader
                    if (s == null) {
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFF67A2FF).copy(alpha = 0.45f),
                                    Color(0xFF4C8DF6).copy(alpha = 0.92f),
                                ),
                            )
                        )
                        return@drawBehind
                    }
                    // 手指位置：官方把按下的 Offset 传进 touch uniform，
                    // 决定光晕中心与绕流的涡心。拿不到就按「底部说话条」估算。
                    // 官方 q47.L 的绘制高度：rectH = (1 - 0.395/entranceScale - breath*0.1*(1-0.75*cancel)) * H + 4dp
                    // 我们之前直接把 shader 铺满整屏，所以那圈「蓝带」一直是全屏高、比官方高一整截。
                    val mEntr = 1f / entranceScale.coerceAtLeast(0.01f)
                    // 官方 smali 里是 0.395，但那个 0.395 乘的是整屏高；实测官方蓝带起点在 68% 屏高,
                    // 即 band = 32% H、rectH = 0.32/0.395 = 0.81H，所以这里系数取 0.19（1-0.81）。
                    // 实测：0.19 → 蓝带起点 76%，0.266 → 78%，官方是 71%，
                    // 所以系数继续往小走（rectH 越大带子越高）
                    val tCut = 0.06f * mEntr
                    val extra = breathLevel * 0.1f * (1f - 0.75f * cancelMix)
                    val rectH = ((1f - tCut - extra) * h + 4f * pxScale).coerceIn(0f, h)
                    if (rectH < 1f) return@drawBehind
                    val rectTop = h - rectH
                    val tx = if (pressOffset != null) touchX else w * 0.5f
                    val ty = (if (pressOffset != null) touchY else h - 96.dp.toPx()) - rectTop
                    runCatching {
                        s.setFloatUniform("resolution", w, rectH)
                        s.setFloatUniform("touch", tx, ty)
                        s.setFloatUniform("time", time)
                        s.setFloatUniform("glow", glow)
                        s.setFloatUniform("cancelMix", cancelMix)
                        s.setFloatUniform("intensity", 1.45f)
                        s.setFloatUniform("spacingPx", 13.0f * pxScale)
                        s.setFloatUniform("pixelScale", pxScale)
                        s.setFloatUniform("audioLevel", audioLevel)
                        s.setFloatUniform("breathLevel", breathLevel)
                        s.setFloatUniform("cancelAlphaScale", 0.7f)   // 官方 x0c：0.9 / 0.7，不是 1
                        s.setFloatUniform("baselineOffset", 0f)
                        s.setFloatUniform("entranceScale", entranceScale)
                        s.setFloatUniform("arcRadius", w * 1.25f)
                        s.setFloatUniform("flowOffsetPx", 0f)
                        s.setFloatUniform("darkAppearance", 0f)
                        s.setFloatUniform("vmAudioLift", 0.1f)     // 官方 x0c 就是 0.1f，之前我按 0.22 猜的
                        s.setColorUniform("focusColor", android.graphics.Color.parseColor("#4C8DF6"))
                        s.setColorUniform("cancelColor", android.graphics.Color.parseColor("#FF5757"))
                    }
                    // 官方就是把这个 RuntimeShader 当 Paint.shader 直接画；
                    // Compose 的 ShaderBrush 走的是另一条路径，某些驱动上不出像素，这里用原生 Canvas。
                    val nc = drawContext.canvas.nativeCanvas
                    val save = nc.save()
                    nc.translate(0f, rectTop)
                    nc.drawRect(0f, 0f, w, rectH, android.graphics.Paint().also { it.shader = s })
                    nc.restoreToCount(save)
                }
        )
        // 34 根声波条：抽到 VoiceWaveBars，和 API<33 的 fallback 共用同一份绘制，
        // 几何/颜色只在那一处维护（条宽 1.85dp、间距 3.82dp、34 根，这里只放位置）。
        VoiceWaveBars(
            audioLevel = audioLevel,
            bars = bands,
            time = time,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 113.5.dp)
                .height(12.dp),
        )

        // 文案：同样抽到 VoiceOverlayLabel，两个分支字号/颜色/位置完全一致。
        VoiceOverlayLabel(
            canceling = canceling,
            recognizing = recognizing,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 176.75.dp),
        )
    }
}

/**
 * 官方录音态声波条（真机 1600x2176@2x 实测）：
 *   · 34 根细条，条宽 3~4px（1.85dp），条间距 7~8px（gap 3.82dp）
 *   · 条高 9~23px → 常态 5.5dp，随音量长到 11.5dp（+6dp）
 *   · 整排水平居中，垂直中心在内容区底往上 193px = 96.5dp
 *   · 颜色 = 白色 85% 叠在蓝色遮罩上（实测叠加后 #E1EDFF）
 *
 * 这是 API 33+ 的 RuntimeShader 分支和 API<33 的 fallback 共用的唯一实现。
 * 调用方只负责位置（padding/height/对齐），数值不要各自再猜一套。
 * 注意 modifier 的调用顺序：必须 padding 在前、height 在后。反过来写（height 再 padding）
 * 会让内容区变成 12dp-113dp 的负数，Canvas 实际高度 0 —— 声波一根都不会画出来。
 */
@Composable
private fun VoiceWaveBars(
    audioLevel: Float,
    bars: FloatArray,
    time: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val n = 34
        val bw = 1.85.dp.toPx()
        val gap = 3.82.dp.toPx()
        val total = n * bw + (n - 1) * gap
        val startX = (size.width - total) / 2f
        val cy = size.height / 2f
        val minH = 5.5.dp.toPx()
        val extraH = 6.dp.toPx()
        for (i in 0 until n) {
            // 关键：条形高度必须用「每帧都在变」的量驱动，否则整排永远是同一高度、看着完全不动。
            // 之前用的是组合参数 bands.repeat 捕获的旧数组 → 静止。
            // 这里 = 频谱分量（有就听，没有就是 0）+ 每帧 audioLevel + 沿 x 的相位波，
            // 保证麦克风只要有输入就能看到明显的长短起伏。
            val spec = bars.getOrElse(i % bars.size.coerceAtLeast(1)) { 0f }.coerceIn(0f, 1f)
            // 中点向两侧的包络：官方中间略高、两端略低
            val d = kotlin.math.abs(i - (n - 1) / 2f) / ((n - 1) / 2f)
            val env = 0.55f + 0.45f * (1f - d * d)
            val wob = 0.5f + 0.5f * kotlin.math.sin(time * 2.6f + i * 0.62f)
            // audioLevel 不能只做乘数：麦克风安静时它是 0，整排就永远一样高、看着「不动」。
            // 所以给一个随时间走的基线起伏（0.18），再叠加音量与频谱。
            val amp = 0.22f + 0.78f * (audioLevel * 2.2f).coerceIn(0f, 1f)
            val lv = (amp * (0.30f + 0.70f * wob) * env + spec * 0.45f).coerceIn(0f, 1f)
            val hh = minH + extraH * lv
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * (bw + gap), cy - hh / 2f),
                size = androidx.compose.ui.geometry.Size(bw, hh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2f),
            )
        }
    }
}

/**
 * 官方浮层文案（三段状态见 [voiceLabel]）。
 * 平铺白色、13.2sp、无阴影（实测墨迹宽 233px / 9 字 ≈ 13.2sp）；上滑取消时整片转 #FF5757。
 * shader 分支和 fallback 共用，避免低版本再猜一套字号/位置。
 */
@Composable
private fun VoiceOverlayLabel(
    canceling: Boolean,
    recognizing: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = voiceLabel(canceling, recognizing),
        modifier = modifier,
        fontSize = 13.2.sp,
        lineHeight = 18.9.sp,
        textAlign = TextAlign.Center,
        // 官方文案是平铺白色、没有阴影；之前加 0x66000000 模糊阴影会让字看起来发虚。
        color = if (canceling) Color(0xFFFF5757) else Color.White,
    )
}

/**
 * API < 33（如 Android 11 / API 30）的降级浮层。RuntimeShader / AGSL 是 API 33 才有的，
 * 低版本只能用普通 Canvas 绘制去近似官方观感：
 *   · 蓝色垂直渐变遮罩（代替 shader 的蓝色 band，上滑取消时整片转红）——
 *     官方 band 从屏幕下方 ~30% 起坡，这里用 55%/74%/100% 三段渐变近似；
 *   · 和 shader 分支同一个 [VoiceWaveBars]：34 根声波条，宽度/间距/高度/白色 85% 全部沿用实测值；
 *   · 和 shader 分支同一个 [VoiceOverlayLabel]：13.2sp、bottom 176.75dp，文案随 canceling/recognizing 切换。
 *
 * 之前的 fallback 只有一个渐变 + 文案，一根声波条都没有 —— 这就是低版本「按住说话看不到
 * 任何声波动画」的根因。
 */
@Composable
private fun VoiceOverlayFallback(
    canceling: Boolean,
    level: () -> Float,
    recognizing: Boolean = false,
    bands: FloatArray = FloatArray(32),
) {
    // 和 shader 分支同一个坑：LaunchedEffect(Unit) 只跑一次，level 这个 lambda 会把
    // 首次组合时的 voiceHolding 闭包住（之后 level() 恒为 0，声波就不动了）—— 用 rememberUpdatedState。
    val levelNow by rememberUpdatedState(level)
    // fallback 没有 VoiceFx 驱动，这里自己按帧推进 time / audioLevel，保证声波条有相位起伏。
    var time by remember { mutableFloatStateOf(0f) }
    var audioLevel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startNanos = -1L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (startNanos < 0L) startNanos = now
                time = (now - startNanos) / 1_000_000_000f
                // 和 shader 分支同一套电平整形：上游 level 已经归一成 0..1，只保留 smoothstep。
                val lin = levelNow().coerceIn(0f, 1f)
                val shaped = lin * lin * (3f - 2f * lin)
                // 快起慢落，避免低版本上竖条抖成噪点（shader 分支是 24 / 8 的指数跟随）。
                audioLevel += (if (shaped > audioLevel) 0.5f else 0.2f) * (shaped - audioLevel)
            }
        }
    }
    // 上滑取消：用 shader 分支同一个取消色 #FF5757（不是重新猜的色值）。
    val tint = if (canceling) Color(0xFFFF5757) else Color(0xFF67A2FF)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        0.74f to tint.copy(alpha = 0.50f),
                        1.00f to tint.copy(alpha = 0.93f),
                    )
                )
            },
    ) {
        VoiceWaveBars(
            audioLevel = audioLevel,
            bars = bands,
            time = time,
            // 常态沿用 shader 分支的「白色 85%」；取消态整片转红，条也跟着变红。
            color = (if (canceling) Color(0xFFFF5757) else Color.White).copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 113.5.dp)
                .height(12.dp),
        )
        VoiceOverlayLabel(
            canceling = canceling,
            recognizing = recognizing,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 176.75.dp),
        )
    }
}