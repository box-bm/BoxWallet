package com.boxdev.boxwallet.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.AreaChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.screens.analytics.AnalyticsScreen
import com.boxdev.boxwallet.ui.screens.home.HomeScreen
import com.boxdev.boxwallet.ui.screens.loans.LoanDetailScreen
import com.boxdev.boxwallet.ui.screens.loans.LoansScreen
import com.boxdev.boxwallet.ui.screens.settings.BackupScreen
import com.boxdev.boxwallet.ui.screens.settings.CategoriesScreen
import com.boxdev.boxwallet.ui.screens.settings.DataManagementScreen
import com.boxdev.boxwallet.ui.screens.settings.GeneralSettingsScreen
import com.boxdev.boxwallet.ui.screens.settings.SettingsScreen
import com.boxdev.boxwallet.ui.screens.settings.ThemeCustomizationScreen
import com.boxdev.boxwallet.ui.screens.transactions.TransactionsListScreen

enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val activeIcon: ImageVector,
) {
    HOME(NavRoutes.Home.route, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    TRANSACTIONS(
        NavRoutes.TransactionsList.route,
        R.string.nav_transactions,
        Icons.Outlined.Receipt,
        Icons.Filled.Receipt
    ),
    LOANS(
        NavRoutes.Loans.route,
        R.string.nav_loans,
        Icons.Outlined.Payments,
        Icons.Filled.Payments
    ),
    ANALYTICS(
        NavRoutes.Analytics.route,
        R.string.nav_analytics,
        Icons.Outlined.AreaChart,
        Icons.Filled.AreaChart
    ),
}

private const val TRANSITION_DURATION = 300

// Shared-axis-X: slight horizontal slide + fade for push/pop
private fun defaultEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION),
    initialOffsetX = { it / 4 }
) + fadeIn(animationSpec = tween(TRANSITION_DURATION))

private fun defaultExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION),
    targetOffsetX = { -it / 4 }
) + fadeOut(animationSpec = tween(TRANSITION_DURATION))

private fun defaultPopEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION),
    initialOffsetX = { -it / 4 }
) + fadeIn(animationSpec = tween(TRANSITION_DURATION))

private fun defaultPopExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION),
    targetOffsetX = { it / 4 }
) + fadeOut(animationSpec = tween(TRANSITION_DURATION))

// Fade-through for lateral tab switches
private fun fadeThrough() = fadeIn(animationSpec = tween(TRANSITION_DURATION))
private fun fadeThroughExit() = fadeOut(animationSpec = tween(TRANSITION_DURATION / 2))

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier,
    analyticsHelper: AnalyticsHelper,
    onAddTransaction: () -> Unit = {},
    onEditTransaction: (Long) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        navBackStackEntry?.destination?.route?.let { route ->
            analyticsHelper.logScreenView(route.substringBefore("/"))
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.Home.route,
        modifier = modifier,
        enterTransition = { defaultEnterTransition() },
        exitTransition = { defaultExitTransition() },
        popEnterTransition = { defaultPopEnterTransition() },
        popExitTransition = { defaultPopExitTransition() },
    ) {
        // Tab destinations use fade-through
        composable(
            NavRoutes.Home.route,
            enterTransition = { fadeThrough() },
            exitTransition = { fadeThroughExit() },
            popEnterTransition = { fadeThrough() },
            popExitTransition = { fadeThroughExit() },
        ) {
            HomeScreen(
                onSeeAllTransactions = {
                    navController.navigate(NavRoutes.TransactionsList.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSeeAllLoans = {
                    navController.navigate(NavRoutes.Loans.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToLoanDetail = { loanId ->
                    navController.navigate(
                        NavRoutes.LoanDetail.createRoute(
                            loanId
                        )
                    )
                },
                onEditTransaction = onEditTransaction,
                onNavigateToSettings = onNavigateToSettings
            )
        }

        composable(
            NavRoutes.TransactionsList.route,
            enterTransition = { fadeThrough() },
            exitTransition = { fadeThroughExit() },
            popEnterTransition = { fadeThrough() },
            popExitTransition = { fadeThroughExit() },
        ) {
            TransactionsListScreen(
                onEditTransaction = onEditTransaction
            )
        }

        composable(
            NavRoutes.Loans.route,
            enterTransition = { fadeThrough() },
            exitTransition = { fadeThroughExit() },
            popEnterTransition = { fadeThrough() },
            popExitTransition = { fadeThroughExit() },
        ) {
            LoansScreen(
                onNavigateToLoanDetail = { loanId ->
                    navController.navigate(
                        NavRoutes.LoanDetail.createRoute(
                            loanId
                        )
                    )
                }
            )
        }

        composable(
            route = NavRoutes.LoanDetail.route,
            arguments = listOf(navArgument("loanId") { type = NavType.LongType })
        ) {
            LoanDetailScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            NavRoutes.Analytics.route,
            enterTransition = { fadeThrough() },
            exitTransition = { fadeThroughExit() },
            popEnterTransition = { fadeThrough() },
            popExitTransition = { fadeThroughExit() },
        ) {
            AnalyticsScreen()
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGeneral = { navController.navigate(NavRoutes.GeneralSettings.route) },
                onNavigateToCategories = { navController.navigate(NavRoutes.Categories.route) },
                onNavigateToTheme = { navController.navigate(NavRoutes.ThemeCustomization.route) },
                onNavigateToBackup = { navController.navigate(NavRoutes.Backup.route) },
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

        composable(NavRoutes.DataManagement.route) {
            DataManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBackup = { navController.navigate(NavRoutes.Backup.route) }
            )
        }
    }
}
