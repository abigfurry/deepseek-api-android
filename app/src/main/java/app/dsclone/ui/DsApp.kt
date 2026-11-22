package com.yuyuyu.deepseek.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.yuyuyu.deepseek.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.yuyuyu.deepseek.data.Msg
import com.yuyuyu.deepseek.data.MsgVariant
import com.yuyuyu.deepseek.data.Session
import com.yuyuyu.deepseek.data.Store
import com.yuyuyu.deepseek.data.estimateTokens
import com.yuyuyu.deepseek.data.switchVariant
import com.yuyuyu.deepseek.data.syncActiveVariant
import com.yuyuyu.deepseek.data.withBalanceCache
import com.yuyuyu.deepseek.data.withFetchedModels
import com.yuyuyu.deepseek.net.Client
import okhttp3.Call
import java.util.UUID
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val atBottom by androidx.compose.runtime.remember(listState) {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || (last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 8)
        }
    }
    val main = remember { Handler(Looper.getMainLooper()) }
    val clipboard = LocalClipboardManager.current

    var settings by remember { mutableStateOf(Store.loadSettings(ctx)) }
    val sessions = remember {
        mutableStateListOf<Session>().also { it.addAll(Store.loadSessions(ctx)) }
    }
    var jumpToMessageId by remember { mutableStateOf(-1L) }
    var currentId by remember { mutableStateOf(sessions.firstOrNull()?.id) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Msg?>(null) }
    // 两栏常驻断点：官方 smali 静态分析给出的是 840dp（_work\OFFICIAL-ADAPT.md），
    // 但**真机实测这台 800dp 平板官方就是两栏常驻**（抽屉 305dp + 内容），所以静态那个
    // 840 与实测冲突，以实测为准：800dp 必须两栏，用 768 作断点（官方在 768~839 之间的
    // 具体行为没有可测设备验证，先保持 768 不动，避免把平板布局改坏）。
    val isWideScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 768
    var drawerOpen by remember { mutableStateOf(isWideScreen) }
    val launchPhone = remember {
        ((ctx as? android.app.Activity)?.intent?.getBooleanExtra("phone", false)) ?: false
    }
    // 只有「一个 Key 都没有」的首次启动才弹登录页。
    // 之前判断的是 settings.apiKey（= 当前服务商的 Key），于是只要切到一家还没填 Key 的服务商，
    // 重启就直接掉回登录界面 —— 主界面整个变成登录页，这就是「主界面 UI 都不同了」。
    var showLogin by remember {
        mutableStateOf(!launchPhone && settings.keys.values.none { it.isNotBlank() })
    }
    var showPhone by remember { mutableStateOf(launchPhone) }
    var showSettings by remember { mutableStateOf(false) }
    var showModel by remember { mutableStateOf(false) }
    var showContext by remember { mutableStateOf(false) }
    var activeCall by remember { mutableStateOf<Call?>(null) }
    val attachments = remember { mutableStateListOf<Attachment>() }
    var cameraOpen by remember {
        mutableStateOf(
            ((ctx as? android.app.Activity)?.intent?.getBooleanExtra("camera", false)) ?: false
        )
    }
    var attachSheet by remember { mutableStateOf(false) }
    // ：按住?+ 官方?6% 屏高、蓝色渐变?8 根波
    var voiceMode by remember { mutableStateOf(false) }
    var voiceCancelArmed by remember { mutableStateOf(false) }
    var voicePending by remember { mutableStateOf("") }
    // 右上角自动朗读：了之后每答结束自动走 /v1/tools/tts（按量费）
    var autoRead by remember { mutableStateOf(false) }
    var spokenId by remember { mutableStateOf(0L) }
    // 手指按住就出，不依赖识别引擎成功
    var voiceHolding by remember { mutableStateOf(false) }
    val voice = rememberVoiceInput(
        onPartial = { },
        onFinal = { text ->
            // 官方：识直接把这句发出去，不用再点发
    val merged = if (input.isBlank()) text else input + text
            input = ""
            voiceMode = false
            voicePending = merged
        },
        onError = { msg ->
            // 这个回调来自录音/识别线程，直?Toast ?NPE（Can't toast on a thread that has not called Looper.prepare
    android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        },
    )
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) voiceMode = true }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) attachments.add(Attachment(uri, true, "图片"))
    }
    val fileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) attachments.add(Attachment(uri, false, uri.lastPathSegment ?: "文件"))
    }
    val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) cameraOpen = true
    }

    val wantDemo = remember {
        ((ctx as? android.app.Activity)?.intent?.getBooleanExtra("demo", false)) ?: false
    }
    remember(wantDemo) {
        if (wantDemo && sessions.none { it.title == "格式演示" }) {
            val body = """
## 代码?```kotlin
fun main() {
    println("Hello DeepSeek")
}
```
## 
$$
\int_0^1 x^2\,dx = \frac{1}{3}
$$
## 列表
- ?- ?""".trimIndent()
            val demoId = "demo-" + System.currentTimeMillis()
            sessions.add(
                0,
                Session(
                    id = demoId,
                    title = "格式演示",
                    messages = listOf(
                        Msg(role = "user", text = "演示一下代码和公式"),
                        Msg(role = "assistant", text = body),
                    ),
                )
            )
            currentId = demoId
        }
        true
    }

    val current = sessions.firstOrNull { it.id == currentId }

    // 上下文用量圈的数当前会话有消?text 的估?tokens + 输入框里正在打的
    val contextUsed = remember(current?.id, current?.messages, input) {
        (current?.messages?.sumOf { estimateTokens(it.text) } ?: 0) + estimateTokens(input)
    }

    fun persist() {
        Store.saveSessions(ctx, sessions.toList())
    }

    /**
     * 导入聊天记录：用?Store.loadSessions 完全相同?Store.parseSessions 解析?     * ?session.id 去重合并（同 id 覆盖，不?id 追加到列表最前面），
     * 然后持久化并刷新界面。返回文件里的会话数? = 解析失败 / 没有会话?     */
    fun importSessions(json: String): Int {
        val parsed = Store.parseSessions(json)
        if (parsed.isEmpty()) return 0
        val incoming = LinkedHashMap<String, Session>()
        for (s in parsed) incoming[s.id] = s
        val existingIds = sessions.map { it.id }.toSet()
        for ((id, one) in incoming) {
            val i = sessions.indexOfFirst { it.id == id }
            if (i >= 0) sessions[i] = one
        }
        val added = incoming.filterKeys { it !in existingIds }.values.toList()
        if (added.isNotEmpty()) sessions.addAll(0, added)
        persist()
        if (currentId == null || sessions.none { it.id == currentId }) {
            currentId = sessions.firstOrNull()?.id
        }
        return parsed.size
    }

    fun patch(sessionId: String, msgId: Long, block: (Msg) -> Msg) {
        val si = sessions.indexOfFirst { it.id == sessionId }
        if (si < 0) return
        val s = sessions[si]
        val mi = s.messages.indexOfFirst { it.id == msgId }
        if (mi < 0) return
        val list = s.messages.toMutableList()
        list[mi] = block(list[mi])
        sessions[si] = s.copy(messages = list, updatedAt = System.currentTimeMillis())
    }

    fun runChat(
        sessionId: String,
        botId: Long,
        history: List<Msg>,
        images: List<String> = emptyList(),
    ) {
        sending = true
        val startedAt = System.currentTimeMillis()
        activeCall = Client.chat(
            settings = settings,
            history = history,
            onDelta = { content, reasoning ->
                main.post {
                    patch(sessionId, botId) { m ->
                        m.copy(text = m.text + content, reasoning = m.reasoning + reasoning)
                    }
                }
            },
            onDone = {
                main.post {
                    val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                    // 流式结束把?思?用时固化进当前，分支切换时才能互相
    patch(sessionId, botId) { it.copy(streaming = false, thinkSeconds = secs).syncActiveVariant() }
                    sending = false
                    persist()
                }
            },
            onSteps = { steps, rawCalls, citations ->
                main.post {
                    patch(sessionId, botId) { m ->
                        m.copy(
                            // 服务网工具（stream  server_tool_event / ?server_tool_calls
    steps = if (steps.isEmpty()) m.steps else steps,
                            serverToolCalls = rawCalls.ifBlank { m.serverToolCalls },
                            // annotations ?官方那「来源小圆标 + N 页
    citations = if (citations.isEmpty()) m.citations else citations,
                        )
                    }
                }
            },
            onError = { err ->
                main.post {
                    patch(sessionId, botId) { m ->
                        m.copy(
                            streaming = false,
                            error = true,
                            text = if (m.text.isBlank()) err else m.text + "\n\n" + err,
                        ).syncActiveVariant()
                    }
                    sending = false
                    persist()
                }
            },
            imageDataUrls = images,
        )
    }

    fun newChat() {
        val s = Session(UUID.randomUUID().toString(), "新对话")
        sessions.add(0, s)
        currentId = s.id
        input = ""
        editing = null
    }

    fun send(text: String) {
        val t = text.trim()
        // 官方：只带附件（图片/文件）不输入文字也能发
    if ((t.isEmpty() && attachments.isEmpty()) || sending) return
        if (settings.apiKey.isBlank()) {
            showSettings = true
            return
        }
        if (currentId == null) {
            val s = Session(UUID.randomUUID().toString(), if (t.isNotEmpty()) t.take(20) else (attachments.firstOrNull()?.name ?: "图片"))
            sessions.add(0, s)
            currentId = s.id
        }
        val sid = currentId ?: return
        val si = sessions.indexOfFirst { it.id == sid }
        if (si < 0) return
        val s = sessions[si]

        val editingMsg = editing
        val base: List<Msg> = if (editingMsg != null) {
            val idx = s.messages.indexOfFirst { it.id == editingMsg.id }
            if (idx >= 0) s.messages.take(idx) else s.messages
        } else {
            s.messages
        }

        // 官方 deepseek-flash 图片：content 里放 {"type":"image_url","image_url":{"url":"data:image/...;base64,..."}}
        // 这里按真?MIME 编码，并把长边压?1300px（官方进模型前也到约 1300x1300），避免请求体过
    val imageUrls = attachments.filter { it.isImage }.mapNotNull { a ->
            runCatching {
                val mime = ctx.contentResolver.getType(a.uri) ?: "image/jpeg"
                val raw = ctx.contentResolver.openInputStream(a.uri)?.use { it.readBytes() } ?: return@runCatching null
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
                val maxSide = maxOf(opts.outWidth, opts.outHeight)
                var scale = 1
                while (maxSide / scale > 1300) scale *= 2
                val decOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = scale }
                val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, decOpts)
                val bytes: ByteArray = if (bmp != null) {
                    val w = bmp.width
                    val h = bmp.height
                    val target = if (maxOf(w, h) > 1300) {
                        val f = 1300f / maxOf(w, h)
                        android.graphics.Bitmap.createScaledBitmap(bmp, (w * f).toInt(), (h * f).toInt(), true)
                    } else bmp
                    java.io.ByteArrayOutputStream().use { out ->
                        target.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                        out.toByteArray()
                    }
                } else raw
                "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }.getOrNull()
        }
        val savedImages = attachments.filter { it.isImage }.mapNotNull { a -> persistImage(ctx, a.uri) }
        // 文件附件：把名字和大小进消气泡里才显示得出
    val savedFiles = attachments.filter { !it.isImage }.map { a ->
            com.yuyuyu.deepseek.data.MsgFile(
                name = queryFileName(ctx, a.uri, a.name),
                size = queryFileSize(ctx, a.uri),
            )
        }
        val userMsg = Msg(
            role = "user",
            text = t,
            hasImages = attachments.any { it.isImage },
            images = savedImages,
            files = savedFiles,
        )
        val botMsg = Msg(
            role = "assistant",
            streaming = true,
            hasImages = attachments.any { it.isImage },
            // 联网由服务做：流式过程?server_tool_event / server_tool_calls 才填 steps
    // 这里不再预置「在搜xxx」（客户索已经删掉）
        )
        sessions[si] = s.copy(
            title = if (base.isEmpty()) t.take(20) else s.title,
            messages = base + userMsg + botMsg,
            updatedAt = System.currentTimeMillis(),
        )
        editing = null
        input = ""
        runChat(sid, botMsg.id, base + userMsg, imageUrls)
        attachments.clear()
    }

    // 调试am start ... --es ask "你好" 直接发一条，方便逐像素比
    val askExtra = remember {
        ((ctx as? android.app.Activity)?.intent?.getStringExtra("ask")).orEmpty()
    }
    LaunchedEffect(askExtra) {
        if (askExtra.isNotBlank()) {
            kotlinx.coroutines.delay(400)
            send(askExtra)
        }
    }

    fun regenerateFrom(msg: Msg) {
        if (sending) return
        val s = current ?: return
        val idx = s.messages.indexOfFirst { it.id == msg.id }
        if (idx < 0) return
        val old = s.messages[idx]
        val base = s.messages.take(idx)
        if (base.isEmpty()) return
        // 旧回答不再丢弃：先把当前内固化成（老消?variants 就先补成?0 
    // 再追加一候并切过去，请求结果后写?variants[variantIndex]
    // 出错的那条回答不当作候（错文 + 空文不该出现在分支里），直接不保留
    val seed: List<MsgVariant> = if (old.variants.isNotEmpty()) {
            old.variants.toMutableList().also { list ->
                val i = old.variantIndex.coerceIn(0, list.lastIndex)
                if (old.error) list.removeAt(i)
                else list[i] = MsgVariant(
                    text = old.text,
                    reasoning = old.reasoning,
                    thinkSeconds = old.thinkSeconds,
                )
            }
        } else if (old.error || (old.text.isBlank() && old.reasoning.isBlank())) {
            emptyList()
        } else {
            listOf(MsgVariant(text = old.text, reasoning = old.reasoning, thinkSeconds = old.thinkSeconds))
        }
        val botMsg = Msg(
            role = "assistant",
            streaming = true,
            hasImages = attachments.any { it.isImage },
            variants = seed + MsgVariant(),
            variantIndex = seed.size,
        )
        val si = sessions.indexOfFirst { it.id == s.id }
        sessions[si] = s.copy(messages = base + botMsg, updatedAt = System.currentTimeMillis())
        runChat(s.id, botMsg.id, base)
    }

    /** 分支切换：纯把内容写?text/reasoning，绝不重新发请求 */
    fun switchVariant(msg: Msg, index: Int) {
        if (index !in msg.variants.indices) return
        val sid = currentId ?: return
        patch(sid, msg.id) { it.switchVariant(index) }
        persist()
    }

    fun deleteFrom(msg: Msg) {
        val s = current ?: return
        val idx = s.messages.indexOfFirst { it.id == msg.id }
        if (idx < 0) return
        val si = sessions.indexOfFirst { it.id == s.id }
        sessions[si] = s.copy(messages = s.messages.take(idx))
        persist()
    }

    val drawer: @Composable () -> Unit = {
        DrawerContent(
            sessions = sessions,
            currentId = currentId,
            settings = settings,
            onSelect = { id ->
                // 官方双栏布局下点会话换右边内容，侧边栏保持展
                currentId = id
                editing = null
            },
            onNewChat = {
                newChat()
                if (!isWideScreen) drawerOpen = false
            },
            onDelete = { s ->
                sessions.remove(s)
                if (currentId == s.id) currentId = sessions.firstOrNull()?.id
                persist()
            },
            onRename = { s, name ->
                val i = sessions.indexOfFirst { it.id == s.id }
                if (i >= 0) sessions[i] = sessions[i].copy(title = name)
                persist()
            },
            onPin = { s, pinned ->
                val i = sessions.indexOfFirst { it.id == s.id }
                if (i >= 0) sessions[i] = sessions[i].copy(pinned = pinned)
                persist()
            },
            onSettings = {
                showSettings = true
                // 宽屏栏常驻，打开设置不能把抽屉关掉（关掉再出就变全屏了
    if (!isWideScreen) drawerOpen = false
            },
            onModel = {
                showModel = true
                if (!isWideScreen) drawerOpen = false
            },
            onClose = { drawerOpen = false },
            onJump = { sid, mid ->
                currentId = sid
                editing = null
                jumpToMessageId = mid
            },
        )
    }

    val mainContent: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                TopBar(
                    title = current?.title.orEmpty(),
                    onMenu = { drawerOpen = !drawerOpen },
                    onMute = {
                autoRead = !autoRead
                android.widget.Toast.makeText(
                    ctx,
                    if (autoRead) "已开启自动朗读" else "已关闭自动朗读",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                // 关掉时顺手停掉在的（官方关开关会停朗
    if (!autoRead) DsTts.stop()
            },
                    onNewChat = { newChat() },
                    autoRead = autoRead,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // 会话切换过渡：逐帧驱动（rememberFrameAnim / withFrameNanos），
                    // 不再用 AnimatedContent —— 它的时钟由 Compose 动画系统（MotionDurationScale）
                    // 驱动，系统「动画时长缩放=0」或「移除动画」时会一帧跳变。
                    // 进入：新会话淡入 + 从右侧 4% 滑入；退出：旧会话淡出 + 向左 2% 滑出，
                    // 200ms / FastOutSlowInEasing（与原来一致）。
                    // 注意：退出态**不**再渲染第二套 MessageList —— 两套 LazyColumn 同时挂在同一个
                    // 共享 listState 上会互相抢 measure/滚动位置（表现为闪烁、跳位），所以旧内容
                    // 直接让位给页面底色，视觉上等同于「旧内容淡出」。
                    // 进度只在 graphicsLayer 的 block 里读，避免每帧重组整个会话内容。
                    val switchProgress = rememberFrameAnim(
                        durationMs = 200,
                        trigger = currentId,
                        startAtEnd = true,
                    )
                    val shown = current
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = frameEase(switchProgress.value)
                                alpha = t
                                translationX = (1f - t) * size.width * 0.04f
                            },
                    ) {
                        if (shown == null) {
                            // 没有会话：只保留底色
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        } else {
                            val msgs = shown.messages
                            if (msgs.isEmpty()) {
                                Welcome(onPick = { input = it })
                            } else {
                                MessageList(
                                    state = listState,
                                    scrollToMessageId = jumpToMessageId,
                                    onScrolledToMessage = { jumpToMessageId = -1L },
                                    messages = msgs,
                                    onCopy = {
                        clipboard.setText(AnnotatedString(it))
                        // 官方复制后浮已复制」提示（strings: copy_ok
            android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                    },
                                    onEdit = { m ->
                                        editing = m
                                        input = m.text
                                    },
                                    onRegenerateFrom = { regenerateFrom(it) },
                                    onSwitchVariant = { m, i -> switchVariant(m, i) },
                                    onDeleteFrom = { deleteFrom(it) },
                                    onShowContext = { showContext = true },
                                )
                            }
                        }
                    }
                }
                Box(Modifier.alpha(if (voiceHolding) 0f else 1f)) {
                InputBar(
                    value = input,
                    onValueChange = { input = it },
                    attachments = attachments,
                    onAddAttachment = { attachSheet = !attachSheet },
                    onRemoveAttachment = { attachments.remove(it) },
                    sending = sending,
                    thinking = settings.thinking,
                    search = settings.search,
                    editing = editing != null,
                    onToggleThinking = {
                        settings = settings.copy(thinking = !settings.thinking)
                        Store.saveSettings(ctx, settings)
                    },
                    onToggleSearch = {
                        settings = settings.copy(search = !settings.search)
                        Store.saveSettings(ctx, settings)
                    },
                    onCancelEdit = {
                        editing = null
                        input = ""
                    },
                    onSend = { send(input) },
                    voiceMode = voiceMode,
                    voiceHolding = voiceHolding,
                    voiceLevel = if (voiceHolding) voice.level else 0f,
                    voiceBands = voice.bands,
                    voiceListening = voice.listening,
                    onToggleVoice = {
                        if (voiceMode) {
                            voice.cancel()
                            voiceMode = false
                        } else if (androidx.core.content.ContextCompat.checkSelfPermission(
                                ctx, android.Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            voiceMode = true
                        } else {
                            micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onVoiceStart = { voiceCancelArmed = false; voiceHolding = true; runCatching { voice.start(settings) } },
                    onVoiceStop = { voiceHolding = false; runCatching { if (voiceCancelArmed) voice.cancel() else voice.stop() } },
                    onVoiceCancel = { voiceCancelArmed = true; voiceHolding = false; runCatching { voice.cancel() } },
                onVoiceCancelArm = { armed -> voiceCancelArmed = armed },
                    onStop = {
                        activeCall?.cancel()
                        activeCall = null
                        sending = false
                    },
                    attachOpen = attachSheet,
                    showFooter = !attachSheet,
                    contextUsed = contextUsed,
                    contextLimit = 131072,
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom && !attachSheet,
                    enter = androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(180)
                    ) + androidx.compose.animation.scaleIn(
                        animationSpec = androidx.compose.animation.core.tween(180),
                        initialScale = 0.86f,
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(140)
                    ) + androidx.compose.animation.scaleOut(
                        animationSpec = androidx.compose.animation.core.tween(140),
                        targetScale = 0.86f,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-14.10).dp, y = (-(45.71 + 8.75)).dp),
                ) {
                    ScrollToBottomButton(
                        onClick = { scope.launch { listState.animateScrollToItem((current?.messages?.size ?: 1) - 1) } },
                    )
                }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = attachSheet,
                    enter = androidx.compose.animation.expandVertically(
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ) + androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ),
                    exit = androidx.compose.animation.shrinkVertically(
                        animationSpec = androidx.compose.animation.core.tween(220)
                    ) + androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(220)
                    ),
                ) {
                    AttachmentTileRow(
                        onCamera = { attachSheet = false; cameraPermission.launch(android.Manifest.permission.CAMERA) },
                        onGallery = {
                            attachSheet = false
                            galleryLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onFile = {
                            attachSheet = false
                            fileLauncher.launch(arrayOf("*/*"))
                        },
                    )
                }
            }
        }
    }

    if (showLogin) {
        // 开屏页直接填 API Key（没有服务端账号体系，不需要手机号登录）
        ApiKeySetupScreen(initial = settings) { ns ->
            settings = ns
            showLogin = false
            showPhone = false
        }
    } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 768.dp) {
                // 官方平板两栏（audit-drawer.png 实测：抽屉 305dp + 1dp 分隔线 + 内容栏 494dp）。
                // 抽屉是一整张「纸」：开合时整块 305dp 从左边平移进来/出去（shift 0→1，240ms
                // FastOutSlowIn），而不是把抽屉内容钉在原位、靠外层容器裁切「刷」出来 ——
                // 后者看起来就是「覆盖式」。
                // 内容栏占剩余宽度，被纸片推着收窄；静态结果与官方一致（两栏时正文列 417.5dp、
                // 输入卡 460dp，都是按 pane 宽算的，见 ChatParts.bodyPadX / InputBar）。
                val drawerW = minOf(com.yuyuyu.deepseek.ui.theme.DsMetric.DRAWER_W.dp, maxWidth)
                // 官方：抽屉面板 305dp + 1dp 分隔线（总共 306dp），内容栏 = 494dp（800−306）。
                // 容器宽度按 306dp 动画，分隔线永远贴在容器右缘、正好补掉纸片右缘那 1dp。
                val drawerTotal = drawerW + 1.dp
                val shift by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (drawerOpen) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 240,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing,
                    ),
                    label = "drawerShift",
                )
                Row(
                Modifier
                    .fillMaxSize()
                    // 抽屉开合动画的缝隙里不能露出窗口白色底（深色模式下就是一条白边）
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            ) {
                    // 容器宽度 = 抽屉露出宽度（0…306dp）；纸片本体固定 305dp，随 shift 从 −306dp
                    // 平移到 0，所以整张纸（含其中的文字）一起滑进来，右缘的分隔线跟着抽屉右缘走。
                    Box(
                        Modifier
                            .width(drawerTotal * shift)
                            .fillMaxHeight()
                            .clip(androidx.compose.ui.graphics.RectangleShape)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                // requiredWidth：纸片宽度固定 305dp，不被「0→306dp」的容器约束带着
                                // 每帧重新测量/折行；超出的部分由外层 clip 裁掉。
                                .requiredWidth(drawerW)
                                // 纸片平移：只走绘制层，不参与布局，避免和容器宽度动画互相打架
                                .graphicsLayer {
                                    translationX = -drawerTotal.toPx() * (1f - shift)
                                }
                        ) {
                            drawer()
                        }
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(com.yuyuyu.deepseek.ui.theme.LocalDsColors.current.divider)
                        )
                    }
                    Box(Modifier.weight(1f)) { mainContent() }
                }
            } else {
                PushDrawer(
                    open = drawerOpen,
                    onClose = { drawerOpen = false },
                    // 官方实测：抽屉 310.48dp（关?310.5 始）
                    drawerWidth = minOf(com.yuyuyu.deepseek.ui.theme.DsMetric.DRAWER_W.dp, maxWidth),
                    drawer = drawer,
                    content = mainContent,
                )
            }
        }

        if (showSettings) {
            SettingsSheet(
                initial = settings,
                onDismiss = { showSettings = false },
                onSave = { s ->
                    settings = s
                    Store.saveSettings(ctx, s)
                    showSettings = false
                },
                // 数据管理 ?导出 / 导入聊天记录
                sessionsJson = { Store.sessionsJson(sessions.toList()) },
                onImportSessions = { json -> importSessions(json) },
            )
        }

        if (showModel) {
            ModelSheet(
                settings = settings,
                onDismiss = { showModel = false },
                onPick = { pid, url, m ->
                    settings = settings.copy(providerId = pid, baseUrl = url, model = m)
                    Store.saveSettings(ctx, settings)
                    showModel = false
                },
                onManage = {
                    showModel = false
                    showSettings = true
                },
                // 「获取模型列表」/「查询余额」的结果写回 Settings 并落盘（失败不会清空旧列表）
                onModelsFetched = { pid, ids ->
                    settings = if (pid == settings.providerId) settings.withFetchedModels(ids)
                    else settings.copy(providerModels = settings.providerModels + (pid to ids.take(500)))
                    Store.saveSettings(ctx, settings)
                },
                onBalanceCached = { pid, cache ->
                    settings = settings.copy(balanceCache = settings.balanceCache + (pid to cache))
                    Store.saveSettings(ctx, settings)
                },
            )
        }

        if (showContext) {
            ContextSheet(
                settings = settings,
                messages = current?.messages ?: emptyList(),
                onDismiss = { showContext = false },
            )
        }
    }

    // 设置里的朗音色 ?DsTts（stella / tide）：时和设置里切换后都同步一
    LaunchedEffect(settings.voiceId) {
        DsTts.voiceId = settings.voiceId.ifBlank { "stella" }
    }

    // 「朗读按右上角自动朗读都走官?/v1/tools/tts（失败自动降级系?TTS
    SideEffect {
        DsTts.onRead = { msgId, text ->
            val s = settings
            if (s.apiKey.isBlank()) {
                android.widget.Toast.makeText(ctx, "没有 API Key，用系统朗读", android.widget.Toast.LENGTH_SHORT).show()
                DsTts.speak(ctx, s, msgId, text)
            } else {
                // 同一句在时再点一次朗读? 停；换句则打断旧的读新
    DsTts.toggle(ctx, s, msgId, text) { msg ->
                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // 右上角自动朗读开了之后，每轮回答结束朗（官?/v1/tools/tts，按量费）
    LaunchedEffect(current?.id, current?.messages?.lastOrNull()?.id, autoRead) {
        val lm = current?.messages?.lastOrNull() ?: return@LaunchedEffect
        if (!autoRead || lm.role != "assistant" || lm.streaming || lm.error) return@LaunchedEffect
        if (lm.id == spokenId || lm.text.isBlank()) return@LaunchedEffect
        spokenId = lm.id
        // 关掉朗关不会打在的；新一答会用新 DsTts 覆盖旧的
    // ?Key / 接口失败?DsTts 会降级到系统 TextToSpeech
    DsTts.speak(ctx, settings, lm.id, lm.text) { msg ->
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 识别要继（官方识」这屏就层的态）
    LaunchedEffect(voicePending) {
        if (voicePending.isNotBlank()) {
            val t = voicePending
            voicePending = ""
            send(t)
        }
    }

    if (voiceHolding || voice.recognizing) {
        androidx.compose.foundation.layout.Box(
            // 官方：侧边栏收起时浮层铺满整屏；侧边栏展（平板两栏）时只在话栏
    Modifier
                .fillMaxSize()
                .padding(start = if (isWideScreen && drawerOpen) 311.48.dp else 0.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            VoiceOverlay(
                active = true,
                canceling = voiceCancelArmed,
                level = { if (voiceHolding) voice.level else 0f },
                bands = voice.bands,
                recognizing = voice.recognizing,
            )
        }
    }

    if (cameraOpen) {
        CameraCapture(
            onClose = { cameraOpen = false },
            onCaptured = { uri ->
                attachments.add(Attachment(uri, true, "拍照"))
                cameraOpen = false
            },
        )
    }

    if (attachSheet) {
        // 官方样式：附件菜单是输入卡下方的三块方按?AttachmentTileRow 渲染
    }

}
