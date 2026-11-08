package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.domain.model.BackupData
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.RateSource
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency

class LegacyBackupConverterTest {

    private val gtq = Currency.getInstance("GTQ")
    private val jpy = Currency.getInstance("JPY")
    private val accountId = 7L

    private fun fixtureText(): String =
        checkNotNull(javaClass.classLoader?.getResource("backup-v2.json")) { "missing backup-v2.json" }.readText()

    private fun fixture(): JsonObject = JsonParser.parseString(fixtureText()).asJsonObject

    private fun convert(main: Currency = gtq): BackupData = LegacyBackupConverter.fromV2(fixture(), main, accountId)

    @Test
    fun `amounts become minor units of the main currency with half-even rounding`() {
        val byId = convert().transactions.associateBy { it.id }
        assertEquals(1250L, byId.getValue(1).originalAmountMinor)
        assertEquals(30L, byId.getValue(2).originalAmountMinor)
        assertEquals(250000L, byId.getValue(3).originalAmountMinor)
        assertEquals(33333L, byId.getValue(5).originalAmountMinor)
        assertEquals(33334L, byId.getValue(7).originalAmountMinor)
        byId.values.forEach {
            assertEquals(it.originalAmountMinor, it.accountAmountMinor)
            assertEquals(it.originalAmountMinor, it.amountMinor)
        }
    }

    @Test
    fun `zero-decimal main currency rounds half even`() {
        val byId = convert(jpy).transactions.associateBy { it.id }
        assertEquals(12L, byId.getValue(1).originalAmountMinor) // 12.5 → 12 (half even)
        assertEquals(0L, byId.getValue(2).originalAmountMinor)
        assertEquals(333L, byId.getValue(5).originalAmountMinor)
    }

    @Test
    fun `every movement goes to the given account in the main currency with identity rates`() {
        val rows = convert().transactions
        assertEquals(7, rows.size)
        rows.forEach {
            assertEquals(accountId, it.accountId)
            assertEquals("GTQ", it.currencyCode)
            assertEquals(0, ExchangeRate.IDENTITY.value.compareTo(it.accountRate))
            assertEquals(RateSource.AUTO, it.accountRateSource)
            assertEquals(0, BigDecimal.ONE.compareTo(it.mainRate))
            assertEquals(RateSource.AUTO, it.mainRateSource)
        }
    }

    @Test
    fun `ids, references and the remaining fields are kept`() {
        val byId = convert().transactions.associateBy { it.id }
        val salary = byId.getValue(3)
        assertEquals("INCOME", salary.type)
        assertEquals("Salary", salary.description)
        assertEquals(1759363200000L, salary.date)
        assertEquals(2L, salary.categoryId)
        assertTrue(salary.isRecurrent)
        assertEquals("MONTHLY", salary.recurrenceFrequency)
        assertEquals(1, salary.recurrenceInterval)
        assertEquals(1764547200000L, salary.nextOccurrenceDate)
        assertTrue(salary.hasReminder)
        assertEquals(1761955200000L, salary.reminderDate)
        assertEquals(3600L, salary.reminderSecondsBefore)
        assertEquals(3L, byId.getValue(4).parentRecurrenceId)
        assertEquals(false, byId.getValue(4).isCleared)

        val installment = byId.getValue(6)
        assertEquals(1L, installment.loanId)
        assertEquals(2, installment.installmentNumber)
        assertEquals("EXPENSE", installment.type)

        val interest = byId.getValue(2)
        assertNull(interest.categoryId)
        assertNull(interest.loanId)
        assertNull(interest.parentRecurrenceId)
        assertNull(interest.reminderDate)
    }

    @Test
    fun `loan gets total in minor units, the main currency and the account`() {
        val loan = convert().loans.single()
        assertEquals(1L, loan.id)
        assertEquals("Laptop", loan.name)
        assertEquals("Store credit", loan.description)
        assertEquals(100000L, loan.totalAmountMinor)
        assertEquals("GTQ", loan.currencyCode)
        assertEquals(accountId, loan.accountId)
        assertEquals(3, loan.totalInstallments)
        assertEquals(1L, loan.categoryId)
        assertEquals(1759363200000L, loan.startDate)
        assertEquals("EXPENSE", loan.loanType)
        assertEquals("MONTHLY", loan.frequency)
        assertEquals(1, loan.frequencyInterval)
    }

