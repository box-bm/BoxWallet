@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.loans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.ui.components.AmountField
import com.boxdev.boxwallet.ui.components.ConfirmDeleteDialog
import com.boxdev.boxwallet.ui.components.ConnectedChoiceRow
import com.boxdev.boxwallet.util.DateFormats
import com.boxdev.boxwallet.ui.components.DateRow
import com.boxdev.boxwallet.ui.components.LocalDateDialog
import com.boxdev.boxwallet.ui.components.SwitchRow
import com.boxdev.boxwallet.ui.components.ValueRow
import com.boxdev.boxwallet.ui.screens.transaction.AccountChip
import com.boxdev.boxwallet.ui.screens.transaction.AccountPickerSheet
import com.boxdev.boxwallet.ui.components.DateTimePickerDialogs
import com.boxdev.boxwallet.ui.components.FullScreenForm
import com.boxdev.boxwallet.ui.components.MenuRow
import com.boxdev.boxwallet.ui.components.OptionGroup
import com.boxdev.boxwallet.ui.components.TimeRow
import com.boxdev.boxwallet.ui.components.amountKind
import com.boxdev.boxwallet.ui.components.directionToggleColors
import com.boxdev.boxwallet.ui.components.groupShape
import com.boxdev.boxwallet.ui.components.rememberDateTimePickerState
import com.boxdev.boxwallet.ui.components.trendIcon
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.util.Locale

/**
 * Full-screen create/edit form for a loan. The view model loads the loan from the navigation `id`
 * argument. Exactly one exit runs, once: [onDone] after ✕ or a save, [onDeleted] after a confirmed
 * delete (a system back without edits is popped by the NavHost itself). Each returns whether it
 * navigated: while the entry is still animating in it may refuse, and the form stays usable.
 */
