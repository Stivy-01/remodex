package com.remodex.mobile.ui.design.canvas

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CanvasWebView(
    bridge: CanvasBridge,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            bridge.detach()
            webView?.destroy()
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                @SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                addJavascriptInterface(bridge, "AndroidCanvasBridge")

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        return true
                    }
                }
                webView = this
                bridge.attach(this)
                loadUrl("file:///android_asset/mobile_canvas_stub.html")
            }
        },
        modifier = modifier,
    )
}
