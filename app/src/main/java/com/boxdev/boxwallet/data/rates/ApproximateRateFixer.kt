package com.boxdev.boxwallet.data.rates

import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.data.local.dao.AppMetaDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/**
 * Upgrades movements saved with an approximate rate once the exact rate of their day is known.
 * MANUAL rates are never touched.
 */
class ApproximateRateFixer @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val appMetaDao: AppMetaDao,
    private val resolver: ExchangeRateResolver,
    private val prefs: UserPreferences,
    private val lock: CurrencyChangeLock,
) {

    /** Returns how many movements were corrected. */
    suspend fun fix(): Int = lock.mutex.withLock {
        // A main currency change is rewriting every movement; it resolves them itself.
        if (appMetaDao.get(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY) != null) return@withLock 0
        val pending = transactionDao.getWithApproximateRates()
        if (pending.isEmpty()) return@withLock 0
        val main = prefs.mainCurrency.first()
        var fixed = 0
        for (tx in pending) {
            val account = accountDao.getById(tx.accountId) ?: continue
            val updated = correct(tx, Currency.getInstance(account.currencyCode), main) ?: continue
            transactionDao.update(updated)
            fixed++
        }
        fixed
    }

    private suspend fun correct(tx: TransactionEntity, accountCurrency: Currency, main: Currency): TransactionEntity? {
        val day = LocalDate.ofEpochDay(Math.floorDiv(tx.date, MILLIS_PER_DAY))
        var result = tx

        if (tx.accountRateSource == RateSource.APPROXIMATE) {
            val rate = resolver.resolve(day, Currency.getInstance(tx.currencyCode), accountCurrency)
            if (rate != null && rate.source == RateSource.AUTO) {
                val accountAmount = Money(tx.originalAmountMinor, Currency.getInstance(tx.currencyCode))
                    .convert(accountCurrency, rate.value).minor
                result = result.copy(
                    accountRate = rate.value,
                    accountRateSource = RateSource.AUTO,
                    accountAmountMinor = accountAmount,
                )
            }
        }
        if (tx.mainRateSource == RateSource.APPROXIMATE) {
            val rate = resolver.resolve(day, accountCurrency, main)
            if (rate != null && rate.source == RateSource.AUTO) {
                result = result.copy(mainRate = rate.value, mainRateSource = RateSource.AUTO)
            }
        }
        if (result == tx) return null
        // The account amount or main rate changed, so the main-currency amount follows.
        val amountMinor = Money(result.accountAmountMinor, accountCurrency).convert(main, result.mainRate).minor
        return result.copy(amountMinor = amountMinor)
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
