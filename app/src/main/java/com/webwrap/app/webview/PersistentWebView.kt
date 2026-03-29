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
import com.webwrap.app.feature.darkmode.WebDarkMode
import com.webwrap.app.feature.download.DownloadHelper
import com.webwrap.app.feature.https.HttpsEnforcer
import com.webwrap.app.feature.incognito.IncognitoManager

// ============================================================
// BACKGROUND AUDIO WEBVIEW
// Overrides visibility/pause to keep audio alive.
// ============================================================

/**
 * Custom WebView that prevents pause when
 * background audio mode is enabled.
 */
class BackgroundAudioWebView(
    context: Context
) : WebView(context) {

    /** Block visibility change if bg audio on. */
    override fun onWindowVisibilityChanged(
        visibility: Int
    ) {
        if (WebViewHolder.backgroundAudioEnabled &&
            visibility != View.VISIBLE
        ) return
        super.onWindowVisibilityChanged(visibility)
    }

    /** Block pause if bg audio on. */
    override fun onPause() {
        if (WebViewHolder.backgroundAudioEnabled) {
            return
        }
        super.onPause()
    }
}

// ============================================================
// CREATE PERSISTENT WEBVIEW
// Main factory function for WebView instances.
// ============================================================

/**
 * Creates and configures a WebView with all features:
 * - Ad blocking
 * - Shorts blocking
 * - Background audio
 * - Downloads
 * - HTTPS enforcement
 * - Incognito mode
 * - Dark mode
 * - Full screen support
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
    desktopMode: Boolean = false,
    isIncognito: Boolean = false,
    darkModeEnabled: Boolean = false
): BackgroundAudioWebView {

    return BackgroundAudioWebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // ===== SETTINGS =====
        if (isIncognito) {
            configureIncognitoSettings()
        } else {
            configureNormalSettings(desktopMode)
        }

        keepScreenOn = true

        // Enable cookies per-WebView
        if (!isIncognito) {
            CookieHelper.enableThirdPartyCookies(this)
        } else {
            IncognitoManager.configureCookies(this)
        }

        isScrollbarFadingEnabled = true
        scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY

        // ===== DOWNLOAD LISTENER =====
        setDownloadListener { url, userAgent,
                              contentDisposition, mimeType,
                              contentLength ->
            DownloadHelper.download(
                context = context,
                url = url,
                userAgent = userAgent,
                contentDisposition =
                    contentDisposition,
                mimeType = mimeType,
                contentLength = contentLength
            )
        }

        // ===== FULLSCREEN STATE =====
        var fullScreenCallback:
                WebChromeClient.CustomViewCallback? = null
        var zoomableContainer:
                ZoomableLayout? = null

        // ===== WEB VIEW CLIENT =====
        webViewClient = createWebViewClient(
            webView = this,
            isIncognito = isIncognito,
            darkModeEnabled = darkModeEnabled,
            onPageStarted = onPageStarted,
            onPageFinished = onPageFinished,
            onUrlChanged = onUrlChanged
        )

        // ===== WEB CHROME CLIENT =====
        webChromeClient = createWebChromeClient(
            webView = this,
            context = context,
            fullScreenContainer =
                fullScreenContainer,
            fullScreenCallbackRef = {
                fullScreenCallback
            },
            setFullScreenCallback = {
                fullScreenCallback = it
            },
            zoomableRef = { zoomableContainer },
            setZoomable = {
                zoomableContainer = it
            },
            onProgressChanged = onProgressChanged,
            onTitleChanged = onTitleChanged,
            onFullScreenEnter = onFullScreenEnter,
            onFullScreenExit = onFullScreenExit
        )

        // Apply dark mode if enabled
        if (darkModeEnabled) {
            WebDarkMode.apply(this, true)
        }
    }
}

// ============================================================
// NORMAL WEBVIEW SETTINGS
// ============================================================

/** Configure standard WebView settings. */
@SuppressLint("SetJavaScriptEnabled")
private fun BackgroundAudioWebView
        .configureNormalSettings(
    desktopMode: Boolean
) {
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
        mixedContentMode =
            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        useWideViewPort = true
        loadWithOverviewMode = true
        defaultTextEncodingName = "UTF-8"
        mediaPlaybackRequiresUserGesture = false
        setSupportMultipleWindows(true)
        setGeolocationEnabled(true)

        if (desktopMode) {
            userAgentString = buildString {
                append("Mozilla/5.0 ")
                append("(Macintosh; Intel Mac OS X ")
                append("10_15_7) ")
                append("AppleWebKit/537.36 ")
                append("(KHTML, like Gecko) ")
                append("Chrome/120.0.0.0 ")
                append("Safari/537.36")
            }
        }
    }
}

