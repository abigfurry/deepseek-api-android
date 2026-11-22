package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.data.BalanceResult
import com.yuyuyu.deepseek.data.ProviderApi
import com.yuyuyu.deepseek.data.ProviderPresets
import com.yuyuyu.deepseek.data.Settings
import com.yuyuyu.deepseek.data.balanceCacheEntry
import com.yuyuyu.deepseek.data.balanceCacheJson
import com.yuyuyu.deepseek.data.balanceDetailLines
import com.yuyuyu.deepseek.data.balanceDisplayText
import com.yuyuyu.deepseek.data.displayText
import com.yuyuyu.deepseek.data.presetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 多 provider 的模型/服务商选择器（独立 Composable，不依赖本工程其它 UI 文件）。
 *
 * 数据驱动：列表内容全部来自 data/Providers.kt（ProviderPresets / presetOf / mergedModels 的等价逻辑），
 * 网络请求全部在 Dispatchers.IO 上跑，不阻塞主线程；拉取失败只显示错误文案，不清空已有模型。
 *
 * 接线方式（后续由 UI 层决定）：
 *   ProviderPickerDialog(
 *       settings = settings,
 *       onDismiss = { showPicker = false },
 *       onPickProvider = { pid -> settings = settings.copy(providerId = pid) },          // 需要持久化就 Store.saveSettings
 *       onPickModel = { pid, m -> settings = settings.copy(providerId = pid, model = m) },
 *       onModelsFetched = { pid, models ->                                          // 缓存到 Settings.providerModels
 *           settings = settings.copy(providerModels = settings.providerModels + (pid to models))
 *       },
 *       onBalanceCached = { pid, cache ->                                           // 缓存到 Settings.balanceCache
 *           settings = settings.copy(balanceCache = settings.balanceCache + (pid to cache))
 *       },
 *   )
 */

