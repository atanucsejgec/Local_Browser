package com.webwrap.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.webwrap.app.data.SiteBookmark
import com.webwrap.app.data.getSiteFaviconUrl

// ── Color Palette (shared with HomeScreen) ──────────
val BlueDark = Color(0xFF1A3B6D)
val BlueMid = Color(0xFF2D6BBF)
val BlueLight = Color(0xFF5B9FE8)
val BlueLighter = Color(0xFF8AC4F8)
val BlueGlassCard = Color.White.copy(alpha = 0.13f)
val BlueGlassBorder = Color.White.copy(alpha = 0.2f)
val TextWhite = Color.White
val TextWhiteDim = Color.White.copy(alpha = 0.6f)
val TextWhiteSubtle = Color.White.copy(alpha = 0.4f)

// ══════════════════════════════════════════════════════
// HERO CARD — Large card with watermark icon (YouTube, Google)
// ══════════════════════════════════════════════════════
@Composable
fun HeroCard(
    site: SiteBookmark,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val watermarkIcon = when {
        site.url.contains("youtube") -> Icons.Default.PlayArrow
        site.url.contains("google") -> Icons.Default.Search
        site.url.contains("facebook") -> Icons.Default.ThumbUp
        else -> Icons.Default.Language
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = BlueGlassCard,
        modifier = modifier.height(120.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Watermark icon (large, faded)
            Icon(
                imageVector = watermarkIcon, contentDescription = null,
                tint = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.size(100.dp).align(Alignment.BottomEnd).offset(x = 15.dp, y = 15.dp)
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                SiteFavicon(url = site.url, emoji = site.icon, size = 40, bgColor = Color.White, iconPadding = 6)
                Text(text = site.name, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// GRID SITE ICON — Circular icon + label
// ══════════════════════════════════════════════════════
@Composable
fun GridSiteIcon(site: SiteBookmark, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp).clickable { onClick() }
    ) {
        SiteFavicon(url = site.url, emoji = site.icon, size = 52, bgColor = Color.White.copy(alpha = 0.15f), iconPadding = 12)
        Spacer(Modifier.height(8.dp))
        Text(
            text = site.name, color = TextWhiteDim, fontSize = 11.sp,
            fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ══════════════════════════════════════════════════════
// DELETABLE GRID ICON — Bookmark with delete X button
// ══════════════════════════════════════════════════════
@Composable
fun DeletableGridIcon(site: SiteBookmark, onClick: () -> Unit, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(70.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().clickable { onClick() }
        ) {
            SiteFavicon(url = site.url, emoji = site.icon, size = 52, bgColor = Color.White.copy(alpha = 0.15f), iconPadding = 12)
            Spacer(Modifier.height(8.dp))
            Text(
                text = site.name, color = TextWhiteDim, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Delete X button
        Surface(
            onClick = { showConfirm = true }, shape = CircleShape,
            color = Color(0xFFE57373).copy(alpha = 0.9f),
            modifier = Modifier.size(18.dp).align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Default.Close, "Delete", tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
    }
    // Delete confirmation dialog
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = BlueDark,
            title = { Text("Delete Bookmark?", color = TextWhite) },
            text = { Text("Remove \"${site.name}\" from bookmarks?", color = TextWhiteDim) },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", color = TextWhiteDim)
                }
            }
        )
    }
}

// ══════════════════════════════════════════════════════
// SITE FAVICON — Loads image from Google service, falls back to emoji
// ══════════════════════════════════════════════════════
@Composable
fun SiteFavicon(
    url: String, emoji: String, customIconUrl: String = "",
    size: Int, bgColor: Color, iconPadding: Int
) {
    val context = LocalContext.current
    var loadFailed by remember { mutableStateOf(false) }
    val faviconUrl = remember(url, customIconUrl) {
        if (customIconUrl.isNotEmpty()) customIconUrl else getSiteFaviconUrl(url)
    }
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (!loadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(faviconUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.size((size - iconPadding * 2).dp).clip(CircleShape),
                contentScale = ContentScale.Fit,
                onError = { loadFailed = true }
            )
        } else {
            Text(text = emoji, fontSize = (size / 2.5).sp)
        }
    }
}

// ══════════════════════════════════════════════════════
// STAT ITEM — Count + Label (used in stats footer)
// ══════════════════════════════════════════════════════
@Composable
fun StatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        Text(
            text = label, fontSize = 10.sp, color = TextWhiteSubtle,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
        )
    }
}

// ══════════════════════════════════════════════════════
// SECTION HEADER — Title text
// ══════════════════════════════════════════════════════
@Composable
fun SectionHeader(title: String) {
    Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
}