/** Configure incognito WebView settings. */
@SuppressLint("SetJavaScriptEnabled")
private fun BackgroundAudioWebView
        .configureIncognitoSettings() {
    IncognitoManager.configureWebSettings(settings)
}

// ============================================================
// WEB VIEW CLIENT FACTORY
// ============================================================

/** Create WebViewClient with all intercepts. */
private fun createWebViewClient(
    webView: BackgroundAudioWebView,
    isIncognito: Boolean,
    darkModeEnabled: Boolean,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String) -> Unit,
    onUrlChanged: (String) -> Unit
): WebViewClient {
    return object : WebViewClient() {

        /** Handle page start — shorts redirect. */
        override fun onPageStarted(
            view: WebView?,
            url: String?,
            favicon: Bitmap?
        ) {
            super.onPageStarted(view, url, favicon)
            url?.let { currentUrl ->
                // Shorts redirect on direct load
                if (shouldRedirectShorts(currentUrl)) {
                    val converted =
                        WebViewHolder.convertShortsUrl(
                            currentUrl
                        )
                    if (converted != currentUrl) {
                        view?.post {
                            view.stopLoading()
                            view.loadUrl(converted)
                        }
                        return
                    }
                }
                onPageStarted(currentUrl)
            }
        }

        /** Handle page finish — inject scripts. */
        override fun onPageFinished(
            view: WebView?,
            url: String?
        ) {
            super.onPageFinished(view, url)

            // Save cookies (skip in incognito)
            if (!isIncognito) {
                CookieHelper.saveCookies()
            }

            url?.let {
                onPageFinished(it)
                onUrlChanged(it)
            }

            if (view == null) return

            // Inject ad blocker
            if (WebViewHolder.adBlockEnabled) {
                injectAdBlocker(view, url ?: "")
            }

            // Inject visibility spoof
            injectVisibilitySpoof(view)

            // Inject pause detector
            WebViewHolder.injectPauseDetector()

            // Inject shorts blocker
            injectShortsBlocker(view, url)

            // Apply dark mode
            if (darkModeEnabled) {
                WebDarkMode.apply(view, true)
            }
        }

        /** Handle URL loading — HTTPS + shorts. */
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val url = request?.url?.toString()
                ?: return false

            // HTTPS enforcement
            val upgraded =
                HttpsEnforcer.shouldUpgrade(url)
            if (upgraded != null) {
                view?.loadUrl(upgraded)
                return true
            }

            // Shorts redirect
            if (shouldRedirectShorts(url)) {
                val converted =
                    WebViewHolder.convertShortsUrl(url)
                if (converted != url) {
                    view?.loadUrl(converted)
                    return true
                }
            }

            // Track URL change
            onUrlChanged(url)

            // Handle special schemes
            return handleSpecialSchemes(
                view?.context, url
            )
        }

        /** Intercept requests — block ads. */
        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            if (WebViewHolder.adBlockEnabled) {
                val reqUrl =
                    request?.url?.toString() ?: ""
                if (AdBlocker.shouldBlock(reqUrl)) {
                    return AdBlocker
                        .createEmptyResponse()
                }
            }
            return super.shouldInterceptRequest(
                view, request
            )
        }

        /** Track URL from redirects. */
        override fun doUpdateVisitedHistory(
            view: WebView?,
            url: String?,
            isReload: Boolean
        ) {
            super.doUpdateVisitedHistory(
                view, url, isReload
            )
            url?.let { currentUrl ->
                if (shouldRedirectShorts(
                        currentUrl
                    )
                ) {
                    val converted =
                        WebViewHolder
                            .convertShortsUrl(
                                currentUrl
                            )
                    if (converted != currentUrl) {
                        view?.post {
                            view.loadUrl(converted)
                        }
                        return
                    }
                }
                onUrlChanged(currentUrl)
            }
        }

        /** Handle SSL errors — proceed. */
        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            // TODO: Show user dialog in production
            handler?.proceed()
        }
    }
}

// ============================================================
// WEB CHROME CLIENT FACTORY
// ============================================================

