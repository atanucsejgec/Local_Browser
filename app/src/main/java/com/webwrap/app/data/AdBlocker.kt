package com.webwrap.app.data

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {

    // ══════════════════════════════════════════════════════
    // Ad domains — CAREFULLY filtered to NOT break YouTube
    // ══════════════════════════════════════════════════════
    private val AD_DOMAINS = setOf(
        // Google Ads (NOT googlevideo.com — that's actual video!)
        "googlesyndication.com",
        "googleadservices.com",
        "doubleclick.net",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "ad.doubleclick.net",
        "tpc.googlesyndication.com",

        // Facebook Ads
        "an.facebook.com",

        // Common Ad Networks
        "adnxs.com",
        "adsrvr.org",
        "adform.net",
        "advertising.com",
        "ads-twitter.com",
        "amazon-adsystem.com",
        "media.net",
        "outbrain.com",
        "taboola.com",
        "criteo.com",
        "criteo.net",
        "pubmatic.com",
        "openx.net",
        "rubiconproject.com",
        "casalemedia.com",
        "smartadserver.com",
        "moatads.com",
        "scorecardresearch.com",
        "quantserve.com",
        "bluekai.com",
        "exelator.com",
        "turn.com",
        "mathtag.com",
        "serving-sys.com",
        "bidswitch.net",

        // Trackers
        "hotjar.com",
        "mixpanel.com",
        "amplitude.com",

        // Pop-ups
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "admaven.com",
        "adsterra.com",
        "hilltopads.net",
        "exoclick.com",
        "juicyads.com",
        "trafficjunky.com",
        "clickadu.com",

        // Widgets
        "widgets.outbrain.com",
        "cdn.taboola.com",
    )

    // ═══════════════════════════════════════════════════
    // ✅ WHITELIST — Never block these (breaks sites)
    // ═══════════════════════════════════════════════════
    private val WHITELIST = setOf(
        "googlevideo.com",           // YouTube actual video streams
        "youtube.com/videoplayback", // YouTube video playback
        "youtube.com/watch",         // YouTube watch page
        "youtube.com/embed",         // Embedded videos
        "ytimg.com",                 // YouTube thumbnails
        "yt3.ggpht.com",            // YouTube channel icons
        "googleapis.com",           // Google APIs (needed for login)
        "gstatic.com",              // Google static files
        "accounts.google.com",      // Google login
        "facebook.com/login",       // Facebook login
        "instagram.com/accounts",   // Instagram login
        "youtube.com/youtubei",     // YouTube API
        "youtube.com/s/player",     // YouTube player
        "youtube.com/iframe_api",   // YouTube iframe
        "i.ytimg.com",              // YouTube images
        "s.ytimg.com",              // YouTube static
        "jnn-pa.googleapis.com",    // YouTube playback
        "play.google.com",          // Google Play
    )

    /**
     * Check if a URL should be blocked
     * ✅ Fixed: Checks whitelist FIRST to never break video playback
     */
    fun shouldBlock(url: String): Boolean {
        val lowerUrl = url.lowercase()

        // ✅ NEVER block whitelisted domains
        for (safe in WHITELIST) {
            if (lowerUrl.contains(safe)) {
                return false
            }
        }

        // Check against ad domains
        for (domain in AD_DOMAINS) {
            if (lowerUrl.contains(domain)) {
                return true
            }
        }

        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream("".toByteArray())
        )
    }

    // ═══════════════════════════════════════════════════
    // CSS to hide ad elements (visual blocking only)
    // ✅ Fixed: Won't break video player
    // ═══════════════════════════════════════════════════
    fun getAdBlockCss(): String = """
        .adsbygoogle,
        ins.adsbygoogle,
        [id^="google_ads_"],
        .ad-container,
        .ad-wrapper,
        .ad-banner,
        .advertisement,
        [data-ad],
        [data-ad-slot],
        .ytp-ad-overlay-container,
        .ytp-ad-text-overlay,
        .ytd-promoted-sparkles-web-renderer,
        .ytd-display-ad-renderer,
        .ytd-companion-slot-renderer,
        .ytd-action-companion-ad-renderer,
        .ytd-in-feed-ad-layout-renderer,
        .ytd-banner-promo-renderer,
        .ytd-statement-banner-renderer,
        .ytd-ad-slot-renderer,
        ytd-promoted-video-renderer,
        .ytd-mealbar-promo-renderer,
        #masthead-ad,
        #player-ads,
        .OUTBRAIN,
        .taboola-widget,
        [id^="taboola-"],
        [class*="taboola"],
        [class*="outbrain"],
        .popup-ad,
        .overlay-ad
        {
            display: none !important;
            height: 0 !important;
            max-height: 0 !important;
            overflow: hidden !important;
        }
    """.trimIndent()

    // ═══════════════════════════════════════════════════
    // YouTube ad skip JS
    // ✅ Fixed: Only skips actual ads, not regular videos
    // ═══════════════════════════════════════════════════
    fun getYouTubeAdSkipJs(): String = """
        (function() {
            'use strict';
            function skipAd() {
                // Only act when ad is showing
                var adShowing = document.querySelector('.ad-showing');
                if (!adShowing) return;
                
                // Click skip button
                var skipBtn = document.querySelector(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button'
                );
                if (skipBtn) { skipBtn.click(); return; }
                
                // Skip unskippable ads
                var video = document.querySelector('video');
                if (video && adShowing) {
                    video.currentTime = video.duration || 9999;
                }
                
                // Remove overlay ads
                var overlays = document.querySelectorAll(
                    '.ytp-ad-overlay-container, .ytp-ad-text-overlay'
                );
                overlays.forEach(function(el) { el.remove(); });
            }
            setInterval(skipAd, 1000);
        })();
    """.trimIndent()

    fun getGeneralAdBlockJs(): String = """
        (function() {
            'use strict';
            // Block popup windows
            var origOpen = window.open;
            window.open = function(url) {
                if (url && (url.includes('ad') || url.includes('popup') || url.includes('click'))) {
                    return null;
                }
                return origOpen.apply(this, arguments);
            };
            
            // Remove ad iframes
            var iframes = document.querySelectorAll('iframe');
            iframes.forEach(function(iframe) {
                var src = iframe.src || '';
                if (src.includes('doubleclick') || 
                    src.includes('googlesyndication') ||
                    src.includes('amazon-adsystem')) {
                    iframe.remove();
                }
            });
        })();
    """.trimIndent()
}