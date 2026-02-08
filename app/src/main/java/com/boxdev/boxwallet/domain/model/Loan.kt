package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.LoanEntity
import java.time.LocalDate
import java.time.ZoneId

data class Loan(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val totalAmount: Double,
    val currency: String,
    val totalInstallments: Int,
    val categoryId: Long? = null,
    val startDate: LocalDate,
    val paidInstallments: Int = 0,
    val nextDueDate: LocalDate? = null
) {
    val installmentAmount: Double get() = if (totalInstallments > 0) totalAmount / totalInstallments else 0.0
    val remainingInstallments: Int get() = totalInstallments - paidInstallments
    val progressFraction: Float get() = if (totalInstallments == 0) 0f else paidInstallments.toFloat() / totalInstallments
    val isComplete: Boolean get() = paidInstallments >= totalInstallments
}

fun LoanEntity.toDomain(paidInstallments: Int = 0, nextDueDate: LocalDate? = null): Loan = Loan(
    id = id,
    name = name,
    description = description,
    totalAmount = totalAmount,
    currency = currency,
    totalInstallments = totalInstallments,
    categoryId = categoryId,
    startDate = LocalDate.ofEpochDay(startDate / (24 * 60 * 60 * 1000)),
    paidInstallments = paidInstallments,
    nextDueDate = nextDueDate
)

fun Loan.toEntity(): LoanEntity = LoanEntity(
    id = id,
    name = name,
    description = description,
    totalAmount = totalAmount,
    currency = currency,
    totalInstallments = totalInstallments,
    categoryId = categoryId,
    startDate = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
)
