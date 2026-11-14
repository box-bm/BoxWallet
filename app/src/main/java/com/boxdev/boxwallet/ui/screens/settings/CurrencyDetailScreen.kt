@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.ui.components.charts.RateLineChart
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurrencyDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: CurrencyDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val locale = remember { Locale.getDefault() }
    val currency = remember(state.code) { runCatching { Currency.getInstance(state.code) }.getOrNull() }
    val name = currency?.let { CurrencyCatalog.displayName(it, locale) } ?: state.code
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    var selected by remember(state.series) { mutableIntStateOf(state.series.lastIndex.coerceAtLeast(0)) }

    SettingsScaffold(
        title = stringResource(R.string.currency_detail_title, state.code),
        subtitle = name,
        onNavigateBack = onNavigateBack,
    ) {
        val stats = state.stats
        when {
            state.code == state.main.currencyCode ->
                Note(stringResource(R.string.currency_detail_same))
            stats == null -> {
                RangeChips(state.range, viewModel::selectRange)
                if (state.loaded) Note(stringResource(R.string.currency_detail_empty))
            }
            else -> {
                fun fmt(v: BigDecimal) = formatRate(v, state.main, locale)
                val point = state.series[selected.coerceIn(0, state.series.lastIndex)]
                val summary = stringResource(
                    R.string.rate_chart_summary, state.code,
                    state.series.first().first.format(dateFormat), state.series.last().first.format(dateFormat),
                    fmt(stats.current), fmt(stats.min), fmt(stats.max),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.currencies_rate_line, state.code, fmt(point.second)),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            point.first.format(dateFormat),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ChangeLabel(stats.dailyChangePercent)
                    }
                }
                RangeChips(state.range, viewModel::selectRange)
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLargeIncreased) {
                    RateLineChart(
                        series = state.series,
                        summary = summary,
                        selected = selected,
                        onSelect = { selected = it },
                        modifier = Modifier.padding(16.dp),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile(stringResource(R.string.currency_detail_current), fmt(stats.current))
                    StatTile(stringResource(R.string.currency_detail_min), fmt(stats.min))
                    StatTile(stringResource(R.string.currency_detail_max), fmt(stats.max))
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeChips(selected: RateRange, onSelect: (RateRange) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RateRange.entries.forEach { range ->
            val label = stringResource(
                when (range) {
                    RateRange.M1 -> R.string.currency_range_1m
                    RateRange.M3 -> R.string.currency_range_3m
                    RateRange.Y1 -> R.string.currency_range_1y
                    RateRange.ALL -> R.string.currency_range_all
                }
            )
            val a11y = stringResource(R.string.currency_range_a11y, label)
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = a11y },
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
