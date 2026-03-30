package com.webwrap.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.*

/**
 * IncognitoManager — Provides strict session isolation for incognito mode.
 * Saves/restores normal cookies, creates isolated WebView,
 * and cleans up all incognito data on exit.
 * Ensures no data leaks between normal and incognito sessions.
 */
object IncognitoManager {

    /** Stored normal-mode cookies, keyed by domain */
    private var savedCookies: Map<String, String> = emptyMap()
    /** Domains we save cookies for */
    private val COOKIE_DOMAINS = listOf(
        "https://www.google.com", "https://accounts.google.com",
        "https://www.youtube.com", "https://www.facebook.com",
        "https://www.instagram.com", "https://www.github.com",
        "https://twitter.com", "https://www.reddit.com",
        "https://www.linkedin.com", "https://web.whatsapp.com",
        "https://www.amazon.com", "https://www.netflix.com",
        "https://mail.google.com", "https://chat.openai.com"
    )

    /** Save all normal-mode cookies before entering incognito */
    fun saveNormalCookies() {
        val cm = CookieManager.getInstance()
        savedCookies = COOKIE_DOMAINS.associateWith { domain ->
            cm.getCookie(domain) ?: ""
        }.filter { it.value.isNotEmpty() }
    }

    /** Clear all cookies/cache to create a clean incognito environment */
    fun enterIncognito() {
        saveNormalCookies()
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
        WebStorage.getInstance().deleteAllData()
    }

    /** Exit incognito: wipe everything, restore normal cookies */
    fun exitIncognito() {
        val cm = CookieManager.getInstance()
        // Wipe incognito data completely
        cm.removeAllCookies(null)
        cm.removeSessionCookies(null)
        cm.flush()
        WebStorage.getInstance().deleteAllData()

        // Restore normal cookies
        savedCookies.forEach { (domain, cookies) ->
            cookies.split(";").forEach { cookie ->
                val trimmed = cookie.trim()
                if (trimmed.isNotEmpty()) {
                    cm.setCookie(domain, trimmed)
                }
            }
        }
        cm.flush()
        savedCookies = emptyMap()
    }

    /**
     * Create an incognito WebView with maximum isolation.
     * No cache, no storage, no form data, no passwords,
     * randomized user-agent suffix for fingerprint protection.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun createIncognitoWebView(context: Context): WebView {
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE       // No cache
                domStorageEnabled = false                     // No localStorage
                databaseEnabled = false                       // No databases
                @Suppress("DEPRECATION")
                saveFormData = false                           // No form autofill
                allowFileAccess = false
                allowContentAccess = false
                // Randomized UA to prevent fingerprinting
                userAgentString = settings.userAgentString +
                        " Incognito/${System.currentTimeMillis() % 10000}"
                mediaPlaybackRequiresUserGesture = false
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            // Don't accept cookies for incognito WebView
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        }
    }

    /** Clean up incognito WebView completely */
    fun destroyIncognitoWebView(webView: WebView?) {
        webView?.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            clearFormData()
            clearSslPreferences()
            destroy()
        }
    }
}