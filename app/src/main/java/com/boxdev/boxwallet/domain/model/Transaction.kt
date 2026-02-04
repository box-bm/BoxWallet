package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import java.time.LocalDate
import java.time.ZoneId

data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val comment: String,
    val date: LocalDate,
    val type: TransactionType,
    val isLoan: Boolean = false,
    val totalInstallments: Int? = null,
    val currentInstallment: Int? = null,
    val installmentAmount: Double? = null,
    val parentLoanId: Long? = null,
    val hasReminder: Boolean = false,
    val reminderDate: LocalDate? = null
)

fun TransactionEntity.toDomain(): Transaction {
    return Transaction(
        id = id,
        amount = amount,
        comment = comment,
        date = LocalDate.ofEpochDay(date / (24 * 60 * 60 * 1000)),
        type = type,
        isLoan = isLoan,
        totalInstallments = totalInstallments,
        currentInstallment = currentInstallment,
        installmentAmount = installmentAmount,
        parentLoanId = parentLoanId,
        hasReminder = hasReminder,
        reminderDate = reminderDate?.let { LocalDate.ofEpochDay(it / (24 * 60 * 60 * 1000)) }
    )
}

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        amount = amount,
        comment = comment,
        date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        type = type,
        isLoan = isLoan,
        totalInstallments = totalInstallments,
        currentInstallment = currentInstallment,
        installmentAmount = installmentAmount,
        parentLoanId = parentLoanId,
        hasReminder = hasReminder,
        reminderDate = reminderDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    )
}
