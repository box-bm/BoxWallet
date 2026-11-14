@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3AdaptiveApi::class
)

package com.boxdev.boxwallet.ui.screens.loans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.LoanStatus
import com.boxdev.boxwallet.domain.model.LoanTotals
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.ui.components.AmountKind
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.AppPane
import com.boxdev.boxwallet.ui.components.PanePlaceholder
import com.boxdev.boxwallet.ui.components.AppSearchBar
import com.boxdev.boxwallet.ui.components.SearchBarSpan
import com.boxdev.boxwallet.ui.components.CategoryFilterChips
import com.boxdev.boxwallet.ui.components.CollapsibleFilterPanel
import com.boxdev.boxwallet.ui.components.ConnectedChoiceRow
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.FilterGroup
import com.boxdev.boxwallet.ui.components.FilterToggleAction
import com.boxdev.boxwallet.ui.components.LoanCard
import com.boxdev.boxwallet.ui.components.LoanRow
import com.boxdev.boxwallet.ui.components.mainMoney
import com.boxdev.boxwallet.ui.navigation.NavRoutes
import com.boxdev.boxwallet.ui.navigation.TabReselectEffect
import com.boxdev.boxwallet.ui.theme.BoxTheme
import kotlinx.coroutines.launch

/**
 * Loans as an M3 list-detail. Single pane: the list, and a tapped loan replaces it with a back
 * button. Two panes (expanded): the list beside the selected loan, or a hint while none is selected.
 * The navigator keeps the selection across folding and rotating.
 */
@Composable
fun LoansScreen(
    onNavigateToSearch: () -> Unit = {},
    /** Called with true while a loan fills the screen in a single pane, false otherwise. */
    onDetailInSinglePaneChange: (Boolean) -> Unit = {},
    /** Called each time a loan opens in the detail pane. */
    onLoanOpened: () -> Unit = {},
    onEditLoan: (Long) -> Unit = {},
    viewModel: LoansViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter = uiState.filterState
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val activeFilters = listOf(
        filter.loanTypeFilter != null,
        filter.statusFilter != null,
        filter.categoryId != null,
        filter.sortBy != LoanSortBy.DATE_DESC,
    ).count { it }

    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).copy(
        maxHorizontalPartitions = if (BoxTheme.layout.twoPane) 2 else 1,
        horizontalPartitionSpacerSize = 24.dp,
        defaultPanePreferredWidth = LIST_PANE_WIDTH,
    )
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>(scaffoldDirective = directive)
    val scope = rememberCoroutineScope()
    val listShown = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val detailShown = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val bothShown = listShown && detailShown
    val selectedLoanId = navigator.currentDestination?.contentKey

    val detailInSinglePane = detailShown && !listShown
    val detailInSinglePaneChange by rememberUpdatedState(onDetailInSinglePaneChange)
    DisposableEffect(detailInSinglePane) {
        detailInSinglePaneChange(detailInSinglePane)
        onDispose { detailInSinglePaneChange(false) }
    }
    val loanOpened by rememberUpdatedState(onLoanOpened)
    LaunchedEffect(selectedLoanId) { if (selectedLoanId != null) loanOpened() }

    // A deleted loan leaves the detail: back to the list (two panes: the hint). Filters don't clear it.
    LaunchedEffect(selectedLoanId, uiState.loans, uiState.isLoading) {
        if (!uiState.isLoading && selectedLoanId != null && uiState.loans.none { it.id == selectedLoanId }) {
            // In the screen scope: going back changes this effect's key, which would cancel the pane
            // animation midway and leave the old detail drawn over the list.
            scope.launch { navigator.navigateBack(BackNavigationBehavior.PopUntilContentChange) }
        }
    }

    val listState = rememberLazyListState()
    TabReselectEffect(NavRoutes.Loans.route) {
        if (!listShown) navigator.navigateBack() else listState.animateScrollToItem(0)
    }

    BackHandler(enabled = showFilters && listShown) { showFilters = false }

    val margin = BoxTheme.layout.margin
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            // A single-pane detail shows its own app bar instead.
            if (listShown) {
                AppSearchBar(
                    onClick = onNavigateToSearch,
                    // The list pane is 360dp including its start margin.
                    span = if (bothShown) SearchBarSpan.Fixed(LIST_PANE_WIDTH - margin) else SearchBarSpan.Full,
                    trailing = {
                        FilterToggleAction(
                            filtersOpen = showFilters,
                            onToggleFilters = { showFilters = it },
                            activeFilterCount = activeFilters,
                        )
                    },
                )
            }
        },
    ) { padding ->
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            listPane = {
                AnimatedPane {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = margin, end = if (bothShown) 0.dp else margin)
                    ) {
                        LoansList(
                            viewModel = viewModel,
                            showFilters = showFilters,
                            activeFilters = activeFilters,
                            listState = listState,
                            selectedLoanId = if (bothShown) selectedLoanId else null,
                            onLoanClick = { id ->
                                if (id != selectedLoanId) {
                                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) }
                                }
                            },
                        )
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    // Without the search bar above it, the pane starts below the status bar.
                    val top = if (listShown) Modifier else {
                        Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp)
                    }
                    val paneModifier = Modifier
                        .fillMaxSize()
                        .then(top)
                        // On its own the detail brings its 16dp content padding, so only the rest of the margin is added.
                        .padding(
                            start = if (bothShown) 0.dp else margin - 16.dp,
                            end = if (bothShown) margin else margin - 16.dp,
                            bottom = if (bothShown) margin else 0.dp,
                        )
                    val detail: @Composable () -> Unit = {
                        val id = selectedLoanId
                        if (id == null) {
                            PanePlaceholder(icon = Icons.Outlined.TouchApp, title = stringResource(R.string.loans_select_hint))
                        } else {
                            LoanDetailScreen(
                                loanId = id,
                                onEditLoan = onEditLoan,
                                onNavigateBack = if (bothShown) null else {
                                    { scope.launch { navigator.navigateBack() } }
                                },
                            )
                        }
                    }
                    // Beside the list the detail is the contained pane; on its own it sits on the surface body.
                    if (bothShown) AppPane(paneModifier) { detail() } else Box(paneModifier) { detail() }
                }
            },
        )
    }
}

