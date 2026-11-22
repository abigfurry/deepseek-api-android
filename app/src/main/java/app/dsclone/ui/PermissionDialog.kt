package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

/**
 * 权限「同意弹窗」。
 *
 * 用户诉求：点语音/相机时要先弹一个说明弹窗，同意了再走系统权限申请；被拒绝（尤其是勾了
 * 不再询问）时要有「去设置」入口，而不是点了没有任何反应。
 *
 * @param kind       "mic" 或 "camera"
 * @param denied     false = 申请前的说明弹窗；true = 被拒绝后的引导弹窗
 * @param canAsk     被拒绝后系统是否还允许再次弹（shouldShowRequestPermissionRationale）
 * @param onDismiss  关闭
 * @param onConfirm  同意 / 重试
 * @param onOpenSettings 去系统应用设置页
 */
@Composable
internal fun PermissionDialog(
    kind: String,
    denied: Boolean,
    canAsk: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ds = LocalDsColors.current
    val isMic = kind == "mic"
    val title = when {
        denied && isMic -> "麦克风权限未开启"
        denied -> "相机权限未开启"
        isMic -> "允许使用麦克风？"
        else -> "允许使用相机？"
    }
    val body = when {
        denied && isMic && !canAsk ->
            "你之前拒绝过，并且勾选了「不再询问」，所以系统不会再弹权限框。\n" +
                "请到 系统设置 → 应用 → 本应用 → 权限 里手动打开「麦克风」。"
        denied && !isMic && !canAsk ->
            "你之前拒绝过，并且勾选了「不再询问」，所以系统不会再弹权限框。\n" +
                "请到 系统设置 → 应用 → 本应用 → 权限 里手动打开「相机」。"
        denied -> "需要该权限才能继续，点「重试」再授权一次。"
        isMic ->
            "语音输入需要使用麦克风：按住说话时录制音频，发送给你自己配置的语音识别接口。" +
                "录音只在本机处理和上传到你指定的服务商，不经过开发者服务器。"
        else ->
            "拍照发图需要使用相机：拍摄的照片会作为附件，随消息一起发送给你选择的 AI 服务商。"
    }
    val confirmText = if (denied) (if (canAsk) "重试" else "去设置") else "同意"

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ds.cardBg)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(title, fontSize = 17.sp, lineHeight = 24.sp, color = ds.bodyText)
            Spacer(Modifier.height(10.dp))
            Text(body, fontSize = 14.sp, lineHeight = 21.sp, color = ds.secondary)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "取消",
                    fontSize = 16.sp,
                    color = ds.subText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = confirmText,
                    fontSize = 16.sp,
                    color = DsBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (denied && !canAsk) onOpenSettings() else onConfirm()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
