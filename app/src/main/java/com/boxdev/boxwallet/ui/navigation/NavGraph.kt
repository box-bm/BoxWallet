package com.boxdev.boxwallet.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.ActivityNavigator
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.boxdev.boxwallet.ui.screens.analytics.AnalyticsScreen
import com.boxdev.boxwallet.ui.screens.home.HomeScreen
import com.boxdev.boxwallet.ui.screens.settings.BackupScreen
import com.boxdev.boxwallet.ui.screens.settings.ExportScreen
import com.boxdev.boxwallet.ui.screens.settings.SettingsScreen
import com.boxdev.boxwallet.ui.screens.settings.ThemeCustomizationScreen
import com.boxdev.boxwallet.ui.screens.transaction.TransactionScreen

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    HOME(NavRoutes.Home.route, "home", Icons.Default.Home, "Home"),
    ANALYTICS(NavRoutes.Analytics.route, "Summary", Icons.Default.Analytics, "Summary"),
    SETTINGS("settings_graph", "settings", Icons.Default.Settings, "Settings")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.Home.route,
    modifier: Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // Home tab
        composable(NavRoutes.Home.route) {
            HomeScreen(
                onNavigateToTransaction = { transactionId ->
                    navController.navigate(NavRoutes.Transaction.createRoute(transactionId))
                },
            )
        }

        // Transaction screen (child of Home)
        composable(
            route = NavRoutes.Transaction.route,
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong("transactionId")
            TransactionScreen(
                transactionId = if (transactionId == -1L) null else transactionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Analytics tab
        composable(NavRoutes.Analytics.route) {
            AnalyticsScreen()
        }

        // Settings tab with nested navigation
        navigation(
            startDestination = NavRoutes.Settings.route,
            route = "settings_graph"
        ) {
            composable(NavRoutes.Settings.route) {
                SettingsScreen(
                    onNavigateToTheme = {
                        navController.navigate(NavRoutes.ThemeCustomization.route)
                    },
                    onNavigateToBackup = {
                        navController.navigate(NavRoutes.Backup.route)
                    },
                    onNavigateToExport = {
                        navController.navigate(NavRoutes.Export.route)
                    }
                )
            }

            composable(NavRoutes.ThemeCustomization.route) {
                ThemeCustomizationScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.Backup.route) {
                BackupScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.Export.route) {
                ExportScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
