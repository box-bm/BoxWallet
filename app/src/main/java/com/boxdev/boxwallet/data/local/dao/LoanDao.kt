package com.boxdev.boxwallet.data.local.dao

import androidx.room.*
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import kotlinx.coroutines.flow.Flow

private const val PAID_COUNT =
    "(SELECT COUNT(*) FROM transactions WHERE transactions.loanId = loans.id AND transactions.isCleared = 1)"

data class LoanWithPaidCount(
    @Embedded val loan: LoanEntity,
    val paidCount: Int
)

@Dao
interface LoanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loan: LoanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(loans: List<LoanEntity>)

    @Update
    suspend fun update(loan: LoanEntity)

    @Delete
    suspend fun delete(loan: LoanEntity)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getById(id: Long): LoanEntity?

    @Query("SELECT loans.*, $PAID_COUNT AS paidCount FROM loans ORDER BY loans.startDate DESC")
    fun getAllLoansWithPaidCount(): Flow<List<LoanWithPaidCount>>

    @Query("SELECT loans.*, $PAID_COUNT AS paidCount FROM loans WHERE loans.id = :id")
    fun getLoanWithPaidCount(id: Long): Flow<LoanWithPaidCount?>

    @Query("SELECT * FROM loans")
    suspend fun getAllLoansSync(): List<LoanEntity>

    @Query("DELETE FROM loans")
    suspend fun deleteAll()
}
