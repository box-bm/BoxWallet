package com.boxdev.boxwallet.domain.money

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Transaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.Currency

class TransactionAmountsTest {

    private val usd = Currency.getInstance("USD")
    private val eur = Currency.getInstance("EUR")
    private val gtq = Currency.getInstance("GTQ")
    private val jpy = Currency.getInstance("JPY")
    private val bhd = Currency.getInstance("BHD")
    private val day = LocalDate.of(2026, 11, 5)

    /** Units per USD by code, the same for every day; counts lookups so tests can prove the resolver was skipped. */
    private class FakeLookup(private val units: Map<String, BigDecimal>) : RateLookup {
        var calls = 0
        override suspend fun exact(day: LocalDate, code: String): BigDecimal? { calls++; return units[code] }
        override suspend fun nearest(day: LocalDate, code: String): BigDecimal? { calls++; return units[code] }
        override suspend fun download(day: LocalDate): Boolean = false
    }

    private fun amounts(lookup: RateLookup) =
        TransactionAmounts(ExchangeRateResolver(lookup) { LocalDate.of(2026, 11, 7) })

    /** EUR per USD such that 1 EUR = 1.0852 USD. */
    private val eurUnits = BigDecimal.ONE.divide(BigDecimal("1.0852"), MathContext.DECIMAL64)

    @Test
    fun `same currencies give identity rates`() = runTest {
        val lookup = FakeLookup(emptyMap())
        val result = amounts(lookup).compute(AmountInput(Money(1050, gtq), day, gtq, gtq))

        assertEquals(
            AmountResult.Ready(Money(1050, gtq), ExchangeRate.IDENTITY, Money(1050, gtq), ExchangeRate.IDENTITY),
            result,
        )
        assertEquals(0, lookup.calls)
    }

    @Test
    fun `foreign purchase converts twice`() = runTest {
        val lookup = FakeLookup(mapOf("EUR" to eurUnits, "GTQ" to BigDecimal("7.64")))
        val result = amounts(lookup).compute(AmountInput(Money(2000, eur), day, usd, gtq)) as AmountResult.Ready

        assertEquals(Money(2170, usd), result.accountAmount)
        assertEquals(Money(16579, gtq), result.amount)
        assertEquals(RateSource.AUTO, result.accountRate.source)
        assertEquals(0, BigDecimal("7.64").compareTo(result.mainRate.value))
        assertEquals(RateSource.AUTO, result.mainRate.source)
    }

    @Test
    fun `manual account rate marks MANUAL and skips resolver`() = runTest {
        val lookup = FakeLookup(emptyMap())
        val result = amounts(lookup).compute(
            AmountInput(Money(2000, eur), day, usd, usd, manualAccountRate = BigDecimal("1.1")),
        ) as AmountResult.Ready

        assertEquals(ExchangeRate(BigDecimal("1.1"), RateSource.MANUAL), result.accountRate)
        assertEquals(Money(2200, usd), result.accountAmount)
        assertEquals(ExchangeRate.IDENTITY, result.mainRate)
        assertEquals(Money(2200, usd), result.amount)
        assertEquals(0, lookup.calls)
    }

    @Test
    fun `no rate at all needs a manual rate`() = runTest {
        val result = amounts(FakeLookup(emptyMap())).compute(AmountInput(Money(2000, eur), day, usd, usd))

        assertEquals(AmountResult.NeedsManualRate, result)
    }

    @Test
    fun `jpy to bhd rounds to three decimals`() = runTest {
        val lookup = FakeLookup(mapOf("JPY" to BigDecimal("150"), "BHD" to BigDecimal("0.376")))
        val result = amounts(lookup).compute(AmountInput(Money(1000, jpy), day, bhd, bhd)) as AmountResult.Ready

        // ¥1000 × (0.376 / 150) = 2.50666… BHD → 2.507 (3 minor digits)
        assertEquals(Money(2507, bhd), result.accountAmount)
        assertEquals(Money(2507, bhd), result.amount)
    }

