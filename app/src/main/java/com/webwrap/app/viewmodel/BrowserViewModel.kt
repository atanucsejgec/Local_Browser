package com.webwrap.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.webwrap.app.data.CookieHelper
import com.webwrap.app.data.SavedSession
import com.webwrap.app.data.SavedTab
import com.webwrap.app.data.SessionManager
import com.webwrap.app.data.SiteBookmark
import com.webwrap.app.data.repository.BookmarkRepository
import com.webwrap.app.webview.WebViewHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for WebViewScreen.
 * Manages browser state: tabs, toggles, session.
 * All state survives configuration changes.
 */
class BrowserViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    val bookmarkRepo = BookmarkRepository(ctx)

    // ===== TAB STATE =====

    private val _activeTabId = MutableStateFlow("")
    val activeTabId: StateFlow<String> =
        _activeTabId.asStateFlow()

    // ===== UI STATE =====

    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> =
        _isFullScreen.asStateFlow()

    private val _showUrlBar = MutableStateFlow(false)
    val showUrlBar: StateFlow<Boolean> =
        _showUrlBar.asStateFlow()

    private val _showTabSwitcher = MutableStateFlow(false)
    val showTabSwitcher: StateFlow<Boolean> =
        _showTabSwitcher.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> =
        _urlInput.asStateFlow()

    // ===== TOGGLE STATE =====

    private val _desktopMode = MutableStateFlow(false)
    val desktopMode: StateFlow<Boolean> =
        _desktopMode.asStateFlow()

    private val _adBlockEnabled = MutableStateFlow(
        WebViewHolder.adBlockEnabled
    )
    val adBlockEnabled: StateFlow<Boolean> =
        _adBlockEnabled.asStateFlow()

    private val _bgAudioEnabled = MutableStateFlow(
        WebViewHolder.backgroundAudioEnabled
    )
    val bgAudioEnabled: StateFlow<Boolean> =
        _bgAudioEnabled.asStateFlow()

    private val _shortsBlockerEnabled = MutableStateFlow(
        WebViewHolder.shortsBlockerEnabled
    )
    val shortsBlockerEnabled: StateFlow<Boolean> =
        _shortsBlockerEnabled.asStateFlow()

    private val _pinchZoomEnabled = MutableStateFlow(
        WebViewHolder.pinchZoomEnabled
    )
    val pinchZoomEnabled: StateFlow<Boolean> =
        _pinchZoomEnabled.asStateFlow()

    private val _manualZoomEnabled = MutableStateFlow(
        WebViewHolder.manualZoomEnabled
    )
    val manualZoomEnabled: StateFlow<Boolean> =
        _manualZoomEnabled.asStateFlow()

    private val _repeatEnabled = MutableStateFlow(
        WebViewHolder.isRepeatEnabled
    )
    val repeatEnabled: StateFlow<Boolean> =
        _repeatEnabled.asStateFlow()

    private val _darkModeEnabled = MutableStateFlow(false)
    val darkModeEnabled: StateFlow<Boolean> =
        _darkModeEnabled.asStateFlow()

    // ===== INCOGNITO STATE =====

    private val _isIncognito = MutableStateFlow(false)
    val isIncognito: StateFlow<Boolean> =
        _isIncognito.asStateFlow()

    // ===== FIND IN PAGE STATE =====

    private val _findInPageVisible = MutableStateFlow(false)
    val findInPageVisible: StateFlow<Boolean> =
        _findInPageVisible.asStateFlow()

    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> =
        _findQuery.asStateFlow()

    // ===== SETTERS =====

    /** Update active tab ID. */
    fun setActiveTabId(id: String) {
        _activeTabId.value = id
    }

    /** Toggle fullscreen state. */
    fun setFullScreen(fullScreen: Boolean) {
        _isFullScreen.value = fullScreen
        WebViewHolder.isFullScreen = fullScreen
    }

    /** Toggle URL bar visibility. */
    fun setShowUrlBar(show: Boolean) {
        _showUrlBar.value = show
    }

    /** Toggle tab switcher visibility. */
    fun setShowTabSwitcher(show: Boolean) {
        _showTabSwitcher.value = show
    }

    /** Update URL input text. */
    fun setUrlInput(input: String) {
        _urlInput.value = input
    }

    /** Toggle desktop mode. */
    fun toggleDesktopMode() {
        _desktopMode.value = !_desktopMode.value
    }

    /** Toggle ad blocker. */
    fun toggleAdBlock() {
        _adBlockEnabled.value = !_adBlockEnabled.value
        WebViewHolder.adBlockEnabled = _adBlockEnabled.value
    }

    /** Toggle background audio. */
    fun toggleBgAudio() {
        _bgAudioEnabled.value = !_bgAudioEnabled.value
        WebViewHolder.backgroundAudioEnabled =
            _bgAudioEnabled.value
    }

    /** Toggle shorts blocker. */
    fun toggleShortsBlocker() {
        _shortsBlockerEnabled.value =
            !_shortsBlockerEnabled.value
        WebViewHolder.shortsBlockerEnabled =
            _shortsBlockerEnabled.value
    }

    /** Toggle pinch zoom. */
    fun togglePinchZoom() {
        _pinchZoomEnabled.value = !_pinchZoomEnabled.value
        WebViewHolder.pinchZoomEnabled =
            _pinchZoomEnabled.value
    }

    /** Toggle manual zoom buttons. */
    fun toggleManualZoom() {
        _manualZoomEnabled.value =
            !_manualZoomEnabled.value
        WebViewHolder.manualZoomEnabled =
            _manualZoomEnabled.value
    }

    /** Toggle repeat mode. */
    fun toggleRepeat() {
        WebViewHolder.toggleRepeat()
        _repeatEnabled.value = WebViewHolder.isRepeatEnabled
    }

    /** Toggle dark mode for web content. */
    fun toggleDarkMode() {
        _darkModeEnabled.value = !_darkModeEnabled.value
    }

    /** Set incognito mode. */
    fun setIncognito(enabled: Boolean) {
        _isIncognito.value = enabled
    }

    /** Toggle find in page bar. */
    fun setFindInPageVisible(visible: Boolean) {
        _findInPageVisible.value = visible
        if (!visible) _findQuery.value = ""
    }

    /** Update find in page query. */
    fun setFindQuery(query: String) {
        _findQuery.value = query
    }

    /**
     * Convert search query to URL.
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

    /**
     * Consume saved session for restoration.
     */
    fun consumePendingSession(): SavedSession? {
        val session = SessionManager.loadSession(ctx)
        return session
    }

    /** Save current session to disk. */
    fun saveSession(
        tabs: List<SavedTab>,
        activeIndex: Int
    ) {
        if (tabs.isNotEmpty()) {
            SessionManager.saveSession(
                ctx, tabs, activeIndex
            )
        }
    }

    /** Save cookies to disk. */
    fun saveCookies() {
        CookieHelper.saveCookies()
    }

    /** Add current page to bookmarks. */
    fun addBookmark(title: String, url: String) {
        val name = title.take(20).ifEmpty {
            url.take(20)
        }
        val bookmark = SiteBookmark(
            name = name,
            url = url,
            icon = "📌",
            color = 0xFFFFB74D
        )
        bookmarkRepo.addBookmark(bookmark)
    }
}