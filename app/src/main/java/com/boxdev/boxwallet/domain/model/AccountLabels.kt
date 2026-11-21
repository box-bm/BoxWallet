package com.boxdev.boxwallet.domain.model

/**
 * How pickers name [account]: a card's second currency reads "<card name> · <ISO code>" (e.g.
 * "Visa BAC · USD"; [parent] is its card), any other account just its name.
 */
fun accountLabel(account: Account, parent: Account?): String =
    if (account.parentAccountId != null && parent != null) {
        "${parent.name} · ${account.currency.currencyCode}"
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
