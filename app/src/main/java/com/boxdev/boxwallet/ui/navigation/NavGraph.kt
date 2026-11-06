package com.boxdev.boxwallet.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
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
import com.boxdev.boxwallet.ui.screens.search.SearchScreen
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
        Icons.AutoMirrored.Outlined.ReceiptLong,
        Icons.AutoMirrored.Filled.ReceiptLong
    ),
    LOANS(
        NavRoutes.Loans.route,
        R.string.nav_loans,
        Icons.Outlined.Handshake,
        Icons.Filled.Handshake
    ),
    ANALYTICS(
        NavRoutes.Analytics.route,
        R.string.nav_analytics,
        Icons.Outlined.Insights,
        Icons.Filled.Insights
    ),
}

private const val TRANSITION_DURATION = 400

/** Material emphasized-decelerate easing for incoming content; emphasized-accelerate for outgoing. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Shared-axis-X: slight horizontal slide + fade for push/pop
// Shared axis X for push/pop.
private fun defaultEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerate),
    initialOffsetX = { it / 10 }
) + fadeIn(animationSpec = tween(TRANSITION_DURATION / 2, delayMillis = 50))

private fun defaultExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerate),
    targetOffsetX = { -it / 10 }
) + fadeOut(animationSpec = tween(TRANSITION_DURATION / 4))

private fun defaultPopEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerate),
    initialOffsetX = { -it / 10 }
) + fadeIn(animationSpec = tween(TRANSITION_DURATION / 2, delayMillis = 50))

private fun defaultPopExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerate),
    targetOffsetX = { it / 10 }
) + fadeOut(animationSpec = tween(TRANSITION_DURATION / 4))

// Fade-through for lateral tab switches: outgoing fades fast, incoming fades + settles from 96%.
private fun fadeThrough() = fadeIn(animationSpec = tween(TRANSITION_DURATION - 90, delayMillis = 90)) +
    scaleIn(initialScale = 0.96f, animationSpec = tween(TRANSITION_DURATION - 90, delayMillis = 90, easing = EmphasizedDecelerate))
private fun fadeThroughExit() = fadeOut(animationSpec = tween(90))

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
    val onNavigateToSearch: () -> Unit = { navController.navigate(NavRoutes.Search.route) { launchSingleTop = true } }
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
                    navController.navigateToTab(NavRoutes.TransactionsList.route)
                },
                onSeeAllLoans = {
                    navController.navigateToTab(NavRoutes.Loans.route)
                },
                onNavigateToLoanDetail = { loanId ->
                    navController.navigate(
                        NavRoutes.LoanDetail.createRoute(
                            loanId
                        )
                    )
                },
                onEditTransaction = onEditTransaction,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSearch = onNavigateToSearch,
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
                onEditTransaction = onEditTransaction,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSearch = onNavigateToSearch,
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
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSearch = onNavigateToSearch,
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
            AnalyticsScreen(
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSearch = onNavigateToSearch,
                onEditTransaction = onEditTransaction,
                onOpenLoan = { loanId -> navController.navigate(NavRoutes.LoanDetail.createRoute(loanId)) },
                onNavigateToLoans = { navController.navigateToTab(NavRoutes.Loans.route) },
                onNavigateToMovements = { navController.navigateToTab(NavRoutes.TransactionsList.route) },
            )
        }

        composable(NavRoutes.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenTransaction = onEditTransaction,
                onOpenLoan = { loanId -> navController.navigate(NavRoutes.LoanDetail.createRoute(loanId)) },
            )
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

/** Switches bottom-nav tabs, keeping one copy of each tab and restoring its saved state. */
fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
