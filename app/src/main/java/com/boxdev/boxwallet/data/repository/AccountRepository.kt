package com.boxdev.boxwallet.data.repository

import android.content.Context
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.UserPreferences
import androidx.room.withTransaction
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.TotalBalance
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.domain.model.totalBalance
import com.boxdev.boxwallet.domain.money.AmountInput
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/** A rule the account screens turn into a message instead of a crash. */
sealed class AccountRuleError : Exception() {
    /** The default account can't be deleted or archived. */
    data object IsDefault : AccountRuleError()

    /** At least one account must remain. */
    data object LastAccount : AccountRuleError()

    /** Accounts with movements (other than balance adjustments) are archived, not deleted. */
    data object HasMovements : AccountRuleError()
}

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val userPreferences: UserPreferences,
    private val rates: RateLookup,
    @param:ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val database: BoxWalletDatabase,
    private val amounts: TransactionAmounts,
    private val guard: MovementWriteGuard,
) {
    private val mutex = Mutex()

    /**
     * Returns the default account, creating it (CHECKING, main currency) when the database has none.
     * Idempotent and safe to call concurrently; the id is kept in DataStore as `defaultAccountId`.
     */
    suspend fun ensureDefaultAccount(): Account = mutex.withLock {
        userPreferences.defaultAccountId.first()
            ?.let { accountDao.getById(it) }
            ?.let { return@withLock it.toDomain() }

        val existing = accountDao.getAll().firstOrNull()
        val account = existing?.toDomain() ?: run {
            val created = Account(
                name = context.getString(R.string.account_default_name),
                type = AccountType.CHECKING,
                currency = userPreferences.mainCurrency.first(),
                createdAt = System.currentTimeMillis(),
            )
            created.copy(id = accountDao.insert(created.toEntity()))
        }
        userPreferences.setDefaultAccountId(account.id)
        account
    }

    /** The account new movements go to; created on demand so writes never miss the foreign key. */
    suspend fun defaultAccount(): Account = ensureDefaultAccount()

    suspend fun count(): Int = accountDao.count()

    /** Account id → currency, for mapping movements to the domain. */
    fun observeCurrencies(): Flow<Map<Long, Currency>> =
        accountDao.observeAll().map { rows -> rows.associate { it.id to Currency.getInstance(it.currencyCode) } }

    /** Accounts (by `sortOrder, id`) with their cleared balance; accounts without movements read 0. */
    fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>> =
        combine(accountDao.observeAll(), accountDao.observeBalances()) { accounts, balances ->
            val byAccount = balances.associate { it.accountId to it.balanceMinor }
            accounts.map {
                val account = it.toDomain()
                AccountWithBalance(account, Money(byAccount[account.id] ?: 0L, account.currency))
            }
        }

    /** Total in the main currency at today's rates, from local data only (nearest rate as fallback, never downloads). */
    fun observeTotalBalance(): Flow<TotalBalance> =
        combine(observeAccountsWithBalance(), userPreferences.mainCurrency) { accounts, main -> accounts to main }
            .map { (accounts, main) ->
                val resolver = ExchangeRateResolver(offline(rates))
                val today = LocalDate.now()
                totalBalance(accounts, main) { currency -> resolver.resolve(today, currency, main) }
            }

    /** Same lookup without the network: the resolver then falls back from exact to nearest rates. */
    private fun offline(delegate: RateLookup) = object : RateLookup by delegate {
        override suspend fun download(day: LocalDate) = false
    }

    fun observeAll(): Flow<List<Account>> = accountDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeDefaultAccountId(): Flow<Long?> = userPreferences.defaultAccountId

    suspend fun hasNonAdjustmentMovements(accountId: Long): Boolean =
        transactionDao.countNonAdjustmentByAccount(accountId) > 0

    /** Inserts [account]; a non-zero [currentBalance] (in the account's currency) becomes an opening adjustment. */
    suspend fun create(account: Account, currentBalance: Money?): Long = guard.write { current ->
        database.withTransaction {
            val id = accountDao.insert(account.toEntity())
            if (currentBalance != null && currentBalance.minor != 0L) {
                insertAdjustment(id, account.currency, current.main, currentBalance.minor)
            }
            id
        }
    }

    /** The currency is frozen once the account has movements besides adjustments. */
    suspend fun update(account: Account) = database.withTransaction {
        val stored = accountDao.getById(account.id)
            ?: throw IllegalStateException("Account ${account.id} does not exist")
        require(stored.isArchived == account.isArchived) { "use archive()" }
        check(stored.currencyCode == account.currency.currencyCode || !hasNonAdjustmentMovements(account.id)) {
            "Currency of account ${account.id} can't change once it has movements"
        }
        accountDao.update(account.toEntity())
    }

    /** Adds the adjustment that makes the cleared balance equal [realBalance]; nothing when already equal. */
    suspend fun adjustBalance(accountId: Long, realBalance: Money) {
        guard.write { current ->
            database.withTransaction {
                val currency = current.accountCurrency(accountId)
                require(realBalance.currency == currency) { "Balance must be in the account currency" }
                val difference = realBalance.minor - accountDao.getBalance(accountId)
                if (difference != 0L) insertAdjustment(accountId, currency, current.main, difference)
            }
        }
    }

    suspend fun archive(accountId: Long, archived: Boolean) {
        if (archived && userPreferences.defaultAccountId.first() == accountId) throw AccountRuleError.IsDefault
        val stored = accountDao.getById(accountId) ?: return
        accountDao.update(stored.copy(isArchived = archived))
    }

    /** Removes an account with no movements besides its adjustments (those go with it). */
    suspend fun delete(accountId: Long) {
        if (userPreferences.defaultAccountId.first() == accountId) throw AccountRuleError.IsDefault
        if (accountDao.count() <= 1) throw AccountRuleError.LastAccount
        database.withTransaction {
            if (hasNonAdjustmentMovements(accountId)) throw AccountRuleError.HasMovements
            transactionDao.deleteAdjustmentsByAccount(accountId)
            accountDao.deleteById(accountId)
        }
    }

    /** An archived account can't become the default; unarchive it first. */
    suspend fun setDefault(accountId: Long) {
        val account = accountDao.getById(accountId) ?: throw IllegalStateException("Account $accountId does not exist")
        check(!account.isArchived) { "Archived account $accountId can't be the default" }
        userPreferences.setDefaultAccountId(accountId)
    }

    /** A cleared adjustment of [deltaMinor] (sign picks the direction) dated now, in the account's currency. */
    private suspend fun insertAdjustment(accountId: Long, currency: Currency, main: Currency, deltaMinor: Long) {
        val original = Money(kotlin.math.abs(deltaMinor), currency)
        val now = LocalDateTime.now()
        val ready = amounts.compute(AmountInput(original, now.toLocalDate(), currency, main)) as? AmountResult.Ready
            ?: throw MissingRateException(currency, main)
        val adjustment = Transaction(
            accountId = accountId,
            type = if (deltaMinor > 0) TransactionType.ADJUSTMENT_IN else TransactionType.ADJUSTMENT_OUT,
            date = now,
            original = original,
            accountAmount = ready.accountAmount,
            accountRate = ready.accountRate,
            amount = ready.amount,
            mainRate = ready.mainRate,
            isCleared = true,
        )
        transactionDao.insert(adjustment.toEntity())
    }
}
