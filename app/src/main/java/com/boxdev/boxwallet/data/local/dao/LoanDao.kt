package com.boxdev.boxwallet.data.local.dao

import androidx.room.*
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import kotlinx.coroutines.flow.Flow

private const val PAID_COUNT =
    "(SELECT COUNT(*) FROM transactions WHERE transactions.loanId = loans.id AND transactions.isCleared = 1)"

/** Sum of the unpaid installments' `amountMinor` (main currency). */
private const val REMAINING_MAIN =
    "(SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE transactions.loanId = loans.id AND transactions.isCleared = 0)"

data class LoanWithPaidCount(
    @Embedded val loan: LoanEntity,
    val paidCount: Int,
    val remainingMainMinor: Long,
)

@Dao
interface LoanDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(loan: LoanEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(loans: List<LoanEntity>)

    @Update
    suspend fun update(loan: LoanEntity)

    @Delete
    suspend fun delete(loan: LoanEntity)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getById(id: Long): LoanEntity?

    @Query("SELECT loans.*, $PAID_COUNT AS paidCount, $REMAINING_MAIN AS remainingMainMinor FROM loans ORDER BY loans.startDate DESC")
    fun getAllLoansWithPaidCount(): Flow<List<LoanWithPaidCount>>

    @Query("SELECT loans.*, $PAID_COUNT AS paidCount, $REMAINING_MAIN AS remainingMainMinor FROM loans WHERE loans.id = :id")
    fun getLoanWithPaidCount(id: Long): Flow<LoanWithPaidCount?>

    @Query("SELECT * FROM loans")
    suspend fun getAllLoansSync(): List<LoanEntity>

    @Query("DELETE FROM loans")
    suspend fun deleteAll()
}
