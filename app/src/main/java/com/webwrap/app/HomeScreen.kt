package com.webwrap.app

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.webwrap.app.data.DefaultSites
import com.webwrap.app.data.SiteBookmark
import com.webwrap.app.data.SessionManager
import com.webwrap.app.data.getSiteFaviconUrl
import com.webwrap.app.data.model.HistoryEntry
import com.webwrap.app.ui.AddBookmarkDialog
import com.webwrap.app.viewmodel.HomeViewModel

// ============================================================
// COLOR PALETTE
// ============================================================

private val BlueDark = Color(0xFF1A3B6D)
private val BlueMid = Color(0xFF2D6BBF)
private val BlueLight = Color(0xFF5B9FE8)
private val BlueLighter = Color(0xFF8AC4F8)
private val BlueGlassCard = Color.White.copy(alpha = 0.13f)
private val BlueGlassBorder = Color.White.copy(alpha = 0.2f)
private val TextWhite = Color.White
private val TextWhiteDim = Color.White.copy(alpha = 0.6f)
private val TextWhiteSubtle = Color.White.copy(alpha = 0.4f)

// ============================================================
// MAIN HOME SCREEN
// ============================================================

/**
 * Home screen composable.
 * Shows greeting, search bar, site grid,
 * bookmarks, history, and stats.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToBrowser: (String) -> Unit,
    onNavigateToIncognito: (String) -> Unit
) {
    val context = LocalContext.current

    // Collect state from ViewModel
    val customBookmarks by viewModel.bookmarkRepo
        .bookmarks.collectAsState()
    val historyEntries by viewModel.historyRepo
        .history.collectAsState()
    val searchQuery by viewModel.searchQuery
        .collectAsState()
    val showAddDialog by viewModel.showAddDialog
        .collectAsState()
    val showSettings by viewModel.showSettings
        .collectAsState()

    // Computed values
    val greeting = remember { viewModel.getGreeting() }
    val dateText = remember { viewModel.getDateText() }

    // Auto-restore session on first launch
    var sessionChecked by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(Unit) {
        if (!sessionChecked) {
            sessionChecked = true
            viewModel.consumeAutoRestoreUrl()?.let { url ->
                onNavigateToBrowser(url)
            }
        }
    }

    // Site splits
    val heroSites = DefaultSites.sites.take(2)
    val gridSites = DefaultSites.sites.drop(2)

    val gradient = Brush.verticalGradient(
        colors = listOf(
            BlueDark, BlueMid, BlueLight, BlueLighter
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // HEADER — Greeting + Date + Settings
            HeaderSection(
                greeting = greeting,
                dateText = dateText,
                showSettings = showSettings,
                onToggleSettings = {
                    viewModel.toggleSettings(it)
                },
                onClearData = {
                    viewModel.clearAllData()
                    viewModel.toggleSettings(false)
                    Toast.makeText(
                        context,
                        "🧹 Cleared!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onIncognito = {
                    viewModel.toggleSettings(false)
                    onNavigateToIncognito(
                        "https://www.google.com"
                    )
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SEARCH BAR
            SearchBarSection(
                query = searchQuery,
                onQueryChange = {
                    viewModel.updateSearchQuery(it)
                },
                onSearch = {
                    val q = searchQuery.trim()
                    if (q.isNotEmpty()) {
                        val url = viewModel.queryToUrl(q)
                        viewModel.addToHistory(url)
                        onNavigateToBrowser(url)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // HERO CARDS — YouTube & Google
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                heroSites.forEach { site ->
                    HeroCard(
                        site = site,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.addToHistory(site.url)
                            onNavigateToBrowser(site.url)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // FREQUENT SITES HEADER
            FrequentSitesHeader()

            Spacer(modifier = Modifier.height(16.dp))

            // FREQUENT SITES GRID
            FrequentSitesGrid(
                sites = gridSites,
                onSiteClick = { url ->
                    viewModel.addToHistory(url)
                    onNavigateToBrowser(url)
                }
            )

            // YOUR BOOKMARKS
            if (customBookmarks.isNotEmpty()) {
                BookmarksSection(
                    bookmarks = customBookmarks,
                    onBookmarkClick = { url ->
                        viewModel.addToHistory(url)
                        onNavigateToBrowser(url)
                    },
                    onDeleteBookmark = {
                        viewModel.deleteBookmark(it)
                    }
                )
            }

            // ADD CUSTOM WEBSITE BUTTON
            AddWebsiteButton(
                onClick = {
                    viewModel.toggleAddDialog(true)
                }
            )

            // RECENT VISITS
            if (historyEntries.isNotEmpty()) {
                RecentVisitsSection(
                    entries = historyEntries
                        .takeLast(5)
                        .reversed(),
                    onEntryClick = { url ->
                        onNavigateToBrowser(url)
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // STATS FOOTER
            StatsFooter(
                totalSites = DefaultSites.sites.size +
                        customBookmarks.size,
                bookmarkCount = customBookmarks.size,
                historyCount = historyEntries.size
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ADD BOOKMARK DIALOG
    if (showAddDialog) {
        AddBookmarkDialog(
            onDismiss = {
                viewModel.toggleAddDialog(false)
            },
            onAdd = { bookmark ->
                viewModel.addBookmark(bookmark)
                viewModel.toggleAddDialog(false)
                Toast.makeText(
                    context,
                    "📌 Added!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

// ============================================================
// HEADER SECTION
// ============================================================

/** Header with greeting, date, and settings menu. */
@Composable
private fun HeaderSection(
    greeting: String,
    dateText: String,
    showSettings: Boolean,
    onToggleSettings: (Boolean) -> Unit,
    onClearData: () -> Unit,
    onIncognito: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
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
                onClick = { onToggleSettings(true) },
                shape = CircleShape,
                color = BlueGlassCard,
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showSettings,
                onDismissRequest = {
                    onToggleSettings(false)
                },
                containerColor = BlueDark
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "🧹 Clear All Data",
                            color = TextWhite
                        )
                    },
                    onClick = onClearData
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "🕶️ Incognito Mode",
                            color = TextWhite
                        )
                    },
                    onClick = onIncognito
                )
            }
        }
    }
}

