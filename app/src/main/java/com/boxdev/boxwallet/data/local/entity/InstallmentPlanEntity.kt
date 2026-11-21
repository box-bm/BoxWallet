package com.boxdev.boxwallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.boxdev.boxwallet.domain.money.RateSource
import java.math.BigDecimal

/**
 * A credit-card purchase split into installments (visacuotas). Each installment is a
 * [TransactionEntity] that points here; deleting the plan deletes them all.
 * The purchase converts to the account currency once: [accountRate] applies to every installment.
 */
@Entity(
    tableName = "installment_plans",
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
    ],
    indices = [Index("accountId"), Index("categoryId")],
)
data class InstallmentPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val description: String,
    val categoryId: Long? = null,
    /** The purchase's original currency (ISO 4217) and amount in minor units. */
    val currencyCode: String,
    val originalMinor: Long,
    /** The purchase amount in the account's currency. */
    val accountTotalMinor: Long,
    val accountRate: BigDecimal,
    val accountRateSource: RateSource,
    val installments: Int,
    /** Local wall-clock time stored as if it were UTC, like `transactions.date`. */
    val purchaseDate: Long,
)
