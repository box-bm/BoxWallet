@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.transaction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.CreditScore
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.BuildConfig
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.ReminderDuration
import com.boxdev.boxwallet.domain.model.accountLabel
import com.boxdev.boxwallet.domain.model.withChildrenAfterParents
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
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.components.SheetHeader
import com.boxdev.boxwallet.ui.screens.accounts.AccountBadge
import com.boxdev.boxwallet.util.accountIcon

/**
 * Full-screen create/edit form for a movement. The view model loads the movement from the
 * navigation `id` argument; [onDone] runs after ✕, a save or a confirmed delete (a system back
 * without edits is popped by the NavHost itself). It returns whether it navigated: while the entry
 * is still animating in it may refuse, and the form stays usable to try again.
 */
@Composable
fun TransactionFormScreen(
    onDone: () -> Boolean,
    viewModel: TransactionFormViewModel = hiltViewModel(),
    /** True when the form is the editor pane beside the Movements list instead of its own screen. */
    embedded: Boolean = false,
) {
    val formState by viewModel.formState.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val currentOnDone by rememberUpdatedState(onDone)
    val scrollState = rememberScrollState()
    var finished by rememberSaveable { mutableStateOf(false) }
    // The latch is only set once the exit really navigated, so an early ✕ can't lock the form.
    val finish: () -> Unit = { if (!finished && currentOnDone()) finished = true }
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(Lifecycle.State.RESUMED)

    // Delete also flags isSaved, so this covers both exits; a save that lands while the form is
    // still animating in exits once it settles.
    LaunchedEffect(formState.isSaved, resumed) { if (formState.isSaved && resumed) finish() }
    // A failed save is reported above the fields: bring it into view.
    LaunchedEffect(formState.error) { if (formState.error != null) scrollState.animateScrollTo(0) }

    FullScreenForm(
        title = stringResource(if (formState.isEditing) R.string.transaction_edit_title else R.string.transaction_new_title),
        onClose = finish,
        onSave = viewModel::save,
        onDelete = if (formState.isEditing) ({ showDeleteDialog = true }) else null,
        hasUnsavedChanges = hasUnsavedChanges,
        deleteLabel = stringResource(R.string.transaction_delete_title),
        embedded = embedded,
        scrollState = scrollState,
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

/** The movement form's fields plus the pickers they open (currency sheet, date/time dialogs). */
@Composable
private fun TransactionFormFields(
    viewModel: TransactionFormViewModel,
    modifier: Modifier = Modifier,
) {
    val formState by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val pickers = rememberDateTimePickerState()
    val recentCurrencies by viewModel.recentCurrencies.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    val locale = remember { Locale.getDefault() }
    val currencyFormatter = rememberCurrencyFormatter()
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
            // With a single account the form stays as it was before accounts: no chip.
            accountChip = if (formState.accounts.size > 1) {
                {
                    AccountChip(
                        formState.accounts.firstOrNull { it.id == formState.accountId },
                        formState.accounts,
                    ) { showAccountPicker = true }
                }
            } else null,
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
            shape = MaterialTheme.shapes.extraLarge,
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

        val showReminder = !formState.isCleared && !formState.installmentsActive
        val showReminderTime = showReminder && formState.hasReminder
        val showFrequency = formState.isRecurrent
        val showInstallments = formState.canUseInstallments
        val count = 1 + (if (formState.installmentsActive) 0 else 1) + (if (showInstallments) 1 else 0) + (if (showReminder) 1 else 0) + (if (showReminderTime) 1 else 0) + (if (showFrequency) 1 else 0)
        var i = 0
        OptionGroup(title = stringResource(R.string.form_options)) {
            if (!formState.installmentsActive) SwitchRow(
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
            if (showInstallments) {
                val preview = formState.installmentPreview
                SwitchRow(
                    groupShape(i++, count), Icons.Outlined.CreditScore,
                    stringResource(R.string.transaction_in_installments),
                    formState.inInstallments, viewModel::updateInInstallments,
                    supporting = preview?.let {
                        stringResource(
                            R.string.transaction_installments_preview,
                            currencyFormatter.format(it.perClosing),
                            remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
                                .format(it.firstClosing),
                        )
                    },
                )
            }
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

        AnimatedVisibility(visible = formState.installmentsActive) {
            OutlinedTextField(
                value = formState.installmentCount,
                onValueChange = viewModel::updateInstallmentCount,
                label = { Text(stringResource(R.string.transaction_installment_count)) },
                isError = formState.installmentCount.toIntOrNull()?.let { it !in MIN_INSTALLMENTS..MAX_INSTALLMENTS } != false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AnimatedVisibility(visible = formState.isRecurrent && formState.recurrenceFrequency == RecurrenceFrequency.CUSTOM_MONTHS) {
            OutlinedTextField(
                value = formState.recurrenceInterval,
                onValueChange = viewModel::updateRecurrenceInterval,
                label = { Text(stringResource(R.string.transaction_every_n_months)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.extraLarge,
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

    if (showAccountPicker) {
        AccountPickerSheet(
            accounts = formState.accounts,
            selectedId = formState.accountId,
            onPick = { viewModel.setAccount(it); showAccountPicker = false },
            onDismiss = { showAccountPicker = false },
        )
    }

    DateTimePickerDialogs(pickers, formState.date, viewModel::updateDate, viewModel::updateTime)
}

/** The movement's account as a quiet button beside the currency: its icon and [accountLabel]. */
@Composable
private fun AccountChip(account: Account?, accounts: List<Account>, onClick: () -> Unit) {
    val name = account?.let { a -> accountLabel(a, accounts.firstOrNull { it.id == a.parentAccountId }) }
        ?: stringResource(R.string.transaction_account_label)
    val description = stringResource(R.string.transaction_account_chip_description, name)
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(
            account?.let { accountIcon(it.icon) } ?: Icons.Outlined.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp))
    }
}

/**
 * The accounts a movement (or either side of a transfer) can go to, each with its badge, name and
 * currency, and its balance when [balances] has one. A card's second currency follows its card,
 * named by [accountLabel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountPickerSheet(
    accounts: List<Account>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.transaction_account_label),
    balances: Map<Long, Money> = emptyMap(),
) {
    val locale = remember { Locale.getDefault() }
    val ordered = remember(accounts) { withChildrenAfterParents(accounts) }
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            SheetHeader(title = title)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(ordered, key = { it.id }) { account ->
                    val selected = account.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(account.id) })
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AccountBadge(account)
                        Column(Modifier.weight(1f)) {
                            Text(
                                accountLabel(account, account.parentAccountId?.let(byId::get)),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${account.currency.currencyCode} · ${CurrencyCatalog.displayName(account.currency, locale)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        balances[account.id]?.let { AmountText(amount = it, style = BoxTheme.moneyType.small) }
                        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** The form's error line, in the error color. */
@Composable
private fun TransactionFormErrorText(error: TransactionFormError) {
    Text(
        text = errorMessage(error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun errorMessage(error: TransactionFormError): String = when (error) {
    TransactionFormError.InvalidAmount -> stringResource(R.string.transaction_error_invalid_amount)
    TransactionFormError.NeedsRate -> stringResource(R.string.conversion_needs_rate)
    TransactionFormError.AccountLoading -> stringResource(R.string.transaction_error_account_loading)
    TransactionFormError.NotFound -> stringResource(R.string.transaction_error_not_found)
    is TransactionFormError.MissingRate -> stringResource(R.string.transaction_error_missing_rate, error.from.currencyCode)
    TransactionFormError.SaveFailed -> stringResource(R.string.transaction_error_save)
    TransactionFormError.DeleteFailed -> stringResource(R.string.transaction_error_delete)
}