/** Create WebChromeClient for fullscreen, etc. */
private fun createWebChromeClient(
    webView: BackgroundAudioWebView,
    context: Context,
    fullScreenContainer: FrameLayout,
    fullScreenCallbackRef: () ->
    WebChromeClient.CustomViewCallback?,
    setFullScreenCallback: (
        WebChromeClient.CustomViewCallback?
    ) -> Unit,
    zoomableRef: () -> ZoomableLayout?,
    setZoomable: (ZoomableLayout?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onTitleChanged: (String) -> Unit,
    onFullScreenEnter: () -> Unit,
    onFullScreenExit: () -> Unit
): WebChromeClient {
    return object : WebChromeClient() {

        /** Track page loading progress. */
        override fun onProgressChanged(
            view: WebView?,
            newProgress: Int
        ) {
            super.onProgressChanged(
                view, newProgress
            )
            onProgressChanged(newProgress)
        }

        /** Track page title changes. */
        override fun onReceivedTitle(
            view: WebView?,
            title: String?
        ) {
            super.onReceivedTitle(view, title)
            title?.let { onTitleChanged(it) }
        }

        /** Enter fullscreen with ZoomableLayout. */
        override fun onShowCustomView(
            view: View?,
            callback: CustomViewCallback?
        ) {
            super.onShowCustomView(view, callback)
            setFullScreenCallback(callback)
            webView.visibility = View.GONE

            val zoomable = ZoomableLayout(
                context
            ).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams
                            .MATCH_PARENT,
                        FrameLayout.LayoutParams
                            .MATCH_PARENT
                    )
                setBackgroundColor(
                    android.graphics.Color.BLACK
                )
            }
            zoomable.addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams
                        .MATCH_PARENT,
                    FrameLayout.LayoutParams
                        .MATCH_PARENT
                )
            )
            fullScreenContainer.visibility =
                View.VISIBLE
            fullScreenContainer.addView(
                zoomable,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams
                        .MATCH_PARENT,
                    FrameLayout.LayoutParams
                        .MATCH_PARENT
                )
            )
            setZoomable(zoomable)
            WebViewHolder.fullscreenZoomable =
                zoomable
            WebViewHolder.isFullScreen = true
            onFullScreenEnter()
        }

        /** Exit fullscreen — cleanup. */
        override fun onHideCustomView() {
            super.onHideCustomView()
            zoomableRef()?.resetZoom()
            zoomableRef()?.cleanup()
            WebViewHolder.fullscreenZoomable = null
            setZoomable(null)
            fullScreenContainer.removeAllViews()
            fullScreenContainer.visibility = View.GONE
            webView.visibility = View.VISIBLE
            fullScreenCallbackRef()
                ?.onCustomViewHidden()
            setFullScreenCallback(null)
            WebViewHolder.isFullScreen = false
            onFullScreenExit()
        }

        /** File chooser — return false for now. */
        override fun onShowFileChooser(
            webView: WebView?,
            callback: ValueCallback<Array<
                    android.net.Uri>>?,
            params: FileChooserParams?
        ): Boolean = false

        /** Handle window.open — load in same view. */
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val newWv = WebView(context)
            val transport = resultMsg?.obj
                    as? WebView.WebViewTransport
            transport?.webView = newWv
            resultMsg?.sendToTarget()
            newWv.webViewClient = object :
                WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    v: WebView?,
                    req: WebResourceRequest?
                ): Boolean {
                    req?.url?.toString()?.let {
                        webView.loadUrl(it)
                    }
                    return true
                }
            }
            return true
        }

        /** Auto-grant geolocation permission. */
        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {
            callback?.invoke(origin, true, false)
        }
    }
}

// ============================================================
// HELPER: SHORTS REDIRECT CHECK
// ============================================================

/** Check if URL should be redirected from shorts. */
private fun shouldRedirectShorts(url: String): Boolean {
    return WebViewHolder.shortsBlockerEnabled &&
            url.contains("/shorts/")
}

// ============================================================
// HELPER: SPECIAL SCHEMES
// ============================================================

/** Handle tel:, mailto:, intent: schemes. */
private fun handleSpecialSchemes(
    context: Context?,
    url: String
): Boolean {
    return when {
        url.startsWith("tel:") ||
                url.startsWith("mailto:") ||
                url.startsWith("intent:") -> {
            try {
                context?.startActivity(
                    android.content.Intent(
                        android.content
                            .Intent.ACTION_VIEW,
                        android.net.Uri.parse(url)
                    )
                )
            } catch (_: Exception) { }
            true
        }
        else -> false
    }
}

// ============================================================
// HELPER: INJECT VISIBILITY SPOOF
// ============================================================

