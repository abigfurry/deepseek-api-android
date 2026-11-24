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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuyuyu.deepseek.ui.theme.DsBlue
import com.yuyuyu.deepseek.ui.theme.LocalDsColors

/**
 * 「API 使用教程」弹窗 —— 给首次使用的用户讲清三件事：
 * 1) 怎么拿 API Key（各家的控制台）
 * 2) 怎么给 API 账户充值（DeepSeek 官方平台）
 * 3) 拿到后怎么填进本 App / 常见报错怎么处理
 *
 * 从开屏页（ApiKeySetupScreen）的「API 使用教程 ›」入口打开；也放进设置页便于二次查看。
 */
@Composable
internal fun ApiGuideDialog(onDismiss: () -> Unit) {
    val ds = LocalDsColors.current
    val ctx = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ds.cardBg)
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("API 使用教程", fontSize = 18.sp, color = ds.bodyText)
            Spacer(Modifier.height(4.dp))
            Text(
                "本 App 不提供账号，对话直接走你自己的 API 账户（按量计费）。",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = ds.secondary,
            )
            Spacer(Modifier.height(14.dp))

            Step("1", "获取 API Key")
            LinkText("DeepSeek 开放平台 → API keys → 创建 API key", "https://platform.deepseek.com/api_keys")
            Hint("Key 形如 sk-xxxxxxxx，**只显示一次**，立刻复制；粘贴时不要带空格或中文引号。")
            Hint("用其它服务商/中转站时，去对应控制台拿 Key，并在「服务商」里切换或选「自定义」填 Base URL。")

            Step("2", "给 API 账户充值")
            LinkText("打开「充值」页（支付宝 / 微信；海外 Paypal、银行卡）", "https://platform.deepseek.com/top_up")
            LinkText("在「账单」页查看余额与消费明细", "https://platform.deepseek.com/transactions")
            Hint("充值前需要先在平台完成**实名认证**。余额本身不过期，赠送额度有单独有效期。")
            Hint("App 内「设置 → API 与模型 → 查询余额」也能直接看到剩余额度。")

            Step("3", "填回本 App")
            Hint("开屏页 / 设置 → API 与模型：选服务商 → 粘 API Key → 选模型（DeepSeek 用 deepseek-chat）→ 返回发消息测试。")

            Step("4", "常见报错")
            Hint("HTTP 401：Key 错 / 已删除 / 多打了字符 → 重新复制。")
            Hint("HTTP 402 或 Insufficient Balance：**余额不足** → 按第 2 步充值。")
            Hint("HTTP 429：触发限流，等一会儿再试。")
            Hint("HTTP 400 且提示请改用 /v1/responses：该（中转）站停用了 chat/completions → 设置 → API 与模型 → **接口类型** 选 Responses API。")

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
    Text("$no. $title", fontSize = 15.sp, color = ds.bodyText)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Hint(text: String) {
    val ds = LocalDsColors.current
    Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = ds.secondary)
    Spacer(Modifier.height(5.dp))
}

@Composable
private fun LinkText(text: String, url: String) {
    val ctx = LocalContext.current
    Text(
        text = "$text ›",
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = DsBlue,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { openUrl(ctx, url) }
            .padding(vertical = 3.dp),
    )
    Spacer(Modifier.height(3.dp))
}
