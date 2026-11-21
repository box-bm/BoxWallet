package com.boxdev.boxwallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId")],
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    /** Minor units of [currencyCode]. */
    val totalAmountMinor: Long,
    /** ISO 4217; immutable once the loan has payments. */
    val currencyCode: String,
    val accountId: Long? = null,
    val totalInstallments: Int,
    val categoryId: Long? = null,
    val startDate: Long,
    val loanType: String = "EXPENSE",
    val frequency: String = "MONTHLY",
    val frequencyInterval: Int = 1,
    /** `SCHEDULED` (fixed installments) or an open-ended kind. */
    val kind: String = "SCHEDULED",
    val expectedDate: Long? = null,
)
