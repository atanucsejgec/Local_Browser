package com.webwrap.app.service

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational

/**
 * PipHelper — Handles Picture-in-Picture mode entry/exit.
 * Enters PiP when user presses home with video playing.
 * Requires API 26+ (Android 8.0).
 */
object PipHelper {

    /** Check if PiP is supported on this device */
    fun isPipSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /**
     * Enter PiP mode with 16:9 aspect ratio.
     * Called from Activity.onUserLeaveHint when PiP is enabled.
     */
    fun enterPipMode(activity: Activity): Boolean {
        if (!isPipSupported()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity.enterPictureInPictureMode(params)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Update PiP params (e.g., when video aspect ratio changes).
     * Can be called while already in PiP.
     */
    fun updatePipParams(activity: Activity, widthRatio: Int = 16, heightRatio: Int = 9) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(widthRatio, heightRatio))
                    .build()
                activity.setPictureInPictureParams(params)
            } catch (_: Exception) { }
        }
    }
}