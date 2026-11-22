package com.boxdev.boxwallet.data.local.dao

import androidx.room.*
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** Row type of a loan's payments: the direction opposite to its disbursement. */
private const val PAYMENT_TYPE = "CASE loans.loanType WHEN 'INCOME' THEN 'LOAN_IN' ELSE 'LOAN_OUT' END"
private const val DISBURSEMENT_TYPE = "CASE loans.loanType WHEN 'INCOME' THEN 'LOAN_OUT' ELSE 'LOAN_IN' END"

/** Sum of the payments, in the loan's (= its account's) currency. */
private const val PAID =
    "(SELECT COALESCE(SUM(t.accountAmountMinor), 0) FROM transactions t WHERE t.loanId = loans.id AND t.type = $PAYMENT_TYPE)"

/** The disbursement in the main currency; the loan's remaining is converted proportionally from it. */
private const val DISBURSEMENT_MAIN =
    "(SELECT COALESCE(SUM(t.amountMinor), 0) FROM transactions t WHERE t.loanId = loans.id AND t.type = $DISBURSEMENT_TYPE)"

private const val WITH_TOTALS = "SELECT loans.*, $PAID AS paidMinor, $DISBURSEMENT_MAIN AS disbursementMainMinor FROM loans"

/** A loan with what its movements add up to. */
data class LoanWithTotals(
    @Embedded val loan: LoanEntity,
    val paidMinor: Long,
    val disbursementMainMinor: Long,
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

    @Query("$WITH_TOTALS ORDER BY loans.startDate DESC, loans.id DESC")
    fun getAll(): Flow<List<LoanWithTotals>>

    @Query("$WITH_TOTALS WHERE loans.accountId = :accountId ORDER BY loans.startDate DESC, loans.id DESC")
    fun getByAccount(accountId: Long): Flow<List<LoanWithTotals>>

    @Query("$WITH_TOTALS WHERE loans.id = :id")
    fun getWithTotals(id: Long): Flow<LoanWithTotals?>

    /** The disbursement and every payment of [loanId], oldest first. */
    @Query("SELECT * FROM transactions WHERE loanId = :loanId ORDER BY date ASC, id ASC")
    fun getMovements(loanId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE loanId = :loanId ORDER BY date ASC, id ASC")
    suspend fun getMovementsSync(loanId: Long): List<TransactionEntity>

    @Query("SELECT * FROM loans")
    suspend fun getAllLoansSync(): List<LoanEntity>

    @Query("DELETE FROM loans")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM loans")
    suspend fun countAll(): Int
}
