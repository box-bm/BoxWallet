package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.LoanEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

data class Loan(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val totalAmount: Double,
    val totalInstallments: Int,
    val categoryId: Long? = null,
    val startDate: LocalDateTime,
    val paidInstallments: Int = 0,
    val nextDueDate: LocalDate? = null,
    val loanType: LoanType = LoanType.EXPENSE,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val frequencyInterval: Int = 1
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
    totalInstallments = totalInstallments,
    categoryId = categoryId,
    startDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(startDate), ZoneOffset.UTC),
    paidInstallments = paidInstallments,
    nextDueDate = nextDueDate,
    loanType = LoanType.valueOf(loanType),
    frequency = RecurrenceFrequency.valueOf(frequency),
    frequencyInterval = frequencyInterval
)

fun Loan.toEntity(): LoanEntity = LoanEntity(
    id = id,
    name = name,
    description = description,
    totalAmount = totalAmount,
    totalInstallments = totalInstallments,
    categoryId = categoryId,
    startDate = startDate.toInstant(ZoneOffset.UTC).toEpochMilli(),
    loanType = loanType.name,
    frequency = frequency.name,
    frequencyInterval = frequencyInterval
)