/** 独立的选择面板：服务商列表 + 当前服务商的模型/余额/拉取按钮 */
@Composable
fun ProviderPickerContent(
    settings: Settings,
    onPickProvider: (String) -> Unit,
    onPickModel: (providerId: String, model: String) -> Unit,
    onModelsFetched: (providerId: String, models: List<String>) -> Unit = { _, _ -> },
    onBalanceCached: (providerId: String, cache: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val providerId = settings.providerId
    val preset = presetOf(providerId)
    val apiKey = settings.keys[providerId].orEmpty()
    val scope = rememberCoroutineScope()

    // 网络状态（只在内存里，不进 Settings 草稿，避免一改就触发全量 save）
    var loadingModels by remember(providerId) { mutableStateOf(false) }
    var modelsError by remember(providerId) { mutableStateOf<String?>(null) }
    var fetchedModels by remember(providerId) { mutableStateOf<List<String>?>(null) }
    var loadingBalance by remember(providerId) { mutableStateOf(false) }
    var balanceResult by remember(providerId) { mutableStateOf<BalanceResult?>(null) }
    var manualModel by remember(providerId) { mutableStateOf("") }

    // 手填模型永远置顶 → 本次拉取结果（未落盘也能立刻看到）→ Settings 里的缓存 → 预设兜底
    val models = remember(settings, providerId, fetchedModels) {
        val out = LinkedHashSet<String>()
        settings.customModels[providerId].orEmpty().forEach { m ->
            if (m.isNotBlank()) out.add(m.trim())
        }
        (fetchedModels ?: settings.providerModels[providerId].orEmpty()).forEach { m ->
            if (m.isNotBlank()) out.add(m.trim())
        }
        preset.fallbackModels.forEach { m ->
            if (m.isNotBlank()) out.add(m.trim())
        }
        out.toList()
    }

    val cachedBalance = remember(settings.balanceCache, providerId) {
        balanceCacheEntry(settings.balanceCache[providerId])
    }
    val currentBalance = balanceResult
    val balanceText = when {
        currentBalance != null -> balanceDisplayText(currentBalance)
        preset.balance == null -> "余额 —（该提供商无余额接口）"
        cachedBalance != null -> cachedBalance.text
        else -> "余额 —（未查询）"
    }
    val balanceLines = currentBalance?.let { balanceDetailLines(it) }.orEmpty()

    Column(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                text = "选择服务商与模型",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "手填模型置顶 · 拉取失败不会清空已有列表",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "服务商",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = ProviderPresets.size.toString() + " 家",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(ProviderPresets.size) { pi ->
                val p = ProviderPresets[pi]
                val selected = p.id == providerId
                val hasKey = settings.keys[p.id].orEmpty().isNotBlank()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPickProvider(p.id) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = p.name,
                        fontSize = 14.sp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    )
                    if (selected) {
                        Text(
                            text = " 当前",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (hasKey) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                    }
                }
            }
            item {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            item {
                Text(
                    text = "API 地址：" + settings.effectiveBaseUrl.ifBlank { "未填写" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = balanceText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (balanceLines.isNotEmpty()) {
                            Text(
                                text = balanceLines.joinToString(" · ") { it.first + " " + it.second },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (currentBalance == null && cachedBalance != null) {
                            Text(
                                text = "缓存 · " + cacheTimeText(cachedBalance.timeMillis),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(
                        enabled = !loadingBalance,
                        onClick = {
                            loadingBalance = true
                            scope.launch {
                                // 同步网络调用放到 IO；10s 超时，失败返回 Failed 不抛异常
                                val r = withContext(Dispatchers.IO) {
                                    ProviderApi.fetchBalance(preset, apiKey, settings.effectiveBaseUrl)
                                }
                                loadingBalance = false
                                balanceResult = r
                                if (r is BalanceResult.Money) {
                                    onBalanceCached(
                                        providerId,
                                        balanceCacheJson(
                                            r.displayText(),
                                            r.lines.joinToString("；") { it.first + " " + it.second },
                                        ),
                                    )
                                }
                            }
                        },
                    ) {
                        Text(
                            text = if (loadingBalance) "查询中…" else "查询余额",
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "模型",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = !loadingModels,
                        onClick = {
                            loadingModels = true
                            modelsError = null
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    ProviderApi.fetchModels(preset, apiKey, settings.effectiveBaseUrl)
                                }
                                loadingModels = false
                                val ids = r.models.map { it.id }
                                if (r.ok && ids.isNotEmpty()) {
                                    fetchedModels = ids
                                    onModelsFetched(providerId, ids)
                                } else {
                                    // 失败不清空：不碰 providerModels，只显示错误
                                    modelsError = r.error ?: "未获取到模型"
                                }
                            }
                        },
                    ) {
                        Text(
                            text = if (loadingModels) "拉取中…" else "获取模型列表",
                            fontSize = 13.sp,
                        )
                    }
                }
                val err = modelsError
                if (err != null) {
                    Text(
                        text = err,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
            }
            items(models.size) { mi ->
                val m = models[mi]
                val isCurrent = settings.model == m
                val isCustom = settings.customModels[providerId].orEmpty().contains(m)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPickModel(providerId, m) }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = m,
                        fontSize = 14.sp,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    if (isCustom) {
                        Text(
                            text = "自定义",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    if (isCurrent) {
                        Text(
                            text = "当前",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manualModel,
                        onValueChange = { manualModel = it },
                        singleLine = true,
                        label = { Text("手填模型名", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = manualModel.isNotBlank(),
                        onClick = {
                            val m = manualModel.trim()
                            if (m.isNotEmpty()) onPickModel(providerId, m)
                        },
                    ) {
                        Text(text = "使用", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** 独立的底部弹窗版本；面板内部自带网络状态，不需要外部传 loading。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderPickerDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onPickProvider: (String) -> Unit,
    onPickModel: (providerId: String, model: String) -> Unit,
    onModelsFetched: (providerId: String, models: List<String>) -> Unit = { _, _ -> },
    onBalanceCached: (providerId: String, cache: String) -> Unit = { _, _ -> },
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ProviderPickerContent(
            settings = settings,
            onPickProvider = onPickProvider,
            onPickModel = onPickModel,
            onModelsFetched = onModelsFetched,
            onBalanceCached = onBalanceCached,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 余额缓存的「几点几分」文案 */
private fun cacheTimeText(timeMillis: Long): String {
    if (timeMillis <= 0L) return ""
    return try {
        SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(timeMillis))
    } catch (e: Exception) {
        ""
    }
}
