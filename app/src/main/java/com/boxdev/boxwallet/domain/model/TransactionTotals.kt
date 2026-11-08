package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionType
import java.time.YearMonth

/** Sum of [Transaction.amount] for [type], in minor units of the main currency. */
fun List<Transaction>.total(type: TransactionType): Long =
    sumOf { if (it.type == type) it.amount.minor else 0L }

/** Income minus expenses, in minor units of the main currency. */
fun List<Transaction>.net(): Long =
    sumOf { if (it.type == TransactionType.INCOME) it.amount.minor else -it.amount.minor }

/** Every month from [start] to [end], inclusive. */
fun monthsBetween(start: YearMonth, end: YearMonth): List<YearMonth> =
    generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(end) }.toList()
