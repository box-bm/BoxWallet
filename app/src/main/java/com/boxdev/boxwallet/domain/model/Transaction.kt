package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import java.time.LocalDate
import java.time.ZoneId

data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val currency: String,
    val description: String = "",
    val date: LocalDate,
    val type: TransactionType,
    val isCleared: Boolean = false,
    val hasReminder: Boolean = false,
    val reminderDate: LocalDate? = null,
    val categoryId: Long? = null,
    val loanId: Long? = null,
    val installmentNumber: Int? = null,
    val isRecurrent: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency? = null,
    val recurrenceInterval: Int? = null,
    val nextOccurrenceDate: LocalDate? = null,
    val parentRecurrenceId: Long? = null
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = amount,
    currency = currency,
    description = description,
    date = LocalDate.ofEpochDay(date / (24 * 60 * 60 * 1000)),
    type = TransactionType.valueOf(type),
    isCleared = isCleared,
    hasReminder = hasReminder,
    reminderDate = reminderDate?.let { LocalDate.ofEpochDay(it / (24 * 60 * 60 * 1000)) },
    categoryId = categoryId,
    loanId = loanId,
    installmentNumber = installmentNumber,
    isRecurrent = isRecurrent,
    recurrenceFrequency = recurrenceFrequency?.let { RecurrenceFrequency.valueOf(it) },
    recurrenceInterval = recurrenceInterval,
    nextOccurrenceDate = nextOccurrenceDate?.let { LocalDate.ofEpochDay(it / (24 * 60 * 60 * 1000)) },
    parentRecurrenceId = parentRecurrenceId
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amount,
    currency = currency,
    description = description,
    date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    type = type.name,
    isCleared = isCleared,
    hasReminder = hasReminder,
    reminderDate = reminderDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
    categoryId = categoryId,
    loanId = loanId,
    installmentNumber = installmentNumber,
    isRecurrent = isRecurrent,
    recurrenceFrequency = recurrenceFrequency?.name,
    recurrenceInterval = recurrenceInterval,
    nextOccurrenceDate = nextOccurrenceDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
    parentRecurrenceId = parentRecurrenceId
)
