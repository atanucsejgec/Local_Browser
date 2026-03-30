package com.webwrap.app.service

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.webkit.WebView

/**
 * PipHelper — Handles Picture-in-Picture mode with video-only display.
 * Injects CSS/JS to show only the video element before entering PiP.
 * Restores full page when exiting PiP.
 * Forces video playback to continue during PiP transition.
 */
object PipHelper {

    /** Check if PiP is supported */
    fun isPipSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /**
     * Enter PiP mode — injects video-only CSS first, then enters PiP.
     * The CSS hides everything except the <video> element.
     */
    fun enterPipMode(activity: Activity, webView: WebView? = null): Boolean {
        if (!isPipSupported()) return false
        return try {
            // Inject video-only CSS before entering PiP
            webView?.evaluateJavascript(getVideoOnlyJs(), null)
            // Re-inject visibility spoof to prevent pause
            webView?.evaluateJavascript(getVisibilitySpoofJs(), null)
            // Force play
            webView?.evaluateJavascript(getForcePlayJs(), null)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity.enterPictureInPictureMode(params)
                true
            } else false
        } catch (e: Exception) { false }
    }

    /**
     * Called when PiP mode changes.
     * On enter: force play video.
     * On exit: restore full page.
     */
    fun onPipModeChanged(isInPip: Boolean, webView: WebView?) {
        if (isInPip) {
            // Ensure video keeps playing in PiP
            webView?.evaluateJavascript(getForcePlayJs(), null)
        } else {
            // Restore full page when exiting PiP
            webView?.evaluateJavascript(getRestorePageJs(), null)
        }
    }

    /**
     * CSS/JS to show ONLY the video element.
     * Hides all page content (headers, sidebars, comments, etc.).
     * Makes video fill entire viewport with black background.
     * Works for YouTube, Vimeo, and generic video sites.
     */
    fun getVideoOnlyJs(): String = """
    (function() {
        if (document.getElementById('webwrap-pip')) return;
        var s = document.createElement('style');
        s.id = 'webwrap-pip';
        s.innerHTML = `
            /* Hide everything first */
            body > * { visibility: hidden !important; height: 0 !important; overflow: hidden !important; }
            body { margin: 0 !important; padding: 0 !important; background: black !important; overflow: hidden !important; }
            
            /* YouTube specific — show only player */
            #player, #movie_player, .html5-video-player,
            #player-theater-container, #player-container,
            ytd-player, ytm-player, .player-container {
                visibility: visible !important;
                position: fixed !important; top: 0 !important; left: 0 !important;
                width: 100vw !important; height: 100vh !important;
                max-width: none !important; max-height: none !important;
                z-index: 2147483647 !important; background: black !important;
            }
            
            /* Video element itself */
            video {
                visibility: visible !important;
                position: fixed !important; top: 0 !important; left: 0 !important;
                width: 100vw !important; height: 100vh !important;
                object-fit: contain !important; z-index: 2147483647 !important;
                background: black !important;
            }
            
            /* Hide YouTube UI overlays in PiP */
            .ytp-chrome-top, .ytp-chrome-bottom, .ytp-gradient-top,
            .ytp-gradient-bottom, .ytp-watermark, .ytp-pause-overlay,
            .ytp-show-cards-title, .ytp-ce-element, .ytp-endscreen-content,
            .ytp-storyboard-framepreview, .iv-branding,
            .ytp-cards-teaser, .ytp-cards-button,
            #masthead-container, ytd-masthead, #guide,
            #below, #secondary, #chat, #comments,
            ytm-mobile-topbar-renderer, .mobile-topbar-header-content,
            .watch-below-the-player { 
                display: none !important; visibility: hidden !important; 
            }
        `;
        document.head.appendChild(s);
    })();
    """.trimIndent()

    /** Restore full page — remove PiP CSS */
    fun getRestorePageJs(): String = """
    (function() {
        var s = document.getElementById('webwrap-pip');
        if (s) s.remove();
    })();
    """.trimIndent()

    /** Spoof document visibility to prevent YouTube pausing */
    private fun getVisibilitySpoofJs(): String = """
    (function() {
        Object.defineProperty(document, 'hidden', {value:false, writable:false, configurable:true});
        Object.defineProperty(document, 'visibilityState', {value:'visible', writable:false, configurable:true});
        document.addEventListener('visibilitychange', function(e){ e.stopImmediatePropagation(); }, true);
    })();
    """.trimIndent()

    /** Force all video/audio to play */
    private fun getForcePlayJs(): String = """
    (function() {
        var v = document.querySelector('video');
        if (v && v.paused) { v.play().catch(function(){}); }
        var a = document.querySelector('audio');
        if (a && a.paused) { a.play().catch(function(){}); }
    })();
    """.trimIndent()
}