package com.webwrap.app

import android.Manifest
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
import androidx.navigation.compose.rememberNavController
import com.webwrap.app.data.CacheManager
import com.webwrap.app.data.CookieHelper
import com.webwrap.app.feature.pip.PipHelper
import com.webwrap.app.navigation.AppNavGraph
import com.webwrap.app.service.BackgroundAudioService
import com.webwrap.app.webview.WebViewHolder

/**
 * Main entry point of the app.
 * Handles system bars, permissions, PiP,
 * and hosts the navigation graph.
 */
class MainActivity : ComponentActivity() {

    // Notification permission launcher
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Notification needed for bg audio",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDisplayCutout()
        showSystemBarsNormal()
        initCookies()
        requestNotifPermission()
        performCleanup()

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
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
            }
        }
    }

    // ===== COOKIES INIT =====

    /** Initialize persistent cookies. */
    private fun initCookies() {
        try {
            CookieHelper.setupPersistentCookies(this)
        } catch (_: Exception) { }
    }

    // ===== PERMISSIONS =====

    /** Request notification permission for API 33+. */
    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            val granted = ContextCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    // ===== CLEANUP =====

    /** Perform startup cache cleanup. */
    private fun performCleanup() {
        try {
            CacheManager.performStartupCleanup(this)
            CacheManager.clearExpiredCookies()
        } catch (_: Exception) { }
    }

    // ===== DISPLAY CUTOUT =====

    /** Handle display cutout for edge-to-edge. */
    private fun handleDisplayCutout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams
                    .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams
                    .LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    // ===== SYSTEM BARS =====

    /** Show system bars in normal mode. */
    fun showSystemBarsNormal() {
        WindowCompat.setDecorFitsSystemWindows(
            window, false
        )
        val controller = WindowCompat
            .getInsetsController(window, window.decorView)
        controller.show(
            WindowInsetsCompat.Type.systemBars()
        )
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        window.statusBarColor =
            android.graphics.Color.TRANSPARENT
        window.navigationBarColor =
            android.graphics.Color.parseColor("#121218")
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    /** Hide all system bars for fullscreen. */
    fun hideAllSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(
            window, false
        )
        val controller = WindowCompat
            .getInsetsController(window, window.decorView)
        controller.hide(
            WindowInsetsCompat.Type.systemBars()
        )
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /** Set landscape orientation + hide bars. */
    fun setLandscape() {
        requestedOrientation = android.content.pm
            .ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideAllSystemBars()
    }

    /** Set portrait orientation + show bars. */
    fun setPortrait() {
        requestedOrientation = android.content.pm
            .ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        showSystemBarsNormal()
    }

    // ===== PICTURE IN PICTURE =====

    /**
     * Enter PiP when user leaves the app
     * (e.g., presses home while video plays).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (WebViewHolder.isFullScreen ||
            hasActiveVideo()
        ) {
            PipHelper.enterPipMode(this)
        }
    }

    /**
     * Handle PiP mode configuration changes.
     */
    override fun onPictureInPictureModeChanged(
        isInPip: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(
            isInPip, newConfig
        )
        if (isInPip) {
            hideAllSystemBars()
        } else {
            if (!WebViewHolder.isFullScreen) {
                showSystemBarsNormal()
            }
        }
    }

    /** Check if there's an active video playing. */
    private fun hasActiveVideo(): Boolean {
        return WebViewHolder.backgroundAudioEnabled &&
                !WebViewHolder.isManuallyPaused
    }

    // ===== LIFECYCLE =====

    override fun onPause() {
        super.onPause()
        CookieHelper.saveCookies()
    }

    override fun onStop() {
        super.onStop()
        CookieHelper.saveCookies()
        if (WebViewHolder.backgroundAudioEnabled) {
            WebViewHolder.forcePlay()
            WebViewHolder.activeWebView?.postDelayed(
                { WebViewHolder.forcePlay() }, 500
            )
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