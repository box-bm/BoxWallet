package com.boxdev.boxwallet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** `CASH`, `CHECKING`, `SAVINGS`, `CREDIT_CARD` or `OTHER`. */
    val type: String,
    /** ISO 4217; immutable once the account has movements. */
    val currencyCode: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    /** Epoch millis. */
    val createdAt: Long,
)
