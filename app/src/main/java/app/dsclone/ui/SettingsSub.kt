package com.yuyuyu.deepseek.ui

import android.graphics.RuntimeShader
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.R
import com.yuyuyu.deepseek.data.Providers
import com.yuyuyu.deepseek.data.Settings
import com.yuyuyu.deepseek.data.Store
import com.yuyuyu.deepseek.data.VoiceOption
import com.yuyuyu.deepseek.data.VoiceOptions
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.DsFontLevelNames
import com.yuyuyu.deepseek.ui.theme.DsFontScales
import com.yuyuyu.deepseek.ui.theme.LocalDsColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * 设置页的子页面。结构、文案、间距全部对齐官方 2.9.0：
 *   api      我们的「API 与模型」（官方没有，承载原有全部开发向参数）
 *   account  账号管理（账户 / 未成年人模式 / 安全 / 注销账号）
 *   data     数据管理（数据用于优化体验 / 我共享的链接 / 导出 / 删除）
 *   font     字体大小（聊天预览 + 跟随系统 + 20 档滑块外观的 5 档）
 *   personal 个性化（思考内容自动折叠）
 *   terms    服务协议（用户协议 / 隐私政策 / 三方共享 / 个人信息 / 应用权限）
 *   devices  登录设备管理
 *   help / doc:*  文本页
 */
@Composable
internal fun SettingsSubPage(
    page: String,
    s: Settings,
    onChange: (Settings) -> Unit,
    onNavigate: (String) -> Unit,
    onDialog: (String) -> Unit,
    onToast: (String) -> Unit,
    // 「数据管理 → 导入 / 导出聊天记录」：由 DsApp 提供当前会话 JSON 与合并回调
    sessionsJson: () -> String = { "[]" },
    onImportSessions: (String) -> Int = { 0 },
) {
    when (page) {
        SettingsPage.API -> ApiPage(s, onChange, onToast)
        SettingsPage.ACCOUNT -> AccountPage(s, onChange, onNavigate, onDialog)
        SettingsPage.DATA -> DataPage(
            s = s,
            onChange = onChange,
            onNavigate = onNavigate,
            onDialog = onDialog,
            onToast = onToast,
            sessionsJson = sessionsJson,
            onImportSessions = onImportSessions,
        )
        SettingsPage.FONT -> FontSizePage(s, onChange)
        SettingsPage.PERSONAL -> PersonalizationPage(s, onChange)
        SettingsPage.VOICE -> VoicePage(s, onChange, onToast)
        SettingsPage.TERMS -> TermsPage(onNavigate)
        SettingsPage.DEVICES -> DevicesPage()
        SettingsPage.HELP -> TextPage(HELP_TEXT)
        SettingsPage.LEGAL -> TextPage(LEGAL_TEXT)
        else -> TextPage(docBody(page))
    }
}

// =====================================================================================
//  API 与模型（原有开发向设置，一个不丢）
// =====================================================================================

