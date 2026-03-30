package com.webwrap.app.webview

import android.webkit.WebView
import com.webwrap.app.data.MediaInfo
import org.json.JSONObject
import org.json.JSONTokener

/**
 * WebViewHolder — Singleton that holds the active WebView reference
 * and all global browser state: toggles, media controls, zoom, shorts blocker.
 * Acts as bridge between Compose UI and native WebView.
 */
object WebViewHolder {

    // ══════════════════════════════════════════════════════
    // GLOBAL STATE — Shared across all WebViews
    // ══════════════════════════════════════════════════════

    /** Currently active WebView instance */
    var activeWebView: WebView? = null

    /** Background audio keeps playing when app goes to background */
    var backgroundAudioEnabled: Boolean = true

    /** Ad blocker blocks ad network requests and hides ad elements */
    var adBlockEnabled: Boolean = true

    /** Whether video is currently in fullscreen mode */
    var isFullScreen: Boolean = false

    /** Whether user manually paused media (prevents auto-resume) */
    var isManuallyPaused: Boolean = false

    /** YouTube Shorts blocker — redirects /shorts/ to /watch */
    var shortsBlockerEnabled: Boolean = true

    // ── Zoom Controls ───────────────────────────────────
    /** Pinch-to-zoom enabled in fullscreen video */
    var pinchZoomEnabled: Boolean = true

    /** Manual +/- zoom buttons in fullscreen video */
    var manualZoomEnabled: Boolean = true

    /** Reference to fullscreen ZoomableLayout for zoom control */
    var fullscreenZoomable: ZoomableLayout? = null

    // ── Media Control State ─────────────────────────────
    /** Loop/repeat current media when it ends */
    var isRepeatEnabled: Boolean = false

    // ── NEW: Dark Mode State ────────────────────────────
    /** Dark mode CSS injection — re-injected on every page load */
    var darkModeEnabled: Boolean = false

    // ══════════════════════════════════════════════════════
    // ZOOM METHODS — Called from manual +/- buttons
    // ══════════════════════════════════════════════════════

    /** Step zoom in by 0.1x increment */
    fun stepZoomIn() { fullscreenZoomable?.stepZoomIn() }

    /** Step zoom out by 0.1x decrement */
    fun stepZoomOut() { fullscreenZoomable?.stepZoomOut() }

    /** Reset zoom to 1.0x */
    fun resetFullscreenZoom() { fullscreenZoomable?.resetZoom() }

    /** Get current zoom level (1.0 = no zoom) */
    fun getCurrentZoom(): Float = fullscreenZoomable?.getCurrentZoom() ?: 1f

    // ══════════════════════════════════════════════════════
    // FORCE PLAY — Keeps media alive in background
    // ══════════════════════════════════════════════════════

