package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

/** 开源仓库地址（设置 → 关于 → 开源项目 里展示、可点击打开）。 */
const val OPEN_SOURCE_REPO = "https://github.com/abigfurry/deepseek-api-android"
const val OPEN_SOURCE_RELEASES = "$OPEN_SOURCE_REPO/releases"
const val OPEN_SOURCE_LICENSE = "MIT License"

/**
 * 设置 → 关于 → 开源项目。
 *
 * 纯展示 + 一个可点击的仓库入口（用系统浏览器打开），不涉及网络请求。
 */
@Composable
internal fun OpenSourcePage() {
    val ds = LocalDsColors.current
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = "DeepSeek API 版本 · 开源项目",
            fontSize = 20.sp,
            lineHeight = 28.sp,
            color = ds.bodyText,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "本应用的全部源代码以 $OPEN_SOURCE_LICENSE 开源，你可以自由查看、修改、分发，" +
                "也欢迎提交 issue 与 PR。客户端不包含任何官方专有代码，仅通过公开 HTTP API 与第三方服务通信。",
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = ds.secondary,
        )

        Spacer(Modifier.height(18.dp))

        // 仓库入口
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ds.cardBg)
                .clickable { openUrl(ctx, OPEN_SOURCE_REPO) }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "GitHub 仓库",
                fontSize = 16.sp,
                color = ds.rowText,
                modifier = Modifier.weight(1f),
            )
            Text(text = "打开 ›", fontSize = 14.sp, color = DsBlue)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ds.cardBg)
                .clickable { openUrl(ctx, OPEN_SOURCE_RELEASES) }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "下载最新版本（Releases）",
                fontSize = 16.sp,
                color = ds.rowText,
                modifier = Modifier.weight(1f),
            )
            Text(text = "打开 ›", fontSize = 14.sp, color = DsBlue)
        }

        Spacer(Modifier.height(18.dp))
        Text("仓库地址", fontSize = 13.sp, color = ds.secondary)
        Spacer(Modifier.height(4.dp))
        Text(OPEN_SOURCE_REPO, fontSize = 13.sp, lineHeight = 19.sp, color = DsBlue)
        Spacer(Modifier.height(14.dp))
        Text("开源协议", fontSize = 13.sp, color = ds.secondary)
        Spacer(Modifier.height(4.dp))
        Text("$OPEN_SOURCE_LICENSE（另附非官方声明与商标归属说明，见 NOTICE.md）", fontSize = 13.sp, lineHeight = 19.sp, color = ds.rowText)
        Spacer(Modifier.height(14.dp))
        Text("使用的开源组件", fontSize = 13.sp, color = ds.secondary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Jetpack Compose · OkHttp · CameraX · Lottie · Coil · androidx.webkit\n以上库均遵循 Apache-2.0 等各自许可证。",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = ds.rowText,
        )
        Spacer(Modifier.height(14.dp))
        Text("联系 / 侵权投诉", fontSize = 13.sp, color = ds.secondary)
        Spacer(Modifier.height(4.dp))
        Text(LEGAL_CONTACT_EMAIL, fontSize = 13.sp, lineHeight = 19.sp, color = DsBlue)
        Spacer(Modifier.height(28.dp))
    }
}