@Composable
private fun ApiPage(s: Settings, onChange: (Settings) -> Unit, onToast: (String) -> Unit) {
    val ctx = LocalContext.current
    val busy = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val hint = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val balanceHint = com.yuyuyu.deepseek.data.balanceCacheEntry(s.balanceCache[s.providerId])?.text ?: ""
    Column(Modifier.fillMaxWidth()) {
        DsGroupHeader("服务商", topGap = DsFirstTop)
        DsCard {
            for (p in Providers) {
                SettingRow(
                    label = p.name,
                    value = if (p.id == s.providerId) "当前" else null,
                    valueColor = DsBlue,
                    chevron = false,
                    onClick = {
                        onChange(
                            s.copy(
                                providerId = p.id,
                                baseUrl = p.baseUrl.ifBlank { s.baseUrl },
                                model = p.models.firstOrNull() ?: s.model,
                            )
                        )
                    },
                )
            }
        }

        DsGroupHeader("API Key")
        DsCard {
            FieldRow(
                value = s.keys[s.providerId] ?: "",
                onValueChange = { onChange(s.copy(keys = s.keys + (s.providerId to it))) },
                placeholder = "sk-…",
                password = true,
            )
            // 没有 Key 的用户不用自己去翻服务商官网：这里直接打开当前服务商的申请页
            SettingRow(
                label = "申请 API Key",
                value = s.preset.name,
                chevron = true,
                onClick = { openUrl(ctx, providerKeyUrl(s.preset)) },
            )
        }
        DsSupporting("Key 在各家服务商控制台免费申请，只保存在本机。")
        DsSupporting("Key 只保存在本机 settings.json，请求直接发往下面填的 API 地址。")

        DsGroupHeader("模型")
        DsCard {
            for (m in s.models) {
                SettingRow(
                    label = m,
                    value = if (m == s.model) "当前" else null,
                    valueColor = DsBlue,
                    chevron = false,
                    onClick = { onChange(s.copy(model = m)) },
                )
            }
            // 多服务商必备：自动拉模型列表 / 查余额。放在设置里（不占主界面）。
            // 失败不会清空已有列表（官方没这个功能，这里按各家接口自行适配）。
            SettingRow(
                label = "自动获取模型列表",
                value = if (busy.value == "models") "获取中…" else null,
                valueColor = DsBlue,
                chevron = true,
                onClick = {
                    if (busy.value.isEmpty()) {
                        busy.value = "models"
                        hint.value = "正在请求 " + s.effectiveBaseUrl + " 的模型列表…"
                        val pid = s.providerId
                        val preset = s.preset
                        val key = s.keys[pid].orEmpty()
                        val base = s.effectiveBaseUrl
                        Thread {
                            val res = com.yuyuyu.deepseek.data.ProviderApi.fetchModels(preset, key, base)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                busy.value = ""
                                if (res.ok) {
                                    val ids = res.models.map { it.id }
                                    onChange(
                                        s.copy(
                                            providerModels = s.providerModels + (pid to ids.take(500))
                                        )
                                    )
                                    hint.value = "已获取 " + ids.size + " 个模型（手填的模型名仍然置顶）"
                                } else {
                                    hint.value = "获取失败：" + (res.error ?: "未知错误") +
                                        "（原有列表没有被清空）"
                                }
                            }
                        }.start()
                    }
                },
            )
            SettingRow(
                label = "查询余额",
                value = if (busy.value == "balance") "查询中…" else balanceHint,
                valueColor = DsBlue,
                chevron = true,
                onClick = {
                    if (busy.value.isEmpty()) {
                        busy.value = "balance"
                        hint.value = "正在查询余额…"
                        val pid = s.providerId
                        val preset = s.preset
                        val key = s.keys[pid].orEmpty()
                        val base = s.effectiveBaseUrl
                        Thread {
                            val r = com.yuyuyu.deepseek.data.ProviderApi.fetchBalance(preset, key, base)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                busy.value = ""
                                val text = com.yuyuyu.deepseek.data.balanceDisplayText(r)
                                val detail = com.yuyuyu.deepseek.data.balanceDetailLines(r)
                                    .joinToString("  ") { it.first + " " + it.second }
                                hint.value = if (detail.isBlank()) text else text + "  " + detail
                                if (r is com.yuyuyu.deepseek.data.BalanceResult.Money) {
                                    onChange(
                                        s.copy(
                                            balanceCache = s.balanceCache + (
                                                pid to com.yuyuyu.deepseek.data.balanceCacheJson(text, detail)
                                                )
                                        )
                                    )
                                }
                            }
                        }.start()
                    }
                },
            )
            FieldRow(
                value = s.model,
                onValueChange = { onChange(s.copy(model = it)) },
                placeholder = "或直接填模型 ID",
            )
        }
        if (hint.value.isNotBlank()) DsSupporting(hint.value)

        DsGroupHeader("API 地址")
        DsCard {
            FieldRow(
                value = s.baseUrl,
                onValueChange = { onChange(s.copy(baseUrl = it)) },
                placeholder = "https://api.deepseek.com",
            )
        }

        DsGroupHeader("思考")
        DsCard {
            SettingSwitchRow("深度思考", s.thinking, { onChange(s.copy(thinking = it)) })
            for (e in listOf("none", "low", "high", "max")) {
                SettingRow(
                    label = "思考强度  $e",
                    value = if (s.reasoningEffort == e) "当前" else null,
                    valueColor = DsBlue,
                    chevron = false,
                    onClick = { onChange(s.copy(reasoningEffort = e)) },
                )
            }
        }
        DsSupporting("对应请求体里的 thinking / reasoning_effort。")

        DsGroupHeader("采样")
        DsCard {
            SliderRow(
                label = "温度 temperature",
                valueText = String.format("%.2f", s.temperature),
                value = s.temperature,
                min = 0f,
                max = 2f,
                onChange = { onChange(s.copy(temperature = it)) },
            )
            SliderRow(
                label = "top_p",
                valueText = String.format("%.2f", s.topP),
                value = s.topP,
                min = 0.01f,
                max = 1f,
                onChange = { onChange(s.copy(topP = it)) },
            )
        }

        DsGroupHeader("输出")
        DsCard {
            FieldRow(
                value = if (s.maxTokens > 0) s.maxTokens.toString() else "",
                onValueChange = { t -> onChange(s.copy(maxTokens = t.filter { it.isDigit() }.toIntOrNull() ?: 0)) },
                placeholder = "最大输出 tokens（留空=默认）",
                keyboardType = KeyboardType.Number,
            )
            FieldRow(
                value = s.stop,
                onValueChange = { onChange(s.copy(stop = it)) },
                placeholder = "停止词 stop（多个用 | 分隔）",
            )
            SettingSwitchRow("JSON 模式", s.jsonMode, { onChange(s.copy(jsonMode = it)) })
        }
        DsSupporting("JSON 模式会给请求加上 response_format=json_object。")

        DsGroupHeader("系统提示词")
        DsCard {
            FieldRow(
                value = s.systemPrompt,
                onValueChange = { onChange(s.copy(systemPrompt = it)) },
                placeholder = "可选，作为 system 消息插在最前面",
                minHeight = 96.dp,
            )
        }

        DsGroupHeader("高级")
        DsCard {
            FieldRow(
                value = s.userId,
                onValueChange = { onChange(s.copy(userId = it)) },
                placeholder = "user_id（可选，用于缓存隔离）",
            )
            SettingSwitchRow("返回会话标题", s.titleEnabled, { onChange(s.copy(titleEnabled = it)) })
            SettingSwitchRow("内容安全", s.safety, { onChange(s.copy(safety = it)) })
            SettingSwitchRow("流式返回用量", s.includeUsage, { onChange(s.copy(includeUsage = it)) })
        }
        DsSupporting("对应 title.enabled / safety_check / stream_options.include_usage。")

        DsGroupHeader("维护")
        DsCard {
            SettingRow(
                label = "清理缓存",
                chevron = false,
                onClick = {
                    runCatching {
                        ctx.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                        ctx.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                    }
                    onToast("已清理")
                },
            )
        }
        DsSupporting("清理应用缓存目录（图片、语音等临时文件），不影响对话记录与设置项。")

        DsSupporting("修改会立刻生效，返回上一页时自动保存到本机。")
        Spacer(Modifier.height(36.dp))
    }
}

