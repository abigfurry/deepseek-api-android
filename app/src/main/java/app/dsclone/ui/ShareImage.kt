package com.yuyuyu.deepseek.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.yuyuyu.deepseek.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 官方「生成分享图片」的本地复刻（自绘，不依赖 Compose 截图）。
 *
 * 参数来源（反编译 apk_analysis/decoded_full）：
 * - 卡片圆角 16dp：smali:i7a `<clinit>` -> `Lbh9;->b(16.0f)`，即 ShareViewDefaults.cardShape
 *   （u67.smali 中 `sget-object Li7a;->a` 作为 shape 传入 clip）。
 * - 最大宽度 768dp：smali:p50（OffscreenPreviewRenderer.kt:54 的 lambda）
 *   `const/high16 # 768.0f` -> `Lwca;->u`（widthIn max）。
 * - 水平内边距 32dp：smali:i7a `<clinit>` 里的 PaddingValues(32,...)，Footer/Header 均用 `Lb08;->Q` 套 padding。
 * - 正文 17sp/26sp、颜色 #191B23（深色 #E1E2EC）：与官方 MarkdownTypography 正文一致（见 OFFICIAL-PARAMS.md 2.3）。
 * - 页脚高度 160dp：smali:u48 `<clinit>` 20 + 8 + 8 + 8 + 64 + 20 + 32（footer 高度常量 Lu48;->a）。
 * - 页脚免责 12sp/16.8sp、次色 #7D7F85（深色 #B9BABB）：smali:i7a `a()` =
 *   ShareViewDefaults.disclaimerTextStyle（SP 12 / lineHeight 16.8 / colorSchemeV2 secondary）。
 * - 页脚水印文案：res/values-b+zh+Hans/strings.xml `share_image_footer_alert_v2` =
 *   「回答由 AI 生成，仅供参考」（英文 "AI-generated, for reference only"）。
 * - 页脚 logo：res/drawable/deepseek_logo_title.xml（177dp x 27dp，fill #FF426EFE）；
 *   本工程已有同文件 res/drawable/logo_wordmark.xml。
 * - 顶部 logo 内边距：smali:h67 Header（ShareCaptureContent.kt:110）先 padding(32,72,0,34) 再 padding(26)。
 *
 * 官方另含「扫码继续聊」二维码水印（smali:h67 Footer 中的 QR 生成逻辑 + `scan_q_r_code_alert`），
 * 但本工程没有分享链接服务，故未画二维码，仅保留免责水印 + DeepSeek logo。
 */
object ShareImageDefaults {
    const val MAX_WIDTH_DP = 768f
    const val MIN_WIDTH_DP = 320f
    const val CORNER_DP = 16f
    const val PAD_H_DP = 32f
    const val HEADER_TOP_DP = 26f
    const val LOGO_H_DP = 27f
    const val BODY_TOP_GAP_DP = 24f
    const val BODY_BOTTOM_DP = 24f
    const val FOOTER_H_DP = 160f
    const val FOOTER_LINE_DP = 1f
    const val DISCLAIMER_SP = 12f
    const val DISCLAIMER_LINE_SP = 16.8f
    const val FOOTER_LOGO_H_DP = 18f
    const val MIN_IMAGE_H_DP = 291f
    const val BODY_SP = 17f
    const val BODY_LINE_SP = 26f
    const val TITLE_SP = 15f
    const val MAX_TEXT_CHARS = 6000

    // 官方浅色：卡片 #FFFFFF、正文 #191B23、次色 #7D7F85、分隔线 #EDEDED
    // 官方深色：卡片 #0F0F0F、正文 #E1E2EC、次色 #B9BABB、分隔线 #292929
    val LIGHT_CARD = 0xFFFFFFFF.toInt()
    val LIGHT_TEXT = 0xFF191B23.toInt()
    val LIGHT_SUB = 0xFF7D7F85.toInt()
    val LIGHT_DIVIDER = 0xFFEDEDED.toInt()
    val DARK_CARD = 0xFF0F0F0F.toInt()
    val DARK_TEXT = 0xFFE1E2EC.toInt()
    val DARK_SUB = 0xFFB9BABB.toInt()
    val DARK_DIVIDER = 0xFF292929.toInt()

    fun footerAlert(ctx: Context): String = "回答由 AI 生成，仅供参考"
}

/**
 * 生成官方样式的分享卡片位图（调用线程：任意后台线程均可，会自行 measure/layout）。
 *
 * @param ctx   Context（只用 resources / getDrawable / displayMetrics）
 * @param title 卡片顶部小标题（官方卡片只有 logo，标题是本工程附加的可选信息，传空串则不画）
 * @param text  回答正文（按 17sp/26sp StaticLayout 自动换行，超长截断到 6000 字符）
 * @param dark  是否深色卡片
 */
