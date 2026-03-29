package com.webwrap.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.webwrap.app.data.CacheManager
import com.webwrap.app.data.CookieHelper
import com.webwrap.app.data.SessionManager
import com.webwrap.app.data.SiteBookmark
import com.webwrap.app.data.model.HistoryEntry
import com.webwrap.app.data.repository.BookmarkRepository
import com.webwrap.app.data.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel for HomeScreen.
 * Manages bookmarks, history, search query.
 * Survives configuration changes.
 */
class HomeViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val ctx = application.applicationContext

    val bookmarkRepo = BookmarkRepository(ctx)
    val historyRepo = HistoryRepository(ctx)

    // Search query with SavedStateHandle persistence
    private val _searchQuery = MutableStateFlow(
        savedStateHandle.get<String>("search") ?: ""
    )
    val searchQuery: StateFlow<String> =
        _searchQuery.asStateFlow()

    // Settings dropdown visibility
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> =
        _showSettings.asStateFlow()

    // Add bookmark dialog visibility
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> =
        _showAddDialog.asStateFlow()

    /** Get greeting based on time of day. */
    fun getGreeting(): String {
        val hour = Calendar.getInstance()
            .get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 6 -> "Good Night"
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            hour < 21 -> "Good Evening"
            else -> "Good Night"
        }
    }

    /** Get formatted date string. */
    fun getDateText(): String {
        return SimpleDateFormat(
            "EEEE, MMM d",
            Locale.getDefault()
        ).format(Date())
    }

    /** Update search query. */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        savedStateHandle["search"] = query
    }

    /** Toggle settings dropdown. */
    fun toggleSettings(show: Boolean) {
        _showSettings.value = show
    }

    /** Toggle add bookmark dialog. */
    fun toggleAddDialog(show: Boolean) {
        _showAddDialog.value = show
    }

    /** Add bookmark via repository. */
    fun addBookmark(bookmark: SiteBookmark) {
        bookmarkRepo.addBookmark(bookmark)
    }

    /** Delete bookmark via repository. */
    fun deleteBookmark(bookmark: SiteBookmark) {
        bookmarkRepo.deleteBookmark(bookmark)
    }

    /** Add URL to history. */
    fun addToHistory(url: String) {
        historyRepo.addEntry(url)
    }

    /** Clear all app data. */
    fun clearAllData() {
        CookieHelper.clearAllCookies()
        CacheManager.clearWebViewCache(ctx)
        SessionManager.clearSession(ctx)
        historyRepo.clearHistory()
    }

    /**
     * Check if there's a saved session to restore.
     * Returns the active URL if found.
     */
    fun consumeAutoRestoreUrl(): String? {
        val session = SessionManager.loadSession(ctx)
        return if (session != null && session.tabs.isNotEmpty()) {
            val idx = session.activeTabIndex.coerceIn(0, session.tabs.size - 1)
            session.tabs[idx].url
        } else {
            null
        }
    }

    /**
     * Convert search query to URL.
     * If it looks like a URL, prepend https.
     * Otherwise, Google search.
     */
    fun queryToUrl(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return ""
        return if (q.contains(".") && !q.contains(" ")) {
            if (q.startsWith("http")) q
            else "https://$q"
        } else {
            "https://www.google.com/search?q=$q"
        }
    }
}