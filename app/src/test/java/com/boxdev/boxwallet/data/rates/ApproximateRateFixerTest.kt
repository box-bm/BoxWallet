package com.boxdev.boxwallet.data.rates

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

/** Uses a real [ExchangeRateResolver] over a fake [RateLookup]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApproximateRateFixerTest {

    private class FakeLookup(var hasExact: Boolean) : RateLookup {
        private val units = mapOf("USD" to BigDecimal.ONE, "GTQ" to BigDecimal("8"), "EUR" to BigDecimal("0.5"))
        override suspend fun exact(day: LocalDate, code: String) = if (hasExact) units[code] else null
        override suspend fun nearest(day: LocalDate, code: String) = units[code]
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var lookup: FakeLookup
    private lateinit var fixer: ApproximateRateFixer
    private var accountId = 0L
    private val day = LocalDate.of(2025, 5, 10)
    private val dateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java)
            .allowMainThreadQueries().build()
        accountId = db.accountDao().insert(
            AccountEntity(name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L)
        )
        val prefs = UserPreferences(context)
        prefs.setMainCurrency("USD")
        prefs.setPendingMainCurrency(null)
        lookup = FakeLookup(hasExact = true)
        fixer = ApproximateRateFixer(db.transactionDao(), db.accountDao(), ExchangeRateResolver(lookup) { day }, prefs, CurrencyChangeLock())
    }

    @After
    fun tearDown() = db.close()

    private fun tx(
        currency: String = "GTQ",
        accountAmountMinor: Long = 1000,
        accountRate: String = "1",
        accountSource: RateSource = RateSource.AUTO,
        mainRate: String = "0.1",
        mainSource: RateSource = RateSource.APPROXIMATE,
    ) = TransactionEntity(
        accountId = accountId,
        type = "EXPENSE",
        date = dateMillis,
        originalAmountMinor = 1000,
        currencyCode = currency,
        accountAmountMinor = accountAmountMinor,
        accountRate = BigDecimal(accountRate),
        accountRateSource = accountSource,
        amountMinor = 0,
        mainRate = BigDecimal(mainRate),
        mainRateSource = mainSource,
    )

    @Test
    fun `upgrades approximate to auto when exact day appears`() = runTest {
        val id = db.transactionDao().insert(tx(currency = "EUR", accountSource = RateSource.APPROXIMATE))

        assertEquals(1, fixer.fix())

        val t = db.transactionDao().getWithApproximateRates()
        assertEquals(0, t.size)
        val fixed = db.transactionDao().getById(id)!!
        // 10.00 EUR -> GTQ at 8/0.5 = 16 -> 160.00 GTQ; -> USD at 1/8 -> 20.00 USD
        assertEquals(16000L, fixed.accountAmountMinor)
        assertEquals(2000L, fixed.amountMinor)
        assertEquals(RateSource.AUTO, fixed.accountRateSource)
        assertEquals(RateSource.AUTO, fixed.mainRateSource)
    }

    @Test
    fun `leaves manual untouched`() = runTest {
        val id = db.transactionDao().insert(tx(currency = "EUR", accountAmountMinor = 9000, accountRate = "9", accountSource = RateSource.MANUAL))

        assertEquals(1, fixer.fix())

        val fixed = db.transactionDao().getById(id)!!
        assertEquals(0, BigDecimal("9").compareTo(fixed.accountRate))
        assertEquals(9000L, fixed.accountAmountMinor)
        assertEquals(1125L, fixed.amountMinor)
        assertEquals(RateSource.MANUAL, fixed.accountRateSource)
        assertEquals(RateSource.AUTO, fixed.mainRateSource)
        assertEquals(0, BigDecimal("0.125").compareTo(fixed.mainRate))
    }

    @Test
    fun `keeps approximate when still no exact rate`() = runTest {
        lookup.hasExact = false
        val id = db.transactionDao().insert(tx())

        assertEquals(0, fixer.fix())

        val same = db.transactionDao().getById(id)!!
        assertEquals(RateSource.APPROXIMATE, same.mainRateSource)
        assertEquals(0, BigDecimal("0.1").compareTo(same.mainRate))
    }
}
