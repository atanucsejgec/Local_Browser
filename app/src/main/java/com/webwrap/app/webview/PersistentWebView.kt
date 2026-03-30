package com.webwrap.app.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import com.webwrap.app.data.AdBlocker
import com.webwrap.app.data.CookieHelper
import com.webwrap.app.data.DarkModeInjector
import com.webwrap.app.data.DownloadHelper
import java.io.ByteArrayInputStream

/**
 * BackgroundAudioWebView — Custom WebView that prevents
 * audio from pausing when window loses visibility.
 */
class BackgroundAudioWebView(context: Context) : WebView(context) {
    /** Override to prevent pausing audio when window goes invisible */
    override fun onWindowVisibilityChanged(visibility: Int) {
        if (WebViewHolder.backgroundAudioEnabled && visibility != View.VISIBLE) return
        super.onWindowVisibilityChanged(visibility)
    }

    /** Override to prevent pausing media in background */
    override fun onPause() {
        if (WebViewHolder.backgroundAudioEnabled) return
        super.onPause()
    }
}

/**
 * createPersistentWebView — Factory function to create a fully configured
 * WebView with ad blocking, shorts blocking, dark mode, download listener,
 * fullscreen video support, and background audio.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createPersistentWebView(
    context: Context,
    fullScreenContainer: FrameLayout,
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String) -> Unit = {},
    onProgressChanged: (Int) -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onUrlChanged: (String) -> Unit = {},
    onFullScreenEnter: () -> Unit = {},
    onFullScreenExit: () -> Unit = {},
    desktopMode: Boolean = false
): BackgroundAudioWebView {
    return BackgroundAudioWebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // ── WebView Settings ────────────────────────────
        configureWebViewSettings(settings, desktopMode)

        keepScreenOn = true
        CookieHelper.enableThirdPartyCookies(this)
        isScrollbarFadingEnabled = true
        scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY

        // Fullscreen state holders
        var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
        var zoomableContainer: ZoomableLayout? = null

        // ── WebViewClient ───────────────────────────────
        webViewClient = createWebViewClient(
            context = context,
            webViewRef = { this },
            onPageStarted = onPageStarted,
            onPageFinished = onPageFinished,
            onUrlChanged = onUrlChanged
        )

        // ── WebChromeClient ─────────────────────────────
        webChromeClient = createWebChromeClient(
            context = context,
            webView = this,
            fullScreenContainer = fullScreenContainer,
            onProgressChanged = onProgressChanged,
            onTitleChanged = onTitleChanged,
            onFullScreenEnter = onFullScreenEnter,
            onFullScreenExit = onFullScreenExit,
            getFullScreenCallback = { fullScreenCallback },
            setFullScreenCallback = { fullScreenCallback = it },
            getZoomableContainer = { zoomableContainer },
            setZoomableContainer = { zoomableContainer = it }
        )

        // ── Download Listener — handles file downloads ──
        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            DownloadHelper.downloadFile(context, url, contentDisposition, mimeType, userAgent)
        }
    }
}

// ══════════════════════════════════════════════════════
// SETTINGS: Configure all WebView settings
// ══════════════════════════════════════════════════════
/** Apply all required settings to WebView */
private fun configureWebViewSettings(settings: WebSettings, desktopMode: Boolean) {
    settings.apply {
        javaScriptEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        domStorageEnabled = true
        databaseEnabled = true
        cacheMode = WebSettings.LOAD_DEFAULT
        allowFileAccess = true
        allowContentAccess = true
        builtInZoomControls = true
        displayZoomControls = false
        setSupportZoom(true)
        loadsImagesAutomatically = true
        blockNetworkImage = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        useWideViewPort = true
        loadWithOverviewMode = true
        defaultTextEncodingName = "UTF-8"
        mediaPlaybackRequiresUserGesture = false
        setSupportMultipleWindows(true)
        if (desktopMode) {
            userAgentString = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        setGeolocationEnabled(true)
    }
}

// ══════════════════════════════════════════════════════
// WEBVIEW CLIENT: Page load, URL intercept, ad blocking
// ══════════════════════════════════════════════════════
/** Create WebViewClient with shorts redirect, ad blocking, visibility spoof */
private fun createWebViewClient(
    context: Context,
    webViewRef: () -> WebView,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String) -> Unit,
    onUrlChanged: (String) -> Unit
): WebViewClient {
    return object : WebViewClient() {

        /** Called when page starts loading — handles shorts redirect */
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            url?.let { currentUrl ->
                // Shorts redirect on direct URL load
                if (WebViewHolder.shortsBlockerEnabled && currentUrl.contains("/shorts/")) {
                    val convertedUrl = WebViewHolder.convertShortsUrl(currentUrl)
                    if (convertedUrl != currentUrl) {
                        view?.post { view.stopLoading(); view.loadUrl(convertedUrl) }
                        return
                    }
                }
                onPageStarted(currentUrl)
            }
        }

        /** Called when page finishes — injects ad blocker, dark mode, shorts blocker */
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            CookieHelper.saveCookies()
            url?.let {
                onPageFinished(it)
                onUrlChanged(it)
            }

            // Ad blocker CSS/JS injection
            if (WebViewHolder.adBlockEnabled && view != null) {
                injectAdBlocker(view, url ?: "")
            }

            // Visibility spoof — keeps media playing in background
            view?.evaluateJavascript(getVisibilitySpoofJs(), null)

            // Pause detector for manual pause tracking
            WebViewHolder.injectPauseDetector()

            // Shorts blocker CSS + JS injection
            if (WebViewHolder.shortsBlockerEnabled &&
                (url?.contains("youtube.com") == true || url?.contains("youtu.be") == true)
            ) {
                injectShortsBlocker(view)
                view?.evaluateJavascript(getShortsUrlWatcherJs(), null)
            }

            // Dark mode re-injection on page load if enabled
            if (WebViewHolder.darkModeEnabled) {
                DarkModeInjector.enableDarkMode(view)
            }
        }

        /** Intercept URL loads — shorts redirect, external intents */
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false

            // Shorts redirect
            if (WebViewHolder.shortsBlockerEnabled && url.contains("/shorts/")) {
                val convertedUrl = WebViewHolder.convertShortsUrl(url)
                if (convertedUrl != url) { view?.loadUrl(convertedUrl); return true }
            }

            // Track URL change
            onUrlChanged(url)

            // Handle external intents (tel:, mailto:, intent:)
            return handleExternalIntent(context, url)
        }

        /** Block ad requests at network level */
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            if (WebViewHolder.adBlockEnabled) {
                val requestUrl = request?.url?.toString() ?: ""
                if (AdBlocker.shouldBlock(requestUrl)) return AdBlocker.createEmptyResponse()
            }
            return super.shouldInterceptRequest(view, request)
        }

        /** Track URL changes from SPA navigation/redirects */
        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            url?.let { currentUrl ->
                if (WebViewHolder.shortsBlockerEnabled && currentUrl.contains("/shorts/")) {
                    val convertedUrl = WebViewHolder.convertShortsUrl(currentUrl)
                    if (convertedUrl != currentUrl) {
                        view?.post { view.loadUrl(convertedUrl) }
                        return
                    }
                }
                onUrlChanged(currentUrl)
            }
        }

        /** Accept SSL errors (for development — consider removing for production) */
        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.proceed()
        }
    }
}

