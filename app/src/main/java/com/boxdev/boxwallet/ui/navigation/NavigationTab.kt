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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import kotlin.math.roundToInt
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
    // A loan open in a single pane fills the Loans tab; the FAB would cover its actions.
    var loanDetailInSinglePane by remember { mutableStateOf(false) }
    val showFab = currentRoute in listOf(
        NavRoutes.Home.route,
        NavRoutes.TransactionsList.route,
        NavRoutes.Loans.route,
    ) && !(currentRoute == NavRoutes.Loans.route && loanDetailInSinglePane)

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
            onLoanDetailInSinglePaneChange = { loanDetailInSinglePane = it },
        ) }
    }

    val rail = layout.nav == NavKind.Rail
    // The rail already pads itself past the display cutout on its side; the content beside it must not again.
    val railInsets = if (rail && !isSecondary(currentRoute)) {
        Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
    } else {
        Modifier
    }
    // Without a bottom bar under them (rail layouts), the tab panes stop above the system navigation
    // bar. Secondary routes have no bottom bar either, but some draw edge to edge behind it
    // (SettingsScaffold), so each of their pane layouts pads itself instead.
    val navBarInsets = if (rail && onTabRoute) {
        Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
    } else {
        Modifier
    }
    // The NavHost keeps one position in the composition whatever the navigation kind: its screens'
    // rememberSaveable state is keyed by that position, so moving it between a rail and a bar layout
    // (a fold or rotation recreates the activity) would silently drop that state.
    Row(modifier = modifier.background(surfaceContainer)) {
        if (rail) {
            val railExpanded = layout.railExpanded
            val showRailLabels = railExpanded || !layout.compactHeight
            val railState = rememberWideNavigationRailState(
                if (railExpanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed
            )
            LaunchedEffect(railExpanded) { if (railExpanded) railState.expand() else railState.collapse() }
            AnimatedVisibility(
                visible = !isSecondary(currentRoute),
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
            BoxWithConstraints(Modifier.fillMaxHeight()) {
                // The rail fills the window height and scrolls only when its FAB and destinations don't
                // fit (short phone landscape), so every destination stays reachable. Inside a scroll it
                // would get an infinite height and crash, so it always gets a bounded one: the window's,
                // or, when taller, what its content measured (its items stack from the top, so their
                // positions don't depend on the rail height and this settles in one pass).
                val density = LocalDensity.current
                val railBottomInset = WideNavigationRailDefaults.windowInsets.getBottom(density)
                val railMeasure = remember { RailContentMeasure() }
                val railHeight = maxOf(maxHeight, with(density) { railMeasure.contentHeightPx.toDp() })
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    WideNavigationRail(
                        state = railState,
                        header = {
                            // The rail places its header at x = 0, so the FAB carries the rail's item inset itself.
                            // Fully qualified: unqualified, it would resolve to the shell Row's RowScope extension,
                            // but the header is not a child of that Row.
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showFab,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut(),
                            ) {
                                // Icon-only items sit nearer the rail start; the small FAB lines up with them.
                                val fabStart = if (showRailLabels) 20.dp else 8.dp
                                Box(Modifier.padding(start = fabStart, top = 8.dp)) {
                                    if (railExpanded) {
                                        ExtendedFloatingActionButton(
                                            onClick = { primaryAction(!fabExpanded) },
                                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                            text = { Text(stringResource(R.string.fab_new)) },
                                        )
                                    } else if (layout.compactHeight) {
                                        SmallFloatingActionButton(onClick = { primaryAction(!fabExpanded) }) {
                                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_new))
                                        }
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
                            }
                        },
                        colors = WideNavigationRailDefaults.colors(containerColor = surfaceContainer),
                        windowInsets = WideNavigationRailDefaults.windowInsets,
                        modifier = Modifier
                            .height(railHeight)
                            .onGloballyPositioned { railMeasure.rail = it; railMeasure.update(railBottomInset) },
                    ) {
                        Destination.entries.forEach { destination ->
                            val selected = selectedTab == destination
                            WideNavigationRailItem(
                                selected = selected,
                                onClick = { onTabClick(destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) destination.activeIcon else destination.icon,
                                        // With the label hidden, the icon carries the destination's name.
                                        contentDescription = if (showRailLabels) null else stringResource(destination.labelRes),
                                    )
                                },
                                // Phone landscape: icons only, to keep the rail short.
                                label = if (showRailLabels) {
                                    { Text(stringResource(destination.labelRes)) }
                                } else null,
                                railExpanded = railExpanded,
                                modifier = if (destination == Destination.entries.last()) {
                                    Modifier.onGloballyPositioned {
                                        railMeasure.lastItem = it
                                        railMeasure.update(railBottomInset)
                                    }
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
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
            Box(Modifier.padding(padding).then(railInsets).then(navBarInsets)) {
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

/** Tracks how tall the rail's content is: the bottom of its last item plus the rail's bottom inset. */
private class RailContentMeasure {
    var rail: LayoutCoordinates? = null
    var lastItem: LayoutCoordinates? = null
    var contentHeightPx by mutableIntStateOf(0)

    fun update(bottomInsetPx: Int) {
        val rail = rail?.takeIf { it.isAttached } ?: return
        val item = lastItem?.takeIf { it.isAttached } ?: return
        val itemBottom = rail.localPositionOf(item, Offset.Zero).y + item.size.height
        contentHeightPx = itemBottom.roundToInt() + bottomInsetPx
    }
}
