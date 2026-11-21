package com.boxdev.boxwallet.data.repository

import android.content.Context
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.TotalBalance
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.domain.model.totalBalance
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val userPreferences: UserPreferences,
    private val rates: RateLookup,
    @param:ApplicationContext private val context: Context,
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
}
