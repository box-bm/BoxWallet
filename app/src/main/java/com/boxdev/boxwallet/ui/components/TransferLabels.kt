package com.boxdev.boxwallet.ui.components

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.TransferPair

/** "<from> → <to>" for a transfer's row, or null while either account is unknown. */
fun transferTitle(pair: TransferPair?, accounts: Map<Long, Account>): String? {
    pair ?: return null
    val from = accounts[pair.fromAccountId] ?: return null
    val to = accounts[pair.toAccountId] ?: return null
    return "${from.name} → ${to.name}"
}
