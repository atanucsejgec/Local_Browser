package com.webwrap.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.webwrap.app.data.*

/**
 * HomeViewModel — Manages bookmarks, history, and clear-data for HomeScreen.
 * Survives config changes. Replaces state previously in MainActivity.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    /** Current user bookmarks loaded from storage */
    var bookmarks by mutableStateOf(BookmarkStorage.loadBookmarks(app))
        private set

    /** Browsing history loaded from storage */
    var history by mutableStateOf(BookmarkStorage.loadHistory(app))
        private set

    /** Add a new bookmark and persist */
    fun addBookmark(bookmark: SiteBookmark) {
        bookmarks = bookmarks + bookmark
        BookmarkStorage.saveBookmarks(getApplication(), bookmarks)
    }

    /** Delete a bookmark by URL and persist */
    fun deleteBookmark(bookmark: SiteBookmark) {
        bookmarks = bookmarks.filter { it.url != bookmark.url }
        BookmarkStorage.saveBookmarks(getApplication(), bookmarks)
    }

    /** Record a URL visit to history */
    fun addToHistory(url: String) {
        history = history + url
        BookmarkStorage.saveHistory(getApplication(), history)
    }

    /** Clear all browsing data: cookies, cache, session, history */
    fun clearAllData() {
        CookieHelper.clearAllCookies()
        CacheManager.clearWebViewCache(getApplication())
        SessionManager.clearSession(getApplication())
        history = emptyList()
        BookmarkStorage.saveHistory(getApplication(), history)
    }
}