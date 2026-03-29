package com.webwrap.app.feature.darkmode

import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Manages dark mode for web content.
 * Uses AndroidX WebKit's force dark feature.
 */
object WebDarkMode {

    /**
     * Apply dark mode to WebView content.
     * Uses algorithmic darkening for sites
     * that don't support dark mode natively.
     *
     * @param webView Target WebView
     * @param enabled Whether dark mode is on
     */
    fun apply(webView: WebView, enabled: Boolean) {
        try {
            applyForceDark(webView, enabled)
            applyDarkStrategy(webView)
        } catch (_: Exception) {
            // Fallback: inject CSS dark mode
            if (enabled) {
                injectDarkCss(webView)
            }
        }
    }

    /**
     * Apply force dark setting via WebSettingsCompat.
     */
    @Suppress("DEPRECATION")
    private fun applyForceDark(
        webView: WebView,
        enabled: Boolean
    ) {
        if (WebViewFeature.isFeatureSupported(
                WebViewFeature.FORCE_DARK
            )
        ) {
            val mode = if (enabled) {
                WebSettingsCompat.FORCE_DARK_ON
            } else {
                WebSettingsCompat.FORCE_DARK_OFF
            }
            WebSettingsCompat.setForceDark(
                webView.settings, mode
            )
        }
    }

    /**
     * Set dark mode strategy to prefer web theme
     * but use algorithmic darkening as fallback.
     */
    @Suppress("DEPRECATION")
    private fun applyDarkStrategy(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(
                WebViewFeature.FORCE_DARK_STRATEGY
            )
        ) {
            WebSettingsCompat.setForceDarkStrategy(
                webView.settings,
                WebSettingsCompat
                    .DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
            )
        }
    }

    /**
     * Fallback: inject CSS to force dark mode.
     * Used when WebSettingsCompat is not available.
     */
    private fun injectDarkCss(webView: WebView) {
        val css = """
            html {
                filter: invert(1) hue-rotate(180deg);
            }
            img, video, canvas, svg {
                filter: invert(1) hue-rotate(180deg);
            }
        """.trimIndent()
            .replace("\n", "")
            .replace("'", "\\'")

        val js = buildString {
            append("(function(){")
            append("var s=document.createElement('style');")
            append("s.innerHTML='$css';")
            append("document.head.appendChild(s);")
            append("})();")
        }
        webView.evaluateJavascript(js, null)
    }
}