package com.webwrap.app

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.webwrap.app.data.CacheManager
import com.webwrap.app.data.CookieHelper
import com.webwrap.app.service.BackgroundAudioService
import com.webwrap.app.service.PipHelper
import com.webwrap.app.ui.navigation.AppNavigation
import com.webwrap.app.ui.viewmodel.BrowserViewModel
import com.webwrap.app.webview.WebViewHolder

/**
 * MainActivity — App entry point.
 * Handles system bars, PiP callbacks, display cutout, permissions.
 * Navigation is now delegated to AppNavigation (Jetpack Compose Navigation).
 */
class MainActivity : ComponentActivity() {

    // ── Notification Permission Launcher ────────────────
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification needed for background audio", Toast.LENGTH_LONG).show()
        }
    }

    // ══════════════════════════════════════════════════════
    // LIFECYCLE: onCreate
    // ══════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Display cutout handling (Pixel, Samsung notch)
        handleDisplayCutout()
        // Show system bars normally on startup
        showSystemBarsNormal()

        // Setup cookies
        try { CookieHelper.setupPersistentCookies(this) } catch (_: Exception) { }

        // Request notification permission (Android 13+)
        requestNotificationPermissionIfNeeded()

        // Startup cache cleanup
        try {
            CacheManager.performStartupCleanup(this)
            CacheManager.clearExpiredCookies()
        } catch (_: Exception) { }

        // Set Compose content — uses Jetpack Navigation via AppNavigation
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
                AppNavigation()
            }
        }
    }

    // ══════════════════════════════════════════════════════
    // PERMISSION: Notification (Android 13+)
    // ══════════════════════════════════════════════════════
    /** Request POST_NOTIFICATIONS if not already granted */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ══════════════════════════════════════════════════════
    // DISPLAY CUTOUT: Handle notch/punch-hole displays
    // ══════════════════════════════════════════════════════
    /** Allow content to render behind cutout area */
    private fun handleDisplayCutout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    // ══════════════════════════════════════════════════════
    // SYSTEM BARS: Show/Hide for portrait/landscape
    // ══════════════════════════════════════════════════════

    /** Show system bars normally — used in portrait mode */
    fun showSystemBarsNormal() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        // Transparent status bar
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.parseColor("#121218")
        // White icons on dark background
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        }
    }

    /** Hide all system bars — used for fullscreen/landscape video */
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

    // ══════════════════════════════════════════════════════
    // ORIENTATION: Landscape/Portrait helpers
    // ══════════════════════════════════════════════════════

    /** Switch to landscape and hide system bars */
    fun setLandscape() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideAllSystemBars()
    }

    /** Switch to portrait and show system bars */
    fun setPortrait() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        showSystemBarsNormal()
    }

    // ══════════════════════════════════════════════════════
    // PICTURE-IN-PICTURE: Enter PiP when pressing Home
    // ══════════════════════════════════════════════════════

    /** Called when user presses Home — triggers PiP if enabled */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        try {
            val browserVm = ViewModelProvider(this)[BrowserViewModel::class.java]
            if (browserVm.pipEnabled && !browserVm.isInPipMode) {
                PipHelper.enterPipMode(this)
            }
        } catch (_: Exception) { }
    }

    /** Handle PiP mode changes — update ViewModel flag */
    override fun onPictureInPictureModeChanged(
        isInPipMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPipMode, newConfig)
        try {
            val browserVm = ViewModelProvider(this)[BrowserViewModel::class.java]
            browserVm.isInPipMode = isInPipMode
        } catch (_: Exception) { }
    }

    // ══════════════════════════════════════════════════════
    // LIFECYCLE: Pause / Stop / Resume / Destroy
    // ══════════════════════════════════════════════════════

    override fun onPause() {
        super.onPause()
        CookieHelper.saveCookies()
    }

    override fun onStop() {
        super.onStop()
        CookieHelper.saveCookies()
        // Keep audio playing in background
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
        BackgroundAudioService.stop(this)
        WebViewHolder.activeWebView = null
    }
}