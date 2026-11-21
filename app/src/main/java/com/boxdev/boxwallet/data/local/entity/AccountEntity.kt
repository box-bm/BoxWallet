package com.boxdev.boxwallet.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [parentAccountId] has no SQLite foreign key on purpose: adding one would mean rebuilding `accounts`,
 * whose children reference it with RESTRICT. `AccountRepository` validates it instead.
 */
@Entity(
    tableName = "accounts",
    indices = [Index("parentAccountId")],
)
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
    @ColumnInfo(defaultValue = "")
    val description: String = "",
    /** Material icon name. */
    @ColumnInfo(defaultValue = "AccountBalance")
    val icon: String = "AccountBalance",
    /** `AccountColor.key`. */
    @ColumnInfo(defaultValue = "DEFAULT")
    val color: String = "DEFAULT",
    @ColumnInfo(defaultValue = "1")
    val includeInTotal: Boolean = true,
    val parentAccountId: Long? = null,
    /** Credit cards only, in the account's currency. */
    val creditLimitMinor: Long? = null,
    /** Day of month (1–31), credit cards only. */
    val statementClosingDay: Int? = null,
    /** Day of month (1–31), credit cards only. */
    val paymentDueDay: Int? = null,
    val dueReminderDaysBefore: Int? = null,
)
