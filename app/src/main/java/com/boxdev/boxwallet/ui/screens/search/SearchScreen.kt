@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.boxdev.boxwallet.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.AppPane
import com.boxdev.boxwallet.ui.components.BackButton
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.components.CategoryBadge
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.LoanCard
import com.boxdev.boxwallet.ui.components.TransactionItem
import com.boxdev.boxwallet.ui.components.readableWidth

/**
 * Global search: one field that finds movements and loans by description, name, category or
 * amount. Opened from the search action in every tab's top bar.
 */
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenLoan: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val categoryMap = remember(uiState.categories) { uiState.categories.associateBy { it.id } }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            Surface(color = Color.Transparent) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp, end = 16.dp),
                    ) {
                        BackButton(onClick = onNavigateBack)
                        SearchField(
                            query = uiState.query,
                            onQueryChange = viewModel::updateQuery,
                            onSearch = { focusManager.clearFocus() },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                        )
                    }
                    ScopeChips(
                        selected = uiState.scope,
                        onSelect = viewModel::updateScope,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                }
            }
        },
    ) { padding ->
        val margin = BoxTheme.layout.margin
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // The pane ends above the keyboard when it shows, else above the navigation bar.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))
                .padding(start = margin, end = margin, bottom = margin),
            contentAlignment = Alignment.TopCenter,
        ) {
            AppPane(Modifier.fillMaxWidth().widthIn(max = 720.dp).fillMaxHeight()) {
            when {
                uiState.isLoading -> LoadingIndicator(Modifier.padding(top = 64.dp))

                !uiState.hasQuery -> Column(
                    Modifier
                        .readableWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = stringResource(R.string.search_empty_title),
                        message = stringResource(R.string.search_empty_message),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    if (uiState.categories.isNotEmpty()) {
                        CategorySuggestions(
                            categories = uiState.categories,
                            onPick = { viewModel.updateQuery(it) },
                        )
                    }
                }

                !uiState.hasResults -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.search_no_results, uiState.query.trim()),
                    message = stringResource(R.string.search_no_results_hint),
                    modifier = Modifier.padding(top = 48.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .readableWidth()
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                ) {
                    if (uiState.loans.isNotEmpty()) {
                        item(key = "h_loans") {
                            ResultHeader(stringResource(R.string.search_section_loans), uiState.loans.size)
                        }
                        itemsIndexed(uiState.loans, key = { _, l -> "l_${l.id}" }) { _, loan ->
                            LoanCard(
                                loan = loan,
                                category = loan.categoryId?.let { categoryMap[it] },
                                onClick = { onOpenLoan(loan.id) },
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .animateItem(),
                            )
                        }
                    }
                    if (uiState.transactions.isNotEmpty()) {
                        item(key = "h_movements") {
                            ResultHeader(stringResource(R.string.search_section_movements), uiState.transactions.size)
                        }
                        val list = uiState.transactions
                        itemsIndexed(list, key = { _, t -> "t_${t.id}" }) { index, transaction ->
                            TransactionItem(
                                transaction = transaction,
                                category = transaction.categoryId?.let { categoryMap[it] },
                                index = index,
                                count = list.size,
                                showDate = true,
                                onClick = { onOpenTransaction(transaction.id) },
                                modifier = Modifier
                                    .padding(bottom = ListItemDefaults.SegmentedGap)
                                    .animateItem(),
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(stringResource(R.string.search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.heightIn(min = 56.dp),
    )
}

@Composable
private fun ScopeChips(
    selected: SearchScope,
    onSelect: (SearchScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchScope.entries.forEach { scope ->
            val isSelected = scope == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(scope) },
                label = {
                    Text(
                        when (scope) {
                            SearchScope.ALL -> stringResource(R.string.transactions_filter_all)
                            SearchScope.MOVEMENTS -> stringResource(R.string.search_section_movements)
                            SearchScope.LOANS -> stringResource(R.string.search_section_loans)
                        }
                    )
                },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, null, Modifier.width(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

/** With an empty query, categories double as one-tap searches. */
@Composable
private fun CategorySuggestions(
    categories: List<com.boxdev.boxwallet.domain.model.Category>,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.search_browse_categories),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                SuggestionChip(
                    onClick = { onPick(category.name) },
                    label = { Text(category.name) },
                    icon = { CategoryBadge(category = category, size = SuggestionChipDefaults.IconSize + 4.dp) },
                )
            }
        }
    }
}

@Composable
private fun ResultHeader(title: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
