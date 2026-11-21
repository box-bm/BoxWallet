package com.boxdev.boxwallet.ui.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.StatementStatus
import com.boxdev.boxwallet.domain.model.cardBalance
import com.boxdev.boxwallet.domain.model.cardSummary
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardBlockTextTest {

    private val gtq = Currency.getInstance("GTQ")
    private val card = Account(
        id = 3, name = "Visa BAC", type = AccountType.CREDIT_CARD, currency = gtq, createdAt = 0,
        statementClosingDay = 20, paymentDueDay = 5,
    )
    private val formatter = CurrencyFormatter(gtq, Locale.US)
    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    private fun statement(toPay: Long, debt: Long = toPay, noStatementYet: Boolean = false) = StatementStatus(
        lastClosing = LocalDate.of(2026, 11, 20),
        dueDate = LocalDate.of(2026, 12, 5),
        statementDebtMinor = debt,
        paidSinceMinor = debt - toPay,
        toPayMinor = toPay,
        noStatementYet = noStatementYet,
    )

    private fun balance(statement: StatementStatus?) = cardBalance(card, Money(-2_500_00, gtq), statement)

    @Test
    fun statementLineDueAndPaid() {
        assertEquals("This statement: Q1,980.00 · pay by Dec 5", statementLine(balance(statement(1_980_00)), formatter, resources))
        assertEquals("Statement paid ✓", statementLine(balance(statement(0, debt = 1_980_00)), formatter, resources))
    }

    @Test
    @Config(qualifiers = "es")
    fun statementLineInSpanish() {
        assertEquals("Este corte: Q1,980.00 · pagar antes del 5 dic", statementLine(balance(statement(1_980_00)), formatter, resources))
        assertEquals("Corte pagado ✓", statementLine(balance(statement(0, debt = 1_980_00)), formatter, resources))
    }

    @Test
    fun noStatementYetHasNoLine() {
        assertNull(statementLine(balance(statement(0, debt = 0, noStatementYet = true)), formatter, resources))
        assertNull(statementLine(balance(null), formatter, resources))
    }

    @Test
    fun payRouteOmitsReceiveWhenNothingToPay() {
        assertEquals("transfer_form?from=5&to=3", payRoute(card, balance(statement(0, debt = 1_980_00)), payer = 5))
        assertEquals("transfer_form?from=5&to=3", payRoute(card, balance(null), payer = 5))
        assertEquals("transfer_form?to=3", payRoute(card, balance(statement(0, debt = 0, noStatementYet = true)), payer = null))
        assertEquals("transfer_form?from=5&to=3&receive=1980.00", payRoute(card, balance(statement(1_980_00)), payer = 5))
    }

    @Test
    fun dualCardWithoutRateStillHasAUsageBar() {
        val usd = Currency.getInstance("USD")
        val limited = card.copy(creditLimitMinor = 10_000_00)
        val child = card.copy(id = 4, currency = usd, parentAccountId = 3)
        val summary = cardSummary(
            limited, cardBalance(limited, Money(-2_500_00, gtq), null), cardBalance(child, Money(-100_00, usd), null),
            rateSecondToPrimary = null, approximate = false,
        )

        // No Available without the rate, but the bar (main currency only) shows in the detail as on Home.
        assertNull(summary.available)
        assertEquals(0.25f, usageFraction(summary)!!, 0.0001f)
    }

    @Test
    fun theUsageBarCountsPendingInstallments() {
        val usd = Currency.getInstance("USD")
        val limited = card.copy(creditLimitMinor = 10_000_00)
        val single = cardSummary(
            limited, cardBalance(limited, Money(-2_000_00, gtq), null, pendingInstallmentsMinor = 1_000_00), null,
            rateSecondToPrimary = null, approximate = false,
        )
        assertEquals(0.30f, usageFraction(single)!!, 0.0001f)

        // Without the second currency's rate the bar still counts the main currency's pending installments.
        val child = card.copy(id = 4, currency = usd, parentAccountId = 3)
        val noRate = cardSummary(
            limited, cardBalance(limited, Money(-2_000_00, gtq), null, pendingInstallmentsMinor = 500_00),
            cardBalance(child, Money(-100_00, usd), null), rateSecondToPrimary = null, approximate = false,
        )
        assertEquals(0.25f, usageFraction(noRate)!!, 0.0001f)
    }
}
