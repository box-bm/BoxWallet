package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity

/**
 * Backup file contents (spec 6.3). Version 3: accounts, minor-unit amounts, currencies and rates.
 * Version 4: account customization (description, icon, color, total inclusion, cards) and the
 * default account. Exchange rates are not backed up; they are downloaded again.
 */
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    /** Main currency the stored main-currency amounts (`amountMinor`) are expressed in. */
    val mainCurrencyCode: String,
    val accounts: List<AccountEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
    /** Version 4: the account new movements default to; null in older backups. */
    val defaultAccountId: Long? = null,
) {
    companion object {
        const val CURRENT_VERSION = 4
    }
}
