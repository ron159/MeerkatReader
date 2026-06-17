package com.capyreader.app.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import androidx.webkit.WebViewAssetLoader.ResourcesPathHandler
import com.capyreader.app.articleimages.ArticleImagePathHandler
import com.capyreader.app.articleimages.ArticleImageStore
import com.capyreader.app.common.AudioEnclosure
import com.capyreader.app.common.Media
import com.capyreader.app.common.WebViewInterface
import com.capyreader.app.ui.LocalTimeFormats
import com.capyreader.app.ui.articles.detail.articleTemplateColors
import com.capyreader.app.ui.articles.detail.byline
import com.capyreader.app.ui.articles.displayFeedName
import com.jocmp.capy.Article
import com.jocmp.capy.articles.ArticleRenderer
import com.jocmp.capy.common.DisplayTimeFormats
import com.jocmp.capy.logging.CapyLog
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebView(
    modifier: Modifier,
    state: WebViewState,
    article: Article? = null,
    showImages: Boolean = true,
    articleTopMarginPx: Int = 0,
    initialScrollPercent: Double = 0.0,
) {
    val timeFormats = LocalTimeFormats.current
    AndroidView(
        modifier = modifier,
        factory = { state.webView },
        update = {
            article?.let {
                state.loadHtml(article, showImages, timeFormats, articleTopMarginPx)
                state.restoreScrollPercent(article.id, initialScrollPercent)
            }
        }
    )
}

class AccompanistWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val onOpenLink: (url: Uri) -> Unit,
    private val httpClient: OkHttpClient,
) : WebViewClient(),
    KoinComponent {
    lateinit var state: WebViewState
        internal set

    var pageUrl: String? = null
        internal set

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val asset = assetLoader.shouldInterceptRequest(request.url)
        if (asset != null) {
            val headers = asset.responseHeaders ?: mutableMapOf()
            headers["Access-Control-Allow-Origin"] = "*"
            asset.responseHeaders = headers
            return asset
        }

        if (!shouldProxyRequest(request)) {
            return null
        }

        return proxyRequest(request)
    }

    private fun shouldProxyRequest(request: WebResourceRequest) =
        WebRequestProxyPolicy.shouldProxy(request.url.toString(), request, pageUrl)

    /**
     * Proxies requests to add CORS headers for cross-origin
     * requests (Issue #1616) and Referer headers for media CDNs (Issue #1878)
     */
    private fun proxyRequest(request: WebResourceRequest): WebResourceResponse? {
        return try {
            val okHttpRequest = Request.Builder()
                .url(request.url.toString())
                .apply {
                    request
                        .requestHeaders
                        .filterNot { it.key.equals("Accept-Encoding", ignoreCase = true) }
                        .forEach { (key, value) ->
                            header(key, value)
                        }
                    pageUrl?.let { header("Referer", it) }
                }
                .build()

            val response = httpClient.newCall(okHttpRequest).execute()
            val contentType = response.header("Content-Type") ?: "text/html"
            val mimeType = contentType.substringBefore(";").trim()

            val charset = contentType
                .substringAfter("charset=", "UTF-8")
                .substringBefore(";")
                .trim()

            val corsHeaders = mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                "Access-Control-Allow-Headers" to "*"
            )
            val responseHeaders = response.headers.toMap() + corsHeaders

            WebResourceResponse(
                mimeType,
                charset,
                response.code,
                response.message.ifEmpty { "OK" },
                responseHeaders,
                response.body.byteStream()
            )
        } catch (e: Exception) {
            CapyLog.error("webview_intercept_request", e)
            null
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url

        if (url != null) {
            onOpenLink(url)
        }

        return true
    }
}

