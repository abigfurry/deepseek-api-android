package com.yuyuyu.deepseek.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.yuyuyu.deepseek.data.ProviderPreset

/** 打开外部链接（申请 API Key / 文档），失败就给个 Toast，绝不崩。 */
fun openUrl(ctx: Context, url: String) {
    val u = url.trim().ifEmpty { "https://platform.deepseek.com/api_keys" }
    val full = if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(full)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(ctx, "打不开链接：$full", Toast.LENGTH_SHORT).show()
    }
}

/** 某服务商的「申请 API Key」页面：优先 preset.keyUrl，没有就退回它 baseUrl 的官网首页。 */
fun providerKeyUrl(preset: ProviderPreset): String {
    if (preset.keyUrl.isNotBlank()) return preset.keyUrl
    val host = preset.baseUrl.substringAfter("://").substringBefore('/').substringBefore(':')
    return if (host.isBlank()) "https://platform.deepseek.com/api_keys" else "https://$host"
}
