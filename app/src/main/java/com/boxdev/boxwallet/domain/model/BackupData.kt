package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.data.local.entity.InstallmentPlanEntity
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity

/**
 * Backup file contents (spec 6.3). There is one format, [CURRENT_VERSION]: a file in any other
 * version is refused on restore (7 added the category color). Exchange rates are not backed up; they are downloaded again.
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
    val installmentPlans: List<InstallmentPlanEntity> = emptyList(),
    /** The account new movements default to. */
    val defaultAccountId: Long? = null,
) {
    companion object {
        const val CURRENT_VERSION = 7
    }
}
