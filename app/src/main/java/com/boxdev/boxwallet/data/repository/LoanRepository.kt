package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
                            entity.startDate.let { startMillis ->
                                java.time.LocalDate.ofEpochDay(startMillis / (24 * 60 * 60 * 1000))
                                    .plusMonths(paidCount.toLong())
                            }
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
            TransactionEntity(
                amount = loan.installmentAmount,
                currency = loan.currency,
                description = "${loan.name} - Installment $number/${loan.totalInstallments}",
                date = loan.startDate.plusMonths(number.toLong() - 1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                type = "EXPENSE",
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
}
