@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.loans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.ui.components.LoanSheet
import com.boxdev.boxwallet.util.toCurrencySymbol
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoanDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: LoanDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEditSheet by remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.loan?.name ?: stringResource(R.string.loan_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.loan_detail_edit))
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { LoadingIndicator() }

            uiState.loan == null -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.loan_not_found), style = MaterialTheme.typography.bodyLarge) }

            else -> {
                val loan = uiState.loan!!
                val symbol = loan.currency.toCurrencySymbol()

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(bottom = 0.dp, top = paddingValues.calculateTopPadding()),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.loan_summary),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.loan_total_amount), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "$symbol${"%.2f".format(loan.totalAmount)}",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.loan_per_installment), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "$symbol${"%.2f".format(loan.installmentAmount)}",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.loan_progress), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        stringResource(R.string.loan_paid_progress, loan.paidInstallments, loan.totalInstallments),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { loan.progressFraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                loan.nextDueDate?.let { dueDate ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(stringResource(R.string.loan_next_payment), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            dueDate.format(dateFormatter),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.loan_detail_start_date), style = MaterialTheme.typography.bodyMedium)
                                    Text(loan.startDate.format(dateFormatter))
                                }
                                if (loan.description.isNotBlank()) {
                                    Text(
                                        loan.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.loan_installments_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(uiState.installments, key = { it.id }) { installment ->
                        InstallmentRow(
                            installment = installment,
                            symbol = symbol,
                            dateFormatter = dateFormatter,
                            onToggleCleared = { viewModel.toggleInstallmentCleared(installment) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showEditSheet) {
        LoanSheet(
            loanId = uiState.loan?.id,
            onDismiss = { showEditSheet = false }
        )
    }
}

@Composable
private fun InstallmentRow(
    installment: Transaction,
    symbol: String,
    dateFormatter: DateTimeFormatter,
    onToggleCleared: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (installment.isCleared)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onToggleCleared) {
                    Icon(
                        imageVector = if (installment.isCleared) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (installment.isCleared) stringResource(R.string.loan_mark_as_unpaid) else stringResource(R.string.loan_mark_as_paid),
                        tint = if (installment.isCleared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.loan_installment_number, installment.installmentNumber ?: 0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = installment.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "$symbol${"%.2f".format(installment.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (installment.isCleared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
