package com.webwrap.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.webwrap.app.data.*
import com.webwrap.app.ui.AddBookmarkDialog
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════
// Color Palette
// ═══════════════════════════════════════════════
private val BlueDark = Color(0xFF1A3B6D)
private val BlueMid = Color(0xFF2D6BBF)
private val BlueLight = Color(0xFF5B9FE8)
private val BlueLighter = Color(0xFF8AC4F8)
private val BlueGlassCard = Color.White.copy(alpha = 0.13f)
private val BlueGlassBorder = Color.White.copy(alpha = 0.2f)
private val TextWhite = Color.White
private val TextWhiteDim = Color.White.copy(alpha = 0.6f)
private val TextWhiteSubtle = Color.White.copy(alpha = 0.4f)

@Composable
fun HomeScreen(
    onSiteSelected: (String) -> Unit,
    customBookmarks: List<SiteBookmark>,
    onAddBookmark: (SiteBookmark) -> Unit,
    onDeleteBookmark: (SiteBookmark) -> Unit,
    onClearData: () -> Unit,
    history: List<String>
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 6 -> "Good Night"
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            hour < 21 -> "Good Evening"
            else -> "Good Night"
        }
    }

    val dateText = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }

    val heroSites = DefaultSites.sites.take(2)        // YouTube, Google
    val gridSites = DefaultSites.sites.drop(2)        // Rest in 4-column grid

    val gradient = Brush.verticalGradient(
        colors = listOf(BlueDark, BlueMid, BlueLight, BlueLighter)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ═══════════════════════════════════════
            // ✅ HEADER — Greeting + Date + Settings
            // ═══════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = greeting,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateText,
                        fontSize = 14.sp,
                        color = TextWhiteDim
                    )
                }

                Box {
                    Surface(
                        onClick = { showSettings = true },
                        shape = CircleShape,
                        color = BlueGlassCard,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Settings, null,
                                tint = TextWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showSettings,
                        onDismissRequest = { showSettings = false },
                        containerColor = BlueDark
                    ) {
                        DropdownMenuItem(
                            text = { Text("🗑️ Clear All Data", color = TextWhite) },
                            onClick = { onClearData(); showSettings = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══════════════════════════════════════
            // ✅ SEARCH BAR — Glassmorphism style
            // ═══════════════════════════════════════
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search or enter URL...",
                            color = TextWhiteDim,
                            fontSize = 15.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search, null,
                            tint = TextWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear, null,
                                    tint = TextWhiteDim,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.Mic, null,
                                tint = TextWhiteDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = TextWhite
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val q = searchQuery.trim()
                            if (q.isNotEmpty()) {
                                val url = if (q.contains(".") && !q.contains(" ")) {
                                    if (q.startsWith("http")) q else "https://$q"
                                } else "https://www.google.com/search?q=$q"
                                onSiteSelected(url)
                            }
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ═══════════════════════════════════════
            // ✅ HERO CARDS — YouTube & Google
            // ═══════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                heroSites.forEach { site ->
                    HeroCard(
                        site = site,
                        modifier = Modifier.weight(1f),
                        onClick = { onSiteSelected(site.url) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ═══════════════════════════════════════
            // ✅ FREQUENT SITES — 4-Column Grid
            // ═══════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Frequent Sites",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = "All Apps",
                    fontSize = 11.sp,
                    color = TextWhiteSubtle,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val gridRows = gridSites.chunked(4)
            gridRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { site ->
                        GridSiteIcon(
                            site = site,
                            onClick = { onSiteSelected(site.url) }
                        )
                    }
                    // Fill empty slots if row is incomplete
                    repeat(4 - row.size) {
                        Spacer(modifier = Modifier.width(70.dp))
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ═══════════════════════════════════════
            // ✅ YOUR BOOKMARKS — With Delete
            // ═══════════════════════════════════════
            if (customBookmarks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your Bookmarks",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(16.dp))

                val bookmarkRows = customBookmarks.chunked(4)
                bookmarkRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { site ->
                            DeletableGridIcon(
                                site = site,
                                onClick = { onSiteSelected(site.url) },
                                onDelete = { onDeleteBookmark(site) }
                            )
                        }
                        repeat(4 - row.size) {
                            Spacer(modifier = Modifier.width(70.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // ═══════════════════════════════════════
            // ✅ ADD CUSTOM WEBSITE BUTTON
            // ═══════════════════════════════════════
            Surface(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = BlueGlassCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add, null,
                        tint = TextWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        " Add Custom Website",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }

            // ═══════════════════════════════════════
            // ✅ RECENT VISITS
            // ═══════════════════════════════════════
            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Recent Visits",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(14.dp))

                history.takeLast(5).reversed().forEachIndexed { index, url ->
                    val timeAgo = when (index) {
                        0 -> "Just now"
                        1 -> "2 minutes ago"
                        2 -> "15 minutes ago"
                        3 -> "1 hour ago"
                        else -> "Earlier today"
                    }

                    Surface(
                        onClick = { onSiteSelected(url) },
                        shape = RoundedCornerShape(14.dp),
                        color = BlueGlassCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Clock icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.History, null,
                                    tint = TextWhiteDim,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = url.removePrefix("https://")
                                        .removePrefix("http://")
                                        .removePrefix("www."),
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = timeAgo,
                                    color = TextWhiteSubtle,
                                    fontSize = 11.sp
                                )
                            }

                            Icon(
                                Icons.Default.ChevronRight, null,
                                tint = TextWhiteSubtle,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ═══════════════════════════════════════
            // ✅ STATS FOOTER
            // ═══════════════════════════════════════
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BlueGlassCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        count = "${DefaultSites.sites.size + customBookmarks.size}",
                        label = "SITES"
                    )
                    StatItem(
                        count = "${customBookmarks.size}",
                        label = "BOOKMARKS"
                    )
                    StatItem(
                        count = "${history.size}",
                        label = "HISTORY"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddDialog) {
        AddBookmarkDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { onAddBookmark(it); showAddDialog = false }
        )
    }
}

// ═══════════════════════════════════════════════
// ✅ HERO CARD — Large card with watermark icon
// ═══════════════════════════════════════════════
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
                imageVector = watermarkIcon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 15.dp, y = 15.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Site favicon
                SiteFavicon(
                    url = site.url,
                    emoji = site.icon,
                    size = 40,
                    bgColor = Color.White,
                    iconPadding = 6
                )

                Text(
                    text = site.name,
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// ✅ GRID SITE ICON — Circular icon + label
// ═══════════════════════════════════════════════
@Composable
fun GridSiteIcon(
    site: SiteBookmark,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .clickable { onClick() }
    ) {
        SiteFavicon(
            url = site.url,
            emoji = site.icon,
            size = 52,
            bgColor = Color.White.copy(alpha = 0.15f),
            iconPadding = 12
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = site.name,
            color = TextWhiteDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ═══════════════════════════════════════════════
// ✅ DELETABLE GRID ICON — Bookmark with delete X
// ═══════════════════════════════════════════════
@Composable
fun DeletableGridIcon(
    site: SiteBookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.width(70.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
        ) {
            SiteFavicon(
                url = site.url,
                emoji = site.icon,
                size = 52,
                bgColor = Color.White.copy(alpha = 0.15f),
                iconPadding = 12
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = site.name,
                color = TextWhiteDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Delete X button
        Surface(
            onClick = { showConfirm = true },
            shape = CircleShape,
            color = Color(0xFFE57373).copy(alpha = 0.9f),
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-2).dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    Icons.Default.Close, "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = BlueDark,
            title = { Text("Delete Bookmark?", color = TextWhite) },
            text = {
                Text(
                    "Remove \"${site.name}\" from bookmarks?",
                    color = TextWhiteDim
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showConfirm = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE57373)
                    )
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", color = TextWhiteDim)
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════
// ✅ SITE FAVICON — Loads image, falls back to emoji
// ═══════════════════════════════════════════════
@Composable
fun SiteFavicon(
    url: String,
    emoji: String,
    customIconUrl: String = "",
    size: Int,
    bgColor: Color,
    iconPadding: Int
) {
    val context = LocalContext.current
    var loadFailed by remember { mutableStateOf(false) }
    //val faviconUrl = remember(url) { getSiteFaviconUrl(url) }
    val faviconUrl = remember(url, customIconUrl) {
        if (customIconUrl.isNotEmpty()) customIconUrl
        else getSiteFaviconUrl(url)
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (!loadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(faviconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size((size - iconPadding * 2).dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit,
                onError = { loadFailed = true }
            )
        } else {
            // Fallback to emoji
            Text(
                text = emoji,
                fontSize = (size / 2.5).sp
            )
        }
    }
}

// ═══════════════════════════════════════════════
// ✅ STAT ITEM — Count + Label
// ═══════════════════════════════════════════════
@Composable
fun StatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextWhiteSubtle,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}

// ═══════════════════════════════════════════════
// ✅ SECTION HEADER
// ═══════════════════════════════════════════════
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TextWhite
    )
}