// =====================================================================================
//  账号管理
// =====================================================================================

@Composable
internal fun AccountPage(
    s: Settings,
    onChange: (Settings) -> Unit,
    onNavigate: (String) -> Unit,
    onDialog: (String) -> Unit,
) {
    val ds = LocalDsColors.current
    val ctx = LocalContext.current
    var editingName by remember { mutableStateOf(false) }
    var draftName by remember(editingName, s.nickName) {
        mutableStateOf(s.nickName.ifBlank { "本地账号" })
    }
    // 换头像：系统相册选图 → 复制到应用私有目录（不申请存储权限，也不上传）
    val pickAvatar = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val path = runCatching {
                val f = java.io.File(ctx.filesDir, "avatar_" + System.currentTimeMillis() + ".img")
                ctx.contentResolver.openInputStream(uri)!!.use { input ->
                    f.outputStream().use { out -> input.copyTo(out) }
                }
                f.absolutePath
            }.getOrNull()
            if (path != null) onChange(s.copy(avatarPath = path))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().weight(1f)) {
            // ── 个人资料（本机昵称 / 头像）──────────────────────────────
            DsGroupHeader("个人资料", margin = DsNarrowMargin, topGap = DsFirstTop)
            DsCard(margin = DsNarrowMargin) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { pickAvatar.launch("image/*") }
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarView(path = s.avatarPath, size = 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("头像", fontSize = 16.sp, color = ds.rowText, modifier = Modifier.weight(1f))
                    Text(
                        text = if (s.avatarPath.isBlank()) "点击设置" else "点击更换",
                        fontSize = 14.sp,
                        color = ds.secondary,
                    )
                }
                Box(Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp).height(1.dp).background(ds.divider))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingName = true }
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("昵称", fontSize = 16.sp, color = ds.rowText, modifier = Modifier.weight(1f))
                    Text(
                        text = s.nickName.ifBlank { "本地账号" },
                        fontSize = 15.sp,
                        color = ds.secondary,
                    )
                }
            }
            DsGroupHeader("账户", margin = DsNarrowMargin, topGap = DsFirstTop)
            DsCard(margin = DsNarrowMargin) {
                SettingRow("手机号", icon = "phone", value = "未绑定", chevron = true, onClick = { onDialog(SettingsDialog.ACCOUNT_ENTRY) })
                SettingRow("电子邮箱", icon = "mail", value = "未绑定", chevron = false, onClick = { onDialog(SettingsDialog.ACCOUNT_ENTRY) })
                SettingRow("微信", icon = "wechat", value = "未绑定", chevron = true, onClick = { onDialog(SettingsDialog.ACCOUNT_ENTRY) })
            }
            DsCard(margin = DsNarrowMargin, topGap = 26.5.dp) {
                SettingRow("未成年人模式", value = "已关闭", chevron = true, onClick = { onDialog(SettingsDialog.MINOR) })
            }
            DsGroupHeader("安全", margin = DsNarrowMargin)
            DsCard(margin = DsNarrowMargin) {
                SettingRow("登录设备管理", icon = "device", chevron = true, onClick = { onNavigate(SettingsPage.DEVICES) })
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 4.dp)
                .height(45.dp)
                .clickable { onDialog(SettingsDialog.DELETE_ACCOUNT) },
            contentAlignment = Alignment.Center,
        ) {
            Text("注销账号", fontSize = 16.sp, color = ds.destructive)
        }
        Spacer(Modifier.height(20.dp))
    }

    if (editingName) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { editingName = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ds.cardBg)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Text("修改昵称", fontSize = 17.sp, color = ds.bodyText)
                Spacer(Modifier.height(12.dp))
                FieldRow(
                    value = draftName,
                    onValueChange = { draftName = it },
                    placeholder = "输入昵称",
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "取消",
                        fontSize = 16.sp,
                        color = ds.subText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { editingName = false }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "保存",
                        fontSize = 16.sp,
                        color = DsBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                editingName = false
                                onChange(s.copy(nickName = draftName.trim()))
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** 圆形头像：有本地图片就显示图片，没有就显示灰底人像。抽屉和设置页共用。 */
@Composable
internal fun AvatarView(path: String, size: androidx.compose.ui.unit.Dp) {
    val ds = LocalDsColors.current
    val file = remember(path) { if (path.isBlank()) null else java.io.File(path) }
    if (file != null && file.exists()) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(ds.tileBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = null,
                tint = ds.placeholder,
                modifier = Modifier.size(size * 0.62f),
            )
        }
    }
}

