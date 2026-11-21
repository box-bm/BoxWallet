package com.boxdev.boxwallet.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** One card movement, in the card's currency: [signedMinor] is positive for an inflow, negative for an outflow. */
data class CardMovement(val date: LocalDate, val signedMinor: Long, val isInflow: Boolean)

/** Where a card stands in its current statement cycle; all amounts are in the card's currency. */
data class StatementStatus(
    val lastClosing: LocalDate,
    val dueDate: LocalDate,
    /** Debt at the end of the last closing day. */
    val statementDebtMinor: Long,
    /** Inflows after the last closing, up to today. */
    val paidSinceMinor: Long,
    val toPayMinor: Long,
    val noStatementYet: Boolean,
)

/** [day] in [month], clamped into 1..the month's last day (a malformed backup may hold 0 or less). */
fun clampDay(year: Int, month: Int, day: Int): LocalDate {
    val ym = YearMonth.of(year, month)
    return ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))
}

/** The latest closing date (day [closingDay], clamped) that is not after [today]. */
fun lastClosingDate(closingDay: Int, today: LocalDate): LocalDate {
    val thisMonth = clampDay(today.year, today.monthValue, closingDay)
    if (!thisMonth.isAfter(today)) return thisMonth
    val previous = today.withDayOfMonth(1).minusMonths(1)
    return clampDay(previous.year, previous.monthValue, closingDay)
}

/** The first date with day [dueDay] (clamped) strictly after [lastClosing]. */
fun dueDateAfter(lastClosing: LocalDate, dueDay: Int): LocalDate {
    val sameMonth = clampDay(lastClosing.year, lastClosing.monthValue, dueDay)
    if (sameMonth.isAfter(lastClosing)) return sameMonth
    val next = lastClosing.withDayOfMonth(1).plusMonths(1)
    return clampDay(next.year, next.monthValue, dueDay)
}

fun statementStatus(movements: List<CardMovement>, closingDay: Int, dueDay: Int, today: LocalDate): StatementStatus {
    val closing = lastClosingDate(closingDay, today)
    val upToClosing = movements.filter { !it.date.isAfter(closing) }
    val statementDebt = -upToClosing.sumOf { it.signedMinor }
    val paid = movements
        .filter { it.isInflow && it.date.isAfter(closing) && !it.date.isAfter(today) }
        .sumOf { it.signedMinor }
    return StatementStatus(
        lastClosing = closing,
        dueDate = dueDateAfter(closing, dueDay),
        statementDebtMinor = statementDebt,
        paidSinceMinor = paid,
        toPayMinor = (statementDebt - paid).coerceAtLeast(0L),
        noStatementYet = statementDebt == 0L && upToClosing.isEmpty(),
    )
}