// ============================================================
// SEARCH BAR SECTION
// ============================================================

/** Glassmorphism-style search bar. */
@Composable
private fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Search or enter URL...",
                    color = TextWhiteDim,
                    fontSize = 15.sp
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(22.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") }
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = null,
                            tint = TextWhiteDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
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
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = { onSearch() }
            )
        )
    }
}

// ============================================================
// FREQUENT SITES HEADER
// ============================================================

/** Section header for frequent sites. */
@Composable
private fun FrequentSitesHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
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
}

// ============================================================
// FREQUENT SITES GRID
// ============================================================

/** 4-column grid of frequently visited sites. */
@Composable
private fun FrequentSitesGrid(
    sites: List<SiteBookmark>,
    onSiteClick: (String) -> Unit
) {
    val gridRows = sites.chunked(4)
    gridRows.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceEvenly
        ) {
            row.forEach { site ->
                GridSiteIcon(
                    site = site,
                    onClick = { onSiteClick(site.url) }
                )
            }
            repeat(4 - row.size) {
                Spacer(
                    modifier = Modifier.width(70.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ============================================================
// BOOKMARKS SECTION
// ============================================================

/** Section showing user bookmarks with delete. */
@Composable
private fun BookmarksSection(
    bookmarks: List<SiteBookmark>,
    onBookmarkClick: (String) -> Unit,
    onDeleteBookmark: (SiteBookmark) -> Unit
) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Your Bookmarks",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TextWhite
    )
    Spacer(modifier = Modifier.height(16.dp))

    val bookmarkRows = bookmarks.chunked(4)
    bookmarkRows.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceEvenly
        ) {
            row.forEach { site ->
                DeletableGridIcon(
                    site = site,
                    onClick = {
                        onBookmarkClick(site.url)
                    },
                    onDelete = {
                        onDeleteBookmark(site)
                    }
                )
            }
            repeat(4 - row.size) {
                Spacer(
                    modifier = Modifier.width(70.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ============================================================
// ADD WEBSITE BUTTON
// ============================================================

/** Button to add a custom website bookmark. */
@Composable
private fun AddWebsiteButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = BlueGlassCard,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement =
                Arrangement.Center,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = TextWhite,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Add Custom Website",
                color = TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

// ============================================================
// RECENT VISITS SECTION
// ============================================================

/** Recent visits with real timestamps. */
@Composable
private fun RecentVisitsSection(
    entries: List<HistoryEntry>,
    onEntryClick: (String) -> Unit
) {
    Spacer(modifier = Modifier.height(28.dp))
    Text(
        text = "Recent Visits",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TextWhite
    )
    Spacer(modifier = Modifier.height(14.dp))

    entries.forEach { entry ->
        Surface(
            onClick = { onEntryClick(entry.url) },
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
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                // Clock icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Color.White.copy(
                                alpha = 0.1f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = TextWhiteDim,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.url
                            .removePrefix("https://")
                            .removePrefix("http://")
                            .removePrefix("www."),
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.getTimeAgo(),
                        color = TextWhiteSubtle,
                        fontSize = 11.sp
                    )
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextWhiteSubtle,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ============================================================
// STATS FOOTER
// ============================================================

/** Stats row: sites, bookmarks, history count. */
@Composable
private fun StatsFooter(
    totalSites: Int,
    bookmarkCount: Int,
    historyCount: Int
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BlueGlassCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalArrangement =
                Arrangement.SpaceEvenly
        ) {
            StatItem(
                count = "$totalSites",
                label = "SITES"
            )
            StatItem(
                count = "$bookmarkCount",
                label = "BOOKMARKS"
            )
            StatItem(
                count = "$historyCount",
                label = "HISTORY"
            )
        }
    }
}

// ============================================================
// HERO CARD — Large card with watermark icon
// ============================================================

/** Large hero card for YouTube/Google. */
@Composable
fun HeroCard(
    site: SiteBookmark,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val watermarkIcon = when {
        site.url.contains("youtube") ->
            Icons.Default.PlayArrow
        site.url.contains("google") ->
            Icons.Default.Search
        site.url.contains("facebook") ->
            Icons.Default.ThumbUp
        else -> Icons.Default.Language
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = BlueGlassCard,
        modifier = modifier.height(120.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Watermark icon
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
                verticalArrangement =
                    Arrangement.SpaceBetween
            ) {
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

// ============================================================
// GRID SITE ICON — Circular icon + label
// ============================================================

/** Grid icon for frequent sites. */
@Composable
fun GridSiteIcon(
    site: SiteBookmark,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally,
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

// ============================================================
// DELETABLE GRID ICON — Bookmark with delete X
// ============================================================

/** Bookmark icon with delete confirmation. */
@Composable
fun DeletableGridIcon(
    site: SiteBookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirm by remember {
        mutableStateOf(false)
    }

    Box(modifier = Modifier.width(70.dp)) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
        ) {
            SiteFavicon(
                url = site.url,
                emoji = site.icon,
                size = 52,
                bgColor = Color.White.copy(
                    alpha = 0.15f
                ),
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
            color = Color(0xFFE57373)
                .copy(alpha = 0.9f),
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
                    Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = {
                showConfirm = false
            },
            containerColor = BlueDark,
            title = {
                Text(
                    "Delete Bookmark?",
                    color = TextWhite
                )
            },
            text = {
                Text(
                    "Remove \"${site.name}\"?",
                    color = TextWhiteDim
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showConfirm = false
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                Color(0xFFE57373)
                        )
                ) {
                    Text(
                        "Delete",
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                    }
                ) {
                    Text(
                        "Cancel",
                        color = TextWhiteDim
                    )
                }
            }
        )
    }
}

// ============================================================
// SITE FAVICON — Loads image, falls back to emoji
// ============================================================

/** Favicon loader with emoji fallback. */
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
    var loadFailed by remember {
        mutableStateOf(false)
    }
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
                    .size(
                        (size - iconPadding * 2).dp
                    )
                    .clip(CircleShape),
                contentScale = ContentScale.Fit,
                onError = { loadFailed = true }
            )
        } else {
            Text(
                text = emoji,
                fontSize = (size / 2.5).sp
            )
        }
    }
}

// ============================================================
// STAT ITEM — Count + Label
// ============================================================

/** Single stat display (count + label). */
@Composable
fun StatItem(count: String, label: String) {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
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

// ============================================================
// SECTION HEADER
// ============================================================

/** Reusable section title text. */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TextWhite
    )
}