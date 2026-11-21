package com.boxdev.boxwallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/** Cleared balance of one account, in the account's currency. */
data class AccountBalanceRow(val accountId: Long, val balanceMinor: Long)

/** Cleared balance expression shared by the per-account query and the one-shot lookup. */
private const val CLEARED_BALANCE =
    "SUM(CASE WHEN type IN ('INCOME','ADJUSTMENT_IN','TRANSFER_IN') THEN accountAmountMinor ELSE -accountAmountMinor END)"

@Dao
interface AccountDao {

    /** ABORT, never REPLACE: replacing would trip the RESTRICT key from transactions. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    /** Only once no movement references an account (RESTRICT key). */
    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("SELECT accountId, $CLEARED_BALANCE AS balanceMinor FROM transactions WHERE isCleared = 1 GROUP BY accountId")
    fun observeBalances(): Flow<List<AccountBalanceRow>>

    /** Cleared balance of one account in its currency; 0 without movements. */
    @Query("SELECT COALESCE($CLEARED_BALANCE, 0) FROM transactions WHERE isCleared = 1 AND accountId = :accountId")
    suspend fun getBalance(accountId: Long): Long
}
