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
import androidx.navigation.NavController
import com.webwrap.app.data.*
import com.webwrap.app.feature.darkmode.WebDarkMode
import com.webwrap.app.feature.findinpage.FindInPageBar
import com.webwrap.app.feature.findinpage.FindInPageHelper
import com.webwrap.app.feature.incognito.IncognitoManager
import com.webwrap.app.navigation.NavRoutes
import com.webwrap.app.service.BackgroundAudioService
import com.webwrap.app.ui.ExpandableFab
import com.webwrap.app.ui.FabItem
import com.webwrap.app.viewmodel.BrowserViewModel
import com.webwrap.app.webview.WebViewHolder
import com.webwrap.app.webview.createPersistentWebView

// ============================================================
// MAIN WEB VIEW SCREEN
// ============================================================

/**
 * Browser screen with WebView, tabs, FAB menu,
 * URL bar, tab switcher, zoom, and all toggles.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    viewModel: BrowserViewModel,
    initialUrl: String,
    isIncognito: Boolean = false,
    onNavigateToHomeScreen: () -> Unit,
    onGoHome: () -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    // ===== COLLECT VIEWMODEL STATE =====
    val isFullScreen by viewModel.isFullScreen
        .collectAsState()
    val showUrlBar by viewModel.showUrlBar
        .collectAsState()
    val showTabSwitcher by viewModel.showTabSwitcher
        .collectAsState()
    val urlInput by viewModel.urlInput
        .collectAsState()
    val desktopMode by viewModel.desktopMode
        .collectAsState()
    val adBlockEnabled by viewModel.adBlockEnabled
        .collectAsState()
    val bgAudioEnabled by viewModel.bgAudioEnabled
        .collectAsState()
    val shortsBlockerEnabled by viewModel
        .shortsBlockerEnabled.collectAsState()
    val pinchZoomEnabled by viewModel
        .pinchZoomEnabled.collectAsState()
    val manualZoomEnabled by viewModel
        .manualZoomEnabled.collectAsState()
    val repeatEnabled by viewModel.repeatEnabled
        .collectAsState()
    val darkModeEnabled by viewModel.darkModeEnabled
        .collectAsState()
    val findInPageVisible by viewModel
        .findInPageVisible.collectAsState()
    val findQuery by viewModel.findQuery
        .collectAsState()

    // ===== LOCAL WEBVIEW STATE =====
    // (Cannot go in ViewModel — holds Android Context)
    val tabs = remember {
        mutableStateListOf<BrowserTab>()
    }
    var activeTabId by remember {
        mutableStateOf("")
    }
    var showZoomButtons by remember {
        mutableStateOf(false)
    }
    var currentZoomLevel by remember {
        mutableFloatStateOf(1f)
    }
    val rootContainerRef = remember {
        mutableStateOf<FrameLayout?>(null)
    }
    val fullScreenContainerRef = remember {
        mutableStateOf<FrameLayout?>(null)
    }
    val activeTab = tabs.find { it.id == activeTabId }

    // Consume saved session once
    val savedSession = remember {
        if (!isIncognito) {
            viewModel.consumePendingSession()
        } else null
    }

    // ===== INCOGNITO LIFECYCLE =====
    DisposableEffect(isIncognito) {
        if (isIncognito) {
            IncognitoManager.enterIncognito()
            viewModel.setIncognito(true)
        }
        onDispose {
            if (isIncognito) {
                tabs.forEach { tab ->
                    IncognitoManager.cleanupWebView(
                        tab.webView
                    )
                }
                IncognitoManager.exitIncognito()
            }
        }
    }

    // ===== SYNC STATES =====
    LaunchedEffect(Unit) {
        activity?.showSystemBarsNormal()
    }

    // Background audio service
    DisposableEffect(bgAudioEnabled) {
        if (bgAudioEnabled && !isIncognito) {
            WebViewHolder.isManuallyPaused = false
            BackgroundAudioService.start(context)
        } else {
            BackgroundAudioService.stop(context)
        }
        onDispose { }
    }

    // Zoom buttons auto-show/hide
    LaunchedEffect(isFullScreen) {
        if (isFullScreen && manualZoomEnabled) {
            showZoomButtons = true
            currentZoomLevel = 1f
            kotlinx.coroutines.delay(10_000)
            showZoomButtons = false
        } else {
            showZoomButtons = false
        }
    }

    var zoomLastUsed by remember {
        mutableLongStateOf(0L)
    }
    LaunchedEffect(zoomLastUsed) {
        if (zoomLastUsed > 0 && showZoomButtons) {
            kotlinx.coroutines.delay(10_000)
            showZoomButtons = false
        }
    }

    /** Reset zoom timer on use. */
    fun useZoomButton() {
        showZoomButtons = true
        zoomLastUsed = System.currentTimeMillis()
    }

    // ===== SESSION MANAGEMENT =====

    /** Save current session to disk. */
    fun saveSessionNow() {
        if (tabs.isNotEmpty() && !isIncognito) {
            val savedTabs = tabs.map {
                SavedTab(url = it.url, title = it.title)
            }
            val idx = tabs.indexOfFirst {
                it.id == activeTabId
            }.coerceAtLeast(0)
            viewModel.saveSession(savedTabs, idx)
        }
    }

    LaunchedEffect(tabs.size, activeTabId) {
        saveSessionNow()
    }

    // ===== TAB FUNCTIONS =====

    /** Switch active tab by ID. */
    fun switchToTab(tabId: String) {
        tabs.forEach { tab ->
            tab.webView?.visibility =
                if (tab.id == tabId) View.VISIBLE
                else View.INVISIBLE
        }
        activeTabId = tabId
        tabs.find { it.id == tabId }
            ?.webView?.let {
                WebViewHolder.activeWebView = it
            }
        saveSessionNow()
    }

    /** Create a new tab with WebView. */
    fun createTabWithWebView(
        url: String,
        root: FrameLayout,
        fs: FrameLayout
    ): BrowserTab {
        val tab = BrowserTab(initialUrl = url)
        tabs.add(tab)
        var wv: android.webkit.WebView? = null
        wv = createPersistentWebView(
            context = context,
            fullScreenContainer = fs,
            desktopMode = desktopMode,
            isIncognito = isIncognito,
            darkModeEnabled = darkModeEnabled,
            onPageStarted = { u ->
                tab.url = u
                tab.isLoading = true
            },
            onPageFinished = { u ->
                tab.url = u
                tab.isLoading = false
                tab.canGoBack =
                    wv?.canGoBack() ?: false
                tab.canGoForward =
                    wv?.canGoForward() ?: false
                saveSessionNow()
            },
            onProgressChanged = { p ->
                tab.progress = p
                if (p == 100) tab.isLoading = false
            },
            onTitleChanged = { t ->
                tab.title = t
                saveSessionNow()
            },
            onUrlChanged = { u ->
                tab.url = u
                saveSessionNow()
            },
            onFullScreenEnter = {
                viewModel.setFullScreen(true)
                activity?.setLandscape()
            },
            onFullScreenExit = {
                viewModel.setFullScreen(false)
                activity?.setPortrait()
            }
        )
        tab.webView = wv
        wv.visibility = View.INVISIBLE
        root.addView(wv, 0)
        wv.loadUrl(url)
        return tab
    }

    /** Create new tab and switch to it. */
    fun createNewTab(url: String) {
        val root = rootContainerRef.value ?: return
        val fs = fullScreenContainerRef.value ?: return
        val tab = createTabWithWebView(url, root, fs)
        switchToTab(tab.id)
    }

    /** Close a tab and clean up. */
    fun closeTab(tabId: String) {
        val tab = tabs.find {
            it.id == tabId
        } ?: return
        tab.webView?.let { wv ->
            wv.stopLoading()
            wv.destroy()
            rootContainerRef.value?.removeView(wv)
        }
        tabs.removeAll { it.id == tabId }
        if (tabs.isEmpty()) {
            if (!isIncognito) {
                SessionManager.clearSession(context)
            }
            onGoHome()
        } else {
            switchToTab(tabs.last().id)
        }
    }

    // ===== BACK HANDLER =====
    BackHandler {
        when {
            isFullScreen -> {
                activeTab?.webView?.evaluateJavascript(
                    buildExitFullscreenJs(),
                    null
                )
            }
            findInPageVisible -> {
                viewModel.setFindInPageVisible(false)
                FindInPageHelper.clearFind(
                    activeTab?.webView
                )
            }
            showTabSwitcher -> {
                viewModel.setShowTabSwitcher(false)
            }
            showUrlBar -> {
                viewModel.setShowUrlBar(false)
            }
            activeTab?.canGoBack == true -> {
                activeTab.webView?.goBack()
            }
            else -> {
                viewModel.saveCookies()
                saveSessionNow()
                onGoHome()

            }
        }
    }

    // ===== CLEANUP ON DISPOSE =====
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveCookies()
            tabs.forEach { tab ->
                tab.webView?.stopLoading()
                if (isIncognito) {
                    IncognitoManager.cleanupWebView(
                        tab.webView
                    )
                }
            }
        }
    }

    // ===== MAIN UI =====
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isFullScreen) {
                        Modifier.windowInsetsPadding(
                            WindowInsets.systemBars
                        )
                    } else Modifier
                )
        ) {
            // Incognito indicator bar
            if (isIncognito && !isFullScreen) {
                IncognitoIndicatorBar()
            }

            // Loading progress bar
            if (activeTab?.isLoading == true &&
                !isFullScreen
            ) {
                LinearProgressIndicator(
                    progress = {
                        activeTab.progress / 100f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = when {
                        isIncognito ->
                            Color(0xFF607D8B)
                        adBlockEnabled ->
                            Color(0xFFFF9800)
                        else ->
                            Color(0xFF4FC3F7)
                    },
                    trackColor = Color.Transparent,
                )
            }

            // WebView container
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        createWebViewContainer(
                            ctx = ctx,
                            savedSession = savedSession,
                            initialUrl = initialUrl,
                            rootRef = rootContainerRef,
                            fsRef = fullScreenContainerRef,
                            tabs = tabs,
                            desktopMode = desktopMode,
                            isIncognito = isIncognito,
                            darkModeEnabled =
                                darkModeEnabled,
                            onTabCreated = { tab, idx,
                                             restoreIdx ->
                                if (idx == restoreIdx) {
                                    activeTabId = tab.id
                                    WebViewHolder
                                        .activeWebView =
                                        tab.webView
                                    tab.webView
                                        ?.visibility =
                                        View.VISIBLE
                                }
                            },
                            onPageStarted = { tab, u ->
                                tab.url = u
                                tab.isLoading = true
                            },
                            onPageFinished =
                                { tab, u, wv ->
                                    tab.url = u
                                    tab.isLoading = false
                                    tab.canGoBack =
                                        wv?.canGoBack()
                                            ?: false
                                    tab.canGoForward =
                                        wv?.canGoForward()
                                            ?: false
                                    saveSessionNow()
                                },
                            onProgressChanged =
                                { tab, p ->
                                    tab.progress = p
                                    if (p == 100) {
                                        tab.isLoading =
                                            false
                                    }
                                },
                            onTitleChanged = { tab, t ->
                                tab.title = t
                                saveSessionNow()
                            },
                            onUrlChanged = { tab, u ->
                                tab.url = u
                                saveSessionNow()
                            },
                            onFullScreenEnter = {
                                viewModel.setFullScreen(
                                    true
                                )
                                activity?.setLandscape()
                            },
                            onFullScreenExit = {
                                viewModel.setFullScreen(
                                    false
                                )
                                activity?.setPortrait()
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { }
                )
            }
        }

        // MANUAL ZOOM BUTTONS
        ZoomButtonsOverlay(
            visible = isFullScreen &&
                    manualZoomEnabled &&
                    showZoomButtons,
            currentZoom = currentZoomLevel,
            onZoomIn = {
                useZoomButton()
                WebViewHolder.stepZoomIn()
                currentZoomLevel =
                    WebViewHolder.getCurrentZoom()
            },
            onZoomOut = {
                useZoomButton()
                WebViewHolder.stepZoomOut()
                currentZoomLevel =
                    WebViewHolder.getCurrentZoom()
            },
            onReset = {
                useZoomButton()
                WebViewHolder.resetFullscreenZoom()
                currentZoomLevel = 1f
            },
            modifier = Modifier.align(
                Alignment.CenterEnd
            )
        )

        // FIND IN PAGE BAR
        AnimatedVisibility(
            visible = findInPageVisible &&
                    !isFullScreen,
            enter = slideInVertically(
                initialOffsetY = { -it }
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it }
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(
                    WindowInsets.statusBars
                )
        ) {
            FindInPageBar(
                query = findQuery,
                onQueryChange = { q ->
                    viewModel.setFindQuery(q)
                    FindInPageHelper.find(
                        activeTab?.webView, q
                    )
                },
                onFindNext = {
                    FindInPageHelper.findNext(
                        activeTab?.webView
                    )
                },
                onFindPrevious = {
                    FindInPageHelper.findPrevious(
                        activeTab?.webView
                    )
                },
                onClose = {
                    viewModel.setFindInPageVisible(
                        false
                    )
                    FindInPageHelper.clearFind(
                        activeTab?.webView
                    )
                }
            )
        }

        // URL BAR OVERLAY
        UrlBarOverlay(
            visible = showUrlBar && !isFullScreen,
            urlInput = urlInput,
            onInputChange = {
                viewModel.setUrlInput(it)
            },
            onGo = {
                val q = urlInput.trim()
                if (q.isNotEmpty()) {
                    val url = viewModel.queryToUrl(q)
                    activeTab?.webView?.loadUrl(url)
                    viewModel.setShowUrlBar(false)
                }
            },
            onClose = {
                viewModel.setShowUrlBar(false)
            },
            modifier = Modifier.align(
                Alignment.TopCenter
            )
        )

        // TAB SWITCHER OVERLAY
        TabSwitcherOverlay(
            visible = showTabSwitcher && !isFullScreen,
            tabs = tabs,
            activeTabId = activeTabId,
            onTabSelect = { tabId ->
                switchToTab(tabId)
                viewModel.setShowTabSwitcher(false)
            },
            onTabClose = { tabId ->
                closeTab(tabId)
            },
            onNewTab = {
                createNewTab(
                    "https://www.google.com"
                )
                viewModel.setShowTabSwitcher(false)
            },
            onDismiss = {
                viewModel.setShowTabSwitcher(false)
            }
        )

        // FAB MENU
        if (!isFullScreen && !showTabSwitcher) {
            ExpandableFab(
                modifier = Modifier.fillMaxSize(),
                tabCount = tabs.size,
                items = buildFabItems(
                    viewModel = viewModel,
                    context = context,
                    activeTab = activeTab,
                    tabs = tabs,
                    isIncognito = isIncognito,
                    desktopMode = desktopMode,
                    adBlockEnabled = adBlockEnabled,
                    bgAudioEnabled = bgAudioEnabled,
                    shortsBlockerEnabled =
                        shortsBlockerEnabled,
                    pinchZoomEnabled = pinchZoomEnabled,
                    manualZoomEnabled =
                        manualZoomEnabled,
                    repeatEnabled = repeatEnabled,
                    darkModeEnabled = darkModeEnabled,
                    onShowTabSwitcher = {
                        viewModel.setShowTabSwitcher(
                            true
                        )
                    },
                    onNewTab = {
                        createNewTab(
                            "https://www.google.com"
                        )
                    },
                    onGoHome = {
                        viewModel.saveCookies()
                        saveSessionNow()
                        onGoHome()
//                        onNavigateToHomeScreen()
                        //navController.navigate("home")
                    },
                    onShowUrlBar = {
                        viewModel.setUrlInput(
                            activeTab?.url ?: ""
                        )
                        viewModel.setShowUrlBar(true)
                    },
                    onShowFindInPage = {
                        viewModel.setFindInPageVisible(
                            true
                        )
                    }
                )
            )
        }
    }
}

