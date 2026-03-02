package com.boxdev.boxwallet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val totalAmount: Double,
    val totalInstallments: Int,
    val categoryId: Long? = null,
    val startDate: Long,
    val loanType: String = "EXPENSE",
    val frequency: String = "MONTHLY",
    val frequencyInterval: Int = 1
)
