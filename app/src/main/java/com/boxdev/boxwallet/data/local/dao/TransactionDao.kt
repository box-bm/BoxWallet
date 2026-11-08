package com.boxdev.boxwallet.data.local.dao

import androidx.room.*
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** A local date (epoch day) and a currency that a movement on that day converts from. */
data class DayCurrency(val day: Long, val code: String)

data class CurrencyUsage(val currencyCode: String, val uses: Int, val lastUsed: Long)

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
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

    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date < :endDate AND loanId IS NOT NULL ORDER BY date ASC")
    fun getInstallmentsBetweenDates(startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT SUM(amountMinor) FROM transactions WHERE type = 'INCOME' AND isCleared = 1")
    fun getTotalIncome(): Flow<Long?>

    @Query("SELECT SUM(amountMinor) FROM transactions WHERE type = 'EXPENSE' AND isCleared = 1")
    fun getTotalExpense(): Flow<Long?>

    @Query("SELECT SUM(amountMinor) FROM transactions WHERE type = 'INCOME' AND isCleared = 1 AND date BETWEEN :startDate AND :endDate")
    fun getTotalIncomeBetweenDates(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(amountMinor) FROM transactions WHERE type = 'EXPENSE' AND isCleared = 1 AND date BETWEEN :startDate AND :endDate")
    fun getTotalExpenseBetweenDates(startDate: Long, endDate: Long): Flow<Long?>

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

    @Query(
        "SELECT currencyCode, COUNT(*) AS uses, MAX(date) AS lastUsed FROM transactions " +
            "GROUP BY currencyCode ORDER BY uses DESC, lastUsed DESC LIMIT :limit"
    )
    fun recentCurrencies(limit: Int): Flow<List<CurrencyUsage>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countAll(): Int

    @Query("SELECT * FROM transactions WHERE accountRateSource = 'APPROXIMATE' OR mainRateSource = 'APPROXIMATE'")
    suspend fun getWithApproximateRates(): List<TransactionEntity>

    /** Local dates (as epoch days) of movements whose currency is not [code]. */
    @Query("SELECT DISTINCT date / 86400000 FROM transactions WHERE currencyCode != :code ORDER BY 1")
    suspend fun distinctEpochDaysWhereCurrencyNot(code: String): List<Long>

    /** Days and original currencies of movements whose currency is not [code]. */
    @Query("SELECT DISTINCT date / 86400000 AS day, currencyCode AS code FROM transactions WHERE currencyCode != :code")
    suspend fun distinctDayCurrenciesWhereCurrencyNot(code: String): List<DayCurrency>

    /** Days and account currencies of movements whose account is not in [code]. */
    @Query(
        "SELECT DISTINCT t.date / 86400000 AS day, a.currencyCode AS code FROM transactions t " +
            "JOIN accounts a ON a.id = t.accountId WHERE a.currencyCode != :code"
    )
    suspend fun distinctDayAccountCurrenciesWhereAccountNot(code: String): List<DayCurrency>
}
