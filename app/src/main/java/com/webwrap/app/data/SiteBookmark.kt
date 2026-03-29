package com.webwrap.app.data

data class SiteBookmark(
    val name: String,
    val url: String,
    val icon: String,
    val color: Long,
    val iconUrl: String = "",
    val isPinned: Boolean = false
)

object DefaultSites {
    val sites = listOf(
        SiteBookmark("YouTube", "https://www.youtube.com", "▶️", 0xFFFF0000),
        SiteBookmark("Google", "https://www.google.com", "🔍", 0xFF4285F4),
        SiteBookmark("Twitter / X", "https://www.x.com", "🐦", 0xFF1DA1F2),
        SiteBookmark("Instagram", "https://www.instagram.com", "📷", 0xFFE4405F),
        SiteBookmark("WhatsApp", "https://web.whatsapp.com", "💬", 0xFF25D366),
        SiteBookmark("LinkedIn", "https://www.linkedin.com", "💼", 0xFF0A66C2),
        SiteBookmark("Reddit", "https://www.reddit.com", "🤖", 0xFFFF4500),
        SiteBookmark("GitHub", "https://github.com", "🐙", 0xFF333333),
        SiteBookmark("Amazon", "https://www.amazon.com", "🛒", 0xFFFF9900),
        SiteBookmark("Netflix", "https://www.netflix.com", "🎬", 0xFFE50914),
        SiteBookmark("Gmail", "https://mail.google.com", "📧", 0xFFEA4335),
        SiteBookmark("ChatGPT", "https://chat.openai.com", "✨", 0xFF10A37F),
        SiteBookmark("Wikipedia", "https://www.wikipedia.org", "📚", 0xFF000000),
        SiteBookmark("Spotify", "https://open.spotify.com", "🎵", 0xFF1DB954),
        SiteBookmark("Facebook", "https://www.facebook.com", "📘", 0xFF1877F2),
        SiteBookmark("Pinterest", "https://www.pinterest.com", "📌", 0xFFBD081C),
    )
}

// ═══════════════════════════════════════════════
// ✅ Get favicon URL — falls back to Google's service
// ═══════════════════════════════════════════════
fun getSiteFaviconUrl(url: String): String {
    val domain = url.removePrefix("https://").removePrefix("http://")
        .removePrefix("www.").removePrefix("web.").removePrefix("mail.")
        .removePrefix("open.").removePrefix("chat.")
        .split("/").first()
    return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
}