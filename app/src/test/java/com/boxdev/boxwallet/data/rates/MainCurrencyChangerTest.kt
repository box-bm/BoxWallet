package com.boxdev.boxwallet.data.rates

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.AppMetaEntity
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.BackupRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.async
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainCurrencyChangerTest {

    /** USD-based rates; every day shares them. `stored` days have exact rates, the rest only nearest. */
    private class FakeLookup : RateLookup {
        var units = mapOf("USD" to BigDecimal.ONE, "GTQ" to BigDecimal("8"), "EUR" to BigDecimal("0.5"))
        val stored = mutableSetOf<LocalDate>()
        var allExact = true
        var online = true
        var throwOn: LocalDate? = null
        var latestReachable = true
        val downloads = mutableListOf<LocalDate>()

        override suspend fun exact(day: LocalDate, code: String): BigDecimal? {
            if (day == throwOn) throw IllegalStateException("boom")
            return if (allExact || day in stored) units[code] else null
        }

        override suspend fun nearest(day: LocalDate, code: String): BigDecimal? {
            if (day == throwOn) throw IllegalStateException("boom")
            return units[code]
        }

        override suspend fun download(day: LocalDate): Boolean {
            downloads += day
            if (!online) return false
            stored += day
            return true
        }
    }

    private lateinit var context: Context
    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var lookup: FakeLookup
    private lateinit var changer: MainCurrencyChanger
    private lateinit var transactions: TransactionRepository
    private val usd = Currency.getInstance("USD")
    private val gtq = Currency.getInstance("GTQ")
    private val day = LocalDate.of(2025, 5, 10)
    private val today = LocalDate.of(2026, 11, 7)

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java)
            .allowMainThreadQueries().build()
        prefs = UserPreferences(context)
        prefs.setMainCurrency("USD")
        lookup = FakeLookup()
        val lock = CurrencyChangeLock()
        val backup = BackupRepository(context, db, db.transactionDao(), db.categoryDao(), db.loanDao(), db.accountDao(), db.appMetaDao(), prefs, lock, GoogleDriveManager(context))
        changer = MainCurrencyChanger(
            db, db.transactionDao(), db.accountDao(), db.loanDao(), db.appMetaDao(), lookup, prefs, backup, lock,
            today = { today },
            sourceReachable = { lookup.online && lookup.latestReachable },
        )
        val amounts = TransactionAmounts(ExchangeRateResolver(lookup) { today })
        transactions = TransactionRepository(
            db.transactionDao(),
            AccountRepository(db.accountDao(), prefs, lookup, context, db.transactionDao(), db, amounts, MovementWriteGuard(lock, prefs, db.accountDao(), amounts)),
            prefs,
            context,
            amounts,
            MovementWriteGuard(lock, prefs, db.accountDao(), amounts),
        )
    }

    /** DataStore outlives the test; leave it as other tests expect it (main USD, nothing pending). */
    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
    }

    private suspend fun account(code: String, makeDefault: Boolean = true): Long {
        val id = db.accountDao().insert(AccountEntity(name = code, type = "CHECKING", currencyCode = code, createdAt = 0L))
        if (makeDefault) prefs.setDefaultAccountId(id)
        return id
    }

    private fun millis(d: LocalDate) = d.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000

    private fun tx(
        accountId: Long,
        originalMinor: Long,
        currency: String,
        accountMinor: Long,
        accountRate: String,
        amountMinor: Long,
        mainRate: String,
        accountSource: RateSource = RateSource.AUTO,
        mainSource: RateSource = RateSource.AUTO,
        date: LocalDate = day,
        loanId: Long? = null,
    ) = TransactionEntity(
        accountId = accountId,
        type = "EXPENSE",
        date = millis(date),
        loanId = loanId,
        originalAmountMinor = originalMinor,
        currencyCode = currency,
        accountAmountMinor = accountMinor,
        accountRate = BigDecimal(accountRate),
        accountRateSource = accountSource,
        amountMinor = amountMinor,
        mainRate = BigDecimal(mainRate),
        mainRateSource = mainSource,
    )

    @Test
    fun `single account follows main currency`() = runTest {
        val acc = account("USD")
        val dao = db.transactionDao()
        val usdTx = dao.insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1"))
        val eurTx = dao.insert(tx(acc, 1000, "EUR", 2000, "2", 2000, "1"))
        val gtqTx = dao.insert(tx(acc, 1000, "GTQ", 125, "0.125", 125, "1"))

        val summary = changer.apply(gtq)

        assertEquals("GTQ", db.accountDao().getById(acc)!!.currencyCode)
        assertEquals(gtq, prefs.mainCurrency.first())
        assertNull(db.appMetaDao().get(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY))
        assertEquals(ChangeSummary(converted = 3, approximate = 0, manualReplaced = 0), summary)
        // 10 USD -> 80 GTQ; 10 EUR -> 160 GTQ (8 / 0.5); 10 GTQ stays.
        val expected = mapOf(usdTx to 8000L, eurTx to 16000L, gtqTx to 1000L)
        for ((id, minor) in expected) {
            val t = dao.getById(id)!!
            assertEquals(minor, t.accountAmountMinor)
            assertEquals(t.accountAmountMinor, t.amountMinor)
            assertEquals(1000L, t.originalAmountMinor)
            assertEquals(0, BigDecimal.ONE.compareTo(t.mainRate))
            assertEquals(RateSource.AUTO, t.mainRateSource)
            assertEquals(RateSource.AUTO, t.accountRateSource)
        }
        assertEquals("EUR", dao.getById(eurTx)!!.currencyCode)
        assertEquals(0, BigDecimal.ONE.compareTo(dao.getById(gtqTx)!!.accountRate))
        assertTrue(java.io.File(context.filesDir, "backups/pre-currency-change.json").exists())
    }

    @Test
    fun `manual rates are replaced and counted`() = runTest {
        val acc = account("USD")
        val id = db.transactionDao().insert(
            tx(acc, 1000, "EUR", 3000, "3", 3000, "1", accountSource = RateSource.MANUAL)
        )

        val summary = changer.apply(gtq)

        assertEquals(1, summary.manualReplaced)
        val t = db.transactionDao().getById(id)!!
        assertEquals(RateSource.AUTO, t.accountRateSource)
        assertEquals(0, BigDecimal("16").compareTo(t.accountRate))
        assertEquals(16000L, t.accountAmountMinor)
        assertEquals(16000L, t.amountMinor)
    }

    @Test
    fun `missing exact rate falls back to nearest and is counted as approximate`() = runTest {
        val acc = account("USD")
        lookup.allExact = false
        val id = db.transactionDao().insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1"))

        val summary = changer.apply(gtq)

        assertEquals(1, summary.approximate)
        assertEquals(RateSource.APPROXIMATE, db.transactionDao().getById(id)!!.accountRateSource)
        assertTrue("apply must not hit the network", lookup.downloads.isEmpty())
    }

    @Test
    fun `multiple accounts keep their balances`() = runTest {
        val usdAcc = account("USD")
        val gtqAcc = account("GTQ", makeDefault = false)
        val dao = db.transactionDao()
        dao.insert(tx(usdAcc, 1000, "USD", 1000, "1", 1000, "1"))
        dao.insert(tx(usdAcc, 1000, "EUR", 2000, "2", 2000, "1"))
        val manualAccount = dao.insert(
            tx(gtqAcc, 1000, "EUR", 15000, "15", 1875, "0.125", accountSource = RateSource.MANUAL)
        )
        val manualMain = dao.insert(
            tx(gtqAcc, 800, "GTQ", 800, "1", 200, "0.25", mainSource = RateSource.MANUAL)
        )
        val before = dao.getAllTransactionsSync().groupBy { it.accountId }
            .mapValues { (_, rows) -> rows.sumOf { it.accountAmountMinor } }

        val summary = changer.apply(gtq)

        val after = dao.getAllTransactionsSync()
        assertEquals(before, after.groupBy { it.accountId }.mapValues { (_, rows) -> rows.sumOf { it.accountAmountMinor } })
        assertEquals("USD", db.accountDao().getById(usdAcc)!!.currencyCode)
        assertEquals("GTQ", db.accountDao().getById(gtqAcc)!!.currencyCode)
        assertEquals(gtq, prefs.mainCurrency.first())
        assertEquals(4, summary.converted)
        assertEquals(1, summary.manualReplaced)
        // USD account: amounts × 8; GTQ account: amount = account amount, mainRate 1.
        assertEquals(listOf(8000L, 16000L), after.filter { it.accountId == usdAcc }.map { it.amountMinor })
        after.filter { it.accountId == gtqAcc }.forEach {
            assertEquals(it.accountAmountMinor, it.amountMinor)
            assertEquals(RateSource.AUTO, it.mainRateSource)
        }
        val kept = dao.getById(manualAccount)!!
        assertEquals(RateSource.MANUAL, kept.accountRateSource)
        assertEquals(0, BigDecimal("15").compareTo(kept.accountRate))
        assertEquals(RateSource.AUTO, dao.getById(manualMain)!!.mainRateSource)
    }

    @Test
    fun `failure mid transaction rolls back`() = runTest {
        val acc = account("USD")
        val dao = db.transactionDao()
        (1..5L).forEach { dao.insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1", date = day.plusDays(it))) }
        val before = dao.getAllTransactionsSync()
        lookup.throwOn = day.plusDays(3)

        try {
            changer.apply(gtq)
            fail("expected the change to fail")
        } catch (_: IllegalStateException) {
        }

        assertEquals(before, dao.getAllTransactionsSync())
        assertEquals("USD", db.accountDao().getById(acc)!!.currencyCode)
        assertEquals(usd, prefs.mainCurrency.first())
        assertNull(db.appMetaDao().get(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY))
    }

    @Test
    fun `no rate at all aborts without writes`() = runTest {
        val acc = account("USD")
        val dao = db.transactionDao()
        dao.insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1"))
        val before = dao.getAllTransactionsSync()
        lookup.units = mapOf("USD" to BigDecimal.ONE)

        try {
            changer.apply(gtq)
            fail("expected a missing rate")
        } catch (_: MissingRateException) {
        }

        assertEquals(before, dao.getAllTransactionsSync())
        assertEquals("USD", db.accountDao().getById(acc)!!.currencyCode)
        assertEquals(usd, prefs.mainCurrency.first())
    }

    @Test
    fun `completePending finishes a committed change with several accounts`() = runTest {
        prefs.setMainCurrency("GTQ")
        account("GTQ")
        account("USD", makeDefault = false)
        // Committed: the marker rode in the data transaction; DataStore never got the new currency.
        db.appMetaDao().put(AppMetaEntity(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY, "EUR"))

        changer.completePending()

        assertEquals(Currency.getInstance("EUR"), prefs.mainCurrency.first())
        assertNull(db.appMetaDao().get(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY))
    }

    @Test
    fun `completePending without a marker leaves the main currency alone`() = runTest {
        account("GTQ")

        changer.completePending()

        assertEquals(usd, prefs.mainCurrency.first())
    }

    @Test
    fun `legacy pending from v1 is finished with a single account`() = runTest {
        prefs.setMainCurrency("GTQ")
        account("EUR")
        prefs.setLegacyPendingMainCurrencyForTest("EUR")

        changer.completePending()

        assertEquals(Currency.getInstance("EUR"), prefs.mainCurrency.first())
        assertNull(prefs.takeLegacyPendingMainCurrency())
    }

    @Test
    fun `legacy pending ignored when rolled back`() = runTest {
        prefs.setMainCurrency("GTQ")
        account("GTQ")
        prefs.setLegacyPendingMainCurrencyForTest("EUR")

        changer.completePending()

        assertEquals(gtq, prefs.mainCurrency.first())
        assertNull(prefs.takeLegacyPendingMainCurrency())
    }

    @Test
    fun `rolled back change leaves no marker`() = runTest {
        val acc = account("USD")
        db.transactionDao().insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1"))
        lookup.units = mapOf("USD" to BigDecimal.ONE)

        try {
            changer.apply(gtq)
            fail("expected a missing rate")
        } catch (_: MissingRateException) {
        }

        assertNull(db.appMetaDao().get(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY))
        assertEquals(usd, prefs.mainCurrency.first())
    }

    @Test
    fun `preview counts movements and loans`() = runTest {
        val acc = account("USD")
        val loan = db.loanDao().insert(
            LoanEntity(name = "Car", totalAmountMinor = 100000, currencyCode = "USD", totalInstallments = 10, startDate = 0L)
        )
        db.transactionDao().insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1"))
        db.transactionDao().insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1", loanId = loan))

        assertEquals(ChangePreview(2, 1, usd, gtq), changer.preview(gtq))
    }

    @Test
    fun `lone account in another currency keeps its currency and amounts`() = runTest {
        prefs.setMainCurrency("GTQ")
        val acc = account("USD")
        val dao = db.transactionDao()
        val id = dao.insert(tx(acc, 1000, "USD", 1000, "1", 8000, "8"))

        val summary = changer.apply(Currency.getInstance("EUR"))

        assertEquals("USD", db.accountDao().getById(acc)!!.currencyCode)
        assertEquals(Currency.getInstance("EUR"), prefs.mainCurrency.first())
        assertEquals(1, summary.converted)
        val t = dao.getById(id)!!
        assertEquals(1000L, t.originalAmountMinor)
        assertEquals("USD", t.currencyCode)
        assertEquals(1000L, t.accountAmountMinor)
        assertEquals(0, BigDecimal.ONE.compareTo(t.accountRate))
        // US$10 at 0.5 EUR per USD = €5.
        assertEquals(500L, t.amountMinor)
        assertEquals(0, BigDecimal("0.5").compareTo(t.mainRate))
    }

    @Test
    fun `prefetch for a lone account in another currency needs its account currency`() = runTest {
        prefs.setMainCurrency("GTQ")
        val acc = account("USD")
        lookup.allExact = false
        db.transactionDao().insert(tx(acc, 1000, "EUR", 2000, "2", 16000, "8"))

        assertTrue(changer.prefetch(Currency.getInstance("EUR")) { _, _ -> })

        // The movement is already in EUR, but its USD account leg still needs that day's rate.
        assertEquals(listOf(day), lookup.downloads)
    }

    @Test
    fun `prefetch downloads only days without local rates`() = runTest {
        val acc = account("USD")
        lookup.allExact = false
        lookup.stored += day
        val dao = db.transactionDao()
        dao.insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1", date = day))
        dao.insert(tx(acc, 1000, "EUR", 2000, "2", 2000, "1", date = day.plusDays(1)))
        dao.insert(tx(acc, 1000, "GTQ", 125, "0.125", 125, "1", date = day.plusDays(2)))
        dao.insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1", date = LocalDate.of(2023, 1, 1)))
        val progress = mutableListOf<Pair<Int, Int>>()

        assertTrue(changer.prefetch(gtq) { done, total -> progress += done to total })

        // GTQ movements need no rate; days before the history can't be downloaded.
        assertEquals(listOf(day.plusDays(1)), lookup.downloads)
        assertEquals(1 to 1, progress.last())
    }

    @Test
    fun `prefetch offline fails without writes`() = runTest {
        val acc = account("USD")
        lookup.allExact = false
        lookup.online = false
        db.transactionDao().insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1"))
        val before = db.transactionDao().getAllTransactionsSync()

        assertFalse(changer.prefetch(gtq) { _, _ -> })

        assertEquals(before, db.transactionDao().getAllTransactionsSync())
        assertEquals(usd, prefs.mainCurrency.first())
        assertNull(db.appMetaDao().get(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY))
    }

    @Test
    fun `changes a thousand movements in one go`() = runTest {
        val acc = account("USD")
        val rows = (0 until 1000).map { i ->
            if (i % 2 == 0) tx(acc, 100L + i, "USD", 100L + i, "1", 100L + i, "1", date = day.minusDays(i % 300L))
            else tx(acc, 100L + i, "EUR", 2 * (100L + i), "2", 2 * (100L + i), "1", date = day.minusDays(i % 300L))
        }
        db.transactionDao().insertAll(rows)
        // USD × 8, EUR × 16: both exact in minor units.
        val expected = rows.sumOf { if (it.currencyCode == "USD") it.originalAmountMinor * 8 else it.originalAmountMinor * 16 }

        val summary = changer.apply(gtq)

        assertEquals(1000, summary.converted)
        val after = db.transactionDao().getAllTransactionsSync()
        assertEquals(expected, after.sumOf { it.amountMinor })
        assertTrue(after.all { it.amountMinor == it.accountAmountMinor })
        assertEquals("GTQ", db.accountDao().getById(acc)!!.currencyCode)
    }

    @Test
    fun `prefetch offline with only recent movements fails`() = runTest {
        val acc = account("USD")
        lookup.allExact = false
        lookup.online = false
        db.transactionDao().insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1", date = today))
        db.transactionDao().insert(tx(acc, 1000, "EUR", 2000, "2", 2000, "1", date = today.minusDays(1)))
        val before = db.transactionDao().getAllTransactionsSync()

        assertFalse(changer.prefetch(gtq) { _, _ -> })

        assertEquals(before, db.transactionDao().getAllTransactionsSync())
        assertEquals("USD", db.accountDao().getById(acc)!!.currencyCode)
        assertEquals(usd, prefs.mainCurrency.first())
        assertNull(db.appMetaDao().get(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY))
    }

    @Test
    fun `prefetch tolerates an unpublished recent day when the source answers`() = runTest {
        val acc = account("USD")
        lookup.allExact = false
        lookup.online = true
        val unpublished = object : RateLookup by lookup {
            override suspend fun download(day: LocalDate) = false
        }
        val reachable = MainCurrencyChanger(
            db, db.transactionDao(), db.accountDao(), db.loanDao(), db.appMetaDao(), unpublished, prefs,
            BackupRepository(context, db, db.transactionDao(), db.categoryDao(), db.loanDao(), db.accountDao(), db.appMetaDao(), prefs, CurrencyChangeLock(), GoogleDriveManager(context)),
            CurrencyChangeLock(), today = { today }, sourceReachable = { true },
        )
        db.transactionDao().insert(tx(acc, 1000, "USD", 1000, "1", 1000, "1", date = today))

        assertTrue(reachable.prefetch(gtq) { _, _ -> })
    }

    /** A movement computed for USD (e.g. a form opened before the change) saved after the change. */
    private fun staleUsdMovement(accountId: Long) = Transaction(
        accountId = accountId,
        type = TransactionType.EXPENSE,
        date = day.atTime(9, 0),
        original = Money(1000, Currency.getInstance("EUR")),
        accountAmount = Money(2000, usd),
        accountRate = ExchangeRate(BigDecimal("3"), RateSource.MANUAL),
        amount = Money(2000, usd),
        mainRate = ExchangeRate.IDENTITY,
    )

    private fun assertInvariantsForGtq(t: TransactionEntity) {
        // §2.3 with account = main = GTQ: 10 EUR × 16 = 160 GTQ, main rate 1.
        assertEquals(1000L, t.originalAmountMinor)
        assertEquals("EUR", t.currencyCode)
        assertEquals(16000L, t.accountAmountMinor)
        assertEquals(0, BigDecimal("16").compareTo(t.accountRate))
        assertEquals(RateSource.AUTO, t.accountRateSource)
        assertEquals(t.accountAmountMinor, t.amountMinor)
        assertEquals(0, BigDecimal.ONE.compareTo(t.mainRate))
        assertEquals(RateSource.AUTO, t.mainRateSource)
    }

    @Test
    fun `stale movement saved after the change is recomputed for the new currency`() = runTest {
        val acc = account("USD")
        changer.apply(gtq)

        val id = transactions.insertTransaction(staleUsdMovement(acc))

        assertInvariantsForGtq(db.transactionDao().getById(id)!!)
    }

    @Test
    fun `save racing the change ends consistent either way`() = runTest {
        val acc = account("USD")
        db.transactionDao().insertAll(
            (1..200).map { tx(acc, 1000, "USD", 1000, "1", 1000, "1", date = day.minusDays(it.toLong())) }
        )

        val change = async { changer.apply(gtq) }
        val save = async { transactions.insertTransaction(staleUsdMovement(acc)) }
        change.await()
        val id = save.await()

        assertInvariantsForGtq(db.transactionDao().getById(id)!!)
        assertTrue(db.transactionDao().getAllTransactionsSync().all { it.amountMinor == it.accountAmountMinor })
    }
}
