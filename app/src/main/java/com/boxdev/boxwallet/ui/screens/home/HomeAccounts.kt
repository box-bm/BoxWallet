package com.boxdev.boxwallet.ui.screens.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.CardSummary
import com.boxdev.boxwallet.domain.model.primaryUsed
import com.boxdev.boxwallet.domain.model.secondUsed
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.CardUsageBar
import com.boxdev.boxwallet.ui.components.usageFraction
import com.boxdev.boxwallet.ui.screens.accounts.AccountBadge
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import java.util.Currency

/** Home only lists accounts once there is a choice: with a single one it looks as it did before accounts. */
fun showAccountsRow(accounts: List<AccountWithBalance>): Boolean = accounts.size > 1

private val CardWidth = 168.dp
private val CardHeight = 132.dp

/** Room for a card's second-currency line; every tile in the row grows together. */
private val TallCardHeight = 156.dp

/**
 * A horizontal row of account cards (badge, name, balance in the account's currency) ending in
 * "See accounts". A credit card (in [cards]) shows what is used instead (its second currency on a line
 * below when in use), with a thin bar of its limit. A card opens that account's detail.
 */
@Composable
fun HomeAccountsRow(
    accounts: List<AccountWithBalance>,
    onOpenAccount: (Long) -> Unit,
    onSeeAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    cards: Map<Long, CardSummary> = emptyMap(),
) {
    val height = if (accounts.any { cards[it.account.id]?.secondUsed != null }) TallCardHeight else CardHeight
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // End padding lets the last card ("See accounts") scroll fully into view.
        contentPadding = PaddingValues(top = 4.dp, end = 16.dp, bottom = 4.dp),
    ) {
        items(accounts, key = { it.account.id }) { item ->
            AccountCard(item, cards[item.account.id], height, onClick = { onOpenAccount(item.account.id) })
        }
        item(key = "see_accounts") { SeeAccountsCard(height, onSeeAccounts) }
    }
}

@Composable
private fun AccountCard(item: AccountWithBalance, card: CardSummary?, height: Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = BoxTheme.cardColor,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.width(CardWidth).height(height),
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AccountBadge(item.account)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.account.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card == null) {
                    AmountText(amount = item.balance, autoSize = true)
                } else {
                    AmountText(amount = card.primaryUsed, autoSize = true)
                    card.secondUsed?.let { AmountText(amount = it, style = BoxTheme.moneyType.small) }
                    usageFraction(card)?.let { CardUsageBar(it, Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SeeAccountsCard(height: Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.width(CardWidth).height(height),
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            Text(stringResource(R.string.home_see_accounts), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun HomeAccountsRowPreviewContent() {
    BoxWalletTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            val gtq = Currency.getInstance("GTQ")
            val usd = Currency.getInstance("USD")
            fun account(id: Long, name: String, currency: Currency, color: AccountColor, icon: String) =
                Account(id = id, name = name, type = AccountType.CHECKING, currency = currency, createdAt = 0, color = color, icon = icon)
            HomeAccountsRow(
                accounts = listOf(
                    AccountWithBalance(account(1, "Principal", gtq, AccountColor.DEFAULT, "AccountBalance"), Money(1_250_00, gtq)),
                    AccountWithBalance(account(2, "Ahorro USD", usd, AccountColor.TEAL, "Savings"), Money(300_00, usd)),
                    AccountWithBalance(account(3, "Tarjeta", gtq, AccountColor.PINK, "CreditCard"), Money(-420_50, gtq)),
                ),
                onOpenAccount = {},
                onSeeAccounts = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "Light", widthDp = 411)
@Composable
private fun HomeAccountsRowPreview() = HomeAccountsRowPreviewContent()

@Preview(name = "Dark", widthDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeAccountsRowDarkPreview() = HomeAccountsRowPreviewContent()
