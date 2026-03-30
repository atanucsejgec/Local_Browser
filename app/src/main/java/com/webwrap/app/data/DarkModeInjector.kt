package com.webwrap.app.data

import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * DarkModeInjector — Enables dark mode for web content.
 * Uses Android's built-in algorithmic darkening (API 33+),
 * forceDark (API 29-32), and CSS injection as universal fallback.
 */
object DarkModeInjector {

    /** Enable dark mode on WebView using platform API + CSS fallback */
    fun enableDarkMode(webView: WebView?) {
        webView ?: return
        applyPlatformDarkMode(webView, true)
        webView.evaluateJavascript(getDarkModeCss(), null)
    }

    /** Disable dark mode, restore normal rendering */
    fun disableDarkMode(webView: WebView?) {
        webView ?: return
        applyPlatformDarkMode(webView, false)
        webView.evaluateJavascript(getRemoveDarkModeCss(), null)
    }

    /** Apply Android platform-level dark mode setting */
    private fun applyPlatformDarkMode(webView: WebView, enable: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) {
            webView.settings.isAlgorithmicDarkeningAllowed = enable
        } else if (Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION")
            webView.settings.forceDark = if (enable)
                WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
    }

    /** CSS injection for universal dark mode — works on all API levels */
    private fun getDarkModeCss(): String = """
        (function() {
            if (document.getElementById('webwrap-darkmode')) return;
            var s = document.createElement('style');
            s.id = 'webwrap-darkmode';
            s.innerHTML = `
                html, body, div, section, article, main, header, footer, nav,
                p, span, h1, h2, h3, h4, h5, h6, li, td, th, tr, table,
                form, input, textarea, select, button, label {
                    background-color: #1a1a2e !important;
                    color: #e0e0e0 !important;
                    border-color: #333 !important;
                }
                a { color: #6db3f2 !important; }
                img, video, canvas, svg, iframe { filter: brightness(0.9); }
                input, textarea, select {
                    background-color: #252538 !important;
                    color: #fff !important;
                    border: 1px solid #444 !important;
                }
            `;
            document.head.appendChild(s);
        })();
    """.trimIndent()

    /** Remove injected dark mode CSS */
    private fun getRemoveDarkModeCss(): String = """
        (function() {
            var el = document.getElementById('webwrap-darkmode');
            if (el) el.remove();
        })();
    """.trimIndent()
}