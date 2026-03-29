package com.webwrap.app

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets as ComposeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.webwrap.app.data.*
import com.webwrap.app.service.BackgroundAudioService
import com.webwrap.app.webview.WebViewHolder

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification needed for background audio", Toast.LENGTH_LONG).show()
        }
    }

    private var startScreen: Screen = Screen.Home

    var currentTabUrls: List<SavedTab> = emptyList()
    var currentActiveIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ═══════════════════════════════════════════════
        // ✅ DISPLAY CUTOUT HANDLING
        //    Makes content render into cutout area properly
        // ═══════════════════════════════════════════════
        handleDisplayCutout()

        // Show system bars normally on startup
        showSystemBarsNormal()

        try { CookieHelper.setupPersistentCookies(this) } catch (_: Exception) { }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        try {
            CacheManager.performStartupCleanup(this)
            CacheManager.clearExpiredCookies()
        } catch (_: Exception) { }

        val savedSession = SessionManager.loadSession(this)
        if (savedSession != null && savedSession.tabs.isNotEmpty()) {
            val activeIndex = savedSession.activeTabIndex.coerceIn(0, savedSession.tabs.size - 1)
            startScreen = Screen.Browser(
                url = savedSession.tabs[activeIndex].url,
                session = savedSession
            )
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF4FC3F7),
                    secondary = Color(0xFF81C784),
                    surface = Color(0xFF1E1E2E),
                    background = Color(0xFF121218),
                    onSurface = Color.White,
                    onBackground = Color.White,
                )
            ) {
                WebWrapApp()
            }
        }
    }

    // ═══════════════════════════════════════════════
    // ✅ HANDLE DISPLAY CUTOUT (Pixel, Samsung, etc.)
    // ═══════════════════════════════════════════════
    private fun handleDisplayCutout() {
        // Allow content to render behind cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    // ═══════════════════════════════════════════════
    // ✅ SHOW SYSTEM BARS — Normal portrait mode
    //    Respects cutout/notch area
    // ═══════════════════════════════════════════════
    fun showSystemBarsNormal() {
        // ✅ Let system handle insets properly
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT

        // ✅ Make status bar transparent so content shows correctly behind it
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.parseColor("#121218")

        // ✅ Set light/dark status bar icons
        controller.isAppearanceLightStatusBars = false  // white icons on dark bg
        controller.isAppearanceLightNavigationBars = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        }
    }

    // ═══════════════════════════════════════════════
    // ✅ HIDE ALL — Fullscreen/Landscape only
    // ═══════════════════════════════════════════════
    fun hideAllSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    fun setLandscape() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideAllSystemBars()
    }

    fun setPortrait() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        showSystemBarsNormal()
    }

    override fun onPause() {
        super.onPause()
        CookieHelper.saveCookies()
        saveCurrentSession()
    }

    override fun onStop() {
        super.onStop()
        CookieHelper.saveCookies()
        saveCurrentSession()
        if (WebViewHolder.backgroundAudioEnabled) {
            WebViewHolder.forcePlay()
            WebViewHolder.activeWebView?.postDelayed({ WebViewHolder.forcePlay() }, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!WebViewHolder.isFullScreen) {
            showSystemBarsNormal()
        }
        WebViewHolder.activeWebView?.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveCurrentSession()
        BackgroundAudioService.stop(this)
        WebViewHolder.activeWebView = null
    }

    private fun saveCurrentSession() {
        try {
            if (currentTabUrls.isNotEmpty()) {
                SessionManager.saveSession(this, currentTabUrls, currentActiveIndex)
            }
        } catch (_: Exception) { }
    }

    @Composable
    fun WebWrapApp() {
        var currentScreen by remember { mutableStateOf(startScreen) }
        var customBookmarks by remember {
            mutableStateOf(BookmarkStorage.loadBookmarks(this@MainActivity))
        }
        var history by remember {
            mutableStateOf(BookmarkStorage.loadHistory(this@MainActivity))
        }

        when (val screen = currentScreen) {
            is Screen.Home -> {
                LaunchedEffect(Unit) { showSystemBarsNormal() }

                HomeScreen(
                    onSiteSelected = { url ->
                        history = history + url
                        BookmarkStorage.saveHistory(this@MainActivity, history)
                        currentScreen = Screen.Browser(url = url, session = null)
                    },
                    customBookmarks = customBookmarks,
                    onAddBookmark = { bookmark ->
                        customBookmarks = customBookmarks + bookmark
                        BookmarkStorage.saveBookmarks(this@MainActivity, customBookmarks)
                        Toast.makeText(this, "✅ Added!", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteBookmark = { bookmark ->
                        customBookmarks = customBookmarks.filter { it.url != bookmark.url }
                        BookmarkStorage.saveBookmarks(this@MainActivity, customBookmarks)
                    },
                    onClearData = {
                        CookieHelper.clearAllCookies()
                        CacheManager.clearWebViewCache(this@MainActivity)
                        SessionManager.clearSession(this@MainActivity)
                        history = emptyList()
                        BookmarkStorage.saveHistory(this@MainActivity, history)
                        Toast.makeText(this, "🗑️ Cleared!", Toast.LENGTH_SHORT).show()
                    },
                    history = history
                )
            }

            is Screen.Browser -> {
                WebViewScreen(
                    initialUrl = screen.url,
                    savedSession = screen.session,
                    onGoHome = {
                        CookieHelper.saveCookies()
                        setPortrait()
                        currentScreen = Screen.Home
                    },
                    onAddToBookmark = { title, url ->
                        val bookmark = SiteBookmark(
                            name = title.take(20).ifEmpty { url.take(20) },
                            url = url, icon = "⭐", color = 0xFFFFB74D
                        )
                        if (customBookmarks.none { it.url == url }) {
                            customBookmarks = customBookmarks + bookmark
                            BookmarkStorage.saveBookmarks(this@MainActivity, customBookmarks)
                            Toast.makeText(this, "⭐ Bookmarked!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSessionUpdate = { tabs, activeIndex ->
                        currentTabUrls = tabs
                        currentActiveIndex = activeIndex
                    }
                )
            }
        }
    }
}

sealed class Screen {
    data object Home : Screen()
    data class Browser(
        val url: String,
        val session: SavedSession? = null
    ) : Screen()
}