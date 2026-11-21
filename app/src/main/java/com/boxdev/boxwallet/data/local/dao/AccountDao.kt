package com.boxdev.boxwallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/** Balance of one account, in the account's currency. */
data class AccountBalanceRow(val accountId: Long, val balanceMinor: Long)

/** Balance expression shared by the per-account query and the one-shot lookup (over `transactions t`). */
private const val BALANCE =
    "SUM(CASE WHEN t.type IN ('INCOME','ADJUSTMENT_IN','TRANSFER_IN') THEN t.accountAmountMinor ELSE -t.accountAmountMinor END)"

/**
 * Which movements count: cards ignore the cleared flag and count everything dated before `:endOfToday`
 * (start of tomorrow); every other account type counts cleared movements only.
 */
private const val COUNTED =
    "((a.type = 'CREDIT_CARD' AND t.date < :endOfToday) OR (a.type <> 'CREDIT_CARD' AND t.isCleared = 1))"

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

    /** A card's second-currency child, if any (at most one). */
    @Query("SELECT * FROM accounts WHERE parentAccountId = :parentId LIMIT 1")
    suspend fun getChildOf(parentId: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE parentAccountId = :parentId LIMIT 1")
    fun observeChildOf(parentId: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("SELECT t.accountId AS accountId, $BALANCE AS balanceMinor FROM transactions t JOIN accounts a ON a.id = t.accountId WHERE $COUNTED GROUP BY t.accountId")
    fun observeBalances(endOfToday: Long): Flow<List<AccountBalanceRow>>

    /** Balance of one account in its currency; 0 without counted movements. */
    @Query("SELECT COALESCE($BALANCE, 0) FROM transactions t JOIN accounts a ON a.id = t.accountId WHERE t.accountId = :accountId AND $COUNTED")
    suspend fun getBalance(accountId: Long, endOfToday: Long): Long
}
