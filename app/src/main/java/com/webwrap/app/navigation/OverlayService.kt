package com.webwrap.app.navigation

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import com.webwrap.app.MainActivity
import android.os.Handler
import android.os.Looper



/**
 * OverlayService — Creates a floating browser window on top of other apps.
 * Uses WindowManager with TYPE_APPLICATION_OVERLAY.
 * Draggable, resizable mini WebView.
 */
class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "webwrap_overlay_channel"
        private const val NOTIF_ID = 201
        private var currentUrl: String = "https://www.google.com"

        /** Check if overlay permission is granted */
        fun hasOverlayPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        /** Request overlay permission — opens system settings */
        fun requestOverlayPermission(activity: Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
            }
        }

        /** Start overlay with given URL */
        fun start(context: Context, url: String) {
            currentUrl = url
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
        }

        /** Stop overlay service */
        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var miniWebView: WebView? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        createOverlayWindow()
    }

    /** Build the floating window with header bar + WebView */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createOverlayWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Window layout params
        val params = WindowManager.LayoutParams(
            dpToPx(320), dpToPx(480),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50; y = 200
        }

        // Root container
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
        }

        // Header bar with drag + close
        val header = createHeaderBar(params, root)
        root.addView(header)

        // Mini WebView
        miniWebView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            loadUrl(currentUrl)
        }
        root.addView(miniWebView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        overlayView = root
        windowManager?.addView(root, params)
    }

    /** Create draggable header bar with close button */
    /** Create draggable header bar with resize and close buttons */
    @SuppressLint("ClickableViewAccessibility")
    private fun createHeaderBar(
        params: WindowManager.LayoutParams,
        root: View
    ): LinearLayout {
        val resizeHandler = Handler(Looper.getMainLooper())
        // Container for resize buttons — hidden by default
        val resizeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }

        /** Auto-hide resize buttons after 5 seconds */
        val hideRunnable = Runnable { resizeContainer.visibility = View.GONE }
        fun showResizeButtons() {
            resizeContainer.visibility = View.VISIBLE
            resizeHandler.removeCallbacks(hideRunnable)
            resizeHandler.postDelayed(hideRunnable, 5000)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.parseColor("#121218"))
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            gravity = Gravity.CENTER_VERTICAL

            // Drag handle
            var initX = 0; var initY = 0; var initTouchX = 0f; var initTouchY = 0f
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = params.x; initY = params.y
                        initTouchX = event.rawX; initTouchY = event.rawY
                        showResizeButtons()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initX + (event.rawX - initTouchX).toInt()
                        params.y = initY + (event.rawY - initTouchY).toInt()
                        windowManager?.updateViewLayout(root, params)
                        true
                    }
                    else -> false
                }
            }

            // Shrink button (-)
            val shrinkBtn = ImageButton(this@OverlayService).apply {
                setImageResource(android.R.drawable.btn_minus)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    showResizeButtons()
                    params.width = (params.width * 0.85f).toInt().coerceAtLeast(dpToPx(200))
                    params.height = (params.height * 0.85f).toInt().coerceAtLeast(dpToPx(250))
                    windowManager?.updateViewLayout(root, params)
                }
            }
            // Grow button (+)
            val growBtn = ImageButton(this@OverlayService).apply {
                setImageResource(android.R.drawable.btn_plus)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    showResizeButtons()
                    val dm = resources.displayMetrics
                    params.width = (params.width * 1.15f).toInt().coerceAtMost(dm.widthPixels)
                    params.height = (params.height * 1.15f).toInt().coerceAtMost(dm.heightPixels)
                    windowManager?.updateViewLayout(root, params)
                }
            }

            resizeContainer.addView(shrinkBtn, LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)))
            resizeContainer.addView(growBtn, LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)))

            addView(resizeContainer)

            // Spacer
            addView(View(this@OverlayService), LinearLayout.LayoutParams(0, 1, 1f))

            // Close button
            val closeBtn = ImageButton(this@OverlayService).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener { stop(this@OverlayService) }
            }
            addView(closeBtn, LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)))
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        miniWebView?.destroy()
        overlayView?.let { windowManager?.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Browser")
            .setContentText("Tap to open app")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            ))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Floating Window",
                NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(ch)
        }
    }
}