@Stable
class WebViewState(
    private val renderer: ArticleRenderer,
    private val colors: Map<String, String>,
    private val enableNativeScroll: Boolean,
    internal val webView: WebView,
) {
    private var htmlId: String? = null
    private var contentHash: Int = 0
    private var restoredScrollArticleId: String? = null
    private var currentAudioUrl: String? = null
    private var isAudioPlaying: Boolean = false

    init {
        loadEmpty()
    }

    fun loadHtml(
        article: Article,
        showImages: Boolean,
        timeFormats: DisplayTimeFormats,
        articleTopMarginPx: Int,
    ) {
        val id = article.id
        val hash = listOf(article.content, showImages, articleTopMarginPx).hashCode()

        if (id == htmlId && hash == contentHash) {
            return
        }

        webView.isVerticalScrollBarEnabled = enableNativeScroll
        htmlId = id
        contentHash = hash
        restoredScrollArticleId = null
        webView.scrollTo(0, 0)

        val client = webView.webViewClient as? AccompanistWebViewClient
        client?.pageUrl = article.url?.toString()

        val html = renderer.render(
            article,
            hideImages = !showImages,
            byline = article.byline(context = webView.context, formats = timeFormats),
            colors = colors,
            feedName = article.displayFeedName(webView.context),
            articleTopMargin = "${articleTopMarginPx}px",
        )

        webView.loadDataWithBaseURL(
            null,
            html,
            null,
            "UTF-8",
            null,
        )
    }

    fun restoreScrollPercent(articleID: String, scrollPercent: Double) {
        if (articleID != htmlId || restoredScrollArticleId == articleID || scrollPercent <= 0.0) {
            return
        }

        restoredScrollArticleId = articleID
        webView.postDelayed({
            val targetY = (webView.maxScrollY() * scrollPercent.coerceIn(0.0, 1.0)).roundToInt()
            webView.scrollTo(0, targetY)
        }, RESTORE_SCROLL_DELAY_MS)
    }

    fun reset() {
        htmlId = null
        restoredScrollArticleId = null
        loadEmpty()
    }

    fun updateAudioPlayState(url: String?, isPlaying: Boolean) {
        currentAudioUrl = url
        isAudioPlaying = isPlaying
        if (htmlId == null) {
            return
        }
        webView.post {
            if (url != null) {
                val escapedUrl = url.replace("'", "\\'")
                webView.evaluateJavascript("updateAudioPlayState('$escapedUrl', $isPlaying)", null)
            } else {
                webView.evaluateJavascript("resetAudioPlayState()", null)
            }
        }
    }

    fun resetAudioPlayState() {
        updateAudioPlayState(null, false)
    }

    private fun loadEmpty() = webView.loadUrl("about:blank")

    companion object {
        private const val RESTORE_SCROLL_DELAY_MS = 250L
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun rememberWebViewState(
    renderer: ArticleRenderer = koinInject(),
    httpClient: OkHttpClient = koinInject(),
    articleImageStore: ArticleImageStore = koinInject(),
    onNavigateToMedia: (media: Media) -> Unit,
    onRequestLinkDialog: (link: ShareLink) -> Unit,
    onRequestImageDialog: (imageUrl: String) -> Unit = {},
    onOpenLink: (url: Uri) -> Unit,
    onOpenAudioPlayer: (audio: AudioEnclosure) -> Unit = {},
    onPauseAudio: () -> Unit = {},
    onScrollChanged: (scrollY: Int, oldScrollY: Int) -> Unit = { _, _ -> },
    onScrollProgressChanged: (scrollPercent: Double) -> Unit = {},
    enableTopSwipe: Boolean = false,
    enableBottomSwipe: Boolean = false,
    onSwipeDownFromTop: () -> Unit = {},
    onSwipeUpFromBottom: () -> Unit = {},
    currentAudioUrl: String? = null,
    isAudioPlaying: Boolean = false,
): WebViewState {
    val colors = articleTemplateColors()
    val context = LocalContext.current
    val edgeSwipeTriggerDistancePx = with(LocalDensity.current) { 80.dp.toPx() }
    val currentAudioUrlState by rememberUpdatedState(currentAudioUrl)
    val isAudioPlayingState by rememberUpdatedState(isAudioPlaying)
    val scrollChangedState by rememberUpdatedState(onScrollChanged)
    val scrollProgressChangedState by rememberUpdatedState(onScrollProgressChanged)
    val enableTopSwipeState = rememberUpdatedState(enableTopSwipe)
    val enableBottomSwipeState = rememberUpdatedState(enableBottomSwipe)
    val swipeDownFromTopState = rememberUpdatedState(onSwipeDownFromTop)
    val swipeUpFromBottomState = rememberUpdatedState(onSwipeUpFromBottom)
    val edgeSwipeTriggerDistanceState = rememberUpdatedState(edgeSwipeTriggerDistancePx)

    val client = remember {
        AccompanistWebViewClient(
            assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", AssetsPathHandler(context))
                .addPathHandler("/res/", ResourcesPathHandler(context))
                .addPathHandler("/article-images/", ArticleImagePathHandler(articleImageStore))
                .build(),
            onOpenLink = onOpenLink,
            httpClient = httpClient,
        )
    }

    return remember {
        val webViewInterface = WebViewInterface(
            navigateToMedia = { onNavigateToMedia(it) },
            onRequestLinkDialog = onRequestLinkDialog,
            onRequestImageDialog = onRequestImageDialog,
            onOpenAudioPlayer = onOpenAudioPlayer,
            onPauseAudio = onPauseAudio,
        )

        val webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                mediaPlaybackRequiresUserGesture = false
                domStorageEnabled = true
            }
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            setOnLongClickListener {
                hitTestResult.type == SRC_ANCHOR_TYPE
            }

            addJavascriptInterface(webViewInterface, WebViewInterface.INTERFACE_NAME)

            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            webViewClient = client

            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                scrollChangedState(scrollY, oldScrollY)
                scrollProgressChangedState(scrollY.toScrollPercent(this))
            }

            setOnTouchListener(
                WebViewEdgeSwipeTouchListener(
                    touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
                    triggerDistancePx = { edgeSwipeTriggerDistanceState.value },
                    isTopSwipeEnabled = { enableTopSwipeState.value },
                    isBottomSwipeEnabled = { enableBottomSwipeState.value },
                    onSwipeDownFromTop = { swipeDownFromTopState.value.invoke() },
                    onSwipeUpFromBottom = { swipeUpFromBottomState.value.invoke() },
                )
            )
        }

        WebViewState(
            renderer,
            colors,
            enableNativeScroll = true,
            webView,
        ).also {
            client.state = it
            webViewInterface.onRequestAudioState = {
                it.updateAudioPlayState(currentAudioUrlState, isAudioPlayingState)
            }
        }
    }
}

