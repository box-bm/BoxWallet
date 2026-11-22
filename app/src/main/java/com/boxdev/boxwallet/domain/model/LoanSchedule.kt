package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.domain.money.Money
import java.time.LocalDate

enum class InstallmentState { PAID, PARTIAL, PENDING, OVERDUE }

data class ScheduledInstallment(
    val number: Int,
    val due: LocalDate,
    val amount: Money,
    /** How much of [amount] the loan's payments so far have covered. */
    val covered: Money,
    val state: InstallmentState,
) {
    val uncovered: Money get() = Money(amount.minor - covered.minor, amount.currency)
}

/**
 * Spreads [paid] over the installments of [plan] in order: payments are free-form, so one can
 * cover several installments or only part of one. Installments are equal shares with the last
 * one absorbing the remainder; installment *n* falls due at `firstDue + (n - 1)` periods.
 */
fun allocate(total: Money, plan: LoanPlan, paid: Money, today: LocalDate): List<ScheduledInstallment> {
    if (plan.installments <= 0) return emptyList()
    val base = total.minor / plan.installments
    var left = paid.minor.coerceAtLeast(0)
    return (1..plan.installments).map { n ->
        val amount = if (n == plan.installments) total.minor - base * (plan.installments - 1) else base
        val covered = minOf(left, amount)
        left -= covered
        val due = plan.frequency.advance(plan.firstDue, plan.interval, (n - 1).toLong())
        val state = when {
            covered >= amount -> InstallmentState.PAID
            due.isBefore(today) -> InstallmentState.OVERDUE
            covered > 0 -> InstallmentState.PARTIAL
            else -> InstallmentState.PENDING
        }
        ScheduledInstallment(n, due, Money(amount, total.currency), Money(covered, total.currency), state)
    }
}
