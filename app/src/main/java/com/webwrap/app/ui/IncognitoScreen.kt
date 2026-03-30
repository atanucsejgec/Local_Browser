package com.webwrap.app.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.webwrap.app.data.IncognitoManager

/**
 * IncognitoScreen — Completely isolated browser environment.
 * Uses its own WebView with disabled cache, cookies, storage.
 * No data shared with normal browsing. All data wiped on exit.
 * Acts like a brand new device with zero login sessions.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IncognitoScreen(onClose: () -> Unit) {
    val context = LocalContext.current

    // ── URL State ────────────────────────────────────────
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var urlInput by remember { mutableStateOf("") }
    var showUrlBar by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("Incognito") }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    // ── Incognito WebView Reference ─────────────────────
    val incognitoWebView = remember { mutableStateOf<WebView?>(null) }

    // ── Enter/Exit Incognito — Save/Restore normal cookies ──
    DisposableEffect(Unit) {
        IncognitoManager.enterIncognito()
        onDispose {
            // Destroy WebView completely
            incognitoWebView.value?.let { wv ->
                wv.stopLoading()
                wv.clearHistory()
                wv.clearCache(true)
                wv.clearFormData()
                wv.clearSslPreferences()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
            incognitoWebView.value = null
            // Restore normal cookies
            IncognitoManager.exitIncognito()
        }
    }

    /** Navigate to URL or search query */
    fun navigateTo(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val url = if (q.contains(".") && !q.contains(" ")) {
            if (q.startsWith("http")) q else "https://$q"
        } else "https://www.google.com/search?q=$q"
        incognitoWebView.value?.loadUrl(url)
        showUrlBar = false
    }

    // ── Back Handler ────────────────────────────────────
    BackHandler {
        when {
            showUrlBar -> showUrlBar = false
            canGoBack -> incognitoWebView.value?.goBack()
            else -> onClose()
        }
    }

    // ══════════════════════════════════════════════════════
    // UI LAYOUT
    // ══════════════════════════════════════════════════════
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // ── Incognito Header Bar ────────────────────
            Surface(
                color = Color(0xFF1A1A1A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close Incognito", tint = Color.White)
                    }
                    // Incognito indicator
                    Icon(
                        Icons.Default.VisibilityOff, null,
                        tint = Color(0xFF888888), modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    // Title/URL display — tap to open URL bar
                    Surface(
                        onClick = {
                            urlInput = currentUrl
                            showUrlBar = true
                        },
                        color = Color(0xFF2A2A2A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = pageTitle.ifEmpty {
                                currentUrl.removePrefix("https://")
                                    .removePrefix("http://").removePrefix("www.")
                            },
                            color = Color(0xFFAAAAAA), fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    // Back button
                    IconButton(
                        onClick = { incognitoWebView.value?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack, "Back",
                            tint = if (canGoBack) Color.White else Color(0xFF444444)
                        )
                    }
                    // Reload
                    IconButton(onClick = { incognitoWebView.value?.reload() }) {
                        Icon(Icons.Default.Refresh, "Reload", tint = Color.White)
                    }
                }
            }

            // ── Progress Bar ────────────────────────────
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF888888), trackColor = Color.Transparent
                )
            }

            // ── Incognito WebView ───────────────────────
            AndroidView(
                factory = { ctx ->
                    IncognitoManager.createIncognitoWebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?, url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                url?.let { currentUrl = it; isLoading = true }
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                url?.let { currentUrl = it }
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                            }
                            override fun shouldOverrideUrlLoading(
                                view: WebView?, request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.startsWith("tel:") || url.startsWith("mailto:")) {
                                    try {
                                        ctx.startActivity(android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(url)
                                        ))
                                    } catch (_: Exception) { }
                                    return true
                                }
                                return false
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                if (newProgress == 100) isLoading = false
                            }
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                title?.let { pageTitle = it }
                            }
                        }
                        loadUrl("https://www.google.com")
                        incognitoWebView.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Incognito Badge ─────────────────────────────
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            color = Color(0xFF333333).copy(alpha = 0.9f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.VisibilityOff, null,
                    tint = Color(0xFF888888), modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Incognito — No data saved",
                    color = Color(0xFFAAAAAA), fontSize = 12.sp
                )
            }
        }

        // ── URL Bar Overlay ─────────────────────────────
        AnimatedVisibility(
            visible = showUrlBar,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                color = Color(0xFF1A1A1A).copy(alpha = 0.98f),
                shape = RoundedCornerShape(16.dp), shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = urlInput, onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f).height(50.dp),
                        singleLine = true,
                        placeholder = { Text("Search or URL...", color = Color(0xFF666666)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF2A2A2A),
                            unfocusedContainerColor = Color(0xFF2A2A2A),
                            focusedBorderColor = Color(0xFF666666),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = { navigateTo(urlInput) }
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { showUrlBar = false }) {
                        Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}