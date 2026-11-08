package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.RateSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val DECIMAL = Regex("^\\d*\\.?\\d*$")

/**
 * Shows `1 EUR = 1.0852 USD · 7 nov` with editable rate and equivalent (in [to], the account's
 * currency). [manualRate] is what the user typed, if anything; otherwise [rate] is shown.
 * [rate] is null while no automatic rate exists, which [needsManualRate] flags.
 */
@Composable
fun ConversionRow(
    rate: ExchangeRate?,
    from: Currency,
    to: Currency,
    day: LocalDate,
    equivalent: String,
    onRateChange: (String) -> Unit,
    onEquivalentChange: (String) -> Unit,
    onUseAutomatic: () -> Unit,
    modifier: Modifier = Modifier,
    manualRate: String? = null,
    needsManualRate: Boolean = false,
) {
    val locale = remember { Locale.getDefault() }
    val rateText = manualRate ?: rate?.value?.display().orEmpty()
    val date = remember(day, locale) { day.format(DateTimeFormatter.ofPattern("d MMM", locale)) }
    val approximate = manualRate == null && rate?.source == RateSource.APPROXIMATE
    val missing = needsManualRate && manualRate == null

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    missing -> {
                        Text(
                            stringResource(R.string.conversion_needs_rate),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(R.string.conversion_needs_rate_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.conversion_rate_summary, from.currencyCode, rateText.ifEmpty { "—" }, to.currencyCode, date),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (manualRate != null) {
                            Text(stringResource(R.string.conversion_manual), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (approximate) {
                            Text(stringResource(R.string.conversion_approximate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            Text(
                                stringResource(R.string.conversion_approximate_explanation),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { if (DECIMAL.matches(it)) onRateChange(it) },
                    label = { Text(stringResource(R.string.conversion_rate_label)) },
                    isError = missing,
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = equivalent,
                    onValueChange = { if (DECIMAL.matches(it)) onEquivalentChange(it) },
                    label = { Text(stringResource(R.string.conversion_equivalent_label, to.currencyCode)) },
                    prefix = { Text("≈ " + CurrencyCatalog.symbol(to, locale)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            if (manualRate != null) {
                TextButton(onClick = onUseAutomatic, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.conversion_use_automatic))
                }
            }
        }
    }
}

private fun BigDecimal.display(): String =
    setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
