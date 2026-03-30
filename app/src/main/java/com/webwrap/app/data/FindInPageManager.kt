package com.webwrap.app.data

import android.webkit.WebView

/**
 * FindInPageManager — Wraps WebView's find-in-page functionality.
 * Uses findAllAsync for search and findNext for navigation.
 */
object FindInPageManager {

    /** Start searching for query text inside WebView */
    fun find(webView: WebView?, query: String) {
        if (query.isEmpty()) {
            clearFind(webView)
            return
        }
        webView?.findAllAsync(query)
    }

    /** Navigate to next match */
    fun findNext(webView: WebView?) {
        webView?.findNext(true)
    }

    /** Navigate to previous match */
    fun findPrevious(webView: WebView?) {
        webView?.findNext(false)
    }

    /** Clear all find highlights */
    fun clearFind(webView: WebView?) {
        webView?.clearMatches()
    }
}