// ══════════════════════════════════════════════════════
// CHROME CLIENT: Fullscreen, file chooser, progress
// ══════════════════════════════════════════════════════
/** Create WebChromeClient with fullscreen video, zoom, progress tracking */
private fun createWebChromeClient(
    context: Context,
    webView: BackgroundAudioWebView,
    fullScreenContainer: FrameLayout,
    onProgressChanged: (Int) -> Unit,
    onTitleChanged: (String) -> Unit,
    onFullScreenEnter: () -> Unit,
    onFullScreenExit: () -> Unit,
    getFullScreenCallback: () -> WebChromeClient.CustomViewCallback?,
    setFullScreenCallback: (WebChromeClient.CustomViewCallback?) -> Unit,
    getZoomableContainer: () -> ZoomableLayout?,
    setZoomableContainer: (ZoomableLayout?) -> Unit
): WebChromeClient {
    return object : WebChromeClient() {

        /** Track page load progress */
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            onProgressChanged(newProgress)
        }

        /** Track page title changes */
        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            title?.let { onTitleChanged(it) }
        }

        /** Enter fullscreen — wraps video in ZoomableLayout */
        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            super.onShowCustomView(view, callback)
            setFullScreenCallback(callback)
            webView.visibility = View.GONE

            val zoomable = ZoomableLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
            }
            zoomable.addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            fullScreenContainer.visibility = View.VISIBLE
            fullScreenContainer.addView(zoomable, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            setZoomableContainer(zoomable)
            WebViewHolder.fullscreenZoomable = zoomable
            WebViewHolder.isFullScreen = true
            onFullScreenEnter()
        }

        /** Exit fullscreen — restore normal WebView */
        override fun onHideCustomView() {
            super.onHideCustomView()
            getZoomableContainer()?.resetZoom()
            getZoomableContainer()?.cleanup()
            WebViewHolder.fullscreenZoomable = null
            setZoomableContainer(null)
            fullScreenContainer.removeAllViews()
            fullScreenContainer.visibility = View.GONE
            webView.visibility = View.VISIBLE
            getFullScreenCallback()?.onCustomViewHidden()
            setFullScreenCallback(null)
            WebViewHolder.isFullScreen = false
            onFullScreenExit()
        }

        /** File chooser — not implemented (returns false) */
        override fun onShowFileChooser(
            webView: WebView?, filePathCallback: ValueCallback<Array<android.net.Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean = false

        /** Handle window.open() — load URL in current WebView */
        override fun onCreateWindow(
            view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
        ): Boolean {
            val newWebView = WebView(context)
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            transport?.webView = newWebView
            resultMsg?.sendToTarget()
            newWebView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    request?.url?.toString()?.let { webView.loadUrl(it) }
                    return true
                }
            }
            return true
        }

        /** Auto-grant geolocation permission */
        override fun onGeolocationPermissionsShowPrompt(
            origin: String?, callback: GeolocationPermissions.Callback?
        ) { callback?.invoke(origin, true, false) }
    }
}

