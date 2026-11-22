package com.yuyuyu.deepseek.ui

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.yuyuyu.deepseek.data.Settings
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 按住说话。
 *
 * 官方语音识别是 `POST /v1/tools/asr`（multipart，file = 16bit PCM WAV），返回 {text}。
 * 这台平板没有 RecognitionService，系统 SpeechRecognizer 根本起不来，所以这里彻底不用它：
 *   · AudioRecord 一边算 RMS 喂浮层（官方 s0c 吃的就是 sqrt(mean)）
 *   · 同一份 PCM 累积成 WAV，松手时整段 POST 给 /v1/tools/asr
 */
class VoiceInputState internal constructor(
    private val ctx: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    var listening by mutableStateOf(false)
        private set
    var level by mutableStateOf(0f)

    /** 32 段频谱能量（0..1），长按录音时的声波竖条用它，跟官方 demo 一样按频率跳 */
    var bands by mutableStateOf(FloatArray(32))
        private set
    var recognizing by mutableStateOf(false)
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var recorder: AudioRecord? = null
    private var meterThread: Thread? = null
    @Volatile private var metering = false
    // 每次启动换代：老线程看到代号变了就退出，否则它会拿着已释放的 recorder
    // 一直 read 出 0，把新线程算出来的电平覆盖掉 —— 表现就是「跳一下又不动了」
    @Volatile private var generation = 0
    private val pcm = ByteArrayOutputStream()

    private var apiKey = ""
    private var baseUrl = "https://api.deepseek.com"
    private val rate = 16000

    /** 本次录音听到过的最大 RMS，用来做「相对峰值」归一（不同设备的麦克风量级差几十倍）。 */
    private var peak = 0f
    private var logTick = 0

    /**
     * 把 RMS 归一成 0..1：比本次峰值低 22dB 以内有效。
     * 这样不管是底噪 0.003 的平板还是 0.03 的手机，说话时都能推到 1，静音时落到 0。
     */
    private fun normalize(rms: Float): Float {
        if (rms > peak) peak = rms else peak *= 0.9995f
        val p = maxOf(peak, 0.0015f)
        val db = 20.0 * Math.log10((rms / p).toDouble().coerceAtLeast(1e-4))
        val v = ((db + 22.0) / 22.0).coerceIn(0.0, 1.0).toFloat()
        if (++logTick % 15 == 0) {
            log("rms=" + String.format("%.4f", rms) + " pk=" + String.format("%.4f", peak) +
                " lv=" + String.format("%.3f", v))
        }
        return v
    }

    fun start(settings: Settings) {
        peak = 0f
        // 上一次残留的实例必须先清掉：metering=true 而 recorder 已释放时，
        // startMeter() 会直接 return，电平就永远是 0（浮层不跳就是这么来的）
        finishMeter()
        apiKey = settings.apiKey
        baseUrl = settings.baseUrl.ifBlank { "https://api.deepseek.com" }
        startMeter()
    }

    private fun log(msg: String) {
        runCatching {
            ctx.openFileOutput("voice_mic.txt", Context.MODE_APPEND).use { f ->
                f.write((msg + "\n").toByteArray())
            }
        }
    }

    fun stop() {
        val audio = finishMeter()
        if (audio.isEmpty()) return
        recognizing = true
        Thread {
            val text = runCatching { recognize(audio) }.getOrElse {
                recognizing = false
                onError(it.message ?: "语音识别失败")
                return@Thread
            }
            recognizing = false
            if (text.isNotBlank()) onFinal(text)
            else onError("没听清，再说一次")
        }.also { it.isDaemon = true }.start()
    }

    fun cancel() {
        finishMeter()
        listening = false
        level = 0f
        bands = FloatArray(32)
    }

    fun release() {
        finishMeter()
    }

    private fun startMeter() {
        val myGen = ++generation
        runCatching { recorder?.release() }
        recorder = null
        synchronized(pcm) { pcm.reset() }
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min, 4096),
            )
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            log("init_fail state=" + (rec?.state ?: -1))
            runCatching { rec?.release() }
            onError("麦克风打不开")
            return
        }
        log("init_ok rate=" + rate)
        recorder = rec
        metering = true
        listening = true
        runCatching { rec.startRecording() }
        meterThread = Thread {
            val buf = ShortArray(1024)
            val bytes = ByteArray(2048)
            while (generation == myGen) {
                val n = runCatching { rec.read(buf, 0, buf.size) }.getOrDefault(-1)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) {
                    val v = buf[i] / 32768.0
                    sum += v * v
                    val s = buf[i].toInt()
                    bytes[i * 2] = (s and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }
                // 电平不能直接套官方 (x-0.04)/0.4：那组常量是按他们那路麦克风的量纲写的。
                // 这台平板实测底噪 RMS≈0.003、说话只有 0.02~0.08，套上去永远贴在 0
                // —— 声波一根都不动就是这么来的。换成「相对峰值 dB 归一」：
                // 任何设备上，只要比本次录音听到过的最大声低 22dB 以内就映射到 0..1。
                val rms = Math.sqrt(sum / n).toFloat()
                level = normalize(rms)
                // 32 段：对最近 1024 个样本做单频点 DFT（Goertzel 式），映射成 8..80 的相对高度
                run {
                    val take = minOf(n, 1024)
                    if (take >= 128) {
                        val start = n - take
                        val out = FloatArray(32)
                        for (b in 0 until 32) {
                            // 频点：200Hz 起步，每段往上走，覆盖到 ~6kHz
                            val freq = 200.0 + b * 180.0
                            val w = 2.0 * Math.PI * freq / rate
                            var re = 0.0
                            var im = 0.0
                            var i = 0
                            while (i < take) {
                                val v = buf[start + i] / 32768.0
                                re += v * Math.cos(w * i)
                                im += v * Math.sin(w * i)
                                i += 4
                            }
                            val mag = Math.sqrt(re * re + im * im) / (take / 4.0)
                            out[b] = (mag * 26.0).toFloat().coerceIn(0f, 1f)
                        }
                        bands = out
                    }
                }
                synchronized(pcm) { pcm.write(bytes, 0, n * 2) }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    /** 停止录音并把累积的 PCM 拿出来（带 WAV 头）。 */
    private fun finishMeter(): ByteArray {
        generation++
        if (!metering) return ByteArray(0)
        metering = false
        listening = false
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        meterThread = null
        level = 0f
        val data = synchronized(pcm) {
            val d = pcm.toByteArray()
            pcm.reset()
            d
        }
        if (data.size < rate / 2) return ByteArray(0) // 少于 0.25 秒当没说话
        return wav(data, rate)
    }

    private fun recognize(wav: ByteArray): String {
        if (apiKey.isBlank()) throw IllegalStateException("先在设置里填 API Key")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "voice.wav",
                wav.toRequestBody("audio/wav".toMediaTypeOrNull()),
            )
            .build()
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/tools/asr")
            .addHeader("Authorization", "Bearer " + apiKey)
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("识别失败 HTTP " + resp.code + " " + text.take(160))
            return JSONObject(text).optString("text").trim()
        }
    }

    private fun wav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream(pcmData.size + 44)
        fun writeStr(s: String) = out.write(s.toByteArray())
        fun writeInt(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun writeShort(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        }
        writeStr("RIFF"); writeInt(pcmData.size + 36); writeStr("WAVE")
        writeStr("fmt "); writeInt(16); writeShort(1); writeShort(1)
        writeInt(sampleRate); writeInt(sampleRate * 2); writeShort(2); writeShort(16)
        writeStr("data"); writeInt(pcmData.size)
        out.write(pcmData)
        return out.toByteArray()
    }
}

@Composable
fun rememberVoiceInput(
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (String) -> Unit,
): VoiceInputState {
    val ctx = LocalContext.current
    val partial by rememberUpdatedState(onPartial)
    val final by rememberUpdatedState(onFinal)
    val error by rememberUpdatedState(onError)
    val state = remember {
        VoiceInputState(ctx.applicationContext, { partial(it) }, { final(it) }, { error(it) })
    }
    DisposableEffect(Unit) { onDispose { state.release() } }
    return state
}
