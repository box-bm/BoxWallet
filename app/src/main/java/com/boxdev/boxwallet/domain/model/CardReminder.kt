package com.boxdev.boxwallet.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** App-meta key holding the closing date of the last statement [cardId] was reminded about. */
fun cardReminderKey(cardId: Long): String = "card_reminder_$cardId"

/**
 * The statement [summary]'s card should notify about today, or null: the card has reminder days and isn't
 * archived, some currency has a statement that leaves something to pay (every currency shares the closing and
 * due dates), the due date is 0..N days away, and that statement (identified by its closing date) wasn't
 * already notified ([lastNotifiedClosing]).
 */
fun cardReminderStatement(summary: CardSummary, today: LocalDate, lastNotifiedClosing: LocalDate?): StatementStatus? {
    val days = summary.card.dueReminderDaysBefore ?: return null
    if (summary.card.isArchived) return null
    val statement = listOfNotNull(summary.primary, summary.second)
        .mapNotNull { it.statement }
        .firstOrNull { !it.noStatementYet && it.toPayMinor > 0 }
        ?: return null
    val daysLeft = ChronoUnit.DAYS.between(today, statement.dueDate)
    return statement.takeIf { daysLeft in 0..days && lastNotifiedClosing != it.lastClosing }
}

/** True when [cardReminderStatement] finds a statement to notify about. */
fun cardReminderDue(summary: CardSummary, today: LocalDate, lastNotifiedClosing: LocalDate?): Boolean =
    cardReminderStatement(summary, today, lastNotifiedClosing) != null
