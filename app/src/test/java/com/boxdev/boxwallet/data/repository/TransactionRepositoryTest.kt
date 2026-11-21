package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionRepositoryTest {

    private val usd = Currency.getInstance("USD")
    private val eur = Currency.getInstance("EUR")
    private val gtq = Currency.getInstance("GTQ")
    private val october = LocalDate.of(2026, 10, 5)
    private val november = LocalDate.of(2026, 11, 5)

    /** Units per USD by day: October and November publish different rates. */
    private val units = mapOf(
        october to mapOf("EUR" to BigDecimal("0.9"), "GTQ" to BigDecimal("7.7")),
        november to mapOf(
            "EUR" to BigDecimal.ONE.divide(BigDecimal("1.0852"), MathContext.DECIMAL64),
            "GTQ" to BigDecimal("7.64"),
        ),
    )

    private val lookup = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String) = units[day]?.get(code)
        override suspend fun nearest(day: LocalDate, code: String) = units[day]?.get(code)
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var repository: TransactionRepository
    private lateinit var prefs: UserPreferences
    private var accountId = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        accountId = db.accountDao().insert(
            AccountEntity(name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L)
        )
        prefs = UserPreferences(context)
        // The templates below are in a USD account with GTQ as the main currency.
        prefs.setMainCurrency("GTQ")
        val amounts = TransactionAmounts(ExchangeRateResolver(lookup) { LocalDate.of(2026, 11, 7) })
        repository = TransactionRepository(
            db.transactionDao(),
            AccountRepository(db.accountDao(), prefs, lookup, context, db.transactionDao(), db, amounts, MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts)),
            prefs,
            context,
            amounts,
            MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts),
        )
    }

    /** DataStore outlives the test; restore the default main currency. */
    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
    }

    private suspend fun saved(t: Transaction): Transaction = t.copy(id = db.transactionDao().insert(t.toEntity()))

    private fun template(accountRate: ExchangeRate, accountAmountMinor: Long) = Transaction(
        accountId = accountId,
        type = TransactionType.EXPENSE,
        date = october.atTime(9, 0),
        original = Money(2000, eur),
        accountAmount = Money(accountAmountMinor, usd),
        accountRate = accountRate,
        amount = Money(accountAmountMinor, usd).convert(gtq, BigDecimal("7.7")),
        mainRate = ExchangeRate(BigDecimal("7.7"), RateSource.AUTO),
        isCleared = true,
        isRecurrent = true,
        recurrenceFrequency = RecurrenceFrequency.MONTHLY,
        recurrenceInterval = 1,
    )

    @Test
    fun `recurrence occurrence resolves its own date unless template rate is manual`() = runTest {
        // Automatic template: the November occurrence uses November's rates, not October's.
        val autoTemplate = saved(template(ExchangeRate(BigDecimal.ONE.divide(BigDecimal("0.9"), MathContext.DECIMAL64), RateSource.AUTO), 2222))
        repository.createNextRecurrence(autoTemplate)
        // Manual template: the account rate is copied; the main leg still resolves for November.
        val manualTemplate = saved(template(ExchangeRate(BigDecimal("1.2"), RateSource.MANUAL), 2400))
        repository.createNextRecurrence(manualTemplate)

        val rows = db.transactionDao().getAllTransactions().first()
            .filter { it.parentRecurrenceId != null }
            .sortedBy { it.parentRecurrenceId }

        val auto = rows[0]
        assertEquals(november, LocalDate.ofEpochDay(auto.date / 86_400_000))
        assertEquals(2170L, auto.accountAmountMinor)
        assertEquals(RateSource.AUTO, auto.accountRateSource)
        assertEquals(16579L, auto.amountMinor)
        assertEquals(0, BigDecimal("7.64").compareTo(auto.mainRate))

        val manual = rows[1]
        assertEquals(0, BigDecimal("1.2").compareTo(manual.accountRate))
        assertEquals(RateSource.MANUAL, manual.accountRateSource)
        assertEquals(2400L, manual.accountAmountMinor)
        assertEquals(18336L, manual.amountMinor) // 24.00 USD × 7.64
        assertEquals(0, BigDecimal("7.64").compareTo(manual.mainRate))
    }

    @Test
    fun `observeByAccount lists only that account's movements without loan installments`() = runTest {
        val other = db.accountDao().insert(AccountEntity(name = "Other", type = "CHECKING", currencyCode = "USD", createdAt = 0L))
        val mine = saved(template(ExchangeRate(BigDecimal.ONE, RateSource.AUTO), 2000).copy(isRecurrent = false))
        saved(mine.copy(id = 0, accountId = other))
        val loanId = db.loanDao().insert(
            com.boxdev.boxwallet.data.local.entity.LoanEntity(
                name = "Loan", totalAmountMinor = 1000, currencyCode = "USD", totalInstallments = 1,
                startDate = 0L, loanType = "EXPENSE", frequency = "MONTHLY",
            )
        )
        saved(mine.copy(id = 0, loanId = loanId, installmentNumber = 1))

        assertEquals(listOf(mine.id), repository.observeByAccount(accountId).first().map { it.id })
    }
    private suspend fun quetzalDefault(): Long {
        val id = db.accountDao().insert(AccountEntity(name = "Quetzales", type = "CASH", currencyCode = "GTQ", createdAt = 0L, sortOrder = 1))
        prefs.setDefaultAccountId(id)
        return id
    }

    /** €20 in November: 7.64 GTQ/USD × 1.0852 USD/EUR = 8.290928 GTQ/EUR → Q165.82. */
    private suspend fun assertOccurrenceInQuetzales(defaultId: Long) {
        val occurrence = db.transactionDao().getAllTransactions().first().single { it.parentRecurrenceId != null }
        assertEquals(defaultId, occurrence.accountId)
        assertEquals("EUR", occurrence.currencyCode)
        assertEquals(2000L, occurrence.originalAmountMinor)
        assertEquals(16582L, occurrence.accountAmountMinor)
        assertEquals(RateSource.AUTO, occurrence.accountRateSource)
        assertEquals(16582L, occurrence.amountMinor)
        assertEquals(0, BigDecimal.ONE.compareTo(occurrence.mainRate))
    }

    @Test
    fun `recurrence of an archived account goes to the default account`() = runTest {
        val defaultId = quetzalDefault()
        val template = saved(template(ExchangeRate(BigDecimal("1.2"), RateSource.MANUAL), 2400))
        db.accountDao().update(db.accountDao().getById(accountId)!!.copy(isArchived = true))

        repository.createNextRecurrence(template)

        assertOccurrenceInQuetzales(defaultId)
    }

    @Test
    fun `recurrence of a missing account goes to the default account`() = runTest {
        val defaultId = quetzalDefault()
        val template = saved(template(ExchangeRate(BigDecimal("1.2"), RateSource.MANUAL), 2400))

        repository.createNextRecurrence(template.copy(accountId = 999))

        assertOccurrenceInQuetzales(defaultId)
    }
}