    @Test
    fun `categories are kept and one default account in the main currency is created`() {
        val data = convert()
        assertEquals(listOf(1L to "Food", 2L to "Salary"), data.categories.map { it.id to it.name })
        assertEquals("Restaurant", data.categories.first().icon)
        val account = data.accounts.single()
        assertEquals(accountId, account.id)
        assertEquals("GTQ", account.currencyCode)
        assertEquals("CHECKING", account.type)
        assertEquals(3, data.version)
        assertEquals("GTQ", data.mainCurrencyCode)
        assertEquals(1762300800000L, data.createdAt)
    }

    @Test
    fun `fromV2 rejects other versions`() {
        val v3 = fixture().apply { addProperty("version", 3) }
        assertThrows(IllegalArgumentException::class.java) { LegacyBackupConverter.fromV2(v3, gtq, accountId) }
    }

    @Test
    fun `decode dispatches by version and reads v3 as is`() {
        assertEquals(30L, LegacyBackupConverter.decode(fixtureText(), gtq, "Main").transactions.first { it.id == 2L }.amountMinor)
        val v3 = """{"version":3,"createdAt":5,"mainCurrencyCode":"EUR","accounts":[],"transactions":[],"categories":[],"loans":[]}"""
        val data = LegacyBackupConverter.decode(v3, gtq, "Main")
        assertEquals("EUR", data.mainCurrencyCode)
        assertEquals(5L, data.createdAt)
        val sparse = LegacyBackupConverter.decode("""{"version":3,"mainCurrencyCode":"EUR"}""", gtq, "Main")
        assertTrue(sparse.transactions.isEmpty() && sparse.accounts.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            LegacyBackupConverter.decode("""{"version":3,"accounts":[]}""", gtq, "Main")
        }
    }

    private fun v3(account: String = "GTQ", tx: String = "GTQ", loan: String = "GTQ", main: String = "GTQ"): String = """
        {"version":3,"mainCurrencyCode":"$main",
         "accounts":[{"id":1,"name":"A","type":"CASH","currencyCode":"$account","createdAt":0}],
         "transactions":[{"id":1,"accountId":1,"type":"EXPENSE","date":0,"originalAmountMinor":1,"currencyCode":"$tx",
           "accountAmountMinor":1,"accountRate":1,"accountRateSource":"IDENTITY","amountMinor":1,"mainRate":1,"mainRateSource":"IDENTITY"}],
         "loans":[{"id":1,"name":"L","totalAmountMinor":1,"currencyCode":"$loan","accountId":1,"totalInstallments":1,"startDate":0}]}
    """.trimIndent()

    @Test
    fun `decode accepts v3 with CFA and East Caribbean currencies`() {
        val data = LegacyBackupConverter.decode(v3(account = "XOF", tx = "XCD", loan = "XAF", main = "XPF"), gtq, "Main")
        assertEquals("XCD", data.transactions.single().currencyCode)
    }

    @Test
    fun `decode rejects v3 with an unknown currency anywhere`() {
        val cases = listOf(
            v3(main = "XAU") to "XAU",
            v3(account = "ZZZ") to "ZZZ",
            v3(tx = "XXX") to "XXX",
            v3(loan = "QQQ") to "QQQ",
            v3(tx = "null").replace("\"null\"", "null") to "missing",
        )
        for ((json, code) in cases) {
            val e = assertThrows(IllegalArgumentException::class.java) { LegacyBackupConverter.decode(json, gtq, "Main") }
            assertTrue("$code: ${e.message}", e.message!!.contains(code))
        }
    }

    @Test
    fun `decode rejects an unknown version with a clear error`() {
        val e = assertThrows(UnsupportedBackupVersionException::class.java) {
            LegacyBackupConverter.decode("""{"version":99}""", gtq, "Main")
        }
        assertTrue(e.message!!.contains("99"))
        assertThrows(UnsupportedBackupVersionException::class.java) {
            LegacyBackupConverter.decode("""{"transactions":[]}""", gtq, "Main")
        }
    }
}
