package com.webwrap.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.webwrap.app.data.*
import com.webwrap.app.webview.WebViewHolder

/**
 * BrowserViewModel — Holds all browser feature toggles, session state,
 * and new feature flags (PiP, incognito, dark mode, find-in-page).
 * Replaces all `var ... by remember` state from WebViewScreen.
 */
class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    // ── Navigation / Session ────────────────────────────
    /** URL to open when browser screen first loads */
    var initialUrl by mutableStateOf("https://www.google.com")
    /** Saved session restored on cold start */
    var savedSession by mutableStateOf<SavedSession?>(null)
    /** Show offline page viewer dialog */
    var showOfflineViewer by mutableStateOf(false)
    // ── UI Visibility ───────────────────────────────────
    var showUrlBar by mutableStateOf(false)
    var showTabSwitcher by mutableStateOf(false)
    var urlInput by mutableStateOf("")
    var isFullScreen by mutableStateOf(false)

    // ── Feature Toggles ─────────────────────────────────
    var desktopMode by mutableStateOf(false)
    var adBlockEnabled by mutableStateOf(WebViewHolder.adBlockEnabled)
    var bgAudioEnabled by mutableStateOf(WebViewHolder.backgroundAudioEnabled)
    var shortsBlockerEnabled by mutableStateOf(WebViewHolder.shortsBlockerEnabled)
    var pinchZoomEnabled by mutableStateOf(WebViewHolder.pinchZoomEnabled)
    var manualZoomEnabled by mutableStateOf(WebViewHolder.manualZoomEnabled)
    var repeatEnabled by mutableStateOf(WebViewHolder.isRepeatEnabled)

    // ── Zoom UI ─────────────────────────────────────────
    var showZoomButtons by mutableStateOf(false)
    var currentZoomLevel by mutableFloatStateOf(1f)

    // ── NEW Feature Flags ───────────────────────────────
    /** Picture-in-Picture toggle — enters PiP on home press */
    var pipEnabled by mutableStateOf(false)
    /** Incognito mode — full session isolation */
    var incognitoMode by mutableStateOf(false)
    /** Dark mode CSS injection for web content */
    var darkModeEnabled by mutableStateOf(false)
    /** Find-in-page search bar visible */
    var findInPageVisible by mutableStateOf(false)
    /** Current find-in-page query */
    var findQuery by mutableStateOf("")
    /** Whether activity is currently in PiP mode */
    var isInPipMode by mutableStateOf(false)

    // ── Desktop User Agent (Mac Chrome style) ───────────
    /** Returns Mac Chrome UA string for desktop mode */
    fun getDesktopUserAgent(): String =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // ── Session Management ──────────────────────────────
    /** Save current session to persistent storage */
    fun saveSession(tabs: List<SavedTab>, activeIndex: Int) {
        if (tabs.isNotEmpty()) {
            SessionManager.saveSession(getApplication(), tabs, activeIndex)
        }
    }

    /** Load saved session from storage */
    fun loadSavedSession(): SavedSession? =
        SessionManager.loadSession(getApplication())

    /** Check if a saved session exists */
    fun hasSavedSession(): Boolean =
        SessionManager.hasSession(getApplication())

    /** Sync all toggles to WebViewHolder singleton */
    fun syncToWebViewHolder() {
        WebViewHolder.adBlockEnabled = adBlockEnabled
        WebViewHolder.backgroundAudioEnabled = bgAudioEnabled
        WebViewHolder.shortsBlockerEnabled = shortsBlockerEnabled
        WebViewHolder.pinchZoomEnabled = pinchZoomEnabled
        WebViewHolder.manualZoomEnabled = manualZoomEnabled
        WebViewHolder.isRepeatEnabled = repeatEnabled
    }
}