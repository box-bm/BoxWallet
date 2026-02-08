package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
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

    suspend fun insertTransaction(transaction: Transaction): Long =
        transactionDao.insert(transaction.toEntity())

    suspend fun updateTransaction(transaction: Transaction) =
        transactionDao.update(transaction.toEntity())

    suspend fun deleteTransaction(id: Long) = transactionDao.deleteById(id)

    suspend fun getTransactionById(id: Long): Transaction? = transactionDao.getById(id)?.toDomain()

    fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions().map { it.map { e -> e.toDomain() } }

    fun getLatestTransactions(limit: Int = 3): Flow<List<Transaction>> =
        transactionDao.getLatestTransactions(limit).map { it.map { e -> e.toDomain() } }

    fun getTransactionsBetweenDates(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> {
        val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return transactionDao.getTransactionsBetweenDates(startMillis, endMillis)
            .map { it.map { e -> e.toDomain() } }
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
            FinancialSummary(
                totalIncome = totalIncome ?: 0.0,
                totalExpense = totalExpense ?: 0.0,
                monthlyIncome = monthlyIncome ?: 0.0,
                monthlyExpense = monthlyExpense ?: 0.0
            )
        }
    }

    suspend fun restoreTransactions(transactions: List<TransactionEntity>) {
        transactions.forEach { transaction ->
            transactionDao.insert(transaction)
        }
    }

    fun getUpcomingReminders(): Flow<List<Transaction>> =
        transactionDao.getUpcomingReminders(System.currentTimeMillis())
            .map { it.map { e -> e.toDomain() } }

    fun getInstallmentsByLoanId(loanId: Long): Flow<List<Transaction>> =
        transactionDao.getInstallmentsByLoanIdFlow(loanId).map { it.map { e -> e.toDomain() } }

    suspend fun getAllTransactionsSync() = transactionDao.getAllTransactionsSync()
}
