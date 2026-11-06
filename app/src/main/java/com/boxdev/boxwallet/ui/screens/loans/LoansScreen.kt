@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)

package com.boxdev.boxwallet.ui.screens.loans

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.ConnectedChoiceRow
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.FilterGroup
import com.boxdev.boxwallet.ui.components.LoanCard
import com.boxdev.boxwallet.ui.components.SearchFilterBar
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.WidthClass

/**
 * Loans. Compact: a list that pushes to the detail screen. Medium/expanded (unfolded): a
 * list-detail layout where the selected loan opens beside the list.
 */
@Composable
fun LoansScreen(
    onNavigateToLoanDetail: (Long) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: LoansViewModel = hiltViewModel()
) {
    val widthClass = BoxTheme.widthClass
    val twoPane = widthClass == WidthClass.Expanded
    var selectedLoanId by rememberSaveable { mutableStateOf<Long?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    // Keep a sensible selection in the detail pane.
    LaunchedEffect(twoPane, uiState.filteredLoans) {
        if (twoPane && uiState.filteredLoans.none { it.id == selectedLoanId }) {
            selectedLoanId = uiState.filteredLoans.firstOrNull()?.id
        }
    }

    if (!twoPane) {
        LoansList(
            viewModel = viewModel,
            selectedLoanId = null,
            onLoanClick = onNavigateToLoanDetail,
            onNavigateToSettings = onNavigateToSettings,
        )
        return
    }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.46f)) {
            LoansList(
                viewModel = viewModel,
                selectedLoanId = selectedLoanId,
                onLoanClick = { selectedLoanId = it },
                onNavigateToSettings = onNavigateToSettings,
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .weight(0.54f)
                .fillMaxHeight()
                .padding(top = 8.dp, end = 16.dp, bottom = 16.dp),
        ) {
            val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = selectedLoanId,
                transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
                label = "loanDetailPane",
            ) { id ->
                if (id == null) {
                    EmptyState(
                        icon = Icons.Outlined.TouchApp,
                        title = stringResource(R.string.loans_select_hint),
                        modifier = Modifier.padding(top = 96.dp),
                    )
                } else {
                    LoanDetailScreen(onNavigateBack = null, loanId = id)
                }
            }
        }
    }
}

@Composable
private fun LoansList(
    viewModel: LoansViewModel,
    selectedLoanId: Long?,
    onLoanClick: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter = uiState.filterState
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val categoryMap = uiState.categories.associateBy { it.id }
    val activeFilters = listOf(
        filter.loanTypeFilter != LoanTypeFilter.ALL,
        filter.statusFilter != LoanStatusFilter.ALL,
        filter.categoryId != null,
        filter.sortBy != LoanSortBy.DATE_DESC,
    ).count { it }

    BackHandler(enabled = showFilters) { showFilters = false }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_loans)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchFilterBar(
                query = filter.searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                placeholder = stringResource(R.string.loans_search_placeholder),
                filtersOpen = showFilters,
                onToggleFilters = { showFilters = it },
                activeFilterCount = activeFilters,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        FilterGroup(stringResource(R.string.loans_filter_status)) {
                            ConnectedChoiceRow(
                                options = LoanStatusFilter.entries,
                                selected = filter.statusFilter,
                                onSelect = viewModel::updateStatusFilter,
                                label = {
                                    when (it) {
                                        LoanStatusFilter.ALL -> stringResource(R.string.transactions_filter_all)
                                        LoanStatusFilter.CURRENT -> stringResource(R.string.loans_filter_current)
                                        LoanStatusFilter.OVERDUE -> stringResource(R.string.loans_filter_overdue)
                                        LoanStatusFilter.PAID -> stringResource(R.string.loans_filter_paid)
                                    }
                                },
                            )
                        }
                        FilterGroup(stringResource(R.string.loans_filter_type)) {
                            ConnectedChoiceRow(
                                options = LoanTypeFilter.entries,
                                selected = filter.loanTypeFilter,
                                onSelect = viewModel::updateLoanTypeFilter,
                                label = {
                                    when (it) {
                                        LoanTypeFilter.ALL -> stringResource(R.string.transactions_filter_all)
                                        LoanTypeFilter.INCOME -> stringResource(R.string.transaction_type_income)
                                        LoanTypeFilter.EXPENSE -> stringResource(R.string.transaction_type_expense)
                                    }
                                },
                            )
                        }
                        if (uiState.categories.isNotEmpty()) {
                            FilterGroup(stringResource(R.string.loans_filter_category)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = filter.categoryId == null,
                                        onClick = { viewModel.updateCategoryFilter(null) },
                                        label = { Text(stringResource(R.string.transactions_filter_all)) },
                                        leadingIcon = if (filter.categoryId == null) {
                                            { Icon(Icons.Filled.Check, null, Modifier.width(FilterChipDefaults.IconSize)) }
                                        } else null,
                                    )
                                    uiState.categories.forEach { cat ->
                                        val selected = filter.categoryId == cat.id
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.updateCategoryFilter(cat.id) },
                                            label = { Text(cat.name) },
                                            leadingIcon = if (selected) {
                                                { Icon(Icons.Filled.Check, null, Modifier.width(FilterChipDefaults.IconSize)) }
                                            } else null,
                                        )
                                    }
                                }
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
            }

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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.filteredLoans, key = { it.id }) { loan ->
                        LoanCard(
                            loan = loan,
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
