package com.webwrap.app

import android.annotation.SuppressLint
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
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
import com.webwrap.app.ui.ExpandableFab
import com.webwrap.app.ui.FabItem
import com.webwrap.app.webview.WebViewHolder
import com.webwrap.app.webview.createPersistentWebView
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    initialUrl: String,
    savedSession: SavedSession? = null,
    onGoHome: () -> Unit,
    onAddToBookmark: (String, String) -> Unit,
    onSessionUpdate: (List<SavedTab>, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    val tabs = remember { mutableStateListOf<BrowserTab>() }
    var activeTabId by remember { mutableStateOf("") }
    var isFullScreen by remember { mutableStateOf(false) }
    var showUrlBar by remember { mutableStateOf(false) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var desktopMode by remember { mutableStateOf(false) }
    var adBlockEnabled by remember { mutableStateOf(WebViewHolder.adBlockEnabled) }
    var bgAudioEnabled by remember { mutableStateOf(WebViewHolder.backgroundAudioEnabled) }
    var shortsBlockerEnabled by remember { mutableStateOf(WebViewHolder.shortsBlockerEnabled) }
    var pinchZoomEnabled by remember { mutableStateOf(WebViewHolder.pinchZoomEnabled) }
    var manualZoomEnabled by remember { mutableStateOf(WebViewHolder.manualZoomEnabled) }
    var repeatEnabled by remember { mutableStateOf(WebViewHolder.isRepeatEnabled) }

    // ✅ Manual zoom button visibility
    var showZoomButtons by remember { mutableStateOf(false) }
    var currentZoomLevel by remember { mutableFloatStateOf(1f) }

    val rootContainerRef = remember { mutableStateOf<FrameLayout?>(null) }
    val fullScreenContainerRef = remember { mutableStateOf<FrameLayout?>(null) }

    val activeTab = tabs.find { it.id == activeTabId }

    // Sync states
    LaunchedEffect(adBlockEnabled) { WebViewHolder.adBlockEnabled = adBlockEnabled }
    LaunchedEffect(bgAudioEnabled) { WebViewHolder.backgroundAudioEnabled = bgAudioEnabled }
    LaunchedEffect(shortsBlockerEnabled) { WebViewHolder.shortsBlockerEnabled = shortsBlockerEnabled }
    LaunchedEffect(pinchZoomEnabled) { WebViewHolder.pinchZoomEnabled = pinchZoomEnabled }
    LaunchedEffect(manualZoomEnabled) { WebViewHolder.manualZoomEnabled = manualZoomEnabled }
    LaunchedEffect(repeatEnabled) { WebViewHolder.isRepeatEnabled = repeatEnabled }


    LaunchedEffect(Unit) { activity?.showSystemBarsNormal() }

    DisposableEffect(bgAudioEnabled) {
        if (bgAudioEnabled) {
            WebViewHolder.isManuallyPaused = false
            BackgroundAudioService.start(context)
        } else BackgroundAudioService.stop(context)
        onDispose { }
    }

    // ═══════════════════════════════════════════════
    // ✅ Show zoom buttons when entering fullscreen
    //    Auto-hide after 10 seconds
    // ═══════════════════════════════════════════════
    LaunchedEffect(isFullScreen) {
        if (isFullScreen && manualZoomEnabled) {
            showZoomButtons = true
            currentZoomLevel = 1f
            delay(10_000)
            showZoomButtons = false
        } else {
            showZoomButtons = false
        }
    }

    // Auto-hide zoom buttons after 10s of no use
    var zoomButtonsLastUsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(zoomButtonsLastUsed) {
        if (zoomButtonsLastUsed > 0 && showZoomButtons) {
            delay(10_000)
            showZoomButtons = false
        }
    }

    fun useZoomButton() {
        showZoomButtons = true
        zoomButtonsLastUsed = System.currentTimeMillis()
    }

    // Session save
    fun saveSessionNow() {
        if (tabs.isNotEmpty()) {
            val savedTabs = tabs.map { SavedTab(url = it.url, title = it.title) }
            val activeIndex = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
            SessionManager.saveSession(context, savedTabs, activeIndex)
            onSessionUpdate(savedTabs, activeIndex)
        }
    }

    LaunchedEffect(tabs.size, activeTabId) { saveSessionNow() }

    fun switchToTab(tabId: String) {
        tabs.forEach { tab ->
            tab.webView?.visibility = if (tab.id == tabId) View.VISIBLE else View.INVISIBLE
        }
        activeTabId = tabId
        tabs.find { it.id == tabId }?.webView?.let { WebViewHolder.activeWebView = it }
        saveSessionNow()
    }

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

    fun createNewTab(url: String) {
        val root = rootContainerRef.value ?: return
        val fs = fullScreenContainerRef.value ?: return
        val tab = createTabWithWebView(url, root, fs)
        switchToTab(tab.id)
    }

    fun closeTab(tabId: String) {
        val tab = tabs.find { it.id == tabId } ?: return
        tab.webView?.let { wv -> wv.stopLoading(); wv.destroy(); rootContainerRef.value?.removeView(wv) }
        tabs.removeAll { it.id == tabId }
        if (tabs.isEmpty()) { SessionManager.clearSession(context); onGoHome() }
        else switchToTab(tabs.last().id)
    }

    BackHandler {
        when {
            isFullScreen -> activeTab?.webView?.evaluateJavascript(
                "document.exitFullscreen?document.exitFullscreen():document.webkitExitFullscreen?document.webkitExitFullscreen():null", null
            )
            showTabSwitcher -> showTabSwitcher = false
            showUrlBar -> showUrlBar = false
            activeTab?.canGoBack == true -> activeTab.webView?.goBack()
            else -> { CookieHelper.saveCookies(); saveSessionNow(); onGoHome() }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize().then(
                if (!isFullScreen) Modifier.windowInsetsPadding(WindowInsets.systemBars)
                else Modifier
            )
        ) {
            if (activeTab?.isLoading == true && !isFullScreen) {
                LinearProgressIndicator(
                    progress = { (activeTab.progress) / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = if (adBlockEnabled) Color(0xFFFF9800) else Color(0xFF4FC3F7),
                    trackColor = Color.Transparent,
                )
            }

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

        // ═══════════════════════════════════════════════
        // ✅ MANUAL ZOOM BUTTONS — Fullscreen only
        //    Transparent +/- buttons, auto-hide 10s
        // ═══════════════════════════════════════════════
        AnimatedVisibility(
            visible = isFullScreen && manualZoomEnabled && showZoomButtons,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .background(Color.Transparent),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Zoom level indicator
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${"%.1f".format(currentZoomLevel)}x",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // ✅ ZOOM IN (+)
                Surface(
                    onClick = {
                        useZoomButton()
                        WebViewHolder.stepZoomIn()
                        currentZoomLevel = WebViewHolder.getCurrentZoom()
                    },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Add, "Zoom In",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // ✅ ZOOM OUT (-)
                Surface(
                    onClick = {
                        useZoomButton()
                        WebViewHolder.stepZoomOut()
                        currentZoomLevel = WebViewHolder.getCurrentZoom()
                    },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Remove, "Zoom Out",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // ✅ RESET ZOOM
                if (currentZoomLevel > 1.1f) {
                    Surface(
                        onClick = {
                            useZoomButton()
                            WebViewHolder.resetFullscreenZoom()
                            currentZoomLevel = 1f
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                "1x", color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // URL Bar overlay
        AnimatedVisibility(
            visible = showUrlBar && !isFullScreen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                color = Color(0xFF1A1A2E).copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp), shadowElevation = 12.dp
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = urlInput, onValueChange = { urlInput = it },
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
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val q = urlInput.trim()
                                if (q.isNotEmpty()) {
                                    val url = if (q.contains(".") && !q.contains(" ")) { if (q.startsWith("http")) q else "https://$q" } else "https://www.google.com/search?q=$q"
                                    activeTab?.webView?.loadUrl(url); showUrlBar = false
                                }
                            }
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { showUrlBar = false }) { Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
                }
            }
        }

        // Tab Switcher overlay
        AnimatedVisibility(
            visible = showTabSwitcher && !isFullScreen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xEE0A0A1A))) {
                Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Open Tabs (${tabs.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(onClick = { createNewTab("https://www.google.com"); showTabSwitcher = false }, shape = RoundedCornerShape(12.dp), color = Color(0xFF4FC3F7).copy(alpha = 0.2f)) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp)); Text("New Tab", color = Color(0xFF4FC3F7), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            IconButton(onClick = { showTabSwitcher = false }) { Icon(Icons.Default.Close, null, tint = Color.White) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        items(tabs, key = { it.id }) { tab ->
                            val isActive = tab.id == activeTabId
                            Surface(onClick = { switchToTab(tab.id); showTabSwitcher = false }, shape = RoundedCornerShape(16.dp), color = if (isActive) Color(0xFF1A2540) else Color(0xFF151828), border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4FC3F7)) else null, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().height(130.dp)) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) { Text(tab.getEmoji(), fontSize = 22.sp); Spacer(Modifier.width(8.dp)); Text(tab.getDisplayTitle(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)) }
                                        Spacer(Modifier.height(8.dp)); Text(tab.url.removePrefix("https://").removePrefix("www."), color = Color(0xFF556677), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.weight(1f))
                                        if (isActive) Text("● Active", color = Color(0xFF4FC3F7), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (tabs.size > 1) { Surface(onClick = { closeTab(tab.id) }, shape = CircleShape, color = Color(0xFFE57373).copy(alpha = 0.2f), modifier = Modifier.size(28.dp).align(Alignment.TopEnd).padding(4.dp)) { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Icon(Icons.Default.Close, null, tint = Color(0xFFE57373), modifier = Modifier.size(14.dp)) } } }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════
        // ✅ FAB with zoom toggles
        // ═══════════════════════════════════════════════
        if (!isFullScreen && !showTabSwitcher) {
            ExpandableFab(
                modifier = Modifier.fillMaxSize(),
                tabCount = tabs.size,
                items = listOf(
                    FabItem(icon = Icons.Default.Tab, label = "Tabs (${tabs.size})", activeColor = Color(0xFF4FC3F7), inactiveColor = Color(0xFF4FC3F7), onClick = { showTabSwitcher = true }),
                    FabItem(icon = Icons.Default.AddBox, label = "New Tab", activeColor = Color(0xFF81C784), inactiveColor = Color(0xFF81C784), onClick = { createNewTab("https://www.google.com") }),
                    FabItem(icon = Icons.Default.Home, label = "Home", activeColor = Color(0xFF4FC3F7), inactiveColor = Color(0xFF4FC3F7), onClick = { CookieHelper.saveCookies(); saveSessionNow(); onGoHome() }),
                    FabItem(icon = Icons.Default.Search, label = "Search / URL", activeColor = Color.White, inactiveColor = Color.White, onClick = { urlInput = activeTab?.url ?: ""; showUrlBar = true }),
                    FabItem(icon = Icons.Default.ArrowBack, label = "Back", inactiveColor = if (activeTab?.canGoBack == true) Color.White else Color(0xFF444444), onClick = { activeTab?.webView?.goBack() }),
                    FabItem(icon = Icons.Default.ArrowForward, label = "Forward", inactiveColor = if (activeTab?.canGoForward == true) Color.White else Color(0xFF444444), onClick = { activeTab?.webView?.goForward() }),
                    FabItem(icon = if (activeTab?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh, label = if (activeTab?.isLoading == true) "Stop" else "Refresh", activeColor = Color.White, inactiveColor = Color.White, onClick = { if (activeTab?.isLoading == true) activeTab.webView?.stopLoading() else activeTab?.webView?.reload() }),
                    // ✅ PINCH ZOOM TOGGLE
                    FabItem(
                        icon = Icons.Default.Gesture,
                        label = if (pinchZoomEnabled) "Pinch Zoom: ON" else "Pinch Zoom: OFF",
                        isToggle = true, isOn = pinchZoomEnabled,
                        activeColor = Color(0xFF9C27B0),
                        onClick = {
                            pinchZoomEnabled = !pinchZoomEnabled
                            Toast.makeText(context, if (pinchZoomEnabled) "👌 Pinch Zoom ON" else "Pinch Zoom OFF", Toast.LENGTH_SHORT).show()
                        }
                    ),
                    // ✅ MANUAL ZOOM TOGGLE
                    FabItem(
                        icon = Icons.Default.ZoomIn,
                        label = if (manualZoomEnabled) "Button Zoom: ON" else "Button Zoom: OFF",
                        isToggle = true, isOn = manualZoomEnabled,
                        activeColor = Color(0xFF00BCD4),
                        onClick = {
                            manualZoomEnabled = !manualZoomEnabled
                            Toast.makeText(context, if (manualZoomEnabled) "🔍 +/- Zoom ON (fullscreen)" else "+/- Zoom OFF", Toast.LENGTH_SHORT).show()
                        }
                    ),
                    FabItem(icon = Icons.Default.ShortText, label = if (shortsBlockerEnabled) "Shorts:Blocked" else "Shorts: Showing", isToggle = true, isOn = shortsBlockerEnabled, activeColor = Color(0xFFE91E63), onClick = { shortsBlockerEnabled = !shortsBlockerEnabled; activeTab?.webView?.reload(); Toast.makeText(context, if (shortsBlockerEnabled) "🚫 Shorts Blocked" else "📱 Shorts Enabled", Toast.LENGTH_SHORT).show() }),
                    FabItem(icon = Icons.Default.Shield, label = if (adBlockEnabled) "Ads: Blocked" else "Ads: Showing", isToggle = true, isOn = adBlockEnabled, activeColor = Color(0xFF4CAF50), onClick = { adBlockEnabled = !adBlockEnabled; activeTab?.webView?.reload() }),
                    // Add after the BG Audio FabItem
                    FabItem(
                        icon = Icons.Default.Repeat,
                        label = if ( repeatEnabled ) "Repeat: ON" else "Repeat: OFF",
                        isToggle = true,
                        isOn = repeatEnabled,
                        activeColor = Color(0xFF00BCD4),
                        onClick = {
                            //repeatEnabled = !repeatEnabled
                            WebViewHolder.toggleRepeat()
                            repeatEnabled = WebViewHolder.isRepeatEnabled
                            Toast.makeText(
                                context,
                                if (repeatEnabled) "🔁 Repeat ON" else "➡️ Repeat OFF",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ),
                    FabItem(icon = Icons.Default.MusicNote, label = if (bgAudioEnabled) "BG Audio: ON" else "BG Audio: OFF", isToggle = true, isOn = bgAudioEnabled, activeColor = Color(0xFF4FC3F7), onClick = { bgAudioEnabled = !bgAudioEnabled }),
                    FabItem(icon = Icons.Default.Computer, label = if (desktopMode) "Desktop: ON" else "Desktop: OFF", isToggle = true, isOn = desktopMode, activeColor = Color(0xFFFF9800), onClick = { desktopMode = !desktopMode; activeTab?.webView?.settings?.userAgentString = if (desktopMode) "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36" else null; activeTab?.webView?.reload() }),
                    FabItem(icon = Icons.Default.BookmarkAdd, label = "Bookmark", activeColor = Color(0xFFFFB74D), inactiveColor = Color(0xFFFFB74D), onClick = { onAddToBookmark(activeTab?.title ?: "", activeTab?.url ?: "") }),
                    FabItem(icon = Icons.Default.Share, label = "Share", activeColor = Color.White, inactiveColor = Color.White, onClick = { context.startActivity(android.content.Intent.createChooser(android.content.Intent().apply { action = android.content.Intent.ACTION_SEND; type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, activeTab?.url ?: "") }, "Share")) }),
                    FabItem(icon = Icons.Default.CleaningServices, label = "Clear Cache", activeColor = Color(0xFFE57373), inactiveColor = Color(0xFFE57373), onClick = { CacheManager.clearWebViewCache(context); CacheManager.clearExpiredCookies(); Toast.makeText(context, "🗑️ Cleared!", Toast.LENGTH_SHORT).show() }),
                )
            )
        }
    }
}