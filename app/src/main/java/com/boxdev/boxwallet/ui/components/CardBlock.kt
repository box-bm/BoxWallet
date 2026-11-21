@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.CardBalance
import com.boxdev.boxwallet.domain.model.CardSummary
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.ui.navigation.NavRoutes
import com.boxdev.boxwallet.ui.screens.accounts.AccountBadge
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.CurrencyFormatter
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What is left to pay of [balance]'s statement; null without a statement (or before the first one) or when paid. */
private fun toPayOf(balance: CardBalance): Money? =
    balance.statement?.takeIf { !it.noStatementYet && it.toPayMinor > 0 }
        ?.let { Money(it.toPayMinor, balance.balance.currency) }

/**
 * "This statement: Q1,980.00 · pay by Dec 5", or "Statement paid ✓" once nothing is left to pay; null
 * without a statement or before the first one.
 */
fun statementLine(balance: CardBalance, formatter: CurrencyFormatter, resources: Resources): String? {
    val statement = balance.statement?.takeIf { !it.noStatementYet } ?: return null
    val toPay = toPayOf(balance) ?: return resources.getString(R.string.card_statement_paid)
    val locale = resources.configuration.locales[0]
    return resources.getString(R.string.card_statement_due, formatter.format(toPay), shortDate(statement.dueDate, locale))
}

/** Month and day in [locale]'s order ("Dec 5", "5 dic"), without the abbreviation's period. */
private fun shortDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale).format(date).replace(".", "")

/**
 * A transfer from [payer] into [balance]'s account (the card or its second currency) that delivers what is
 * left of its statement; without one, or once it is paid, the amount is left to the user. A [payer] that is
 * one of [card]'s own accounts is dropped, so the form picks the default instead.
 */
fun payRoute(card: Account, balance: CardBalance, payer: Long?): String =
    NavRoutes.TransferForm.createRoute(
        from = payer?.takeIf { it != card.id && it != balance.account.id },
        to = balance.account.id,
        receive = toPayOf(balance)?.toMajor()?.toPlainString(),
    )

/** Share of the limit in use (both currencies when the rate is known), or null without a limit. */
fun usageFraction(summary: CardSummary): Float? {
    val limit = summary.limit?.minor?.takeIf { it > 0 } ?: return null
    val used = summary.available?.let { limit - it.minor } ?: summary.primary.usedMinor
    return (used.toFloat() / limit).coerceIn(0f, 1f)
}

/** A thin bar of [fraction] of the limit in use, on the progress track tone. */
@Composable
fun CardUsageBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier.fillMaxWidth().height(height),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
        gapSize = if (fraction <= 0f || fraction >= 1f) 0.dp else 2.dp,
    )
}

/**
 * A card's detail block in place of the balance hero: used and available (with the limit bar), then each
 * currency's credit balance, statement line, Pay and Adjust. [tags] and [actions] fill the shared rows.
 */
@Composable
fun CardBlock(
    summary: CardSummary,
    onPay: (CardBalance) -> Unit,
    onAdjust: (CardBalance) -> Unit,
    tags: @Composable () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberCurrencyFormatter()
    val balances = listOfNotNull(summary.primary, summary.second)
    Surface(
        color = BoxTheme.cardColor,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AccountBadge(summary.card, size = 56)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(stringResource(R.string.card_used))
                    AmountText(amount = summary.primary.usedMoney, style = BoxTheme.moneyType.large, autoSize = true)
                    summary.second?.let { AmountText(amount = it.usedMoney, style = BoxTheme.moneyType.small) }
                }
            }
            summary.available?.let { available ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    usageFraction(summary)?.let { CardUsageBar(it, height = 8.dp) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(stringResource(R.string.card_available), Modifier.weight(1f))
                        Text(
                            (if (summary.availableApproximate) "≈ " else "") + formatter.format(available),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
            tags()
            balances.forEach { balance ->
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                CurrencySection(balance, dual = balances.size > 1, formatter, onPay = { onPay(balance) }, onAdjust = { onAdjust(balance) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

private val CardBalance.usedMoney: Money get() = Money(usedMinor, balance.currency)

/** One currency's statement, credit balance and its Pay / Adjust buttons; [dual] heads it with the ISO code. */
@Composable
private fun CurrencySection(
    balance: CardBalance,
    dual: Boolean,
    formatter: CurrencyFormatter,
    onPay: () -> Unit,
    onAdjust: () -> Unit,
) {
    val resources = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (dual) {
            Text(
                balance.balance.currency.currencyCode,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (balance.balance.minor > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.card_credit_balance), Modifier.weight(1f))
                AmountText(amount = balance.balance, style = BoxTheme.moneyType.small, color = BoxTheme.money.income)
            }
        }
        val line = statementLine(balance, formatter, resources)
            ?: stringResource(R.string.card_no_statement_yet).takeIf { balance.statement?.noStatementYet == true }
        line?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val toPay = toPayOf(balance)
            Button(onClick = onPay, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(
                    toPay?.let { stringResource(R.string.card_pay_amount, formatter.format(it)) } ?: stringResource(R.string.card_pay),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onAdjust, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.account_adjust_balance), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
