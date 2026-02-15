package com.boxdev.boxwallet.ui.screens.loans

import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import org.junit.Assert.*
import org.junit.Test

class LoanFormStateTest {

    private fun state(
        name: String = "Test",
        totalAmount: String = "1200",
        installments: String = "12",
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        frequencyInterval: String = "1"
    ) = LoanFormState(
        name = name,
        totalAmount = totalAmount,
        installments = installments,
        frequency = frequency,
        frequencyInterval = frequencyInterval
    )

    // installmentAmount

    @Test
    fun `installmentAmount calculates correctly`() {
        assertEquals(100.0, state().installmentAmount!!, 0.001)
    }

    @Test
    fun `installmentAmount null when amount is not a number`() {
        assertNull(state(totalAmount = "abc").installmentAmount)
    }

    @Test
    fun `installmentAmount null when installments is not a number`() {
        assertNull(state(installments = "abc").installmentAmount)
    }

    @Test
    fun `installmentAmount null when installments is zero`() {
        assertNull(state(installments = "0").installmentAmount)
    }

    @Test
    fun `installmentAmount null when amount is empty`() {
        assertNull(state(totalAmount = "").installmentAmount)
    }

    // isValid

    @Test
    fun `isValid true for valid state`() {
        assertTrue(state().isValid)
    }

    @Test
    fun `isValid false when name is blank`() {
        assertFalse(state(name = "").isValid)
    }

    @Test
    fun `isValid false when name is whitespace`() {
        assertFalse(state(name = "   ").isValid)
    }

    @Test
    fun `isValid false when amount is empty`() {
        assertFalse(state(totalAmount = "").isValid)
    }

    @Test
    fun `isValid false when amount is zero`() {
        assertFalse(state(totalAmount = "0").isValid)
    }

    @Test
    fun `isValid false when amount is negative`() {
        assertFalse(state(totalAmount = "-100").isValid)
    }

    @Test
    fun `isValid false when installments is empty`() {
        assertFalse(state(installments = "").isValid)
    }

    @Test
    fun `isValid false when installments is zero`() {
        assertFalse(state(installments = "0").isValid)
    }

    @Test
    fun `isValid true with CUSTOM_MONTHS and valid interval`() {
        assertTrue(state(frequency = RecurrenceFrequency.CUSTOM_MONTHS, frequencyInterval = "3").isValid)
    }

    @Test
    fun `isValid false with CUSTOM_MONTHS and zero interval`() {
        assertFalse(state(frequency = RecurrenceFrequency.CUSTOM_MONTHS, frequencyInterval = "0").isValid)
    }

    @Test
    fun `isValid false with CUSTOM_MONTHS and empty interval`() {
        assertFalse(state(frequency = RecurrenceFrequency.CUSTOM_MONTHS, frequencyInterval = "").isValid)
    }
}
