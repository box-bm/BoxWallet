package com.boxdev.boxwallet.ui.screens.analytics

import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.outstandingTotals
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

/**
 * What loans did in a period, in minor units of the main currency. [received] and [paid] belong to
 * loans the user borrowed (disbursement and payments); [lent] and [collected] to loans the user
 * lent. [owe] and [owed] are what is outstanding now, not bound to the period.
 */
data class LoanFlows(
    val received: Long,
    val paid: Long,
    val lent: Long,
    val collected: Long,
    val owe: Long,
    val owed: Long,
)

/** Splits the period's loan [movements] by loan direction and role; movements of unknown loans are ignored. */
fun loanFlows(movements: List<Transaction>, loans: Map<Long, Loan>): LoanFlows {
    var received = 0L
    var paid = 0L
    var lent = 0L
    var collected = 0L
    for (tx in movements) {
        val loan = tx.loanId?.let(loans::get) ?: continue
        val amount = tx.amount.minor
        val borrowed = loan.loanType == LoanType.EXPENSE
        when (tx.type) {
            loan.disbursementType -> if (borrowed) received += amount else lent += amount
            loan.paymentType -> if (borrowed) paid += amount else collected += amount
            else -> Unit
        }
    }
    val outstanding = loans.values.toList().outstandingTotals()
    return LoanFlows(received, paid, lent, collected, owe = outstanding.toPay, owed = outstanding.toCollect)
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