/** Spoof document visibility for bg audio. */
private fun injectVisibilitySpoof(view: WebView) {
    val js = buildString {
        append("(function(){")
        append("Object.defineProperty(document,")
        append("'hidden',{value:false,")
        append("writable:false,")
        append("configurable:true});")
        append("Object.defineProperty(document,")
        append("'visibilityState',")
        append("{value:'visible',")
        append("writable:false,")
        append("configurable:true});")
        append("document.addEventListener(")
        append("'visibilitychange',function(e)")
        append("{e.stopImmediatePropagation();},")
        append("true);")
        append("})();")
    }
    view.evaluateJavascript(js, null)
}

// ============================================================
// HELPER: INJECT AD BLOCKER
// ============================================================

/** Inject ad blocking CSS and JS into page. */
private fun injectAdBlocker(
    webView: WebView,
    url: String
) {
    val css = AdBlocker.getAdBlockCss()
        .replace("\n", "")
        .replace("'", "\\'")
    val cssJs = buildString {
        append("(function(){")
        append("var s=document.createElement(")
        append("'style');")
        append("s.innerHTML='$css';")
        append("document.head.appendChild(s);")
        append("})();")
    }
    webView.evaluateJavascript(cssJs, null)
    webView.evaluateJavascript(
        AdBlocker.getGeneralAdBlockJs(), null
    )
    if (url.contains("youtube.com") ||
        url.contains("youtu.be")
    ) {
        webView.evaluateJavascript(
            AdBlocker.getYouTubeAdSkipJs(), null
        )
    }
}

// ============================================================
// HELPER: INJECT SHORTS BLOCKER
// ============================================================

/** Inject shorts blocker CSS/JS on YouTube. */
private fun injectShortsBlocker(
    view: WebView,
    url: String?
) {
    if (!WebViewHolder.shortsBlockerEnabled) return
    if (url?.contains("youtube.com") != true &&
        url?.contains("youtu.be") != true
    ) return

    // Inject CSS
    val css = WebViewHolder.getShortsBlockerCss()
        .replace("\n", "")
        .replace("'", "\\'")
    val cssJs = buildString {
        append("(function(){")
        append("var s=document.createElement(")
        append("'style');")
        append("s.type='text/css';")
        append("s.innerHTML='$css';")
        append("document.head.appendChild(s);")
        append("})();")
    }
    view.evaluateJavascript(cssJs, null)

    // Inject JS
    view.evaluateJavascript(
        WebViewHolder.getShortsBlockerJs(), null
    )

    // Inject URL watcher
    view.evaluateJavascript(
        getShortsUrlWatcherJs(), null
    )
}

// ============================================================
// SHORTS URL WATCHER JAVASCRIPT
// ============================================================

/** JS that watches for YouTube SPA navigation
 *  to /shorts/ URLs and redirects to /watch. */
private fun getShortsUrlWatcherJs(): String = """
(function() {
    'use strict';
    if (window.__webwrap_shorts_watcher) return;
    window.__webwrap_shorts_watcher = true;

    function checkAndRedirectShorts() {
        var url = window.location.href;
        var match = url.match(
            /\/shorts\/([a-zA-Z0-9_-]+)/
        );
        if (match) {
            var videoId = match[1];
            var newUrl = url.replace(
                /\/shorts\/[a-zA-Z0-9_-]+/,
                '/watch?v=' + videoId
            );
            window.location.replace(newUrl);
        }
    }

    checkAndRedirectShorts();

    var origPush = history.pushState;
    history.pushState = function() {
        origPush.apply(this, arguments);
        setTimeout(checkAndRedirectShorts, 100);
    };

    var origReplace = history.replaceState;
    history.replaceState = function() {
        origReplace.apply(this, arguments);
        setTimeout(checkAndRedirectShorts, 100);
    };

    window.addEventListener('popstate', function() {
        setTimeout(checkAndRedirectShorts, 100);
    });

    setInterval(checkAndRedirectShorts, 1500);

    document.addEventListener(
        'yt-navigate-start', function() {
            setTimeout(checkAndRedirectShorts, 200);
        }
    );
    document.addEventListener(
        'yt-navigate-finish', function() {
            setTimeout(checkAndRedirectShorts, 200);
        }
    );

    document.addEventListener('click', function(e) {
        var target = e.target;
        while (target && target !== document) {
            if (target.tagName === 'A') {
                var href =
                    target.getAttribute('href') || '';
                var m = href.match(
                    /\/shorts\/([a-zA-Z0-9_-]+)/
                );
                if (m) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.location.href =
                        '/watch?v=' + m[1];
                    return false;
                }
            }
            target = target.parentElement;
        }
    }, true);
})();
""".trimIndent()