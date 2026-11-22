package com.yuyuyu.deepseek.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yuyuyu.deepseek.data.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 官方语音合成：`POST {baseUrl}/v1/tools/tts`
 *
 * 实测（api.deepseek.com）：
 *  - 请求体 `{text, voice_id, format:"wav", filter_markdown}`，成功返回 `audio/wav`（RIFF/WAVE，24kHz/16bit/单声道）
 *  - voice_id 只有两个可用值：`stella`（默认，女声，官方 App 的音色）、`tide`（男声）；
 *    alloy/nova/onyx 等 OpenAI 音色一律 400 `Invalid voice_id`
 *  - 没有 `/v1/audio/speech`（404），所以朗读只走这条接口
 *
 * 播放策略：长文本按句子切段（单段约 240 字），边合成边播放（提前预取下一段），
 * 所以长回答也能较快出声；失败（无 Key / 网络 / 非 200 / 返回不是 WAV）时自动降级到
 * 系统 TextToSpeech（zh-CN），保证一定有声音。
 *
 * `speaking` / `progressText` 是 Compose State，UI 读它就能把「朗读」变成「停止」。
 */
object DsTts {
    /** 官方音色：stella=女声（默认，和官方 App 一致），tide=男声。 */
    var voiceId: String = "stella"

    private const val SEG_MAX = 240
    private const val SEG_CAP = 80
    private const val DEF_VOICE = "stella"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val main = Handler(Looper.getMainLooper())
    private val token = AtomicInteger(0)
    private val waiters = Collections.synchronizedSet(HashSet<CountDownLatch>())

    @Volatile
    private var player: MediaPlayer? = null

    @Volatile
    private var fetchExec: ExecutorService? = null

    /** 当前这次朗读的原文（用于「再点一次同一句就停止」）。 */
    @Volatile
    var activeText: String = ""
        private set

    /** 是否正在朗读（Compose State，UI 可直接读）。 */
    /**
     * 官方是三态：Idle → Loading（扫光）→ Playing（波形）。
     * 之前只有一个 speaking 布尔量，UI 分不清「正在请求」和「正在播」。
     */
    enum class Phase { Idle, Loading, Playing }

    /** 当前处于 Loading/Playing 的消息 id，用来只高亮那一条的朗读按钮 */
    var activeMessageId: Long = -1L

    var phase: Phase by mutableStateOf(Phase.Idle)
        private set

    var speaking by mutableStateOf(false)
        private set

    /** 给 UI 用的只读别名。 */
    internal fun setPhase(p: Phase) { phase = p }

    val isSpeaking: Boolean
        get() = speaking

    /** 进度文案，例如 "3/8" 或 "系统语音"；不在朗读时为空。 */
    var progressText by mutableStateOf("")
        private set

    /** 给 ChatParts 的「朗读」按钮用；由 DsApp 在组合时挂上。 */
    var onRead: (Long, String) -> Unit = { _, _ -> }

    @Volatile
    private var lastError: String = ""
    @Volatile
    private var lastSystemError: String = ""
    private var sysTts: TextToSpeech? = null

    /**
     * 「朗读」按钮：同一句正在读时再点一次就是停止；换一句则打断旧的、读新的。
     */
    fun toggle(ctx: Context, settings: Settings, msgId: Long, text: String, onError: (String) -> Unit = {}) {
        if (phase != Phase.Idle && activeMessageId == msgId) stop()
        else speak(ctx, settings, msgId, text, onError)
    }

