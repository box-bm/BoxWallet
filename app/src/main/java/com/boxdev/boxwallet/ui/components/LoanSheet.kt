@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.ui.screens.loans.LoanFormViewModel
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.rememberCurrencyFormatter

@Composable
fun LoanSheet(
    loanId: Long?,
    onDismiss: () -> Unit,
    viewModel: LoanFormViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteDialog by remember { mutableStateOf(false) }
    val pickers = rememberDateTimePickerState()
    val formatter = rememberCurrencyFormatter()
    val money = BoxTheme.money

    LaunchedEffect(loanId) { viewModel.loadLoan(loanId) }
    LaunchedEffect(formState.isSaved) { if (formState.isSaved) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SheetHeader(
                title = stringResource(if (formState.isEditing) R.string.loan_edit_title else R.string.loan_new_title),
                onDelete = if (formState.isEditing) ({ showDeleteDialog = true }) else null,
            )

            if (formState.isLoading) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@Column
            }

            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ConnectedChoiceRow(
                    options = LoanType.entries,
                    selected = formState.loanType,
                    onSelect = viewModel::updateLoanType,
                    label = { stringResource(it.displayNameRes) },
                    icon = { trendIcon(it == LoanType.INCOME) },
                    colors = {
                        val income = it == LoanType.INCOME
                        ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            checkedContainerColor = if (income) money.incomeContainer else money.expenseContainer,
                            checkedContentColor = if (income) money.onIncomeContainer else money.onExpenseContainer,
                        )
                    },
                )

                AmountField(
                    value = formState.totalAmount,
                    onValueChange = viewModel::updateTotalAmount,
                    symbol = formatter.symbol,
                    kind = if (formState.loanType == LoanType.INCOME) AmountKind.Income else AmountKind.Expense,
                    label = stringResource(R.string.loan_total_amount_label),
                    isError = formState.error != null && formState.totalAmount.isEmpty(),
                    autoFocus = !formState.isEditing,
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = formState.name,
                        onValueChange = viewModel::updateName,
                        label = { Text(stringResource(R.string.loan_name_label)) },
                        placeholder = { Text(stringResource(R.string.loan_name_placeholder)) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = formState.description,
                        onValueChange = viewModel::updateDescription,
                        label = { Text(stringResource(R.string.description_optional)) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                CategoryPicker(
                    categories = categories,
                    selectedCategoryId = formState.categoryId,
                    onCategorySelected = viewModel::updateCategory,
                )

                // Terms: how many installments, how often, starting when.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.loan_terms),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = formState.installments,
                            onValueChange = viewModel::updateInstallments,
                            label = { Text(stringResource(R.string.loan_installments_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.large,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        AnimatedVisibility(
                            visible = formState.frequency == RecurrenceFrequency.CUSTOM_MONTHS,
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = formState.frequencyInterval,
                                onValueChange = viewModel::updateFrequencyInterval,
                                label = { Text(stringResource(R.string.transaction_every_n_months)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = MaterialTheme.shapes.large,
                                singleLine = true,
                            )
                        }
                    }
                    OptionGroup {
                        MenuRow(
                            shape = groupShape(0, 3),
                            icon = Icons.Outlined.EventRepeat,
                            label = stringResource(R.string.transaction_frequency_label),
                            value = stringResource(formState.frequency.displayNameRes),
                            options = RecurrenceFrequency.entries,
                            optionLabel = { stringResource(it.displayNameRes) },
                            onSelect = viewModel::updateFrequency,
                        )
                        DateRow(groupShape(1, 3), stringResource(R.string.loan_start_date), formState.startDate, pickers)
                        TimeRow(groupShape(2, 3), formState.startDate, pickers)
                    }
                    formState.installmentAmount?.let { amount ->
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.loan_each_installment,
                                    formatter.format(amount),
                                    stringResource(formState.frequency.displayNameRes),
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
            }

            SheetPrimaryButton(
                text = stringResource(if (formState.isEditing) R.string.transaction_update else R.string.loan_create),
                enabled = formState.isValid,
                onClick = viewModel::save,
            )
        }
    }

    DateTimePickerDialogs(pickers, formState.startDate, viewModel::updateStartDate, viewModel::updateTime)

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.loan_delete_title),
            message = stringResource(R.string.loan_delete_message, formState.installments),
            onConfirm = viewModel::delete,
            onDismiss = { showDeleteDialog = false }
        )
    }
}