    private fun tx(
        original: Money = Money(2000, eur),
        date: java.time.LocalDateTime = day.atTime(10, 0),
        accountRate: ExchangeRate = ExchangeRate(BigDecimal("1.0852"), RateSource.AUTO),
    ) = Transaction(
        accountId = 1,
        type = TransactionType.EXPENSE,
        date = date,
        description = "Lunch",
        original = original,
        accountAmount = Money(2170, usd),
        accountRate = accountRate,
        amount = Money(2170, usd),
        mainRate = ExchangeRate.IDENTITY,
    )

    @Test
    fun `needsRateRefresh false when only description changed`() {
        val previous = tx()
        assertFalse(previous.copy(description = "Dinner", isCleared = true).needsRateRefresh(previous))
        assertFalse(previous.copy(date = day.atTime(22, 30)).needsRateRefresh(previous))
    }

    @Test
    fun `needsRateRefresh true when currency day or amount change with automatic rate`() {
        val previous = tx()
        assertTrue(previous.copy(original = Money(2000, gtq)).needsRateRefresh(previous))
        assertTrue(previous.copy(date = day.plusDays(1).atTime(10, 0)).needsRateRefresh(previous))
        assertTrue(previous.copy(original = Money(2500, eur)).needsRateRefresh(previous))
    }

    @Test
    fun `needsRateRefresh keeps a manual rate unless the currency changes`() {
        val previous = tx(accountRate = ExchangeRate(BigDecimal("1.1"), RateSource.MANUAL))
        assertFalse(previous.copy(original = Money(2500, eur)).needsRateRefresh(previous))
        assertFalse(previous.copy(date = day.plusDays(1).atTime(10, 0)).needsRateRefresh(previous))
        assertTrue(previous.copy(original = Money(2000, gtq)).needsRateRefresh(previous))
    }

    @Test
    fun `refresh re-resolves for the transaction's own date`() = runTest {
        val lookup = FakeLookup(mapOf("EUR" to eurUnits))
        val stale = tx(accountRate = ExchangeRate(BigDecimal("1.2"), RateSource.APPROXIMATE))
            .copy(accountAmount = Money(2400, usd), amount = Money(2400, usd))

        val refreshed = amounts(lookup).refresh(stale)!!

        assertEquals(Money(2170, usd), refreshed.accountAmount)
        assertEquals(RateSource.AUTO, refreshed.accountRate.source)
        assertEquals(Money(2170, usd), refreshed.amount)
    }

    @Test
    fun `refresh keeps manual legs`() = runTest {
        val lookup = FakeLookup(mapOf("EUR" to eurUnits, "GTQ" to BigDecimal("7.64")))
        val manual = tx(accountRate = ExchangeRate(BigDecimal("1.2"), RateSource.MANUAL)).copy(
            accountAmount = Money(2400, usd),
            amount = Money(18000, gtq),
            mainRate = ExchangeRate(BigDecimal("7.5"), RateSource.MANUAL),
        )

        val refreshed = amounts(lookup).refresh(manual)!!

        assertEquals(ExchangeRate(BigDecimal("1.2"), RateSource.MANUAL), refreshed.accountRate)
        assertEquals(Money(2400, usd), refreshed.accountAmount)
        assertEquals(ExchangeRate(BigDecimal("7.5"), RateSource.MANUAL), refreshed.mainRate)
        assertEquals(Money(18000, gtq), refreshed.amount)
    }

    // Edit path (forSave)

    /** Stored Oct 5: EUR→USD MANUAL 1.2, USD→GTQ AUTO 7.7. */
    private fun manualOctober() = Transaction(
        id = 7,
        accountId = 1,
        type = TransactionType.EXPENSE,
        date = LocalDate.of(2026, 10, 5).atTime(9, 0),
        description = "Hotel",
        original = Money(2000, eur),
        accountAmount = Money(2400, usd),
        accountRate = ExchangeRate(BigDecimal("1.2"), RateSource.MANUAL),
        amount = Money(18480, gtq),
        mainRate = ExchangeRate(BigDecimal("7.7"), RateSource.AUTO),
    )

