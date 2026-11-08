package com.boxdev.boxwallet.data.rates

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.RateSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

/** Real repository (fake HTTP) and real fixer: what "Update now" in Currencies runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RateSyncTest {

    private lateinit var db: BoxWalletDatabase
    private lateinit var sync: RateSync
    private var body: String? = null
    private val day = LocalDate.of(2026, 11, 7)

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        val prefs = UserPreferences(context)
        prefs.setMainCurrency("USD")
        prefs.setPendingMainCurrency(null)
        val repository = ExchangeRateRepository(
            db.exchangeRateDao(),
            ExchangeRateApi({ _ -> body?.let { HttpResult.Ok(it) } ?: HttpResult.Status(404) }),
            prefs,
        )
        val fixer = ApproximateRateFixer(
            db.transactionDao(), db.accountDao(), ExchangeRateResolver(repository) { day.minusDays(1) }, prefs, CurrencyChangeLock(),
        )
        sync = RateSync(repository, fixer)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun approximateMovement(): Long {
        val account = db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L))
        return db.transactionDao().insert(
            TransactionEntity(
                accountId = account, type = "EXPENSE",
                date = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000,
                originalAmountMinor = 7500, currencyCode = "GTQ",
                accountAmountMinor = 7500, accountRate = BigDecimal.ONE, accountRateSource = RateSource.AUTO,
                amountMinor = 900, mainRate = BigDecimal("0.12"), mainRateSource = RateSource.APPROXIMATE,
            )
        )
    }

    @Test
    fun `a successful sync also upgrades approximate rates`() = runTest {
        val id = approximateMovement()
        body = """{"date":"2026-11-07","usd":{"gtq":7.5,"usd":1}}"""

        assertTrue(sync.syncAndFix())

        val fixed = db.transactionDao().getById(id)!!
        assertEquals(RateSource.AUTO, fixed.mainRateSource)
        assertEquals(1000L, fixed.amountMinor)
    }

    @Test
    fun `a failed sync reports false and leaves movements alone`() = runTest {
        val id = approximateMovement()
        body = null

        assertFalse(sync.syncAndFix())

        assertEquals(RateSource.APPROXIMATE, db.transactionDao().getById(id)!!.mainRateSource)
    }
}
