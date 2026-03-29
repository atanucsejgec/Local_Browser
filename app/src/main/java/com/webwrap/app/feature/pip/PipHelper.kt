package com.webwrap.app.feature.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational

/**
 * Handles Picture-in-Picture mode for video.
 * Allows video to continue in a floating window.
 */
object PipHelper {

    /**
     * Check if device supports PiP mode.
     */
    fun isPipSupported(activity: Activity): Boolean {
        return Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O &&
                activity.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_PICTURE_IN_PICTURE
                )
    }

    /**
     * Enter PiP mode with 16:9 aspect ratio.
     * Call from onUserLeaveHint or manually.
     *
     * @param activity The current activity
     * @param hasVideo Whether video is playing
     * @return true if PiP was entered
     */
    fun enterPipMode(
        activity: Activity,
        hasVideo: Boolean = true
    ): Boolean {
        if (!isPipSupported(activity)) return false
        if (!hasVideo) return false

        return try {
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                val params = buildPipParams()
                activity.enterPictureInPictureMode(params)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Build PiP parameters with 16:9 aspect ratio.
     */
    private fun buildPipParams():
            PictureInPictureParams
    {
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            return PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
        }
        throw UnsupportedOperationException(
            "PiP requires API 26+"
        )
    }

    /**
     * Update PiP params (e.g., after rotation).
     */
    fun updatePipParams(
        activity: Activity,
        width: Int = 16,
        height: Int = 9
    ) {
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            try {
                val params = PictureInPictureParams
                    .Builder()
                    .setAspectRatio(Rational(width, height))
                    .build()
                activity.setPictureInPictureParams(params)
            } catch (_: Exception) {
                // Ignore
            }
        }
    }
}