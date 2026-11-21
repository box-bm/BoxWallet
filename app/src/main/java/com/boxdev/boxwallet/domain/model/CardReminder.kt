package com.boxdev.boxwallet.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** App-meta key holding the closing date of the last statement [cardId] was reminded about. */
fun cardReminderKey(cardId: Long): String = "card_reminder_$cardId"

/**
 * True when [summary]'s card should notify today: it has reminder days, isn't archived, its current statement
 * leaves something to pay in some currency, the due date is 0..N days away, and that statement (identified by
 * its closing date) wasn't already notified ([lastNotifiedClosing]).
 */
fun cardReminderDue(summary: CardSummary, today: LocalDate, lastNotifiedClosing: LocalDate?): Boolean {
    val days = summary.card.dueReminderDaysBefore ?: return false
    if (summary.card.isArchived) return false
    val statement = summary.primary.statement?.takeIf { !it.noStatementYet } ?: return false
    val anythingToPay = statement.toPayMinor > 0 ||
        (summary.second?.statement?.takeIf { !it.noStatementYet }?.toPayMinor ?: 0L) > 0
    if (!anythingToPay) return false
    val daysLeft = ChronoUnit.DAYS.between(today, statement.dueDate)
    return daysLeft in 0..days && lastNotifiedClosing != statement.lastClosing
}
