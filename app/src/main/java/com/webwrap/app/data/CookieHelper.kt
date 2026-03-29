package com.webwrap.app.data

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import android.webkit.WebView

object CookieHelper {

    fun setupPersistentCookies(context: Context) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            CookieSyncManager.createInstance(context)
        }

        // ═══════════════════════════════════════════════════
        // ❌ REMOVED: cookieManager.setAcceptThirdPartyCookies(null, true)
        //    Passing null WebView causes NullPointerException CRASH
        //
        // ✅ Third-party cookies are now set per-WebView in
        //    enableThirdPartyCookies() below
        // ═══════════════════════════════════════════════════
    }

    fun enableThirdPartyCookies(webView: WebView) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveCookies() {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAllCookies() {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasCookiesFor(url: String): Boolean {
        return try {
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie(url)
            !cookies.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
}