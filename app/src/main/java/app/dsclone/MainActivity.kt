package com.yuyuyu.deepseek

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yuyuyu.deepseek.data.Store
import com.yuyuyu.deepseek.ui.DsApp
import com.yuyuyu.deepseek.ui.theme.DsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前决定主题：窗口底色/启动画面第一帧由主题决定。
        // 之前窗口底色永远用 values/themes.xml 里的白色 → 深色模式下进 App 会「先白后黑」。
        val startupSettings = runCatching { Store.loadSettings(this) }.getOrNull()
        val savedAppearance = startupSettings?.appearance ?: "system"
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = savedAppearance == "dark" || (savedAppearance != "light" && systemDark)
        setTheme(if (dark) R.style.Theme_DS else R.style.Theme_DS_Light)
        // 关键：外观/字号必须在**第一次组合之前**就位。之前是在 DsTheme 的 LaunchedEffect 里异步读，
        // 首帧按「跟随系统」渲染（系统浅色 → 白），读到设置后才变深色 → 看到「先白再变黑」。
        if (startupSettings != null) {
            com.yuyuyu.deepseek.ui.theme.DsAppearance.apply(
                startupSettings.appearance,
                startupSettings.fontSizeLevel,
                startupSettings.followSystemFont,
            )
        }

        super.onCreate(savedInstanceState)
        // 调试开关默认关闭（之前带 --ez forceThink true 启动会一直卡在"正在思考"）
        DsDebug.forceThinking = false

        // 官方规则（MainActivity.smali 实测，见 _work\OFFICIAL-ADAPT.md）：
        //   宽高比 ≥ 1.75 且 smallestScreenWidthDp < 600 → 判定为手机 → 强制竖屏；
        //   平板/大屏 → UNSPECIFIED（可横竖、可分屏）。
        val c = resources.configuration
        val longSide = maxOf(c.screenWidthDp, c.screenHeightDp).toFloat()
        val shortSide = minOf(c.screenWidthDp, c.screenHeightDp).toFloat().coerceAtLeast(1f)
        val isPhone = c.smallestScreenWidthDp < 600 && (longSide / shortSide) >= 1.75f
        requestedOrientation = if (isPhone) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        // 官方全 APK 没有一处给状态栏/导航栏染色，而是 edge-to-edge + 关掉系统给导航栏
        // 补的那层「对比度遮罩」—— 否则深色模式底部会多出一条浅色带。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.setBackgroundDrawable(ColorDrawable(if (dark) 0xFF0F0F0F.toInt() else 0xFFFFFFFF.toInt()))

        enableEdgeToEdge()
        setContent {
            DsTheme {
                DsApp()
            }
        }
    }
}
