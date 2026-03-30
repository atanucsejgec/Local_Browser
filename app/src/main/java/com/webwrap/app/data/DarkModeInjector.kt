package com.webwrap.app.data

import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * DarkModeInjector — Forces dark mode on web content.
 * Uses filter:invert for general sites (most reliable).
 * Uses YouTube's built-in dark theme for YouTube.
 * Double-inverts images/videos so they look normal.
 */
object DarkModeInjector {

    /** Enable dark mode — chooses best method per site */
    fun enableDarkMode(webView: WebView?) {
        webView ?: return
        applyPlatformDarkMode(webView, true)
        val url = webView.url ?: ""
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            webView.evaluateJavascript(getYouTubeDarkModeJs(), null)
        } else {
            webView.evaluateJavascript(getFilterInvertJs(), null)
        }
    }

    /** Disable dark mode — restore normal rendering */
    fun disableDarkMode(webView: WebView?) {
        webView ?: return
        applyPlatformDarkMode(webView, false)
        webView.evaluateJavascript(getRemoveDarkModeJs(), null)
    }

    /** Apply platform-level dark mode (API 29+) */
    private fun applyPlatformDarkMode(webView: WebView, enable: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) {
            webView.settings.isAlgorithmicDarkeningAllowed = enable
        } else if (Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION")
            webView.settings.forceDark = if (enable)
                WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
    }

    /**
     * Filter:invert approach — works on ALL websites.
     * Inverts entire page colors, then double-inverts images/videos
     * so they appear normal. Most reliable universal dark mode.
     */
    private fun getFilterInvertJs(): String = """
    (function() {
        if (document.getElementById('webwrap-darkmode')) return;
        var s = document.createElement('style');
        s.id = 'webwrap-darkmode';
        s.innerHTML = `
            html {
                filter: invert(1) hue-rotate(180deg) !important;
                -webkit-filter: invert(1) hue-rotate(180deg) !important;
                background: white !important;
            }
            img, video, canvas, svg, picture, iframe,
            [style*="background-image"],
            .ytp-videowall-still-image,
            ytd-thumbnail, ytd-thumbnail img,
            yt-image, yt-img-shadow img,
            .emoji, [role="img"] {
                filter: invert(1) hue-rotate(180deg) !important;
                -webkit-filter: invert(1) hue-rotate(180deg) !important;
            }
        `;
        document.head.appendChild(s);
    })();
    """.trimIndent()

    /**
     * YouTube-specific dark mode — uses YouTube's built-in dark theme.
     * Sets the 'dark' attribute on html element + dark preference cookie.
     * Much better than filter:invert for YouTube specifically.
     */
    private fun getYouTubeDarkModeJs(): String = """
    (function() {
        if (document.getElementById('webwrap-darkmode')) return;
        
        // Method 1: Set dark attribute on html element
        document.documentElement.setAttribute('dark', 'true');
        document.documentElement.setAttribute('data-dark-theme', 'true');
        
        // Method 2: Set YouTube dark theme cookie
        document.cookie = 'PREF=f6=400;domain=.youtube.com;path=/;max-age=31536000';
        
        // Method 3: Force dark background via CSS for elements that resist
        var s = document.createElement('style');
        s.id = 'webwrap-darkmode';
        s.innerHTML = `
            html[dark] body, body {
                background-color: #0f0f0f !important;
            }
            /* Mobile YouTube overrides */
            ytm-app, .mobile-topbar-header, 
            .page-container, .scbrr-tabs,
            .tab-content, .single-column-browse-results-renderer,
            ytm-section-list-renderer, ytm-item-section-renderer,
            ytm-rich-grid-renderer, c3-material-button,
            .compact-media-item, .media-item-info,
            ytm-compact-video-renderer, ytm-video-with-context-renderer {
                background-color: #0f0f0f !important;
                color: #e0e0e0 !important;
            }
            /* Desktop YouTube elements that might not pick up dark theme */
            ytd-app, #content, #page-manager,
            ytd-browse, ytd-two-column-browse-results-renderer,
            ytd-rich-grid-renderer, ytd-section-list-renderer,
            ytd-watch-flexy, #below, #secondary,
            ytd-comments, ytd-comment-thread-renderer {
                background-color: #0f0f0f !important;
            }
            /* Text colors */
            yt-formatted-string, .yt-core-attributed-string,
            #video-title, .title, span, a, p, h1, h2, h3 {
                color: #f1f1f1 !important;
            }
            /* Search bar */
            #search-input, input#search, .ytSearchboxComponentInput {
                background-color: #121212 !important;
                color: white !important;
            }
        `;
        document.head.appendChild(s);
    })();
    """.trimIndent()

    /** Remove all dark mode injections */
    private fun getRemoveDarkModeJs(): String = """
    (function() {
        // Remove injected style
        var el = document.getElementById('webwrap-darkmode');
        if (el) el.remove();
        // Remove YouTube dark attributes
        document.documentElement.removeAttribute('dark');
        document.documentElement.removeAttribute('data-dark-theme');
        // Clear YouTube dark cookie
        if (window.location.hostname.includes('youtube')) {
            document.cookie = 'PREF=;domain=.youtube.com;path=/;max-age=0';
        }
    })();
    """.trimIndent()
}