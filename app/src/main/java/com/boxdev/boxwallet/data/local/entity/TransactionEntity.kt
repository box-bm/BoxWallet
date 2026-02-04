package com.boxdev.boxwallet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val comment: String,
    val date: Long, // Timestamp in milliseconds
    val type: TransactionType,
    val isLoan: Boolean = false,
    val totalInstallments: Int? = null,
    val currentInstallment: Int? = null,
    val installmentAmount: Double? = null,
    val parentLoanId: Long? = null, // Links installments to parent loan
    val hasReminder: Boolean = false,
    val reminderDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class TransactionType {
    INCOME,
    EXPENSE
}
