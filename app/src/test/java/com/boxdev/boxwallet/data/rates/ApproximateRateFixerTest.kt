package com.boxdev.boxwallet.data.rates

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.AppMetaEntity
import com.boxdev.boxwallet.data.local.entity.InstallmentPlanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.Money
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
import java.util.Currency

/** Uses a real [ExchangeRateResolver] over a fake [RateLookup]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApproximateRateFixerTest {

    private class FakeLookup(var hasExact: Boolean) : RateLookup {
        private val units = mapOf("USD" to BigDecimal.ONE, "GTQ" to BigDecimal("8"), "EUR" to BigDecimal("0.5"))
        /** Days with only a nearest rate even while [hasExact]. */
        val missing = mutableSetOf<LocalDate>()
        override suspend fun exact(day: LocalDate, code: String) = if (hasExact && day !in missing) units[code] else null
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
        lookup = FakeLookup(hasExact = true)
        fixer = ApproximateRateFixer(db.transactionDao(), db.accountDao(), db.appMetaDao(), ExchangeRateResolver(lookup) { day }, prefs, CurrencyChangeLock(), InstallmentPlanRates(db))
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
    fun `skips while pending marker exists`() = runTest {
        val id = db.transactionDao().insert(tx(currency = "EUR", accountSource = RateSource.APPROXIMATE))
        db.appMetaDao().put(AppMetaEntity(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY, "EUR"))

        assertEquals(0, fixer.fix())
        assertEquals(RateSource.APPROXIMATE, db.transactionDao().getById(id)!!.accountRateSource)

        db.appMetaDao().delete(listOf(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY))
        assertEquals(1, fixer.fix())
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

    @Test
    fun `an installment keeps its account rate and upgrades only its main rate`() = runTest {
        // The purchase day still has no exact rate; the installment's own day does.
        val purchaseDay = day.minusMonths(1)
        lookup.missing += purchaseDay
        val plan = db.installmentPlanDao().insert(
            com.boxdev.boxwallet.data.local.entity.InstallmentPlanEntity(
                accountId = accountId, description = "TV", currencyCode = "EUR", originalMinor = 2000,
                accountTotalMinor = 1000, accountRate = BigDecimal("0.5"), accountRateSource = RateSource.APPROXIMATE,
                installments = 2, purchaseDate = purchaseDay.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            )
        )
        val id = db.transactionDao().insert(
            tx(currency = "EUR", accountAmountMinor = 1000, accountRate = "0.5", accountSource = RateSource.APPROXIMATE)
                .copy(installmentPlanId = plan, installmentNumber = 1)
        )

        assertEquals(1, fixer.fix())

        val fixed = db.transactionDao().getById(id)!!
        assertEquals(0, BigDecimal("0.5").compareTo(fixed.accountRate))
        assertEquals(RateSource.APPROXIMATE, fixed.accountRateSource)
        assertEquals(1000L, fixed.accountAmountMinor)
        assertEquals(RateSource.AUTO, fixed.mainRateSource)
        assertEquals(0, BigDecimal("0.125").compareTo(fixed.mainRate))
        assertEquals(125L, fixed.amountMinor)
    }

    @Test
    fun `a plan with an approximate purchase rate upgrades with all its installments`() = runTest {
        val purchaseMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000
        // 10.00 EUR saved at an approximate 15 -> 150.00 GTQ in 3 parts.
        val planId = db.installmentPlanDao().insert(
            InstallmentPlanEntity(
                accountId = accountId, description = "TV", currencyCode = "EUR", originalMinor = 1000,
                accountTotalMinor = 15000, accountRate = BigDecimal("15"), accountRateSource = RateSource.APPROXIMATE,
                installments = 3, purchaseDate = purchaseMillis,
            )
        )
        val originals = listOf(333L, 333L, 334L)
        val ids = (1..3).map { k ->
            db.transactionDao().insert(
                tx(currency = "EUR", accountAmountMinor = 5000, accountRate = "15", accountSource = RateSource.APPROXIMATE)
                    .copy(
                        date = day.plusMonths(k.toLong()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000,
                        originalAmountMinor = originals[k - 1],
                        installmentPlanId = planId,
                        installmentNumber = k,
                    )
            )
        }

        assertEquals(3, fixer.fix())

        // Exact purchase-day rate: 8 / 0.5 = 16 -> 160.00 GTQ = 5333 + 5333 + 5334.
        val plan = db.installmentPlanDao().getById(planId)!!
        assertEquals(16000L, plan.accountTotalMinor)
        assertEquals(0, BigDecimal("16").compareTo(plan.accountRate))
        assertEquals(RateSource.AUTO, plan.accountRateSource)
        val rows = ids.map { db.transactionDao().getById(it)!! }
        assertEquals(listOf(5333L, 5333L, 5334L), rows.map { it.accountAmountMinor })
        assertEquals(plan.accountTotalMinor, rows.sumOf { it.accountAmountMinor })
        assertEquals(originals, rows.map { it.originalAmountMinor })
        rows.forEach { row ->
            assertEquals(0, plan.accountRate.compareTo(row.accountRate))
            assertEquals(RateSource.AUTO, row.accountRateSource)
            assertEquals(RateSource.AUTO, row.mainRateSource)
            assertEquals(0, BigDecimal("0.125").compareTo(row.mainRate))
            val expected = Money(row.accountAmountMinor, Currency.getInstance("GTQ"))
                .convert(Currency.getInstance("USD"), row.mainRate).minor
            assertEquals(expected, row.amountMinor)
        }
        assertEquals(0, db.transactionDao().getWithApproximateRates().size)
    }

    @Test
    fun `a plan stays approximate while its purchase day has no exact rate`() = runTest {
        lookup.missing += day
        val planId = db.installmentPlanDao().insert(
            InstallmentPlanEntity(
                accountId = accountId, description = "TV", currencyCode = "EUR", originalMinor = 1000,
                accountTotalMinor = 15000, accountRate = BigDecimal("15"), accountRateSource = RateSource.APPROXIMATE,
                installments = 2, purchaseDate = dateMillis,
            )
        )

        fixer.fix()

        val plan = db.installmentPlanDao().getById(planId)!!
        assertEquals(15000L, plan.accountTotalMinor)
        assertEquals(RateSource.APPROXIMATE, plan.accountRateSource)
    }
}
