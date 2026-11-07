@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)

package com.boxdev.boxwallet.ui.screens.transactions

import android.text.format.DateUtils

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.boxdev.boxwallet.ui.navigation.NavRoutes
import com.boxdev.boxwallet.ui.navigation.TabReselectEffect
import com.boxdev.boxwallet.ui.navigation.expand
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.ui.components.AmountKind
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.ConnectedChoiceRow
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.FilterGroup
import com.boxdev.boxwallet.ui.components.FilterToggleAction
import com.boxdev.boxwallet.ui.components.SearchAction
import com.boxdev.boxwallet.ui.components.TransactionItem
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.isWide
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TransactionsListScreen(
    onEditTransaction: (Long) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: TransactionsListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter = uiState.filterState
    val categoryMap = remember(uiState.availableCategories) { uiState.availableCategories.associateBy { it.id } }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val wide = BoxTheme.widthClass.isWide
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    TabReselectEffect(NavRoutes.TransactionsList.route) {
        scrollBehavior.expand()
        listState.animateScrollToItem(0)
    }
    val activeFilters = listOf(
        filter.transactionType != TransactionTypeFilter.ALL,
        filter.categoryId != null,
        filter.startDate != null || filter.endDate != null,
        filter.sortBy != SortBy.DATE_DESC,
        filter.showOnlyRecurrent,
    ).count { it }

    BackHandler(enabled = showFilters && !wide) { showFilters = false }

    val filterPanel: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            FilterGroup(stringResource(R.string.transactions_filter_type)) {
                ConnectedChoiceRow(
                    options = TransactionTypeFilter.entries,
                    selected = filter.transactionType,
                    onSelect = viewModel::updateTypeFilter,
                    label = {
                        when (it) {
                            TransactionTypeFilter.ALL -> stringResource(R.string.transactions_filter_all)
                            TransactionTypeFilter.INCOME -> stringResource(R.string.transaction_type_income)
                            TransactionTypeFilter.EXPENSE -> stringResource(R.string.transaction_type_expense)
                        }
                    },
                )
            }
            if (uiState.availableCategories.isNotEmpty()) {
                FilterGroup(stringResource(R.string.transactions_filter_category)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        FilterChip(
                            selected = filter.categoryId == null,
                            onClick = { viewModel.updateCategoryFilter(null) },
                            label = { Text(stringResource(R.string.transactions_filter_all)) },
                            leadingIcon = if (filter.categoryId == null) {
                                { Icon(Icons.Filled.Check, null, Modifier.width(FilterChipDefaults.IconSize)) }
                            } else null,
                        )
                        uiState.availableCategories.forEach { cat ->
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
            FilterGroup(stringResource(R.string.transactions_filter_sort)) {
                ConnectedChoiceRow(
                    options = listOf(SortBy.DATE_DESC, SortBy.DATE_ASC, SortBy.AMOUNT_DESC, SortBy.AMOUNT_ASC),
                    selected = filter.sortBy,
                    onSelect = viewModel::updateSortBy,
                    label = {
                        when (it) {
                            SortBy.DATE_DESC -> stringResource(R.string.transactions_filter_latest)
                            SortBy.DATE_ASC -> stringResource(R.string.transactions_filter_oldest)
                            SortBy.AMOUNT_DESC -> stringResource(R.string.transactions_filter_highest)
                            SortBy.AMOUNT_ASC -> stringResource(R.string.transactions_filter_lowest)
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = filter.showOnlyRecurrent,
                    onClick = { viewModel.updateRecurrentFilter(!filter.showOnlyRecurrent) },
                    label = { Text(stringResource(R.string.transactions_filter_recurring_only)) },
                    leadingIcon = if (filter.showOnlyRecurrent) {
                        { Icon(Icons.Filled.Check, null, Modifier.width(FilterChipDefaults.IconSize)) }
                    } else null,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = viewModel::clearFilters, enabled = activeFilters > 0) {
                    Text(stringResource(R.string.action_clear_all))
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_transactions)) },
                subtitle = if (uiState.transactions.isNotEmpty()) {
                    {
                        Text(
                            pluralStringResource(
                                R.plurals.records_count,
                                uiState.filteredTransactions.size,
                                uiState.filteredTransactions.size,
                            )
                        )
                    }
                } else null,
                actions = {
                    SearchAction(onClick = onNavigateToSearch)
                    if (!wide) {
                        FilterToggleAction(
                            filtersOpen = showFilters,
                            onToggleFilters = { showFilters = it },
                            activeFilterCount = activeFilters,
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (wide) {
                // Unfolded: filters stay open as a side pane.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .padding(start = 16.dp, bottom = 16.dp),
                ) {
                    filterPanel(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                if (!wide) {
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
                            filterPanel(Modifier.padding(16.dp))
                        }
                    }
                }

                when {
                    uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }

                    uiState.transactions.isEmpty() -> EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = stringResource(R.string.transactions_no_transactions),
                        message = stringResource(R.string.home_no_transactions_hint),
                        modifier = Modifier.padding(top = 48.dp),
                    )

                    uiState.filteredTransactions.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.transactions_no_results),
                        modifier = Modifier.padding(top = 48.dp),
                        action = {
                            OutlinedButton(onClick = viewModel::clearFilters) {
                                Text(stringResource(R.string.action_clear_all))
                            }
                        },
                    )

                    else -> TransactionsByDay(
                        uiState = uiState,
                        listState = listState,
                        categoryMap = categoryMap,
                        onEditTransaction = onEditTransaction,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionsByDay(
    uiState: TransactionsListUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    categoryMap: Map<Long, Category>,
    onEditTransaction: (Long) -> Unit,
) {
    val sortedByDate = uiState.filterState.sortBy == SortBy.DATE_DESC || uiState.filterState.sortBy == SortBy.DATE_ASC
    val groups: List<Pair<LocalDate?, List<Transaction>>> = remember(uiState.filteredTransactions, sortedByDate) {
        if (sortedByDate) {
            // Keep the view model's order (latest or oldest first) while grouping by day.
            uiState.filteredTransactions.groupBy { it.date.toLocalDate() }.map { (d, list) -> d to list }
        } else {
            // Sorted by amount: one flat group, each row shows its own date.
            listOf(null to uiState.filteredTransactions)
        }
    }
    val today = remember { LocalDate.now() }
    val headerFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
    ) {
        groups.forEach { (date, transactions) ->
            if (date != null) {
                stickyHeader(key = "h_$date") {
                    val net = transactions.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (date) {
                                    today -> stringResource(R.string.date_today)
                                    today.minusDays(1) -> stringResource(R.string.date_yesterday)
                                    // The year only appears for days outside the current year.
                                    else -> if (date.year == today.year) {
                                        DateUtils.formatDateTime(
                                            context,
                                            date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR,
                                        ).replaceFirstChar { it.titlecase() }
                                    } else date.format(headerFormatter).replaceFirstChar { it.titlecase() }
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { heading() },
                            )
                            AmountText(
                                amount = kotlin.math.abs(net),
                                kind = if (net >= 0) AmountKind.Income else AmountKind.Expense,
                                style = BoxTheme.moneyType.small,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            itemsIndexed(transactions, key = { _, t -> t.id }) { index, transaction ->
                TransactionItem(
                    transaction = transaction,
                    category = transaction.categoryId?.let { categoryMap[it] },
                    index = index,
                    count = transactions.size,
                    showDate = date == null,
                    onClick = { onEditTransaction(transaction.id) },
                    modifier = Modifier
                        .padding(bottom = ListItemDefaults.SegmentedGap)
                        .animateItem(),
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