fun renderShareBitmap(ctx: Context, title: String, text: String, dark: Boolean): Bitmap {
    val res = ctx.resources
    val dm = res.displayMetrics
    val density = dm.density
    val scaled = dm.scaledDensity

    val screenWdp = dm.widthPixels / density
    val cardWdp = min(
        ShareImageDefaults.MAX_WIDTH_DP,
        max(ShareImageDefaults.MIN_WIDTH_DP, screenWdp - 32f)
    )
    val padH = ShareImageDefaults.PAD_H_DP * density
    val contentW = max(1, (cardWdp * density - 2 * padH).roundToInt())

    val body = normalizeShareText(text)

    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = ShareImageDefaults.BODY_SP * scaled
        color = if (dark) ShareImageDefaults.DARK_TEXT else ShareImageDefaults.LIGHT_TEXT
        typeface = Typeface.DEFAULT
        isSubpixelText = true
    }
    val targetLine = ShareImageDefaults.BODY_LINE_SP * scaled
    val fm = bodyPaint.fontMetrics
    val spacingExtra = max(0f, targetLine - (fm.descent - fm.ascent))
    val layout = StaticLayout.Builder
        .obtain(body, 0, body.length, bodyPaint, contentW)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(spacingExtra, 1f)
        .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .build()

    val topPad = ShareImageDefaults.HEADER_TOP_DP * density
    val logoH = ShareImageDefaults.LOGO_H_DP * density
    val logoW = logoH * 177f / 27f
    val hasTitle = title.isNotBlank()
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = ShareImageDefaults.TITLE_SP * scaled
        color = if (dark) ShareImageDefaults.DARK_SUB else ShareImageDefaults.LIGHT_SUB
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val titleH = if (hasTitle) titlePaint.fontMetrics.bottom - titlePaint.fontMetrics.top else 0f
    val titleGap = if (hasTitle) 8f * density else 0f
    val headerBottom = topPad + logoH + titleGap + titleH + ShareImageDefaults.BODY_TOP_GAP_DP * density

    val bodyH = layout.height.toFloat()
    val bodyBottom = ShareImageDefaults.BODY_BOTTOM_DP * density
    val footerH = ShareImageDefaults.FOOTER_H_DP * density
    val minTotal = ShareImageDefaults.MIN_IMAGE_H_DP * density

    val totalH = max(minTotal, headerBottom + bodyH + bodyBottom + footerH)
    val w = (cardWdp * density).roundToInt()
    val h = totalH.roundToInt()

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val radius = ShareImageDefaults.CORNER_DP * density

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (dark) ShareImageDefaults.DARK_CARD else ShareImageDefaults.LIGHT_CARD
        style = Paint.Style.FILL
    }
    val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
    canvas.drawRoundRect(rect, radius, radius, cardPaint)

    canvas.save()
    canvas.clipRect(rect) // 圆角裁切，避免内容溢出圆角

    // ---- 顶部：DeepSeek wordmark（官方 Header 只有 logo） ----
    drawVectorDrawable(ctx, R.drawable.logo_wordmark, padH, topPad, logoW, logoH, canvas)

    var cursorY = topPad + logoH
    if (hasTitle) {
        cursorY += titleGap + titleH
        val baseline = cursorY - titlePaint.fontMetrics.descent
        canvas.drawText(ellipsize(title, titlePaint, contentW), padH, baseline, titlePaint)
    }

    // ---- 正文：StaticLayout 17sp / 26sp ----
    canvas.save()
    canvas.translate(padH, headerBottom)
    layout.draw(canvas)
    canvas.restore()

    // ---- 页脚：分隔线 + 免责水印 + logo ----
    val footerTop = h - footerH
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (dark) ShareImageDefaults.DARK_DIVIDER else ShareImageDefaults.LIGHT_DIVIDER
        strokeWidth = ShareImageDefaults.FOOTER_LINE_DP * density
    }
    canvas.drawLine(padH, footerTop, w - padH, footerTop, dividerPaint)

    val discPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = ShareImageDefaults.DISCLAIMER_SP * scaled
        color = if (dark) ShareImageDefaults.DARK_SUB else ShareImageDefaults.LIGHT_SUB
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    val discLine = ShareImageDefaults.DISCLAIMER_LINE_SP * scaled
    val discTop = footerTop + 20f * density
    val discBaseline = discTop + (discLine - (discPaint.fontMetrics.descent - discPaint.fontMetrics.ascent)) / 2f - discPaint.fontMetrics.ascent
    canvas.drawText(
        ShareImageDefaults.footerAlert(ctx),
        w / 2f,
        discBaseline,
        discPaint
    )

    val fLogoH = ShareImageDefaults.FOOTER_LOGO_H_DP * density
    val fLogoW = fLogoH * 177f / 27f
    val fLogoTop = discTop + discLine + 12f * density
    drawVectorDrawable(
        ctx, R.drawable.logo_wordmark,
        (w - fLogoW) / 2f, fLogoTop, fLogoW, fLogoH, canvas
    )

    canvas.restore()
    return bmp
}

