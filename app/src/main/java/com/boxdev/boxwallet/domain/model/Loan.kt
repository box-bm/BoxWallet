package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Currency

data class LoanPlan(
    val installments: Int,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val firstDue: LocalDate,
)

/**
 * A loan lives inside one account: [total] is in that account's currency, the disbursement and
 * every payment are movements of [accountId]. [total] is what has to be repaid; [disbursed] is what
 * changed hands at the start (may differ from [total]). [paid] is the sum of the payments so far.
 */
data class Loan(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    /** [LoanType.INCOME] = I lent it, [LoanType.EXPENSE] = I borrowed it. */
    val loanType: LoanType,
    val total: Money,
    /** Received (borrowed) or given (lent) at the start; the disbursement movement's amount. */
    val disbursed: Money = total,
    val accountId: Long,
    /** Disbursement date. */
    val date: LocalDateTime,
    val plan: LoanPlan? = null,
    val expectedDate: LocalDate? = null,
    val paid: Money = Money(0, total.currency),
    /** Still outstanding, in minor units of the main currency. */
    val remainingMainMinor: Long = 0,
) {
    val remaining: Money get() = Money((total.minor - paid.minor).coerceAtLeast(0), total.currency)
    val isSettled: Boolean get() = paid.minor >= total.minor

    /** How much of the total has been paid, from 0 to 1 (0 when the total is not positive). */
    val progressFraction: Float
        get() = if (total.minor <= 0) 0f else (paid.minor.toFloat() / total.minor).coerceIn(0f, 1f)

    fun schedule(today: LocalDate = LocalDate.now()): List<ScheduledInstallment> =
        plan?.let { allocate(total, it, paid, today) } ?: emptyList()

    /** First installment not fully paid, or null without a plan or once settled. */
    fun nextDue(today: LocalDate = LocalDate.now()): ScheduledInstallment? =
        schedule(today).firstOrNull { it.state != InstallmentState.PAID }

    fun status(today: LocalDate): LoanStatus = when {
        isSettled -> LoanStatus.PAID
        plan != null ->
            if (schedule(today).any { it.state == InstallmentState.OVERDUE }) LoanStatus.OVERDUE else LoanStatus.ACTIVE
        expectedDate?.isBefore(today) == true -> LoanStatus.OVERDUE
        else -> LoanStatus.ACTIVE
    }

    /** Movement that hands the money over: lending sends it out, borrowing brings it in. */
    val disbursementType: TransactionType
        get() = if (loanType == LoanType.INCOME) TransactionType.LOAN_OUT else TransactionType.LOAN_IN

    /** Movement for each payment: the opposite direction of the disbursement. */
    val paymentType: TransactionType
        get() = if (loanType == LoanType.INCOME) TransactionType.LOAN_IN else TransactionType.LOAN_OUT
}

enum class LoanStatus { PAID, OVERDUE, ACTIVE }

/**
 * What is still outstanding across loans, kept apart by direction and never netted.
 * Minor units of the main currency.
 */
data class LoanTotals(val toPay: Long, val toCollect: Long)

fun List<Loan>.outstandingTotals(): LoanTotals = LoanTotals(
    toPay = filter { it.loanType == LoanType.EXPENSE && !it.isSettled }.sumOf { it.remainingMainMinor },
    toCollect = filter { it.loanType == LoanType.INCOME && !it.isSettled }.sumOf { it.remainingMainMinor },
)

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun LocalDate.toEpochMilli(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun LoanEntity.toDomain(paidMinor: Long, remainingMainMinor: Long): Loan {
    val currency = Currency.getInstance(currencyCode)
    val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startDate), ZoneOffset.UTC)
    return Loan(
        id = id,
        name = name,
        description = description,
        loanType = LoanType.valueOf(loanType),
        total = Money(totalAmountMinor, currency),
        disbursed = Money(disbursedAmountMinor, currency),
        accountId = accountId,
        date = start,
        plan = if (totalInstallments > 0) {
            LoanPlan(
                installments = totalInstallments,
                frequency = RecurrenceFrequency.valueOf(frequency),
                interval = frequencyInterval,
                firstDue = firstDueDate?.toLocalDate() ?: start.toLocalDate(),
            )
        } else null,
        expectedDate = expectedDate?.toLocalDate(),
        paid = Money(paidMinor, currency),
        remainingMainMinor = remainingMainMinor,
    )
}

fun Loan.toEntity(): LoanEntity = LoanEntity(
    id = id,
    name = name,
    description = description,
    totalAmountMinor = total.minor,
    disbursedAmountMinor = disbursed.minor,
    currencyCode = total.currency.currencyCode,
    accountId = accountId,
    startDate = date.toInstant(ZoneOffset.UTC).toEpochMilli(),
    loanType = loanType.name,
    totalInstallments = plan?.installments ?: 0,
    frequency = plan?.frequency?.name ?: RecurrenceFrequency.MONTHLY.name,
    frequencyInterval = plan?.interval ?: 1,
    firstDueDate = plan?.firstDue?.toEpochMilli(),
    expectedDate = expectedDate?.toEpochMilli(),
)
