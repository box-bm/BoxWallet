package com.boxdev.boxwallet.ui.screens.analytics

import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.inReports
import com.boxdev.boxwallet.domain.model.total
import java.time.YearMonth
import com.boxdev.boxwallet.data.local.entity.TransactionType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Pure Reports logic, kept out of the view model and composables so it can be unit tested.

/** Income and expense per month in [months]; balance adjustments never count. */
fun List<Transaction>.monthlyTotals(months: List<YearMonth>): List<MonthlyData> {
    val byMonth = inReports().groupBy { YearMonth.from(it.date) }
    return months.map { month ->
        val monthTx = byMonth[month].orEmpty()
        MonthlyData(month, monthTx.total(TransactionType.INCOME), monthTx.total(TransactionType.EXPENSE))
    }
}

/** The previous-period range a "vs previous" figure compares against. */
data class ComparisonRange(val start: LocalDate, val end: LocalDate, val toDate: Boolean)

/**
 * While the current period ([start]..[end]) is still in progress on [today], the previous period is
 * cut to the same number of elapsed days, so 6 days of November are compared with 6 days of
 * October rather than all of it. A finished (or future) period compares in full.
 */
fun comparisonRange(
    start: LocalDate,
    end: LocalDate,
    prevStart: LocalDate,
    prevEnd: LocalDate,
    today: LocalDate,
): ComparisonRange {
    val inProgress = !today.isBefore(start) && today.isBefore(end)
    if (!inProgress) return ComparisonRange(prevStart, prevEnd, toDate = false)
    val clipped = minOf(prevEnd, prevStart.plusDays(ChronoUnit.DAYS.between(start, today)))
    return ComparisonRange(prevStart, clipped, toDate = true)
}

/** Paid, still open, or past its due date and unpaid. The one rule Reports and Loan detail share. */
fun installmentState(isCleared: Boolean, due: LocalDate, today: LocalDate): InstallmentState = when {
    isCleared -> InstallmentState.PAID
    due.isBefore(today) -> InstallmentState.OVERDUE
    else -> InstallmentState.OPEN
}

/**
 * Loan installments falling due in a period: counts, money to pay and to collect kept apart, one
 * state per installment for the count bar, and unpaid installments grouped once per loan.
 */
fun summarizeInstallments(
    installments: List<Transaction>,
    loanNames: Map<Long, String>,
    today: LocalDate,
): InstallmentSummary? {
    if (installments.isEmpty()) return null
    val paid = installments.filter { it.isCleared }
    fun overdue(tx: Transaction) = installmentState(tx.isCleared, tx.date.toLocalDate(), today) == InstallmentState.OVERDUE
    val pending = installments.filter { !it.isCleared }
        .groupBy { it.loanId ?: -it.id }
        .map { (_, txs) ->
            val first = txs.minBy { it.date }
            LoanInstallmentsDue(
                loanId = first.loanId ?: 0L,
                loanName = first.loanId?.let { loanNames[it] } ?: first.description,
                type = first.type,
                count = txs.size,
                overdueCount = txs.count(::overdue),
                amount = txs.sumOf { it.amount.minor },
                firstDue = first.date.toLocalDate(),
            )
        }
        .sortedWith(compareByDescending<LoanInstallmentsDue> { it.overdueCount > 0 }.thenBy { it.firstDue })
    return InstallmentSummary(
        dueCount = installments.size,
        paidCount = paid.size,
        overdueCount = installments.count(::overdue),
        toPayDue = installments.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.minor },
        toPayOpen = installments.filter { it.type == TransactionType.EXPENSE && !it.isCleared }.sumOf { it.amount.minor },
        toCollectDue = installments.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.minor },
        toCollectOpen = installments.filter { it.type == TransactionType.INCOME && !it.isCleared }.sumOf { it.amount.minor },
        states = installments.sortedBy { it.date }.map { installmentState(it.isCleared, it.date.toLocalDate(), today) },
        pending = pending,
    )
}

/**
 * Picks how to split stacked sections of [heights] between two columns so they end as level as
 * possible. Order is kept inside each column and the first section always goes left. Returns a
 * bit mask: bit i-1 set means section i goes in the right column.
 */
fun bestColumnSplit(heights: List<Int>, gap: Int): Int {
    if (heights.size < 2) return 0
    fun imbalance(mask: Int): Int {
        var left = 0
        var right = 0
        heights.forEachIndexed { i, h ->
            val toRight = i > 0 && (mask shr (i - 1)) and 1 == 1
            if (toRight) right += (if (right > 0) gap else 0) + h else left += (if (left > 0) gap else 0) + h
        }
        return kotlin.math.abs(left - right)
    }
    return (0 until (1 shl (heights.size - 1))).minBy(::imbalance)
}
