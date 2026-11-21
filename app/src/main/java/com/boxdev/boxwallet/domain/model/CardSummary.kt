package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.domain.money.Money
import java.math.BigDecimal

/**
 * One currency of a card: [balance] is the card balance (a debt is negative), [usedMinor] is the debt
 * floored at 0. [statement] is null unless the card has both a closing and a due day.
 */
data class CardBalance(val account: Account, val balance: Money, val usedMinor: Long,
    val statement: StatementStatus?,
    /** Installments of plans dated tomorrow or later: not owed yet, but they already take credit. */
    val pendingInstallmentsMinor: Long = 0L,
)

/** [balance] with its used amount: max(0, −balance). */
fun cardBalance(
    account: Account,
    balance: Money,
    statement: StatementStatus?,
    pendingInstallmentsMinor: Long = 0L,
): CardBalance =
    CardBalance(account, balance, (-balance.minor).coerceAtLeast(0L), statement, pendingInstallmentsMinor)

/** A card with its optional second currency; [limit] and [available] are in the card's currency. */
data class CardSummary(
    val card: Account,
    val primary: CardBalance,
    val second: CardBalance?,
    val limit: Money?,
    val available: Money?,
    /** The second currency was converted at an approximate rate. */
    val availableApproximate: Boolean,
    /**
     * Pending installments in the card's currency (the second currency's converted); null when none. Without a
     * rate for the second currency, only the card currency's part (null when that part is zero).
     */
    val pendingInstallments: Money? = null,
)

/** What is used of the main currency, in the card's currency. */
val CardSummary.primaryUsed: Money get() = Money(primary.usedMinor, primary.balance.currency)

/** What is used of the second currency, or null without one or with nothing used: lists show it under the main. */
val CardSummary.secondUsed: Money? get() = second?.takeIf { it.usedMinor > 0 }?.let { Money(it.usedMinor, it.balance.currency) }

/**
 * Available = limit − (used₁ + pending₁) − (used₂ + pending₂) × [rateSecondToPrimary], rounded HALF_EVEN. Null without a limit, or
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
    val committedPrimary = primary.usedMinor + primary.pendingInstallmentsMinor
    val committedSecond = second?.let { Money(it.usedMinor + it.pendingInstallmentsMinor, it.balance.currency) }
    val secondConverted = if (committedSecond != null && rateSecondToPrimary != null) {
        committedSecond.convert(card.currency, rateSecondToPrimary)
    } else {
        null
    }
    val available = when {
        limit == null -> null
        second == null -> Money(limit.minor - committedPrimary, card.currency)
        secondConverted == null -> null
        else -> Money(limit.minor - committedPrimary - secondConverted.minor, card.currency)
    }
    val pendingSecond = second?.let { Money(it.pendingInstallmentsMinor, it.balance.currency) }
    val pending = when {
        pendingSecond == null || pendingSecond.minor == 0L -> primary.pendingInstallmentsMinor
        // Without a rate the second currency's part can't be added; the known part still shows.
        rateSecondToPrimary == null -> primary.pendingInstallmentsMinor
        else -> primary.pendingInstallmentsMinor + pendingSecond.convert(card.currency, rateSecondToPrimary).minor
    }
    return CardSummary(
        card = card,
        primary = primary,
        second = second,
        limit = limit,
        available = available,
        availableApproximate = available != null && second != null && approximate,
        pendingInstallments = pending?.takeIf { it > 0 }?.let { Money(it, card.currency) },
    )
}
