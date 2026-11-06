package com.boxdev.boxwallet.data.local.dao

import androidx.room.*
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Update
    suspend fun updateAll(transactions: List<TransactionEntity>)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions WHERE loanId = :loanId")
    suspend fun deleteByLoanId(loanId: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE loanId IS NULL ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE loanId IS NULL ORDER BY date DESC LIMIT :limit")
    fun getLatestTransactions(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate AND loanId IS NULL ORDER BY date DESC")
    fun getTransactionsBetweenDates(startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND isCleared = 1")
    fun getTotalIncome(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND isCleared = 1")
    fun getTotalExpense(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND isCleared = 1 AND date BETWEEN :startDate AND :endDate")
    fun getTotalIncomeBetweenDates(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND isCleared = 1 AND date BETWEEN :startDate AND :endDate")
    fun getTotalExpenseBetweenDates(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT * FROM transactions WHERE hasReminder = 1 AND reminderDate > :currentTime ORDER BY reminderDate ASC")
    fun getUpcomingReminders(currentTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE loanId = :loanId ORDER BY installmentNumber ASC")
    fun getInstallmentsByLoanIdFlow(loanId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE loanId = :loanId ORDER BY installmentNumber ASC")
    suspend fun getInstallmentsByLoanId(loanId: Long): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE loanId = :loanId AND isCleared = 1")
    fun getPaidInstallmentCount(loanId: Long): Flow<Int>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE isRecurrent = 1 AND loanId IS NULL ORDER BY date DESC")
    fun getRecurrentTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE nextOccurrenceDate <= :currentDate AND isRecurrent = 1")
    suspend fun getTransactionsDueForRecurrence(currentDate: Long): List<TransactionEntity>
}
