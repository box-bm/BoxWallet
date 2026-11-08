package com.boxdev.boxwallet.data.rates

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExchangeRateRepositoryTest {

    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private var body: String? = null
    private val urls = mutableListOf<String>()

    private fun repo(): ExchangeRateRepository {
        val transport = HttpTransport { url ->
            urls += url
            body?.let { HttpResult.Ok(it) } ?: HttpResult.Status(404)
        }
        return ExchangeRateRepository(db.exchangeRateDao(), ExchangeRateApi(transport), prefs)
    }

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        prefs = UserPreferences(ctx)
    }

    @After
    fun tearDown() = db.close()

    private fun row(day: LocalDate, code: String, v: String) =
        ExchangeRateEntity(day.toEpochDay(), code, BigDecimal(v))

    @Test
    fun `syncLatest stores snapshot and sync time`() = runTest {
        body = """{"date":"2026-11-07","usd":{"gtq":7.5,"eur":0.9,"usd":1}}"""
        val r = repo()
        assertTrue(r.syncLatest())
        assertEquals(0, BigDecimal("7.5").compareTo(r.exact(LocalDate.of(2026, 11, 7), "GTQ")))
        assertNotNull(prefs.lastRateSyncAt.first())
    }

    @Test
    fun `syncLatest failure stores nothing`() = runTest {
        body = null
        val r = repo()
        assertFalse(r.syncLatest())
        assertNull(prefs.lastRateSyncAt.first())
    }

    @Test
    fun `nearest prefers previous day then next`() = runTest {
        val dao = db.exchangeRateDao()
        val d = LocalDate.of(2026, 1, 10)
        dao.upsertAll(listOf(row(d.minusDays(3), "GTQ", "7"), row(d.plusDays(2), "GTQ", "9")))
        val r = repo()
        assertEquals(0, BigDecimal("7").compareTo(r.nearest(d, "GTQ")))
        // Earlier than every stored row: falls through to the next one.
        assertEquals(0, BigDecimal("7").compareTo(r.nearest(d.minusDays(10), "GTQ")))
        // A later day prefers the previous (d+2) row.
        assertEquals(0, BigDecimal("9").compareTo(r.nearest(d.plusDays(5), "GTQ")))
        assertNull(r.nearest(d, "EUR"))
    }

    @Test
    fun `nearest returns same day row when present`() = runTest {
        val d = LocalDate.of(2026, 1, 10)
        db.exchangeRateDao().upsertAll(listOf(row(d.minusDays(1), "GTQ", "7"), row(d, "GTQ", "8"), row(d.plusDays(1), "GTQ", "9")))
        assertEquals(0, BigDecimal("8").compareTo(repo().nearest(d, "GTQ")))
    }

    @Test
    fun `download rejects out of range dates without network`() = runTest {
        val r = repo()
        assertFalse(r.download(LocalDate.of(2024, 3, 1)))
        assertFalse(r.download(LocalDate.now().plusDays(1)))
        assertTrue(urls.isEmpty())
    }

    @Test
    fun `latest returns current and previous in main currency`() = runTest {
        val dao = db.exchangeRateDao()
        val d1 = LocalDate.of(2026, 11, 6)
        val d2 = LocalDate.of(2026, 11, 7)
        dao.upsertAll(listOf(
            row(d1, "GTQ", "8"), row(d1, "EUR", "1"),
            row(d2, "GTQ", "10"), row(d2, "EUR", "1"),
        ))
        val (now, prev) = repo().latest("GTQ", Currency.getInstance("EUR"))!!
        assertEquals(0, BigDecimal("0.1").compareTo(now))
        assertEquals(0, BigDecimal("0.125").compareTo(prev!!))
    }

    @Test
    fun `series expresses code in main currency`() = runTest {
        val dao = db.exchangeRateDao()
        val d = LocalDate.of(2026, 11, 7)
        dao.upsertAll(listOf(row(d, "GTQ", "8"), row(d, "EUR", "1")))
        val s = repo().series("GTQ", Currency.getInstance("EUR"), d.minusDays(5)).first()
        assertEquals(1, s.size)
        assertEquals(d, s[0].first)
        assertEquals(0, BigDecimal("0.125").compareTo(s[0].second))
    }
}
