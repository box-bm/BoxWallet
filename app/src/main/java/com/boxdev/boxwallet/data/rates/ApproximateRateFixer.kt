package com.boxdev.boxwallet.data.rates

import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.data.local.dao.AppMetaDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.InstallmentPlanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.rates.InstallmentPlanRates.Companion.purchaseDay
import com.boxdev.boxwallet.domain.money.ExchangeRate
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
 * MANUAL rates are never touched. A plan's purchase leg is upgraded at its purchase day, for the
 * plan and all its installments at once ([InstallmentPlanRates.reconvert]); an installment's account
 * leg is never re-converted at its own date.
 */
class ApproximateRateFixer @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val appMetaDao: AppMetaDao,
    private val resolver: ExchangeRateResolver,
    private val prefs: UserPreferences,
    private val lock: CurrencyChangeLock,
    private val plans: InstallmentPlanRates,
) {

    /** Returns how many movements were corrected. */
    suspend fun fix(): Int = lock.mutex.withLock {
        // A main currency change is rewriting every movement; it resolves them itself.
        if (appMetaDao.get(MainCurrencyChanger.META_PENDING_MAIN_CURRENCY) != null) return@withLock 0
        val approximatePlans = plans.withApproximateRate()
        val pending = transactionDao.getWithApproximateRates()
        if (approximatePlans.isEmpty() && pending.isEmpty()) return@withLock 0
        val main = prefs.mainCurrency.first()
        val fixedRows = HashSet<Long>()
        for (plan in approximatePlans) {
            val account = accountDao.getById(plan.accountId) ?: continue
            fixedRows += fixPlan(plan, Currency.getInstance(account.currencyCode), main)
        }
        var fixed = fixedRows.size
        // Re-read: a fixed plan's installments may already carry exact main rates.
        for (tx in if (fixedRows.isEmpty()) pending else transactionDao.getWithApproximateRates()) {
            val account = accountDao.getById(tx.accountId) ?: continue
            val updated = correct(tx, Currency.getInstance(account.currencyCode), main) ?: continue
            transactionDao.update(updated)
            if (fixedRows.add(tx.id)) fixed++
        }
        fixed
    }

    /** Ids of the plan's installments rewritten; none while the purchase day has no exact rate. */
    private suspend fun fixPlan(plan: InstallmentPlanEntity, accountCurrency: Currency, main: Currency): List<Long> {
        val rate = resolver.resolve(plan.purchaseDay(), Currency.getInstance(plan.currencyCode), accountCurrency)
        if (rate == null || rate.source != RateSource.AUTO) return emptyList()
        return plans.reconvert(plan, accountCurrency, rate, main) { row, day ->
            val kept = ExchangeRate(row.mainRate, row.mainRateSource)
            if (row.mainRateSource != RateSource.APPROXIMATE) kept
            else resolver.resolve(day, accountCurrency, main)?.takeIf { it.source == RateSource.AUTO } ?: kept
        }.map { it.id }
    }

    private suspend fun correct(tx: TransactionEntity, accountCurrency: Currency, main: Currency): TransactionEntity? {
        val day = LocalDate.ofEpochDay(Math.floorDiv(tx.date, MILLIS_PER_DAY))
        var result = tx

        // A plan's purchase converted once: its installments keep that account leg.
        if (tx.accountRateSource == RateSource.APPROXIMATE && tx.installmentPlanId == null) {
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
