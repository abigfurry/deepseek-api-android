package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.R
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.DsColors
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

/* ------------------------------------------------------------------ 参数表
 * 全部数值来源：_work\OFFICIAL-PARAMS.md §2.3（AI 正文 Markdown）与同批 smali 常量
 *
 *  正文       17sp / 26sp，字距 0，字色 #191B23（浅）/#E1E2EC（深）  kt:MarkdownTypography.kt:84
 *  小号正文   15sp / 24sp（思考区）                                  kt:MarkdownTypography.kt:108
 *  段落间距   ≈19dp（相邻段落首行墨迹差 45.5 − 行高 26.5）            实测:audit-chat.png
 *  标题      h1 26/39、h2 23/34、h3 21/30、h4 19/28、h5 17/28、h6 13/18
 *                                                                  kt:MarkdownTypography.kt:294
 *  列表      符号墨迹起点 = 正文左 +1dp；条目文字 = 正文左 +14dp        ui:bullet 节点
 *            条目间距 ≈13dp（39.5 − 26.5）                          实测
 *            smali 常量 DP 1.0 / 2.0 / 8.0 / 5.0（无序）、1.0 / 8.0（有序）
 *                                                                  kt:MarkdownList.kt:171/99/317
 *  引用块    竖线 3dp（smali const/high16 3.0f ×2）                   kt:MarkdownBlockQuote.kt:32
 *  行内代码  底色 codeBg（浅 #F7F7F7 / 深 #1B1B1C）、等宽字体
 *  代码块    容器圆角 12dp、内边距 12dp、头部 padding 15/8dp、
 *            头部动作盒 36dp / 图标 12dp、代码字号 13.33sp
 *            kt:MarkdownCode.kt:137, MarkdownCodeBlockHeader.kt:316, ...Action.kt:51
 *  分隔线    1dp（smali const/high16 1.0f）、#EDEDED（浅）/#333333（深）
 *  链接色    primary #426EFE
 *  表格      标题栏 46dp / padding 8·4dp / 图标 16dp（GfmTable.kt 内已按 smali 实现）
 */

/** 官方正文：17sp / 26sp。 */
private val MD_BODY_SIZE = 17.sp
private val MD_BODY_LINE = 26.sp

/** 官方小号正文（思考区）：15sp / 24sp。 */
private val MD_SMALL_SIZE = 15.sp
private val MD_SMALL_LINE = 24.sp

/** 段落间距实测 ≈19dp；思考区（compact）按 12dp 收紧。 */
private val MD_PARA_GAP = 19.dp
private val MD_COMPACT_GAP = 12.dp

/** 列表：条目文字缩进 14dp（实测），条目间距 13dp（实测），每级再缩进 14dp。 */
private val MD_LIST_INDENT = 14.dp
private val MD_LIST_GAP = 13.dp

/** 标题字号 / 行高（V2 序列，MarkdownTypography.kt:294）。 */
private val MD_HEAD_SIZES = floatArrayOf(26f, 23f, 21f, 19f, 17f, 13f)
private val MD_HEAD_LINES = floatArrayOf(39f, 34f, 30f, 28f, 28f, 18f)

/** 官方行内代码等宽字号（跟随正文缩放，17sp 正文下 ≈15sp）。 */
private val MD_INLINE_CODE_SIZE = 15.sp

/** 代码块：圆角 12dp / 内边距 12dp / 头部 15·8dp / 动作盒 36dp / 图标 12dp / 代码 13.33sp。 */
private val MD_CODE_RADIUS = 12.dp
private val MD_CODE_PAD = 12.dp
private val MD_CODE_HEADER_PAD_START = 15.dp
private val MD_CODE_HEADER_PAD_END = 4.dp
private val MD_CODE_HEADER_H = 36.dp
private val MD_CODE_ACTION_BOX = 36.dp
private val MD_CODE_ACTION_ICON = 12.dp
private val MD_CODE_FONT = 13.33.sp
private val MD_CODE_LINE = 20.sp

/** 引用块竖线 3dp（smali）。 */
private val MD_QUOTE_BAR = 3.dp
private val MD_QUOTE_PAD = 10.dp