// =====================================================================================
//  数据管理
// =====================================================================================

@Composable
private fun DataPage(
    s: Settings,
    onChange: (Settings) -> Unit,
    onNavigate: (String) -> Unit,
    onDialog: (String) -> Unit,
    onToast: (String) -> Unit,
    sessionsJson: () -> String,
    onImportSessions: (String) -> Int,
) {
    val ds = LocalDsColors.current
    val ctx = LocalContext.current

    // 导出聊天记录：SAF CreateDocument，默认文件名 dsclone-chats.json，
    // 写进去的就是 sessions.json 的文本（DsApp 提供当前内存里的完整会话列表）。
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val json = sessionsJson()
                val out = ctx.contentResolver.openOutputStream(uri)
                    ?: error("openOutputStream 返回 null")
                out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            Toast.makeText(ctx, if (ok) "已导出" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 导入聊天记录：SAF OpenDocument 选 json → 读成字符串 → 交给 DsApp
    // （DsApp 里用 Store.parseSessions 同一套解析逻辑，按 session.id 去重合并后持久化）
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val json = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            val n = if (json.isNullOrBlank()) 0 else runCatching { onImportSessions(json) }.getOrDefault(0)
            Toast.makeText(ctx, if (n > 0) "已导入 $n 个会话" else "导入失败", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        DsCard(topGap = DsFirstTop) {
            SettingSwitchRow("数据用于优化体验", s.trainOnData, { onChange(s.copy(trainOnData = it)) })
        }
        DsSupporting("允许我们将你的对话内容用于优化 DeepSeek 的使用体验。我们保障你的数据隐私安全。")

        DsCard(topGap = 26.5.dp) {
            SettingRow("我共享的链接", chevron = true, onClick = { onNavigate(SettingsPage.DOC_SHARE) })
        }
        DsCard(topGap = 26.5.dp) {
            SettingRow("导出所有历史对话", chevron = true, onClick = { onNavigate(SettingsPage.DOC_EXPORT) })
        }
        // 本地复刻版新增：直接走系统文件选择器，把 sessions.json 导出 / 导入
        DsCard(topGap = 26.5.dp) {
            SettingRow(
                label = "导出聊天记录",
                chevron = true,
                onClick = { exportLauncher.launch("dsclone-chats.json") },
            )
            SettingRow(
                label = "导入聊天记录",
                chevron = true,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
            )
        }
        DsSupporting("导出的文件就是本机的 sessions.json；导入时同一 id 的会话会被覆盖，其余会话追加到列表最前面。")
        DsCard(topGap = 26.5.dp) {
            SettingRow(
                label = "删除所有历史对话",
                chevron = false,
                labelColor = ds.destructive,
                onClick = { onDialog(SettingsDialog.DELETE_CHATS) },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

// =====================================================================================
//  朗读音色
//
//  官方实现（反编译确认）：com.deepseek.chat.ui.components.blob 里的 FluidBlob，两套渲染层：
//    · 实时层：OpenGL ES —— fp4 = FluidBlobGLRenderer + assets/shaders/fluid_blob.frag
//      （fp4.smali:1263/1271/1279 就是 shaders/fluid_blob_low|medium|.frag，1284 是 .vert）
//    · 静态帧/降级层：AGSL RuntimeShader —— up4.a(Context) + assets/shaders/fluid_blob.agsl
//      up4.smali:77  const-string v0, "shaders/fluid_blob.agsl"
//      up4.smali:89  invoke-static {p0}, Luq0;->b(Ljava/lang/String;)Landroid/graphics/RuntimeShader;
//      sp4.smali:44  a(FFFFLrq4;Lbp4;F)V 里逐个 setFloatUniform（uniform 名见 si8/qi8 枚举）
//  两套 shader 的数学完全一致（fluid_blob.frag 头部注释明说 "GLSL ES 100 port of
//  fluid_blob.agsl. Keep the math in lockstep"），所以这里直接把官方 .agsl 搬进
//  res/raw/voice_blob.agsl，用 RuntimeShader + Paint 画 —— 和 VoiceOverlay.kt 跑
//  voice_mask.agsl 是同一套做法，没有自己画静态渐变。
// =====================================================================================

/**
 * 官方「朗读音色」弹层的每个音色各配一套 FluidColorPalette（smali/rq4.smali），
 * 这里按官方 App 的实测色块给每个音色单独配色。
 *
 * 实测（2026-11-22 13:30，官方 com.deepseek.chat 设置 → 语音 → 朗读音色弹层，
 * 1600x2176 截图，python+PIL 只统计色块区域内 B-R>10 的蓝色系 / 饱和度(max-min)>=24 的像素，
 * 左右翻页各抓 5 帧做动画平均）：
 *   第 1 张「贝壳 / 百变活泼」：平均 #C5DEF6，最暗 2% #8DACE1，最亮 98% #EDF9FF
 *   第 2 张「白浪 / 明朗坚定」：平均 #A7BAEB，最暗 2% #7481C7，最亮 98% #D3E2FF
 * 两张都是蓝色系，第 2 张更深、偏蓝紫（同一位置比第 1 张暗约 40 级）。
 *
 * 三色沿用 shader 的结构：base = 最暗、flow = 平均、highlight = 最亮。
 * （rq4.smali 默认值是 0xFF1A6B94 / 0xFF3899B8 / 0xFFD9EAF5；那是 shader 的输入色，
 *  画面会被 highlight 和玻璃高光整体提亮，所以直接照抄默认值会明显偏深，实测值才对得上官方截图。）
 */
private data class VoicePalette(val base: Color, val flow: Color, val highlight: Color)

/** 第 1 张音色卡（贝壳，浅天蓝）实测配色 */
private val VoicePaletteShell = VoicePalette(
    base = Color(0xFF8DACE1),
    flow = Color(0xFFC5DEF6),
    highlight = Color(0xFFEDF9FF),
)

/** 第 2 张音色卡（白浪，深一档的蓝紫）实测配色 */
private val VoicePaletteWave = VoicePalette(
    base = Color(0xFF7481C7),
    flow = Color(0xFFA7BAEB),
    highlight = Color(0xFFD3E2FF),
)

/**
 * 音色卡 = 一条官方 VoiceOption + 它自己的 palette。
 * VoiceOptions/VoiceOption 定义在 data/Models.kt（本次不动那个文件），
 * 所以在这里按 voice id 把三色绑到每一项上：星光 stella 用第 1 张、潮汐 tide 用第 2 张。
 */
private data class VoiceCard(val option: VoiceOption, val palette: VoicePalette)

private val VoiceCards: List<VoiceCard> = VoiceOptions.map { v ->
    VoiceCard(v, if (v.id == "tide") VoicePaletteWave else VoicePaletteShell)
}

// 色块尺寸/圆角：官方 GL 层用胶囊裁剪，radius = min(width, height) * 0.5
// （assets/shaders/fluid_blob.frag 的 pt_capsuleAlpha），232x104 -> 52dp。
private val VoiceBlobWidth = 232.dp
private val VoiceBlobHeight = 104.dp
private val VoiceBlobRadius = 52.dp

@Composable
private fun VoicePage(s: Settings, onChange: (Settings) -> Unit, onToast: (String) -> Unit) {
    val ctx = LocalContext.current
    val ds = LocalDsColors.current
    val startIndex = VoiceCards.indexOfFirst { it.option.id == s.voiceId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = startIndex) { VoiceCards.size }

    // 试听：选中当前音色 + 落盘，再用 DsTts 读一句例句（失败自动降级系统 TTS）
    val preview: (VoiceOption) -> Unit = { v ->
        val ns = s.copy(voiceId = v.id)
        DsTts.voiceId = v.id
        onChange(ns)
        Store.saveSettings(ctx, ns)
        if (ns.apiKey.isBlank()) onToast("未填 API Key，用系统语音试听")
        DsTts.speak(ctx, ns, -1L, "你好，我是 DeepSeek，很高兴为你服务。") { msg ->
            onToast(msg)
        }
    }

    Column(Modifier.fillMaxWidth()) {

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(196.dp),
        ) { index ->
            val card = VoiceCards[index]
            val v = card.option
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                VoiceBlobFlow(
                    modifier = Modifier
                        .size(VoiceBlobWidth, VoiceBlobHeight)
                        .clip(RoundedCornerShape(VoiceBlobRadius))
                        .clickable { preview(v) },
                    // 每个音色用自己的实测配色（stella = 官方第 1 张，tide = 官方第 2 张）
                    palette = card.palette,
                )
                Spacer(Modifier.height(22.dp))
                Text(text = v.name, fontSize = 16.sp, color = ds.rowText)
                Spacer(Modifier.height(5.dp))
                Text(text = v.desc, fontSize = 13.sp, color = ds.secondary)
            }
        }

        // 页码圆点：官方 PagerIndicator（VoiceSelectContent.kt:378）
        Row(
            Modifier.fillMaxWidth().height(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in VoiceCards.indices) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) DsBlue else ds.chipBorder),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 底部整宽「确认」：文案取官方 voice_switch_confirm（0x7f0f03f2 -> "确认"）
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DsMainMargin)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DsBlue)
                .clickable {
                    val v = VoiceCards[pager.currentPage.coerceIn(0, VoiceCards.lastIndex)].option
                    if (s.voiceId != v.id) {
                        val ns = s.copy(voiceId = v.id)
                        DsTts.voiceId = v.id
                        onChange(ns)
                        Store.saveSettings(ctx, ns)
                    }
                    // 官方 tts_voice_switched_toast：「朗读音色已切换为 %1$s」
                    onToast("朗读音色已切换为 " + v.name)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "确认", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }

        Spacer(Modifier.height(14.dp))
        DsSupporting("点击色块可试听。音色由官方 /v1/tools/tts 提供，目前仅 stella（女声）与 tide（男声）。")
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * 官方 FluidBlob 的 AGSL 层（API >= 33）。参数全部来自官方默认值：
 *
 *  PatternBlobParams（smali/r17.smali:769-883 的 clinit，也就是 Lr17;->A，
 *  同一份也出现在 fp4.smali:90-207 的渲染器默认值里）：
 *    patternSpeed 80.0(0x42a00000) / scale 1.74(0x3fdeb852) / rotation 0.2333333(0x3e6eeeef)
 *    offsetX 0.0 / offsetY 0.8(0x3f4ccccd) / colorCount 3.0(0x40400000)
 *    proportion 0.45(0x3ee66666) / softness 1.0(0x3f800000) / shape CHECKS(0.0, ui8.b)
 *    shapeScale 0.18(0x3e3851ec) / distortion 0.55(0x3f0ccccd) / swirl 0.08(0x3da3d70a)
 *    swirlIterations 10.0(0x41200000)
 *  FluidBlobConfig（同两处）：speed 0.05 / warpStrength 25.0 / brushIntensity 0.15 /
 *    lightMix 0.85 / hueShift 0.015 / grain 0.0 / glassIntensity 0.2
 *  time uniform = 秒 * patternSpeed / 100（sp4.smali:56-74，100.0f = 0x42c80000）→ 0.8 * 秒。
 */
@Composable
private fun VoiceBlobFlow(modifier: Modifier = Modifier, palette: VoicePalette) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density

    val source = remember {
        runCatching {
            ctx.resources.openRawResource(R.raw.voice_blob).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    // AGSL 编译器对非 ASCII 很敏感（之前被 GBK 乱码注释坑过），这里统一清一遍；
    // 我们的 voice_blob.agsl 本来就是纯 ASCII，等于原样。
    val clean = remember(source) {
        source.lineSequence()
            .filter { it.isNotBlank() }
            .joinToString("\n") { line -> line.filter { ch -> ch.code in 32..126 } }
    }
    val shader = remember(clean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || clean.isBlank()) null
        else runCatching { RuntimeShader(clean) }.getOrNull()
    }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

    // 官方实时层是 frame clock 连续累加；这里用 1 小时的线性无限过渡等价模拟，
    // 一秒都不跳（3_600_000ms 走完 0..3600 秒）。
    val transition = rememberInfiniteTransition(label = "voiceBlob")
    val seconds by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_600_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "voiceBlobTime",
    )

    Canvas(modifier) {
        val pal = palette
        val rs = shader
        if (rs == null) {
            // API < 33：官方在这个版本区间走 OpenGL 层，我们没搬 GLSurfaceView，
            // 只能降级成同一组颜色的静态渐变（不动，但颜色/形状一致）。
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(pal.highlight, pal.flow, pal.base),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                ),
            )
            return@Canvas
        }

        rs.setFloatUniform("resolution", size.width, size.height)
        rs.setFloatUniform("pixelRatio", density)
        rs.setFloatUniform("time", seconds * 0.8f)
        rs.setFloatUniform("offset", 0f, 0.8f)
        rs.setFloatUniform("scale", 1.74f)
        rs.setFloatUniform("rotation", 0.2333333f)
        rs.setFloatUniform("colorCount", 3f)
        rs.setFloatUniform("grain", 0f)
        rs.setFloatUniform("proportion", 0.45f)
        rs.setFloatUniform("softness", 1f)
        rs.setFloatUniform("shape", 0f)
        rs.setFloatUniform("shapeScale", 0.18f)
        rs.setFloatUniform("distortion", 0.55f)
        rs.setFloatUniform("swirl", 0.08f)
        rs.setFloatUniform("swirlIterations", 10f)
        rs.setFloatUniform("color1", pal.base.red, pal.base.green, pal.base.blue, 1f)
        rs.setFloatUniform("color2", pal.flow.red, pal.flow.green, pal.flow.blue, 1f)
        rs.setFloatUniform("color3", pal.highlight.red, pal.highlight.green, pal.highlight.blue, 1f)

        paint.shader = rs
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

// =====================================================================================
//  字体大小
// =====================================================================================

@Composable
private fun FontSizePage(s: Settings, onChange: (Settings) -> Unit) {
    val ds = LocalDsColors.current
    val level = s.fontSizeLevel.coerceIn(0, 4)
    // 预览区跟随选择缩放；「跟随系统」时预览用 1.0（真实字号由系统决定）
    val scale = if (s.followSystemFont) 1f else DsFontScales[level]

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 40.dp, end = 40.dp, top = 26.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(ds.userBubble)
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                ) {
                    Text("预览文字大小", fontSize = (17f * scale).sp, lineHeight = (25f * scale).sp, color = ds.rowText)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "你可以拖动滑块来调整字体大小。",
                fontSize = (17f * scale).sp,
                lineHeight = (25f * scale).sp,
                color = ds.rowText,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "如果在使用过程中存在问题或建议，可反馈给 DeepSeek 团队。",
                fontSize = (17f * scale).sp,
                lineHeight = (25f * scale).sp,
                color = ds.rowText,
            )
        }

        DsCard(topGap = 0.dp) {
            SettingSwitchRow("跟随系统", s.followSystemFont, {
                com.yuyuyu.deepseek.ui.theme.DsAppearance.apply(s.appearance, s.fontSizeLevel, it)
                onChange(s.copy(followSystemFont = it))
            })
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 22.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("A", fontSize = 14.sp, color = ds.rowText)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (s.followSystemFont) "标准" else DsFontLevelNames[level],
                            fontSize = 13.sp,
                            color = ds.rowText,
                        )
                    }
                    Text("A", fontSize = 20.sp, color = ds.rowText)
                }
                Spacer(Modifier.height(10.dp))
                LevelSlider(level = level, enabled = true) {
                    // 关键：滑块必须始终可拖。之前是 enabled = !followSystemFont，
                    // 而「跟随系统」默认就是开的 —— 滑块被禁用，怎么拖都没反应，
                    // 用户看到的就是「字体大小调不了」。官方行为是拖了滑块就自动取消跟随系统。
                    com.yuyuyu.deepseek.ui.theme.DsAppearance.apply(s.appearance, it, false)
                    onChange(s.copy(fontSizeLevel = it, followSystemFont = false))
                }
            }
        }
        DsSupporting("开启「跟随系统」时，字号与系统设置保持一致。")
        Spacer(Modifier.height(32.dp))
    }
}

