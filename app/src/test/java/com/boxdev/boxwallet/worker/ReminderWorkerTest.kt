package com.boxdev.boxwallet.worker

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.StatementStatus
import com.boxdev.boxwallet.domain.model.cardBalance
import com.boxdev.boxwallet.domain.model.cardSummary
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

class ReminderWorkerTest {

    private val gtq = Currency.getInstance("GTQ")
    private val usd = Currency.getInstance("USD")
    private val card = Account(
        id = 1, name = "Visa BAC", type = AccountType.CREDIT_CARD, currency = gtq, createdAt = 0,
        statementClosingDay = 20, paymentDueDay = 5,
    )
    private val child = card.copy(id = 2, currency = usd, parentAccountId = 1, statementClosingDay = null, paymentDueDay = null)
    private val formatter = CurrencyFormatter(gtq, Locale.US)

    private fun statement(toPay: Long, noStatementYet: Boolean = false) = StatementStatus(
        lastClosing = LocalDate.of(2026, 11, 20),
        dueDate = LocalDate.of(2026, 12, 5),
        statementDebtMinor = toPay,
        paidSinceMinor = 0,
        toPayMinor = toPay,
        noStatementYet = noStatementYet,
    )

    private fun summary(primary: StatementStatus?, second: StatementStatus?) = cardSummary(
        card,
        cardBalance(card, Money(-5_000_00, gtq), primary),
        cardBalance(child, Money(-200_00, usd), second),
        null,
        approximate = false,
    )

    @Test
    fun bothCurrenciesAreJoined() {
        assertEquals("Q1,980.00 + $120.00", cardAmountsToPay(summary(statement(1_980_00), statement(120_00)), formatter))
    }

    @Test
    fun aCurrencyWithNothingToPayIsLeftOut() {
        assertEquals("Q1,980.00", cardAmountsToPay(summary(statement(1_980_00), statement(0)), formatter))
        assertEquals("$120.00", cardAmountsToPay(summary(statement(0), statement(120_00)), formatter))
        assertEquals("$120.00", cardAmountsToPay(summary(statement(500_00, noStatementYet = true), statement(120_00)), formatter))
        assertEquals("Q1,980.00", cardAmountsToPay(summary(statement(1_980_00), null), formatter))
    }
}
