package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.data.ProviderApi
import com.yuyuyu.deepseek.data.ProviderPreset
import com.yuyuyu.deepseek.data.ProviderPresets
import com.yuyuyu.deepseek.data.Settings
import com.yuyuyu.deepseek.data.Store
import com.yuyuyu.deepseek.data.presetOf
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.LocalDsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 开屏页：直接填 API Key（不登录账号）。
 *
 * 官方 App 是手机号/微信登录 + 服务端额度，我们这个复刻版没有服务端，
 * 所以首屏改成「填自己的 Key」——填完立刻进入聊天，Key 只落在本机 settings.json。
 *
 * 首屏同时也是「第一次打开就能把服务商/模型配好」的入口（用户需求：初始页面的模型设置）：
 *   服务商  <名称>  ›   → 底部弹窗选 19 家预设，选中后 Key / 地址 / 模型换成该家默认值
 *   模型    <名称>  ›   → 底部弹窗列出 手填模型 + 拉取缓存 + 预设兜底，并可「自动获取模型列表」
 *   Key 输入框下方还有「没有 Key？去 XX 申请 ›」，用系统浏览器打开该家的 keyUrl。
 * 拉到的模型先放内存，点「开始使用」时一起写进 Settings.providerModels（下次进来就有缓存）。
 */