@Composable
fun LoanFormScreen(
    onDone: () -> Boolean,
    onDeleted: () -> Boolean,
    viewModel: LoanFormViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val scrollState = rememberScrollState()
    var finished by rememberSaveable { mutableStateOf(false) }
    // The latch is only set once an exit really navigated, so an early ✕ can't lock the form.
    val exit: (() -> Boolean) -> Unit = { callback -> if (!finished && callback()) finished = true }
    val finish: () -> Unit = { exit(currentOnDone) }
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(Lifecycle.State.RESUMED)

    // A save or delete that lands while the form is still animating in exits once it settles.
    LaunchedEffect(formState.isSaved, formState.deleted, resumed) {
        if (!resumed) return@LaunchedEffect
        if (formState.deleted) exit(currentOnDeleted) else if (formState.isSaved) finish()
    }
    // A failed save is reported above the fields: bring it into view.
    LaunchedEffect(formState.error) { if (formState.error != null) scrollState.animateScrollTo(0) }

    FullScreenForm(
        title = stringResource(if (formState.isEditing) R.string.loan_edit_title else R.string.loan_new_title),
        onClose = finish,
        onSave = viewModel::save,
        deleteLabel = stringResource(R.string.loan_delete_button),
        onDelete = if (formState.isEditing) ({ showDeleteDialog = true }) else null,
        hasUnsavedChanges = hasUnsavedChanges,
        scrollState = scrollState,
    ) {
        if (formState.isLoading) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@FullScreenForm
        }
        // Above the fields so a failed save from the top bar is reported where the eye already is.
        formState.error?.let { LoanFormErrorText(it) }
        LoanFormFields(viewModel)
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.loan_delete_title),
            message = stringResource(R.string.loan_delete_message),
            onConfirm = viewModel::delete,
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun LoanFormErrorText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The loan form's fields plus the pickers they open (account sheet, date/time dialogs). */
@Composable
private fun LoanFormFields(
    viewModel: LoanFormViewModel,
    modifier: Modifier = Modifier,
) {
    val formState by viewModel.formState.collectAsState()
    val pickers = rememberDateTimePickerState()
    val formatter = rememberCurrencyFormatter()
    var showAccountPicker by remember { mutableStateOf(false) }
    var showFirstDuePicker by remember { mutableStateOf(false) }
    var showExpectedPicker by remember { mutableStateOf(false) }
    val locale = remember { Locale.getDefault() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ConnectedChoiceRow(
            options = LoanType.entries,
            selected = formState.loanType,
            onSelect = viewModel::updateLoanType,
            label = { stringResource(it.displayNameRes) },
            icon = { trendIcon(it == LoanType.INCOME) },
            colors = { directionToggleColors(it == LoanType.INCOME) },
        )

        AmountField(
            value = formState.totalAmount,
            onValueChange = viewModel::updateTotalAmount,
            symbol = CurrencyCatalog.symbol(formState.currency, locale),
            kind = amountKind(formState.loanType == LoanType.INCOME),
            label = stringResource(R.string.loan_total_amount_label),
            isError = formState.error != null && formState.totalAmount.isEmpty(),
            autoFocus = !formState.isEditing,
            currencyCode = formState.currency.currencyCode,
            // The currency is the account's: shown, never picked.
            onCurrencyClick = {},
            currencyEnabled = false,
            currencyDisabledDescription = stringResource(
                R.string.loan_currency_from_account,
                formState.currency.currencyCode,
            ),
            // With a single account there is nothing to choose: no chip.
            accountChip = if (formState.accounts.size > 1) {
                { AccountChip(formState.account, formState.accounts) { showAccountPicker = true } }
            } else null,
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = formState.name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.loan_name_label)) },
                placeholder = { Text(stringResource(R.string.loan_name_placeholder)) },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = formState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text(stringResource(R.string.description_optional)) },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        OptionGroup {
            DateRow(groupShape(0, 2), stringResource(R.string.loan_start_date), formState.date, pickers)
            TimeRow(groupShape(1, 2), formState.date, pickers)
        }

        OptionGroup {
            SwitchRow(
                shape = groupShape(0, 1),
                icon = Icons.Outlined.EventRepeat,
                label = stringResource(R.string.loan_plan_switch),
                checked = formState.hasPlan,
                onCheckedChange = viewModel::setHasPlan,
                supporting = stringResource(R.string.loan_plan_switch_supporting),
            )
        }

        if (formState.hasPlan) {
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
                        shape = MaterialTheme.shapes.extraLarge,
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
                            shape = MaterialTheme.shapes.extraLarge,
                            singleLine = true,
                        )
                    }
                }
                OptionGroup {
                    MenuRow(
                        shape = groupShape(0, 2),
                        icon = Icons.Outlined.EventRepeat,
                        label = stringResource(R.string.transaction_frequency_label),
                        value = stringResource(formState.frequency.displayNameRes),
                        options = RecurrenceFrequency.entries,
                        optionLabel = { stringResource(it.displayNameRes) },
                        onSelect = viewModel::updateFrequency,
                    )
                    ValueRow(
                        groupShape(1, 2),
                        Icons.Outlined.CalendarMonth,
                        stringResource(R.string.loan_first_due),
                        formState.firstDueDate.format(DateFormats.mediumDate),
                        onClick = { showFirstDuePicker = true },
                    )
                }
                formState.installmentAmount?.let { amount ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.extraLarge,
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
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OptionGroup {
                        ValueRow(
                            groupShape(0, 1),
                            Icons.Outlined.CalendarMonth,
                            stringResource(R.string.loan_expected_date),
                            formState.expectedDate?.format(DateFormats.mediumDate)
                                ?: stringResource(R.string.loan_expected_date_none),
                            onClick = { showExpectedPicker = true },
                        )
                    }
                }
                if (formState.expectedDate != null) {
                    IconButton(onClick = { viewModel.updateExpectedDate(null) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.loan_expected_date_clear))
                    }
                }
            }
        }
    }

    if (showAccountPicker) {
        AccountPickerSheet(
            accounts = formState.accounts,
            selectedId = formState.accountId,
            onPick = { viewModel.setAccount(it); showAccountPicker = false },
            onDismiss = { showAccountPicker = false },
        )
    }
    if (showFirstDuePicker) {
        LocalDateDialog(
            initial = formState.firstDueDate,
            onPick = viewModel::updateFirstDueDate,
            onDismiss = { showFirstDuePicker = false },
        )
    }
    if (showExpectedPicker) {
        LocalDateDialog(
            initial = formState.expectedDate ?: formState.date.toLocalDate(),
            onPick = viewModel::updateExpectedDate,
            onDismiss = { showExpectedPicker = false },
        )
    }

    DateTimePickerDialogs(pickers, formState.date, viewModel::updateDate, viewModel::updateTime)
}
