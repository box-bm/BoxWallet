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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllLoans(): Flow<List<Loan>> {
        return loanDao.getAllLoans().flatMapLatest { loanEntities ->
            if (loanEntities.isEmpty()) {
                flowOf(emptyList())
            } else {
                val loanFlows = loanEntities.map { entity ->
                    transactionDao.getPaidInstallmentCount(entity.id).map { paidCount ->
                        val nextDueDate = if (paidCount < entity.totalInstallments) {
                            val startDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(entity.startDate), ZoneOffset.UTC).toLocalDate()
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
                loan.startDate.toLocalDate(),
                number - 1,
                loan.frequency,
                loan.frequencyInterval
            )
            TransactionEntity(
                amount = loan.installmentAmount,
                currency = loan.currency,
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
        val installments = transactionDao.getInstallmentsByLoanId(loanId)
        installments.forEach { installment ->
            val installmentNumber = installment.installmentNumber ?: return@forEach
            val newDate = calculateInstallmentDate(
                newStartDate,
                installmentNumber - 1,
                frequency,
                interval
            )
            val updatedInstallment = installment.copy(
                date = newDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            )
            transactionDao.update(updatedInstallment)
        }
    }

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
