package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.domain.money.Money
import java.time.LocalDate
import java.time.LocalDateTime

/** The first closing date (day [closingDay], clamped per month) that is not before [date]. */
fun firstClosingOnOrAfter(closingDay: Int, date: LocalDate): LocalDate {
    val thisMonth = clampDay(date.year, date.monthValue, closingDay)
    if (!thisMonth.isBefore(date)) return thisMonth
    val next = date.withDayOfMonth(1).plusMonths(1)
    return clampDay(next.year, next.monthValue, closingDay)
}

/** [count] consecutive closing dates, the first being [firstClosingOnOrAfter] [from]. */
fun installmentClosingDates(closingDay: Int, from: LocalDate, count: Int): List<LocalDate> {
    val first = firstClosingOnOrAfter(closingDay, from)
    return List(count) { k ->
        val month = first.withDayOfMonth(1).plusMonths(k.toLong())
        clampDay(month.year, month.monthValue, closingDay)
    }
}

/** Splits [totalMinor] into [count] equal parts; the last one absorbs the remainder. */
fun splitEvenly(totalMinor: Long, count: Int): List<Long> {
    val base = totalMinor / count
    return List(count) { i -> if (i == count - 1) totalMinor - base * (count - 1) else base }
}

/** A credit-card purchase paid in [installments] parts, one per statement closing. */
data class InstallmentPlan(
    val id: Long,
    val accountId: Long,
    val description: String,
    val categoryId: Long?,
    val original: Money,
    val accountTotal: Money,
    val installments: Int,
    val purchaseDate: LocalDateTime,
    /** Installments dated today or earlier. */
    val charged: Int,
    /** Account-currency sum of the installments still to come. */
    val pendingMinor: Long,
)

/** Installment 1's account amount. */
val InstallmentPlan.perClosing: Money
    get() = Money(splitEvenly(accountTotal.minor, installments).first(), accountTotal.currency)

val InstallmentPlan.isFinished: Boolean
    get() = charged == installments
