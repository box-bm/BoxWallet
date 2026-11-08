package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.domain.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Currency

data class Loan(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    /** In the loan's own currency. */
    val totalAmount: Money,
    val totalInstallments: Int,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val startDate: LocalDateTime,
    val paidInstallments: Int = 0,
    val loanType: LoanType = LoanType.EXPENSE,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val frequencyInterval: Int = 1,
    /** Unpaid installments' real `amount`, in minor units of the main currency. */
    val remainingMainMinor: Long = 0,
) {
    private val baseInstallmentMinor: Long
        get() = if (totalInstallments > 0) totalAmount.minor / totalInstallments else 0L

    /** Installment [number] (1-based): an even share, with the last one absorbing the remainder. */
    fun installmentAmount(number: Int): Money {
        if (totalInstallments <= 0 || number !in 1..totalInstallments) return Money(0, totalAmount.currency)
        val base = baseInstallmentMinor
        val minor = if (number == totalInstallments) totalAmount.minor - base * (totalInstallments - 1) else base
        return Money(minor, totalAmount.currency)
    }

    val remainingInstallments: Int get() = totalInstallments - paidInstallments
    val progressFraction: Float get() = if (totalInstallments == 0) 0f else paidInstallments.toFloat() / totalInstallments
    val isComplete: Boolean get() = paidInstallments >= totalInstallments
    /** Money still to move on this loan: to pay for [LoanType.EXPENSE], to collect for [LoanType.INCOME]. */
    val remainingAmount: Money get() = Money(totalAmount.minor - settledAmount.minor, totalAmount.currency)
    /** Money already moved on this loan: paid for [LoanType.EXPENSE], collected for [LoanType.INCOME]. */
    val settledAmount: Money
        get() = when {
            totalInstallments <= 0 -> Money(0, totalAmount.currency)
            isComplete -> totalAmount
            else -> Money(paidInstallments.coerceAtLeast(0) * baseInstallmentMinor, totalAmount.currency)
        }
    /** Due date of the first unpaid installment, or null once the loan is complete. */
    val nextDueDate: LocalDate? get() = if (isComplete) null else installmentDate(paidInstallments + 1)

    /** Scheduled due date of installment [number] (1-based). */
    fun installmentDate(number: Int): LocalDate =
        frequency.advance(startDate.toLocalDate(), frequencyInterval, (number - 1).toLong())

    fun status(today: LocalDate): LoanStatus = when {
        isComplete -> LoanStatus.PAID
        nextDueDate?.isBefore(today) == true -> LoanStatus.OVERDUE
        else -> LoanStatus.ACTIVE
    }
}

enum class LoanStatus { PAID, OVERDUE, ACTIVE }

/**
 * What is still outstanding across loans, kept apart by direction and never netted.
 * Minor units of the main currency.
 */
data class LoanTotals(val toPay: Long, val toCollect: Long)

fun List<Loan>.outstandingTotals(): LoanTotals = LoanTotals(
    toPay = filter { it.loanType == LoanType.EXPENSE && !it.isComplete }.sumOf { it.remainingMainMinor },
    toCollect = filter { it.loanType == LoanType.INCOME && !it.isComplete }.sumOf { it.remainingMainMinor },
)

fun LoanEntity.toDomain(paidInstallments: Int = 0, remainingMainMinor: Long = 0): Loan = Loan(
    id = id,
    name = name,
    description = description,
    totalAmount = Money(totalAmountMinor, Currency.getInstance(currencyCode)),
    totalInstallments = totalInstallments,
    accountId = accountId,
    categoryId = categoryId,
    startDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(startDate), ZoneOffset.UTC),
    paidInstallments = paidInstallments,
    loanType = LoanType.valueOf(loanType),
    frequency = RecurrenceFrequency.valueOf(frequency),
    frequencyInterval = frequencyInterval,
    remainingMainMinor = remainingMainMinor,
)

fun Loan.toEntity(): LoanEntity = LoanEntity(
    id = id,
    name = name,
    description = description,
    totalAmountMinor = totalAmount.minor,
    currencyCode = totalAmount.currency.currencyCode,
    accountId = accountId,
    totalInstallments = totalInstallments,
    categoryId = categoryId,
    startDate = startDate.toInstant(ZoneOffset.UTC).toEpochMilli(),
    loanType = loanType.name,
    frequency = frequency.name,
    frequencyInterval = frequencyInterval
)
