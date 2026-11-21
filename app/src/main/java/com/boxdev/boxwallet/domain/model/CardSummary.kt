package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.domain.money.Money
import java.math.BigDecimal

/**
 * One currency of a card: [balance] is the card balance (a debt is negative), [usedMinor] is the debt
 * floored at 0. [statement] is null unless the card has both a closing and a due day.
 */
data class CardBalance(val account: Account, val balance: Money, val usedMinor: Long, val statement: StatementStatus?)

/** [balance] with its used amount: max(0, −balance). */
fun cardBalance(account: Account, balance: Money, statement: StatementStatus?): CardBalance =
    CardBalance(account, balance, (-balance.minor).coerceAtLeast(0L), statement)

/** A card with its optional second currency; [limit] and [available] are in the card's currency. */
data class CardSummary(
    val card: Account,
    val primary: CardBalance,
    val second: CardBalance?,
    val limit: Money?,
    val available: Money?,
    /** The second currency was converted at an approximate rate. */
    val availableApproximate: Boolean,
)

/**
 * Available = limit − used₁ − used₂ × [rateSecondToPrimary], rounded HALF_EVEN. Null without a limit, or
 * when there is a [second] currency but no rate.
 */
fun cardSummary(
    card: Account,
    primary: CardBalance,
    second: CardBalance?,
    rateSecondToPrimary: BigDecimal?,
    approximate: Boolean,
): CardSummary {
    val limit = card.creditLimitMinor?.let { Money(it, card.currency) }
    val available = when {
        limit == null -> null
        second == null -> Money(limit.minor - primary.usedMinor, card.currency)
        rateSecondToPrimary == null -> null
        else -> {
            val usedSecond = Money(second.usedMinor, second.balance.currency).convert(card.currency, rateSecondToPrimary)
            Money(limit.minor - primary.usedMinor - usedSecond.minor, card.currency)
        }
    }
    return CardSummary(
        card = card,
        primary = primary,
        second = second,
        limit = limit,
        available = available,
        availableApproximate = available != null && second != null && approximate,
    )
}
