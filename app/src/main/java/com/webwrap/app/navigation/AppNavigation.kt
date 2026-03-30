package com.webwrap.app.ui.navigation

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.webwrap.app.MainActivity
import com.webwrap.app.data.SiteBookmark
import com.webwrap.app.HomeScreen
import com.webwrap.app.WebViewScreen
import com.webwrap.app.ui.IncognitoScreen
import com.webwrap.app.ui.viewmodel.BrowserViewModel
import com.webwrap.app.ui.viewmodel.HomeViewModel

/**
 * AppNavigation — Replaces sealed class Screen with Jetpack Navigation.
 * Two routes: "home" and "browser".
 * WebViews persist via WebViewHolder singleton across navigation.
 */
@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel = viewModel(),
    browserViewModel: BrowserViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    // Determine start destination from saved session
    val startDest = remember {
        val session = browserViewModel.loadSavedSession()
        if (session != null && session.tabs.isNotEmpty()) {
            browserViewModel.savedSession = session
            browserViewModel.initialUrl = session.tabs[
                session.activeTabIndex.coerceIn(0, session.tabs.size - 1)
            ].url
            "browser"
        } else "home"
    }

    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDest) {

        /** Home screen route */
        composable("home") {
            LaunchedEffect(Unit) { activity?.showSystemBarsNormal() }
            HomeScreen(
                onSiteSelected = { url ->
                    homeViewModel.addToHistory(url)
                    browserViewModel.initialUrl = url
                    browserViewModel.savedSession = null
                    navController.navigate("browser") {
                        launchSingleTop = true
                    }
                },
                customBookmarks = homeViewModel.bookmarks,
                onAddBookmark = {
                    homeViewModel.addBookmark(it)
                    Toast.makeText(context, "✅ Added!", Toast.LENGTH_SHORT).show()
                },
                onDeleteBookmark = { homeViewModel.deleteBookmark(it) },
                onClearData = {
                    homeViewModel.clearAllData()
                    Toast.makeText(context, "🗑 Cleared!", Toast.LENGTH_SHORT).show()
                },
                history = homeViewModel.history
            )
        }

        /** Incognito route — completely isolated browser */
        composable("incognito") {
            IncognitoScreen(
                onClose = { navController.popBackStack() }
            )
        }

        /** Browser screen route */
        composable("browser") {
            WebViewScreen(
                initialUrl = browserViewModel.initialUrl,
                savedSession = browserViewModel.savedSession,
                browserViewModel = browserViewModel,
                onGoHome = {
                    activity?.setPortrait()
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onAddToBookmark = { title, url ->
                    val bm = SiteBookmark(
                        name = title.take(20).ifEmpty { url.take(20) },
                        url = url, icon = "📌", color = 0xFFFFB74D
                    )
                    if (homeViewModel.bookmarks.none { it.url == url }) {
                        homeViewModel.addBookmark(bm)
                        Toast.makeText(context, "📌 Bookmarked!", Toast.LENGTH_SHORT).show()
                    }
                },
                onSessionUpdate = { tabs, idx -> browserViewModel.saveSession(tabs, idx) },
                onOpenIncognito = {
                    navController.navigate("incognito")
                }
            )
        }
    }
}