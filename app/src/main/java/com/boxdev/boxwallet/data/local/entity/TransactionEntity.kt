package com.boxdev.boxwallet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val currency: String,
    val description: String = "",
    val date: Long,
    val type: String, // "INCOME" or "EXPENSE"
    val isCleared: Boolean = false,
    val hasReminder: Boolean = false,
    val reminderDate: Long? = null,
    val reminderSecondsBefore: Long? = null,
    val categoryId: Long? = null,
    val loanId: Long? = null,
    val installmentNumber: Int? = null,
    val isRecurrent: Boolean = false,
    val recurrenceFrequency: String? = null,
    val recurrenceInterval: Int? = null,
    val nextOccurrenceDate: Long? = null,
    val parentRecurrenceId: Long? = null
)

enum class TransactionType {
    INCOME,
    EXPENSE
}