/** 分隔线 1dp。 */
private val MD_RULE_H = 1.dp

/**
 * 行高在行框内居中、不裁掉上下伸部（CJK + 上下标不会互相吃掉），
 * 与官方 Compose 文本排版一致（LineHeightStyle.Alignment.Center / Trim.None）。
 */
private val MdLineStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun mdBodyStyle(compact: Boolean): TextStyle = TextStyle(
    fontSize = if (compact) MD_SMALL_SIZE else MD_BODY_SIZE,
    lineHeight = if (compact) MD_SMALL_LINE else MD_BODY_LINE,
    lineHeightStyle = MdLineStyle,
)

private fun mdHeadStyle(level: Int, compact: Boolean): TextStyle {
    if (compact) {
        return TextStyle(
            fontSize = MD_SMALL_SIZE,
            lineHeight = MD_SMALL_LINE,
            fontWeight = FontWeight.SemiBold,
            lineHeightStyle = MdLineStyle,
        )
    }
    val idx = (level - 1).coerceIn(0, 5)
    return TextStyle(
        fontSize = MD_HEAD_SIZES[idx].sp,
        lineHeight = MD_HEAD_LINES[idx].sp,
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = MdLineStyle,
    )
}

/** 官方正文色：浅色 #191B23（暗字）/ 深色 #E1E2EC（亮字），据此判断明暗，不改 Theme.kt。 */
private fun isLightMode(ds: DsColors): Boolean {
    val c = ds.bodyText
    return (c.red + c.green + c.blue) / 3f < 0.5f
}

/** 浅色用官方 #EDEDED，深色用 DsColors.divider（#333333）。 */
private fun mdDividerColor(ds: DsColors): Color =
    if (isLightMode(ds)) Color(0xFFEDEDED) else ds.divider

sealed interface MdBlock {
    data class Para(val text: String) : MdBlock
    data class Head(val level: Int, val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    /** levels 与 items 一一对应：0 = 顶层，1 = 二级…… 默认空列表等价全 0。 */
    data class Bullet(val items: List<String>, val levels: List<Int> = emptyList()) : MdBlock
    data class Num(val items: List<String>, val levels: List<Int> = emptyList()) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Math(val tex: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private val bulletRx = Regex("^[-*+]\\s+.*")
private val numRx = Regex("^\\d+[.)]\\s+.*")
private val headRx = Regex("^#{1,6}\\s+.*")
private val ruleRx = Regex("^(?:-{3,}|\\*{3,}|_{3,})$")

/** GFM 表格：`| a | b |` 行 + `|---|:--:|` 分隔行。 */
internal fun isTableRow(line: String): Boolean =
    line.length > 1 && line.contains('|') && (line.startsWith("|") || line.count { it == '|' } >= 2)

internal fun isTableDivider(line: String): Boolean {
    if (!line.contains('-')) return false
    val cells = splitRow(line)
    if (cells.isEmpty()) return false
    return cells.all { cell ->
        val c = cell.trim().trim(':')
        c.isNotEmpty() && c.all { it == '-' }
    }
}

internal fun splitRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|")) s = s.dropLast(1)
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length && s[i + 1] == '|') {
            sb.append('|'); i += 2; continue
        }
        if (c == '|') { out.add(sb.toString().trim()); sb.setLength(0); i++; continue }
        sb.append(c); i++
    }
    out.add(sb.toString().trim())
    return out
}

/** 行首缩进（空格=1，Tab=4），用于多级列表层级。 */
private fun indentWidth(raw: String): Int {
    var n = 0
    for (c in raw) {
        if (c == ' ') n += 1 else if (c == '\t') n += 4 else break
    }
    return n
}

/** 缩进 → 列表层级（2 空格一级，最多 3 级）。 */
private fun indentLevel(raw: String): Int = (indentWidth(raw) / 2).coerceIn(0, 3)