private fun WebView.maxScrollY(): Int {
    return ((contentHeight * scale) - height).roundToInt().coerceAtLeast(0)
}

private fun Int.toScrollPercent(webView: WebView): Double {
    val maxScrollY = webView.maxScrollY()
    return if (maxScrollY == 0) {
        0.0
    } else {
        (toDouble() / maxScrollY).coerceIn(0.0, 1.0)
    }
}

private enum class EdgeSwipeDirection {
    Top,
    Bottom,
}

private class WebViewEdgeSwipeTouchListener(
    private val touchSlopPx: Float,
    private val triggerDistancePx: () -> Float,
    private val isTopSwipeEnabled: () -> Boolean,
    private val isBottomSwipeEnabled: () -> Boolean,
    private val onSwipeDownFromTop: () -> Unit,
    private val onSwipeUpFromBottom: () -> Unit,
) : View.OnTouchListener {
    private var startX = 0f
    private var startY = 0f
    private var edgeStartY = 0f
    private var startedAtTop = false
    private var startedAtBottom = false
    private var activeDirection: EdgeSwipeDirection? = null
    private var thresholdReached = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val webView = view as? WebView ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                edgeStartY = event.y
                startedAtTop = !webView.canScrollVertically(-1)
                startedAtBottom = !webView.canScrollVertically(1)
                activeDirection = null
                thresholdReached = false
            }

            MotionEvent.ACTION_MOVE -> updateSwipe(webView, event)

            MotionEvent.ACTION_UP -> {
                triggerSwipeIfNeeded()
                reset()
            }

            MotionEvent.ACTION_CANCEL -> reset()
        }

        return false
    }

    private fun updateSwipe(webView: WebView, event: MotionEvent) {
        val totalDeltaX = event.x - startX
        val totalDeltaY = event.y - startY

        if (abs(totalDeltaY) < touchSlopPx || abs(totalDeltaX) > abs(totalDeltaY)) {
            return
        }

        if (activeDirection == null) {
            activeDirection = when {
                totalDeltaY > 0f && isTopSwipeEnabled() && !webView.canScrollVertically(-1) -> {
                    edgeStartY = if (startedAtTop) startY else event.y
                    EdgeSwipeDirection.Top
                }

                totalDeltaY < 0f && isBottomSwipeEnabled() && !webView.canScrollVertically(1) -> {
                    edgeStartY = if (startedAtBottom) startY else event.y
                    EdgeSwipeDirection.Bottom
                }

                else -> null
            }
        }

        val pullDistance = when (activeDirection) {
            EdgeSwipeDirection.Top -> event.y - edgeStartY
            EdgeSwipeDirection.Bottom -> edgeStartY - event.y
            null -> 0f
        }

        if (!thresholdReached && pullDistance >= triggerDistancePx()) {
            thresholdReached = true
            webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun triggerSwipeIfNeeded() {
        if (!thresholdReached) {
            return
        }

        when (activeDirection) {
            EdgeSwipeDirection.Top -> onSwipeDownFromTop()
            EdgeSwipeDirection.Bottom -> onSwipeUpFromBottom()
            null -> {}
        }
    }

    private fun reset() {
        activeDirection = null
        thresholdReached = false
    }
}
