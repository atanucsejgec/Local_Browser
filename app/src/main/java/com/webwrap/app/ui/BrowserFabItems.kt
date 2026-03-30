package com.webwrap.app.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import com.webwrap.app.data.*
import com.webwrap.app.navigation.OverlayService
import com.webwrap.app.webview.WebViewHolder
import com.webwrap.app.ui.viewmodel.BrowserViewModel

/**
 * buildFabItems — Creates the complete list of FabItem for the browser FAB menu.
 * Separated from WebViewScreen to keep code manageable.
 * Returns a list of FabItem with all toggles and actions.
 */
fun buildFabItems(
    vm: BrowserViewModel,
    activeTabUrl: String?,
    activeTabTitle: String?,
    activeTabCanGoBack: Boolean,
    activeTabCanGoForward: Boolean,
    activeTabIsLoading: Boolean,
    activeWebView: android.webkit.WebView?,
    tabCount: Int,
    onShowTabSwitcher: () -> Unit,
    onCreateNewTab: () -> Unit,
    onGoHome: () -> Unit,
    onShowUrlBar: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onBookmark: () -> Unit,
    onShare: (String) -> Unit,
    onClearCache: () -> Unit,
    context: android.content.Context,
    activity: android.app.Activity?,
    onOpenIncognito: () -> Unit,
    onOpenOfflinePages: () -> Unit
): List<FabItem> = listOf(
    // ── Tab Management ──
    FabItem(icon = Icons.Default.Tab, label = "Tabs ($tabCount)",
        activeColor = Color(0xFF4FC3F7), inactiveColor = Color(0xFF4FC3F7),
        onClick = onShowTabSwitcher),
    FabItem(icon = Icons.Default.AddBox, label = "New Tab",
        activeColor = Color(0xFF81C784), inactiveColor = Color(0xFF81C784),
        onClick = onCreateNewTab),
    FabItem(icon = Icons.Default.Home, label = "Home",
        activeColor = Color(0xFF4FC3F7), inactiveColor = Color(0xFF4FC3F7),
        onClick = onGoHome),

    // ── Navigation ──
    FabItem(icon = Icons.Default.Search, label = "Search / URL",
        activeColor = Color.White, inactiveColor = Color.White,
        onClick = onShowUrlBar),
    FabItem(icon = Icons.Default.ArrowBack, label = "Back",
        inactiveColor = if (activeTabCanGoBack) Color.White else Color(0xFF444444),
        onClick = onGoBack),
    FabItem(icon = Icons.Default.ArrowForward, label = "Forward",
        inactiveColor = if (activeTabCanGoForward) Color.White else Color(0xFF444444),
        onClick = onGoForward),
    FabItem(
        icon = if (activeTabIsLoading) Icons.Default.Close else Icons.Default.Refresh,
        label = if (activeTabIsLoading) "Stop" else "Refresh",
        activeColor = Color.White, inactiveColor = Color.White,
        onClick = onRefreshOrStop),

    // ── Zoom Toggles ──
    FabItem(icon = Icons.Default.Gesture,
        label = if (vm.pinchZoomEnabled) "Pinch Zoom: ON" else "Pinch Zoom: OFF",
        isToggle = true, isOn = vm.pinchZoomEnabled, activeColor = Color(0xFF9C27B0),
        onClick = {
            vm.pinchZoomEnabled = !vm.pinchZoomEnabled
            Toast.makeText(context, if (vm.pinchZoomEnabled) "🤏 Pinch Zoom ON" else "Pinch Zoom OFF", Toast.LENGTH_SHORT).show()
        }),
    FabItem(icon = Icons.Default.ZoomIn,
        label = if (vm.manualZoomEnabled) "Button Zoom: ON" else "Button Zoom: OFF",
        isToggle = true, isOn = vm.manualZoomEnabled, activeColor = Color(0xFF00BCD4),
        onClick = {
            vm.manualZoomEnabled = !vm.manualZoomEnabled
            Toast.makeText(context, if (vm.manualZoomEnabled) "🔍 +/- Zoom ON" else "+/- Zoom OFF", Toast.LENGTH_SHORT).show()
        }),

    // ── Content Filters ──
    FabItem(icon = Icons.Default.ShortText,
        label = if (vm.shortsBlockerEnabled) "Shorts:Blocked" else "Shorts: Showing",
        isToggle = true, isOn = vm.shortsBlockerEnabled, activeColor = Color(0xFFE91E63),
        onClick = {
            vm.shortsBlockerEnabled = !vm.shortsBlockerEnabled
            activeWebView?.reload()
            Toast.makeText(context, if (vm.shortsBlockerEnabled) "🚫 Shorts Blocked" else "📱 Shorts Enabled", Toast.LENGTH_SHORT).show()
        }),
    FabItem(icon = Icons.Default.Shield,
        label = if (vm.adBlockEnabled) "Ads: Blocked" else "Ads: Showing",
        isToggle = true, isOn = vm.adBlockEnabled, activeColor = Color(0xFF4CAF50),
        onClick = { vm.adBlockEnabled = !vm.adBlockEnabled; activeWebView?.reload() }),

    // ── Media Controls ──
    FabItem(icon = Icons.Default.Repeat,
        label = if (vm.repeatEnabled) "Repeat: ON" else "Repeat: OFF",
        isToggle = true, isOn = vm.repeatEnabled, activeColor = Color(0xFF00BCD4),
        onClick = {
            WebViewHolder.toggleRepeat()
            vm.repeatEnabled = WebViewHolder.isRepeatEnabled
            Toast.makeText(context, if (vm.repeatEnabled) "🔁 Repeat ON" else "Repeat OFF", Toast.LENGTH_SHORT).show()
        }),
    FabItem(icon = Icons.Default.MusicNote,
        label = if (vm.bgAudioEnabled) "BG Audio: ON" else "BG Audio: OFF",
        isToggle = true, isOn = vm.bgAudioEnabled, activeColor = Color(0xFF4FC3F7),
        onClick = { vm.bgAudioEnabled = !vm.bgAudioEnabled }),

    // ── DESKTOP MODE — Proper UA toggle with reset ──
    FabItem(icon = Icons.Default.Computer,
        label = if (vm.desktopMode) "Desktop: ON" else "Desktop: OFF",
        isToggle = true, isOn = vm.desktopMode, activeColor = Color(0xFFFF9800),
        onClick = {
            vm.desktopMode = !vm.desktopMode
            val webView = activeWebView ?: return@FabItem
            if (vm.desktopMode) {
                webView.settings.userAgentString = vm.getDesktopUserAgent()
            } else {
                // Restore to saved default UA (not null — null may not reset on all devices)
                webView.settings.userAgentString = WebViewHolder.defaultUserAgent.ifEmpty { null }
            }
            // Clear cache so server sees new UA, then reload
            webView.clearCache(true)
            webView.loadUrl(webView.url ?: "https://www.google.com")
        }),

    // ── Actions ──
    FabItem(icon = Icons.Default.BookmarkAdd, label = "Bookmark",
        activeColor = Color(0xFFFFB74D), inactiveColor = Color(0xFFFFB74D),
        onClick = onBookmark),
    FabItem(icon = Icons.Default.Share, label = "Share",
        activeColor = Color.White, inactiveColor = Color.White,
        onClick = { onShare(activeTabUrl ?: "") }),
    FabItem(icon = Icons.Default.CleaningServices, label = "Clear Cache",
        activeColor = Color(0xFFE57373), inactiveColor = Color(0xFFE57373),
        onClick = onClearCache),

    // ═══ NEW FEATURES ═════════════════════════════════
    // ── Find in Page ──
    FabItem(icon = Icons.Default.FindInPage, label = "Find in Page",
        activeColor = Color.White, inactiveColor = Color.White,
        onClick = {
            vm.findInPageVisible = !vm.findInPageVisible
            if (!vm.findInPageVisible) {
                vm.findQuery = ""
                FindInPageManager.clearFind(activeWebView)
            }
        }),

    // ── DARK MODE — Filter invert + YouTube dark theme ──
    FabItem(icon = Icons.Default.DarkMode,
        label = if (vm.darkModeEnabled) "Dark: ON" else "Dark: OFF",
        isToggle = true, isOn = vm.darkModeEnabled, activeColor = Color(0xFF7C4DFF),
        onClick = {
            vm.darkModeEnabled = !vm.darkModeEnabled
            WebViewHolder.darkModeEnabled = vm.darkModeEnabled
            if (vm.darkModeEnabled) DarkModeInjector.enableDarkMode(activeWebView)
            else DarkModeInjector.disableDarkMode(activeWebView)
        }),

    // ── Incognito ──
    FabItem(
        icon = Icons.Default.VisibilityOff,
        label = "Incognito",
        activeColor = Color(0xFF555555), inactiveColor = Color(0xFF555555),
        onClick = onOpenIncognito
    ),

    // ── PiP ──
    FabItem(icon = Icons.Default.PictureInPicture,
        label = if (vm.pipEnabled) "PiP: ON" else "PiP: OFF",
        isToggle = true, isOn = vm.pipEnabled, activeColor = Color(0xFFFF6F00),
        onClick = {
            vm.pipEnabled = !vm.pipEnabled
            Toast.makeText(context, if (vm.pipEnabled) "📺 PiP ON — press Home" else "PiP OFF", Toast.LENGTH_SHORT).show()
        }),

    // ── Floating Window ──
    FabItem(icon = Icons.Default.OpenInNew, label = "Float Window",
        activeColor = Color(0xFF26C6DA), inactiveColor = Color(0xFF26C6DA),
        onClick = {
            if (OverlayService.hasOverlayPermission(context)) {
                OverlayService.start(context, activeTabUrl ?: "https://www.google.com")
            } else {
                activity?.let { OverlayService.requestOverlayPermission(it) }
                Toast.makeText(context, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
            }
        }),

    // ── Save Offline ──
    FabItem(icon = Icons.Default.SaveAlt, label = "Save Offline",
        activeColor = Color(0xFF66BB6A), inactiveColor = Color(0xFF66BB6A),
        onClick = { DownloadHelper.saveWebsiteOffline(context, activeWebView) }),

    // ── VIEW SAVED OFFLINE PAGES ──
    FabItem(
        icon = Icons.Default.FolderOpen,
        label = "Saved Pages",
        activeColor = Color(0xFF66BB6A), inactiveColor = Color(0xFF66BB6A),
        onClick = onOpenOfflinePages
    ),
)