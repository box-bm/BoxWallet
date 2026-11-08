package com.boxdev.boxwallet.data.repository

import androidx.room.withTransaction
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.AmountInput
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.domain.money.withAmounts
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepository @Inject constructor(
    private val database: BoxWalletDatabase,
    private val loanDao: LoanDao,
    private val transactionDao: TransactionDao,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferences,
    private val transactionAmounts: TransactionAmounts,
) {

    fun getAllLoans(): Flow<List<Loan>> =
        loanDao.getAllLoansWithPaidCount().map { rows -> rows.map { it.loan.toDomain(it.paidCount, it.remainingMainMinor) } }

    fun observeLoan(id: Long): Flow<Loan?> =
        loanDao.getLoanWithPaidCount(id).map { it?.let { row -> row.loan.toDomain(row.paidCount, row.remainingMainMinor) } }

    suspend fun getLoanById(id: Long): Loan? = observeLoan(id).first()

    suspend fun insertLoanWithInstallments(loan: Loan): Long {
        val installments = buildInstallments(loan)
        val loanId = loanDao.insert(loan.toEntity())
        transactionDao.insertAll(installments.map { it.copy(loanId = loanId).toEntity() })
        return loanId
    }

    /**
     * Installments are in the loan's currency; each carries an estimate at the rate of its due date,
     * re-resolved when it is marked paid. Computed before any write so a missing rate saves nothing.
     */
    private suspend fun buildInstallments(loan: Loan): List<Transaction> {
        val accountId = loan.accountId ?: accountRepository.defaultAccount().id
        val main = userPreferences.mainCurrency.first()
        val accountCurrency = accountRepository.observeCurrencies().first()[accountId] ?: main
        return (1..loan.totalInstallments).map { number ->
            val date = loan.installmentDate(number).atStartOfDay()
            val original = loan.installmentAmount(number)
            val input = AmountInput(original, date.toLocalDate(), accountCurrency, main)
            val ready = transactionAmounts.compute(input) as? AmountResult.Ready
                ?: throw MissingRateException(original.currency, accountCurrency)
            Transaction(
                accountId = accountId,
                type = loan.loanType.toTransactionType(),
                date = date,
                description = "${loan.name} - Installment $number/${loan.totalInstallments}",
                categoryId = loan.categoryId,
                installmentNumber = number,
                original = original,
                accountAmount = original,
                accountRate = ExchangeRate.IDENTITY,
                amount = original,
                mainRate = ExchangeRate.IDENTITY,
            ).withAmounts(ready)
        }
    }

    suspend fun updateLoan(loan: Loan) {
        val existingLoan = loanDao.getById(loan.id)
        val installmentRows = transactionDao.getInstallmentsByLoanId(loan.id)
        if (existingLoan != null && installmentRows.none { it.isCleared } && existingLoan.termsDiffer(loan)) {
            // Nothing is paid yet: rebuild every installment so they match the loan's currency and split.
            // Conversions are computed first, so a missing rate throws before anything is written.
            val installments = buildInstallments(loan)
            database.withTransaction {
                loanDao.update(loan.toEntity())
                transactionDao.deleteByLoanId(loan.id)
                transactionDao.insertAll(installments.map { it.copy(loanId = loan.id).toEntity() })
            }
            return
        }
        loanDao.update(loan.toEntity())

        if (existingLoan != null) {
            val oldStartDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(existingLoan.startDate), ZoneOffset.UTC).toLocalDate()
            if (oldStartDate != loan.startDate.toLocalDate()) {
                updateInstallmentDates(loan.id, loan.startDate.toLocalDate(), loan.frequency, loan.frequencyInterval)
            }
        }
    }

    private suspend fun updateInstallmentDates(
        loanId: Long,
        newStartDate: LocalDate,
        frequency: RecurrenceFrequency,
        interval: Int
    ) {
        val main = userPreferences.mainCurrency.first()
        val currencies = accountRepository.observeCurrencies().first()
        val updated = transactionDao.getInstallmentsByLoanId(loanId).mapNotNull { installment ->
            val installmentNumber = installment.installmentNumber ?: return@mapNotNull null
            val newDate = frequency.advance(newStartDate, interval, (installmentNumber - 1).toLong())
            val moved = installment.copy(date = newDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            // A new due date means a new rate day; MANUAL legs are kept by refresh.
            val domain = moved.toDomain(currencies[moved.accountId] ?: main, main)
            transactionAmounts.refresh(domain)?.toEntity() ?: moved
        }
        transactionDao.updateAll(updated)
    }

    suspend fun deleteLoan(id: Long) {
        transactionDao.deleteByLoanId(id)
        loanDao.deleteById(id)
    }

    private fun com.boxdev.boxwallet.data.local.entity.LoanEntity.termsDiffer(loan: Loan): Boolean {
        val new = loan.toEntity()
        return currencyCode != new.currencyCode || totalAmountMinor != new.totalAmountMinor ||
            totalInstallments != new.totalInstallments || frequency != new.frequency ||
            frequencyInterval != new.frequencyInterval || startDate != new.startDate
    }
}
