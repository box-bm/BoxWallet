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
            onDelete = ForeignKey.RESTRICT,
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
    val accountId: Long,
    val startDate: Long,
    val loanType: String = "EXPENSE",
    /** 0 for an open loan paid with free-form payments. */
    val totalInstallments: Int = 0,
    val frequency: String = "MONTHLY",
    val frequencyInterval: Int = 1,
    val firstDueDate: Long? = null,
    val expectedDate: Long? = null,
)
