package com.boxdev.boxwallet.data.rates

import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes movement writes with the main currency change ([CurrencyChangeLock]). A movement
 * computed before a change committed still carries the old account or main currency; inside the
 * lock it is recomputed for the current currencies, so no stale row lands after the commit.
 */
@Singleton
class MovementWriteGuard @Inject constructor(
    private val lock: CurrencyChangeLock,
    private val prefs: UserPreferences,
    private val accountDao: AccountDao,
    private val amounts: TransactionAmounts,
) {

    /** Runs [block] under the lock; [Current] reflects the currencies as they are now. */
    suspend fun <T> write(block: suspend (Current) -> T): T = lock.mutex.withLock {
        val main = prefs.mainCurrency.first()
        val accounts = accountDao.getAll().associate { it.id to Currency.getInstance(it.currencyCode) }
        block(Current(main, accounts))
    }

    inner class Current internal constructor(
        val main: Currency,
        private val accounts: Map<Long, Currency>,
    ) {
        fun accountCurrency(accountId: Long): Currency = accounts[accountId] ?: main

        /**
         * [transaction] unchanged when it already matches the current account and main currencies;
         * otherwise recomputed with [TransactionAmounts.refresh]. A MANUAL leg is kept only when its
         * target currency didn't change (spec 4: a rate set against the old currency no longer applies).
         * Throws [MissingRateException] when no rate is known, so nothing inconsistent is written.
         */
        suspend fun revalidate(transaction: Transaction): Transaction {
            val accountCurrency = accountCurrency(transaction.accountId)
            val accountChanged = transaction.accountAmount.currency != accountCurrency
            val mainChanged = transaction.amount.currency != main
            if (!accountChanged && !mainChanged) return transaction
            val relabeled = transaction.copy(
                accountAmount = Money(transaction.accountAmount.minor, accountCurrency),
                accountRate = if (accountChanged) transaction.accountRate.automatic() else transaction.accountRate,
                amount = Money(transaction.amount.minor, main),
                mainRate = transaction.mainRate.automatic(),
            )
            return amounts.refresh(relabeled) ?: throw MissingRateException(transaction.original.currency, main)
        }
    }

    private fun ExchangeRate.automatic() = if (source == RateSource.MANUAL) copy(source = RateSource.AUTO) else this
}
