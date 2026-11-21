package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Currency

class CardReminderTest {

    private val gtq = Currency.getInstance("GTQ")
    private val usd = Currency.getInstance("USD")
    private val closing = LocalDate.of(2026, 11, 15)
    private val due = LocalDate.of(2026, 12, 5)

    private fun card(days: Int? = 3, archived: Boolean = false) = Account(
        id = 1, name = "Visa BAC", type = AccountType.CREDIT_CARD, currency = gtq, createdAt = 0L,
        isArchived = archived, statementClosingDay = 15, paymentDueDay = 5, dueReminderDaysBefore = days,
    )

    private fun status(toPay: Long, noStatement: Boolean = false) = StatementStatus(
        lastClosing = closing, dueDate = due, statementDebtMinor = toPay, paidSinceMinor = 0,
        toPayMinor = toPay, noStatementYet = noStatement,
    )

    private fun summary(
        card: Account = card(),
        primaryToPay: Long = 198000,
        secondToPay: Long? = null,
        noStatement: Boolean = false,
    ): CardSummary {
        val primary = CardBalance(card, Money(-primaryToPay, gtq), primaryToPay, status(primaryToPay, noStatement))
        val second = secondToPay?.let {
            val child = card.copy(id = 2, currency = usd, parentAccountId = 1)
            CardBalance(child, Money(-it, usd), it, status(it))
        }
        return CardSummary(card, primary, second, null, null, false)
    }

    private fun due(s: CardSummary, today: LocalDate, notified: LocalDate? = null) = cardReminderDue(s, today, notified)

    @Test fun firesWithinWindow() = assertTrue(due(summary(), LocalDate.of(2026, 12, 2)))

    @Test fun firesOnDueDay() = assertTrue(due(summary(), due))

    @Test fun notBeforeWindow() = assertFalse(due(summary(), LocalDate.of(2026, 12, 1)))

    @Test fun notAfterDue() = assertFalse(due(summary(), LocalDate.of(2026, 12, 6)))

    @Test fun oncePerStatement() {
        val today = LocalDate.of(2026, 12, 2)
        assertTrue(due(summary(), today, null))
        assertFalse(due(summary(), today, closing))
        assertTrue(due(summary(), today, closing.minusMonths(1)))
    }

    @Test fun notWhenNothingToPay() = assertFalse(due(summary(primaryToPay = 0), LocalDate.of(2026, 12, 2)))

    @Test fun notWhenNoReminderDays() = assertFalse(due(summary(card = card(days = null)), LocalDate.of(2026, 12, 2)))

    @Test fun notWhenArchived() = assertFalse(due(summary(card = card(archived = true)), LocalDate.of(2026, 12, 2)))

    @Test fun notWhenNoStatementYet() = assertFalse(due(summary(noStatement = true), LocalDate.of(2026, 12, 2)))

    @Test fun secondCurrencyAloneTriggers() =
        assertTrue(due(summary(primaryToPay = 0, secondToPay = 2500), LocalDate.of(2026, 12, 2)))
}
