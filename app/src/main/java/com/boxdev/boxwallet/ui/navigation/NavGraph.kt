package com.boxdev.boxwallet.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.AppPane
import com.boxdev.boxwallet.ui.screens.analytics.AnalyticsScreen
import com.boxdev.boxwallet.ui.screens.home.HomeScreen
import com.boxdev.boxwallet.ui.screens.loans.LoanDetailScreen
import com.boxdev.boxwallet.ui.screens.loans.LoansScreen
import com.boxdev.boxwallet.ui.screens.search.SearchScreen
import com.boxdev.boxwallet.ui.screens.settings.BackupScreen
import com.boxdev.boxwallet.ui.screens.settings.CategoriesScreen
import com.boxdev.boxwallet.ui.screens.settings.CurrenciesScreen
import com.boxdev.boxwallet.ui.screens.settings.CurrencyDetailScreen
import com.boxdev.boxwallet.ui.screens.settings.MainCurrencyChangeScreen
import com.boxdev.boxwallet.ui.screens.settings.DataManagementScreen
import com.boxdev.boxwallet.ui.screens.settings.GeneralSettingsScreen
import com.boxdev.boxwallet.ui.screens.settings.SettingsScreen
import com.boxdev.boxwallet.ui.screens.settings.SettingsSection
import com.boxdev.boxwallet.ui.screens.settings.ThemeCustomizationScreen
import com.boxdev.boxwallet.ui.screens.transactions.TransactionsListScreen
import com.boxdev.boxwallet.ui.theme.BoxTheme

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

// Shared axis X for push/pop: slight horizontal slide + fade; [forward] is push, otherwise pop.
private fun sharedAxisIn(forward: Boolean) = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerate),
    initialOffsetX = { if (forward) it / 10 else -it / 10 }
) + fadeIn(animationSpec = tween(TRANSITION_DURATION / 2, delayMillis = 50))

private fun sharedAxisOut(forward: Boolean) = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerate),
    targetOffsetX = { if (forward) -it / 10 else it / 10 }
) + fadeOut(animationSpec = tween(TRANSITION_DURATION / 4))

// Fade-through for lateral tab switches: outgoing fades fast, incoming fades + settles from 96%.
private fun fadeThrough() = fadeIn(animationSpec = tween(TRANSITION_DURATION - 90, delayMillis = 90)) +
    scaleIn(initialScale = 0.96f, animationSpec = tween(TRANSITION_DURATION - 90, delayMillis = 90, easing = EmphasizedDecelerate))
private fun fadeThroughExit() = fadeOut(animationSpec = tween(90))

