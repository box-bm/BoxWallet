package com.boxdev.boxwallet.domain.money

import com.boxdev.boxwallet.domain.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/** What a movement needs to freeze its two conversions (spec 2.3): original → account → main. */
data class AmountInput(
    val original: Money,
    val day: LocalDate,
    val accountCurrency: Currency,
    val main: Currency,
    /** Original → account rate typed by the user; ignored when both currencies match. */
    val manualAccountRate: BigDecimal? = null,
)

sealed interface AmountResult {
    data class Ready(
        val accountAmount: Money,
        val accountRate: ExchangeRate,
        val amount: Money,
        val mainRate: ExchangeRate,
    ) : AmountResult

    /** No rate is known for a leg at all; the user must type one. */
    data object NeedsManualRate : AmountResult
}

/** No rate is known to convert [from] into [to] and there is no form to type one in. */
class MissingRateException(val from: Currency, val to: Currency) :
    IllegalStateException("No exchange rate available for ${from.currencyCode} → ${to.currencyCode}")

/** Computes the frozen account and main amounts of a movement from the rates of its day. */
class TransactionAmounts(private val resolver: ExchangeRateResolver) {

    suspend fun compute(input: AmountInput): AmountResult {
        val original = input.original
        val accountRate = when {
            original.currency == input.accountCurrency -> ExchangeRate.IDENTITY
            input.manualAccountRate != null -> ExchangeRate(input.manualAccountRate, RateSource.MANUAL)
            else -> resolver.resolve(input.day, original.currency, input.accountCurrency)
                ?: return AmountResult.NeedsManualRate
        }
        val accountAmount = original.convert(input.accountCurrency, accountRate.value)
        val mainRate = resolver.resolve(input.day, input.accountCurrency, input.main)
            ?: return AmountResult.NeedsManualRate
        return AmountResult.Ready(accountAmount, accountRate, accountAmount.convert(input.main, mainRate.value), mainRate)
    }

    /**
     * [transaction] with its conversions re-resolved for its own date and amount, keeping any
     * MANUAL leg as typed (spec 3.3: MANUAL is never overwritten). Null when a rate is missing.
     */
    suspend fun refresh(transaction: Transaction): Transaction? {
        val accountCurrency = transaction.accountAmount.currency
        val main = transaction.amount.currency
        val input = AmountInput(
            original = transaction.original,
            day = transaction.date.toLocalDate(),
            accountCurrency = accountCurrency,
            main = main,
            manualAccountRate = transaction.accountRate.takeIf { it.source == RateSource.MANUAL }?.value,
        )
        val ready = if (transaction.mainRate.source == RateSource.MANUAL) {
            val accountLeg = compute(input.copy(main = accountCurrency)) as? AmountResult.Ready ?: return null
            accountLeg.copy(
                amount = accountLeg.accountAmount.convert(main, transaction.mainRate.value),
                mainRate = transaction.mainRate,
            )
        } else {
            compute(input) as? AmountResult.Ready ?: return null
        }
        return transaction.withAmounts(ready)
    }
}

fun Transaction.withAmounts(ready: AmountResult.Ready): Transaction = copy(
    accountAmount = ready.accountAmount,
    accountRate = ready.accountRate,
    amount = ready.amount,
    mainRate = ready.mainRate,
)

/**
 * Whether an edit from [previous] invalidates the frozen rates: the currency changed, or the day or
 * the amount changed while the account rate isn't MANUAL. Any other edit keeps the stored conversion.
 */
fun Transaction.needsRateRefresh(previous: Transaction): Boolean {
    if (original.currency != previous.original.currency) return true
    if (previous.accountRate.source == RateSource.MANUAL) return false
    return date.toLocalDate() != previous.date.toLocalDate() || original != previous.original
}
