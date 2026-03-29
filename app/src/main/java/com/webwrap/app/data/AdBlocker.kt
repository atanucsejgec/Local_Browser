package com.webwrap.app.data

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Ad blocker with domain filtering, URL patterns,
 * CSS hiding, and YouTube ad skipping.
 * Uses EasyList-style pattern matching.
 */
object AdBlocker {

    // ========================================================
    // AD DOMAINS — Known ad/tracking domains
    // ========================================================

    private val AD_DOMAINS = setOf(
        // Google Ads (NOT googlevideo.com)
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

    // ========================================================
    // URL PATH PATTERNS — EasyList style matching
    // ========================================================

    private val AD_URL_PATTERNS = listOf(
        "/ads/",
        "/ad/",
        "/adserver",
        "/adframe",
        "/adclick",
        "/adview",
        "/pagead/",
        "/sponsor",
        "/adsense",
        "/banner/ad",
        "/pop-under",
        "/popunder",
        "doubleclick.net/",
        "/prebid",
        "/adsapi/",
        "tracking.js",
        "analytics.js",
        "tracker.js",
        "/pixel?",
        "/pixel/",
        "beacon.js",
        "adsbygoogle",
        "amazon-adsystem",
        "/ad-manager/",
        "/ad_banner",
        "advert.",
        "/clicktrack",
        "/adlog.",
        "/adserv",
        "popundr.",
        "/adx.",
        "/adsign.",
    )

    // ========================================================
    // WHITELIST — Never block these domains
    // ========================================================

    private val WHITELIST = setOf(
        // YouTube video streams
        "googlevideo.com",
        "youtube.com/videoplayback",
        "youtube.com/watch",
        "youtube.com/embed",
        "ytimg.com",
        "yt3.ggpht.com",

        // Google core services
        "googleapis.com",
        "gstatic.com",
        "accounts.google.com",

        // Social logins
        "facebook.com/login",
        "instagram.com/accounts",

        // YouTube API & player
        "youtube.com/youtubei",
        "youtube.com/s/player",
        "youtube.com/iframe_api",
        "i.ytimg.com",
        "s.ytimg.com",
        "jnn-pa.googleapis.com",

        // Google Play
        "play.google.com",
    )

    // ========================================================
    // SHOULD BLOCK — Main filtering logic
    // ========================================================

    /**
     * Check if a URL should be blocked.
     * Checks whitelist first, then domain list,
     * then URL path patterns.
     *
     * @param url The request URL to check
     * @return true if URL should be blocked
     */
    fun shouldBlock(url: String): Boolean {
        val lowerUrl = url.lowercase()

        // NEVER block whitelisted domains
        for (safe in WHITELIST) {
            if (lowerUrl.contains(safe)) {
                return false
            }
        }

        // Check domain blocklist
        for (domain in AD_DOMAINS) {
            if (lowerUrl.contains(domain)) {
                return true
            }
        }

        // Check URL path patterns (EasyList style)
        for (pattern in AD_URL_PATTERNS) {
            if (lowerUrl.contains(pattern)) {
                return true
            }
        }

        return false
    }

    // ========================================================
    // EMPTY RESPONSE — Returned for blocked URLs
    // ========================================================

    /** Create empty response for blocked requests. */
    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(
                "".toByteArray()
            )
        )
    }

    // ========================================================
    // AD BLOCK CSS — Hide ad elements visually
    // ========================================================

    /** CSS rules to hide common ad elements. */
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
        .overlay-ad,
        .ad-placeholder,
        [data-testid="ad"],
        .sponsored-content,
        [aria-label="advertisement"],
        .ad-unit,
        .ad-slot {
            display: none !important;
            height: 0 !important;
            min-height: 0 !important;
            max-height: 0 !important;
            overflow: hidden !important;
        }
    """.trimIndent()

    // ========================================================
    // YOUTUBE AD SKIP JS
    // ========================================================

    /**
     * JavaScript to auto-skip YouTube video ads.
     * Only acts when ad is actually showing.
     */
    fun getYouTubeAdSkipJs(): String = """
        (function() {
            'use strict';
            function skipAd() {
                var adShowing =
                    document.querySelector(
                        '.ad-showing'
                    );
                if (!adShowing) return;

                var skipBtn =
                    document.querySelector(
                        '.ytp-ad-skip-button, ' +
                        '.ytp-ad-skip-button-modern, ' +
                        '.ytp-skip-ad-button'
                    );
                if (skipBtn) {
                    skipBtn.click();
                    return;
                }

                var video =
                    document.querySelector('video');
                if (video && adShowing) {
                    video.currentTime =
                        video.duration || 9999;
                }

                var overlays =
                    document.querySelectorAll(
                        '.ytp-ad-overlay-container, ' +
                        '.ytp-ad-text-overlay'
                    );
                overlays.forEach(function(el) {
                    el.remove();
                });
            }
            setInterval(skipAd, 1000);
        })();
    """.trimIndent()

    // ========================================================
    // GENERAL AD BLOCK JS
    // ========================================================

    /**
     * JavaScript to block popup windows
     * and remove ad iframes.
     */
    fun getGeneralAdBlockJs(): String = """
        (function() {
            'use strict';

            var origOpen = window.open;
            window.open = function(url) {
                if (url && (
                    url.includes('ad') ||
                    url.includes('popup') ||
                    url.includes('click')
                )) {
                    return null;
                }
                return origOpen.apply(
                    this, arguments
                );
            };

            var iframes =
                document.querySelectorAll('iframe');
            iframes.forEach(function(iframe) {
                var src = iframe.src || '';
                if (
                    src.includes('doubleclick') ||
                    src.includes(
                        'googlesyndication'
                    ) ||
                    src.includes(
                        'amazon-adsystem'
                    )
                ) {
                    iframe.remove();
                }
            });
        })();
    """.trimIndent()
}