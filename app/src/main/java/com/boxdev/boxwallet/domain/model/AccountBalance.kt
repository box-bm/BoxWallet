package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateSource
import java.util.Currency

data class AccountWithBalance(val account: Account, val balance: Money)

/** [approximate] is true when the sum involved conversions, estimated rates or skipped accounts. */
data class TotalBalance(val amount: Money, val approximate: Boolean)

/**
 * Sums the accounts flagged `includeInTotal` in [main]. Archived accounts still count (their money exists).
 * A null rate skips that account and marks the total approximate.
 */
suspend fun totalBalance(
    accounts: List<AccountWithBalance>,
    main: Currency,
    rateToMain: suspend (Currency) -> ExchangeRate?,
): TotalBalance {
    var sum = Money(0, main)
    var approximate = false
    for ((account, balance) in accounts) {
        if (!account.includeInTotal) continue
        if (balance.currency == main) {
            sum += balance
            continue
        }
        approximate = true
        val rate = rateToMain(balance.currency) ?: continue
        if (rate.source == RateSource.APPROXIMATE) approximate = true
        sum += balance.convert(main, rate.value)
    }
    return TotalBalance(sum, approximate)
}
