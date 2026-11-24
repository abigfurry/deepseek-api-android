package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * 「导入聊天记录」教程弹窗。
 *
 * 入口两处（同一个弹窗）：
 *   · 开屏页（ApiKeySetupScreen）「API 使用教程…」下面一行；
 *   · 设置 → 数据管理 → 「导入聊天记录」旁边一行。
 *
 * 说明 A：本 App 旧设备 → 新设备和「设置 → 数据管理 → 导出/导入聊天记录」对应，
 *         导出得到 dsclone-chats.json（就是本机 sessions.json 的文本）。
 * 说明 B：官方 DeepSeek App 真实可用的导出路径（反编译 + 设备实测 2.9.0 确认）：
 *         设置 → 数据管理 → 导出所有历史对话（HistoryExportPage）。
 *         官方是在服务端打包「账号信息 + 全部历史对话」，返回一个下载链接
 *         （smali: HistoryExportFinishedResponse.history_download_url / exported_at；
 *          文案 export_all_desc「…下载链接的有效期为 7 天」，export_all_expiry_notice_desc
 *          「请前往浏览器粘贴链接开始下载」）。
 *         1.0.14 起本 App 能直接解析这个 ZIP：按 chat_session_index.json 排序拆成会话，
 *         REQUEST→用户、RESPONSE→回答、THINK→思考、TOOL_*→联网步骤、FILE→附件名；
 *         只导入当前分支（被「重新生成」丢弃的分支不导入），图片本体不导入。
 */
@Composable
internal fun ImportGuideDialog(onDismiss: () -> Unit) {
    val ds = LocalDsColors.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ds.cardBg)
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("导入聊天记录", fontSize = 18.sp, color = ds.bodyText)
            Spacer(Modifier.height(4.dp))
            Text(
                "聊天记录只保存在本机，不会上传到任何服务器。换手机 / 从官方 App 搬过来，按下面两种方式操作。",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = ds.secondary,
            )
            Spacer(Modifier.height(14.dp))

            Step("A", "从本 App 的旧设备迁移（能保留对话结构，推荐）")
            Hint("旧设备：设置 → 数据管理 → 导出聊天记录，用系统文件选择器保存，文件名默认 dsclone-chats.json。")
            Hint("把 dsclone-chats.json 用微信 / 网盘 / U 盘 / 数据线拷到新设备。")
            Hint("新设备：设置 → 数据管理 → 导入聊天记录，选中这个文件即可。")
            Hint("合并规则：同一会话 id 会被文件里的版本覆盖，其余会话追加到会话列表最前面。")

            Step("B", "从官方 DeepSeek App 搬过来（直接导入官方导出包）")
            Hint("官方导出：点左下角头像进「设置」→ 数据管理 → 导出所有历史对话 →「重新导出」/「复制下载链接」，再到浏览器粘贴链接下载，得到一个 .zip。下载链接 7 天内有效。")
            Hint("本 App：设置 → 数据管理 → 导入聊天记录，选中这个 .zip 即可。会自动按会话拆开导入，几千条对话也能一次导完（大包要等几十秒，屏幕上有进度）。")
            Hint("能导入的内容：用户提问、回答、思考过程（含用时）、联网搜索/浏览步骤、搜索来源、附件文件名；会话标题、置顶、更新时间都按官方顺序保留。")
            Hint("不导入的内容：官方里「重新生成」后被放弃的分支（只保留当前显示的那条回答），以及图片/文件本体（只记文件名和大小，导出包里本来也只是附件，不占本 App 空间）。")
            Hint("导入按会话 id 去重：同一份包重复导入只会覆盖，不会出现两份。超长消息 / 超大会话超过内存上限的部分会被截断或跳过，导入完成的提示里会给出数量。")

            Section("常见问题")
            Hint("提示「导入失败：无法识别这个文件」：多半选错文件。本 App 认两种：自己导出的 dsclone-chats.json（整个文件是 [ … ] 数组、每项带 messages 字段），和官方导出的 .zip（里面应有 manifest.json / chat_session_index.json / chat_sessions/）。聊天截图、txt 都不行。")
            Hint("只导入了一部分：官方下载没完成 / 网盘还在同步 / 微信传文件被压缩。重新下载一次再导入；导入完成的提示会说明跳过了多少个会话。")
            Hint("导入后在列表里看不到：会话按官方索引顺序排在列表里（置顶的排前面）；往上滑到顶部找。")
            Hint("图片和文件：导出只记录文件名和大小，不打包图片本体，换设备后附件要重新发送。")
            Hint("API Key 不会随聊天记录迁移：新设备请在开屏页 / 设置 → API 与模型里重新填。")

            Spacer(Modifier.height(16.dp))
            Text(
                "我知道了",
                fontSize = 16.sp,
                color = DsBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Step(no: String, title: String) {
    val ds = LocalDsColors.current
    Spacer(Modifier.height(6.dp))
    Text("$no. $title", fontSize = 15.sp, lineHeight = 21.sp, color = ds.bodyText)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Section(title: String) {
    val ds = LocalDsColors.current
    Spacer(Modifier.height(10.dp))
    Text(title, fontSize = 15.sp, lineHeight = 21.sp, color = ds.bodyText)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Hint(text: String) {
    val ds = LocalDsColors.current
    Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = ds.secondary)
    Spacer(Modifier.height(5.dp))
}
