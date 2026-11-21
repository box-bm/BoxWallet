package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.util.CurrentDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallmentPlanRepositoryTest {

    private val usd = Currency.getInstance("USD")
    private val eur = Currency.getInstance("EUR")
    private val gtq = Currency.getInstance("GTQ")
    private val today = LocalDate.of(2026, 11, 20)
    private val lastRateDay = LocalDate.of(2026, 11, 20)

    /** Units per USD; rates exist up to [lastRateDay] exactly, later days only by nearest. */
    private val units = mapOf(
        "EUR" to BigDecimal.ONE.divide(BigDecimal("1.10"), MathContext.DECIMAL64),
        "GTQ" to BigDecimal("7.7"),
    )
    private val lookup = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String) = if (day <= lastRateDay) units[code] else null
        override suspend fun nearest(day: LocalDate, code: String) = units[code]
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var repository: InstallmentPlanRepository
    private var card = 0L
    private var child = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        prefs = UserPreferences(context)
        prefs.setMainCurrency("GTQ")
        val resolver = ExchangeRateResolver(lookup) { today }
        val amounts = TransactionAmounts(resolver)
        repository = InstallmentPlanRepository(
            db, db.installmentPlanDao(), db.transactionDao(), db.accountDao(), amounts, resolver,
            MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts), prefs,
            CurrentDay(flowOf(today)),
        )
        card = db.accountDao().insert(
            AccountEntity(
                name = "Card", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L,
                creditLimitMinor = 1_000_000, statementClosingDay = 15, paymentDueDay = 5,
            )
        )
        child = db.accountDao().insert(
            AccountEntity(name = "Card USD", type = "CREDIT_CARD", currencyCode = "USD", createdAt = 0L, parentAccountId = card)
        )
    }

    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
    }

    private fun plan(
        accountId: Long = card,
        original: Money = Money(100_000, gtq),
        installments: Int = 3,
        date: LocalDateTime = LocalDateTime.of(2026, 11, 15, 10, 30),
    ) = NewInstallmentPlan(accountId, "TV", null, original, installments, date)

    private fun millis(d: LocalDateTime) = d.toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `creates one expense per closing with the remainder on the last`() = runTest {
        val id = repository.create(plan())
        val rows = db.transactionDao().getByPlanId(id)
        assertEquals(
            listOf(
                millis(LocalDateTime.of(2026, 11, 15, 10, 30)),
                millis(LocalDateTime.of(2026, 12, 15, 10, 30)),
                millis(LocalDateTime.of(2027, 1, 15, 10, 30)),
            ),
            rows.map { it.date },
        )
        assertEquals(listOf(33333L, 33333L, 33334L), rows.map { it.originalAmountMinor })
        assertEquals(listOf(33333L, 33333L, 33334L), rows.map { it.accountAmountMinor })
        assertEquals(listOf(1, 2, 3), rows.map { it.installmentNumber })
        assertTrue(rows.all { it.type == TransactionType.EXPENSE.name && it.installmentPlanId == id && it.loanId == null })
        assertTrue(rows.all { it.description == "TV" && !it.isCleared })
    }

    @Test
    fun `foreign purchase converts once at the purchase day`() = runTest {
        val id = repository.create(plan(accountId = child, original = Money(50_000, eur)))
        val entity = db.installmentPlanDao().getById(id)!!
        assertEquals(55_000L, entity.accountTotalMinor)
        val rows = db.transactionDao().getByPlanId(id)
        assertTrue(rows.all { it.accountRate.setScale(2, java.math.RoundingMode.HALF_UP) == BigDecimal("1.10") })
        assertEquals(55_000L, rows.sumOf { it.accountAmountMinor })
        assertEquals(50_000L, rows.sumOf { it.originalAmountMinor })
    }

    @Test
    fun `future installment main leg is approximate`() = runTest {
        // USD card with GTQ as the main currency: the main leg needs a real rate.
        val id = repository.create(plan(accountId = child, original = Money(30_000, usd)))
        val rows = db.transactionDao().getByPlanId(id)
        assertEquals(RateSource.AUTO, rows[0].mainRateSource)
        assertEquals(RateSource.APPROXIMATE, rows[1].mainRateSource)
        assertEquals(RateSource.APPROXIMATE, rows[2].mainRateSource)
    }

    @Test
    fun `rejects a card without a closing day, a checking account and 1 installment`() = runTest {
        val noClosing = db.accountDao().insert(
            AccountEntity(name = "NoClose", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L)
        )
        val checking = db.accountDao().insert(
            AccountEntity(name = "Chk", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L)
        )
        for (bad in listOf(plan(accountId = noClosing), plan(accountId = checking), plan(installments = 1), plan(installments = 61))) {
            try {
                repository.create(bad)
                fail("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertTrue(db.installmentPlanDao().getAllSync().isEmpty())
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
    }

    @Test
    fun `missing rate writes nothing`() = runTest {
        val noRates = object : RateLookup {
            override suspend fun exact(day: LocalDate, code: String) = null
            override suspend fun nearest(day: LocalDate, code: String) = null
            override suspend fun download(day: LocalDate) = false
        }
        val resolver = ExchangeRateResolver(noRates) { today }
        val amounts = TransactionAmounts(resolver)
        val repo = InstallmentPlanRepository(
            db, db.installmentPlanDao(), db.transactionDao(), db.accountDao(), amounts, resolver,
            MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts), prefs, CurrentDay(flowOf(today)),
        )
        try {
            repo.create(plan(accountId = child, original = Money(50_000, eur)))
            fail("expected MissingRateException")
        } catch (_: MissingRateException) {
        }
        assertTrue(db.installmentPlanDao().getAllSync().isEmpty())
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
    }

    @Test
    fun `updateDetails propagates to every installment`() = runTest {
        val category = db.categoryDao().insert(CategoryEntity(name = "Home"))
        val id = repository.create(plan())
        repository.updateDetails(id, "Sofa", category)
        val rows = db.transactionDao().getByPlanId(id)
        assertTrue(rows.all { it.description == "Sofa" && it.categoryId == category })
        val planRow = db.installmentPlanDao().getById(id)!!
        assertEquals("Sofa", planRow.description)
        assertEquals(category, planRow.categoryId)
    }

    @Test
    fun `delete removes the plan and its installments`() = runTest {
        val id = repository.create(plan())
        repository.delete(id)
        assertNull(db.installmentPlanDao().getById(id))
        assertTrue(db.transactionDao().getByPlanId(id).isEmpty())
    }

    @Test
    fun `observePlan reports charged and pending by today`() = runTest {
        val id = repository.create(plan())
        val p = repository.observePlan(id).first()!!
        assertEquals(1, p.charged)
        assertEquals(66_667L, p.pendingMinor)
        assertEquals(3, repository.observeInstallments(id).first().size)
    }

    @Test
    fun `plans of a card include its second currency's`() = runTest {
        val own = repository.create(plan(date = LocalDateTime.of(2026, 10, 1, 9, 0)))
        val second = repository.create(plan(accountId = child, original = Money(50_000, eur)))
        val plans = repository.observePlansOfCard(card).first()
        assertEquals(listOf(second, own), plans.map { it.id })
    }
}