/** A bottom-nav tab destination: lateral switches use fade-through. */
private fun NavGraphBuilder.tabComposable(route: String, content: @Composable () -> Unit) = composable(
    route,
    enterTransition = { fadeThrough() },
    exitTransition = { fadeThroughExit() },
    popEnterTransition = { fadeThrough() },
    popExitTransition = { fadeThroughExit() },
) { content() }

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier,
    analyticsHelper: AnalyticsHelper,
    onEditTransaction: (Long) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    /** Whether Loans shows a loan in a single pane (in place of its list). */
    onLoanDetailInSinglePaneChange: (Boolean) -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val onNavigateToSearch: () -> Unit = { navController.navigate(NavRoutes.Search.route) { launchSingleTop = true } }
    val openLoan: (Long) -> Unit = { loanId -> navController.navigate(NavRoutes.LoanDetail.createRoute(loanId)) }
    LaunchedEffect(navBackStackEntry) {
        navBackStackEntry?.destination?.route?.let { route ->
            analyticsHelper.logScreenView(route.substringBefore("/"))
        }
    }

    // A settings section opened on one pane moves into the Settings detail pane once two show.
    val twoPane = BoxTheme.layout.twoPane
    val currentRoute = navBackStackEntry?.destination?.route
    LaunchedEffect(twoPane, currentRoute) {
        val section = SettingsSection.toMoveIntoPane(twoPane, currentRoute) ?: return@LaunchedEffect
        val settingsEntry = runCatching { navController.getBackStackEntry(NavRoutes.Settings.route) }.getOrNull()
            ?: return@LaunchedEffect
        settingsEntry.savedStateHandle[SettingsSection.PRESELECT_KEY] = section.name
        navController.popBackStack(NavRoutes.Settings.route, inclusive = false)
    }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.Home.route,
        modifier = modifier,
        enterTransition = { sharedAxisIn(forward = true) },
        exitTransition = { sharedAxisOut(forward = true) },
        popEnterTransition = { sharedAxisIn(forward = false) },
        popExitTransition = { sharedAxisOut(forward = false) },
    ) {
        tabComposable(NavRoutes.Home.route) {
            HomeScreen(
                onSeeAllTransactions = {
                    navController.navigateToTab(NavRoutes.TransactionsList.route)
                },
                onSeeAllLoans = {
                    navController.navigateToTab(NavRoutes.Loans.route)
                },
                onNavigateToLoanDetail = openLoan,
                onEditTransaction = onEditTransaction,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSearch = onNavigateToSearch,
            )
        }

        tabComposable(NavRoutes.TransactionsList.route) {
            TransactionsListScreen(
                onEditTransaction = onEditTransaction,
                onNavigateToSearch = onNavigateToSearch,
            )
        }

        tabComposable(NavRoutes.Loans.route) {
            LoansScreen(
                onNavigateToSearch = onNavigateToSearch,
                onDetailInSinglePaneChange = onLoanDetailInSinglePaneChange,
                // Loans opens a loan in its own detail pane rather than the loan_detail route: log it the same.
                onLoanOpened = { analyticsHelper.logScreenView(NavRoutes.LoanDetail.route.substringBefore("/")) },
            )
        }

        composable(
            route = NavRoutes.LoanDetail.route,
            arguments = listOf(navArgument("loanId") { type = NavType.LongType })
        ) {
            // Opened from Home, Search or Reports: one pane on the window background.
            val margin = BoxTheme.layout.margin
            AppPane(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = margin, top = 8.dp, end = margin, bottom = margin)
            ) {
                LoanDetailScreen(onNavigateBack = { navController.popBackStack() })
            }
        }

        tabComposable(NavRoutes.Analytics.route) {
            AnalyticsScreen(
                onNavigateToSearch = onNavigateToSearch,
                onEditTransaction = onEditTransaction,
                onOpenLoan = openLoan,
                onNavigateToLoans = { navController.navigateToTab(NavRoutes.Loans.route) },
                onNavigateToMovements = { navController.navigateToTab(NavRoutes.TransactionsList.route) },
            )
        }

        composable(
            NavRoutes.Search.route,
            enterTransition = { fadeThrough() },
            exitTransition = { fadeThroughExit() },
            popEnterTransition = { fadeThrough() },
            popExitTransition = { fadeThroughExit() },
        ) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenTransaction = onEditTransaction,
                onOpenLoan = openLoan,
            )
        }

        composable(NavRoutes.Settings.route) { entry ->
            val preselected by entry.savedStateHandle
                .getStateFlow<String?>(SettingsSection.PRESELECT_KEY, null)
                .collectAsState()
            // Wide windows show sections in a detail pane; their outgoing links stay full routes.
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSection = { navController.navigate(it.route) },
                onOpenCurrency = { navController.navigate(NavRoutes.CurrencyDetail.createRoute(it)) },
                onChangeMainCurrency = { navController.navigate(NavRoutes.MainCurrencyChange.createRoute(it)) },
                preselected = preselected?.let(SettingsSection::valueOf),
                onPreselectedConsumed = { entry.savedStateHandle.set<String?>(SettingsSection.PRESELECT_KEY, null) },
            )
        }

        composable(NavRoutes.Currencies.route) {
            CurrenciesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenCurrency = { navController.navigate(NavRoutes.CurrencyDetail.createRoute(it)) },
                onChangeMainCurrency = { navController.navigate(NavRoutes.MainCurrencyChange.createRoute(it)) },
            )
        }

        composable(
            NavRoutes.MainCurrencyChange.route,
            arguments = listOf(androidx.navigation.navArgument("code") { type = androidx.navigation.NavType.StringType }),
        ) {
            MainCurrencyChangeScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            NavRoutes.CurrencyDetail.route,
            arguments = listOf(androidx.navigation.navArgument("code") { type = androidx.navigation.NavType.StringType }),
        ) {
            CurrencyDetailScreen(onNavigateBack = { navController.popBackStack() })
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
            BackupScreen(
                onNavigateBack = { navController.popBackStack() },
                onChangeMainCurrency = { navController.navigate(NavRoutes.MainCurrencyChange.createRoute(it)) },
            )
        }

        composable(NavRoutes.DataManagement.route) {
            DataManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBackup = { navController.navigate(NavRoutes.Backup.route) }
            )
        }
    }
}

/**
 * Switches tabs, keeping one copy of each tab and restoring its saved state. Callers leaving a
 * secondary destination (settings, detail...) pop it first (see [performTabAction]) so it is never
 * saved as a tab's state.
 */
fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
