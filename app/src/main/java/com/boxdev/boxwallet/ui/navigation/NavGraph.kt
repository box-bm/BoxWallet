package com.boxdev.boxwallet.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.boxdev.boxwallet.ui.screens.analytics.AnalyticsScreen
import com.boxdev.boxwallet.ui.screens.home.HomeScreen
import com.boxdev.boxwallet.ui.screens.loans.LoanDetailScreen
import com.boxdev.boxwallet.ui.screens.loans.LoansScreen
import com.boxdev.boxwallet.ui.screens.settings.BackupScreen
import com.boxdev.boxwallet.ui.screens.settings.CategoriesScreen
import com.boxdev.boxwallet.ui.screens.settings.DataManagementScreen
import com.boxdev.boxwallet.ui.screens.settings.ExportScreen
import com.boxdev.boxwallet.ui.screens.settings.GeneralSettingsScreen
import com.boxdev.boxwallet.ui.screens.settings.SettingsScreen
import com.boxdev.boxwallet.ui.screens.settings.ThemeCustomizationScreen
import com.boxdev.boxwallet.ui.screens.transactions.TransactionsListScreen

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    HOME(NavRoutes.Home.route, "Home", Icons.Default.Home, "Home"),
    TRANSACTIONS(NavRoutes.TransactionsList.route, "Transactions", Icons.AutoMirrored.Filled.List, "Transactions"),
    LOANS(NavRoutes.Loans.route, "Loans", Icons.AutoMirrored.Filled.List, "Loans")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Home.route,
        modifier = modifier,
    ) {
        composable(NavRoutes.Home.route) {
            HomeScreen(
                onNavigateToAnalytics = { navController.navigate(NavRoutes.Analytics.route) },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onSeeAllTransactions = { navController.navigate(NavRoutes.TransactionsList.route) },
                onNavigateToLoanDetail = { loanId -> navController.navigate(NavRoutes.LoanDetail.createRoute(loanId)) }
            )
        }

        composable(NavRoutes.TransactionsList.route) {
            TransactionsListScreen()
        }

        composable(NavRoutes.Loans.route) {
            LoansScreen(
                onNavigateToLoanDetail = { loanId -> navController.navigate(NavRoutes.LoanDetail.createRoute(loanId)) }
            )
        }

        composable(
            route = NavRoutes.LoanDetail.route,
            arguments = listOf(navArgument("loanId") { type = NavType.LongType })
        ) {
            LoanDetailScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(NavRoutes.Analytics.route) {
            AnalyticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        navigation(
            startDestination = NavRoutes.Settings.route,
            route = "settings_graph"
        ) {
            composable(NavRoutes.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToGeneral = { navController.navigate(NavRoutes.GeneralSettings.route) },
                    onNavigateToCategories = { navController.navigate(NavRoutes.Categories.route) },
                    onNavigateToTheme = { navController.navigate(NavRoutes.ThemeCustomization.route) },
                    onNavigateToBackup = { navController.navigate(NavRoutes.Backup.route) },
                    onNavigateToExport = { navController.navigate(NavRoutes.Export.route) },
                    onNavigateToDataManagement = { navController.navigate(NavRoutes.DataManagement.route) },
                )
            }

            composable(NavRoutes.GeneralSettings.route) {
                GeneralSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.Categories.route) {
                CategoriesScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.ThemeCustomization.route) {
                ThemeCustomizationScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.Backup.route) {
                BackupScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.Export.route) {
                ExportScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.DataManagement.route) {
                DataManagementScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