// ══════════════════════════════════════════════════════
// HELPERS: Ad blocker injection
// ══════════════════════════════════════════════════════
/** Inject ad-blocking CSS and JS into the page */
private fun injectAdBlocker(webView: WebView, url: String) {
    val css = AdBlocker.getAdBlockCss().replace("\n", "").replace("'", "\\'")
    webView.evaluateJavascript(
        "(function(){var s=document.createElement('style');s.innerHTML='$css';document.head.appendChild(s);})();", null
    )
    webView.evaluateJavascript(AdBlocker.getGeneralAdBlockJs(), null)
    if (url.contains("youtube.com") || url.contains("youtu.be")) {
        webView.evaluateJavascript(AdBlocker.getYouTubeAdSkipJs(), null)
    }
}

// ══════════════════════════════════════════════════════
// HELPERS: Shorts blocker injection
// ══════════════════════════════════════════════════════
/** Inject shorts blocker CSS + JS into YouTube pages */
private fun injectShortsBlocker(view: WebView?) {
    view ?: return
    val shortsCss = WebViewHolder.getShortsBlockerCss().replace("\n", "").replace("'", "\\'")
    view.evaluateJavascript(
        "(function(){var s=document.createElement('style');s.type='text/css';s.innerHTML='$shortsCss';document.head.appendChild(s);})();",
        null
    )
    view.evaluateJavascript(WebViewHolder.getShortsBlockerJs(), null)
}

// ══════════════════════════════════════════════════════
// HELPERS: Visibility spoof JS (keeps media alive)
// ══════════════════════════════════════════════════════
/** Returns JS that spoofs document visibility to keep media playing */
private fun getVisibilitySpoofJs(): String = """
    (function() {
        Object.defineProperty(document, 'hidden', {value:false,writable:false,configurable:true});
        Object.defineProperty(document, 'visibilityState', {value:'visible',writable:false,configurable:true});
        document.addEventListener('visibilitychange', function(e){e.stopImmediatePropagation();}, true);
    })();
""".trimIndent()

// ══════════════════════════════════════════════════════
// HELPERS: Handle external intents (tel:, mailto:)
// ══════════════════════════════════════════════════════
/** Open external apps for tel:, mailto:, intent: URLs */
private fun handleExternalIntent(context: Context, url: String): Boolean {
    return when {
        url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:") -> {
            try {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                )
            } catch (_: Exception) { }
            true
        }
        else -> false
    }
}

// ══════════════════════════════════════════════════════
// SHORTS URL WATCHER JS — Catches SPA navigation
// ══════════════════════════════════════════════════════
/** Returns JS that watches for YouTube shorts URLs and redirects to /watch */
private fun getShortsUrlWatcherJs(): String = """
    (function() {
        'use strict';
        if (window.__webwrap_shorts_watcher) return;
        window.__webwrap_shorts_watcher = true;
        function checkAndRedirectShorts() {
            var url = window.location.href;
            var match = url.match(/\/shorts\/([a-zA-Z0-9_-]+)/);
            if (match) {
                var videoId = match[1];
                var newUrl = url.replace(/\/shorts\/[a-zA-Z0-9_-]+/, '/watch?v=' + videoId);
                window.location.replace(newUrl);
            }
        }
        checkAndRedirectShorts();
        var origPushState = history.pushState;
        history.pushState = function() {
            origPushState.apply(this, arguments);
            setTimeout(checkAndRedirectShorts, 100);
        };
        var origReplaceState = history.replaceState;
        history.replaceState = function() {
            origReplaceState.apply(this, arguments);
            setTimeout(checkAndRedirectShorts, 100);
        };
        window.addEventListener('popstate', function() { setTimeout(checkAndRedirectShorts, 100); });
        setInterval(checkAndRedirectShorts, 1500);
        document.addEventListener('yt-navigate-start', function() { setTimeout(checkAndRedirectShorts, 200); });
        document.addEventListener('yt-navigate-finish', function() { setTimeout(checkAndRedirectShorts, 200); });
        document.addEventListener('click', function(e) {
            var target = e.target;
            while (target && target !== document) {
                if (target.tagName === 'A') {
                    var href = target.getAttribute('href') || '';
                    var shortsMatch = href.match(/\/shorts\/([a-zA-Z0-9_-]+)/);
                    if (shortsMatch) {
                        e.preventDefault(); e.stopPropagation();
                        window.location.href = '/watch?v=' + shortsMatch[1];
                        return false;
                    }
                }
                target = target.parentElement;
            }
        }, true);
    })();
""".trimIndent()