package com.webwrap.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.webwrap.app.HomeScreen
import com.webwrap.app.WebViewScreen
import com.webwrap.app.viewmodel.BrowserViewModel
import com.webwrap.app.viewmodel.HomeViewModel

/**
 * Main navigation graph for the app.
 * Defines all screen destinations and arguments.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        // Home screen destination
        composable(route = "home") {
            val homeViewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToBrowser = { url ->
                    navController.navigate(
                        NavRoutes.browserRoute(url)
                    ) {
                        launchSingleTop = true
                    }
                },
                onNavigateToIncognito = { url ->
                    navController.navigate(
                        NavRoutes.browserRoute(
                            url = url,
                            incognito = true
                        )
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // Browser screen destination
        composable(
            route = NavRoutes.BROWSER_ROUTE,
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    defaultValue = "https://www.google.com"
                },
                navArgument("incognito") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "https://{path}"
                },
                navDeepLink {
                    uriPattern = "http://{path}"
                }
            )
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments
                ?.getString("url")
                ?: "https://www.google.com"
            val isIncognito = backStackEntry.arguments
                ?.getBoolean("incognito")
                ?: false

            val browserViewModel: BrowserViewModel =
                viewModel()

            WebViewScreen(
                navController = navController,
                viewModel = browserViewModel,
                initialUrl = NavRoutes.decodeUrl(encodedUrl),
                isIncognito = isIncognito,
                onNavigateToHomeScreen = { navController.navigate(NavRoutes.HOME) },
                onGoHome = {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.HOME) {
                            inclusive = false
                        }
                        //launchSingleTop = true
                    }
                }
            )
        }
    }
}