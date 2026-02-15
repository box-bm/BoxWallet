package com.boxdev.boxwallet.ui.screens.transaction

import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import org.junit.Assert.*
import org.junit.Test

class TransactionFormStateTest {

    private fun state(
        amount: String = "100",
        isRecurrent: Boolean = false,
        recurrenceFrequency: RecurrenceFrequency? = null,
        recurrenceInterval: String = "1"
    ) = TransactionFormState(
        amount = amount,
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
}
