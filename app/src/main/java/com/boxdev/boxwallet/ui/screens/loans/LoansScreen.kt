@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.ui.components.BackButton
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.LoanCard
import com.boxdev.boxwallet.ui.components.SectionHeader
import com.boxdev.boxwallet.ui.theme.BoxTheme

/**
 * Every loan as a secondary screen: what people owe me, what I owe, then the settled ones.
 * Tapping a loan opens its detail.
 */
@Composable
fun LoansScreen(
    onNavigateBack: () -> Unit,
    onOpenLoan: (Long) -> Unit,
    viewModel: LoansViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val groups = uiState.groups
    val margin = BoxTheme.layout.margin

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_loans)) },
                navigationIcon = { BackButton(onNavigateBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }

                groups.isEmpty -> EmptyState(
                    icon = Icons.Outlined.Payments,
                    title = stringResource(R.string.loans_no_loans),
                    message = stringResource(R.string.loans_add_from_account),
                    modifier = Modifier.padding(padding).padding(top = 48.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = margin,
                        end = margin,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    loanSection("owed", R.string.loans_group_owed_to_me, groups.owedToMe, onOpenLoan)
                    loanSection("owe", R.string.loans_group_i_owe, groups.iOwe, onOpenLoan)
                    loanSection("settled", R.string.loans_group_settled, groups.settled, onOpenLoan)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.loanSection(
    key: String,
    title: Int,
    loans: List<Loan>,
    onOpenLoan: (Long) -> Unit,
) {
    if (loans.isEmpty()) return
    item(key = "header_$key") { SectionHeader(title = stringResource(title)) }
    items(loans, key = { "${key}_${it.id}" }) { loan ->
        LoanCard(loan = loan, onClick = { onOpenLoan(loan.id) }, modifier = Modifier.animateItem())
    }
}
