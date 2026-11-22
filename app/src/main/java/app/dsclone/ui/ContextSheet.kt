package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.data.Msg
import com.yuyuyu.deepseek.data.Settings
import com.yuyuyu.deepseek.data.estimateTokens
import com.yuyuyu.deepseek.net.Client
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSheet(
    settings: Settings,
    messages: List<Msg>,
    onDismiss: () -> Unit,
) {
    val ds = LocalDsColors.current
    val clipboard = LocalClipboardManager.current
    val sent = messages.filter { (it.role == "user" || it.role == "assistant") && it.text.isNotBlank() }
    val tokens = sent.sumOf { estimateTokens(it.text) + 4 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "本轮上下文",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "复制 JSON",
                    fontSize = 13.sp,
                    color = DsBlue,
                    modifier = Modifier.clickable {
                        clipboard.setText(
                            AnnotatedString(Client.payloadJson(settings, messages).toString(2))
                        )
                    },
                )
            }
            Text(
                text = "${sent.size} 条消息 · 约 $tokens tokens · ${settings.provider.name} / ${settings.model}",
                fontSize = 12.sp,
                color = ds.subText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            HorizontalDivider(color = ds.divider)
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sent, key = { it.id }) { m ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(ds.chipBg, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = if (m.role == "user") "用户" else "助手",
                            fontSize = 11.sp,
                            color = if (m.role == "user") DsBlue else ds.subText,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = m.text,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "只统计已发送的文本；图片/文件未计入。",
                    fontSize = 11.sp,
                    color = ds.subText,
                )
            }
        }
    }
}
