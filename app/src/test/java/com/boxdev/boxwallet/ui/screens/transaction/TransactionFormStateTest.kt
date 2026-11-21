package com.boxdev.boxwallet.ui.screens.transaction

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.money.Money
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency

class TransactionFormStateTest {

    private fun state(
        amount: String = "100",
        isRecurrent: Boolean = false,
        recurrenceFrequency: RecurrenceFrequency? = null,
        recurrenceInterval: String = "1"
    ) = TransactionFormState(
        amount = amount,
        currency = Currency.getInstance("GTQ"),
        isRecurrent = isRecurrent,
        recurrenceFrequency = recurrenceFrequency,
        recurrenceInterval = recurrenceInterval
    )

    // isValid - basic amount validation

    @Test
    fun `isValid true with valid amount`() {
        assertTrue(state().isValid)
    }

    @Test
    fun `isValid true with decimal amount`() {
        assertTrue(state(amount = "99.99").isValid)
    }

    @Test
    fun `isValid false with empty amount`() {
        assertFalse(state(amount = "").isValid)
    }

    @Test
    fun `isValid false with zero amount`() {
        assertFalse(state(amount = "0").isValid)
    }

    @Test
    fun `isValid false with non-numeric amount`() {
        assertFalse(state(amount = "abc").isValid)
    }

    @Test
    fun `isValid false with negative amount`() {
        assertFalse(state(amount = "-50").isValid)
    }

    // isValid - recurrence validation

    @Test
    fun `isValid true when recurrent with frequency set`() {
        assertTrue(state(isRecurrent = true, recurrenceFrequency = RecurrenceFrequency.MONTHLY).isValid)
    }

    @Test
    fun `isValid false when recurrent without frequency`() {
        assertFalse(state(isRecurrent = true, recurrenceFrequency = null).isValid)
    }

    @Test
    fun `isValid true when recurrent CUSTOM_MONTHS with valid interval`() {
        assertTrue(
            state(
                isRecurrent = true,
                recurrenceFrequency = RecurrenceFrequency.CUSTOM_MONTHS,
                recurrenceInterval = "3"
            ).isValid
        )
    }

    @Test
    fun `isValid false when recurrent CUSTOM_MONTHS with zero interval`() {
        assertFalse(
            state(
                isRecurrent = true,
                recurrenceFrequency = RecurrenceFrequency.CUSTOM_MONTHS,
                recurrenceInterval = "0"
            ).isValid
        )
    }

    @Test
    fun `isValid false when recurrent CUSTOM_MONTHS with empty interval`() {
        assertFalse(
            state(
                isRecurrent = true,
                recurrenceFrequency = RecurrenceFrequency.CUSTOM_MONTHS,
                recurrenceInterval = ""
            ).isValid
        )
    }

    @Test
    fun `isValid true when not recurrent ignores frequency`() {
        assertTrue(state(isRecurrent = false, recurrenceFrequency = null).isValid)
    }

    // Conversions (Task 7)

    private val eur = Currency.getInstance("EUR")
    private val usd = Currency.getInstance("USD")

    private fun foreign(amount: String = "20.00") =
        TransactionFormState(amount = amount, currency = eur, accountCurrency = usd)

    @Test
    fun `canSave false until the account currency is known`() {
        assertFalse(state().canSave)
        assertTrue(state().copy(accountCurrency = Currency.getInstance("GTQ")).canSave)
    }

    @Test
    fun `canSave false when a manual rate is needed and missing`() {
        val s = foreign().copy(needsManualRate = true)
        assertFalse(s.canSave)
        assertFalse(s.withManualRate("0").canSave)
        assertTrue(s.withManualRate("1.1").canSave)
    }

    @Test
    fun `editing the equivalent derives the manual rate`() {
        val s = foreign().withEquivalent("21.70")
        assertEquals("1.085", s.manualRate)
        assertEquals(0, BigDecimal("1.085").compareTo(s.manualRateValue))
    }

    @Test
    fun `editing the manual rate recomputes the equivalent`() {
        assertEquals("22.00", foreign().withManualRate("1.1").equivalent)
    }

    @Test
    fun `manual rate is ignored when currency matches the account`() {
        val s = TransactionFormState(amount = "20", currency = usd, accountCurrency = usd, manualRate = "1.1")
        assertNull(s.manualRateValue)
    }

    // installments

    private val gtq = Currency.getInstance("GTQ")
    private val card = Account(id = 1, name = "Visa", type = AccountType.CREDIT_CARD, currency = gtq, createdAt = 0, statementClosingDay = 15)

    private fun cardState(
        account: Account = card,
        type: TransactionType = TransactionType.EXPENSE,
        isEditing: Boolean = false,
        isRecurrent: Boolean = false,
        count: String = "3",
        date: LocalDateTime = LocalDateTime.of(2026, 11, 16, 9, 30),
    ) = TransactionFormState(
        amount = "1000",
        currency = gtq,
        accountCurrency = account.currency,
        accountId = account.id,
        pickedAccountId = account.id,
        accounts = listOf(account),
        type = type,
        isEditing = isEditing,
        isRecurrent = isRecurrent,
        inInstallments = true,
        installmentCount = count,
        date = date,
    )

    @Test
    fun `installments are offered only for a new card expense with a closing day`() {
        assertTrue(cardState().canUseInstallments)
        assertFalse(cardState(account = card.copy(type = AccountType.CHECKING)).canUseInstallments)
        assertFalse(cardState(type = TransactionType.INCOME).canUseInstallments)
        assertFalse(cardState(isEditing = true).canUseInstallments)
        assertFalse(cardState(isRecurrent = true).canUseInstallments)
        assertFalse(cardState(account = card.copy(statementClosingDay = null)).canUseInstallments)
    }

    @Test
    fun `a card child takes its closing day from the parent`() {
        val child = Account(id = 2, name = "Visa", type = AccountType.CREDIT_CARD, currency = Currency.getInstance("USD"), createdAt = 0, parentAccountId = 1)
        val s = cardState(account = child).copy(accounts = listOf(card, child))
        assertTrue(s.canUseInstallments)
    }

    @Test
    fun `preview splits the amount and finds the first closing`() {
        val preview = cardState().installmentPreview!!
        assertEquals(Money(33333, gtq), preview.perClosing)
        assertEquals(LocalDate.of(2026, 12, 15), preview.firstClosing)
    }

    @Test
    fun `the installment count must be between 2 and 60`() {
        assertTrue(cardState(count = "2").isValid)
        assertTrue(cardState(count = "60").isValid)
        assertFalse(cardState(count = "1").isValid)
        assertFalse(cardState(count = "61").isValid)
        assertFalse(cardState(count = "").isValid)
        assertNull(cardState(count = "1").installmentPreview)
    }
}
