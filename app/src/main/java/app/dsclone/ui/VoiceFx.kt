package com.yuyuyu.deepseek.ui

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 官方语音浮层的动画驱动。
 *
 * 这一份是把 APK 里的状态机原样搬过来的，不是自己编的曲线：
 *   smali/s0c.smali  状态机（update(a) + b()），字段 m/n/i/g/h/e/f/j/k/l
 *   smali/r0c.smali  单个弹簧通道（目标变了就重置积分器，dt 用纳秒累加）
 *   smali/cn4.smali  spring(dampingRatio, stiffness) 的解析解
 *   y3a.smali        三个 spring 常量：0.2/1000、1.0/130、0.8/300
 *   x0c.smali        每个 uniform 到底取哪个字段
 *
 * 关键结论（和之前自己猜的完全不同）：
 *   · entranceScale = 1 / max(m, 0.01)，m 是 ζ=0.2 / k=1000 的过冲弹簧，先去 1.0，
 *     冲过 1 之后锁成 ζ=1 / k=130 —— 所以入场是「顶过头再弹回来」，不是线性 260ms。
 *   · breathLevel  = n，ζ=0.8 / k=300 的弹簧，直接跟随音量，没有 3200ms 周期呼吸。
 *   · audioLevel   = i，上升系数 1-e^(-24dt)、下降 1-e^(-8dt)，即「快起慢落」。
 *   · glow         = g，手指按住时以 1-e^(-18dt) 爬到 1，松开以 6 掉下去。
 *   · cancelMix    = h，以 1-e^(-10dt) 靠近 0/1。
 *   · 电平曲线：x = clamp((sqrt(mean) - 0.04) / 0.4, 0, 1)，再 x²(3-2x)。
 */
internal class VoiceFx {

    /** s0c.m —— 入场弹簧，初值 0.8（对应 Smali `new r0c(0.8f)`）。 */
    private val entrance = Spring(0.8f)

    /** s0c.n —— 声浪弹簧，初值 0。 */
    private val breath = Spring(0f)

    /** s0c.i —— audioLevel。 */
    var audioLevel = 0f
        private set

    /** s0c.g —— glow。 */
    var glow = 0f
        private set

    /** s0c.h —— cancelMix。 */
    var cancelMix = 0f
        private set

    /** s0c.o —— 过冲后锁死，换回不弹的弹簧。 */
    private var locked = false

    /** shader 的 time（秒）。 */
    var time = 0f
        private set

    private var started = false
    private var lastNanos = 0L

    /** 官方 b()：entranceScale = 1 / max(m, 0.01)。 */
    val entranceScale: Float
        get() = 1f / maxOf(entrance.value, 0.01f)

    /** breathLevel = n。 */
    val breathLevel: Float
        get() = breath.value

    fun advance(nowNanos: Long, level: Float, canceling: Boolean) {
        if (!started) {
            started = true
            lastNanos = nowNanos
        }
        // 官方把 dt 夹在 0..0.1 秒（z57.u(f, 0, 0.1f)）。
        var dt = (nowNanos - lastNanos) / 1_000_000_000f
        lastNanos = nowNanos
        if (dt <= 0f) dt = 1f / 60f
        dt = dt.coerceIn(0f, 0.1f)

        val v = level.coerceIn(0f, 1f)

        // audioLevel：涨得快、落得慢
        audioLevel += smooth(dt, if (v > audioLevel) 24f else 8f) * (v - audioLevel)

        // 入场弹簧始终奔向 1.0
        if (locked) {
            entrance.step(dt, 1f, 1.0f, 130f)
        } else {
            entrance.step(dt, 1f, 0.2f, 1000f)
            if (entrance.value > 1f && entrance.velocity <= 0f) locked = true
        }

        // 声浪：跟随音量
        breath.step(dt, v, 0.8f, 300f)

        // glow：按住 → 1（18），松开 → 0（6）
        glow += smooth(dt, 18f) * (1f - glow)

        // cancelMix：上滑取消
        cancelMix += smooth(dt, 10f) * ((if (canceling) 1f else 0f) - cancelMix)

        time += dt
    }

    /** 官方 q47.k(dt, rate) = 1 - e^(-dt·rate)。 */
    private fun smooth(dt: Float, rate: Float): Float = 1f - exp(-dt * rate)

    /**
     * r0c：一个弹簧通道。目标值变化时把当前位置/速度当作新的起始条件，
     * 时间归零后重新用解析解求解。
     */
    private class Spring(initial: Float) {
        var value = initial
            private set
        var velocity = 0f
            private set

        private var fromValue = initial
        private var fromVelocity = 0f
        private var target = initial
        private var damping = Float.NaN
        private var elapsed = 0f

        fun step(dt: Float, target: Float, damping: Float, stiffness: Float) {
            if (target != this.target || damping != this.damping) {
                fromValue = value
                fromVelocity = velocity
                this.target = target
                this.damping = damping
                elapsed = 0f
            }
            elapsed += dt
            val t = elapsed
            val w = sqrt(stiffness)
            val z = damping
            val x0 = fromValue - target
            val v0 = fromVelocity
            if (z < 1f) {
                val wd = w * sqrt(1f - z * z)
                val e = exp(-z * w * t)
                val a = x0
                val b = (v0 + z * w * x0) / wd
                value = target + e * (a * cos(wd * t) + b * sin(wd * t))
                velocity = e * (
                    (b * wd - z * w * a) * cos(wd * t) -
                        (a * wd + z * w * b) * sin(wd * t)
                    )
            } else {
                val e = exp(-w * t)
                val b = v0 + w * x0
                value = target + (x0 + b * t) * e
                velocity = (b - w * (x0 + b * t)) * e
            }
        }
    }
}
