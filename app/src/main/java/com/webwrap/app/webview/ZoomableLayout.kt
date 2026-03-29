package com.webwrap.app.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

class ZoomableLayout(context: Context) : FrameLayout(context) {

    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val minZoom = 1.0f
    private val maxZoom = 5.0f

    private val handler = Handler(Looper.getMainLooper())

    // ═══════════════════════════════════════════════
    // ✅ Pinch zoom toggle — controlled from FAB
    // ═══════════════════════════════════════════════
    var pinchZoomEnabled: Boolean
        get() = WebViewHolder.pinchZoomEnabled
        set(_) {}

    private var isScaling = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (!pinchZoomEnabled) return false
                isScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!pinchZoomEnabled) return false

                val previousScale = scaleFactor
                scaleFactor *= detector.scaleFactor
                scaleFactor = max(minZoom, min(maxZoom, scaleFactor))

                val focusX = detector.focusX
                val focusY = detector.focusY
                val scaleChange = scaleFactor / previousScale

                translateX = focusX - (focusX - translateX) * scaleChange
                translateY = focusY - (focusY - translateY) * scaleChange

                clampTranslation()
                applyTransform()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                if (scaleFactor < 1.05f) resetZoom()
            }
        }
    )

    // ═══════════════════════════════════════════════
    // ✅ STEP ZOOM — Called from manual +/- buttons
    // ═══════════════════════════════════════════════
    fun stepZoomIn() {
        val prevScale = scaleFactor
        scaleFactor = min(maxZoom, scaleFactor + 0.10f)

        // Zoom from center
        val centerX = width / 2f
        val centerY = height / 2f
        val scaleChange = scaleFactor / prevScale
        translateX = centerX - (centerX - translateX) * scaleChange
        translateY = centerY - (centerY - translateY) * scaleChange

        clampTranslation()
        applyTransform()
    }

    fun stepZoomOut() {
        val prevScale = scaleFactor
        scaleFactor = max(minZoom, scaleFactor - 0.10f)

        if (scaleFactor < 1.05f) {
            resetZoom()
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val scaleChange = scaleFactor / prevScale
        translateX = centerX - (centerX - translateX) * scaleChange
        translateY = centerY - (centerY - translateY) * scaleChange

        clampTranslation()
        applyTransform()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount

        // ═══════════════════════════════════════
        // ✅ If pinch zoom DISABLED → pass everything to child
        //    No 2x speed issue, no blurring
        // ═══════════════════════════════════════
        if (!pinchZoomEnabled) {
            // Still handle panning if already zoomed (from manual +/- buttons)
            if (scaleFactor > 1.05f && pointerCount == 1) {
                handlePanEvent(event)

                // Pass single taps through for controls
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val duration = System.currentTimeMillis() - touchStartTime
                    if (duration < 200) {
                        super.dispatchTouchEvent(event)
                    }
                }
                return true
            }
            return super.dispatchTouchEvent(event)
        }

        // ═══════════════════════════════════════
        // Pinch zoom ENABLED — handle multi-touch
        // ═══════════════════════════════════════
        if (pointerCount >= 2) {
            scaleDetector.onTouchEvent(event)
            return true
        }

        // Panning when zoomed
        if (scaleFactor > 1.05f) {
            handlePanEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP && !isScaling) {
                val duration = System.currentTimeMillis() - touchStartTime
                if (duration < 200) {
                    super.dispatchTouchEvent(event)
                }
            }
            return true
        }

        return super.dispatchTouchEvent(event)
    }

    private var touchStartTime = 0L

    private fun handlePanEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activePointerId = event.getPointerId(0)
                touchStartTime = System.currentTimeMillis()
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex >= 0) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    translateX += x - lastTouchX
                    translateY += y - lastTouchY
                    clampTranslation()
                    applyTransform()
                    lastTouchX = x
                    lastTouchY = y
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
    }

    private fun clampTranslation() {
        if (scaleFactor <= 1f) {
            translateX = 0f; translateY = 0f; return
        }
        val maxTransX = (width * scaleFactor - width) / 2f
        val maxTransY = (height * scaleFactor - height) / 2f
        translateX = translateX.coerceIn(-maxTransX, maxTransX)
        translateY = translateY.coerceIn(-maxTransY, maxTransY)
    }

    private fun applyTransform() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.pivotX = 0f
            child.pivotY = 0f
            child.scaleX = scaleFactor
            child.scaleY = scaleFactor
            child.translationX = translateX
            child.translationY = translateY
        }
    }

    fun resetZoom() {
        scaleFactor = 1.0f
        translateX = 0f
        translateY = 0f
        applyTransform()
    }

    fun getCurrentZoom(): Float = scaleFactor

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }
}