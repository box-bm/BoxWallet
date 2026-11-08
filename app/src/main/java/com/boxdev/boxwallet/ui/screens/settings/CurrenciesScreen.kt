@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.domain.money.pickerSections
import com.boxdev.boxwallet.ui.components.CurrencyPickerSheet
import com.boxdev.boxwallet.ui.components.segmentShapes
import com.boxdev.boxwallet.ui.theme.BoxTheme
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

@Composable
fun CurrenciesScreen(
    onNavigateBack: () -> Unit,
    onOpenCurrency: (String) -> Unit,
    viewModel: CurrenciesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locale = remember { Locale.getDefault() }
    val snackbar = remember { SnackbarHostState() }
    var showPicker by remember { mutableStateOf(false) }
    val failedText = stringResource(R.string.currencies_refresh_failed)

    LaunchedEffect(state.syncFailed) {
        if (state.syncFailed) {
            snackbar.showSnackbar(failedText)
            viewModel.failureShown()
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_currencies_title),
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbar,
    ) {
        SettingsGroup(title = null) {
            // The change flow arrives with a later task; until then the row only shows the value.
            SegmentedListItem(
                onClick = {},
                enabled = false,
                shapes = segmentShapes(index = 0, count = 1),
                colors = settingsRowColors(),
                leadingContent = { SettingsIcon(Icons.Outlined.CurrencyExchange) },
                supportingContent = { Text(CurrencyCatalog.displayName(state.main, locale)) },
                trailingContent = { Text(state.main.currencyCode, style = MaterialTheme.typography.titleMedium) },
            ) { Text(stringResource(R.string.currencies_main_currency)) }
        }

        SettingsGroup(title = null) {
            val synced = state.lastSyncAt?.let {
                stringResource(
                    R.string.currencies_updated_at,
                    DateUtils.getRelativeDateTimeString(
                        context, it, DateUtils.DAY_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0,
                    ).toString(),
                )
            } ?: stringResource(R.string.currencies_never_updated)
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    synced,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = viewModel::refresh,
                    enabled = !state.syncing,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    if (state.syncing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        androidx.compose.material3.Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        stringResource(if (state.syncing) R.string.currencies_refreshing else R.string.currencies_refresh),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        SettingsGroup(stringResource(R.string.currencies_recent_section)) {
            if (state.rows.isEmpty()) {
                Text(
                    stringResource(R.string.currencies_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            state.rows.forEachIndexed { i, row ->
                val rate = formatRate(row.current, state.main, locale)
                SegmentedListItem(
                    onClick = { onOpenCurrency(row.currency.currencyCode) },
                    shapes = segmentShapes(index = i, count = state.rows.size),
                    colors = settingsRowColors(),
                    supportingContent = {
                        Text(stringResource(R.string.currencies_rate_line, row.currency.currencyCode, rate))
                    },
                    trailingContent = { ChangeLabel(row.dailyChangePercent) },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(CurrencyCatalog.displayName(row.currency, locale)) }
            }
        }

        SettingsGroup(title = null) {
            SettingsRow(
                0, 1, Icons.Outlined.Search,
                stringResource(R.string.currencies_search),
                stringResource(R.string.currencies_search_subtitle),
                onClick = { showPicker = true },
            )
        }

        Text(
            stringResource(R.string.currencies_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    if (showPicker) {
        var query by remember { mutableStateOf("") }
        val all = remember(locale) { CurrencyCatalog.all(locale) }
        val sections = remember(query, state.recent, all) { pickerSections(all, state.recent, query, locale) }
        CurrencyPickerSheet(
            selected = state.main,
            sections = sections,
            onQuery = { query = it },
            onPick = { showPicker = false; onOpenCurrency(it.currencyCode) },
            onDismiss = { showPicker = false },
        )
    }
}

/** Daily change as arrow plus signed text, so colour is never the only signal. */
@Composable
internal fun ChangeLabel(percent: BigDecimal?, modifier: Modifier = Modifier) {
    if (percent == null) return
    val abs = percent.abs().toPlainString()
    val (text, a11y, color) = when (percent.signum()) {
        1 -> Triple(
            stringResource(R.string.currencies_change_up, abs),
            stringResource(R.string.currencies_change_up_a11y, abs),
            BoxTheme.money.income,
        )
        -1 -> Triple(
            stringResource(R.string.currencies_change_down, abs),
            stringResource(R.string.currencies_change_down_a11y, abs),
            BoxTheme.money.expense,
        )
        else -> Triple(
            stringResource(R.string.currencies_change_flat, abs),
            stringResource(R.string.currencies_change_flat_a11y),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier.semantics { contentDescription = a11y },
    )
}

/** `Q7.6400`-style: enough decimals to read small rates, trailing zeros trimmed down to two. */
internal fun formatRate(rate: BigDecimal, main: Currency, locale: Locale): String {
    val scale = when {
        rate.abs() >= BigDecimal(100) -> 2
        rate.abs() >= BigDecimal.ONE -> 4
        else -> 6
    }
    var v = rate.setScale(scale, java.math.RoundingMode.HALF_EVEN).stripTrailingZeros()
    if (v.scale() < 2) v = v.setScale(2)
    val body = java.text.DecimalFormat("#,##0.00####", java.text.DecimalFormatSymbols(Locale.US)).format(v)
    return CurrencyCatalog.symbol(main, locale) + body
}
