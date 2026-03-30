package com.webwrap.app

import android.annotation.SuppressLint
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.webwrap.app.data.*
import com.webwrap.app.service.BackgroundAudioService
import com.webwrap.app.ui.*
import com.webwrap.app.ui.viewmodel.BrowserViewModel
import com.webwrap.app.webview.*
import kotlinx.coroutines.delay
import com.webwrap.app.ui.OfflinePageViewer


/**
 * WebViewScreen — Main browser screen with tabs, URL bar, FAB menu,
 * fullscreen video, zoom controls, find-in-page, incognito indicator.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    initialUrl: String,
    savedSession: SavedSession? = null,
    browserViewModel: BrowserViewModel,
    onGoHome: () -> Unit,
    onAddToBookmark: (String, String) -> Unit,
    onSessionUpdate: (List<SavedTab>, Int) -> Unit = { _, _ -> },
    onOpenIncognito: () -> Unit = {}   // NEW
) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    // ── Tab State ───────────────────────────────────────
    val tabs = remember { mutableStateListOf<BrowserTab>() }
    var activeTabId by remember { mutableStateOf("") }

    // ── ViewModel State (survives config changes) ───────
    val vm = browserViewModel
    var isFullScreen by vm::isFullScreen
    var showUrlBar by vm::showUrlBar
    var showTabSwitcher by vm::showTabSwitcher
    var urlInput by vm::urlInput
    var desktopMode by vm::desktopMode
    var adBlockEnabled by vm::adBlockEnabled
    var bgAudioEnabled by vm::bgAudioEnabled
    var shortsBlockerEnabled by vm::shortsBlockerEnabled
    var pinchZoomEnabled by vm::pinchZoomEnabled
    var manualZoomEnabled by vm::manualZoomEnabled
    var repeatEnabled by vm::repeatEnabled
    var showZoomButtons by vm::showZoomButtons
    var currentZoomLevel by vm::currentZoomLevel

    // ── Container refs ──────────────────────────────────
    val rootContainerRef = remember { mutableStateOf<FrameLayout?>(null) }
    val fullScreenContainerRef = remember { mutableStateOf<FrameLayout?>(null) }

    val activeTab = tabs.find { it.id == activeTabId }

    // ── Sync toggles to WebViewHolder ───────────────────
    LaunchedEffect(adBlockEnabled, bgAudioEnabled, shortsBlockerEnabled,
        pinchZoomEnabled, manualZoomEnabled, repeatEnabled, vm.darkModeEnabled) {
        vm.syncToWebViewHolder()
        WebViewHolder.darkModeEnabled = vm.darkModeEnabled
    }

    // ── Show system bars on entry ───────────────────────
    LaunchedEffect(Unit) { activity?.showSystemBarsNormal() }

    // ── Background Audio Service toggle ─────────────────
    DisposableEffect(bgAudioEnabled) {
        if (bgAudioEnabled) {
            WebViewHolder.isManuallyPaused = false
            BackgroundAudioService.start(context)
        } else BackgroundAudioService.stop(context)
        onDispose { }
    }

    // ── Zoom buttons auto-show/hide in fullscreen ───────
    LaunchedEffect(isFullScreen) {
        if (isFullScreen && manualZoomEnabled) {
            showZoomButtons = true; currentZoomLevel = 1f
            delay(10_000); showZoomButtons = false
        } else showZoomButtons = false
    }

    var zoomButtonsLastUsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(zoomButtonsLastUsed) {
        if (zoomButtonsLastUsed > 0 && showZoomButtons) {
            delay(10_000); showZoomButtons = false
        }
    }

    /** Reset zoom button auto-hide timer */
    fun useZoomButton() {
        showZoomButtons = true
        zoomButtonsLastUsed = System.currentTimeMillis()
    }

    // ── Session Save ────────────────────────────────────
    /** Save current tab state to persistent storage */
    fun saveSessionNow() {
        if (tabs.isNotEmpty()) {
            val savedTabs = tabs.map { SavedTab(url = it.url, title = it.title) }
            val activeIndex = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
            SessionManager.saveSession(context, savedTabs, activeIndex)
            onSessionUpdate(savedTabs, activeIndex)
        }
    }

    LaunchedEffect(tabs.size, activeTabId) { saveSessionNow() }

    // ── Tab Management Functions ────────────────────────

    /** Switch visibility to the given tab */
    fun switchToTab(tabId: String) {
        tabs.forEach { tab ->
            tab.webView?.visibility = if (tab.id == tabId) View.VISIBLE else View.INVISIBLE
        }
        activeTabId = tabId
        tabs.find { it.id == tabId }?.webView?.let { WebViewHolder.activeWebView = it }
        saveSessionNow()
    }

    /** Create a new tab with a WebView and load the given URL */
    fun createTabWithWebView(url: String, root: FrameLayout, fs: FrameLayout): BrowserTab {
        val tab = BrowserTab(initialUrl = url)
        tabs.add(tab)
        var wv: android.webkit.WebView? = null
        wv = createPersistentWebView(
            context = context, fullScreenContainer = fs, desktopMode = desktopMode,
            onPageStarted = { u -> tab.url = u; tab.isLoading = true },
            onPageFinished = { u ->
                tab.url = u; tab.isLoading = false
                tab.canGoBack = wv?.canGoBack() ?: false
                tab.canGoForward = wv?.canGoForward() ?: false
                saveSessionNow()
            },
            onProgressChanged = { p -> tab.progress = p; if (p == 100) tab.isLoading = false },
            onTitleChanged = { t -> tab.title = t; saveSessionNow() },
            onUrlChanged = { u -> tab.url = u; saveSessionNow() },
            onFullScreenEnter = { isFullScreen = true; activity?.setLandscape() },
            onFullScreenExit = { isFullScreen = false; activity?.setPortrait() }
        )
        tab.webView = wv
        wv.visibility = View.INVISIBLE
        root.addView(wv, 0)
        wv.loadUrl(url)
        return tab
    }

    /** Create a new tab and switch to it */
    fun createNewTab(url: String) {
        val root = rootContainerRef.value ?: return
        val fs = fullScreenContainerRef.value ?: return
        val tab = createTabWithWebView(url, root, fs)
        switchToTab(tab.id)
    }

    /** Close a tab, clean up its WebView */
    fun closeTab(tabId: String) {
        val tab = tabs.find { it.id == tabId } ?: return
        tab.webView?.let { wv ->
            wv.stopLoading(); wv.destroy()
            rootContainerRef.value?.removeView(wv)
        }
        tabs.removeAll { it.id == tabId }
        if (tabs.isEmpty()) { SessionManager.clearSession(context); onGoHome() }
        else switchToTab(tabs.last().id)
    }

    // ── Back Handler ────────────────────────────────────
    BackHandler {
        when {
            isFullScreen -> activeTab?.webView?.evaluateJavascript(
                "document.exitFullscreen?document.exitFullscreen():document.webkitExitFullscreen?document.webkitExitFullscreen():null",
                null
            )
            vm.findInPageVisible -> {
                vm.findInPageVisible = false; vm.findQuery = ""
                FindInPageManager.clearFind(activeTab?.webView)
            }
            showTabSwitcher -> showTabSwitcher = false
            showUrlBar -> showUrlBar = false
            activeTab?.canGoBack == true -> activeTab.webView?.goBack()
            else -> { CookieHelper.saveCookies(); saveSessionNow(); onGoHome() }
        }
    }

    // ══════════════════════════════════════════════════════
    // MAIN UI LAYOUT
    // ══════════════════════════════════════════════════════
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize().then(
                if (!isFullScreen) Modifier.windowInsetsPadding(WindowInsets.systemBars)
                else Modifier
            )
        ) {
            // ── Progress Bar ────────────────────────────
            if (activeTab?.isLoading == true && !isFullScreen) {
                LinearProgressIndicator(
                    progress = { (activeTab.progress) / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = if (adBlockEnabled) Color(0xFFFF9800) else Color(0xFF4FC3F7),
                    trackColor = Color.Transparent,
                )
            }

            // ── WebView Container ───────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        val root = FrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                        val fs = FrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            visibility = View.GONE
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                        rootContainerRef.value = root
                        fullScreenContainerRef.value = fs

                        // Restore session or load initial URL
                        val urlsToLoad = if (savedSession != null && savedSession.tabs.isNotEmpty())
                            savedSession.tabs.map { it.url } else listOf(initialUrl)
                        val restoreIndex = savedSession?.activeTabIndex?.coerceIn(0, urlsToLoad.size - 1) ?: 0

                        urlsToLoad.forEachIndexed { index, url ->
                            val tab = BrowserTab(initialUrl = url)
                            tabs.add(tab)
                            var wv: android.webkit.WebView? = null
                            wv = createPersistentWebView(
                                context = ctx, fullScreenContainer = fs, desktopMode = desktopMode,
                                onPageStarted = { u -> tab.url = u; tab.isLoading = true },
                                onPageFinished = { u ->
                                    tab.url = u; tab.isLoading = false
                                    tab.canGoBack = wv?.canGoBack() ?: false
                                    tab.canGoForward = wv?.canGoForward() ?: false
                                    saveSessionNow()
                                },
                                onProgressChanged = { p -> tab.progress = p; if (p == 100) tab.isLoading = false },
                                onTitleChanged = { t -> tab.title = t; saveSessionNow() },
                                onUrlChanged = { u -> tab.url = u; saveSessionNow() },
                                onFullScreenEnter = { isFullScreen = true; activity?.setLandscape() },
                                onFullScreenExit = { isFullScreen = false; activity?.setPortrait() }
                            )
                            tab.webView = wv
                            wv.visibility = View.INVISIBLE
                            root.addView(wv, 0)
                            wv.loadUrl(url)
                            if (index == restoreIndex) {
                                activeTabId = tab.id
                                WebViewHolder.activeWebView = wv
                                wv.visibility = View.VISIBLE
                            }
                        }
                        root.addView(fs)
                        root
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { }
                )
            }
        }

        // ── MANUAL ZOOM BUTTONS (fullscreen only) ───────
        ZoomButtonsOverlay(
            visible = isFullScreen && manualZoomEnabled && showZoomButtons,
            currentZoomLevel = currentZoomLevel,
            onZoomIn = { useZoomButton(); WebViewHolder.stepZoomIn(); currentZoomLevel = WebViewHolder.getCurrentZoom() },
            onZoomOut = { useZoomButton(); WebViewHolder.stepZoomOut(); currentZoomLevel = WebViewHolder.getCurrentZoom() },
            onReset = { useZoomButton(); WebViewHolder.resetFullscreenZoom(); currentZoomLevel = 1f },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // ── ALL OVERLAYS (hidden in PiP mode) ───────────
        if (!vm.isInPipMode) {
            // ── FIND IN PAGE BAR ────────────────────────
            FindInPageBar(
                visible = vm.findInPageVisible && !isFullScreen,
                query = vm.findQuery,
                onQueryChange = {
                    vm.findQuery = it
                    FindInPageManager.find(activeTab?.webView, it)
                },
                onNext = { FindInPageManager.findNext(activeTab?.webView) },
                onPrevious = { FindInPageManager.findPrevious(activeTab?.webView) },
                onClose = {
                    vm.findInPageVisible = false; vm.findQuery = ""
                    FindInPageManager.clearFind(activeTab?.webView)
                },
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
            )

            // ── INCOGNITO INDICATOR ─────────────────────


            // ── URL BAR OVERLAY ─────────────────────────
            UrlBarOverlay(
                visible = showUrlBar && !isFullScreen,
                urlInput = urlInput,
                onUrlChange = { urlInput = it },
                onGo = { q ->
                    if (q.isNotEmpty()) {
                        val url = if (q.contains(".") && !q.contains(" ")) {
                            if (q.startsWith("http")) q else "https://$q"
                        } else "https://www.google.com/search?q=$q"
                        activeTab?.webView?.loadUrl(url); showUrlBar = false
                    }
                },
                onClose = { showUrlBar = false },
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
            )

            // ── TAB SWITCHER OVERLAY ────────────────────
            TabSwitcherOverlay(
                visible = showTabSwitcher && !isFullScreen,
                tabs = tabs, activeTabId = activeTabId,
                onSwitchTab = { switchToTab(it); showTabSwitcher = false },
                onNewTab = { createNewTab("https://www.google.com"); showTabSwitcher = false },
                onCloseTab = { closeTab(it) },
                onDismiss = { showTabSwitcher = false }
            )

            // ── FAB MENU ────────────────────────────────
            if (!isFullScreen && !showTabSwitcher) {
                ExpandableFab(
                    modifier = Modifier.fillMaxSize(),
                    tabCount = tabs.size,
                    items = buildFabItems(
                        vm = vm,
                        activeTabUrl = activeTab?.url,
                        activeTabTitle = activeTab?.title,
                        activeTabCanGoBack = activeTab?.canGoBack ?: false,
                        activeTabCanGoForward = activeTab?.canGoForward ?: false,
                        activeTabIsLoading = activeTab?.isLoading ?: false,
                        activeWebView = activeTab?.webView,
                        tabCount = tabs.size,
                        onShowTabSwitcher = { showTabSwitcher = true },
                        onCreateNewTab = { createNewTab("https://www.google.com") },
                        onGoHome = { CookieHelper.saveCookies(); saveSessionNow(); onGoHome() },
                        onShowUrlBar = { urlInput = activeTab?.url ?: ""; showUrlBar = true },
                        onGoBack = { activeTab?.webView?.goBack() },
                        onGoForward = { activeTab?.webView?.goForward() },
                        onRefreshOrStop = {
                            if (activeTab?.isLoading == true) activeTab.webView?.stopLoading()
                            else activeTab?.webView?.reload()
                        },
                        onBookmark = { onAddToBookmark(activeTab?.title ?: "", activeTab?.url ?: "") },
                        onShare = { url ->
                            context.startActivity(android.content.Intent.createChooser(
                                android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                                }, "Share"))
                        },
                        onClearCache = {
                            CacheManager.clearWebViewCache(context)
                            CacheManager.clearExpiredCookies()
                            Toast.makeText(context, "🗑️ Cleared!", Toast.LENGTH_SHORT).show()
                        },
                        context = context,
                        activity = activity,
                        onOpenIncognito = onOpenIncognito,           // NEW
                        onOpenOfflinePages = { vm.showOfflineViewer = true }  // NEW
                    )
                )
            }
            // ── OFFLINE PAGE VIEWER DIALOG ──────────────────
            if (vm.showOfflineViewer) {
                OfflinePageViewer(
                    onDismiss = { vm.showOfflineViewer = false },
                    onOpenFile = { fileUrl ->
                        createNewTab(fileUrl)
                        vm.showOfflineViewer = false
                    }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// ZOOM BUTTONS OVERLAY — Fullscreen +/- buttons
// ══════════════════════════════════════════════════════
@Composable
fun ZoomButtonsOverlay(
    visible: Boolean, currentZoomLevel: Float,
    onZoomIn: () -> Unit, onZoomOut: () -> Unit, onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible, enter = fadeIn(), exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(end = 16.dp).background(Color.Transparent),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom level display
            Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = "${"%.1f".format(currentZoomLevel)}x",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            // Zoom In (+)
            Surface(onClick = onZoomIn, shape = CircleShape,
                color = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Add, "Zoom In", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
                }
            }
            // Zoom Out (-)
            Surface(onClick = onZoomOut, shape = CircleShape,
                color = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Remove, "Zoom Out", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
                }
            }
            // Reset (1x) — only shows when zoomed
            if (currentZoomLevel > 1.1f) {
                Surface(onClick = onReset, shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("1x", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// FIND IN PAGE BAR — Search text inside webpage
// ══════════════════════════════════════════════════════
@Composable
fun FindInPageBar(
    visible: Boolean, query: String, onQueryChange: (String) -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            color = Color(0xFF1A1A2E).copy(alpha = 0.95f),
            shape = RoundedCornerShape(16.dp), shadowElevation = 12.dp
        ) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).height(48.dp),
                    placeholder = { Text("Find in page...", color = Color(0xFF556677), fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF252538), unfocusedContainerColor = Color(0xFF252538),
                        focusedBorderColor = Color(0xFF4FC3F7), unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFF4FC3F7)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                IconButton(onClick = onPrevious) { Icon(Icons.Default.KeyboardArrowUp, "Previous", tint = Color.White) }
                IconButton(onClick = onNext) { Icon(Icons.Default.KeyboardArrowDown, "Next", tint = Color.White) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White.copy(alpha = 0.7f)) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// URL BAR OVERLAY — Search/URL input
// ══════════════════════════════════════════════════════
@Composable
fun UrlBarOverlay(
    visible: Boolean, urlInput: String, onUrlChange: (String) -> Unit,
    onGo: (String) -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            color = Color(0xFF1A1A2E).copy(alpha = 0.95f),
            shape = RoundedCornerShape(16.dp), shadowElevation = 12.dp
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = urlInput, onValueChange = onUrlChange,
                    modifier = Modifier.weight(1f).height(50.dp), singleLine = true,
                    placeholder = { Text("Search or URL...", color = Color(0xFF556677), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF252538), unfocusedContainerColor = Color(0xFF252538),
                        focusedBorderColor = Color(0xFF4FC3F7), unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFF4FC3F7)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onGo(urlInput.trim()) })
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// TAB SWITCHER OVERLAY — Grid of open tabs
// ══════════════════════════════════════════════════════
@Composable
fun TabSwitcherOverlay(
    visible: Boolean,
    tabs: List<BrowserTab>, activeTabId: String,
    onSwitchTab: (String) -> Unit, onNewTab: () -> Unit,
    onCloseTab: (String) -> Unit, onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xEE0A0A1A))) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars).padding(16.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Open Tabs (${tabs.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = onNewTab, shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF4FC3F7).copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New Tab", color = Color(0xFF4FC3F7), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Tab grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isActive = tab.id == activeTabId
                        TabCard(
                            tab = tab, isActive = isActive,
                            onSelect = { onSwitchTab(tab.id) },
                            onClose = if (tabs.size > 1) {{ onCloseTab(tab.id) }} else null
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// TAB CARD — Single tab preview in the switcher
// ══════════════════════════════════════════════════════
@Composable
private fun TabCard(
    tab: BrowserTab, isActive: Boolean,
    onSelect: () -> Unit, onClose: (() -> Unit)?
) {
    Surface(
        onClick = onSelect, shape = RoundedCornerShape(16.dp),
        color = if (isActive) Color(0xFF1A2540) else Color(0xFF151828),
        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4FC3F7)) else null,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().height(130.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tab.getEmoji(), fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tab.getDisplayTitle(), color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tab.url.removePrefix("https://").removePrefix("www."),
                    color = Color(0xFF556677), fontSize = 11.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                if (isActive) Text("✅ Active", color = Color(0xFF4FC3F7), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            // Close button
            if (onClose != null) {
                Surface(
                    onClick = onClose, shape = CircleShape,
                    color = Color(0xFFE57373).copy(alpha = 0.2f),
                    modifier = Modifier.size(28.dp).align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFFE57373), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}