/**
 * 生成分享图片并调起系统分享面板（ACTION_SEND + FileProvider）。
 *
 * 容错：
 * - provider 未在 AndroidManifest 配置（本工程当前没有 provider 声明）时
 *   `FileProvider.getUriForFile` 会抛 IllegalArgumentException，
 *   这里 try/catch 后自动降级为 ChatParts.kt 里已有的纯文字分享 shareText()。
 * - 任意渲染 / 写文件 / startActivity 失败同样降级为文字分享。
 */
fun shareGeneratedImage(ctx: Context, title: String, text: String) {
    if (text.isBlank()) return
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    val file = try {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        File(dir, "ds_share_${System.currentTimeMillis()}.png")
    } catch (t: Throwable) {
        shareText(ctx, text)
        return
    }

    val ok = try {
        val bmp = renderShareBitmap(ctx, title, text, dark)
        try {
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
        } finally {
            bmp.recycle()
        }
        true
    } catch (t: Throwable) {
        false
    }
    if (!ok || !file.exists() || file.length() <= 0L) {
        shareText(ctx, text)
        return
    }

    val uri = try {
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    } catch (t: Throwable) {
        // provider 缺失 / authority 不匹配：官方样式的图片分享不可用，降级文字
        shareText(ctx, text)
        return
    }

    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = android.content.ClipData.newRawUri("image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享图片").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(chooser)
    } catch (t: Throwable) {
        shareText(ctx, text)
    }
}

/* ------------------------------------------------------------------ */
/* internals                                                           */
/* ------------------------------------------------------------------ */

private fun normalizeShareText(text: String): String {
    var s = text.replace("\r\n", "\n").replace('\r', '\n').trim()
    // 分享图是 StaticLayout 纯文本排版，不认 Markdown —— 必须先把标记洗掉，
    // 否则分享出去的图里会直接出现 **加粗**、`代码`、### 标题、- 列表 这些符号。
    s = stripMarkdownForShare(s)
    if (s.isEmpty()) s = " "
    if (s.length > ShareImageDefaults.MAX_TEXT_CHARS) {
        s = s.substring(0, ShareImageDefaults.MAX_TEXT_CHARS) + "…"
    }
    return s
}

/** 把 Markdown 标记洗成纯文本（分享图片用，官方分享图里就是纯文本）。 */
private fun stripMarkdownForShare(src: String): String {
    val out = StringBuilder()
    for (rawLine in src.split('\n')) {
        var line = rawLine
        // 围栏代码块标记 ```lang / ```
        if (line.trimStart().startsWith("```")) continue
        // 标题 / 引用 / 分隔线
        line = line.replace(Regex("^\\s{0,3}#{1,6}\\s*"), "")
        line = line.replace(Regex("^\\s{0,3}>\\s?"), "")
        if (Regex("^\\s{0,3}([-*_])\\s*\\1\\s*\\1[\\s\\-*_]*$").matches(line)) continue
        // 列表标记 → 项目符号
        line = line.replace(Regex("^(\\s*)[-*+]\\s+"), "$1• ")
        line = line.replace(Regex("^(\\s*)\\d+[.)]\\s+"), "$1• ")
        // 行内标记
        line = line.replace("**", "").replace("__", "")
        line = line.replace(Regex("(?<!\\*)\\*(?!\\*)"), "")
        line = line.replace(Regex("(?<!_)_(?!_)"), "")
        line = line.replace("`", "")
        line = line.replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        line = line.replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        out.append(line).append('\n')
    }
    return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
}

private fun drawVectorDrawable(
    ctx: Context,
    resId: Int,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    canvas: Canvas,
) {
    try {
        val d = ctx.getDrawable(resId) ?: return
        d.setBounds(left.roundToInt(), top.roundToInt(), (left + width).roundToInt(), (top + height).roundToInt())
        d.draw(canvas)
    } catch (_: Throwable) {
        // 图标缺失时忽略，不阻断分享图生成
    }
}

private fun ellipsize(text: String, paint: Paint, maxWidth: Int): String {
    if (text.isEmpty()) return text
    if (paint.measureText(text) <= maxWidth) return text
    val ell = "…"
    val ellW = paint.measureText(ell)
    var end = text.length
    while (end > 0 && paint.measureText(text, 0, end) + ellW > maxWidth) end--
    return if (end <= 0) ell else text.substring(0, end) + ell
}
