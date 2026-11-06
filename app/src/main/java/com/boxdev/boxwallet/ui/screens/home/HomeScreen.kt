@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.BalancePanel
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.LoanCard
import com.boxdev.boxwallet.ui.components.SectionHeader
import com.boxdev.boxwallet.ui.components.TransactionItem
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.isWide

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onSeeAllTransactions: () -> Unit = {},
    onSeeAllLoans: () -> Unit = {},
    onNavigateToLoanDetail: (Long) -> Unit = {},
    onEditTransaction: (Long) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val categoryMap = remember(uiState.categories) { uiState.categories.associateBy { it.id } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val wide = BoxTheme.widthClass.isWide

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            MediumFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }

        val balance: LazyListScope.() -> Unit = {
            item(key = "balance") { BalancePanel(summary = uiState.financialSummary) }
        }
        val recent: LazyListScope.() -> Unit = {
            item(key = "recent_header") {
                SectionHeader(
                    title = stringResource(R.string.home_recent_transactions),
                    actionLabel = stringResource(R.string.home_see_all).takeIf { uiState.latestTransactions.isNotEmpty() },
                    onAction = onSeeAllTransactions,
                    // In two panes this header opens its column, so it aligns with the balance panel.
                    modifier = Modifier.padding(top = if (wide) 0.dp else 16.dp),
                )
            }
            if (uiState.latestTransactions.isEmpty()) {
                item(key = "recent_empty") {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = stringResource(R.string.home_no_transactions),
                        message = stringResource(R.string.home_no_transactions_hint),
                    )
                }
            } else {
                val list = uiState.latestTransactions
                itemsIndexed(list, key = { _, t -> "t_${t.id}" }) { index, transaction ->
                    TransactionItem(
                        transaction = transaction,
                        category = transaction.categoryId?.let { categoryMap[it] },
                        index = index,
                        count = list.size,
                        onClick = { onEditTransaction(transaction.id) },
                        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                    )
                }
            }
        }
        val loans: LazyListScope.() -> Unit = {
            if (uiState.activeLoans.isNotEmpty()) {
                item(key = "loans_header") {
                    SectionHeader(
                        title = stringResource(R.string.home_active_loans),
                        actionLabel = stringResource(R.string.home_see_all),
                        onAction = onSeeAllLoans,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                itemsIndexed(uiState.activeLoans, key = { _, l -> "loan_${l.id}" }) { _, loan ->
                    LoanCard(
                        loan = loan,
                        category = loan.categoryId?.let { categoryMap[it] },
                        onClick = { onNavigateToLoanDetail(loan.id) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }

        if (wide) {
            // Unfolded / tablet: money overview and loans on the left, movements on the right.
            Row(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 24.dp, bottom = 32.dp),
                ) {
                    balance()
                    loans()
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(end = 24.dp, bottom = 32.dp),
                ) {
                    recent()
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                balance()
                recent()
                loans()
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}