/** 官方字体大小滑块：浅灰轨道 + 5 个刻度 + 白色圆点 */
@Composable
private fun LevelSlider(level: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val density = LocalDensity.current
    val padPx = with(density) { 12.dp.toPx() }
    var widthPx by remember { mutableStateOf(1f) }

    fun pick(x: Float) {
        if (!enabled) return
        val usable = (widthPx - padPx * 2f).coerceAtLeast(1f)
        val frac = ((x - padPx) / usable).coerceIn(0f, 1f)
        onChange((frac * 4f).roundToInt().coerceIn(0, 4))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(enabled) {
                detectTapGestures { pick(it.x) }
            }
            .pointerInput(enabled) {
                detectDragGestures(
                    onDragStart = { pick(it.x) },
                ) { change, _ -> pick(change.position.x) }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val usable = size.width - padPx * 2f
            val trackH = 2.dp.toPx()
            drawRoundRect(
                color = Color(0xFFDEDEDE),
                topLeft = Offset(padPx, cy - trackH / 2f),
                size = Size(usable, trackH),
                cornerRadius = CornerRadius(trackH / 2f),
            )
            for (i in 0..4) {
                val x = padPx + usable * i / 4f
                drawRoundRect(
                    color = Color(0xFFC9C9C9),
                    topLeft = Offset(x - 1.dp.toPx(), cy - 6.dp.toPx()),
                    size = Size(2.dp.toPx(), 12.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            }
            val cx = padPx + usable * level / 4f
            if (cx > 0f) {
                drawCircle(Color(0x1F000000), 14.dp.toPx(), Offset(cx, cy + 1.dp.toPx()))
                drawCircle(Color.White, 13.dp.toPx(), Offset(cx, cy))
                drawCircle(Color(0xFFE2E2E2), 13.dp.toPx(), Offset(cx, cy), style = Stroke(1.dp.toPx()))
            }
        }
    }
}

// =====================================================================================
//  个性化
// =====================================================================================

@Composable
private fun PersonalizationPage(s: Settings, onChange: (Settings) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        DsCard(topGap = DsFirstTop) {
            SettingSwitchRow("思考内容自动折叠", s.autoFoldThinking, { onChange(s.copy(autoFoldThinking = it)) })
        }
        DsSupporting("开启后，思考结束后将自动折叠思考内容")
        Spacer(Modifier.height(32.dp))
    }
}

// =====================================================================================
//  服务协议
// =====================================================================================

@Composable
private fun TermsPage(onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        DsCard(topGap = DsFirstTop) {
            SettingRow("用户协议", chevron = true, onClick = { onNavigate(SettingsPage.DOC_TERMS) })
            SettingRow("隐私政策", chevron = true, onClick = { onNavigate(SettingsPage.DOC_PRIVACY) })
            SettingRow("第三方信息共享清单", chevron = true, onClick = { onNavigate(SettingsPage.DOC_SDK) })
            SettingRow("个人信息收集清单", chevron = true, onClick = { onNavigate(SettingsPage.DOC_INFO) })
            SettingRow("应用权限", chevron = true, onClick = { onNavigate(SettingsPage.DOC_PERM) })
        }
        Spacer(Modifier.height(32.dp))
    }
}