fun parseMarkdown(src: String): List<MdBlock> {    val out = mutableListOf<MdBlock>()
    val lines = src.replace("\r\n", "\n").split("\n")
    val para = StringBuilder()

    fun flush() {
        val t = para.toString().trim()
        if (t.isNotEmpty()) out.add(MdBlock.Para(t))
        para.setLength(0)
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trim()
        when {
            line.startsWith("$$") || line.startsWith("\\[") -> {
                flush()
                val fence = if (line.startsWith("$$")) "$$" else "\\]"
                val sb = StringBuilder()
                var first = line.removePrefix(if (line.startsWith("$$")) "$$" else "\\[").trim()
                if (first.endsWith(fence)) {
                    first = first.removeSuffix(fence).trim()
                    if (first.isNotEmpty()) sb.append(first)
                } else {
                    if (first.isNotEmpty()) sb.append(first).append("\n")
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith(fence)) {
                        sb.append(lines[i]).append("\n")
                        i++
                    }
                    if (i < lines.size) {
                        sb.append(lines[i].trim().removeSuffix(fence))
                    }
                }
                out.add(MdBlock.Math(sb.toString().trim()))
            }

            line.startsWith("```") -> {
                flush()
                val lang = line.removePrefix("```").trim()
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    sb.append(lines[i]).append("\n")
                    i++
                }
                out.add(MdBlock.Code(lang, sb.toString().trimEnd()))
            }

            headRx.matches(line) -> {
                flush()
                val level = line.takeWhile { it == '#' }.length
                out.add(MdBlock.Head(level, line.drop(level).trim()))
            }

            line.startsWith(">") -> {
                flush()
                val sb = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    val t = lines[i].trim().removePrefix(">").trim()
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(t)
                    i++
                }
                out.add(MdBlock.Quote(sb.toString()))
                continue
            }

            isTableRow(line) && i + 1 < lines.size && isTableDivider(lines[i + 1].trim()) -> {
                flush()
                val header = splitRow(line)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && isTableRow(lines[i].trim())) {
                    rows.add(splitRow(lines[i].trim()))
                    i++
                }
                out.add(MdBlock.Table(header, rows))
                continue
            }

            bulletRx.matches(line) -> {
                flush()
                val items = mutableListOf<String>()
                val levels = mutableListOf<Int>()
                while (i < lines.size) {
                    val itemRaw = lines[i]
                    val tl = itemRaw.trim()
                    when {
                        bulletRx.matches(tl) -> {
                            items.add(tl.drop(1).trim())
                            levels.add(indentLevel(itemRaw))
                            i++
                        }
                        tl.isEmpty() -> {
                            // 空行后仍是列表项则继续，否则列表结束
                            val next = if (i + 1 < lines.size) lines[i + 1].trim() else ""
                            if (next.isNotEmpty() && (bulletRx.matches(next) || numRx.matches(next))) i++ else break
                        }
                        itemRaw.startsWith(" ") || itemRaw.startsWith("\t") -> {
                            // 悬挂续行：并到上一条目
                            if (items.isNotEmpty()) items[items.size - 1] = items.last() + "\n" + tl
                            i++
                        }
                        else -> break
                    }
                }
                out.add(MdBlock.Bullet(items, levels))
                continue
            }

            numRx.matches(line) -> {
                flush()
                val items = mutableListOf<String>()
                val levels = mutableListOf<Int>()
                while (i < lines.size) {
                    val itemRaw = lines[i]
                    val tl = itemRaw.trim()
                    when {
                        numRx.matches(tl) -> {
                            items.add(tl.substringAfter(' ').trim())
                            levels.add(indentLevel(itemRaw))
                            i++
                        }
                        tl.isEmpty() -> {
                            val next = if (i + 1 < lines.size) lines[i + 1].trim() else ""
                            if (next.isNotEmpty() && (bulletRx.matches(next) || numRx.matches(next))) i++ else break
                        }
                        itemRaw.startsWith(" ") || itemRaw.startsWith("\t") -> {
                            if (items.isNotEmpty()) items[items.size - 1] = items.last() + "\n" + tl
                            i++
                        }
                        else -> break
                    }
                }
                out.add(MdBlock.Num(items, levels))
                continue
            }

            ruleRx.matches(line) -> {
                flush()
                out.add(MdBlock.Rule)
            }

            line.isEmpty() -> flush()

            else -> {
                if (para.isNotEmpty()) para.append("\n")
                para.append(raw)
            }
        }
        i++
    }
    flush()
    return out
}


