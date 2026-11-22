package com.yuyuyu.deepseek.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos

/**
 * 逐帧驱动的小动画工具（不依赖 Compose 的动画时钟）。
 *
 * 为什么不用 Animatable / animateFloatAsState / AnimatedContent：
 * 这些 API 的时长都过一遍 `MotionDurationScale`（跟随系统「开发者选项 → 动画时长缩放」，
 * 部分 ROM 的「移除动画」也会把它设成 0），缩放为 0 时时长被缩成 0，过渡就变成一帧跳变，
 * 看起来「完全没动画」。
 *
 * 这里改成自己用 `withFrameNanos` 累计时间：帧回调只负责告诉我们「下一帧什么时候来」，
 * 进度完全由我们按 durationMs 算，系统动画缩放再小/关闭也照常逐帧播完。
 * 写法与工程里已验证过的 VoiceOverlay / VoiceFx（LaunchedEffect + withFrameNanos）一致。
 *
 * @param durationMs 动画时长（毫秒；<= 0 视为立即完成）
 * @param trigger    触发器：值变化时进度重置并重新从 0 跑到 1（LaunchedEffect 天然可取消）
 * @param startAtEnd 首次组合直接从 1 开始（不播入场）；之后 trigger 变化仍从 0 开始播
 * @param onFinished 进度跑到 1 时回调（每轮一次；被 startAtEnd 跳过的那次不回调）
 * @return 0..1 的线性进度，需要缓动时用 [frameEase] 包一层
 */
@Composable
fun rememberFrameAnim(
    durationMs: Int,
    trigger: Any?,
    startAtEnd: Boolean = false,
    onFinished: (() -> Unit)? = null,
): State<Float> {
    // 只用来判断「是不是首次组合」；不是状态，避免额外重组。
    val firstRun = remember { booleanArrayOf(true) }
    // remember(trigger)：trigger 变化时同步换成一个从 0 开始的新状态。
    // 放在组合期重置（而不是在 LaunchedEffect 里）是为了避免掉一帧「上一轮残留的 1」。
    val progress = remember(trigger) {
        mutableStateOf(if (firstRun[0] && startAtEnd) 1f else 0f)
    }
    val finished = rememberUpdatedState(onFinished)

    LaunchedEffect(trigger, durationMs) {
        firstRun[0] = false
        // 已经跑满（首次组合且 startAtEnd）就不播，也不回调。
        if (progress.value >= 1f) return@LaunchedEffect

        val totalNanos = durationMs.coerceAtLeast(0).toLong() * 1_000_000L
        if (totalNanos <= 0L) {
            progress.value = 1f
            finished.value?.invoke()
            return@LaunchedEffect
        }

        var startNanos = 0L
        // 帧回调返回 false 表示这一轮结束；LaunchedEffect 被取消时循环自然消失。
        while (true) {
            val keepGoing = withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                val elapsed = now - startNanos
                if (elapsed >= totalNanos) {
                    progress.value = 1f
                    false
                } else {
                    progress.value = (elapsed.toFloat() / totalNanos.toFloat()).coerceIn(0f, 1f)
                    true
                }
            }
            if (!keepGoing) break
        }
        finished.value?.invoke()
    }
    return progress
}

/**
 * FastOutSlowInEasing 的手算版：把 [rememberFrameAnim] 的 0..1 线性进度映射成
 * Material 推荐曲线（与原来 tween(easing = FastOutSlowInEasing) 的观感一致）。
 * 注意这里只是纯数学曲线，取的是函数本身，和 Compose 的动画时钟无关。
 */
fun frameEase(t: Float): Float = FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))
