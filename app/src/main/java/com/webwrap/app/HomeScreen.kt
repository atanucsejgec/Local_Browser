package com.webwrap.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webwrap.app.data.DefaultSites
import com.webwrap.app.data.SiteBookmark
import com.webwrap.app.ui.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * HomeScreen — Landing page with greeting, search bar,
 * hero cards, frequent sites grid, bookmarks, history, stats.
 */
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

    // Greeting based on time of day
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 6 -> "Good Night"; hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"; hour < 21 -> "Good Evening"
            else -> "Good Night"
        }
    }
    // Formatted date string
    val dateText = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }

    val heroSites = DefaultSites.sites.take(2)   // YouTube, Google
    val gridSites = DefaultSites.sites.drop(2)   // Rest in 4-column grid

    val gradient = Brush.verticalGradient(
        colors = listOf(BlueDark, BlueMid, BlueLight, BlueLighter)
    )

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ── HEADER: Greeting + Date + Settings ──────
            HomeHeader(
                greeting = greeting,
                dateText = dateText,
                showSettings = showSettings,
                onSettingsToggle = { showSettings = it },
                onClearData = { onClearData(); showSettings = false }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── SEARCH BAR ──────────────────────────────
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { q ->
                    if (q.isNotEmpty()) {
                        val url = if (q.contains(".") && !q.contains(" ")) {
                            if (q.startsWith("http")) q else "https://$q"
                        } else "https://www.google.com/search?q=$q"
                        onSiteSelected(url)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── HERO CARDS: YouTube & Google ─────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                heroSites.forEach { site ->
                    HeroCard(site = site, modifier = Modifier.weight(1f), onClick = { onSiteSelected(site.url) })
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── FREQUENT SITES: 4-Column Grid ───────────
            FrequentSitesSection(gridSites = gridSites, onSiteSelected = onSiteSelected)

            // ── YOUR BOOKMARKS: With Delete ─────────────
            BookmarksSection(
                bookmarks = customBookmarks,
                onSiteSelected = onSiteSelected,
                onDeleteBookmark = onDeleteBookmark
            )

            // ── ADD CUSTOM WEBSITE BUTTON ───────────────
            AddWebsiteButton(onClick = { showAddDialog = true })

            // ── RECENT VISITS ───────────────────────────
            RecentVisitsSection(history = history, onSiteSelected = onSiteSelected)

            Spacer(modifier = Modifier.height(28.dp))

            // ── STATS FOOTER ────────────────────────────
            StatsFooter(
                totalSites = DefaultSites.sites.size + customBookmarks.size,
                bookmarkCount = customBookmarks.size,
                historyCount = history.size
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Add bookmark dialog
    if (showAddDialog) {
        AddBookmarkDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { onAddBookmark(it); showAddDialog = false }
        )
    }
}

// ══════════════════════════════════════════════════════
// HEADER: Greeting + Settings dropdown
// ══════════════════════════════════════════════════════
@Composable
private fun HomeHeader(
    greeting: String, dateText: String,
    showSettings: Boolean, onSettingsToggle: (Boolean) -> Unit,
    onClearData: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(text = greeting, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = dateText, fontSize = 14.sp, color = TextWhiteDim)
        }
        Box {
            Surface(
                onClick = { onSettingsToggle(true) }, shape = CircleShape,
                color = BlueGlassCard, modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Settings, null, tint = TextWhite, modifier = Modifier.size(22.dp))
                }
            }
            DropdownMenu(
                expanded = showSettings, onDismissRequest = { onSettingsToggle(false) },
                containerColor = BlueDark
            ) {
                DropdownMenuItem(
                    text = { Text("🗑️ Clear All Data", color = TextWhite) },
                    onClick = onClearData
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// SEARCH BAR: Glassmorphism style
// ══════════════════════════════════════════════════════
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = query, onValueChange = onQueryChange,
            placeholder = { Text("Search or enter URL...", color = TextWhiteDim, fontSize = 15.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextWhite, modifier = Modifier.size(22.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, null, tint = TextWhiteDim, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Icon(Icons.Default.Mic, null, tint = TextWhiteDim, modifier = Modifier.size(20.dp))
                }
            },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                cursorColor = TextWhite
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSearch(query.trim()) })
        )
    }
}

// ══════════════════════════════════════════════════════
// FREQUENT SITES SECTION: 4-column grid
// ══════════════════════════════════════════════════════
@Composable
private fun FrequentSitesSection(gridSites: List<SiteBookmark>, onSiteSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Frequent Sites", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        Text("All Apps", fontSize = 11.sp, color = TextWhiteSubtle, fontWeight = FontWeight.SemiBold)
    }
    Spacer(modifier = Modifier.height(16.dp))

    val gridRows = gridSites.chunked(4)
    gridRows.forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { site ->
                GridSiteIcon(site = site, onClick = { onSiteSelected(site.url) })
            }
            repeat(4 - row.size) { Spacer(modifier = Modifier.width(70.dp)) }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ══════════════════════════════════════════════════════
// BOOKMARKS SECTION: With delete icons
// ══════════════════════════════════════════════════════
@Composable
private fun BookmarksSection(
    bookmarks: List<SiteBookmark>,
    onSiteSelected: (String) -> Unit,
    onDeleteBookmark: (SiteBookmark) -> Unit
) {
    if (bookmarks.isEmpty()) return
    Spacer(modifier = Modifier.height(8.dp))
    Text("Your Bookmarks", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
    Spacer(modifier = Modifier.height(16.dp))

    bookmarks.chunked(4).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { site ->
                DeletableGridIcon(
                    site = site, onClick = { onSiteSelected(site.url) },
                    onDelete = { onDeleteBookmark(site) }
                )
            }
            repeat(4 - row.size) { Spacer(modifier = Modifier.width(70.dp)) }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ══════════════════════════════════════════════════════
// ADD WEBSITE BUTTON
// ══════════════════════════════════════════════════════
@Composable
private fun AddWebsiteButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(16.dp), color = BlueGlassCard,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, null, tint = TextWhite, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Custom Website", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

// ══════════════════════════════════════════════════════
// RECENT VISITS SECTION
// ══════════════════════════════════════════════════════
@Composable
private fun RecentVisitsSection(history: List<String>, onSiteSelected: (String) -> Unit) {
    if (history.isEmpty()) return
    Spacer(modifier = Modifier.height(28.dp))
    Text("Recent Visits", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
    Spacer(modifier = Modifier.height(14.dp))

    history.takeLast(5).reversed().forEachIndexed { index, url ->
        val timeAgo = when (index) {
            0 -> "Just now"; 1 -> "2 minutes ago"; 2 -> "15 minutes ago"
            3 -> "1 hour ago"; else -> "Earlier today"
        }
        Surface(
            onClick = { onSiteSelected(url) }, shape = RoundedCornerShape(14.dp),
            color = BlueGlassCard, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.History, null, tint = TextWhiteDim, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = url.removePrefix("https://").removePrefix("http://").removePrefix("www."),
                        color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(text = timeAgo, color = TextWhiteSubtle, fontSize = 11.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextWhiteSubtle, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// STATS FOOTER: Sites / Bookmarks / History counts
// ══════════════════════════════════════════════════════
@Composable
private fun StatsFooter(totalSites: Int, bookmarkCount: Int, historyCount: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = BlueGlassCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(count = "$totalSites", label = "SITES")
            StatItem(count = "$bookmarkCount", label = "BOOKMARKS")
            StatItem(count = "$historyCount", label = "HISTORY")
        }
    }
}