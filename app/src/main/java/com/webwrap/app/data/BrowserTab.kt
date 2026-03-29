package com.webwrap.app.data

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String = "https://www.google.com"
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("New Tab")
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(true)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var webView: WebView? = null

    fun getDisplayTitle(): String {
        return when {
            title.isNotEmpty() && title != "New Tab" -> title
            url.contains("google.com") -> "Google"
            url.contains("youtube.com") -> "YouTube"
            url.contains("facebook.com") -> "Facebook"
            url.contains("github.com") -> "GitHub"
            url.contains("instagram.com") -> "Instagram"
            url.contains("twitter.com") || url.contains("x.com") -> "Twitter / X"
            url.contains("reddit.com") -> "Reddit"
            url.contains("whatsapp.com") -> "WhatsApp"
            url.contains("linkedin.com") -> "LinkedIn"
            url.contains("netflix.com") -> "Netflix"
            url.contains("chatgpt") || url.contains("openai") -> "ChatGPT"
            else -> title.ifEmpty { url.take(25) }
        }
    }

    fun getEmoji(): String {
        return when {
            url.contains("google.com/search") -> "🔍"
            url.contains("google.com") -> "🔍"
            url.contains("youtube.com") -> "▶️"
            url.contains("facebook.com") -> "📘"
            url.contains("instagram.com") -> "📷"
            url.contains("twitter.com") || url.contains("x.com") -> "🐦"
            url.contains("github.com") -> "🐙"
            url.contains("reddit.com") -> "🤖"
            url.contains("whatsapp.com") -> "💬"
            url.contains("linkedin.com") -> "💼"
            url.contains("netflix.com") -> "🎬"
            url.contains("amazon.com") -> "🛒"
            url.contains("chatgpt") || url.contains("openai") -> "🤖"
            url.contains("spotify.com") -> "🎵"
            url.contains("wikipedia.org") -> "📚"
            else -> "🌐"
        }
    }
}