// =====================================================================================
//  登录设备管理
// =====================================================================================

@Composable
private fun DevicesPage() {
    val ds = LocalDsColors.current
    Column(Modifier.fillMaxWidth()) {
        DsCard(topGap = DsFirstTop) {
            Text(
                text = "以下为本账号已登录的设备，点击“退出登录”后，该设备上将退出登录。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = ds.secondary,
                modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 16.dp, bottom = 16.dp),
            )
        }
        DsCard(topGap = 12.dp) {
            SettingRow("当前设备（本机）", icon = "device", value = "登录时间：未知", chevron = true)
        }
        Spacer(Modifier.height(32.dp))
    }
}

// =====================================================================================
//  纯文本页（协议 / 帮助 / 共享链接 / 导出说明）
// =====================================================================================

@Composable
private fun TextPage(text: String) {
    val ds = LocalDsColors.current
    Column(Modifier.fillMaxWidth()) {
        DsCard(topGap = DsFirstTop) {
            Text(
                text = text,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = ds.rowText,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

private val HELP_TEXT: String =
    "常见问题\n\n" +
        "1. 为什么发消息没有回答？\n" +
        "请先在「设置 → API 与模型」里选择服务商、填入 API Key 并选好模型。请求会直接发往你填写的 API 地址。\n\n" +
        "2. 深度思考 / 智能搜索怎么用？\n" +
        "输入框上方的两个胶囊按钮就是开关。深度思考开启后会在请求里带上 thinking；" +
        "智能搜索开启后由 DeepSeek 服务端联网检索，自动附引用（搜索/抓取按次计费）。\n\n" +
        "3. 图片和文件怎么发？\n" +
        "点输入框右侧的「+」，可以拍照、从相册选图或选本地文件。\n\n" +
        "4. 语音朗读用的是哪个音色？\n" +
        "「设置 → 语音 → 朗读音色」里可以切换星光（女声）和潮汐（男声）。\n\n" +
        "联系我们\n" +
        "本应用是本地复刻版，问题请反馈给开发者。"

private val SHARE_TEXT: String =
    "暂无共享的链接\n\n" +
        "你在对话里生成的分享链接会出现在这里，可以查看分享时间并删除。删除链接后，任何人都无法通过该链接访问你分享的对话。"

private val EXPORT_TEXT: String =
    "导出内容中将包含你的账号信息和所有历史对话。导出可能需要一段时间，下载链接的有效期为 7 天。\n\n" +
        "本地复刻版的对话保存在应用私有目录（sessions.json），暂不提供云端导出；如需备份，可以直接复制该文件。"

private fun docBody(page: String): String = when (page) {
    SettingsPage.DOC_TERMS ->
        "欢迎使用 DeepSeek。本页为本地复刻版内置的示例文本，用于展示官方「用户协议」页面入口。\n\n" +
            "1. 服务内容\n" +
            "本应用提供与 AI 模型对话的界面，模型能力由你自行配置的服务商提供。\n\n" +
            "2. 使用规范\n" +
            "请勿利用本应用生成、传播违反法律法规的内容。\n\n" +
            "3. 免责声明\n" +
            "AI 生成内容仅供参考，请自行甄别，并合法使用。"
    SettingsPage.DOC_PRIVACY ->
        "本页为本地复刻版内置的示例文本，用于展示官方「隐私政策」页面入口。\n\n" +
            "1. 我们收集什么\n" +
            "本应用不收集任何个人信息。对话内容、设置项仅保存在本机应用私有目录。\n\n" +
            "2. 数据如何传输\n" +
            "你在「API 与模型」中填写的 API Key 与对话内容，会直接发送到你自己配置的 API 地址。\n\n" +
            "3. 你的权利\n" +
            "卸载应用即可清除全部本地数据。"
    SettingsPage.DOC_SDK ->
        "本页为本地复刻版内置的示例文本，用于展示官方「第三方信息共享清单」页面入口。\n\n" +
            "本应用未集成广告、统计类 SDK。运行时会使用系统自带的相机、相册、麦克风与文字转语音能力，均在你授权后使用。"
    SettingsPage.DOC_INFO ->
        "本页为本地复刻版内置的示例文本，用于展示官方「个人信息收集清单」页面入口。\n\n" +
            "本应用仅在本地保存：设置项（settings.json）、对话记录（sessions.json）。不上传任何数据。"
    SettingsPage.DOC_PERM ->
        "相机：拍照提问时使用。\n" +
            "麦克风：语音输入 / 通话时使用。\n" +
            "存储 / 相册：选择图片、保存图片时使用。\n" +
            "网络：调用你配置的 API 服务。\n\n" +
            "以上权限均只在你主动使用对应功能时申请。"
    SettingsPage.DOC_SHARE -> SHARE_TEXT
    SettingsPage.DOC_EXPORT -> EXPORT_TEXT
    SettingsPage.HELP -> HELP_TEXT
    SettingsPage.LEGAL -> LEGAL_TEXT
    else -> ""
}

// 子页面里也用到的尺寸别名（与 SettingsSheet.kt 保持一致）
private val DsMainMargin = 40.dp
private val DsNarrowMargin = 15.dp
private val DsFirstTop = 9.5.dp
