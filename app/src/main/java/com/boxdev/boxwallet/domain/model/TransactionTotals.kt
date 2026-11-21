package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.local.entity.countsInReports
import java.time.YearMonth

/** Sum of [Transaction.amount] for [type], in minor units of the main currency. */
fun List<Transaction>.total(type: TransactionType): Long =
    sumOf { if (it.type == type) it.amount.minor else 0L }

/** The movements that count as income or expense; balance adjustments are left out. */
fun List<Transaction>.inReports(): List<Transaction> = filter { it.type.countsInReports }

/** Income minus expenses, in minor units of the main currency. Balance adjustments are ignored. */
fun List<Transaction>.net(): Long =
    inReports().sumOf { if (it.type == TransactionType.INCOME) it.amount.minor else -it.amount.minor }

/** Every month from [start] to [end], inclusive. */
fun monthsBetween(start: YearMonth, end: YearMonth): List<YearMonth> =
    generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(end) }.toList()
