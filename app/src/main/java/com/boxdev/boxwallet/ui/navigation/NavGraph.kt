package com.boxdev.boxwallet.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.boxdev.boxwallet.ui.screens.analytics.AnalyticsScreen
import com.boxdev.boxwallet.ui.screens.home.HomeScreen
import com.boxdev.boxwallet.ui.screens.settings.BackupScreen
import com.boxdev.boxwallet.ui.screens.settings.ExportScreen
import com.boxdev.boxwallet.ui.screens.settings.SettingsScreen
import com.boxdev.boxwallet.ui.screens.settings.ThemeCustomizationScreen
import com.boxdev.boxwallet.ui.screens.transaction.TransactionScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.Home.route) {
            HomeScreen(
                onNavigateToTransaction = { transactionId ->
                    navController.navigate(NavRoutes.Transaction.createRoute(transactionId))
                },
                onNavigateToAnalytics = {
                    navController.navigate(NavRoutes.Analytics.route)
                },
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.Settings.route)
                }
            )
        }

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

        composable(NavRoutes.Analytics.route) {
            AnalyticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
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
