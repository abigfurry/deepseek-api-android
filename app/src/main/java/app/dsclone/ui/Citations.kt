package com.yuyuyu.deepseek.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.R
import com.yuyuyu.deepseek.data.Source
import com.yuyuyu.deepseek.ui.theme.LocalDsColors
import java.net.URI

/**
 * 联网搜索「出处」相关 UI / 打开逻辑（2026-11-22 对照官方 2.9.x 反编译补）：
 *
 * 官方行为（smali 出处）：
 *  - 回答下方「N 个网页」药丸：AssistantSearchSummary.kt（Lg41;->J），
 *    内部对来源列表 `take(4)` 显示最多 4 个小圆标（Lv13;->c1，常量 4），
 *    圆标为服务端下发的 site_icon（MessageSearchResult.site_icon，cdn.deepseek.com 上的 png），
 *    药丸内边距 14/6/12/6 dp、文字 15sp/24（官方 R.string.message_search_summary = "%d 个网页"）。
 *  - 点药丸 / 点引用：不弹系统浏览器，而是 navigate 到内部页面
 *    `com.deepseek.chat.ui.pages.SearchResultWebViewRoute`（tq9.smali 的 @Serializable 路由，
 *    字段 url/queries/chatSessionId/...），由 SearchResultWebViewPage.kt（Le67;->d）渲染：
 *    内部 WebView（Lrq9）+ 搜索结果列表（AssistantSearchResultItem.kt，Lh11;->P，
 *    行 = 标题 17sp/26 + 4dp + ic_chevron_right_outline_20）。
 *  - 打开前清洗 URL（bz.smali Lbz;->b）：只接受 http/https；没有 scheme 时补 "https://"；
 *    非 http/https（tel:/mailto: 等）直接丢弃。外部浏览器兜底 Lbz;->a 用 ACTION_VIEW，
 *    失败时 Toast "Can't open %s."。
 *
 * 本克隆没有导航到内嵌 WebView 页的容器，所以这里用「底部 sheet 列全部出处 + 点行进系统浏览器」，
 * 尺寸/字号按官方 SearchResult 列表与药丸实测值自绘；没有浏览器时 Toast 提示，不崩。
 */
private val SOURCE_PALETTE = listOf(
    Color(0xFF4C8DF6), Color(0xFF34A853), Color(0xFFF5A623), Color(0xFFE0507A),
    Color(0xFF7B61FF), Color(0xFF00A6A6), Color(0xFFEA6B3E), Color(0xFF5F6368),
)

/** 来源展示用的站点名：优先 siteName，否则 URL 的 host（去掉 www.），都没有就返回原 URL */
fun sourceSiteName(src: Source): String {
    if (src.siteName.isNotBlank()) return src.siteName
    val host = runCatching { URI(src.url).host.orEmpty() }.getOrDefault("")
        .removePrefix("www.")
    return host.ifBlank { src.url }
}

/** 来源小圆标的底色/首字母键：siteName > host > title，和 ChatParts.SourceAvatars 的兜底一致 */
fun sourceColorIndex(src: Source): Int {
    val key = sourceSiteName(src).ifBlank { src.title }
    return (key.hashCode().let { if (it < 0) -it else it }) % SOURCE_PALETTE.size
}

/**
 * 打开一条出处。按官方 bz.smali 的规则清洗 URL：
 *  - 空 → 不打开；
 *  - 没有 scheme → 补 https://；
 *  - 只允许 http/https（官方把非 http(s) 的 scheme 直接丢弃）；
 *  - 用系统浏览器（ACTION_VIEW）打开；没有浏览器 / 系统拒绝时 Toast，绝不抛到 Compose。
 */
fun openSource(ctx: Context, url: String) {
    val raw = url.trim()
    if (raw.isEmpty()) {
        Toast.makeText(ctx, "这条出处没有链接", Toast.LENGTH_SHORT).show()
        return
    }
    val cleaned = when {
        raw.startsWith("//") -> "https:$raw"
        !raw.contains("://") -> "https://$raw"
        else -> raw
    }
    val uri = runCatching { Uri.parse(cleaned) }.getOrNull()
    val scheme = uri?.scheme?.lowercase()
    if (uri == null || scheme != "http" && scheme != "https") {
        Toast.makeText(ctx, "不支持的链接类型：$raw", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "没有可用的浏览器，无法打开链接", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "无法打开链接：${e.message ?: cleaned}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 出处列表底部弹层（按官方 SearchResult 列表样式自绘）。
 * 每行：站点 icon（site_icon 圆图，缺省 = 彩色圆 + 首字母）+ 标题 + 域名/发布时间 + 右侧 chevron，
 * 点一行 → [openSource]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceListSheet(
    sources: List<Source>,
    onDismiss: () -> Unit,
) {
    val ds = LocalDsColors.current
    val ctx = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ds.cardBg,
        contentColor = ds.bodyText,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = ds.divider) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 标题行：官方搜索结果是整页「搜索结果」；这里压缩成 sheet 标题「N 个网页」
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${sources.size} 个网页",
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ds.bodyText,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_outline_20),
                        contentDescription = "关闭",
                        tint = ds.subText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
            ) {
                itemsIndexed(sources) { index, src ->
                    SourceRow(
                        src = src,
                        showDivider = index != sources.lastIndex,
                        onClick = { openSource(ctx, src.url) },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

/** 单行出处：官方 AssistantSearchResultItem 行尺寸（标题 17sp/26、chevron 20dp、行内边距 16/12） */
@Composable
private fun SourceRow(src: Source, showDivider: Boolean, onClick: () -> Unit) {
    val ds = LocalDsColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceIcon(src, 20.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = src.title.ifBlank { sourceSiteName(src) },
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = ds.bodyText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (src.snippet.isNotBlank()) {
                    Text(
                        text = src.snippet,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = ds.subText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sourceSiteName(src),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = ds.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (src.publishTime.isNotBlank()) {
                        Text(
                            text = "·",
                            fontSize = 12.sp,
                            color = ds.chevron,
                        )
                        Text(
                            text = src.publishTime,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = ds.secondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_outline_20),
                contentDescription = null,
                tint = ds.chevron,
                modifier = Modifier.size(20.dp),
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp)
                    .height(0.5.dp)
                    .background(ds.divider)
            )
        }
    }
}

/** 站点 icon：官方用服务端 site_icon（圆图）；没有就彩色圆 + 站点名首字母（和药丸小圆标同款） */
@Composable
private fun SourceIcon(src: Source, size: androidx.compose.ui.unit.Dp) {
    val color = SOURCE_PALETTE[sourceColorIndex(src)]
    val letter = sourceSiteName(src).firstOrNull()?.uppercaseChar()?.toString() ?: "•"
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(0.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (src.siteIcon.isNotBlank()) {
            coil.compose.AsyncImage(
                model = src.siteIcon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = letter,
                color = Color.White,
                fontSize = (size.value * 0.62f).sp,
                lineHeight = (size.value * 0.62f).sp,
            )
        }
    }
}
