package com.webwrap.app.feature.https

/**
 * Enforces HTTPS connections by upgrading
 * insecure HTTP URLs to HTTPS where possible.
 */
object HttpsEnforcer {

    // Domains known to NOT support HTTPS
    private val httpOnlyDomains = setOf(
        "localhost",
        "10.0.2.2", // Android emulator
        "192.168.",  // Local network
        "127.0.0.1",
    )

    /**
     * Upgrade HTTP URL to HTTPS if possible.
     *
     * @param url The original URL
     * @return The upgraded URL (or original if
     *         HTTPS upgrade is not appropriate)
     */
    fun upgradeToHttps(url: String): String {
        // Already HTTPS — no change needed
        if (url.startsWith("https://")) {
            return url
        }

        // Not HTTP — don't modify
        if (!url.startsWith("http://")) {
            return url
        }

        // Check if domain is HTTP-only
        if (isHttpOnlyDomain(url)) {
            return url
        }

        // Upgrade to HTTPS
        return url.replaceFirst(
            "http://", "https://"
        )
    }

    /**
     * Check if URL belongs to a domain that
     * only supports HTTP (local networks, etc).
     */
    private fun isHttpOnlyDomain(url: String): Boolean {
        val host = extractHost(url)
        return httpOnlyDomains.any { domain ->
            host.contains(domain)
        }
    }

    /**
     * Extract hostname from URL.
     */
    private fun extractHost(url: String): String {
        return url
            .removePrefix("http://")
            .removePrefix("https://")
            .split("/")
            .firstOrNull()
            ?: ""
    }

    /**
     * Check if a URL should be intercepted and
     * upgraded in shouldOverrideUrlLoading.
     *
     * @return Upgraded URL if changed, null if
     *         no upgrade needed
     */
    fun shouldUpgrade(url: String): String? {
        if (!url.startsWith("http://")) return null
        if (isHttpOnlyDomain(url)) return null
        return upgradeToHttps(url)
    }
}