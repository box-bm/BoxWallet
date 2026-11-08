package com.boxdev.boxwallet.ui.screens.transaction

import com.boxdev.boxwallet.domain.money.AmountInput
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.domain.money.parseMajor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/**
 * The form's preview rates, resolved once per currency pair and day. Typing an amount only
 * recomputes the equivalent from the cached rate; a resolve (which may download the day's rates)
 * happens again only when the currency, account currency, main currency or date changes.
 *
 * Resolves run in [scope], not in the caller, so a keystroke that cancels the previous preview
 * doesn't cancel the shared resolve.
 */
class FormRateCache(
    private val amounts: TransactionAmounts,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class Key(val currency: Currency, val accountCurrency: Currency, val main: Currency, val day: LocalDate)

    private val cache = HashMap<Key, Deferred<AmountResult>>()
    /** When a non-Ready result (no rate yet, e.g. offline) was first seen; it is retried after [RETRY_MS]. */
    private val missSince = HashMap<Key, Long>()

    /** The rates of one unit of [Key.currency]; [AmountResult.Ready.accountRate] applies to any amount. */
    suspend fun rates(key: Key): AmountResult {
        val pending = synchronized(cache) {
            val since = missSince[key]
            if (since != null && clock() - since >= RETRY_MS) {
                cache.remove(key)
                missSince.remove(key)
            }
            cache.getOrPut(key) {
                scope.async {
                    amounts.compute(AmountInput(Money.of(BigDecimal.ONE, key.currency), key.day, key.accountCurrency, key.main))
                }
            }
        }
        try {
            val result = pending.await()
            if (result !is AmountResult.Ready) {
                synchronized(cache) { if (cache[key] === pending) missSince.putIfAbsent(key, clock()) }
            }
            return result
        } catch (e: Exception) {
            // A failed resolve must not stick (retry next time); this caller's own cancellation keeps it.
            if (pending.isCancelled) synchronized(cache) { if (cache[key] === pending) cache.remove(key) }
            throw e
        }
    }

    companion object {
        /** How long a "no rate yet" result is reused before resolving again (connectivity may be back). */
        const val RETRY_MS = 30_000L

        /** [amount] typed in [currency], converted to [accountCurrency] with the cached [rates]; null when not a number. */
        fun equivalent(amount: String, currency: Currency, accountCurrency: Currency, rates: AmountResult.Ready): String? =
            parseMajor(amount, currency)?.convert(accountCurrency, rates.accountRate.value)?.toMajor()?.toPlainString()
    }
}
