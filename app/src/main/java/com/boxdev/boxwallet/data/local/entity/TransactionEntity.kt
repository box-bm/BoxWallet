package com.boxdev.boxwallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.boxdev.boxwallet.domain.money.RateSource
import java.math.BigDecimal

/**
 * A movement. Amounts are minor units:
 * - [originalAmountMinor] in [currencyCode] (always positive),
 * - [accountAmountMinor] in the account's currency (= original × [accountRate]),
 * - [amountMinor] in the main currency (= account amount × [mainRate]); every total sums this column.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentRecurrenceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("date"),
        Index("categoryId"),
        Index("loanId"),
        Index("currencyCode"),
        Index("parentRecurrenceId"),
        Index("transferId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val type: String, // "INCOME" or "EXPENSE"
    /** Local wall-clock time stored as if it were UTC. */
    val date: Long,
    val description: String = "",
    val categoryId: Long? = null,
    val loanId: Long? = null,
    val installmentNumber: Int? = null,
    val originalAmountMinor: Long,
    val currencyCode: String,
    val accountAmountMinor: Long,
    val accountRate: BigDecimal,
    val accountRateSource: RateSource,
    val amountMinor: Long,
    val mainRate: BigDecimal,
    val mainRateSource: RateSource,
    val isCleared: Boolean = false,
    val hasReminder: Boolean = false,
    val reminderDate: Long? = null,
    val reminderSecondsBefore: Long? = null,
    val isRecurrent: Boolean = false,
    val recurrenceFrequency: String? = null,
    val recurrenceInterval: Int? = null,
    val nextOccurrenceDate: Long? = null,
    val parentRecurrenceId: Long? = null,
    /** Shared by the two legs of a transfer between accounts. */
    val transferId: String? = null,
)

enum class TransactionType {
    INCOME,
    EXPENSE,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT
}

/** Money enters the account (adds to its balance). */
val TransactionType.isInflow: Boolean
    get() = this == TransactionType.INCOME || this == TransactionType.ADJUSTMENT_IN

/** Only real income/expense feeds categories, charts, reports and exports. */
val TransactionType.countsInReports: Boolean
    get() = this == TransactionType.INCOME || this == TransactionType.EXPENSE

/** Balance corrections that are not income or expense. */
val TransactionType.isAdjustment: Boolean
    get() = this == TransactionType.ADJUSTMENT_IN || this == TransactionType.ADJUSTMENT_OUT