@Composable
fun ApiKeySetupScreen(
    initial: Settings,
    onDone: (Settings) -> Unit,
) {
    val ds = LocalDsColors.current
    val ctx = LocalContext.current
    val initialPid = initial.providerId

    // 每家 provider 的草稿都放在以 providerId 为 key 的 map 里：
    // 换了服务商再换回来，刚填的 Key / 地址 / 模型不会丢（还没保存而已）。
    var providerId by remember { mutableStateOf(initialPid) }
    var keyMap by remember { mutableStateOf(initial.keys) }
    var baseMap by remember {
        mutableStateOf(initial.baseUrls + (initialPid to initial.effectiveBaseUrl))
    }
    var modelMap by remember { mutableStateOf(mapOf(initialPid to initial.model)) }
    // 本次开屏页里「自动获取模型列表」拿到的结果：providerId -> 模型 ID（点开始使用时落盘）
    var fetchedMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var reveal by remember { mutableStateOf(false) }
    var showProviderSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }

    val preset = remember(providerId) { presetOf(providerId) }
    val key = keyMap[providerId].orEmpty()
    val base = baseMap[providerId] ?: preset.baseUrl
    val model = modelMap[providerId]
        ?: if (providerId == initialPid) initial.model
        else preset.fallbackModels.firstOrNull() ?: initial.model
    val applyUrl = remember(providerId) { providerKeyUrl(preset) }
    // 「API 使用教程」弹窗（首次使用的人不知道去哪拿 Key / 怎么充值）
    var showApiGuide by remember { mutableStateOf(false) }


    // 模型候选：手填置顶 → 本次拉取结果 → 上次拉取缓存 → 预设兜底（与 data/Providers.kt 的顺序一致）
    fun candidateModels(pid: String): List<String> {
        val out = LinkedHashSet<String>()
        fun addAll(list: List<String>?) {
            for (m in list.orEmpty()) {
                val t = m.trim()
                if (t.isNotEmpty()) out.add(t)
            }
        }
        addAll(initial.customModels[pid])
        addAll(fetchedMap[pid])
        addAll(initial.providerModels[pid])
        addAll(presetOf(pid).fallbackModels)
        return out.toList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ds.pageBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        Text(
            text = "填入 API Key 开始使用",
            fontSize = 24.sp,
            color = ds.bodyText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "不需要登录账号，用自己的 Key 就能对话。\nKey 只保存在本机，不会经过任何中转服务器。",
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = ds.secondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // ---- 模型设置：服务商 / 模型 两行（首屏就能配好，不用先进设置页）----
        PickerRow(
            label = "服务商",
            value = preset.name,
            onClick = { showProviderSheet = true },
        )
        Spacer(Modifier.height(10.dp))
        PickerRow(
            label = "模型",
            value = model.ifBlank { "未设置" },
            onClick = { showModelSheet = true },
        )

        Spacer(Modifier.height(24.dp))

        // API Key
        Column(Modifier.fillMaxWidth()) {
            Text("API Key（" + preset.name + "）", fontSize = 13.sp, color = ds.secondary)
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ds.cardBg)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                if (key.isEmpty()) {
                    Text("sk-…", fontSize = 16.sp, color = ds.placeholder)
                }
                BasicTextField(
                    value = key,
                    onValueChange = { keyMap = keyMap + (providerId to it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = ds.rowText),
                    cursorBrush = SolidColor(DsBlue),
                    visualTransformation =
                        if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = if (reveal) "隐藏" else "显示",
                    fontSize = 13.sp,
                    color = ds.secondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { reveal = !reveal }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            // 没有 Key？直接去这家服务商的控制台申请（系统浏览器打开，失败只 Toast）
            Text(
                text = "没有 Key？去 " + preset.name + " 申请 ›",
                fontSize = 13.sp,
                color = DsBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { openUrl(ctx, applyUrl) }
                    .padding(vertical = 5.dp, horizontal = 2.dp),
            )
            // API 使用教程：怎么拿 Key、怎么充值、常见报错（应用内弹窗，不跳浏览器）
            Text(
                text = "API 使用教程（拿 Key · 充值 · 报错）›",
                fontSize = 13.sp,
                color = DsBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showApiGuide = true }
                    .padding(vertical = 5.dp, horizontal = 2.dp),
            )
        }

        if (showApiGuide) ApiGuideDialog(onDismiss = { showApiGuide = false })

        Spacer(Modifier.height(14.dp))

        // API 地址（可换成兼容 OpenAI 的中转地址）
        Column(Modifier.fillMaxWidth()) {
            Text("API 地址", fontSize = 13.sp, color = ds.secondary)
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ds.cardBg)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                if (base.isEmpty()) {
                    Text(preset.baseUrl.ifBlank { "https://api.example.com/v1" }, fontSize = 16.sp, color = ds.placeholder)
                }
                BasicTextField(
                    value = base,
                    onValueChange = { baseMap = baseMap + (providerId to it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = ds.rowText),
                    cursorBrush = SolidColor(DsBlue),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(34.dp))

        val ready = key.isNotBlank()
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(if (ready) DsBlue else ds.tileBg)
                .clickable(enabled = ready) {
                    val k = key.trim()
                    val b = base.trim()
                    val pid = providerId
                    val m = model.trim().ifBlank {
                        candidateModels(pid).firstOrNull() ?: initial.model
                    }
                    // 本次开屏页拉到的模型一起写进缓存（失败不清空：没拉到就没有这个 key）
                    val providerModels = initial.providerModels.toMutableMap()
                    for ((fp, list) in fetchedMap) {
                        if (list.isNotEmpty()) providerModels[fp] = list.distinct().take(500)
                    }
                    val s = initial.copy(
                        providerId = pid,
                        model = m,
                        keys = initial.keys + (pid to k),
                        baseUrl = b.ifBlank { preset.baseUrl },
                        baseUrls = if (b.isNotBlank() && b != preset.baseUrl) {
                            initial.baseUrls + (pid to b)
                        } else {
                            initial.baseUrls - pid
                        },
                        providerModels = providerModels,
                    )
                    Store.saveSettings(ctx, s)
                    onDone(s)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "开始使用",
                fontSize = 17.sp,
                color = if (ready) Color.White else ds.secondary,
            )
        }

        Spacer(Modifier.height(26.dp))
        Text(
            text = "Key 在 deepseek 开放平台 / 各家服务商控制台获取",
            fontSize = 12.sp,
            color = ds.placeholder,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
    }

    // ---- 服务商选择弹窗：19 家预设，选中即回填 ----
    if (showProviderSheet) {
        ProviderPickerSheet(
            currentId = providerId,
            onDismiss = { showProviderSheet = false },
            onPick = { p ->
                providerId = p.id
                showProviderSheet = false
            },
        )
    }

    // ---- 模型选择弹窗：候选列表 + 自动获取模型列表 ----
    if (showModelSheet) {
        ModelPickerSheet(
            preset = preset,
            current = model,
            models = candidateModels(providerId),
            apiKey = key,
            baseUrl = base.ifBlank { preset.baseUrl },
            onDismiss = { showModelSheet = false },
            onPick = { m ->
                modelMap = modelMap + (providerId to m)
                showModelSheet = false
            },
            onFetched = { list ->
                fetchedMap = fetchedMap + (providerId to list)
            },
        )
    }
}

/** 开屏页的一行「标签 + 当前值 + ›」，视觉沿用本页圆角 14dp 卡片。 */
@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    val ds = LocalDsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ds.cardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = ds.rowText, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 15.sp,
            color = ds.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text("›", fontSize = 17.sp, color = ds.chevron, modifier = Modifier.padding(start = 6.dp))
    }
}

