package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity

data class BackupData(
    val version: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList()
)