    /**
     * 开始朗读。会先停掉上一次；text 为空时直接返回。
     */
    fun speak(ctx: Context, settings: Settings, msgId: Long, text: String, onError: (String) -> Unit = {}) {
        val body = text.trim()
        if (body.isEmpty()) return
        stop()

        val my = token.incrementAndGet()
        activeText = body
        activeMessageId = msgId
        setPhase(Phase.Loading)
        lastError = ""
        lastSystemError = ""
        setState { speaking = true }
        setProgress("")
        val appCtx = ctx.applicationContext ?: ctx
        cleanCache(appCtx, my)

        Thread {
            var segs: List<String> = emptyList()
            // >=0 表示从这一段开始 /v1/tools/tts 走不通，剩下的交给系统 TTS
            var fallbackFrom = -1
            var segIndex = 0
            var execRef: ExecutorService? = null
            try {
                segs = splitSegments(body)
                if (segs.isEmpty()) return@Thread
                val exec = Executors.newSingleThreadExecutor { r ->
                    Thread(r, "ds-tts-fetch").apply { isDaemon = true }
                }
                execRef = exec
                fetchExec = exec
                var pending: Future<File?>? = null
                while (segIndex < segs.size) {
                    if (token.get() != my) return@Thread
                    val i = segIndex
                    val cur = pending
                        ?: exec.submit<File?> { fetch(appCtx, settings, segs[i], i, my) }
                    pending = if (i + 1 < segs.size) {
                        exec.submit<File?> { fetch(appCtx, settings, segs[i + 1], i + 1, my) }
                    } else null

                    val file = try {
                        cur.get()
                    } catch (e: Exception) {
                        null
                    }
                    if (token.get() != my) return@Thread
                    if (file == null) {
                        fallbackFrom = i
                        break
                    }
                    setProgress("${i + 1}/${segs.size}")
                    if (phase != Phase.Playing) setPhase(Phase.Playing)
                    val played = playBlocking(file, my)
                    if (token.get() != my) return@Thread
                    if (!played) {
                        fallbackFrom = i
                        break
                    }
                    segIndex = i + 1
                }
                exec.shutdownNow()
            } catch (e: Exception) {
                lastError = e.message ?: "朗读失败"
                if (fallbackFrom < 0) fallbackFrom = segIndex
            } finally {
                if (fetchExec === execRef) fetchExec = null
                if (token.get() == my) {
                    if (fallbackFrom >= 0) {
                        // 接口/播放在中途失败：已读过的保留，没读的那部分用系统 TTS 读完
                        val rest = if (fallbackFrom == 0) body
                        else segs.drop(fallbackFrom).joinToString("\n")
                        setProgress("系统语音")
                        val ok = runCatching { speakWithSystem(appCtx, rest, my) }.getOrDefault(false)
                        if (token.get() == my) {
                            setState { speaking = false }
                            setProgress("")
                            activeText = ""
                            setPhase(Phase.Idle)
                            activeMessageId = -1L
                            if (!ok) {
                                val msg = lastSystemError.ifBlank {
                                    lastError.ifBlank { "朗读失败" }
                                }
                                main.post { onError(msg) }
                            }
                        }
                    } else {
                        setState { speaking = false }
                        setProgress("")
                        activeText = ""
                        setPhase(Phase.Idle)
                        activeMessageId = -1L
                    }
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    /** 停止朗读（网络请求、合成、播放、系统 TTS 一并停）。 */
    fun stop() {
        // 推进 token 后，旧的朗读线程在下一次检查时自然退出
        token.incrementAndGet()
        setState { speaking = false }
        setProgress("")
        activeText = ""
        setPhase(Phase.Idle)
        activeMessageId = -1L
        val pendingWaiters = synchronized(waiters) { waiters.toList() }
        pendingWaiters.forEach { it.countDown() }
        synchronized(waiters) { waiters.clear() }
        stopSystem()
        fetchExec?.shutdownNow()
        fetchExec = null
        main.post {
            val p = player
            player = null
            runCatching { p?.release() }
        }
    }

    /* ---------------------------------------------------------------- 网络合成 */

    private fun fetch(
        ctx: Context,
        settings: Settings,
        text: String,
        index: Int,
        my: Int,
    ): File? {
        if (token.get() != my) return null
        if (settings.apiKey.isBlank()) {
            lastError = "先在设置里填 API Key"
            return null
        }
        val payload = JSONObject()
            .put("text", text)
            .put("voice_id", voiceId.ifBlank { DEF_VOICE })
            .put("format", "wav")
            .put("filter_markdown", true)
            .toString()
        val url = settings.baseUrl.trimEnd('/') + "/v1/tools/tts"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + settings.apiKey.trim())
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val hint = runCatching { resp.body.string() }.getOrNull().orEmpty()
                    lastError = if (resp.code == 401 || resp.code == 403) {
                        "朗读失败：API Key 无效（HTTP ${resp.code}）"
                    } else if (resp.code == 400 && hint.contains("voice_id")) {
                        "朗读失败：音色 ${voiceId} 不可用（仅 stella / tide）"
                    } else {
                        "朗读失败 HTTP ${resp.code}"
                    }
                    return null
                }
                val bytes = runCatching { resp.body.bytes() }.getOrNull() ?: run {
                    lastError = "朗读失败：响应为空"
                    return null
                }
                if (!isRiffWav(bytes)) {
                    lastError = "朗读失败：返回的不是 WAV"
                    return null
                }
                if (token.get() != my) return null
                val dir = File(ctx.cacheDir, "tts").apply { mkdirs() }
                val f = File(dir, "seg_${my}_$index.wav")
                f.writeBytes(normalizeWav(bytes))
                f
            }
        } catch (e: Exception) {
            lastError = if (e is java.io.IOException) "朗读失败：网络不通" else (e.message ?: "朗读失败")
            null
        }
    }

