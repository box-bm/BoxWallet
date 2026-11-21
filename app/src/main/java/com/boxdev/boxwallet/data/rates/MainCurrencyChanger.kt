package com.boxdev.boxwallet.data.rates

import androidx.room.withTransaction
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.data.local.dao.AppMetaDao
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AppMetaEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.repository.BackupRepository
import com.boxdev.boxwallet.domain.money.AmountInput
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/** What the warning before a main currency change announces (spec 4, step 1). */
data class ChangePreview(val transactions: Int, val loans: Int, val from: Currency, val to: Currency)

/** What the change did (spec 4, step 6). Counts are movements. */
data class ChangeSummary(val converted: Int, val approximate: Int, val manualReplaced: Int)

/**
 * Changes the main currency by recalculating every movement in one Room transaction (spec 4).
 *
 * - One account: the account follows the new main currency. Each movement re-resolves its account
 *   rate (original → new), recomputes the account amount from the original, and takes main rate 1.
 * - Several accounts: accounts and their amounts stay; only the main leg (account → new) changes.
 *
 * Network work happens only in [prefetch]; [apply] reads local rates and falls back to the nearest
 * one (APPROXIMATE). With no rate at all it throws [MissingRateException] and nothing is written.
 */
class MainCurrencyChanger internal constructor(
    private val database: BoxWalletDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val loanDao: LoanDao,
    private val appMetaDao: AppMetaDao,
    private val lookup: RateLookup,
    private val prefs: UserPreferences,
    private val backup: BackupRepository,
    private val lock: CurrencyChangeLock,
    private val today: () -> LocalDate,
    /** Whether the rate source answers at all (the `latest` file); tells "not published" from offline. */
    private val sourceReachable: suspend () -> Boolean,
) {

    @Inject
    constructor(
        database: BoxWalletDatabase,
        transactionDao: TransactionDao,
        accountDao: AccountDao,
        loanDao: LoanDao,
        appMetaDao: AppMetaDao,
        rates: ExchangeRateRepository,
        prefs: UserPreferences,
        backup: BackupRepository,
        lock: CurrencyChangeLock,
    ) : this(
        database, transactionDao, accountDao, loanDao, appMetaDao, rates, prefs, backup, lock,
        today = { LocalDate.now() },
        sourceReachable = { rates.syncLatest() },
    )

    suspend fun preview(to: Currency): ChangePreview =
        ChangePreview(transactionDao.countAll(), loanDao.countAll(), prefs.mainCurrency.first(), to)

    /**
     * Downloads the rates of every day the change needs and doesn't have locally (one request per
     * day, all currencies). False when a download fails; nothing is written to the movements.
     * Days outside the published history are skipped (they resolve as APPROXIMATE). A failure for
     * the last two days, which may not be published yet, is tolerated only when the source is
     * reachable: another download of this run succeeded or the `latest` file answers.
     */
    suspend fun prefetch(to: Currency, onProgress: (done: Int, total: Int) -> Unit): Boolean {
        val code = to.currencyCode
        val needed = if (accountDao.count() <= 1) transactionDao.distinctDayCurrenciesWhereCurrencyNot(code)
        else transactionDao.distinctDayAccountCurrenciesWhereAccountNot(code)
        val now = today()
        val missing = needed.groupBy({ it.day }, { it.code })
            .map { (day, codes) -> LocalDate.ofEpochDay(day) to codes + code }
            .filter { (day, _) -> !day.isBefore(ExchangeRateResolver.HISTORY_START) && !day.isAfter(now) }
            .filter { (day, codes) -> codes.any { lookup.exact(day, it) == null } }
            .map { it.first }
            .sorted()

        onProgress(0, missing.size)
        var anySucceeded = false
        var recentFailed = false
        missing.forEachIndexed { i, day ->
            currentCoroutineContext().ensureActive()
            when {
                lookup.download(day) -> anySucceeded = true
                day.isBefore(now.minusDays(1)) -> return false
                else -> recentFailed = true
            }
            onProgress(i + 1, missing.size)
        }
        return !recentFailed || anySucceeded || sourceReachable()
    }

    /**
     * Rewrites every movement for [to] and then makes it the main currency. Not cancellable once
     * the transaction starts; any failure rolls the whole change back and is rethrown.
     */
    suspend fun apply(
        to: Currency,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ChangeSummary = lock.mutex.withLock {
        if (prefs.mainCurrency.first() == to) return@withLock ChangeSummary(0, 0, 0)
        backup.writeLocalSnapshot()
        withContext(NonCancellable) {
            // The marker commits with the data, so "marker present" means the data is already in `to`.
            val summary = database.withTransaction {
                val result = recalculate(to, onProgress)
                appMetaDao.put(AppMetaEntity(META_PENDING_MAIN_CURRENCY, to.currencyCode))
                result
            }
            prefs.setMainCurrency(to.currencyCode)
            appMetaDao.delete(listOf(META_PENDING_MAIN_CURRENCY))
            summary
        }
    }

    /**
     * Finishes a change interrupted between the database commit and the DataStore write: the marker
     * is committed with the data, so while it exists the data is already in the pending currency.
     * Call at startup, before other DB work.
     */
    suspend fun completePending(): Unit = lock.mutex.withLock {
        val pending = appMetaDao.get(META_PENDING_MAIN_CURRENCY)
        if (pending == null) {
            finishChangeStartedByV1()
            return@withLock
        }
        prefs.takeLegacyPendingMainCurrency()
        prefs.setMainCurrency(pending)
        appMetaDao.delete(listOf(META_PENDING_MAIN_CURRENCY))
    }

    /**
     * Exists only to finish a change started by v1.0.0, which kept its marker in DataStore and
     * recovered with "the only account already holds the pending currency". v1.0.0 always has one
     * account, so the rule holds there; with more accounts the key is just dropped.
     */
    private suspend fun finishChangeStartedByV1() {
        val legacy = prefs.takeLegacyPendingMainCurrency() ?: return
        if (accountDao.count() > 1) return
        val account = prefs.defaultAccountId.first()?.let { accountDao.getById(it) }
            ?: accountDao.getAll().firstOrNull()
        if (account?.currencyCode == legacy) prefs.setMainCurrency(legacy)
    }

    private suspend fun recalculate(to: Currency, onProgress: (Int, Int) -> Unit): ChangeSummary {
        val amounts = TransactionAmounts(ExchangeRateResolver(LocalRates(lookup), today))
        val accounts = accountDao.getAll()
        val single = accounts.size <= 1
        val accountCurrency = accounts.associate { it.id to Currency.getInstance(it.currencyCode) }
        if (single) accounts.firstOrNull()?.let { accountDao.update(it.copy(currencyCode = to.currencyCode)) }

        val rows = transactionDao.getAllTransactionsSync()
        var approximate = 0
        var manualReplaced = 0
        rows.forEachIndexed { i, tx ->
            val day = LocalDate.ofEpochDay(Math.floorDiv(tx.date, MILLIS_PER_DAY))
            val updated = if (single) {
                val original = Money(tx.originalAmountMinor, Currency.getInstance(tx.currencyCode))
                val ready = amounts.compute(AmountInput(original, day, to, to)) as? AmountResult.Ready
                    ?: throw MissingRateException(original.currency, to)
                if (tx.accountRateSource == RateSource.MANUAL || tx.mainRateSource == RateSource.MANUAL) manualReplaced++
                if (ready.accountRate.source == RateSource.APPROXIMATE) approximate++
                tx.copy(
                    accountAmountMinor = ready.accountAmount.minor,
                    accountRate = ready.accountRate.value,
                    accountRateSource = ready.accountRate.source,
                ).withMain(ready.amount.minor, ready.mainRate.value, ready.mainRate.source)
            } else {
                val currency = accountCurrency.getValue(tx.accountId)
                val accountAmount = Money(tx.accountAmountMinor, currency)
                val ready = amounts.compute(AmountInput(accountAmount, day, currency, to)) as? AmountResult.Ready
                    ?: throw MissingRateException(currency, to)
                if (tx.mainRateSource == RateSource.MANUAL) manualReplaced++
                if (ready.mainRate.source == RateSource.APPROXIMATE) approximate++
                tx.withMain(ready.amount.minor, ready.mainRate.value, ready.mainRate.source)
            }
            transactionDao.update(updated)
            onProgress(i + 1, rows.size)
        }
        return ChangeSummary(rows.size, approximate, manualReplaced)
    }

    private fun TransactionEntity.withMain(amountMinor: Long, rate: BigDecimal, source: RateSource) =
        copy(amountMinor = amountMinor, mainRate = rate, mainRateSource = source)

    /** Local rates only (no network inside the transaction), memoized for the run. */
    private class LocalRates(private val delegate: RateLookup) : RateLookup {
        private val exact = HashMap<Pair<LocalDate, String>, BigDecimal?>()
        private val nearest = HashMap<Pair<LocalDate, String>, BigDecimal?>()

        override suspend fun exact(day: LocalDate, code: String): BigDecimal? =
            exact.getOrPut(day to code) { delegate.exact(day, code) }

        override suspend fun nearest(day: LocalDate, code: String): BigDecimal? =
            nearest.getOrPut(day to code) { delegate.nearest(day, code) }

        override suspend fun download(day: LocalDate): Boolean = false
    }

    companion object {
        /** `app_meta` key of a main currency change committed but not yet applied to DataStore. */
        const val META_PENDING_MAIN_CURRENCY = "pending_main_currency"
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
