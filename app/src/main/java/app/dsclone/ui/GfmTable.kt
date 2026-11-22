package com.yuyuyu.deepseek.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuyuyu.deepseek.R
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

/**
 * 官方 GFM 表格容器。
 *
 * 结构照 `com.deepseek.chat.ui.markdown.components.GFMTable.kt`（smali 里的 e35/g35）：
 *   · 标题栏高度 **46dp**，横向 padding 起 8dp、末 4dp（`wca.b(…,0f,46f)` + `b08.T(…,8f)` + `b08.U(…,8,8,4,8)`）
 *   · 标题文字用 `markdown_table_title` = 「表格」
 *   · 两个 16dp 图标：`ic_download_outline`、`ic_fullscreen_outline`（`wca.n(painter, 16f)`）
 * 图标自带的灰是 #7F8287，官方没有额外 tint，所以这里也保持原色。
 */
@Composable
fun GfmTable(header: List<String>, rows: List<List<String>>) {
    val ds = LocalDsColors.current
    val ctx = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }

    val export = {
        val sb = StringBuilder()
        sb.append(header.joinToString(",") { csvCell(it) }).append('\n')
        rows.forEach { r -> sb.append(r.joinToString(",") { csvCell(it) }).append('\n') }
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }
            ctx.startActivity(Intent.createChooser(send, "表格"))
        }.onFailure {
            Toast.makeText(ctx, "表格导出失败", Toast.LENGTH_SHORT).show()
        }
        Unit
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ds.cardBg)
            .border(1.dp, ds.chipBorder, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("表格", fontSize = 15.sp, color = ds.bodyText, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).clickable { export() },
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_download_outline), null, Modifier.size(16.dp))
            }
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).clickable { fullscreen = true },
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_fullscreen_outline), null, Modifier.size(16.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ds.chipBorder))
        TableGrid(header, rows, compact = true)
    }

    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(ds.cardBg)
                    .padding(top = 12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("表格", fontSize = 17.sp, color = ds.bodyText, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).clickable { export() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(R.drawable.ic_download_outline), null, Modifier.size(16.dp))
                    }
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).clickable { fullscreen = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("关闭", fontSize = 15.sp, color = DsBlue)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(ds.chipBorder))
                TableGrid(header, rows, compact = false)
            }
        }
    }
}

@Composable
private fun TableGrid(header: List<String>, rows: List<List<String>>, compact: Boolean) {
    val ds = LocalDsColors.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val fontSize = if (compact) 14.sp else 15.sp
    val hPad = if (compact) 12.dp else 16.dp
    val vPad = if (compact) 10.dp else 12.dp
    // 官方单元格是单行、等高，宽了整体横向滚动
    val rowH = if (compact) 48.dp else 52.dp
    val maxW = if (compact) 160.dp else 240.dp
    val minW = 64.dp

    // 列宽按内容量出来，超过上限就截断（官方也是定宽表格，不换行）
    val cols = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    val widths = remember(header, rows, compact) {
        // 表头是 SemiBold，量宽也用 SemiBold，否则表头会被截成 "Moo…"
        val style = androidx.compose.ui.text.TextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold)
        (0 until cols).map { c ->
            val widest = (listOf(header.getOrElse(c) { "" }) + rows.map { it.getOrElse(c) { "" } })
                .maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
            with(density) { widest.toDp() } + hPad * 2
        }.map { it.coerceIn(minW, maxW) }
    }

    val scroll = rememberScrollState()

    Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Column(Modifier.drawBehind {
            // 竖向网格线
            var x = 0f
            for (w in widths) {
                x += w.toPx()
                if (x < size.width) drawLine(ds.chipBorder, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
        }) {
            TableRow(
                cells = header,
                widths = widths,
                rowH = rowH,
                fontSize = fontSize,
                hPad = hPad,
                vPad = vPad,
                bg = ds.tileBg,
                weight = FontWeight.SemiBold,
                color = ds.bodyText,
                drawBottom = true,
            )
            rows.forEachIndexed { idx, r ->
                TableRow(
                    cells = r,
                    widths = widths,
                    rowH = rowH,
                    fontSize = fontSize,
                    hPad = hPad,
                    vPad = vPad,
                    bg = Color.Transparent,
                    weight = FontWeight.Normal,
                    color = ds.bodyText,
                    drawBottom = idx != rows.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<androidx.compose.ui.unit.Dp>,
    rowH: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    hPad: androidx.compose.ui.unit.Dp,
    vPad: androidx.compose.ui.unit.Dp,
    bg: Color,
    weight: FontWeight,
    color: Color,
    drawBottom: Boolean,
) {
    val ds = LocalDsColors.current
    val border = ds.chipBorder
    Row(
        Modifier
            .height(rowH)
            .background(bg)
            .drawBehind {
                if (drawBottom) drawLine(border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
        // 官方单元格文字在行内垂直居中（此前是顶对齐）
        verticalAlignment = Alignment.CenterVertically,
    ) {
        widths.forEachIndexed { idx, w ->
            Text(
                text = inlineMarkdown(cells.getOrElse(idx) { "" }, ds.codeBg),
                fontSize = fontSize,
                lineHeight = fontSize * 1.45f,
                fontWeight = weight,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(w)
                    .padding(horizontal = hPad),
            )
        }
    }
}

private fun csvCell(s: String): String {
    val t = s.replace("\n", " ")
    return if (t.any { it == ',' || it == '"' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
}
