package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.LoanPlan
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class LoanRepositoryTest {

    private val eur = Currency.getInstance("EUR")
    private val start = LocalDate.of(2026, 10, 5)

    /** EUR is 0.8 per USD on the first installment's day and 0.5 on the second's. */
    private val units = mapOf(start to BigDecimal("0.8"), start.plusMonths(1) to BigDecimal("0.5"))
    private var rates = true

    private val lookup = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String) = units[day].takeIf { rates && code == "EUR" }
        override suspend fun nearest(day: LocalDate, code: String) = exact(day, code)
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var repository: LoanRepository
    private var accountId = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        accountId = db.accountDao().insert(
            AccountEntity(name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L)
        )
        val prefs = UserPreferences(context)
        prefs.setMainCurrency("USD")
        val amounts = TransactionAmounts(ExchangeRateResolver(lookup) { LocalDate.of(2026, 11, 7) })
        repository = LoanRepository(
            db,
            db.loanDao(),
            db.transactionDao(),
            AccountRepository(db.accountDao(), prefs, lookup, context, db.transactionDao(), db, amounts, MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts)),
            amounts,
            MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun loan() = Loan(
        name = "Car",
        loanType = LoanType.EXPENSE,
        total = Money(20000, eur),
        accountId = accountId,
        date = start.atStartOfDay(),
        plan = LoanPlan(2, RecurrenceFrequency.MONTHLY, 1, start),
    )

    @Test
    fun `installments are in the loan currency with conversions for their due dates`() = runTest {
        val loanId = repository.insertLoanWithInstallments(loan())

        val rows = db.transactionDao().getInstallmentsByLoanId(loanId).sortedBy { it.installmentNumber }
        assertEquals(listOf("EUR", "EUR"), rows.map { it.currencyCode })
        assertEquals(listOf(10000L, 10000L), rows.map { it.originalAmountMinor })
        // €100 / 0.8 = $125.00 ; €100 / 0.5 = $200.00
        assertEquals(listOf(12500L, 20000L), rows.map { it.accountAmountMinor })
        assertEquals(listOf(12500L, 20000L), rows.map { it.amountMinor })
        assertEquals(listOf(RateSource.AUTO, RateSource.AUTO), rows.map { it.accountRateSource })
    }

    @Test
    fun `missing rate saves nothing`() = runTest {
        rates = false
        val error = runCatching { repository.insertLoanWithInstallments(loan()) }.exceptionOrNull()

        assertTrue(error is MissingRateException)
        assertTrue(db.transactionDao().getInstallmentsByLoanId(1).isEmpty())
        assertTrue(db.loanDao().getAllLoansSync().isEmpty())
    }

    @Test
    fun `editing an unpaid loan regenerates installments in the new currency and split`() = runTest {
        val usd = Currency.getInstance("USD")
        val loanId = repository.insertLoanWithInstallments(loan().copy(total = Money(20000, usd)))
        assertEquals(listOf("USD", "USD"), db.transactionDao().getInstallmentsByLoanId(loanId).map { it.currencyCode })
        // Switch to EUR with an odd split: 30001 / 2 -> 15000 + 15001.
        val edited = loan().copy(id = loanId, total = Money(30001, eur))
        repository.updateLoan(edited)

        val rows = db.transactionDao().getInstallmentsByLoanId(loanId).sortedBy { it.installmentNumber }
        assertEquals(2, rows.size)
        assertEquals(listOf("EUR", "EUR"), rows.map { it.currencyCode })
        assertEquals(listOf(15000L, 15001L), rows.map { it.originalAmountMinor })
        // €150.00 / 0.8 = $187.50 ; €150.01 / 0.5 = $300.02
        assertEquals(listOf(18750L, 30002L), rows.map { it.amountMinor })
        assertEquals(30001L, db.loanDao().getById(loanId)!!.totalAmountMinor)
    }

    @Test
    fun `editing an unpaid loan into a currency with no rate throws and changes nothing`() = runTest {
        val loanId = repository.insertLoanWithInstallments(loan())
        val before = db.transactionDao().getInstallmentsByLoanId(loanId).sortedBy { it.installmentNumber }

        val gbp = Currency.getInstance("GBP")
        val error = runCatching {
            repository.updateLoan(loan().copy(id = loanId, total = Money(50000, gbp)))
        }.exceptionOrNull()

        assertTrue(error is MissingRateException)
        assertEquals("EUR", db.loanDao().getById(loanId)!!.currencyCode)
        assertEquals(20000L, db.loanDao().getById(loanId)!!.totalAmountMinor)
        assertEquals(before, db.transactionDao().getInstallmentsByLoanId(loanId).sortedBy { it.installmentNumber })
    }
}