    private fun isRiffWav(bytes: ByteArray): Boolean =
        bytes.size > 44 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()

    /**
     * 官方返回的是「流式 WAV」：RIFF 和 data 的长度字段写的是 0xFFFFFFFF（未知长度）。
     * 本地文件其实是完整的，这里按实际字节数把 RIFF/data 长度补齐，
     * 否则部分 MediaPlayer 实现会算不出时长、播到一半不出声或者 completion 不回调。
     */
    private fun normalizeWav(bytes: ByteArray): ByteArray {
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = readIntLE(bytes, pos + 4)
            if (id == "data") {
                val payload = bytes.size - (pos + 8)
                if (payload > 0 && size != payload) {
                    writeIntLE(bytes, pos + 4, payload)
                    writeIntLE(bytes, 4, bytes.size - 8)
                }
                return bytes
            }
            if (size < 0 || pos + 8 + size > bytes.size) return bytes
            pos += 8 + size + (size and 1)
        }
        return bytes
    }

    private fun readIntLE(a: ByteArray, off: Int): Int =
        (a[off].toInt() and 0xFF) or
            ((a[off + 1].toInt() and 0xFF) shl 8) or
            ((a[off + 2].toInt() and 0xFF) shl 16) or
            ((a[off + 3].toInt() and 0xFF) shl 24)

    private fun writeIntLE(a: ByteArray, off: Int, v: Int) {
        a[off] = (v and 0xFF).toByte()
        a[off + 1] = ((v ushr 8) and 0xFF).toByte()
        a[off + 2] = ((v ushr 16) and 0xFF).toByte()
        a[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    /* ---------------------------------------------------------------- 播放 */

    /** 在主线程创建 MediaPlayer，阻塞到这一段播完（或被打断）。 */
    private fun playBlocking(file: File, my: Int): Boolean {
        if (token.get() != my) return false
        val done = CountDownLatch(1)
        waiters.add(done)
        var ok = false
        main.post {
            if (token.get() != my) {
                done.countDown()
                return@post
            }
            if (!file.exists()) {
                done.countDown()
                return@post
            }
            try {
                val mp = MediaPlayer()
                player = mp
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener { done.countDown() }
                mp.setOnErrorListener { _, _, _ ->
                    lastError = "播放失败"
                    done.countDown()
                    true
                }
                mp.prepare()
                mp.start()
                ok = true
            } catch (e: Exception) {
                lastError = e.message ?: "播放失败"
                runCatching { player?.release() }
                player = null
                done.countDown()
            }
        }
        val finished = try {
            done.await(240, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            false
        }
        waiters.remove(done)
        main.post {
            val p = player
            player = null
            runCatching { p?.release() }
            // 这段已经播完/被停掉，立刻删掉临时 wav，避免长回答把 cache 撑大
            runCatching { file.delete() }
        }
        return ok && finished && token.get() == my
    }

    /* ---------------------------------------------------------------- 系统 TTS 兜底 */

    private fun defaultTtsEngine(ctx: Context): String? = runCatching {
        android.provider.Settings.Secure.getString(ctx.contentResolver, "tts_default_synth")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * 在主线程创建 TextToSpeech（初始化回调也在主线程），worker 线程等结果。
     * [engine] 为空时用系统默认引擎；Android 11+ 有包可见性限制，必要时显式指定
     * 系统默认 TTS 包名（tts_default_synth）再试一次。
     *
     * 注意：targetSdk >= 30 时，AndroidManifest 里最好有
     * `<queries><intent><action android:name="android.intent.action.TTS_SERVICE"/></intent></queries>`，
     * 否则部分机型查不到 TTS 引擎；本仓库的清单目前没有这一项（按约定未改清单），
     * 所以这里再按默认引擎包名显式初始化一次兜底。
     */
    private fun initSystemTts(ctx: Context, engine: String?): TextToSpeech? {
        val initLatch = CountDownLatch(1)
        var initOk = false
        var created: TextToSpeech? = null
        main.post {
            try {
                val listener = TextToSpeech.OnInitListener { status ->
                    initOk = status == TextToSpeech.SUCCESS
                    initLatch.countDown()
                }
                created = if (engine.isNullOrBlank()) {
                    TextToSpeech(ctx, listener)
                } else {
                    TextToSpeech(ctx, listener, engine)
                }
                sysTts = created
            } catch (t: Throwable) {
                initLatch.countDown()
            }
        }
        val inited = try {
            initLatch.await(10, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            false
        }
        if (!inited || !initOk) {
            stopSystem()
            return null
        }
        return created
    }

    /**
     * 系统 TextToSpeech（中文）兜底：接口不可用/没网/没 Key 时也一定能出声。
     * 返回 true 表示确实读完了。
     */
    private fun speakWithSystem(ctx: Context, text: String, my: Int): Boolean {
        stopSystem()
        // 默认引擎不行时，退一步用系统设置里的默认 TTS 引擎包名显式初始化
        val tts: TextToSpeech = initSystemTts(ctx, null)
            ?: defaultTtsEngine(ctx)?.let { initSystemTts(ctx, it) }
            ?: run {
                lastSystemError = "系统朗读引擎不可用"
                stopSystem()
                return false
            }
        if (token.get() != my) {
            stopSystem()
            return false
        }
        val avail = runCatching { tts.setLanguage(Locale.SIMPLIFIED_CHINESE) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED) {
            val retry = runCatching { tts.setLanguage(Locale.CHINA) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (retry == TextToSpeech.LANG_MISSING_DATA || retry == TextToSpeech.LANG_NOT_SUPPORTED) {
                lastSystemError = "系统缺少中文语音包"
                stopSystem()
                return false
            }
        }
        tts.setSpeechRate(1.0f)
        tts.setPitch(1.0f)
        runCatching {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }

        val chunks = systemChunks(text)
        if (chunks.isEmpty()) return false
        val done = CountDownLatch(1)
        val left = AtomicInteger(chunks.size)
        var ttsError = false
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (left.decrementAndGet() <= 0) done.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                ttsError = true
                done.countDown()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                ttsError = true
                done.countDown()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                done.countDown()
            }
        })
        waiters.add(done)
        val first = tts.speak(chunks[0], TextToSpeech.QUEUE_FLUSH, null, "ds-$my-0")
        if (first != TextToSpeech.SUCCESS) {
            waiters.remove(done)
            lastSystemError = "系统朗读启动失败"
            stopSystem()
            return false
        }
        for (i in 1 until chunks.size) {
            if (token.get() != my) break
            tts.speak(chunks[i], TextToSpeech.QUEUE_ADD, null, "ds-$my-$i")
        }
        // 中文 TTS 大约 4~5 字/秒，按 3 字/秒 + 余量等，避免长文被过早判超时
        val limit = (text.length / 3L + 120L).coerceAtMost(1800L)
        val finished = try {
            done.await(limit, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            false
        }
        waiters.remove(done)
        stopSystem()
        if (ttsError) lastSystemError = "系统朗读出错"
        return finished && !ttsError
    }

    private fun systemChunks(text: String): List<String> {
        val max = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000)
            .coerceIn(400, 3800)
        val out = ArrayList<String>()
        var rest = text.trim()
        while (rest.isNotEmpty() && out.size < 20) {
            if (rest.length <= max) {
                out.add(rest)
                break
            }
            val cut = rest.lastIndexOfAny(charArrayOf('。', '！', '？', '；', '\n', '，'), max - 1)
                .let { if (it < max / 2) max else it + 1 }
            out.add(rest.substring(0, cut))
            rest = rest.substring(cut)
        }
        return out
    }

    private fun stopSystem() {
        val t = sysTts
        sysTts = null
        if (t != null) {
            runCatching { t.stop() }
            runCatching { t.shutdown() }
        }
    }

    /* ---------------------------------------------------------------- 其它 */

    private fun setState(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun setProgress(s: String) {
        setState { progressText = s }
    }

    private fun cleanCache(ctx: Context, keepToken: Int) {
        runCatching {
            val dir = File(ctx.cacheDir, "tts")
            if (!dir.exists()) return@runCatching
            val prefix = "seg_${keepToken}_"
            dir.listFiles()?.forEach { f ->
                if (!f.name.startsWith(prefix)) f.delete()
            }
        }
    }

    /**
     * 按中文句末标点切段，单段控制在 SEG_MAX 字以内；相邻短句会合并，
     * 减少请求次数（每段一次 /v1/tools/tts，避免一次传整篇长文）。
     */
    private fun splitSegments(text: String): List<String> {
        val clean = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[ \t\u00A0]+"), " ")
            .replace(Regex("\n{2,}"), "\n")
            .trim()
        if (clean.isBlank()) return emptyList()
        val raw = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in clean) {
            sb.append(ch)
            if (ch in "。！？!?；;\n…") {
                val s = sb.toString().trim()
                if (s.isNotEmpty()) raw.add(s)
                sb.setLength(0)
            }
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) raw.add(tail)

        val merged = ArrayList<String>()
        val acc = StringBuilder()
        for (s in raw) {
            if (s.isEmpty()) continue
            if (acc.isNotEmpty() && acc.length + s.length > SEG_MAX) {
                merged.add(acc.toString().trim())
                acc.setLength(0)
            }
            if (s.length > SEG_MAX) {
                // 超长单句：优先在逗号处断开，实在没有就硬切
                var rest = s
                while (rest.length > SEG_MAX) {
                    var cut = rest.lastIndexOfAny(charArrayOf('，', ',', '、', '：', ':', ' '), SEG_MAX - 1)
                    if (cut < SEG_MAX / 2) cut = SEG_MAX - 1
                    val piece = rest.substring(0, cut + 1).trim()
                    if (piece.isNotEmpty()) {
                        if (acc.isNotEmpty()) {
                            merged.add(acc.toString().trim())
                            acc.setLength(0)
                        }
                        merged.add(piece)
                    }
                    rest = rest.substring(cut + 1)
                }
                if (rest.isNotBlank()) {
                    if (acc.length + rest.length > SEG_MAX) {
                        merged.add(acc.toString().trim())
                        acc.setLength(0)
                    }
                    acc.append(rest)
                }
            } else {
                acc.append(s)
            }
        }
        if (acc.isNotEmpty()) merged.add(acc.toString().trim())

        return merged
            .filter { seg -> seg.any { it.isLetterOrDigit() || it.code > 0x2E80 } }
            .take(SEG_CAP)
    }
}
