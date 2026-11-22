package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.data.Providers
import com.yuyuyu.deepseek.data.Settings
import com.yuyuyu.deepseek.data.presetOf
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    settings: Settings,
    onDismiss: () -> Unit,
    onPick: (providerId: String, baseUrl: String, model: String) -> Unit,
    onManage: () -> Unit,
    onModelsFetched: (String, List<String>) -> Unit = { _, _ -> },
    onBalanceCached: (String, String) -> Unit = { _, _ -> },
) {
    val ds = LocalDsColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            // 19 家服务商 + 模型列表 + 手填模型 + 「获取模型列表」+「查询余额」
            // 全部由 data/Providers.kt 驱动（每家 baseUrl / chatPath / 鉴权 / 余额接口都不同）。
            ProviderPickerContent(
                settings = settings,
                onPickProvider = { pid -> onPick(pid, presetOf(pid).baseUrl, settings.model) },
                onPickModel = { pid, m -> onPick(pid, presetOf(pid).baseUrl, m) },
                onModelsFetched = onModelsFetched,
                onBalanceCached = onBalanceCached,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "管理 Key",
                    fontSize = 13.sp,
                    color = DsBlue,
                    modifier = Modifier.clickable(onClick = onManage),
                )
            }
        }
    }
}
