package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.ui.theme.LocalDsColors
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * 公式渲染：官方 2.9.0 用的是 **JLaTeXMath**（`JLatexMathInitProvider`），
 * 所以这里也换成 `ru.noties:jlatexmath-android`，和官方同一套排版引擎。
 *
 * 旧实现是「每个公式一个 WebView + KaTeX」：流式输出时每个 token 都会重建一次 WebView，
 * 一次回答里几个公式就能卡好几秒（用户反馈的「公式渲染太慢」就是这个）。
 * JLaTeXMath 是纯 Java/Canvas 绘制，一次 drawable 构建 ~几毫秒，按 latex 字符串缓存后
 * 重绘几乎不花钱；流式期间由 MarkdownView 决定先显示原始 latex（见 `streaming` 参数）。
 *
 * 注意：JLatexMath 初始化靠 AAR 自带的 ContentProvider（JLatexMathInitProvider），
 * 首次调用前就会把数学字体装好，不需要手动 init。
 */
@Composable
fun MathText(latex: String, display: Boolean = true, modifier: Modifier = Modifier) {
    val ds = LocalDsColors.current
    val density = LocalDensity.current
    val textSizePx = with(density) { 17.sp.toPx() }
    val color = ds.bodyText.toArgb()
    val drawable = remember(latex, textSizePx, color, display) {
        runCatching {
            JLatexMathDrawable.builder(latex)
                .textSize(textSizePx)
                .color(color)
                .align(if (display) JLatexMathDrawable.ALIGN_CENTER else JLatexMathDrawable.ALIGN_LEFT)
                .padding(4, 2, 4, 2)
                .build()
        }.getOrNull()
    }
    if (drawable == null) {
        // 公式语法错误 / 引擎异常：退化成原文，至少不丢内容
        Text(
            text = latex,
            modifier = modifier.fillMaxWidth(),
            fontSize = 14.sp,
            color = ds.thoughtText,
        )
        return
    }
    val h = drawable.intrinsicHeight
    val heightDp = if (h > 0) (h / density.density).dp else 28.dp
    Box(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(heightDp)) {
            drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
            drawable.draw(drawContext.canvas.nativeCanvas)
        }
    }
}
