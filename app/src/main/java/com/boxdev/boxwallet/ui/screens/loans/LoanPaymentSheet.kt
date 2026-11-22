@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.parseMajor
import com.boxdev.boxwallet.ui.components.DateRow
import com.boxdev.boxwallet.ui.components.DateTimePickerDialogs
import com.boxdev.boxwallet.ui.components.SheetHeader
import com.boxdev.boxwallet.ui.components.TimeRow
import com.boxdev.boxwallet.ui.components.groupShape
import com.boxdev.boxwallet.ui.components.rememberDateTimePickerState
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import java.time.LocalDateTime
import java.util.Locale

/**
 * What the payment sheet is editing. [max] is the most the payment may be: what the loan lacks, plus
 * the payment's own amount while editing it ([editingId] set).
 */
data class LoanPaymentState(
    /** Typed in major units of [max]'s currency. */
    val amount: String,
    val date: LocalDateTime,
    val max: Money,
    val editingId: Long? = null,
) {
    val parsed: Money? get() = parseMajor(amount, max.currency)

    /** More than zero and at most [max]. */
    val isValid: Boolean get() = parsed?.let { it.minor in 1..max.minor } == true

    companion object {
        fun forNew(loan: Loan, prefill: Money, date: LocalDateTime) = LoanPaymentState(
            amount = if (prefill.minor > 0) prefill.toMajor().toPlainString() else "",
            date = date,
            max = loan.remaining,
        )

        fun forEdit(loan: Loan, payment: Transaction) = LoanPaymentState(
            amount = payment.accountAmount.toMajor().toPlainString(),
            date = payment.date,
            max = Money(loan.remaining.minor + payment.accountAmount.minor, loan.total.currency),
            editingId = payment.id,
        )
    }
}

/**
 * Adds a payment, or edits one when [state] carries an [LoanPaymentState.editingId] (then
 * [onDelete] shows). [error] is a refusal from the repository, shown inside the sheet.
 */
@Composable
fun LoanPaymentSheet(
    state: LoanPaymentState,
    error: String?,
    onAmountChange: (String) -> Unit,
    onDateChange: (LocalDateTime) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = remember { Locale.getDefault() }
    val pickers = rememberDateTimePickerState()
    val editing = state.editingId != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.imePadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(
                title = stringResource(if (editing) R.string.loan_payment_edit_title else R.string.loan_payment_new_title),
            )
            OutlinedTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                label = { Text(stringResource(R.string.loan_payment_amount_label)) },
                prefix = { Text(CurrencyCatalog.symbol(state.max.currency, locale)) },
                isError = state.amount.isNotEmpty() && !state.isValid,
                supportingText = {
                    Text(stringResource(R.string.loan_payment_max, com.boxdev.boxwallet.util.rememberCurrencyFormatter().format(state.max)))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (state.isValid) onSave() }),
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DateRow(groupShape(0, 2), stringResource(R.string.loan_payment_date_label), state.date, pickers)
                TimeRow(groupShape(1, 2), state.date, pickers)
            }
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.action_delete)) }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Button(onClick = onSave, enabled = state.isValid, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
    DateTimePickerDialogs(
        state = pickers,
        dateTime = state.date,
        onDateChange = onDateChange,
        onTimeChange = { h, m -> onDateChange(state.date.withHour(h).withMinute(m)) },
    )
}
