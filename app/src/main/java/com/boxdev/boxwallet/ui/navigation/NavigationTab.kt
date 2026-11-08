@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.navigation

import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.ui.components.LoanSheet
import com.boxdev.boxwallet.ui.components.TransactionSheet
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.NavKind

/**
 * App shell. Compact windows (phones, folded foldables) get a short navigation bar and a FAB menu;
 * wider windows (unfolded foldables, tablets) get a wide navigation rail whose header holds the
 * "New" action, and the rail hides on secondary destinations (settings, details, search).
 */
@Composable
fun NavigationTab(
    analyticsHelper: AnalyticsHelper,
    modifier: Modifier = Modifier,
    /** Main currency to open the change screen for right away (after an onboarding restore). */
    pendingCurrencyChange: String? = null,
    onPendingCurrencyChangeOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    LaunchedEffect(pendingCurrencyChange) {
        pendingCurrencyChange?.let {
            navController.currentBackStackEntryFlow.first() // The graph is set.
            navController.navigate(NavRoutes.MainCurrencyChange.createRoute(it))
            onPendingCurrencyChangeOpened()
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val layout = BoxTheme.layout
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    val selectedTab = Destination.entries.firstOrNull { d ->
        navBackStackEntry?.destination?.hierarchy?.any { it.route == d.route } == true
    } ?: when (currentRoute?.substringBefore("/")) {
        "loan_detail" -> Destination.LOANS
        else -> null
    }
    val onTabRoute = currentRoute in Destination.entries.map { it.route }
    val showFab = currentRoute in listOf(
        NavRoutes.Home.route,
        NavRoutes.TransactionsList.route,
        NavRoutes.Loans.route,
    )

    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    val tabReselect = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }

    /**
     * Tapping a tab switches to it; tapping the tab already showing first returns to its root (for
     * example from Loan detail back to Loans) and, once at the root, scrolls that screen to the top.
     */
    val onTabClick: (Destination) -> Unit = { destination ->
        val tabsInBackStack = Destination.entries.map { it.route }
            .filter { runCatching { navController.getBackStackEntry(it) }.isSuccess }
            .toSet()
        navController.performTabAction(
            action = resolveTabClick(destination.route, currentRoute, tabsInBackStack),
            onScrollToTop = { tabReselect.tryEmit(it) },
        )
    }
    var showTransactionSheet by rememberSaveable { mutableStateOf(false) }
    var showLoanSheet by rememberSaveable { mutableStateOf(false) }
    var selectedTransactionId by rememberSaveable { mutableStateOf<Long?>(null) }

    val openTransactionSheet = {
        fabExpanded = false
        selectedTransactionId = null
        showTransactionSheet = true
    }
    val openLoanSheet = {
        fabExpanded = false
        showLoanSheet = true
    }
    /** The primary action is contextual: Records adds a movement, Loans adds a loan, Home asks. */
    val primaryAction: ((Boolean) -> Unit) = { checked ->
        when (currentRoute) {
            NavRoutes.TransactionsList.route -> openTransactionSheet()
            NavRoutes.Loans.route -> openLoanSheet()
            else -> fabExpanded = checked
        }
    }

    LaunchedEffect(currentRoute) { fabExpanded = false }

    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        CompositionLocalProvider(LocalTabReselect provides tabReselect) { NavGraph(
            navController = navController,
            modifier = contentModifier,
            analyticsHelper = analyticsHelper,
            onEditTransaction = { id ->
                selectedTransactionId = id
                showTransactionSheet = true
            },
            onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
        ) }
    }

    val rail = layout.nav == NavKind.Rail
    // The rail already pads itself past the display cutout on its side; the content beside it must not again.
    val railInsets = if (rail && !isSecondary(currentRoute)) {
        Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
    } else {
        Modifier
    }
    // The NavHost keeps one position in the composition whatever the navigation kind: its screens'
    // rememberSaveable state is keyed by that position, so moving it between a rail and a bar layout
    // (a fold or rotation recreates the activity) would silently drop that state.
    Row(modifier = modifier.background(surfaceContainer)) {
        if (rail) {
            val railExpanded = layout.railExpanded
            val railState = rememberWideNavigationRailState(
                if (railExpanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed
            )
            LaunchedEffect(railExpanded) { if (railExpanded) railState.expand() else railState.collapse() }
            AnimatedVisibility(
                visible = !isSecondary(currentRoute),
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
            WideNavigationRail(
                state = railState,
                header = {
                    // The rail places its header at x = 0, so the FAB carries the rail's item inset itself.
                    Box(Modifier.padding(start = 20.dp, top = 8.dp)) {
                        if (railExpanded) {
                            ExtendedFloatingActionButton(
                                onClick = { primaryAction(!fabExpanded) },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text(stringResource(R.string.fab_new)) },
                            )
                        } else {
                            FloatingActionButton(onClick = { primaryAction(!fabExpanded) }) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_new))
                            }
                        }
                        DropdownMenu(expanded = fabExpanded, onDismissRequest = { fabExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fab_add_transaction)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null) },
                                onClick = openTransactionSheet,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fab_add_loan)) },
                                leadingIcon = { Icon(Icons.Outlined.Handshake, contentDescription = null) },
                                onClick = openLoanSheet,
                            )
                        }
                    }
                },
                colors = WideNavigationRailDefaults.colors(containerColor = surfaceContainer),
                windowInsets = WideNavigationRailDefaults.windowInsets,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Destination.entries.forEach { destination ->
                    val selected = selectedTab == destination
                    WideNavigationRailItem(
                        selected = selected,
                        onClick = { onTabClick(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.activeIcon else destination.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                        railExpanded = railExpanded,
                    )
                }
            }
            }
        }
        Scaffold(
            containerColor = surfaceContainer,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.weight(1f),
            bottomBar = {
                if (!rail) {
                    AnimatedVisibility(visible = onTabRoute, enter = fadeIn(), exit = fadeOut()) {
                        ShortNavigationBar(containerColor = surfaceContainer) {
                            Destination.entries.forEach { destination ->
                                val selected = selectedTab == destination
                                ShortNavigationBarItem(
                                    selected = selected,
                                    onClick = { onTabClick(destination) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) destination.activeIcon else destination.icon,
                                            contentDescription = null,
                                        )
                                    },
                                    label = { Text(stringResource(destination.labelRes)) },
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!rail) {
                    AnimatedVisibility(
                        visible = showFab,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
                        FloatingActionButtonMenu(
                            expanded = fabExpanded,
                            modifier = Modifier.semantics { traversalIndex = -1f },
                            button = {
                                ToggleFloatingActionButton(
                                    modifier = Modifier.animateFloatingActionButton(
                                        visible = showFab,
                                        alignment = Alignment.BottomEnd,
                                    ),
                                    checked = fabExpanded,
                                    onCheckedChange = primaryAction,
                                ) {
                                    val icon = if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = stringResource(R.string.fab_new),
                                        modifier = Modifier.animateIcon({ checkedProgress }),
                                    )
                                }
                            },
                        ) {
                            FloatingActionButtonMenuItem(
                                onClick = openTransactionSheet,
                                text = { Text(stringResource(R.string.fab_add_transaction)) },
                                icon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null) },
                            )
                            FloatingActionButtonMenuItem(
                                onClick = openLoanSheet,
                                text = { Text(stringResource(R.string.fab_add_loan)) },
                                icon = { Icon(Icons.Outlined.Handshake, contentDescription = null) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).then(railInsets)) {
                content(Modifier)
                if (fabExpanded && !rail) {
                    // Tap outside the open FAB menu to close it.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { fabExpanded = false }
                    )
                }
            }
        }
    }

    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    if (showTransactionSheet) {
        TransactionSheet(
            transactionId = selectedTransactionId,
            onDismiss = {
                showTransactionSheet = false
                selectedTransactionId = null
            },
        )
    }

    if (showLoanSheet) {
        LoanSheet(
            loanId = null,
            onDismiss = { showLoanSheet = false },
        )
    }
}
