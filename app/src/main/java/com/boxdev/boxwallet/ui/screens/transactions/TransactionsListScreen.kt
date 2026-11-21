@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)

package com.boxdev.boxwallet.ui.screens.transactions

import android.text.format.DateUtils

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.boxdev.boxwallet.ui.navigation.NavRoutes
import com.boxdev.boxwallet.ui.navigation.TabReselectEffect
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.boxdev.boxwallet.ui.screens.accounts.AccountBadge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.boxdev.boxwallet.ui.components.AdjustmentDeleteDialog
import com.boxdev.boxwallet.data.local.entity.countsInReports
import com.boxdev.boxwallet.data.local.entity.isAdjustment
import com.boxdev.boxwallet.data.local.entity.isTransfer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.screens.transaction.TransactionFormViewModel
import com.boxdev.boxwallet.ui.screens.transaction.TransactionFormScreen
import com.boxdev.boxwallet.ui.components.PanePlaceholder
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import com.boxdev.boxwallet.domain.model.net
import com.boxdev.boxwallet.ui.components.AccountFilterChips
import com.boxdev.boxwallet.ui.components.CategoryFilterChips
import com.boxdev.boxwallet.ui.components.CheckFilterChip
import com.boxdev.boxwallet.ui.components.CollapsibleFilterPanel
import com.boxdev.boxwallet.ui.components.amountKind
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.AppSearchBar
import com.boxdev.boxwallet.ui.components.PaneRow
import com.boxdev.boxwallet.ui.components.mainMoney
import com.boxdev.boxwallet.ui.components.ConnectedChoiceRow
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.FilterGroup
import com.boxdev.boxwallet.ui.components.FilterToggleAction
import com.boxdev.boxwallet.ui.components.TransactionItem
import com.boxdev.boxwallet.ui.components.transferTitle
import com.boxdev.boxwallet.ui.theme.BoxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TransactionsListScreen(
    onEditTransaction: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onOpenTransfer: (Long) -> Unit = {},
    viewModel: TransactionsListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingAdjustment by remember { mutableStateOf<Transaction?>(null) }
    pendingAdjustment?.let { adjustment ->
        AdjustmentDeleteDialog(
            adjustment = adjustment,
            onDelete = { viewModel.deleteTransaction(adjustment.id); pendingAdjustment = null },
            onDismiss = { pendingAdjustment = null },
        )
    }
    val filter = uiState.filterState
    val categoryMap = remember(uiState.availableCategories) { uiState.availableCategories.associateBy { it.id } }
    // The selector lists accounts still in use, plus an archived one the filter points at (from its detail).
    val filterAccounts = remember(uiState.accounts, filter.accountId) {
        uiState.accounts.filter { !it.isArchived || it.id == filter.accountId }
    }
    val filteredAccount = uiState.accounts.firstOrNull { it.id == filter.accountId }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val twoPane = BoxTheme.layout.twoPane
    val listState = rememberLazyListState()
    TabReselectEffect(NavRoutes.TransactionsList.route) {
        listState.animateScrollToItem(0)
    }
    val activeFilters = listOf(
        filter.transactionType != TransactionTypeFilter.ALL,
        filter.categoryId != null,
        filter.accountId != null,
        filter.startDate != null || filter.endDate != null,
        filter.sortBy != SortBy.DATE_DESC,
        filter.showOnlyRecurrent,
    ).count { it }

    // Two panes: the list beside an editor for the selected movement, the way Loans shows a loan.
    // One view model serves the pane and is pointed at each selection in turn.
    val editor: TransactionFormViewModel = hiltViewModel(key = "movement_editor_pane")
    val editorHasChanges by editor.hasUnsavedChanges.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingId by remember { mutableStateOf<Long?>(null) }
    val openMovement: (Long) -> Unit = { id ->
        when {
            !twoPane -> onEditTransaction(id)
            id == selectedId -> Unit
            // Edits in the pane are not dropped silently for another row.
            editorHasChanges -> pendingId = id
            else -> { editor.open(id); selectedId = id }
        }
    }
    // After the process was restored, the selection is back but the editor is not.
    LaunchedEffect(selectedId) { selectedId?.let(editor::open) }
    // Folded with a movement open: it carries on in the full-screen form.
    LaunchedEffect(twoPane) {
        val id = selectedId
        if (!twoPane && id != null) { selectedId = null; onEditTransaction(id) }
    }
    // A movement deleted elsewhere leaves the pane.
    LaunchedEffect(selectedId, uiState.transactions, uiState.isLoading) {
        val id = selectedId
        if (id != null && !uiState.isLoading && uiState.transactions.none { it.id == id }) selectedId = null
    }

    BackHandler(enabled = showFilters) { showFilters = false }
    BackHandler(enabled = twoPane && selectedId != null && !showFilters) { selectedId = null }

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
                    CategoryFilterChips(uiState.availableCategories, filter.categoryId, viewModel::updateCategoryFilter)
                }
            }
            // With a single account there is nothing to choose: the panel stays as it was.
            if (filterAccounts.size > 1) {
                FilterGroup(stringResource(R.string.transactions_filter_account)) {
                    AccountFilterChips(filterAccounts, filter.accountId, viewModel::updateAccountFilter)
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
                CheckFilterChip(
                    selected = filter.showOnlyRecurrent,
                    onClick = { viewModel.updateRecurrentFilter(!filter.showOnlyRecurrent) },
                    label = stringResource(R.string.transactions_filter_recurring_only),
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = viewModel::clearFilters, enabled = activeFilters > 0) {
                    Text(stringResource(R.string.action_clear_all))
                }
            }
        }
    }

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            CollapsibleFilterPanel(visible = showFilters) {
                filterPanel(Modifier.padding(16.dp))
            }
            // An account filter usually comes from the account's detail, with the panel closed: say so.
            if (filteredAccount != null && !showFilters) {
                val remove = stringResource(R.string.transactions_filter_account_remove, filteredAccount.name)
                InputChip(
                    selected = true,
                    onClick = { viewModel.updateAccountFilter(null) },
                    label = { Text(filteredAccount.name, maxLines = 1) },
                    avatar = { AccountBadge(filteredAccount, size = InputChipDefaults.AvatarSize.value.toInt()) },
                    trailingIcon = {
                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize))
                    },
                    modifier = Modifier.padding(vertical = 8.dp).semantics { contentDescription = remove },
                )
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
                    selectedId = if (twoPane) selectedId else null,
                    onEditTransaction = { id ->
                        val tapped = uiState.filteredTransactions.firstOrNull { it.id == id }
                        when {
                            tapped?.type?.isAdjustment == true -> pendingAdjustment = tapped
                            tapped?.type?.isTransfer == true -> onOpenTransfer(id)
                            else -> openMovement(id)
                        }
                    },
                )
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            AppSearchBar(
                onClick = onNavigateToSearch,
                trailing = {
                    FilterToggleAction(
                        filtersOpen = showFilters,
                        onToggleFilters = { showFilters = it },
                        activeFilterCount = activeFilters,
                    )
                },
            )
        },
    ) { padding ->
        if (twoPane) {
            PaneRow(
                modifier = Modifier.padding(padding),
                firstWidth = LIST_PANE_WIDTH,
                containSecond = true,
                first = content,
                second = {
                    val id = selectedId
                    if (id == null) {
                        PanePlaceholder(
                            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                            title = stringResource(R.string.transactions_select_hint),
                        )
                    } else {
                        // Keyed, so the form's own exit latch starts fresh for each movement.
                        key(id) {
                            TransactionFormScreen(
                                onDone = { selectedId = null; true },
                                viewModel = editor,
                                embedded = true,
                            )
                        }
                    }
                },
            )
        } else {
            PaneRow(modifier = Modifier.padding(padding), first = content)
        }
    }

    pendingId?.let { next ->
        AlertDialog(
            onDismissRequest = { pendingId = null },
            title = { Text(stringResource(R.string.form_discard_title)) },
            confirmButton = {
                TextButton(onClick = { pendingId = null; editor.open(next); selectedId = next }) {
                    Text(stringResource(R.string.form_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingId = null }) { Text(stringResource(R.string.form_keep_editing)) }
            },
        )
    }
}

