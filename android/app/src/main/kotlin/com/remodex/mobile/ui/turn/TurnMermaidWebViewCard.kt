package com.remodex.mobile.ui.turn

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt
import org.json.JSONObject

private const val MermaidAssetDomain = "appassets.androidplatform.net"
private const val MermaidAssetPath = "/assets/mermaid/index.html"
private const val MermaidAssetUrl = "https://$MermaidAssetDomain$MermaidAssetPath"
private const val MermaidRenderTimeoutMs = 8_000L
private const val MermaidHeightRetryDelayMs = 120L
private const val MermaidKnownHeightCacheLimit = 96
private val MermaidInitialHeight = 160.dp
private val MermaidMinHeight = 120.dp
private val MermaidMaxHeight = 900.dp

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun TurnMermaidWebViewCard(
    code: String,
    darkMode: Boolean,
    state: TurnMermaidWebViewState,
    modifier: Modifier = Modifier,
    fillsAvailableSpace: Boolean = false,
    enablesInteraction: Boolean = false,
) {
    val normalizedCode = remember(code) { code.trimEnd() }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val resolvedModifier =
        if (fillsAvailableSpace) {
            modifier.fillMaxSize()
        } else {
            modifier.height(clampMermaidHeight(state.heightDp))
        }

    DisposableEffect(state) {
        onDispose {
            webViewRef.value?.let { state.detach(it) }
            webViewRef.value = null
        }
    }

    AndroidView(
        factory = { factoryContext ->
            WebView(factoryContext).also { webView ->
                webViewRef.value = webView
                webView.layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                )
                state.attach(webView, enablesInteraction)
                state.updateSource(normalizedCode, darkMode)
            }
        },
        modifier = resolvedModifier,
        update = { webView ->
            webViewRef.value = webView
            state.attach(webView, enablesInteraction)
            state.updateSource(normalizedCode, darkMode)
        },
    )
}

internal class TurnMermaidWebViewState {
    var heightDp by mutableStateOf(MermaidInitialHeight)
        private set

    var shouldShowFallback by mutableStateOf(false)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var currentSource = ""
    private var currentDarkMode = false
    private var interactionEnabled = false
    private var pageLoaded = false
    private var renderGeneration = 0
    private var heightProbePass = 0
    private var disposed = false
    private var timeoutScheduled = false
    private var currentHeightCacheKey: MermaidHeightCacheKey? = null

    fun attach(
        webView: WebView,
        enablesInteraction: Boolean = false,
    ) {
        if (disposed) return
        if (this.webView === webView && interactionEnabled == enablesInteraction) return

        this.webView = webView
        interactionEnabled = enablesInteraction
        configureWebView(webView)
        if (!pageLoaded) {
            loadPreviewPage(webView)
        } else {
            renderCurrentSource()
        }
    }

    fun updateSource(
        source: String,
        darkMode: Boolean,
    ) {
        if (disposed || shouldShowFallback) return
        if (source == currentSource && darkMode == currentDarkMode) return

        currentSource = source
        currentDarkMode = darkMode
        currentHeightCacheKey = MermaidHeightCacheKey(source, darkMode).also { key ->
            MermaidKnownHeightCache.get(key)?.let { heightDp = it }
        }
        if (pageLoaded) {
            renderCurrentSource()
        }
    }

    fun dispose() {
        disposed = true
        mainHandler.removeCallbacksAndMessages(null)
        detach(webView)
    }

    fun detach(webView: WebView?) {
        if (this.webView !== webView) return
        this.webView = null
        webView?.apply {
            stopLoading()
            webViewClient = WebViewClient()
            destroy()
        }
    }

