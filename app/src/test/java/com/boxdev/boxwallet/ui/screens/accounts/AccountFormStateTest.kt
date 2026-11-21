package com.boxdev.boxwallet.ui.screens.accounts

import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Currency

class AccountFormStateTest {

    private val gtq = Currency.getInstance("GTQ")

    private fun state(name: String = "Banco", balance: String = "", isEditing: Boolean = false) =
        AccountFormState(name = name, currency = gtq, currentBalance = balance, isEditing = isEditing)

    @Test
    fun `a named account with no balance can be saved`() {
        val s = state()
        assertNull(s.nameError)
        assertNull(s.balanceError)
        assertNull(s.balance)
        assertTrue(s.canSave)
    }

    @Test
    fun `an empty or blank name blocks saving with an error`() {
        listOf("", "   ").forEach { name ->
            val s = state(name = name)
            assertNotNull(s.nameError)
            assertFalse(s.canSave)
        }
    }

    @Test
    fun `a name over 40 characters is an error`() {
        assertNull(state(name = "a".repeat(40)).nameError)
        val long = state(name = "a".repeat(41))
        assertNotNull(long.nameError)
        assertFalse(long.canSave)
    }

    @Test
    fun `a balance with more decimals than the currency is invalid`() {
        val s = state(balance = "12.345")
        assertNotNull(s.balanceError)
        assertFalse(s.canSave)
    }

    @Test
    fun `a negative balance is valid`() {
        val s = state(balance = "-50")
        assertNull(s.balanceError)
        assertEquals(Money(-5000, gtq), s.balance)
        assertTrue(s.canSave)
    }

    @Test
    fun `zero and thousands separators parse`() {
        assertEquals(Money(0, gtq), state(balance = "0").balance)
        assertEquals(Money(123450, gtq), state(balance = "1,234.5").balance)
    }

    @Test
    fun `text that is not a number is invalid`() {
        listOf("abc", "-", "1-2").forEach { assertNotNull(state(balance = it).balanceError) }
    }

    @Test
    fun `editing ignores the balance field`() {
        val s = state(balance = "12.345", isEditing = true)
        assertNull(s.balanceError)
        assertTrue(s.canSave)
    }

    @Test
    fun `the sign toggle makes the typed balance negative`() {
        val s = AccountFormState(name = "Banco", currency = gtq, currentBalance = "50", negativeBalance = true)
        assertNull(s.balanceError)
        assertEquals(Money(-5000, gtq), s.balance)
    }
}
