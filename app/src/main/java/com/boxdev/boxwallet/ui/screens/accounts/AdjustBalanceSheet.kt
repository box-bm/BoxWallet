@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.ui.components.SheetHeader
import java.util.Locale

/**
 * Asks for an account's real balance, prefilled with [current]. [onSave] gets it in the account's
 * currency; the caller adds the adjustment for the difference.
 */
@Composable
fun AdjustBalanceSheet(
    current: Money,
    onSave: (Money) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = remember { Locale.getDefault() }
    var text by rememberSaveable { mutableStateOf(current.toMajor().toPlainString()) }
    var showError by remember { mutableStateOf(false) }
    val parsed = parseBalance(text, current.currency)
    val submit = { if (parsed != null) onSave(parsed) else showError = true }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.imePadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(title = stringResource(R.string.account_adjust_balance))
            Text(
                stringResource(R.string.account_adjust_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("""^-?[\d,]*\.?\d*$"""))) {
                        text = value
                        showError = false
                    }
                },
                label = { Text(stringResource(R.string.account_balance_label)) },
                prefix = { Text(CurrencyCatalog.symbol(current.currency, locale)) },
                isError = showError,
                supportingText = if (showError) ({ Text(stringResource(R.string.account_balance_invalid)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = submit, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
