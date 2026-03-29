package com.webwrap.app.navigation

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Centralized route definitions for the app.
 * All navigation routes are defined here.
 */
object NavRoutes {

    const val HOME = "home"
    const val BROWSER = "browser"

    // Full route pattern with arguments
    const val BROWSER_ROUTE =
        "browser?url={url}&incognito={incognito}"

    /**
     * Build browser route with encoded URL
     * and incognito flag.
     */
    fun browserRoute(
        url: String,
        incognito: Boolean = false
    ): String {
        val encoded = URLEncoder.encode(url, "UTF-8")
        return "browser?url=$encoded&incognito=$incognito"
    }

    /**
     * Decode URL from navigation argument.
     */
    fun decodeUrl(encoded: String): String {
        return try {
            URLDecoder.decode(encoded, "UTF-8")
        } catch (_: Exception) {
            encoded
        }
    }
}