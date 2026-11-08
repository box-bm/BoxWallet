@file:OptIn(ExperimentalMaterial3Api::class)

package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.domain.money.PickerSections
import java.util.Currency
import java.util.Locale

/** Bottom sheet to choose a currency: search field, recently used, then every currency. */
@Composable
fun CurrencyPickerSheet(
    selected: Currency,
    sections: PickerSections,
    onQuery: (String) -> Unit,
    onPick: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val locale = remember { Locale.getDefault() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.9f).imePadding().padding(horizontal = 16.dp)) {
            SheetHeader(title = stringResource(R.string.currency_picker_title))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onQuery(it) },
                placeholder = { Text(stringResource(R.string.currency_picker_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                shape = MaterialTheme.shapes.large,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.padding(top = 8.dp))
            if (sections.recent.isEmpty() && sections.all.isEmpty()) {
                Text(
                    stringResource(R.string.currency_picker_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (sections.recent.isNotEmpty()) {
                    item(key = "recent-header") { SectionLabel(stringResource(R.string.currency_picker_recent)) }
                    items(sections.recent, key = { "r-" + it.currencyCode }) {
                        CurrencyRow(it, it == selected, locale) { onPick(it) }
                    }
                    if (sections.all.isNotEmpty()) {
                        item(key = "all-header") { SectionLabel(stringResource(R.string.currency_picker_all)) }
                    }
                }
                items(sections.all, key = { "a-" + it.currencyCode }) {
                    CurrencyRow(it, it == selected, locale) { onPick(it) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
    )
}

@Composable
private fun CurrencyRow(currency: Currency, isSelected: Boolean, locale: Locale, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            CurrencyCatalog.symbol(currency, locale),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(48.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(Modifier.weight(1f)) {
            Text(CurrencyCatalog.displayName(currency, locale), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(currency.currencyCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isSelected) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