/** 服务商选择弹窗（19 家，来自 data/Providers.kt 的 ProviderPresets）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPickerSheet(
    currentId: String,
    onDismiss: () -> Unit,
    onPick: (ProviderPreset) -> Unit,
) {
    val ds = LocalDsColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ds.pageBg) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                text = "选择服务商",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = ds.bodyText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Text(
                text = "共 " + ProviderPresets.size + " 家 · 选中后 Key / 地址 / 模型切换成该家的默认值",
                fontSize = 12.sp,
                color = ds.secondary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(ProviderPresets) { p ->
                    val selected = p.id == currentId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p) }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = p.name,
                            fontSize = 15.sp,
                            color = if (selected) DsBlue else ds.bodyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text("当前", fontSize = 12.sp, color = DsBlue)
                        }
                    }
                }
            }
        }
    }
}

/** 模型选择弹窗：候选列表 + 「自动获取模型列表」（网络在 Dispatchers.IO）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    preset: ProviderPreset,
    current: String,
    models: List<String>,
    apiKey: String,
    baseUrl: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onFetched: (List<String>) -> Unit,
) {
    val ds = LocalDsColors.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ds.pageBg) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("选择模型", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ds.bodyText)
                    Text(
                        text = preset.name + " · 共 " + models.size + " 个",
                        fontSize = 12.sp,
                        color = ds.secondary,
                    )
                }
                Text(
                    text = if (loading) "获取中…" else "自动获取模型列表",
                    fontSize = 13.sp,
                    color = if (loading) ds.secondary else DsBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !loading) {
                            if (preset.modelsPath.isNullOrBlank()) {
                                error = "该服务商没有模型列表接口"
                            } else {
                                loading = true
                                error = null
                                info = null
                                scope.launch {
                                    // fetchModels 是同步阻塞调用，放 IO；10 秒超时、失败返回 ModelsResult(error)
                                    val r = withContext(Dispatchers.IO) {
                                        ProviderApi.fetchModels(preset, apiKey, baseUrl)
                                    }
                                    loading = false
                                    val ids = r.models.map { it.id }
                                    if (r.ok && ids.isNotEmpty()) {
                                        onFetched(ids)
                                        info = "已获取 " + ids.size + " 个模型"
                                    } else {
                                        error = "获取失败：" + (r.error ?: "未获取到模型")
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                )
            }
            val err = error
            val tip = info
            if (err != null) {
                Text(
                    text = err,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = ds.destructive,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                )
            } else if (tip != null) {
                Text(
                    text = tip,
                    fontSize = 12.sp,
                    color = ds.secondary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                if (models.isEmpty()) {
                    item {
                        Text(
                            text = "还没有可选模型，点右上角「自动获取模型列表」拉一次。",
                            fontSize = 13.sp,
                            color = ds.secondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
                items(models) { m ->
                    val selected = m == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(m) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = m,
                            fontSize = 15.sp,
                            color = if (selected) DsBlue else ds.bodyText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text("当前", fontSize = 12.sp, color = DsBlue)
                        }
                    }
                }
            }
        }
    }
}
