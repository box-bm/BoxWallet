package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val description: String = "",
    val date: LocalDateTime,
    val type: TransactionType,
    val isCleared: Boolean = false,
    val hasReminder: Boolean = false,
    val reminderSecondsBefore: Long? = null,
    val categoryId: Long? = null,
    val loanId: Long? = null,
    val installmentNumber: Int? = null,
    val isRecurrent: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency? = null,
    val recurrenceInterval: Int? = null,
    val nextOccurrenceDate: LocalDateTime? = null,
    val parentRecurrenceId: Long? = null
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = amount,
    description = description,
    date = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneOffset.UTC),
    type = TransactionType.valueOf(type),
    isCleared = isCleared,
    hasReminder = hasReminder,
    reminderSecondsBefore = reminderSecondsBefore,
    categoryId = categoryId,
    loanId = loanId,
    installmentNumber = installmentNumber,
    isRecurrent = isRecurrent,
    recurrenceFrequency = recurrenceFrequency?.let { RecurrenceFrequency.valueOf(it) },
    recurrenceInterval = recurrenceInterval,
    nextOccurrenceDate = nextOccurrenceDate?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) },
    parentRecurrenceId = parentRecurrenceId
)

fun Transaction.toEntity(): TransactionEntity {
    val dateMillis = date.toInstant(ZoneOffset.UTC).toEpochMilli()
    // reminderDate must use actual wall-clock millis (system timezone) since it's
    // compared against System.currentTimeMillis() in the periodic worker query
    val computedReminderDate = if (hasReminder && reminderSecondsBefore != null) {
        val actualDateMillis = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        actualDateMillis - (reminderSecondsBefore * 1000)
    } else null

    return TransactionEntity(
        id = id,
        amount = amount,
        description = description,
        date = dateMillis,
        type = type.name,
        isCleared = isCleared,
        hasReminder = hasReminder,
        reminderDate = computedReminderDate,
        reminderSecondsBefore = reminderSecondsBefore,
        categoryId = categoryId,
        loanId = loanId,
        installmentNumber = installmentNumber,
        isRecurrent = isRecurrent,
        recurrenceFrequency = recurrenceFrequency?.name,
        recurrenceInterval = recurrenceInterval,
        nextOccurrenceDate = nextOccurrenceDate?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        parentRecurrenceId = parentRecurrenceId
    )
}