@Composable
private fun TransactionsByDay(
    uiState: TransactionsListUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    categoryMap: Map<Long, Category>,
    selectedId: Long?,
    onEditTransaction: (Long) -> Unit,
) {
    val accountsById = remember(uiState.accounts) { uiState.accounts.associateBy { it.id } }
    // Scoped to one account, a transfer's leg reads as money in or out of it.
    val signedTransfer = uiState.filterState.accountId != null
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
        contentPadding = PaddingValues(top = 8.dp, bottom = 112.dp),
    ) {
        item(key = "records_count") {
            Text(
                text = pluralStringResource(
                    R.plurals.records_count,
                    uiState.filteredTransactions.size,
                    uiState.filteredTransactions.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        groups.forEach { (date, transactions) ->
            if (date != null) {
                stickyHeader(key = "h_$date") {
                    val net = transactions.net()
                    val hasReportable = transactions.any { it.type.countsInReports }
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
                            if (hasReportable) {
                                AmountText(
                                    amount = mainMoney(kotlin.math.abs(net)),
                                    kind = amountKind(net >= 0),
                                    style = BoxTheme.moneyType.small,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                    transferTitle = transaction.transferId?.let { transferTitle(uiState.transferPairs[it], accountsById) },
                    signedTransfer = signedTransfer,
                    containerColor = if (transaction.id == selectedId) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else BoxTheme.cardColor,
                    onClick = { onEditTransaction(transaction.id) },
                    modifier = Modifier
                        .semantics { selected = transaction.id == selectedId }
                        .padding(bottom = ListItemDefaults.SegmentedGap)
                        .animateItem(),
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Width of the list beside the editor pane on two-pane windows, the same as the Loans list. */
private val LIST_PANE_WIDTH = 360.dp
