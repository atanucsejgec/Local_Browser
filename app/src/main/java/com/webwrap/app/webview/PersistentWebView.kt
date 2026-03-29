package com.webwrap.app.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import com.webwrap.app.data.AdBlocker
import com.webwrap.app.data.CookieHelper

class BackgroundAudioWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        if (WebViewHolder.backgroundAudioEnabled && visibility != View.VISIBLE) return
        super.onWindowVisibilityChanged(visibility)
    }

    override fun onPause() {
        if (WebViewHolder.backgroundAudioEnabled) return
        super.onPause()
    }
}

@SuppressLint("SetJavaScriptEnabled")
fun createPersistentWebView(
    context: Context,
    fullScreenContainer: FrameLayout,
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String) -> Unit = {},
    onProgressChanged: (Int) -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onUrlChanged: (String) -> Unit = {},     // ✅ NEW: Track every URL change
    onFullScreenEnter: () -> Unit = {},
    onFullScreenExit: () -> Unit = {},
    desktopMode: Boolean = false
): BackgroundAudioWebView {

    return BackgroundAudioWebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

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
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }
            setGeolocationEnabled(true)
        }

        keepScreenOn = true
        CookieHelper.enableThirdPartyCookies(this)
        isScrollbarFadingEnabled = true
        scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY

        var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
        var zoomableContainer: ZoomableLayout? = null

        webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { currentUrl ->
                    // ✅ SHORTS REDIRECT — catches direct URL loads
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
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieHelper.saveCookies()
                url?.let {
                    onPageFinished(it)
                    onUrlChanged(it)
                }

                // Ad blocker
                if (WebViewHolder.adBlockEnabled && view != null) {
                    injectAdBlocker(view, url ?: "")
                }

                // Visibility spoof
                view?.evaluateJavascript(
                    """
        (function() {
            Object.defineProperty(document, 'hidden', {value:false,writable:false,configurable:true});
            Object.defineProperty(document, 'visibilityState', {value:'visible',writable:false,configurable:true});
            document.addEventListener('visibilitychange', function(e){e.stopImmediatePropagation();}, true);
        })();
        """.trimIndent(), null
                )

                WebViewHolder.injectPauseDetector()

                // ═══════════════════════════════════════════════
                // ✅ ADD THIS BLOCK — Shorts blocker CSS + JS
                // ═══════════════════════════════════════════════
                if (WebViewHolder.shortsBlockerEnabled &&
                    (url?.contains("youtube.com") == true || url?.contains("youtu.be") == true)
                ) {
                    // Inject CSS
                    val shortsCss = WebViewHolder.getShortsBlockerCss()
                        .replace("\n", "")
                        .replace("'", "\\'")
                    view?.evaluateJavascript(
                        "(function(){var s=document.createElement('style');s.type='text/css';s.innerHTML='$shortsCss';document.head.appendChild(s);})();",
                        null
                    )

                    // Inject JS to remove shorts elements
                    view?.evaluateJavascript(WebViewHolder.getShortsBlockerJs(), null)

                    // Inject JS URL watcher for SPA navigation
                    view?.evaluateJavascript(getShortsUrlWatcherJs(), null)
                }
            }
            // ═══════════════════════════════════════════════
            // ✅ SHORTS REDIRECT + URL TRACKING
            // ═══════════════════════════════════════════════
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false

                // ✅ Block YouTube Shorts — redirect to regular video
                if (WebViewHolder.shortsBlockerEnabled && url.contains("/shorts/")) {
                    val convertedUrl = WebViewHolder.convertShortsUrl(url)
                    if (convertedUrl != url) {
                        view?.loadUrl(convertedUrl)
                        return true
                    }
                }

                // Track URL change for session save
                onUrlChanged(url)

                return when {
                    url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:") -> {
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url)
                                )
                            )
                        } catch (_: Exception) { }
                        true
                    }
                    else -> false
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?
            ): WebResourceResponse? {
                if (WebViewHolder.adBlockEnabled) {
                    val requestUrl = request?.url?.toString() ?: ""
                    if (AdBlocker.shouldBlock(requestUrl)) return AdBlocker.createEmptyResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            // ✅ Track URL changes from redirects
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                url?.let { currentUrl ->
                    // ✅ SHORTS REDIRECT — catches SPA navigation
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
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { onTitleChanged(it) }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                fullScreenCallback = callback
                this@apply.visibility = View.GONE

                val zoomable = ZoomableLayout(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.BLACK)
                }
                zoomable.addView(
                    view, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                fullScreenContainer.visibility = View.VISIBLE
                fullScreenContainer.addView(
                    zoomable, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                zoomableContainer = zoomable
                WebViewHolder.fullscreenZoomable = zoomable
                WebViewHolder.isFullScreen = true
                onFullScreenEnter()
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                zoomableContainer?.resetZoom()
                zoomableContainer?.cleanup()
                WebViewHolder.fullscreenZoomable = null
                zoomableContainer = null
                fullScreenContainer.removeAllViews()
                fullScreenContainer.visibility = View.GONE
                this@apply.visibility = View.VISIBLE
                fullScreenCallback?.onCustomViewHidden()
                fullScreenCallback = null
                WebViewHolder.isFullScreen = false
                onFullScreenExit()
            }

            override fun onShowFileChooser(
                webView: WebView?, filePathCallback: ValueCallback<Array<android.net.Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean = false

            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean {
                val newWebView = WebView(context)
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean {
                        request?.url?.toString()?.let { this@apply.loadUrl(it) }
                        return true
                    }
                }
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// ✅ FIXED: Block gesture 2x speed BUT allow manual speed from settings
// ═══════════════════════════════════════════════════

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

        // Check immediately
        checkAndRedirectShorts();

        // Watch for URL changes (YouTube SPA uses History API)
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

        window.addEventListener('popstate', function() {
            setTimeout(checkAndRedirectShorts, 100);
        });

        // Also check periodically (catches edge cases)
        setInterval(checkAndRedirectShorts, 1500);

        // Watch for YouTube's navigation events
        document.addEventListener('yt-navigate-start', function() {
            setTimeout(checkAndRedirectShorts, 200);
        });
        document.addEventListener('yt-navigate-finish', function() {
            setTimeout(checkAndRedirectShorts, 200);
        });

        // Click interceptor — catch shorts link clicks BEFORE navigation
        document.addEventListener('click', function(e) {
            var target = e.target;
            while (target && target !== document) {
                if (target.tagName === 'A') {
                    var href = target.getAttribute('href') || '';
                    var shortsMatch = href.match(/\/shorts\/([a-zA-Z0-9_-]+)/);
                    if (shortsMatch) {
                        e.preventDefault();
                        e.stopPropagation();
                        var videoId = shortsMatch[1];
                        window.location.href = '/watch?v=' + videoId;
                        return false;
                    }
                }
                target = target.parentElement;
            }
        }, true);
    })();
""".trimIndent()