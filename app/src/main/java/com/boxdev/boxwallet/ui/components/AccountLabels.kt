package com.boxdev.boxwallet.ui.components

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import java.util.Locale

/**
 * How pickers name [account]: a card's second currency reads "<card name> · <currency symbol>"
 * ([parent] is its card), any other account just its name.
 */
fun accountLabel(account: Account, parent: Account?, locale: Locale = Locale.getDefault()): String =
    if (account.parentAccountId != null && parent != null) {
        "${parent.name} · ${CurrencyCatalog.symbol(account.currency, locale)}"
    } else {
        account.name
    }

/** [accounts] in their order, each card's second currency moved right after its card (or kept in place without it). */
fun withChildrenAfterParents(accounts: List<Account>): List<Account> {
    val ids = accounts.mapTo(HashSet()) { it.id }
    val childrenOf = accounts.filter { it.parentAccountId in ids }.groupBy { it.parentAccountId }
    return accounts.flatMap { account ->
        when {
            account.parentAccountId in ids -> emptyList()
            else -> listOf(account) + childrenOf[account.id].orEmpty()
        }
    }
}
