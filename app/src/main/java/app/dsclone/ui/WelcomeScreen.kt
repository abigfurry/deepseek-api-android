package com.yuyuyu.deepseek.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuyuyu.deepseek.R
import java.time.LocalTime

/*
 * 官方 DeepSeek 2.9.0（com.deepseek.chat）「空会话欢迎页」复刻。
 *
 * 出处（反编译材料）：
 *  - 组件：com.deepseek.chat.ui.pages.chat.session.views.ChatWelcome (ChatWelcome.kt:119)，
 *          在 ChatSessionContent.kt:108 ChatSessionWelcome 里于「会话无消息」时渲染
 *          （smali: smali_classes3/l06.smali:18723 调用 Lpo9;->v）。
 *  - 鲸鱼：ChatWelcomeLargeLogo (ChatWelcomeLogo.kt:42) 动画/大图分支
 *          Modifier.size(120.0f dp)，用 res/drawable/ic_avatar_laydown.xml（144×144dp 矢量），
 *          tint = Color.Transparent（保留原色）；非大图分支 43×32dp 用 res/drawable/chat_welcome_logo.xml。
 *          （smali: sba.smali 方法 l 的 0x42f00000=120.0f / 0x422c0000=43.0f / 0x42000000=32.0f）
 *  - 间距：鲸鱼与问候语之间 Spacer(20.dp)；smali: po9.smali:29111 0x41a00000=20.0f
 *  - 字号/行高/字重：20sp / 28sp / W600
 *          （smali: po9.smali:27834 0x14=20 → TextUnit.sp；27846 0x1c=28；27858 Llu4;->e = FontWeight.W600）
 *  - 水平内边距 24dp（smali: po9.smali:28939 0x41c00000=24.0f + PaddingKt）
 *  - 文案：官方客户端没有「按小时」的字符串映射；空会话标题取服务端下发的 uiWelcomeMessage
 *          （ModelSettingsDefaults.kt:278），为空时回退 strings: welcome_message_title_unified
 *          （zh-Hans = 「今天有什么可以帮到你？」）。服务端会按时间/模型下发不同问候，所以用户
 *          观察到「每次不同、跟时间有关」。
 *          本复刻没有服务端配置，因此用 greetingForHour() 在本地按小时挑选文案来还原该观感。
 *
 * 注意：本文件只依赖 Compose + R.drawable.welcome_whale（新复制的资源），不引用其它工程符号。
 */

/** 官方 welcome_message_title_unified 的中文原文（res/values-b+zh+Hans/strings.xml）。 */
const val WELCOME_FALLBACK_TEXT: String = "今天有什么可以帮到你？"

/**
 * 按小时（0..23）选问候语。
 *
 * 官方 2.9.0 客户端**没有**这套映射（全量 smali + strings.xml 搜 早上好/上午好/…/夜深 均为 0 命中），
 * 文案由服务端 uiWelcomeMessage 下发；这里按 6 个时段还原「文案随时间变化」的观感。
 */
fun greetingForHour(hour: Int): String = when (hour) {
    5, 6, 7, 8 -> "早上好，今天想聊点什么？"
    9, 10, 11 -> "上午好，有什么可以帮到你？"
    12, 13 -> "中午好，想聊点什么？"
    14, 15, 16, 17 -> WELCOME_FALLBACK_TEXT
    18, 19, 20, 21, 22 -> "晚上好，今天过得怎么样？"
    else -> "夜深了，还没睡吗？" // 23, 0..4
}

/** 当前时间对应的问候语（进入页面时取一次，避免重组时跳动）。 */
@Composable
fun rememberGreetingText(): String = remember { greetingForHour(LocalTime.now().hour) }

/**
 * 官方空会话欢迎页（鲸鱼 + 问候语）。
 *
 * 直接铺满父容器并在其中居中；配合 DsApp 里「输入卡固定在下方」的布局即可。
 *
 * @param modifier 外层修饰符。
 * @param onSuggestion 预留给「推荐问题/快捷入口」的点击回调。官方 2.9.0 的空会话页**没有**推荐问题，
 *        只有鲸鱼 + 问候语（其下方的胶囊是模型选择器 ModelCapsuleTab/ModelConfigSelector，属于另一组件），
 *        所以本实现默认不渲染任何快捷入口，此参数仅为将来接线保留。
 */
@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") onSuggestion: (String) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 官方欢迎页只显示「鲸鱼」本体，**没有**蓝色圆角方块背景（那是桌面图标才有的底）。
            // 所以这里直接画 ic_whale_fg（它自身就是渐变填充的鲸鱼），不加底色、不额外染色。
            Image(
                painter = painterResource(R.drawable.ic_whale_fg),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.size(120.dp),
            )
            // 官方 Spacer(20.dp)
            Spacer(Modifier.height(20.dp))
            // 官方问候语样式：20sp / 行高 28sp / W600，居中
            Text(
                text = rememberGreetingText(),
                modifier = Modifier.padding(horizontal = 8.dp),
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.W600,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