    private fun configureWebView(webView: WebView) {
        val assetLoader =
            WebViewAssetLoader.Builder()
                .setDomain(MermaidAssetDomain)
                .addPathHandler(
                    "/assets/",
                    WebViewAssetLoader.AssetsPathHandler(webView.context.applicationContext),
                )
                .build()
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = interactionEnabled
            displayZoomControls = false
            setSupportZoom(interactionEnabled)
            loadWithOverviewMode = interactionEnabled
            useWideViewPort = interactionEnabled
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
        }
        webView.setBackgroundColor(AndroidColor.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = interactionEnabled
        webView.isHorizontalScrollBarEnabled = interactionEnabled
        webView.isNestedScrollingEnabled = interactionEnabled
        webView.overScrollMode =
            if (interactionEnabled) {
                android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            } else {
                android.view.View.OVER_SCROLL_NEVER
            }
        webView.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    return !isAllowedUrl(request.url)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    url: String,
                ): Boolean {
                    return !isAllowedUrl(Uri.parse(url))
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val url = request.url
                    return if (isAllowedUrl(url)) {
                        assetLoader.shouldInterceptRequest(url)
                    } else {
                        blockedResponse()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun shouldInterceptRequest(
                    view: WebView,
                    url: String,
                ): WebResourceResponse? {
                    val parsed = Uri.parse(url)
                    return if (isAllowedUrl(parsed)) {
                        assetLoader.shouldInterceptRequest(parsed)
                    } else {
                        blockedResponse()
                    }
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String,
                ) {
                    if (disposed || shouldShowFallback) return
                    if (!isAllowedUrl(Uri.parse(url))) {
                        fail()
                        return
                    }
                    pageLoaded = true
                    timeoutScheduled = false
                    renderCurrentSource()
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        fail()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame) {
                        fail()
                    }
                }
            }
    }

    private fun loadPreviewPage(webView: WebView) {
        if (disposed || shouldShowFallback) return
        pageLoaded = false
        webView.loadUrl(MermaidAssetUrl)
        schedulePageTimeout()
    }

    private fun renderCurrentSource() {
        if (disposed || shouldShowFallback || !pageLoaded) return
        val webView = webView ?: return
        val source = currentSource.trimEnd()
        if (source.isBlank()) {
            fail()
            return
        }

        val generation = ++renderGeneration
        heightProbePass = 0
        try {
            webView.post {
                if (disposed || shouldShowFallback || generation != renderGeneration) return@post
                try {
                    webView.evaluateJavascript(buildRenderScript(source, currentDarkMode)) { result ->
                        if (disposed || shouldShowFallback || generation != renderGeneration) {
                            return@evaluateJavascript
                        }
                        if (result.toBooleanStrictOrNull() == true) {
                            requestHeightMeasure(generation)
                        } else {
                            fail()
                        }
                    }
                } catch (_: Throwable) {
                    fail()
                }
            }
        } catch (_: Throwable) {
            fail()
        }
    }

    private fun requestHeightMeasure(generation: Int) {
        if (disposed || shouldShowFallback || generation != renderGeneration) return
        val webView = webView ?: return
        try {
            webView.post {
                if (disposed || shouldShowFallback || generation != renderGeneration) return@post
                try {
                    webView.evaluateJavascript(heightScript()) { result ->
                        if (disposed || shouldShowFallback || generation != renderGeneration) {
                            return@evaluateJavascript
                        }
                        val heightPx = result?.trim()?.toFloatOrNull()
                        if (heightPx == null || heightPx <= 0f) {
                            if (heightProbePass == 0) {
                                heightProbePass = 1
                                scheduleHeightRetry(generation)
                            } else {
                                fail()
                            }
                            return@evaluateJavascript
                        }

                        val density = webView.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
                        val measuredHeight = ((heightPx / density).roundToInt()).dp
                        val clampedHeight = measuredHeight.coerceIn(MermaidMinHeight, MermaidMaxHeight)
                        if (clampedHeight != heightDp) {
                            heightDp = clampedHeight
                        }
                        currentHeightCacheKey?.let { MermaidKnownHeightCache.put(it, clampedHeight) }

                        if (heightProbePass == 0) {
                            heightProbePass = 1
                            scheduleHeightRetry(generation)
                        }
                    }
                } catch (_: Throwable) {
                    if (heightProbePass == 0) {
                        heightProbePass = 1
                        scheduleHeightRetry(generation)
                    } else {
                        fail()
                    }
                }
            }
        } catch (_: Throwable) {
            fail()
        }
    }

    private fun schedulePageTimeout() {
        if (timeoutScheduled || disposed || shouldShowFallback) return
        timeoutScheduled = true
        mainHandler.postDelayed(
            {
                timeoutScheduled = false
                if (!disposed && !shouldShowFallback && !pageLoaded) {
                    fail()
                }
            },
            MermaidRenderTimeoutMs,
        )
    }

    private fun scheduleHeightRetry(generation: Int) {
        mainHandler.postDelayed(
            {
                if (!disposed && !shouldShowFallback && generation == renderGeneration) {
                    requestHeightMeasure(generation)
                }
            },
            MermaidHeightRetryDelayMs,
        )
    }

    private fun fail() {
        if (disposed || shouldShowFallback) return
        shouldShowFallback = true
        timeoutScheduled = false
        mainHandler.removeCallbacksAndMessages(null)
        webView?.stopLoading()
    }

    private fun isAllowedUrl(url: Uri): Boolean {
        return url.scheme == "https" && url.host == MermaidAssetDomain
    }

    private fun blockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            403,
            "Blocked",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    private fun buildRenderScript(
        source: String,
        darkMode: Boolean,
    ): String {
        val quotedSource = JSONObject.quote(source)
        return "(function(){try{window.renderRemodexMermaid($quotedSource, $darkMode);return true;}catch(_){return false;}})()"
    }

    private fun heightScript(): String {
        return "(function(){try{return Number(window.remodexMermaidHeight ? window.remodexMermaidHeight() : -1);}catch(_){return -1;}})()"
    }
}

private fun clampMermaidHeight(height: Dp): Dp {
    return when {
        height < MermaidMinHeight -> MermaidMinHeight
        height > MermaidMaxHeight -> MermaidMaxHeight
        else -> height
    }
}

internal data class MermaidHeightCacheKey(
    val source: String,
    val darkMode: Boolean,
)

internal object MermaidKnownHeightCache {
    private val heights = linkedMapOf<MermaidHeightCacheKey, Dp>()

    @Synchronized
    fun get(key: MermaidHeightCacheKey): Dp? {
        val value = heights.remove(key) ?: return null
        heights[key] = value
        return value
    }

    @Synchronized
    fun put(
        key: MermaidHeightCacheKey,
        height: Dp,
    ) {
        heights.remove(key)
        heights[key] = height.coerceIn(MermaidMinHeight, MermaidMaxHeight)
        while (heights.size > MermaidKnownHeightCacheLimit) {
            val first = heights.keys.firstOrNull() ?: break
            heights.remove(first)
        }
    }

    @Synchronized
    fun clear() {
        heights.clear()
    }

    @Synchronized
    fun size(): Int = heights.size
}