    @Test
    fun `edit with manual account rate and new date re-resolves the main leg for the new date`() = runTest {
        val november = LocalDate.of(2026, 11, 5)
        val lookup = object : RateLookup {
            override suspend fun exact(day: LocalDate, code: String) =
                if (code == "GTQ") (if (day == november) BigDecimal("7.64") else BigDecimal("7.7")) else null
            override suspend fun nearest(day: LocalDate, code: String) = exact(day, code)
            override suspend fun download(day: LocalDate) = false
        }
        val previous = manualOctober()
        val draft = previous.copy(date = november.atTime(9, 0))

        val saved = amounts(lookup).forSave(draft, previous, usd, gtq, manualAccountRate = BigDecimal("1.2"))!!

        assertEquals(ExchangeRate(BigDecimal("1.2"), RateSource.MANUAL), saved.accountRate)
        assertEquals(Money(2400, usd), saved.accountAmount)
        assertEquals(0, BigDecimal("7.64").compareTo(saved.mainRate.value))
        assertEquals(RateSource.AUTO, saved.mainRate.source)
        assertEquals(Money(18336, gtq), saved.amount)
    }

    @Test
    fun `edit with manual account rate and new amount keeps the rate`() = runTest {
        val lookup = FakeLookup(mapOf("GTQ" to BigDecimal("7.7")))
        val previous = manualOctober()

        val saved = amounts(lookup).forSave(
            previous.copy(original = Money(3000, eur)), previous, usd, gtq, manualAccountRate = BigDecimal("1.2"),
        )!!

        assertEquals(ExchangeRate(BigDecimal("1.2"), RateSource.MANUAL), saved.accountRate)
        assertEquals(Money(3600, usd), saved.accountAmount)
        assertEquals(Money(27720, gtq), saved.amount)
    }

    @Test
    fun `edit changing only the description keeps the stored conversion without the resolver`() = runTest {
        val lookup = FakeLookup(mapOf("EUR" to eurUnits, "GTQ" to BigDecimal("7.64")))
        val previous = manualOctober().copy(accountRate = ExchangeRate(BigDecimal("1.0852"), RateSource.AUTO))

        val saved = amounts(lookup).forSave(
            previous.copy(description = "Dinner", date = previous.date.withHour(21)), previous, usd, gtq, null,
        )!!

        assertEquals(previous.copy(description = "Dinner", date = previous.date.withHour(21)), saved)
        assertEquals(0, lookup.calls)
    }

    @Test
    fun `new movement or changed rate choice computes for its date`() = runTest {
        val lookup = FakeLookup(mapOf("EUR" to eurUnits, "GTQ" to BigDecimal("7.64")))
        val previous = manualOctober()

        // Switching from MANUAL back to automatic resolves both legs.
        val auto = amounts(lookup).forSave(previous, previous, usd, gtq, null)!!
        assertEquals(Money(2170, usd), auto.accountAmount)
        assertEquals(RateSource.AUTO, auto.accountRate.source)

        assertEquals(null, amounts(FakeLookup(emptyMap())).forSave(previous.copy(id = 0), null, usd, gtq, null))
    }

    @Test
    fun `edit moving to an account in another currency computes the conversion afresh`() = runTest {
        val lookup = FakeLookup(mapOf("EUR" to eurUnits, "GTQ" to BigDecimal("7.64")))
        val previous = manualOctober().copy(accountRate = ExchangeRate(BigDecimal("1.0852"), RateSource.AUTO))

        val saved = amounts(lookup).forSave(previous.copy(accountId = 2), previous, gtq, gtq, null)!!

        assertEquals(gtq, saved.accountAmount.currency)
        assertEquals(Money(2000, eur), saved.original)
        assertEquals(gtq, saved.amount.currency)
    }
}
