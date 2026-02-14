package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepository @Inject constructor(
    private val loanDao: LoanDao,
    private val transactionDao: TransactionDao
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllLoans(): Flow<List<Loan>> {
        return loanDao.getAllLoans().flatMapLatest { loanEntities ->
            if (loanEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                val loanFlows = loanEntities.map { entity ->
                    transactionDao.getPaidInstallmentCount(entity.id).map { paidCount ->
                        val nextDueDate = if (paidCount < entity.totalInstallments) {
                            val startDate = LocalDate.ofEpochDay(entity.startDate / (24 * 60 * 60 * 1000))
                            calculateInstallmentDate(
                                startDate,
                                paidCount,
                                RecurrenceFrequency.valueOf(entity.frequency),
                                entity.frequencyInterval
                            )
                        } else null
                        entity.toDomain(paidInstallments = paidCount, nextDueDate = nextDueDate)
                    }
                }
                combine(loanFlows) { it.toList() }
            }
        }
    }

    suspend fun getLoanById(id: Long): Loan? {
        val entity = loanDao.getById(id) ?: return null
        val paid = transactionDao.getInstallmentsByLoanId(id).count { it.isCleared }
        return entity.toDomain(paidInstallments = paid)
    }

    suspend fun insertLoanWithInstallments(loan: Loan): Long {
        val loanId = loanDao.insert(loan.toEntity())
        val installments = (1..loan.totalInstallments).map { number ->
            val installmentDate = calculateInstallmentDate(
                loan.startDate,
                number - 1,
                loan.frequency,
                loan.frequencyInterval
            )
            TransactionEntity(
                amount = loan.installmentAmount,
                currency = loan.currency,
                description = "${loan.name} - Installment $number/${loan.totalInstallments}",
                date = installmentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                type = loan.loanType.toTransactionType().name,
                categoryId = loan.categoryId,
                loanId = loanId,
                installmentNumber = number
            )
        }
        transactionDao.insertAll(installments)
        return loanId
    }

    suspend fun updateLoan(loan: Loan) = loanDao.update(loan.toEntity())

    suspend fun deleteLoan(id: Long) {
        transactionDao.deleteByLoanId(id)
        loanDao.deleteById(id)
    }

    suspend fun getAllLoansSync(): List<LoanEntity> = loanDao.getAllLoansSync()

    suspend fun restoreLoans(loans: List<LoanEntity>) {
        loans.forEach { loanDao.insert(it) }
    }

    private fun calculateInstallmentDate(
        startDate: LocalDate,
        installmentIndex: Int,
        frequency: RecurrenceFrequency,
        interval: Int
    ): LocalDate = when (frequency) {
        RecurrenceFrequency.DAILY -> startDate.plusDays(installmentIndex.toLong() * interval)
        RecurrenceFrequency.WEEKLY -> startDate.plusWeeks(installmentIndex.toLong() * interval)
        RecurrenceFrequency.BIWEEKLY -> startDate.plusWeeks(installmentIndex.toLong() * 2)
        RecurrenceFrequency.MONTHLY -> startDate.plusMonths(installmentIndex.toLong())
        RecurrenceFrequency.YEARLY -> startDate.plusYears(installmentIndex.toLong())
        RecurrenceFrequency.CUSTOM_MONTHS -> startDate.plusMonths(installmentIndex.toLong() * interval)
    }
}