    /** Force all video/audio elements to play (unless manually paused) */
    fun forcePlay() {
        if (isManuallyPaused) return
        activeWebView?.post {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(v) {
                        if (v.paused && !v.ended) { v.play(); }
                    });
                    var audios = document.querySelectorAll('audio');
                    audios.forEach(function(a) {
                        if (a.paused && !a.ended) { a.play(); }
                    });
                })();
                """.trimIndent(), null
            )
        }
    }

    // ══════════════════════════════════════════════════════
    // PAUSE DETECTOR — Tracks user-initiated pause events
    // ══════════════════════════════════════════════════════

    /** Inject JS listeners to detect user-initiated pause/play */
    fun injectPauseDetector() {
        activeWebView?.post {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(v) {
                        if (!v.getAttribute('data-pause-detected')) {
                            v.setAttribute('data-pause-detected', 'true');
                            v.addEventListener('pause', function(e) {
                                if (e.isTrusted) { window._userManuallyPaused = true; }
                            });
                            v.addEventListener('play', function(e) {
                                if (e.isTrusted) { window._userManuallyPaused = false; }
                            });
                        }
                    });
                })();
                """.trimIndent(), null
            )
        }
    }

    /** Check if user manually paused via JS bridge */
    fun checkManualPause() {
        activeWebView?.post {
            activeWebView?.evaluateJavascript(
                "(window._userManuallyPaused || false)"
            ) { result ->
                isManuallyPaused = result == "true"
            }
        }
    }

    // ══════════════════════════════════════════════════════
    // MEDIA CONTROLS — Play / Pause / Next / Previous / Seek / Repeat
    // ══════════════════════════════════════════════════════

    /** Play media and clear manual pause flag */
    fun playMedia() {
        isManuallyPaused = false
        activeWebView?.post {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    var m = document.querySelector('video') || document.querySelector('audio');
                    if (m) { m.play(); }
                    window._userManuallyPaused = false;
                })();
                """.trimIndent(), null
            )
        }
    }

    /** Pause media and set manual pause flag */
    fun pauseMedia() {
        isManuallyPaused = true
        activeWebView?.post {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    var m = document.querySelector('video') || document.querySelector('audio');
                    if (m) { m.pause(); }
                    window._userManuallyPaused = true;
                })();
                """.trimIndent(), null
            )
        }
    }

    /** Skip to next track (YouTube next button or +10s) */
    fun nextTrack() {
        isManuallyPaused = false
        activeWebView?.post {
            activeWebView?.evaluateJavascript(NEXT_TRACK_JS, null)
        }
    }

    /** Go to previous track or restart current */
    fun previousTrack() {
        activeWebView?.post {
            activeWebView?.evaluateJavascript(PREVIOUS_TRACK_JS, null)
        }
    }

    /** Seek to specific position in milliseconds */
    fun seekTo(positionMs: Long) {
        val sec = positionMs / 1000.0
        activeWebView?.post {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    var m = document.querySelector('video') || document.querySelector('audio');
                    if (m) { m.currentTime = $sec; }
                })();
                """.trimIndent(), null
            )
        }
    }

    /** Restart media from beginning */
    fun restartMedia() {
        isManuallyPaused = false
        activeWebView?.post {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    var m = document.querySelector('video') || document.querySelector('audio');
                    if (m) { m.currentTime = 0; m.play(); }
                    window._userManuallyPaused = false;
                })();
                """.trimIndent(), null
            )
        }
    }

    /** Toggle loop/repeat on current media element */
    fun toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled
        activeWebView?.post {
            activeWebView?.evaluateJavascript(
                """
                (function() {
                    var m = document.querySelector('video') || document.querySelector('audio');
                    if (m) { m.loop = $isRepeatEnabled; }
                })();
                """.trimIndent(), null
            )
        }
    }

    // ══════════════════════════════════════════════════════
    // GET MEDIA INFO — Extracts title, artist, thumbnail, duration
    // ══════════════════════════════════════════════════════

    /** Extract current media info from page via JS, returns via callback */
    fun getMediaInfo(callback: (MediaInfo) -> Unit) {
        val webView = activeWebView
        if (webView == null) {
            callback(MediaInfo())
            return
        }
        try {
            webView.post {
                try {
                    webView.evaluateJavascript(GET_MEDIA_INFO_JS) { result ->
                        try {
                            if (result == null || result == "null" || result.isBlank()) {
                                callback(createFallbackInfo(webView))
                                return@evaluateJavascript
                            }
                            val parsed = JSONTokener(result).nextValue()
                            val jsonStr = if (parsed is String) parsed else result
                            val json = JSONObject(jsonStr)

                            if (!json.optBoolean("found", false)) {
                                callback(createFallbackInfo(webView))
                                return@evaluateJavascript
                            }
                            callback(
                                MediaInfo(
                                    title = json.optString("title", "").ifEmpty {
                                        webView.title ?: "WebWrap Audio"
                                    },
                                    artist = json.optString("artist", "").ifEmpty {
                                        extractDomain(webView.url ?: "")
                                    },
                                    thumbnailUrl = json.optString("thumbnail", "").ifEmpty {
                                        extractYouTubeThumbnail(webView.url ?: "")
                                    },
                                    duration = json.optLong("duration", 0),
                                    currentPosition = json.optLong("position", 0),
                                    isPlaying = json.optBoolean("playing", false),
                                    isLooping = json.optBoolean("loop", false)
                                )
                            )
                        } catch (e: Exception) {
                            callback(createFallbackInfo(webView))
                        }
                    }
                } catch (e: Exception) {
                    callback(MediaInfo())
                }
            }
        } catch (e: Exception) {
            callback(MediaInfo())
        }
    }

    /** Create fallback MediaInfo from WebView title/URL */
    private fun createFallbackInfo(webView: WebView): MediaInfo {
        return MediaInfo(
            title = webView.title ?: "WebWrap Audio",
            artist = extractDomain(webView.url ?: ""),
            thumbnailUrl = extractYouTubeThumbnail(webView.url ?: "")
        )
    }

    /** Extract domain name from URL (strips protocol, www, m.) */
    private fun extractDomain(url: String): String {
        return url.removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").removePrefix("m.")
            .split("/").firstOrNull() ?: ""
    }

    /** Extract YouTube video thumbnail URL from watch/embed/short URL */
    private fun extractYouTubeThumbnail(url: String): String {
        val patterns = listOf(
            Regex("""youtube\.com/watch\?v=([a-zA-Z0-9_-]+)"""),
            Regex("""youtu\.be/([a-zA-Z0-9_-]+)"""),
            Regex("""youtube\.com/embed/([a-zA-Z0-9_-]+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return "https://img.youtube.com/vi/${match.groupValues[1]}/hqdefault.jpg"
            }
        }
        return ""
    }

    // ══════════════════════════════════════════════════════
    // JAVASCRIPT CONSTANTS — Next/Previous/MediaInfo
    // ══════════════════════════════════════════════════════

    /** JS to click YouTube next button or skip forward 10s */
    private const val NEXT_TRACK_JS = """
    (function() {
        try {
            var isYT = window.location.hostname.includes('youtube');
            if (isYT) {
                var nextBtn = document.querySelector('.ytp-next-button');
                if (nextBtn && nextBtn.offsetParent !== null) { nextBtn.click(); return; }
                var mNext = document.querySelector('[aria-label="Next"]');
                if (mNext) { mNext.click(); return; }
                var mNext2 = document.querySelector('button.next-button');
                if (mNext2) { mNext2.click(); return; }
            }
            var m = document.querySelector('video') || document.querySelector('audio');
            if (m) {
                var dur = m.duration || 9999;
                m.currentTime = Math.min(dur, m.currentTime + 10);
            }
        } catch(e) {}
    })();
    """

    /** JS to restart or go to previous track */
    private const val PREVIOUS_TRACK_JS = """
    (function() {
        try {
            var m = document.querySelector('video') || document.querySelector('audio');
            if (!m) return;
            if (m.currentTime > 5) {
                m.currentTime = 0;
                if (m.paused) m.play();
                return;
            }
            var isYT = window.location.hostname.includes('youtube');
            if (isYT) {
                var prevBtn = document.querySelector('.ytp-prev-button');
                if (prevBtn && prevBtn.offsetParent !== null) {
                    prevBtn.click();
                    return;
                }
            }
            m.currentTime = 0;
            if (m.paused) m.play();
        } catch(e) {}
    })();
    """

    /** JS to extract comprehensive media info from page */
    private const val GET_MEDIA_INFO_JS = """
    (function() {
        try {
            var media = document.querySelector('video') || document.querySelector('audio');
            if (!media) return JSON.stringify({found:false});
            var title = '';
            var artist = '';
            var thumbnail = '';
            var isYT = window.location.hostname.includes('youtube') || window.location.hostname.includes('youtu.be');
            if (isYT) {
                var titleSels = [
                    'h1.ytd-watch-metadata yt-formatted-string',
                    '#title h1 yt-formatted-string',
                    '.ytp-title-text .ytp-title-link',
                    '.slim-video-information-title',
                    'h2.slim-video-information-title',
                    '#info-strings yt-formatted-string'
                ];
                for (var i = 0; i < titleSels.length; i++) {
                    var el = document.querySelector(titleSels[i]);
                    if (el && el.textContent && el.textContent.trim()) {
                        title = el.textContent.trim();
                        break;
                    }
                }
                var chSels = [
                    '#channel-name a',
                    'ytd-channel-name a',
                    '.slim-owner-channel-name',
                    '#upload-info a',
                    '.ytd-video-owner-renderer a'
                ];
                for (var j = 0; j < chSels.length; j++) {
                    var ch = document.querySelector(chSels[j]);
                    if (ch && ch.textContent && ch.textContent.trim()) {
                        artist = ch.textContent.trim();
                        break;
                    }
                }
                var vidMatch = window.location.href.match(/[?&]v=([a-zA-Z0-9_-]+)/);
                if (!vidMatch) vidMatch = window.location.href.match(/youtu\.be\/([a-zA-Z0-9_-]+)/);
                if (vidMatch) {
                    thumbnail = 'https://img.youtube.com/vi/' + vidMatch[1] + '/hqdefault.jpg';
                }
            } else {
                title = document.title || '';
                var ogImg = document.querySelector('meta[property="og:image"]');
                if (ogImg) thumbnail = ogImg.getAttribute('content') || '';
                if (!thumbnail && media.poster) thumbnail = media.poster;
                var ogSite = document.querySelector('meta[property="og:site_name"]');
                if (ogSite) artist = ogSite.getAttribute('content') || '';
                if (!artist) {
                    var ogArtist = document.querySelector('meta[name="author"]');
                    if (ogArtist) artist = ogArtist.getAttribute('content') || '';
                }
                if (!artist) artist = window.location.hostname.replace('www.','').replace('m.','');
            }
            if (!title) title = document.title || 'Unknown';
            var dur = media.duration;
            if (!dur || !isFinite(dur) || isNaN(dur)) dur = 0;
            var pos = media.currentTime;
            if (!pos || !isFinite(pos) || isNaN(pos)) pos = 0;
            return JSON.stringify({
                found: true,
                title: title,
                artist: artist,
                thumbnail: thumbnail,
                duration: Math.floor(dur * 1000),
                position: Math.floor(pos * 1000),
                playing: !media.paused && !media.ended,
                loop: !!media.loop
            });
        } catch(e) {
            return JSON.stringify({found:false});
        }
    })();
    """

    // ══════════════════════════════════════════════════════
    // SHORTS BLOCKER — URL redirect + CSS/JS hiding
    // ══════════════════════════════════════════════════════

    /** Convert /shorts/ID URL to /watch?v=ID */
    fun convertShortsUrl(url: String): String {
        if (!shortsBlockerEnabled) return url
        val shortsPattern = Regex("""youtube\.com/shorts/([a-zA-Z0-9_-]+)""")
        val match = shortsPattern.find(url)
        if (match != null) {
            val videoId = match.groupValues[1]
            val baseUrl = url.substringBefore("/shorts/")
            return "$baseUrl/watch?v=$videoId"
        }
        return url
    }

    /** JS to remove shorts elements from YouTube DOM */
    fun getShortsBlockerJs(): String = """
    (function() {
        'use strict';
        if (window.__webwrap_shorts_blocker) return;
        window.__webwrap_shorts_blocker = true;
        function removeShorts() {
            var selectors = [
                'ytd-reel-shelf-renderer',
                'ytd-rich-shelf-renderer[is-shorts]',
                'ytm-reel-shelf-renderer',
                '[is-shorts]',
                'ytm-shorts-lockup-view-model',
                'ytm-shorts-lockup-view-model-v2',
                'ytd-rich-section-renderer:has(ytd-rich-shelf-renderer[is-shorts])',
                'ytm-item-section-renderer:has(ytm-reel-shelf-renderer)',
                'ytm-rich-section-renderer:has([is-shorts])'
            ];
            document.querySelectorAll(selectors.join(',')).forEach(function(el) {
                el.style.display = 'none';
                el.style.height = '0';
                el.style.overflow = 'hidden';
            });
            var navSelectors = [
                'ytm-pivot-bar-item-renderer:has([data-tab-identifier="FEshorts"])',
                'a[href="/shorts"]',
                'a[title="Shorts"]',
                'ytd-mini-guide-entry-renderer[aria-label="Shorts"]',
                'ytd-guide-entry-renderer:has(a[title="Shorts"])',
                'ytm-pivot-bar-item-renderer:nth-child(2)'
            ];
            document.querySelectorAll(navSelectors.join(',')).forEach(function(el) {
                el.style.display = 'none';
            });
            document.querySelectorAll(
                'ytd-video-renderer, ytm-video-with-context-renderer, ytm-compact-video-renderer'
            ).forEach(function(el) {
                var links = el.querySelectorAll('a[href*="/shorts/"]');
                if (links.length > 0) el.style.display = 'none';
            });
            document.querySelectorAll('a[href*="/shorts/"]').forEach(function(link) {
                var href = link.getAttribute('href') || '';
                var match = href.match(/\/shorts\/([a-zA-Z0-9_-]+)/);
                if (match) link.setAttribute('href', '/watch?v=' + match[1]);
            });
            document.querySelectorAll('span, yt-formatted-string').forEach(function(el) {
                if (el.textContent.trim() === 'Shorts') {
                    var parent = el.closest(
                        'ytd-guide-entry-renderer, ytm-pivot-bar-item-renderer, ytd-mini-guide-entry-renderer'
                    );
                    if (parent) parent.style.display = 'none';
                }
            });
        }
        removeShorts();
        new MutationObserver(removeShorts).observe(document.body, { childList: true, subtree: true });
        setInterval(removeShorts, 2000);
        document.addEventListener('yt-navigate-finish', removeShorts);
        document.addEventListener('yt-navigate-start', function() { setTimeout(removeShorts, 500); });
    })();
    """.trimIndent()

    /** CSS to hide shorts-related UI elements */
    fun getShortsBlockerCss(): String = """
    ytd-reel-shelf-renderer,
    ytd-rich-shelf-renderer[is-shorts],
    ytm-reel-shelf-renderer,
    [is-shorts],
    ytm-shorts-lockup-view-model,
    ytm-shorts-lockup-view-model-v2,
    ytm-item-section-renderer:has(ytm-reel-shelf-renderer),
    ytd-rich-section-renderer:has([is-shorts]),
    a[title="Shorts"],
    ytd-mini-guide-entry-renderer[aria-label="Shorts"],
    ytd-guide-entry-renderer:has(a[title="Shorts"]),
    ytm-pivot-bar-item-renderer:has([data-tab-identifier="FEshorts"]) {
        display: none !important;
    }
    """.trimIndent()
}