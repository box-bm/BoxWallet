@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.transfer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.ui.components.AmountField
import com.boxdev.boxwallet.ui.components.AmountKind
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.ConfirmDeleteDialog
import com.boxdev.boxwallet.ui.components.ConversionRow
import com.boxdev.boxwallet.ui.components.DateRow
import com.boxdev.boxwallet.ui.components.DateTimePickerDialogs
import com.boxdev.boxwallet.ui.components.FullScreenForm
import com.boxdev.boxwallet.ui.components.OptionGroup
import com.boxdev.boxwallet.ui.components.TimeRow
import com.boxdev.boxwallet.ui.components.groupShape
import com.boxdev.boxwallet.ui.components.rememberDateTimePickerState
import com.boxdev.boxwallet.ui.screens.accounts.AccountBadge
import com.boxdev.boxwallet.ui.screens.transaction.AccountPickerSheet
import com.boxdev.boxwallet.ui.theme.BoxTheme
import java.util.Locale

/**
 * Full-screen create/edit form for a transfer between two accounts. [onDone] runs after ✕, a save
 * or a confirmed delete, and returns whether it navigated (see the movement form).
 */
@Composable
fun TransferFormScreen(
    onDone: () -> Boolean,
    viewModel: TransferFormViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val currentOnDone by rememberUpdatedState(onDone)
    val scrollState = rememberScrollState()
    var finished by rememberSaveable { mutableStateOf(false) }
    val finish: () -> Unit = { if (!finished && currentOnDone()) finished = true }
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(formState.isSaved, resumed) { if (formState.isSaved && resumed) finish() }
    LaunchedEffect(formState.error) { if (formState.error != null) scrollState.animateScrollTo(0) }

    FullScreenForm(
        title = stringResource(if (formState.isEditing) R.string.transfer_edit_title else R.string.transfer_new_title),
        onClose = finish,
        onSave = viewModel::save,
        onDelete = if (formState.isEditing) ({ showDeleteDialog = true }) else null,
        hasUnsavedChanges = hasUnsavedChanges,
        deleteLabel = stringResource(R.string.transfer_delete_title),
        scrollState = scrollState,
    ) {
        if (formState.isLoading) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@FullScreenForm
        }
        formState.error?.let { TransferFormErrorText(it) }
        TransferFormFields(viewModel)
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.transfer_delete_title),
            message = stringResource(R.string.transfer_delete_body),
            onConfirm = viewModel::delete,
            onDismiss = { showDeleteDialog = false },
        )
    }
}

private enum class Side { From, To }

@Composable
private fun TransferFormFields(viewModel: TransferFormViewModel) {
    val formState by viewModel.formState.collectAsState()
    val pickers = rememberDateTimePickerState()
    var picking by remember { mutableStateOf<Side?>(null) }
    val locale = remember { Locale.getDefault() }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AccountSlot(
                label = stringResource(R.string.transfer_from),
                item = formState.accounts.firstOrNull { it.account.id == formState.fromAccountId },
                onClick = { picking = Side.From },
            )
            FilledTonalIconButton(onClick = viewModel::swap, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(R.string.transfer_swap))
            }
            AccountSlot(
                label = stringResource(R.string.transfer_to),
                item = formState.accounts.firstOrNull { it.account.id == formState.toAccountId },
                onClick = { picking = Side.To },
            )
        }

        val from = formState.fromCurrency
        AmountField(
            value = formState.amount,
            onValueChange = viewModel::updateAmount,
            symbol = from?.let { CurrencyCatalog.symbol(it, locale) }.orEmpty(),
            kind = AmountKind.Neutral,
            isError = formState.error != null && formState.money == null,
            autoFocus = !formState.isEditing,
        )

        val to = formState.toCurrency
        if (formState.isForeign && from != null && to != null) {
            ConversionRow(
                rate = formState.rate,
                from = from,
                to = to,
                day = formState.date.toLocalDate(),
                equivalent = formState.equivalent.orEmpty(),
                onRateChange = viewModel::updateManualRate,
                onEquivalentChange = viewModel::updateEquivalent,
                onUseAutomatic = viewModel::useAutomaticRate,
                manualRate = formState.manualRate,
                needsManualRate = formState.needsManualRate,
            )
        }

        OptionGroup(title = stringResource(R.string.form_when)) {
            DateRow(groupShape(0, 2), stringResource(R.string.transaction_date_label), formState.date, pickers)
            TimeRow(groupShape(1, 2), formState.date, pickers)
        }

        OutlinedTextField(
            value = formState.note,
            onValueChange = viewModel::updateNote,
            label = { Text(stringResource(R.string.transaction_description_label)) },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }

    picking?.let { side ->
        AccountPickerSheet(
            accounts = formState.accounts.map { it.account },
            selectedId = if (side == Side.From) formState.fromAccountId else formState.toAccountId,
            onPick = {
                if (side == Side.From) viewModel.setFrom(it) else viewModel.setTo(it)
                picking = null
            },
            onDismiss = { picking = null },
            title = stringResource(if (side == Side.From) R.string.transfer_from else R.string.transfer_to),
            balances = remember(formState.accounts) { formState.accounts.associate { it.account.id to it.balance } },
        )
    }

    DateTimePickerDialogs(pickers, formState.date, viewModel::updateDate, viewModel::updateTime)
}

/** One side of the transfer: its label, the account's badge and name, and its balance. */
@Composable
private fun AccountSlot(label: String, item: AccountWithBalance?, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (item != null) {
                AccountBadge(item.account)
            } else {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    item?.account?.name ?: stringResource(R.string.transaction_account_label),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item?.let { AmountText(amount = it.balance, style = BoxTheme.moneyType.small) }
        }
    }
}

@Composable
private fun TransferFormErrorText(error: TransferFormError) {
    val text = when (error) {
        TransferFormError.SameAccount -> stringResource(R.string.transfer_error_same_account)
        TransferFormError.InvalidAmount -> stringResource(R.string.transaction_error_invalid_amount)
        TransferFormError.NeedsRate -> stringResource(R.string.conversion_needs_rate)
        TransferFormError.AccountLoading -> stringResource(R.string.transaction_error_account_loading)
        TransferFormError.NotFound -> stringResource(R.string.transfer_error_not_found)
        is TransferFormError.MissingRate -> stringResource(R.string.transaction_error_missing_rate, error.from.currencyCode)
        TransferFormError.SaveFailed -> stringResource(R.string.transaction_error_save)
        TransferFormError.DeleteFailed -> stringResource(R.string.transaction_error_delete)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
