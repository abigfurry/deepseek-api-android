package com.yuyuyu.deepseek.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalDensity

/**
 * 用本地 KaTeX（assets/katex）渲染公式，返回渲染后的高度。
 * 官方 App 的公式走 JLaTeXMath，这里用 KaTeX 保证能和 Markdown 混排。
 */
private fun jsCall(latex: String, display: Boolean): String =
    "renderMath(" + org.json.JSONObject.quote(latex) + ", " + display + ");"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathText(latex: String, display: Boolean = true, modifier: Modifier = Modifier) {
    var heightDp by remember(latex) { mutableStateOf(40.dp) }
    val density = LocalDensity.current

    Box(modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onRendered(h: Int) {
                            val css = if (h > 0) h else 40
                            post {
                                // WebView 的 scrollHeight 是 CSS px（≈ dp），直接用，不要再除 density
                                heightDp = css.dp + 4.dp
                            }
                        }
                    }, "MathBridge")
                    val loader = androidx.webkit.WebViewAssetLoader.Builder()
                        .addPathHandler(
                            "/assets/",
                            androidx.webkit.WebViewAssetLoader.AssetsPathHandler(ctx),
                        )
                        .build()
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                        ): android.webkit.WebResourceResponse? =
                            loader.shouldInterceptRequest(request.url)

                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript(jsCall(latex, display), null)
                        }
                    }
                    loadUrl("https://appassets.androidplatform.net/assets/math.html")
                }
            },
            update = { wv ->
                wv.evaluateJavascript(jsCall(latex, display), null)
            },
        )
    }
}
