package com.boxdev.boxwallet.ui.components

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.TransferPair
import com.boxdev.boxwallet.domain.model.accountLabel

/**
 * "<from> → <to>" for a transfer's row, each end named like the pickers ([accountLabel]: a card's second
 * currency reads "<card> · <code>"), or null while either account is unknown.
 */
fun transferTitle(pair: TransferPair?, accounts: Map<Long, Account>): String? {
    pair ?: return null
    val from = accounts[pair.fromAccountId] ?: return null
    val to = accounts[pair.toAccountId] ?: return null
    fun label(account: Account) = accountLabel(account, account.parentAccountId?.let { accounts[it] })
    return "${label(from)} → ${label(to)}"
}
