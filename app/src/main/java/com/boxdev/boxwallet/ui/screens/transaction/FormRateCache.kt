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
class FormRateCache(private val amounts: TransactionAmounts, private val scope: CoroutineScope) {

    data class Key(val currency: Currency, val accountCurrency: Currency, val main: Currency, val day: LocalDate)

    private val cache = HashMap<Key, Deferred<AmountResult>>()

    /** The rates of one unit of [Key.currency]; [AmountResult.Ready.accountRate] applies to any amount. */
    suspend fun rates(key: Key): AmountResult {
        val pending = synchronized(cache) {
            cache.getOrPut(key) {
                scope.async {
                    amounts.compute(AmountInput(Money.of(BigDecimal.ONE, key.currency), key.day, key.accountCurrency, key.main))
                }
            }
        }
        try {
            return pending.await()
        } catch (e: Exception) {
            // A failed resolve must not stick (retry next time); this caller's own cancellation keeps it.
            if (pending.isCancelled) synchronized(cache) { if (cache[key] === pending) cache.remove(key) }
            throw e
        }
    }

    companion object {
        /** [amount] typed in [currency], converted to [accountCurrency] with the cached [rates]; null when not a number. */
        fun equivalent(amount: String, currency: Currency, accountCurrency: Currency, rates: AmountResult.Ready): String? =
            parseMajor(amount, currency)?.convert(accountCurrency, rates.accountRate.value)?.toMajor()?.toPlainString()
    }
}
