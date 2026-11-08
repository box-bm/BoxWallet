package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
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
        val prefs = UserPreferences(context) // main currency defaults to USD
        repository = LoanRepository(
            db.loanDao(),
            db.transactionDao(),
            AccountRepository(db.accountDao(), prefs, context),
            prefs,
            TransactionAmounts(ExchangeRateResolver(lookup) { LocalDate.of(2026, 11, 7) }),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun loan() = Loan(
        name = "Car",
        totalAmount = Money(20000, eur),
        totalInstallments = 2,
        accountId = accountId,
        startDate = start.atStartOfDay(),
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
}
