package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.domain.model.Loan
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
    private val loanDao: LoanDao,
    private val transactionDao: TransactionDao
) {

    fun getAllLoans(): Flow<List<Loan>> =
        loanDao.getAllLoansWithPaidCount().map { rows -> rows.map { it.loan.toDomain(it.paidCount) } }

    fun observeLoan(id: Long): Flow<Loan?> =
        loanDao.getLoanWithPaidCount(id).map { it?.loan?.toDomain(it.paidCount) }

    suspend fun getLoanById(id: Long): Loan? = observeLoan(id).first()

    suspend fun insertLoanWithInstallments(loan: Loan): Long {
        val loanId = loanDao.insert(loan.toEntity())
        val installments = (1..loan.totalInstallments).map { number ->
            val installmentDate = loan.installmentDate(number)
            TransactionEntity(
                amount = loan.installmentAmount,
                description = "${loan.name} - Installment $number/${loan.totalInstallments}",
                date = installmentDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                type = loan.loanType.toTransactionType().name,
                categoryId = loan.categoryId,
                loanId = loanId,
                installmentNumber = number
            )
        }
        transactionDao.insertAll(installments)
        return loanId
    }

    suspend fun updateLoan(loan: Loan) {
        val existingLoan = loanDao.getById(loan.id)
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
        val updated = transactionDao.getInstallmentsByLoanId(loanId).mapNotNull { installment ->
            val installmentNumber = installment.installmentNumber ?: return@mapNotNull null
            val newDate = frequency.advance(newStartDate, interval, (installmentNumber - 1).toLong())
            installment.copy(date = newDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        }
        transactionDao.updateAll(updated)
    }

    suspend fun deleteLoan(id: Long) {
        transactionDao.deleteByLoanId(id)
        loanDao.deleteById(id)
    }
}
