package com.yuyuyu.deepseek.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把选中的图片落盘到 files/msg_img，返回绝对路径。
 * 相册给的 content:// 只是一次性授权，重启后就读不到了，
 * 所以发出去的消息必须靠自己的副本在气泡里显示。
 */
fun persistImage(ctx: android.content.Context, uri: Uri): String? = runCatching {
    val dir = java.io.File(ctx.filesDir, "msg_img").apply { mkdirs() }
    val f = java.io.File(dir, "img_" + java.util.UUID.randomUUID().toString().take(8) + ".jpg")
    val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    var scale = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / scale > 1300) scale *= 2
    val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = scale })
        ?: return@runCatching null
    java.io.FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 88, out) }
    f.absolutePath
}.getOrNull()


/** 文件大小：官方显示成 12.3 KB 这种 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.1f GB", mb / 1024.0)
}

/** 取文件大小（可能是 content:// 也可能是普通路径） */
fun queryFileSize(ctx: android.content.Context, uri: Uri): Long = runCatching {
    if (uri.scheme == null || uri.scheme == "file") {
        java.io.File(uri.path!!).length()
    } else {
        ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            ?: ctx.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
    }
}.getOrDefault(0L)

/** 从 uri 取文件名 */
fun queryFileName(ctx: android.content.Context, uri: Uri, fallback: String): String = runCatching {
    if (uri.scheme == null || uri.scheme == "file") {
        java.io.File(uri.path!!).name
    } else {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        } ?: uri.lastPathSegment ?: fallback
    }
}.getOrDefault(fallback)

data class Attachment(
    val uri: Uri,
    val isImage: Boolean,
    val name: String,
)

@Composable
fun rememberAttachmentBitmap(uri: Uri, maxSide: Int = 800): Bitmap? {
    val ctx = LocalContext.current
    var bitmap by remember(uri, maxSide) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, maxSide) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                // 消息里的图片存的是应用私有目录的绝对路径（files/msg_img/xxx.jpg），
                // contentResolver 打不开纯路径，必须分两条路
                val stream = if (uri.scheme == null || uri.scheme == "file") {
                    java.io.File(uri.path!!).inputStream()
                } else {
                    ctx.contentResolver.openInputStream(uri) ?: return@runCatching null
                }
                // 缩略图和全屏看图要不同精度：全屏按屏幕尺寸解，不然放大会糊成一团
                val bytes = stream.use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }.getOrNull()
        }
    }
    return bitmap
}

@Composable
fun AttachmentStrip(
    attachments: List<Attachment>,
    onRemove: (Attachment) -> Unit,
) {
    if (attachments.isEmpty()) return
    // 官方的附件块：点缩略图看大图，右上角一个 ✕ 删掉
    var preview by remember { mutableStateOf<Attachment?>(null) }
    Row(Modifier.padding(start = 12.00.dp, end = 12.00.dp, top = 10.16.dp)) {
        attachments.take(4).forEach { a ->
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val isVideo = remember(a.uri) {
                runCatching {
                    ctx.contentResolver.getType(a.uri)?.startsWith("video/") == true ||
                        a.name.matches(Regex("(?i).*\\.(mp4|mov|3gp|mkv|webm|m4v)$"))
                }.getOrDefault(false)
            }
            val bmp = when {
                a.isImage -> rememberAttachmentBitmap(a.uri)
                isVideo -> rememberVideoThumb(a.uri)
                else -> null
            }
            // 文件（非图片/非视频）走官方那种文件卡片，不是 64dp 方块
            if (!a.isImage && !isVideo) {
                AttachmentFileChip(a, onRemove)
                return@forEach
            }
            Box(Modifier.padding(end = 8.00.dp)) {
                Box(
                    Modifier
                        .size(64.00.dp)
                        .clip(RoundedCornerShape(10.67.dp))
                        .background(Color(0xFFF1F3F5))
                        .clickable { preview = a },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (isVideo) {
                        Box(
                            Modifier
                                .size(24.00.dp)
                                .clip(CircleShape)
                                .background(Color(0x66000000)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play_media_outline_20),
                                contentDescription = "视频",
                                tint = Color.White,
                                modifier = Modifier.size(14.00.dp),
                            )
                        }
                    }
                    if (bmp == null && !isVideo) {
                        Text(
                            text = a.name.take(6),
                            fontSize = 11.sp,
                            color = Color(0xFF61666B),
                        )
                    }
                }
                // 右上角的 ✕（官方：小圆底 + 白色叉）
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.00.dp, y = (-4.00).dp)
                        .size(18.00.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC3A3A3C))
                        .clickable { onRemove(a) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_outline_20),
                        contentDescription = "移除",
                        tint = Color.White,
                        modifier = Modifier.size(11.43.dp),
                    )
                }
            }
        }
    }

    preview?.let { a ->
        FullScreenImageViewer(listOf(a.uri.toString()), 0) { preview = null }
    }
}

@Composable
private fun rememberVideoThumb(uri: android.net.Uri): android.graphics.Bitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(uri) {
        runCatching {
            val r = android.media.MediaMetadataRetriever()
            r.setDataSource(context, uri)
            val frame = r.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            r.release()
            frame
        }.getOrNull()
    }
}

/**
 * 输入框里的文件附件卡片（官方 file chip）：文件类型徽标 + 文件名 + 大小 + 删除。
 * 之前用的是 64dp 方块，文件名被截成「docume」，什么都没有。
 */
@Composable
fun AttachmentFileChip(a: Attachment, onRemove: (Attachment) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val name = remember(a.uri) { queryFileName(ctx, a.uri, a.name) }
    val size = remember(a.uri) { queryFileSize(ctx, a.uri) }
    val ext = name.substringAfterLast('.', "").take(4).uppercase().ifBlank { "FILE" }
    Row(
        Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF3F4F6))
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE4EEFF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ext,
                fontSize = 10.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = androidx.compose.ui.graphics.Color(0xFF4C8DF6),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = name,
                fontSize = 13.sp,
                color = Color(0xFF1A1A1A),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (size > 0) {
                Text(text = formatFileSize(size), fontSize = 11.sp, color = Color(0xFF8A8F99))
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable { onRemove(a) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close_outline_20),
                contentDescription = "移除",
                tint = Color(0xFF9AA0A6),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