// ============================================================
// INCOGNITO INDICATOR BAR
// ============================================================

/** Small bar showing incognito mode is active. */
@Composable
private fun IncognitoIndicatorBar() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        color = Color(0xFF37474F)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement =
                Arrangement.Center,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.VisibilityOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Incognito Mode",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ============================================================
// ZOOM BUTTONS OVERLAY
// ============================================================

/** Fullscreen zoom +/- buttons with auto-hide. */
@Composable
private fun ZoomButtonsOverlay(
    visible: Boolean,
    currentZoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(end = 16.dp)
                .background(Color.Transparent),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            // Zoom level display
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${"%.1f".format(
                        currentZoom
                    )}x",
                    color = Color.White
                        .copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
                )
            }

            // Zoom In button
            ZoomCircleButton(
                icon = Icons.Default.Add,
                label = "Zoom In",
                onClick = onZoomIn
            )

            // Zoom Out button
            ZoomCircleButton(
                icon = Icons.Default.Remove,
                label = "Zoom Out",
                onClick = onZoomOut
            )

            // Reset button
            if (currentZoom > 1.1f) {
                Surface(
                    onClick = onReset,
                    shape = CircleShape,
                    color = Color.White
                        .copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center,
                        modifier =
                            Modifier.fillMaxSize()
                    ) {
                        Text(
                            "1x",
                            color = Color.White
                                .copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** Circular zoom button helper. */
@Composable
private fun ZoomCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.25f),
        modifier = Modifier.size(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                icon, label,
                tint = Color.White
                    .copy(alpha = 0.8f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ============================================================
// URL BAR OVERLAY
// ============================================================

/** Animated URL input bar overlay. */
@Composable
private fun UrlBarOverlay(
    visible: Boolean,
    urlInput: String,
    onInputChange: (String) -> Unit,
    onGo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it }
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it }
        ) + fadeOut(),
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.statusBars
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            color = Color(0xFF1A1A2E)
                .copy(alpha = 0.95f),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Search or URL...",
                            color = Color(0xFF556677),
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF4FC3F7),
                            modifier =
                                Modifier.size(18.dp)
                        )
                    },
                    colors =
                        OutlinedTextFieldDefaults
                            .colors(
                                focusedTextColor =
                                    Color.White,
                                unfocusedTextColor =
                                    Color.White,
                                focusedContainerColor =
                                    Color(0xFF252538),
                                unfocusedContainerColor =
                                    Color(0xFF252538),
                                focusedBorderColor =
                                    Color(0xFF4FC3F7),
                                unfocusedBorderColor =
                                    Color.Transparent,
                                cursorColor =
                                    Color(0xFF4FC3F7)
                            ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions =
                        KeyboardOptions(
                            imeAction = ImeAction.Go
                        ),
                    keyboardActions =
                        KeyboardActions(onGo = { onGo() })
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White
                            .copy(alpha = 0.7f),
                        modifier =
                            Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ============================================================
// TAB SWITCHER OVERLAY
// ============================================================

/** Full-screen tab switcher grid overlay. */
@Composable
private fun TabSwitcherOverlay(
    visible: Boolean,
    tabs: List<BrowserTab>,
    activeTabId: String,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { it }
        ),
        exit = fadeOut() + slideOutVertically(
            targetOffsetY = { it }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE0A0A1A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                    )
                    .padding(16.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        "Open Tabs (${tabs.size})",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        // New tab button
                        Surface(
                            onClick = onNewTab,
                            shape = RoundedCornerShape(
                                12.dp
                            ),
                            color = Color(0xFF4FC3F7)
                                .copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(
                                        horizontal =
                                            14.dp,
                                        vertical = 8.dp
                                    ),
                                verticalAlignment =
                                    Alignment
                                        .CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription =
                                        null,
                                    tint =
                                        Color(
                                            0xFF4FC3F7
                                        ),
                                    modifier =
                                        Modifier
                                            .size(18.dp)
                                )
                                Spacer(
                                    Modifier.width(4.dp)
                                )
                                Text(
                                    "New Tab",
                                    color =
                                        Color(
                                            0xFF4FC3F7
                                        ),
                                    fontSize = 13.sp,
                                    fontWeight =
                                        FontWeight
                                            .SemiBold
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription =
                                    null,
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Tab grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp),
                    modifier =
                        Modifier.fillMaxSize()
                ) {
                    items(
                        tabs,
                        key = { it.id }
                    ) { tab ->
                        TabCard(
                            tab = tab,
                            isActive =
                                tab.id == activeTabId,
                            canClose = tabs.size > 1,
                            onSelect = {
                                onTabSelect(tab.id)
                            },
                            onClose = {
                                onTabClose(tab.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Single tab card in the tab switcher. */
@Composable
private fun TabCard(
    tab: BrowserTab,
    isActive: Boolean,
    canClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) Color(0xFF1A2540)
        else Color(0xFF151828),
        border = if (isActive) {
            androidx.compose.foundation
                .BorderStroke(
                    2.dp, Color(0xFF4FC3F7)
                )
        } else null,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        tab.getEmoji(),
                        fontSize = 22.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tab.getDisplayTitle(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight =
                            FontWeight.SemiBold,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis,
                        modifier =
                            Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tab.url
                        .removePrefix("https://")
                        .removePrefix("www."),
                    color = Color(0xFF556677),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                if (isActive) {
                    Text(
                        "● Active",
                        color = Color(0xFF4FC3F7),
                        fontSize = 11.sp,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }

            // Close button
            if (canClose) {
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = Color(0xFFE57373)
                        .copy(alpha = 0.2f),
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center,
                        modifier =
                            Modifier.fillMaxSize()
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint =
                                Color(0xFFE57373),
                            modifier =
                                Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// WEBVIEW CONTAINER FACTORY
// ============================================================

/**
 * Creates the root FrameLayout with WebViews.
 * Called once in AndroidView factory.
 */
private fun createWebViewContainer(
    ctx: android.content.Context,
    savedSession: SavedSession?,
    initialUrl: String,
    rootRef: MutableState<FrameLayout?>,
    fsRef: MutableState<FrameLayout?>,
    tabs: MutableList<BrowserTab>,
    desktopMode: Boolean,
    isIncognito: Boolean,
    darkModeEnabled: Boolean,
    onTabCreated: (BrowserTab, Int, Int) -> Unit,
    onPageStarted: (BrowserTab, String) -> Unit,
    onPageFinished: (
        BrowserTab, String,
        android.webkit.WebView?
    ) -> Unit,
    onProgressChanged: (BrowserTab, Int) -> Unit,
    onTitleChanged: (BrowserTab, String) -> Unit,
    onUrlChanged: (BrowserTab, String) -> Unit,
    onFullScreenEnter: () -> Unit,
    onFullScreenExit: () -> Unit
): FrameLayout {
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
        setBackgroundColor(
            android.graphics.Color.BLACK
        )
    }
    rootRef.value = root
    fsRef.value = fs

    // Determine URLs to load
    val urlsToLoad = if (
        savedSession != null &&
        savedSession.tabs.isNotEmpty() &&
        !isIncognito
    ) {
        savedSession.tabs.map { it.url }
    } else {
        listOf(initialUrl)
    }
    val restoreIdx = savedSession
        ?.activeTabIndex
        ?.coerceIn(0, urlsToLoad.size - 1) ?: 0

    // Create tabs
    urlsToLoad.forEachIndexed { index, url ->
        val tab = BrowserTab(initialUrl = url)
        tabs.add(tab)
        var wv: android.webkit.WebView? = null
        wv = createPersistentWebView(
            context = ctx,
            fullScreenContainer = fs,
            desktopMode = desktopMode,
            isIncognito = isIncognito,
            darkModeEnabled = darkModeEnabled,
            onPageStarted = { u ->
                onPageStarted(tab, u)
            },
            onPageFinished = { u ->
                onPageFinished(tab, u, wv)
            },
            onProgressChanged = { p ->
                onProgressChanged(tab, p)
            },
            onTitleChanged = { t ->
                onTitleChanged(tab, t)
            },
            onUrlChanged = { u ->
                onUrlChanged(tab, u)
            },
            onFullScreenEnter = onFullScreenEnter,
            onFullScreenExit = onFullScreenExit
        )
        tab.webView = wv
        wv.visibility = View.INVISIBLE
        root.addView(wv, 0)
        wv.loadUrl(url)
        onTabCreated(tab, index, restoreIdx)
    }

    root.addView(fs)
    return root
}

// ============================================================
// FAB ITEMS BUILDER
// ============================================================

/**
 * Builds the complete list of FAB menu items.
 * Includes all toggles and actions.
 */
@Composable
private fun buildFabItems(
    viewModel: BrowserViewModel,
    context: android.content.Context,
    activeTab: BrowserTab?,
    tabs: List<BrowserTab>,
    isIncognito: Boolean,
    desktopMode: Boolean,
    adBlockEnabled: Boolean,
    bgAudioEnabled: Boolean,
    shortsBlockerEnabled: Boolean,
    pinchZoomEnabled: Boolean,
    manualZoomEnabled: Boolean,
    repeatEnabled: Boolean,
    darkModeEnabled: Boolean,
    onShowTabSwitcher: () -> Unit,
    onNewTab: () -> Unit,
    onGoHome: () -> Unit,
    onShowUrlBar: () -> Unit,
    onShowFindInPage: () -> Unit
): List<FabItem> {
    return listOf(
        // 1. Tabs
        FabItem(
            icon = Icons.Default.Tab,
            label = "Tabs (${tabs.size})",
            activeColor = Color(0xFF4FC3F7),
            inactiveColor = Color(0xFF4FC3F7),
            onClick = onShowTabSwitcher
        ),
        // 2. New Tab
        FabItem(
            icon = Icons.Default.AddBox,
            label = "New Tab",
            activeColor = Color(0xFF81C784),
            inactiveColor = Color(0xFF81C784),
            onClick = onNewTab
        ),
        // 3. Home
        FabItem(
            icon = Icons.Default.Home,
            label = "Home",
            activeColor = Color(0xFF4FC3F7),
            inactiveColor = Color(0xFF4FC3F7),
            onClick = onGoHome
        ),
        // 4. Search / URL
        FabItem(
            icon = Icons.Default.Search,
            label = "Search / URL",
            activeColor = Color.White,
            inactiveColor = Color.White,
            onClick = onShowUrlBar
        ),
        // 5. Back
        FabItem(
            icon = Icons.Default.ArrowBack,
            label = "Back",
            inactiveColor =
                if (activeTab?.canGoBack == true)
                    Color.White
                else Color(0xFF444444),
            onClick = {
                activeTab?.webView?.goBack()
            }
        ),
        // 6. Forward
        FabItem(
            icon = Icons.Default.ArrowForward,
            label = "Forward",
            inactiveColor =
                if (activeTab?.canGoForward == true)
                    Color.White
                else Color(0xFF444444),
            onClick = {
                activeTab?.webView?.goForward()
            }
        ),
        // 7. Refresh / Stop
        FabItem(
            icon = if (activeTab?.isLoading == true)
                Icons.Default.Close
            else Icons.Default.Refresh,
            label = if (activeTab?.isLoading == true)
                "Stop" else "Refresh",
            activeColor = Color.White,
            inactiveColor = Color.White,
            onClick = {
                if (activeTab?.isLoading == true) {
                    activeTab.webView?.stopLoading()
                } else {
                    activeTab?.webView?.reload()
                }
            }
        ),
        // 8. Find in Page
        FabItem(
            icon = Icons.Default.FindInPage,
            label = "Find in Page",
            activeColor = Color(0xFF4FC3F7),
            inactiveColor = Color(0xFF4FC3F7),
            onClick = onShowFindInPage
        ),
        // 9. Pinch Zoom
        FabItem(
            icon = Icons.Default.Gesture,
            label = if (pinchZoomEnabled)
                "Pinch Zoom: ON"
            else "Pinch Zoom: OFF",
            isToggle = true,
            isOn = pinchZoomEnabled,
            activeColor = Color(0xFF9C27B0),
            onClick = {
                viewModel.togglePinchZoom()
                Toast.makeText(
                    context,
                    if (!pinchZoomEnabled)
                        "Pinch Zoom ON"
                    else "Pinch Zoom OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        ),
        // 10. Manual Zoom
        FabItem(
            icon = Icons.Default.ZoomIn,
            label = if (manualZoomEnabled)
                "Btn Zoom: ON"
            else "Btn Zoom: OFF",
            isToggle = true,
            isOn = manualZoomEnabled,
            activeColor = Color(0xFF00BCD4),
            onClick = {
                viewModel.toggleManualZoom()
                Toast.makeText(
                    context,
                    if (!manualZoomEnabled)
                        "+/- Zoom ON"
                    else "+/- Zoom OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        ),
        // 11. Shorts Blocker
        FabItem(
            icon = Icons.Default.ShortText,
            label = if (shortsBlockerEnabled)
                "Shorts: Blocked"
            else "Shorts: Show",
            isToggle = true,
            isOn = shortsBlockerEnabled,
            activeColor = Color(0xFFE91E63),
            onClick = {
                viewModel.toggleShortsBlocker()
                activeTab?.webView?.reload()
                Toast.makeText(
                    context,
                    if (!shortsBlockerEnabled)
                        "Shorts Blocked"
                    else "Shorts Enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        ),
        // 12. Ad Blocker
        FabItem(
            icon = Icons.Default.Shield,
            label = if (adBlockEnabled)
                "Ads: Blocked"
            else "Ads: Show",
            isToggle = true,
            isOn = adBlockEnabled,
            activeColor = Color(0xFF4CAF50),
            onClick = {
                viewModel.toggleAdBlock()
                activeTab?.webView?.reload()
            }
        ),
        // 13. Repeat
        FabItem(
            icon = Icons.Default.Repeat,
            label = if (repeatEnabled)
                "Repeat: ON"
            else "Repeat: OFF",
            isToggle = true,
            isOn = repeatEnabled,
            activeColor = Color(0xFF00BCD4),
            onClick = {
                viewModel.toggleRepeat()
                Toast.makeText(
                    context,
                    if (WebViewHolder.isRepeatEnabled)
                        "🔁 Repeat ON"
                    else "Repeat OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        ),
        // 14. BG Audio
        FabItem(
            icon = Icons.Default.MusicNote,
            label = if (bgAudioEnabled)
                "BG Audio: ON"
            else "BG Audio: OFF",
            isToggle = true,
            isOn = bgAudioEnabled,
            activeColor = Color(0xFF4FC3F7),
            onClick = {
                viewModel.toggleBgAudio()
            }
        ),
        // 15. Dark Mode
        FabItem(
            icon = Icons.Default.DarkMode,
            label = if (darkModeEnabled)
                "Dark Web: ON"
            else "Dark Web: OFF",
            isToggle = true,
            isOn = darkModeEnabled,
            activeColor = Color(0xFF7C4DFF),
            onClick = {
                val newState = !darkModeEnabled
                viewModel.toggleDarkMode()
                activeTab?.webView?.let { wv ->
                    WebDarkMode.apply(wv, newState)
                }
            }
        ),
        // 16. Desktop Mode
        FabItem(
            icon = Icons.Default.Computer,
            label = if (desktopMode)
                "Desktop: ON"
            else "Desktop: OFF",
            isToggle = true,
            isOn = desktopMode,
            activeColor = Color(0xFFFF9800),
            onClick = {
                viewModel.toggleDesktopMode()
                val ua = if (!desktopMode) {
                    "Mozilla/5.0 (Macintosh; " +
                            "Intel Mac OS X 10_15_7) " +
                            "AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 " +
                            "Safari/537.36"
                } else null
                activeTab?.webView
                    ?.settings
                    ?.userAgentString = ua
                activeTab?.webView?.reload()
            }
        ),
        // 17. Bookmark
        FabItem(
            icon = Icons.Default.BookmarkAdd,
            label = "Bookmark",
            activeColor = Color(0xFFFFB74D),
            inactiveColor = Color(0xFFFFB74D),
            onClick = {
                viewModel.addBookmark(
                    activeTab?.title ?: "",
                    activeTab?.url ?: ""
                )
                Toast.makeText(
                    context,
                    "📌 Bookmarked!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        ),
        // 18. Share
        FabItem(
            icon = Icons.Default.Share,
            label = "Share",
            activeColor = Color.White,
            inactiveColor = Color.White,
            onClick = {
                val shareIntent =
                    android.content.Intent().apply {
                        action = android.content
                            .Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(
                            android.content
                                .Intent.EXTRA_TEXT,
                            activeTab?.url ?: ""
                        )
                    }
                context.startActivity(
                    android.content.Intent
                        .createChooser(
                            shareIntent, "Share"
                        )
                )
            }
        ),
        // 19. Clear Cache
        FabItem(
            icon = Icons.Default.CleaningServices,
            label = "Clear Cache",
            activeColor = Color(0xFFE57373),
            inactiveColor = Color(0xFFE57373),
            onClick = {
                CacheManager.clearWebViewCache(
                    context
                )
                CacheManager.clearExpiredCookies()
                Toast.makeText(
                    context,
                    "🧹 Cleared!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        ),
    )
}

// ============================================================
// HELPER FUNCTIONS
// ============================================================

/** Build JavaScript to exit fullscreen. */
private fun buildExitFullscreenJs(): String {
    return buildString {
        append("document.exitFullscreen")
        append("?document.exitFullscreen():")
        append("document.webkitExitFullscreen")
        append("?document.webkitExitFullscreen():")
        append("null")
    }
}