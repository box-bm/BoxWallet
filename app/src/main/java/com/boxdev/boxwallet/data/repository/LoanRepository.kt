package com.boxdev.boxwallet.data.repository

import androidx.room.withTransaction
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.LoanWithTotals
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loans inside one account: each loan has one disbursement row ([Loan.disbursementType]) and one
 * row per payment ([Loan.paymentType]), all in [Loan.accountId], cleared and without a category.
 * Installments are never stored; [Loan.schedule] spreads what was paid over them.
 */
@Singleton
class LoanRepository @Inject constructor(
    private val database: BoxWalletDatabase,
    private val loanDao: LoanDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val userPreferences: UserPreferences,
    private val amounts: TransactionAmounts,
    private val guard: MovementWriteGuard,
) {

    fun observeAll(): Flow<List<Loan>> = loanDao.getAll().map { rows -> rows.map { it.toDomain() } }

    fun observeByAccount(accountId: Long): Flow<List<Loan>> =
        loanDao.getByAccount(accountId).map { rows -> rows.map { it.toDomain() } }

    fun observe(id: Long): Flow<Loan?> = loanDao.getWithTotals(id).map { it?.toDomain() }

    suspend fun get(id: Long): Loan? = observe(id).first()

    /** The disbursement and every payment, oldest first. */
    fun observeMovements(loanId: Long): Flow<List<Transaction>> = withCurrencies(loanDao.getMovements(loanId))

    /** Every loan movement dated from [start] to [end] inclusive, oldest first. */
    fun observeMovementsBetween(start: LocalDate, end: LocalDate): Flow<List<Transaction>> {
        val startMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endMillis = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return withCurrencies(transactionDao.getLoanMovementsBetweenDates(startMillis, endMillis))
    }

    /** Maps stored [rows] to movements, each in its account's currency. */
    private fun withCurrencies(rows: Flow<List<TransactionEntity>>): Flow<List<Transaction>> = combine(
        rows,
        accountDao.observeAll(),
        userPreferences.mainCurrency,
    ) { entities, accounts, main ->
        val currencies = accounts.associate { it.id to Currency.getInstance(it.currencyCode) }
        entities.map { it.toDomain(currencies[it.accountId] ?: main, main) }
    }

    /** Saves [loan] and its disbursement; returns the loan's id. A missing rate saves nothing. */
    suspend fun create(loan: Loan): Long = guard.write { current ->
        database.withTransaction {
            requireAccount(loan.accountId, keptFrom = null)
            requireTotal(loan, current)
            val disbursement = movement(current, loan, loan.disbursementType, loan.disbursed, loan.date, previous = null)
            val id = loanDao.insert(loan.toEntity().copy(id = 0))
            transactionDao.insert(disbursement.copy(loanId = id).toEntity())
            id
        }
    }

    /**
     * Rewrites the loan and its disbursement. A new account (same currency only) takes the
     * disbursement and every payment with it; the payments' conversions are kept.
     */
    suspend fun update(loan: Loan): Unit = guard.write { current ->
        database.withTransaction {
            val stored = stored(loan.id, current)
            requireAccount(loan.accountId, keptFrom = stored.loan.accountId)
            require(current.accountCurrency(loan.accountId) == stored.loan.total.currency) {
                "A loan can't move to an account in another currency"
            }
            requireTotal(loan, current)
            require(loan.total.minor >= stored.loan.paid.minor) { "The total can't be below what was already paid" }
            require(loan.loanType == stored.loan.loanType || stored.payments.isEmpty()) {
                "A loan with payments can't change direction"
            }
            require(stored.payments.none { it.date.toLocalDate().isBefore(loan.date.toLocalDate()) }) {
                "The disbursement can't be after a payment"
            }
            val disbursement = movement(
                current, loan, loan.disbursementType, loan.disbursed, loan.date, previous = stored.disbursement,
            ).copy(id = stored.disbursement.id)
            val payments = stored.payments.map { payment ->
                movement(current, loan, loan.paymentType, payment.accountAmount, payment.date, previous = payment)
                    .copy(id = payment.id)
            }
            loanDao.update(loan.toEntity())
            transactionDao.updateAll((listOf(disbursement) + payments).map { it.toEntity() })
        }
    }

    /** A payment of [amount] on [date]: more than zero, at most what is left, not before the disbursement. */
    suspend fun addPayment(loanId: Long, amount: Money, date: LocalDateTime): Long =
        pay(loanId, date) { amount }

    /** Pays what installment [number] still lacks, as one payment. */
    suspend fun payInstallment(loanId: Long, number: Int, date: LocalDateTime): Long =
        pay(loanId, date) { loan ->
            loan.schedule(date.toLocalDate()).firstOrNull { it.number == number }?.uncovered
                ?: throw IllegalArgumentException("Loan $loanId has no installment $number")
        }

    suspend fun updatePayment(paymentId: Long, amount: Money, date: LocalDateTime): Unit = guard.write { current ->
        database.withTransaction {
            val row = transactionDao.getById(paymentId)?.takeIf { it.loanId != null }
                ?: throw IllegalArgumentException("Movement $paymentId is not a loan payment")
            val stored = stored(row.loanId!!, current)
            val previous = stored.payments.firstOrNull { it.id == paymentId }
                ?: throw IllegalArgumentException("Movement $paymentId is not a loan payment")
            val loan = stored.loan.copy(paid = Money(stored.loan.paid.minor - previous.accountAmount.minor, stored.loan.total.currency))
            requirePayment(loan, amount, date)
            val payment = movement(current, loan, loan.paymentType, amount, date, previous).copy(id = paymentId)
            transactionDao.update(payment.toEntity())
        }
    }

    suspend fun deletePayment(paymentId: Long): Unit = database.withTransaction {
        val row = transactionDao.getById(paymentId)?.takeIf { it.loanId != null }
            ?: throw IllegalArgumentException("Movement $paymentId is not a loan payment")
        val loan = row.loanId?.let { loanDao.getById(it) }?.toDomain(paidMinor = 0, remainingMainMinor = 0)
            ?: throw IllegalArgumentException("Loan of movement $paymentId does not exist")
        require(row.type == loan.paymentType.name) { "The disbursement goes only with its loan" }
        transactionDao.deleteById(paymentId)
    }

    /** Deletes the loan with its disbursement and payments. */
    suspend fun delete(loanId: Long): Unit = database.withTransaction {
        transactionDao.deleteByLoanId(loanId)
        loanDao.deleteById(loanId)
    }

    private suspend fun pay(loanId: Long, date: LocalDateTime, amountOf: (Loan) -> Money): Long =
        guard.write { current ->
            database.withTransaction {
                val loan = stored(loanId, current).loan
                val amount = amountOf(loan)
                requirePayment(loan, amount, date)
                val payment = movement(current, loan, loan.paymentType, amount, date, previous = null)
                transactionDao.insert(payment.toEntity())
            }
        }

    private fun requirePayment(loan: Loan, amount: Money, date: LocalDateTime) {
        require(amount.currency == loan.total.currency) { "A payment must be in the loan's currency" }
        require(amount.minor > 0) { "A payment must be positive" }
        require(amount.minor <= loan.remaining.minor) { "A payment can't be more than what is left" }
        require(!date.toLocalDate().isBefore(loan.date.toLocalDate())) { "A payment can't be before the disbursement" }
    }

    private fun requireTotal(loan: Loan, current: MovementWriteGuard.Current) {
        require(loan.total.minor > 0) { "A loan must have a positive total" }
        require(loan.disbursed.minor > 0) { "A loan must have a positive amount received or given" }
        require(loan.total.currency == current.accountCurrency(loan.accountId) && loan.disbursed.currency == loan.total.currency) {
            "A loan must be in its account's currency"
        }
    }

    /**
     * The account exists, isn't a card and isn't archived. When editing, an account archived
     * since is still allowed if it is the one the loan already uses ([keptFrom]).
     */
    private suspend fun requireAccount(accountId: Long, keptFrom: Long?) {
        val account = accountDao.getById(accountId) ?: throw IllegalArgumentException("Account $accountId does not exist")
        require(account.type != AccountType.CREDIT_CARD.name) { "A loan can't live in a card" }
        require(!account.isArchived || accountId == keptFrom) { "Account $accountId is archived" }
    }

    /** A movement of [loan] with its main-currency leg frozen by [TransactionAmounts.forSave]. */
    private suspend fun movement(
        current: MovementWriteGuard.Current,
        loan: Loan,
        type: TransactionType,
        amount: Money,
        date: LocalDateTime,
        previous: Transaction?,
    ): Transaction {
        val accountCurrency = current.accountCurrency(loan.accountId)
        val base = Transaction.singleCurrency(loan.accountId, type, date, amount).copy(
            description = loan.name,
            loanId = loan.id.takeIf { it != 0L },
            isCleared = true,
        )
        return amounts.forSave(base, previous, accountCurrency, current.main, manualAccountRate = null)
            ?: throw MissingRateException(accountCurrency, current.main)
    }

    /** A stored loan, with [Loan.paid] filled in, and its rows split into the disbursement and the payments. */
    private class Stored(val loan: Loan, val disbursement: Transaction, val payments: List<Transaction>)

    private suspend fun stored(loanId: Long, current: MovementWriteGuard.Current): Stored {
        val entity = loanDao.getById(loanId) ?: throw IllegalArgumentException("Loan $loanId does not exist")
        val shell = entity.toDomain(paidMinor = 0, remainingMainMinor = 0)
        val rows = loanDao.getMovementsSync(loanId).map { it.toDomain(current.accountCurrency(it.accountId), current.main) }
        val disbursement = rows.single { it.type == shell.disbursementType }
        val payments = rows.filter { it.type == shell.paymentType }
        val paid = Money(payments.sumOf { it.accountAmount.minor }, shell.total.currency)
        return Stored(shell.copy(paid = paid), disbursement, payments)
    }

    /**
     * What is left, converted to the main currency at the disbursement's rate (its frozen main leg
     * over the amount disbursed), half-even.
     */
    private fun LoanWithTotals.toDomain(): Loan {
        val remaining = (loan.totalAmountMinor - paidMinor).coerceAtLeast(0)
        val disbursed = loan.disbursedAmountMinor
        val remainingMain = if (disbursed == 0L) 0L else BigDecimal.valueOf(remaining)
            .multiply(BigDecimal.valueOf(disbursementMainMinor))
            .divide(BigDecimal.valueOf(disbursed), 0, RoundingMode.HALF_EVEN)
            .longValueExact()
        return loan.toDomain(paidMinor = paidMinor, remainingMainMinor = remainingMain)
    }
}
