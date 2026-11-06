package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionType
import java.time.YearMonth

fun List<Transaction>.total(type: TransactionType): Double =
    sumOf { if (it.type == type) it.amount else 0.0 }

/** Every month from [start] to [end], inclusive. */
fun monthsBetween(start: YearMonth, end: YearMonth): List<YearMonth> =
    generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(end) }.toList()