/**
 * 行内公式的轻量渲染：把 \( ... \) / \[ ... \] 里的 LaTeX 转成可读的 Unicode。
 * 块级公式仍然走 MathText(KaTeX)。
 */
fun latexLite(input: String): String {
    if (!input.contains("\\(") && !input.contains("\\[")) return input
    var t = input
        .replace("\\(", "").replace("\\)", "")
        .replace("\\[", "").replace("\\]", "")
    val map = listOf(
        "\\times" to "×", "\\cdot" to "·", "\\div" to "÷",
        "\\pm" to "±", "\\mp" to "∓",
        "\\approx" to "≈", "\\neq" to "≠", "\\ne" to "≠",
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥",
        "\\ll" to "≪", "\\gg" to "≫",
        "\\infty" to "∞", "\\partial" to "∂", "\\nabla" to "∇",
        "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫",
        "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←",
        "\\Rightarrow" to "⇒", "\\Leftrightarrow" to "⇔",
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\lambda" to "λ", "\\mu" to "μ", "\\pi" to "π",
        "\\rho" to "ρ", "\\sigma" to "σ", "\\tau" to "τ", "\\phi" to "φ",
        "\\omega" to "ω", "\\theta" to "θ", "\\Delta" to "Δ", "\\Sigma" to "Σ",
        "\\Omega" to "Ω",
        "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan",
        "\\log" to "log", "\\ln" to "ln", "\\exp" to "exp",
        "\\sqrt" to "√", "\\%" to "%",
    )
    for ((k, v) in map) t = t.replace(k, v)
    // \frac{a}{b} -> a/b
    t = Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}""").replace(t) { m -> "${m.groupValues[1]}/${m.groupValues[2]}" }
    // \text{x} / \mathrm{x} -> x
    t = Regex("""\\(?:text|mathrm|mathbf|mathit)\{([^{}]*)\}""").replace(t) { m -> m.groupValues[1] }
    // ^{...} 和 _{...} -> 上下标
    val sup = mapOf('0' to '⁰','1' to '¹','2' to '²','3' to '³','4' to '⁴','5' to '⁵','6' to '⁶','7' to '⁷','8' to '⁸','9' to '⁹','+' to '⁺','-' to '⁻','n' to 'ⁿ','(' to '⁽',')' to '⁾')
    val sub = mapOf('0' to '₀','1' to '₁','2' to '₂','3' to '₃','4' to '₄','5' to '₅','6' to '₆','7' to '₇','8' to '₈','9' to '₉','+' to '₊','-' to '₋','n' to 'ₙ','(' to '₍',')' to '₎')
    fun toScript(body: String, table: Map<Char, Char>): String? {
        val sb = StringBuilder()
        for (c in body) sb.append(table[c] ?: return null)
        return sb.toString()
    }
    t = Regex("""\^\{([^{}]*)\}""").replace(t) { m -> toScript(m.groupValues[1], sup) ?: ("^" + m.groupValues[1]) }
    t = Regex("""_\{([^{}]*)\}""").replace(t) { m -> toScript(m.groupValues[1], sub) ?: ("_" + m.groupValues[1]) }
    t = Regex("""\^([0-9n])""").replace(t) { m -> toScript(m.groupValues[1], sup) ?: m.value }
    t = Regex("""_([0-9n])""").replace(t) { m -> toScript(m.groupValues[1], sub) ?: m.value }
    return t
}


fun inlineMarkdown(srcRaw: String, codeBg: Color, link: Color = DsBlue): AnnotatedString {
    val src = latexLite(srcRaw)
    val b = AnnotatedString.Builder()
    var i = 0
    while (i < src.length) {
        val rest = src.substring(i)
        when {
            // ***粗斜体*** / ___粗斜体___：必须先于 ** 判断，否则只吃掉 *x 剩一个 *
            rest.startsWith("***") || rest.startsWith("___") -> {
                val marker = rest.substring(0, 3)
                val end = src.indexOf(marker, i + 3)
                if (end > i + 3) {
                    b.withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        b.append(src.substring(i + 3, end))
                    }
                    i = end + 3
                } else {
                    b.append(marker)
                    i += 3
                }
            }

            // **粗体** / __粗体__：官方字重 = Bold(700)
            rest.startsWith("**") || rest.startsWith("__") -> {
                val marker = rest.substring(0, 2)
                val end = src.indexOf(marker, i + 2)
                if (end > i + 2) {
                    b.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        b.append(src.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    b.append(marker)
                    i += 2
                }
            }

            rest.startsWith("~~") -> {
                val end = src.indexOf("~~", i + 2)
                if (end > i + 2) {
                    b.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        b.append(src.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    b.append("~~")
                    i += 2
                }
            }

            // 行内代码：等宽 + 官方 codeBg 底色（浅 #F7F7F7 / 深 #1B1B1C）
            rest.startsWith("`") -> {
                val end = src.indexOf('`', i + 1)
                if (end > i + 1) {
                    b.withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg,
                            fontSize = MD_INLINE_CODE_SIZE,
                        )
                    ) {
                        b.append(src.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    b.append('`')
                    i++
                }
            }

            rest.startsWith("*") || rest.startsWith("_") -> {
                val ch = rest[0]
                val end = src.indexOf(ch, i + 1)
                if (end > i + 1) {
                    b.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        b.append(src.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    b.append(ch)
                    i++
                }
            }

            rest.startsWith("[") -> {
                val close = src.indexOf("](", i)
                val endParen = if (close > 0) src.indexOf(')', close) else -1
                if (close > 0 && endParen > close) {
                    // 官方链接色 = 主色 #426EFE
                    b.withStyle(SpanStyle(color = link)) {
                        b.append(src.substring(i + 1, close))
                    }
                    i = endParen + 1
                } else {
                    b.append('[')
                    i++
                }
            }

            else -> {
                b.append(src[i])
                i++
            }
        }
    }
    return b.toAnnotatedString()
}

@Composable
fun MarkdownView(
    source: String,
    modifier: Modifier = Modifier,
    textColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    compact: Boolean = false,
    selectionMode: Boolean = false,
    selectAtY: Float = -1f,
) {
    val fg = if (textColor == androidx.compose.ui.graphics.Color.Unspecified)
        MaterialTheme.colorScheme.onBackground else textColor
    val blocks = remember(source) { parseMarkdown(source) }

    // 整条消息只用**一个** SelectionState / SelectionContainer：
    // 之前是每个 Markdown 块各自一个容器，导致「只能在某一段里选，跨不了段、也选不了全文」
    // （用户反馈：只能选那一段，不能把整段文字都框选到）。
    // 合成一个容器后，拖动选择柄可以跨段落连续选中整条消息。
    val selState = remember(blocks) { SelectionState() }

    LaunchedEffect(selectionMode, blocks) {
        if (!selectionMode) {
            runCatching { selState.clear() }
            return@LaunchedEffect
        }
        // 进入「选择文本」时默认全选这条消息，用户可以直接复制 / 再拖动手柄微调
        kotlinx.coroutines.delay(80)
        runCatching { selState.selectAll() }
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = modifier.fillMaxWidth(),
            // 官方段落间距 ≈19dp（实测），思考区按 12dp
            verticalArrangement = Arrangement.spacedBy(if (compact) MD_COMPACT_GAP else MD_PARA_GAP)
        ) {
            for (block in blocks) {
                Box(Modifier.fillMaxWidth()) {
                    MarkdownBlock(block, fg, compact)
                }
            }
        }
    }

    if (selectionMode) {
        androidx.compose.foundation.text.selection.SelectionContainer(state = selState) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock, fg: androidx.compose.ui.graphics.Color, compact: Boolean) {
    val ds = LocalDsColors.current
    when (block) {

                // 正文 17sp/26sp（思考区 15sp/24sp），字色官方 #191B23 / #E1E2EC
                is MdBlock.Para -> Text(
                    text = inlineMarkdown(block.text, ds.codeBg),
                    style = mdBodyStyle(compact),
                    color = fg,
                )

                // 标题 h1 26/39 … h6 13/18，SemiBold，同正文色
                is MdBlock.Head -> Text(
                    text = inlineMarkdown(block.text, ds.codeBg),
                    style = mdHeadStyle(block.level, compact),
                    color = fg,
                )

                is MdBlock.Code -> CodeBlock(block.lang, block.code)

                is MdBlock.Math -> MathText(block.tex)

                is MdBlock.Table -> GfmTable(block.header, block.rows)

                // 无序列表：符号墨迹距左 1dp、条目文字缩进 14dp、条目间距 13dp
                is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(MD_LIST_GAP)) {
                    block.items.forEachIndexed { index, item ->
                        val level = block.levels.getOrElse(index) { 0 }.coerceIn(0, 3)
                        ListItemRow(
                            marker = bulletMarker(level),
                            level = level,
                            item = item,
                            fg = fg,
                            compact = compact,
                            markerAlign = Alignment.TopStart,
                        )
                    }
                }

                // 有序列表：标记右对齐于 14dp 文字缩进列
                is MdBlock.Num -> Column(verticalArrangement = Arrangement.spacedBy(MD_LIST_GAP)) {
                    block.items.forEachIndexed { index, item ->
                        val level = block.levels.getOrElse(index) { 0 }.coerceIn(0, 3)
                        ListItemRow(
                            marker = "${index + 1}.",
                            level = level,
                            item = item,
                            fg = fg,
                            compact = compact,
                            markerAlign = Alignment.TopEnd,
                        )
                    }
                }

                // 引用块：3dp 竖线（smali）+ 10dp 内边距，字色官方次级灰 #7D7F85
                is MdBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        Modifier
                            .width(MD_QUOTE_BAR)
                            .fillMaxHeight()
                            .background(mdDividerColor(ds), RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = inlineMarkdown(block.text, ds.codeBg),
                        style = mdBodyStyle(compact),
                        color = ds.thoughtText,
                        modifier = Modifier.padding(start = MD_QUOTE_PAD),
                    )
                }

                // 分隔线：1dp，浅 #EDEDED / 深 #333333
                MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .height(MD_RULE_H)
                        .background(mdDividerColor(ds))
                )
    }
}

/** 多级列表符号：官方顶层为圆点，2/3 级用空心/方块区分层级。 */
private fun bulletMarker(level: Int): String = when (level) {
    0 -> "\u2022"   // •
    1 -> "\u25E6"   // ◦
    else -> "\u25AA" // ▪
}

@Composable
private fun ListItemRow(
    marker: String,
    level: Int,
    item: String,
    fg: Color,
    compact: Boolean,
    markerAlign: Alignment,
) {
    val ds = LocalDsColors.current
    Row(Modifier.fillMaxWidth().padding(start = MD_LIST_INDENT * level.toFloat())) {
        Box(Modifier.width(MD_LIST_INDENT), contentAlignment = markerAlign) {
            Text(
                text = marker,
                style = mdBodyStyle(compact),
                color = fg,
            )
        }
        Text(
            text = inlineMarkdown(item, ds.codeBg),
            style = mdBodyStyle(compact),
            color = fg,
            modifier = Modifier.padding(start = 0.dp),
        )
    }
}

/* --------------------------------------------------------- 代码语法高亮 */
/**
 * DeepSeek 官方代码块是带颜色的（浅色 One-Light 风格，深色 One-Dark 风格）。
 * 这里做一个够用的词法高亮：注释 / 字符串 / 数字 / 关键字 / 函数名。
 */
private val KW = setOf(
    "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while",
    "return", "import", "package", "try", "catch", "finally", "throw", "in", "is", "as", "null",
    "true", "false", "this", "super", "override", "private", "public", "internal", "protected",
    "def", "elif", "except", "raise", "with", "yield", "lambda", "None", "True", "False", "and",
    "or", "not", "from", "async", "await", "pass", "break", "continue", "global", "nonlocal",
    "const", "let", "function", "new", "typeof", "void", "delete", "of", "export", "default",
    "fn", "let", "mut", "impl", "pub", "use", "mod", "match", "struct", "enum", "trait",
    "int", "long", "double", "float", "char", "boolean", "static", "final", "extends", "implements",
    "void", "switch", "case", "do", "goto", "sizeof", "include", "define", "ifdef", "endif",
)

// 浅色 One-Light
private val C_KEYWORD = Color(0xFFA626A4)
private val C_STRING = Color(0xFF50A14F)
private val C_COMMENT = Color(0xFFA0A1A7)
private val C_NUMBER = Color(0xFF986801)
private val C_FUNC = Color(0xFF4078F2)
// 深色 One-Dark
private val D_KEYWORD = Color(0xFFC678DD)
private val D_STRING = Color(0xFF98C379)
private val D_COMMENT = Color(0xFF5C6370)
private val D_NUMBER = Color(0xFFD19A66)
private val D_FUNC = Color(0xFF61AFEF)

/** 返回带颜色的代码；解析失败就退回纯文本。 */
fun highlightCode(code: String): AnnotatedString = highlightCode(code, dark = false)

private fun highlightCode(code: String, dark: Boolean): AnnotatedString {
    val out = androidx.compose.ui.text.AnnotatedString.Builder(code)
    val keyword = if (dark) D_KEYWORD else C_KEYWORD
    val string = if (dark) D_STRING else C_STRING
    val comment = if (dark) D_COMMENT else C_COMMENT
    val number = if (dark) D_NUMBER else C_NUMBER
    val func = if (dark) D_FUNC else C_FUNC
    var i = 0
    val n = code.length
    fun paint(start: Int, end: Int, c: Color) {
        if (end > start) out.addStyle(SpanStyle(color = c), start, end)
    }
    while (i < n) {
        val ch = code[i]
        // 行注释 // 或 #
        if ((ch == '/' && i + 1 < n && code[i + 1] == '/') || ch == '#') {
            var j = i
            while (j < n && code[j] != '\n') j++
            paint(i, j, comment); i = j; continue
        }
        // 块注释
        if (ch == '/' && i + 1 < n && code[i + 1] == '*') {
            var j = i + 2
            while (j + 1 < n && !(code[j] == '*' && code[j + 1] == '/')) j++
            j = minOf(n, j + 2)
            paint(i, j, comment); i = j; continue
        }
        // 字符串
        if (ch == '"' || ch == '\'' || ch == '`') {
            val q = ch
            var j = i + 1
            while (j < n && code[j] != q) { if (code[j] == '\\') j++; j++ }
            j = minOf(n, j + 1)
            paint(i, j, string); i = j; continue
        }
        // 数字
        if (ch.isDigit()) {
            var j = i
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
            paint(i, j, number); i = j; continue
        }
        // 标识符
        if (ch.isLetter() || ch == '_') {
            var j = i
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            val word = code.substring(i, j)
            var k = j
            while (k < n && code[k] == ' ') k++
            when {
                word in KW -> paint(i, j, keyword)
                k < n && code[k] == '(' -> paint(i, j, func)
            }
            i = j; continue
        }
        i++
    }
    return out.toAnnotatedString()
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val ds = LocalDsColors.current
    val clipboard = LocalClipboardManager.current
    val dark = !isLightMode(ds)
    val codeScroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MD_CODE_RADIUS))
            .background(ds.codeBg)
    ) {
        // 头部：padding 15/8dp（smali），动作盒 36dp / 图标 12dp
        Row(
            Modifier
                .fillMaxWidth()
                .background(ds.codeHeader)
                .height(MD_CODE_HEADER_H)
                .padding(start = MD_CODE_HEADER_PAD_START, end = MD_CODE_HEADER_PAD_END),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lang.ifBlank { "code" },
                color = ds.subText,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(MD_CODE_ACTION_BOX)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { clipboard.setText(AnnotatedString(code)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy_outline),
                    contentDescription = "复制代码",
                    tint = ds.subText,
                    modifier = Modifier.size(MD_CODE_ACTION_ICON),
                )
            }
        }
        // 代码区：13.33sp 等宽、行高 20sp、内边距 12dp、横向滚动（不换行）
        Text(
            text = remember(code, dark) { highlightCode(code, dark) },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(codeScroll)
                .padding(MD_CODE_PAD),
            fontFamily = FontFamily.Monospace,
            fontSize = MD_CODE_FONT,
            lineHeight = MD_CODE_LINE,
            color = ds.bodyText,
            softWrap = false,
        )
    }
}
