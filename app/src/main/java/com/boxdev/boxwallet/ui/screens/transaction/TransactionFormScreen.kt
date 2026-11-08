@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.transaction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.BuildConfig
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.ReminderDuration
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.domain.money.pickerSections
import com.boxdev.boxwallet.ui.components.AmountField
import com.boxdev.boxwallet.ui.components.CategoryPicker
import com.boxdev.boxwallet.ui.components.ConfirmDeleteDialog
import com.boxdev.boxwallet.ui.components.ConnectedChoiceRow
import com.boxdev.boxwallet.ui.components.ConversionRow
import com.boxdev.boxwallet.ui.components.CurrencyPickerSheet
import com.boxdev.boxwallet.ui.components.DateRow
import com.boxdev.boxwallet.ui.components.DateTimePickerDialogs
import com.boxdev.boxwallet.ui.components.FullScreenForm
import com.boxdev.boxwallet.ui.components.MenuRow
import com.boxdev.boxwallet.ui.components.OptionGroup
import com.boxdev.boxwallet.ui.components.SwitchRow
import com.boxdev.boxwallet.ui.components.TimeRow
import com.boxdev.boxwallet.ui.components.amountKind
import com.boxdev.boxwallet.ui.components.directionToggleColors
import com.boxdev.boxwallet.ui.components.groupShape
import com.boxdev.boxwallet.ui.components.rememberDateTimePickerState
import com.boxdev.boxwallet.ui.components.trendIcon
import java.util.Locale

/**
 * Full-screen create/edit form for a movement. The view model loads the movement from the
 * navigation `id` argument; [onDone] runs once, after a save or a confirmed delete.
 */
@Composable
fun TransactionFormScreen(
    onDone: () -> Unit,
    viewModel: TransactionFormViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val currentOnDone by rememberUpdatedState(onDone)
    var finished by remember { mutableStateOf(false) }
    val finish: () -> Unit = {
        if (!finished) {
            finished = true
            currentOnDone()
        }
    }

    // Delete also flags isSaved, so this covers both exits.
    LaunchedEffect(formState.isSaved) { if (formState.isSaved) finish() }

    FullScreenForm(
        title = stringResource(if (formState.isEditing) R.string.transaction_edit_title else R.string.transaction_new_title),
        onClose = finish,
        onSave = viewModel::save,
        saveEnabled = formState.canSave,
        onDelete = if (formState.isEditing) ({ showDeleteDialog = true }) else null,
        hasUnsavedChanges = hasUnsavedChanges,
    ) {
        if (formState.isLoading) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@FullScreenForm
        }
        // Above the fields so a failed save from the top bar is reported where the eye already is.
        formState.error?.let { TransactionFormErrorText(it) }
        TransactionFormFields(viewModel)
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.transaction_delete_title),
            message = stringResource(R.string.transaction_delete_message),
            onConfirm = viewModel::delete,
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/**
 * The movement form's fields plus the pickers they open (currency sheet, date/time dialogs).
 * Shared by [TransactionFormScreen] and, until it is removed, the bottom-sheet form.
 */
@Composable
fun TransactionFormFields(
    viewModel: TransactionFormViewModel,
    modifier: Modifier = Modifier,
) {
    val formState by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val pickers = rememberDateTimePickerState()
    val recentCurrencies by viewModel.recentCurrencies.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    val locale = remember { Locale.getDefault() }
    val availableReminders = remember { ReminderDuration.entries.filter { !it.debugOnly || BuildConfig.DEBUG } }

    Column(
        modifier = modifier,
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
            colors = { directionToggleColors(it == TransactionType.INCOME) },
        )

        AmountField(
            value = formState.amount,
            onValueChange = viewModel::updateAmount,
            symbol = CurrencyCatalog.symbol(formState.currency, locale),
            kind = amountKind(formState.type == TransactionType.INCOME),
            isError = formState.error != null && formState.amount.isEmpty(),
            autoFocus = !formState.isEditing,
            currencyCode = formState.currency.currencyCode,
            onCurrencyClick = { showCurrencyPicker = true },
        )

        val accountCurrency = formState.accountCurrency
        if (formState.isForeign && accountCurrency != null) {
            ConversionRow(
                rate = formState.rate,
                from = formState.currency,
                to = accountCurrency,
                day = formState.date.toLocalDate(),
                equivalent = formState.equivalent.orEmpty(),
                onRateChange = viewModel::updateManualRate,
                onEquivalentChange = viewModel::updateEquivalent,
                onUseAutomatic = viewModel::useAutomaticRate,
                manualRate = formState.manualRate,
                needsManualRate = formState.needsManualRate,
            )
        }

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

    if (showCurrencyPicker) {
        var query by remember { mutableStateOf("") }
        val allCurrencies = remember(locale) { CurrencyCatalog.all(locale) }
        val sections = remember(query, recentCurrencies, allCurrencies) {
            pickerSections(allCurrencies, recentCurrencies, query, locale)
        }
        CurrencyPickerSheet(
            selected = formState.currency,
            sections = sections,
            onQuery = { query = it },
            onPick = { viewModel.updateCurrency(it); showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    DateTimePickerDialogs(pickers, formState.date, viewModel::updateDate, viewModel::updateTime)
}

/** The form's error line, in the error color. */
@Composable
fun TransactionFormErrorText(error: TransactionFormError) {
    Text(
        text = transactionFormErrorText(error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun transactionFormErrorText(error: TransactionFormError): String = when (error) {
    TransactionFormError.InvalidAmount -> stringResource(R.string.transaction_error_invalid_amount)
    TransactionFormError.NeedsRate -> stringResource(R.string.conversion_needs_rate)
    TransactionFormError.AccountLoading -> stringResource(R.string.transaction_error_account_loading)
    TransactionFormError.NotFound -> stringResource(R.string.transaction_error_not_found)
    is TransactionFormError.MissingRate -> stringResource(R.string.transaction_error_missing_rate, error.from.currencyCode)
    TransactionFormError.SaveFailed -> stringResource(R.string.transaction_error_save)
    TransactionFormError.DeleteFailed -> stringResource(R.string.transaction_error_delete)
}
