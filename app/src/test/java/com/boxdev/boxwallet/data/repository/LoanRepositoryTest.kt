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
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanPlan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoanRepositoryTest {

    private val gtq = Currency.getInstance("GTQ")
    private val usd = Currency.getInstance("USD")
    private val eur = Currency.getInstance("EUR")
    private fun q(major: Int) = Money(major * 100L, gtq)

    private val disbursed = LocalDateTime.of(2026, 11, 5, 10, 0)
    private val later = LocalDateTime.of(2026, 11, 10, 9, 30)

    /** Units per USD: GTQ is known every day, EUR never, so an EUR account has no main rate. */
    private val lookup = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String) = BigDecimal("7.64").takeIf { code == "GTQ" }
        override suspend fun nearest(day: LocalDate, code: String) = exact(day, code)
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var repository: LoanRepository
    private var banco = 0L
    private var ahorro = 0L
    private var dolares = 0L
    private var tarjeta = 0L
    private var archivada = 0L
    private var euros = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        val accounts = db.accountDao()
        banco = accounts.insert(AccountEntity(name = "Banco", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L))
        ahorro = accounts.insert(AccountEntity(name = "Ahorro", type = "SAVINGS", currencyCode = "GTQ", createdAt = 0L))
        dolares = accounts.insert(AccountEntity(name = "Dólares", type = "CHECKING", currencyCode = "USD", createdAt = 0L))
        tarjeta = accounts.insert(AccountEntity(name = "Visa", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L))
        archivada = accounts.insert(AccountEntity(name = "Vieja", type = "CASH", currencyCode = "GTQ", createdAt = 0L, isArchived = true))
        euros = accounts.insert(AccountEntity(name = "Euros", type = "CHECKING", currencyCode = "EUR", createdAt = 0L))
        prefs = UserPreferences(context)
        prefs.setMainCurrency("GTQ")
        val amounts = TransactionAmounts(ExchangeRateResolver(lookup) { LocalDate.of(2026, 11, 21) })
        val guard = MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts)
        repository = LoanRepository(db, db.loanDao(), db.transactionDao(), db.accountDao(), prefs, amounts, guard)
        // Banco starts with Q5,000.00, as an adjustment so it never counts as income.
        db.transactionDao().insert(
            Transaction.singleCurrency(banco, TransactionType.ADJUSTMENT_IN, LocalDateTime.of(2026, 1, 1, 0, 0), q(5000))
                .copy(isCleared = true).toEntity()
        )
    }

    /** DataStore outlives the test; restore the default main currency. */
    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
    }

    private fun lent(total: Money = q(1000), accountId: Long = banco, plan: LoanPlan? = null) = Loan(
        name = "Juan",
        loanType = LoanType.INCOME,
        total = total,
        accountId = accountId,
        date = disbursed,
        plan = plan,
    )

    private suspend fun balance(accountId: Long) = db.accountDao().getBalance(accountId, Long.MAX_VALUE)
    private suspend fun loan(id: Long) = repository.observe(id).first()!!
    private suspend fun rows(loanId: Long) = repository.observeMovements(loanId).first()
    private suspend fun stored() = db.transactionDao().getAllTransactionsSync()

    private suspend fun assertRefused(block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got $error", error is IllegalArgumentException)
    }

    @Test
    fun `lending takes the money out and payments bring it back`() = runTest {
        val id = repository.create(lent())
        assertEquals(400_000L, balance(banco))

        repository.addPayment(id, q(250), later)

        assertEquals(425_000L, balance(banco))
        assertEquals(q(750), loan(id).remaining)
        assertEquals(q(250), loan(id).paid)
        assertEquals(75_000L, loan(id).remainingMainMinor)
        val movements = rows(id)
        assertEquals(listOf(TransactionType.LOAN_OUT, TransactionType.LOAN_IN), movements.map { it.type })
        assertTrue(movements.all { it.isCleared && it.categoryId == null && it.description == "Juan" && it.loanId == id })
    }

    @Test
    fun `borrowing adds the money and payments take it out`() = runTest {
        val id = repository.create(lent().copy(name = "Banco Industrial", loanType = LoanType.EXPENSE))
        assertEquals(600_000L, balance(banco))

        repository.addPayment(id, q(250), later)

        assertEquals(575_000L, balance(banco))
        assertEquals(q(750), loan(id).remaining)
        assertEquals(listOf(TransactionType.LOAN_IN, TransactionType.LOAN_OUT), rows(id).map { it.type })
    }

    @Test
    fun `borrowing less than what is repaid moves only what was received`() = runTest {
        val id = repository.create(
            lent(total = q(11_000)).copy(name = "Banco Industrial", loanType = LoanType.EXPENSE, disbursed = q(10_000))
        )
        assertEquals(500_000L + 1_000_000L, balance(banco))
        assertEquals(q(11_000), loan(id).remaining)
        assertEquals(q(10_000), loan(id).disbursed)
        assertEquals(1_000_000L, rows(id).single { it.type == TransactionType.LOAN_IN }.accountAmount.minor)

        repository.addPayment(id, q(1_100), later)

        assertEquals(500_000L + 1_000_000L - 110_000L, balance(banco))
        assertEquals(q(9_900), loan(id).remaining)
        assertEquals(1_100_000L, db.loanDao().getById(id)!!.totalAmountMinor)
        assertEquals(1_000_000L, db.loanDao().getById(id)!!.disbursedAmountMinor)
    }

    @Test
    fun `remaining in the main currency uses the rate of what was received`() = runTest {
        prefs.setMainCurrency("USD")
        val id = repository.create(lent(total = q(1_100)).copy(disbursed = q(1_000)))
        repository.addPayment(id, q(275), later)

        // Q1,000.00 / 7.64 = $130.89 at disbursement; Q825.00 left at that rate = $107.98.
        assertEquals(13_089L, rows(id).first { it.type == TransactionType.LOAN_OUT }.amount.minor)
        assertEquals(10_798L, loan(id).remainingMainMinor)
    }

    @Test
    fun `received amount must be positive and in the account's currency`() = runTest {
        assertRefused { repository.create(lent().copy(disbursed = q(0))) }
        assertRefused { repository.create(lent().copy(disbursed = Money(100_00, usd))) }
        val id = repository.create(lent())
        assertRefused { repository.update(loan(id).copy(disbursed = q(0))) }

        assertEquals(1, db.loanDao().getAllLoansSync().size)
        // Received may be above the total as well.
        repository.update(loan(id).copy(disbursed = q(1_200)))
        assertEquals(500_000L - 120_000L, balance(banco))
        assertEquals(q(1_000), loan(id).remaining)
    }

    @Test
    fun `remaining in the main currency follows the disbursement's conversion`() = runTest {
        prefs.setMainCurrency("USD")
        val id = repository.create(lent())
        repository.addPayment(id, q(250), later)

        // Q1,000.00 / 7.64 = $130.89 at disbursement; three quarters of it are still owed.
        val disbursement = rows(id).first { it.type == TransactionType.LOAN_OUT }
        assertEquals(13_089L, disbursement.amount.minor)
        assertEquals(9_817L, loan(id).remainingMainMinor)
    }

    @Test
    fun `loan movements never reach monthly totals`() = runTest {
        val id = repository.create(lent())
        repository.addPayment(id, q(250), later)
        val borrowed = repository.create(lent().copy(loanType = LoanType.EXPENSE))
        repository.addPayment(borrowed, q(100), later)

        val start = LocalDate.of(2026, 11, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val end = LocalDate.of(2026, 12, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val dao = db.transactionDao()
        assertEquals(0L, dao.getTotalIncomeBetweenDates(start, end, Long.MAX_VALUE).first() ?: 0L)
        assertEquals(0L, dao.getTotalExpenseBetweenDates(start, end, Long.MAX_VALUE).first() ?: 0L)
        assertNull(dao.getTotalIncome(Long.MAX_VALUE).first())
        assertNull(dao.getTotalExpense(Long.MAX_VALUE).first())
    }

    @Test
    fun `payment over the remaining is refused`() = runTest {
        val id = repository.create(lent())
        repository.addPayment(id, q(900), later)
        val before = stored()

        assertRefused { repository.addPayment(id, q(101), later) }
        assertRefused { repository.addPayment(id, q(0), later) }

        assertEquals(before, stored())
        assertEquals(q(100), loan(id).remaining)
    }

    @Test
    fun `payment before the disbursement is refused`() = runTest {
        val id = repository.create(lent())

        assertRefused { repository.addPayment(id, q(100), disbursed.minusDays(1)) }
        assertEquals(1, rows(id).size)

        // Earlier on the same day is fine: the rule is by day.
        repository.addPayment(id, q(100), disbursed.toLocalDate().atStartOfDay())
        assertEquals(2, rows(id).size)
    }

    @Test
    fun `paying an installment pays what it lacks`() = runTest {
        val plan = LoanPlan(10, RecurrenceFrequency.MONTHLY, 1, LocalDate.of(2026, 12, 5))
        val id = repository.create(lent(total = q(11_000), plan = plan))
        repository.addPayment(id, q(1500), later)

        val paymentId = repository.payInstallment(id, 2, later)

        assertEquals(70_000L, db.transactionDao().getById(paymentId)!!.accountAmountMinor)
        assertEquals(q(2200), loan(id).paid)
        // Installment 1 is already paid: nothing left to pay on it.
        assertRefused { repository.payInstallment(id, 1, later) }
        assertRefused { repository.payInstallment(id, 11, later) }
    }

    @Test
    fun `editing the total below what was paid is refused`() = runTest {
        val id = repository.create(lent())
        repository.addPayment(id, q(600), later)
        val before = stored()

        assertRefused { repository.update(loan(id).copy(total = q(500))) }

        assertEquals(100_000L, db.loanDao().getById(id)!!.totalAmountMinor)
        assertEquals(before, stored())
    }

    @Test
    fun `editing the amount and date rewrites the disbursement`() = runTest {
        val id = repository.create(lent())
        repository.addPayment(id, q(200), later)
        val newDate = disbursed.minusDays(2)

        repository.update(loan(id).copy(name = "Juan Pérez", total = q(1500), disbursed = q(1500), date = newDate))

        val disbursement = rows(id).first { it.type == TransactionType.LOAN_OUT }
        assertEquals(q(1500), disbursement.accountAmount)
        assertEquals(newDate, disbursement.date)
        assertTrue(rows(id).all { it.description == "Juan Pérez" })
        assertEquals(2, rows(id).size)
        assertEquals(500_000L - 150_000L + 20_000L, balance(banco))
        assertEquals(q(1300), loan(id).remaining)
    }

    @Test
    fun `moving the disbursement after a payment is refused`() = runTest {
        val id = repository.create(lent())
        repository.addPayment(id, q(200), later)

        assertRefused { repository.update(loan(id).copy(date = later.plusDays(1))) }
        assertEquals(disbursed, loan(id).date)
    }

    @Test
    fun `changing the account moves every movement`() = runTest {
        val id = repository.create(lent())
        repository.addPayment(id, q(250), later)
        repository.addPayment(id, q(50), later)

        repository.update(loan(id).copy(accountId = ahorro))

        assertTrue(rows(id).all { it.accountId == ahorro })
        assertEquals(3, rows(id).size)
        assertEquals(500_000L, balance(banco))
        assertEquals(-70_000L, balance(ahorro))
        assertEquals(ahorro, db.loanDao().getById(id)!!.accountId)
    }

    @Test
    fun `changing the account to another currency is refused`() = runTest {
        val id = repository.create(lent())
        val before = stored()

        assertRefused { repository.update(loan(id).copy(accountId = dolares)) }
        assertRefused { repository.update(loan(id).copy(accountId = dolares, total = Money(100_000, usd))) }

        assertEquals(banco, db.loanDao().getById(id)!!.accountId)
        assertEquals(before, stored())
    }

    @Test
    fun `cards and archived accounts are refused on create`() = runTest {
        assertRefused { repository.create(lent(accountId = tarjeta)) }
        assertRefused { repository.create(lent(accountId = archivada)) }

        assertTrue(db.loanDao().getAllLoansSync().isEmpty())
        assertEquals(1, stored().size)
    }

    @Test
    fun `an account archived since is still allowed when editing`() = runTest {
        val id = repository.create(lent())
        db.accountDao().update(db.accountDao().getById(banco)!!.copy(isArchived = true))

        repository.update(loan(id).copy(name = "Juan P."))

        assertEquals("Juan P.", loan(id).name)
        assertRefused { repository.update(loan(id).copy(accountId = archivada)) }
        assertRefused { repository.update(loan(id).copy(accountId = tarjeta)) }
    }

    @Test
    fun `editing and deleting a payment`() = runTest {
        val id = repository.create(lent())
        val first = repository.addPayment(id, q(600), later)
        repository.addPayment(id, q(300), later)

        // A payment can grow up to what is left plus its own amount.
        assertRefused { repository.updatePayment(first, q(701), later) }
        repository.updatePayment(first, q(700), later.plusDays(1))
        assertTrue(loan(id).isSettled)
        assertEquals(later.plusDays(1), rows(id).first { it.id == first }.date)

        repository.deletePayment(first)
        assertEquals(q(700), loan(id).remaining)
        assertEquals(2, rows(id).size)

        // The disbursement is not a payment.
        val disbursementId = rows(id).first { it.type == TransactionType.LOAN_OUT }.id
        assertRefused { repository.deletePayment(disbursementId) }
        assertRefused { repository.updatePayment(disbursementId, q(10), later) }
    }

    @Test
    fun `deleting the loan deletes its movements`() = runTest {
        val id = repository.create(lent())
        repository.addPayment(id, q(250), later)

        repository.delete(id)

        assertNull(repository.observe(id).first())
        assertEquals(1, stored().size)
        assertEquals(500_000L, balance(banco))
    }

    @Test
    fun `observeByAccount lists only that account's loans`() = runTest {
        val here = repository.create(lent())
        repository.create(lent(accountId = ahorro))

        assertEquals(listOf(here), repository.observeByAccount(banco).first().map { it.id })
        assertEquals(2, repository.observeAll().first().size)
    }

    @Test
    fun `missing rate saves nothing`() = runTest {
        val error = runCatching { repository.create(lent(total = Money(100_000, eur), accountId = euros)) }.exceptionOrNull()

        assertTrue(error is MissingRateException)
        assertTrue(db.loanDao().getAllLoansSync().isEmpty())
        assertEquals(1, stored().size)
    }
}