@Composable
private fun LoansList(
    viewModel: LoansViewModel,
    showFilters: Boolean,
    activeFilters: Int,
    listState: LazyListState,
    selectedLoanId: Long?,
    onLoanClick: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter = uiState.filterState
    val categoryMap = remember(uiState.categories) { uiState.categories.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        CollapsibleFilterPanel(visible = showFilters) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                FilterGroup(stringResource(R.string.loans_filter_status)) {
                    ConnectedChoiceRow(
                        options = listOf(null, LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.PAID),
                        selected = filter.statusFilter,
                        onSelect = viewModel::updateStatusFilter,
                        label = {
                            when (it) {
                                null -> stringResource(R.string.transactions_filter_all)
                                LoanStatus.ACTIVE -> stringResource(R.string.loans_filter_current)
                                LoanStatus.OVERDUE -> stringResource(R.string.loans_filter_overdue)
                                LoanStatus.PAID -> stringResource(R.string.loans_filter_paid)
                            }
                        },
                    )
                }
                FilterGroup(stringResource(R.string.loans_filter_type)) {
                    ConnectedChoiceRow(
                        options = listOf(null, LoanType.INCOME, LoanType.EXPENSE),
                        selected = filter.loanTypeFilter,
                        onSelect = viewModel::updateLoanTypeFilter,
                        label = {
                            when (it) {
                                null -> stringResource(R.string.transactions_filter_all)
                                LoanType.INCOME -> stringResource(R.string.transaction_type_income)
                                LoanType.EXPENSE -> stringResource(R.string.transaction_type_expense)
                            }
                        },
                    )
                }
                if (uiState.categories.isNotEmpty()) {
                    FilterGroup(stringResource(R.string.loans_filter_category)) {
                        CategoryFilterChips(uiState.categories, filter.categoryId, viewModel::updateCategoryFilter)
                    }
                }
                FilterGroup(stringResource(R.string.loans_filter_sort_by)) {
                    ConnectedChoiceRow(
                        options = listOf(LoanSortBy.DATE_DESC, LoanSortBy.DATE_ASC, LoanSortBy.AMOUNT_DESC, LoanSortBy.AMOUNT_ASC),
                        selected = filter.sortBy,
                        onSelect = viewModel::updateSortBy,
                        label = {
                            when (it) {
                                LoanSortBy.DATE_DESC -> stringResource(R.string.transactions_filter_latest)
                                LoanSortBy.DATE_ASC -> stringResource(R.string.transactions_filter_oldest)
                                LoanSortBy.AMOUNT_DESC -> stringResource(R.string.transactions_filter_highest)
                                LoanSortBy.AMOUNT_ASC -> stringResource(R.string.loans_filter_lowest)
                            }
                        },
                    )
                }
                OutlinedButton(
                    onClick = viewModel::clearFilters,
                    enabled = activeFilters > 0,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.action_clear_all)) }
            }
        }

        var completedOpen by rememberSaveable { mutableStateOf(false) }
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

            uiState.loans.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Payments,
                title = stringResource(R.string.loans_no_loans),
                message = stringResource(R.string.loans_tap_to_add),
                modifier = Modifier.padding(top = 48.dp),
            )

            uiState.filteredLoans.isEmpty() -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.loans_no_results),
                modifier = Modifier.padding(top = 48.dp),
                action = {
                    OutlinedButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.action_clear_all)) }
                },
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val totals = uiState.totals
                if (totals.toPay > 0 || totals.toCollect > 0) {
                    item(key = "totals") { LoanTotalsHeader(totals, Modifier.padding(bottom = 8.dp)) }
                }
                val (completed, active) = uiState.filteredLoans.partition { it.isComplete }
                items(active, key = { it.id }) { loan ->
                    LoanCard(
                        loan = loan,
                        category = loan.categoryId?.let { categoryMap[it] },
                        selected = loan.id == selectedLoanId,
                        onClick = { onLoanClick(loan.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
                // Paid-off loans fold away so open ones keep the room; open when nothing else is shown.
                if (completed.isNotEmpty()) {
                    item(key = "completed_header") {
                        TextButton(
                            onClick = { completedOpen = !completedOpen },
                            modifier = Modifier.padding(top = 8.dp).animateItem(),
                        ) {
                            Text(stringResource(R.string.loans_completed, completed.size))
                            Icon(
                                if (completedOpen || active.isEmpty()) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                            )
                        }
                    }
                    if (completedOpen || active.isEmpty()) {
                        itemsIndexed(completed, key = { _, l -> "done_${l.id}" }) { index, loan ->
                            LoanRow(
                                loan = loan,
                                index = index,
                                count = completed.size,
                                category = loan.categoryId?.let { categoryMap[it] },
                                selected = loan.id == selectedLoanId,
                                onClick = { onLoanClick(loan.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * What is still outstanding across all loans, by direction: never netted into one number.
 * Two connected tiles, so the pair reads as one contained group.
 */
@Composable
private fun LoanTotalsHeader(totals: LoanTotals, modifier: Modifier = Modifier) {
    val outer = 28.dp
    val inner = 4.dp
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        listOf(
            Triple(R.string.loans_summary_to_pay, totals.toPay, AmountKind.Expense),
            Triple(R.string.loans_summary_to_collect, totals.toCollect, AmountKind.Income),
        ).forEachIndexed { index, (label, amount, kind) ->
            val shape = if (index == 0) {
                RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
            } else {
                RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
            }
            Surface(color = BoxTheme.cardColor, shape = shape, modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(mainMoney(amount), kind = kind, signed = false, style = BoxTheme.moneyType.large, autoSize = true)
                }
            }
        }
    }
}

/** Width the list-detail scaffold gives the loans list on two-pane windows. */
private val LIST_PANE_WIDTH = 360.dp
