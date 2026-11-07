@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.boxdev.boxwallet.BuildConfig
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.ReminderDuration
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.ui.screens.transaction.TransactionFormViewModel
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.rememberCurrencyFormatter

@Composable
fun TransactionSheet(
    transactionId: Long?,
    onDismiss: () -> Unit,
    viewModel: TransactionFormViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteDialog by remember { mutableStateOf(false) }
    val pickers = rememberDateTimePickerState()
    val formatter = rememberCurrencyFormatter()
    val money = BoxTheme.money
    val availableReminders = remember { ReminderDuration.entries.filter { !it.debugOnly || BuildConfig.DEBUG } }

    LaunchedEffect(transactionId) { viewModel.loadTransaction(transactionId) }
    LaunchedEffect(formState.isSaved) { if (formState.isSaved) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SheetHeader(
                title = stringResource(if (formState.isEditing) R.string.transaction_edit_title else R.string.transaction_new_title),
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
                    options = listOf(TransactionType.EXPENSE, TransactionType.INCOME),
                    selected = formState.type,
                    onSelect = viewModel::updateType,
                    label = {
                        stringResource(
                            if (it == TransactionType.INCOME) R.string.transaction_type_income else R.string.transaction_type_expense
                        )
                    },
                    icon = { trendIcon(it == TransactionType.INCOME) },
                    colors = {
                        val income = it == TransactionType.INCOME
                        ToggleButtonDefaults.toggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            checkedContainerColor = if (income) money.incomeContainer else money.expenseContainer,
                            checkedContentColor = if (income) money.onIncomeContainer else money.onExpenseContainer,
                        )
                    },
                )

                AmountField(
                    value = formState.amount,
                    onValueChange = viewModel::updateAmount,
                    symbol = formatter.symbol,
                    kind = if (formState.type == TransactionType.INCOME) AmountKind.Income else AmountKind.Expense,
                    isError = formState.error != null && formState.amount.isEmpty(),
                    autoFocus = !formState.isEditing,
                )

                OutlinedTextField(
                    value = formState.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text(stringResource(R.string.transaction_description_label)) },
                    placeholder = { Text(stringResource(R.string.transaction_description_placeholder)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                CategoryPicker(
                    categories = categories,
                    selectedCategoryId = formState.categoryId,
                    onCategorySelected = viewModel::updateCategory,
                )

                OptionGroup(title = stringResource(R.string.form_when)) {
                    DateRow(groupShape(0, 2), stringResource(R.string.transaction_date_label), formState.date, pickers)
                    TimeRow(groupShape(1, 2), formState.date, pickers)
                }

                val showReminder = !formState.isCleared
                val showReminderTime = showReminder && formState.hasReminder
                val showFrequency = formState.isRecurrent
                val count = 2 + (if (showReminder) 1 else 0) + (if (showReminderTime) 1 else 0) + (if (showFrequency) 1 else 0)
                var i = 0
                OptionGroup(title = stringResource(R.string.form_options)) {
                    SwitchRow(
                        groupShape(i++, count), Icons.Outlined.TaskAlt,
                        stringResource(
                            if (formState.type == TransactionType.INCOME) R.string.transaction_settled_income
                            else R.string.transaction_settled_expense
                        ),
                        formState.isCleared, viewModel::updateIsCleared,
                    )
                    if (showReminder) {
                        SwitchRow(
                            groupShape(i++, count), Icons.Outlined.NotificationsActive,
                            stringResource(R.string.transaction_reminder),
                            formState.hasReminder, viewModel::updateHasReminder,
                        )
                    }
                    if (showReminderTime) {
                        MenuRow(
                            shape = groupShape(i++, count),
                            icon = Icons.Outlined.Timer,
                            label = stringResource(R.string.transaction_reminder_before),
                            value = formState.reminderDuration?.label ?: stringResource(R.string.transaction_reminder_select),
                            options = availableReminders,
                            optionLabel = { it.label },
                            onSelect = viewModel::updateReminderDuration,
                        )
                    }
                    SwitchRow(
                        groupShape(i++, count), Icons.Outlined.Repeat,
                        stringResource(R.string.transaction_recurring),
                        formState.isRecurrent, viewModel::updateIsRecurrent,
                    )
                    if (showFrequency) {
                        MenuRow(
                            shape = groupShape(i++, count),
                            icon = Icons.Outlined.EventRepeat,
                            label = stringResource(R.string.transaction_frequency_label),
                            value = formState.recurrenceFrequency?.let { stringResource(it.displayNameRes) }
                                ?: stringResource(R.string.transaction_frequency_placeholder),
                            options = RecurrenceFrequency.entries,
                            optionLabel = { stringResource(it.displayNameRes) },
                            onSelect = viewModel::updateRecurrenceFrequency,
                        )
                    }
                }

                AnimatedVisibility(visible = formState.isRecurrent && formState.recurrenceFrequency == RecurrenceFrequency.CUSTOM_MONTHS) {
                    OutlinedTextField(
                        value = formState.recurrenceInterval,
                        onValueChange = viewModel::updateRecurrenceInterval,
                        label = { Text(stringResource(R.string.transaction_every_n_months)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.large,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SheetPrimaryButton(
                text = stringResource(if (formState.isEditing) R.string.transaction_update else R.string.transaction_save),
                enabled = formState.isValid,
                onClick = viewModel::save,
            )
        }
    }

    DateTimePickerDialogs(pickers, formState.date, viewModel::updateDate, viewModel::updateTime)

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.transaction_delete_title),
            message = stringResource(R.string.transaction_delete_message),
            onConfirm = viewModel::delete,
            onDismiss = { showDeleteDialog = false }
        )
    }
}
