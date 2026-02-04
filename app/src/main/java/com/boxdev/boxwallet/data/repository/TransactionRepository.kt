package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.FinancialSummary
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {

    suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insert(transaction.toEntity())
    }

    suspend fun insertTransactionWithInstallments(transaction: Transaction): Long {
        val parentId = transactionDao.insert(transaction.toEntity())

        if (transaction.isLoan && transaction.totalInstallments != null && transaction.totalInstallments > 1) {
            val installmentAmount = transaction.amount / transaction.totalInstallments
            val installments = (1..transaction.totalInstallments).map { installmentNumber ->
                TransactionEntity(
                    amount = installmentAmount,
                    comment = "${transaction.comment} - Installment $installmentNumber/${transaction.totalInstallments}",
                    date = transaction.date.plusMonths(installmentNumber.toLong() - 1)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    type = transaction.type,
                    isLoan = false,
                    totalInstallments = transaction.totalInstallments,
                    currentInstallment = installmentNumber,
                    installmentAmount = installmentAmount,
                    parentLoanId = parentId,
                    hasReminder = transaction.hasReminder,
                    reminderDate = if (transaction.hasReminder) {
                        transaction.date.plusMonths(installmentNumber.toLong() - 1)
                            .minusDays(3)
                            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } else null
                )
            }
            transactionDao.insertAll(installments)
        }

        return parentId
    }

    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.update(transaction.toEntity())
    }

    suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteById(id)
        transactionDao.deleteInstallmentsByParentId(id)
    }

    suspend fun deleteTransactions(ids: List<Long>) {
        transactionDao.deleteByIds(ids)
        ids.forEach { id ->
            transactionDao.deleteInstallmentsByParentId(id)
        }
    }

    suspend fun getTransactionById(id: Long): Transaction? {
        return transactionDao.getById(id)?.toDomain()
    }

    fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getLatestExpenses(limit: Int = 7): Flow<List<Transaction>> {
        return transactionDao.getLatestExpenses(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getLatestIncomes(limit: Int = 5): Flow<List<Transaction>> {
        return transactionDao.getLatestIncomes(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getTransactionsBetweenDates(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> {
        val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return transactionDao.getTransactionsBetweenDates(startMillis, endMillis).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getTransactionsBetweenDatesSync(startDate: LocalDate, endDate: LocalDate): List<Transaction> {
        val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return transactionDao.getTransactionsBetweenDatesSync(startMillis, endMillis).map { it.toDomain() }
    }

    fun getFinancialSummary(): Flow<FinancialSummary> {
        val currentMonth = YearMonth.now()
        val startOfMonth = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfMonth = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return combine(
            transactionDao.getTotalIncome(),
            transactionDao.getTotalExpense(),
            transactionDao.getTotalIncomeBetweenDates(startOfMonth, endOfMonth),
            transactionDao.getTotalExpenseBetweenDates(startOfMonth, endOfMonth)
        ) { totalIncome, totalExpense, monthlyIncome, monthlyExpense ->
            val income = totalIncome ?: 0.0
            val expense = totalExpense ?: 0.0
            val monthIncome = monthlyIncome ?: 0.0
            val monthExpense = monthlyExpense ?: 0.0

            FinancialSummary(
                totalIncome = income,
                totalExpense = expense,
                monthlyIncome = monthIncome,
                monthlyExpense = monthExpense,
                balance = income - expense,
                monthlyBalance = monthIncome - monthExpense
            )
        }
    }

    fun getUpcomingReminders(): Flow<List<Transaction>> {
        return transactionDao.getUpcomingReminders(System.currentTimeMillis()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getAllTransactionsSync(): List<TransactionEntity> {
        return transactionDao.getAllTransactionsSync()
    }

    suspend fun restoreTransactions(transactions: List<TransactionEntity>) {
        transactionDao.deleteAll()
        transactionDao.insertAll(transactions)
    }
}
