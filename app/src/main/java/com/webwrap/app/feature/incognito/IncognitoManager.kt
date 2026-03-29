package com.webwrap.app.feature.incognito

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.webwrap.app.data.CookieHelper

/**
 * Manages strict incognito mode isolation.
 *
 * Ensures no data leaks between normal and incognito:
 * - Separate cookie session
 * - No cache / local storage
 * - No form data / passwords
 * - No history saved
 * - No autofill
 *
 * When entering incognito:
 *   1. Backup normal cookies
 *   2. Clear all cookies
 *   3. Create isolated WebView
 *
 * When exiting incognito:
 *   1. Destroy incognito WebView
 *   2. Clear all cookies
 *   3. Restore normal cookies
 */
object IncognitoManager {

    // Stores backed-up cookies: domain -> cookieString
    private val cookieBackup =
        mutableMapOf<String, String>()

    var isActive: Boolean = false
        private set

    // Domains to backup/restore cookies for
    private val importantDomains = listOf(
        "https://www.google.com",
        "https://accounts.google.com",
        "https://mail.google.com",
        "https://www.youtube.com",
        "https://www.facebook.com",
        "https://www.instagram.com",
        "https://twitter.com",
        "https://www.x.com",
        "https://github.com",
        "https://www.reddit.com",
        "https://web.whatsapp.com",
        "https://www.linkedin.com",
        "https://www.netflix.com",
        "https://chat.openai.com",
        "https://open.spotify.com",
        "https://www.amazon.com",
        "https://www.pinterest.com",
    )

    /**
     * Enter incognito mode.
     * Backs up normal cookies, clears everything.
     */
    fun enterIncognito() {
        backupNormalCookies()
        clearAllCookies()
        isActive = true
    }

    /**
     * Exit incognito mode.
     * Clears incognito data, restores normal cookies.
     */
    fun exitIncognito() {
        clearAllCookies()
        restoreNormalCookies()
        isActive = false
    }

    /**
     * Configure WebSettings for incognito mode.
     * Disables all persistence mechanisms.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun configureWebSettings(settings: WebSettings) {
        settings.apply {
            // Core functionality
            javaScriptEnabled = true
            domStorageEnabled = false
            databaseEnabled = false
            cacheMode = WebSettings.LOAD_NO_CACHE

            // Disable data persistence
            @Suppress("DEPRECATION")
            saveFormData = false
            @Suppress("DEPRECATION")
            savePassword = false

            // Disable file access
            allowFileAccess = false
            allowContentAccess = false

            // Disable geolocation
            setGeolocationEnabled(false)

            // Other settings
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }
    }

    /**
     * Configure CookieManager for incognito WebView.
     * Accepts cookies but doesn't persist them.
     */
    fun configureCookies(webView: WebView) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, false)
    }

    /**
     * Clean up incognito WebView completely.
     * Call when closing an incognito tab.
     */
    fun cleanupWebView(webView: WebView?) {
        webView?.apply {
            clearCache(true)
            clearFormData()
            clearHistory()
            clearSslPreferences()
        }
    }

    /**
     * Backup cookies for important domains.
     */
    private fun backupNormalCookies() {
        cookieBackup.clear()
        val cm = CookieManager.getInstance()
        for (domain in importantDomains) {
            val cookie = cm.getCookie(domain)
            if (!cookie.isNullOrEmpty()) {
                cookieBackup[domain] = cookie
            }
        }
    }

    /**
     * Restore previously backed-up cookies.
     */
    private fun restoreNormalCookies() {
        val cm = CookieManager.getInstance()
        for ((domain, cookieStr) in cookieBackup) {
            // Split multiple cookies and set each
            cookieStr.split(";").forEach { singleCookie ->
                val trimmed = singleCookie.trim()
                if (trimmed.isNotEmpty()) {
                    cm.setCookie(domain, trimmed)
                }
            }
        }
        cm.flush()
        cookieBackup.clear()
    }

    /**
     * Clear all cookies from CookieManager.
     */
    private fun clearAllCookies() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
    }
}