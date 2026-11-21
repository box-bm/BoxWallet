package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.Money
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency

/**
 * A movement in three currencies: [original] as entered, [accountAmount] in the account's
 * currency (original × [accountRate]) and [amount] in the main currency (account × [mainRate]).
 * Totals always use [amount].
 */
data class Transaction(
    val id: Long = 0,
    val accountId: Long,
    val type: TransactionType,
    val date: LocalDateTime,
    val description: String = "",
    val categoryId: Long? = null,
    val loanId: Long? = null,
    val installmentNumber: Int? = null,
    val original: Money,
    val accountAmount: Money,
    val accountRate: ExchangeRate,
    val amount: Money,
    val mainRate: ExchangeRate,
    val isCleared: Boolean = false,
    val hasReminder: Boolean = false,
    val reminderSecondsBefore: Long? = null,
    val isRecurrent: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency? = null,
    val recurrenceInterval: Int? = null,
    val nextOccurrenceDate: LocalDateTime? = null,
    val parentRecurrenceId: Long? = null,
    val transferId: String? = null
) {
    companion object {
        /** A movement whose currency, account currency and main currency are all [money]'s (identity rates). */
        fun singleCurrency(
            accountId: Long,
            type: TransactionType,
            date: LocalDateTime,
            money: Money,
        ): Transaction = Transaction(
            accountId = accountId,
            type = type,
            date = date,
            original = money,
            accountAmount = money,
            accountRate = ExchangeRate.IDENTITY,
            amount = money,
            mainRate = ExchangeRate.IDENTITY,
        )
    }
}

fun TransactionEntity.toDomain(accountCurrency: Currency, main: Currency): Transaction = Transaction(
    id = id,
    accountId = accountId,
    type = TransactionType.valueOf(type),
    date = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneOffset.UTC),
    description = description,
    categoryId = categoryId,
    loanId = loanId,
    installmentNumber = installmentNumber,
    original = Money(originalAmountMinor, Currency.getInstance(currencyCode)),
    accountAmount = Money(accountAmountMinor, accountCurrency),
    accountRate = ExchangeRate(accountRate, accountRateSource),
    amount = Money(amountMinor, main),
    mainRate = ExchangeRate(mainRate, mainRateSource),
    isCleared = isCleared,
    hasReminder = hasReminder,
    reminderSecondsBefore = reminderSecondsBefore,
    isRecurrent = isRecurrent,
    recurrenceFrequency = recurrenceFrequency?.let { RecurrenceFrequency.valueOf(it) },
    recurrenceInterval = recurrenceInterval,
    nextOccurrenceDate = nextOccurrenceDate?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) },
    parentRecurrenceId = parentRecurrenceId,
    transferId = transferId
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
        accountId = accountId,
        type = type.name,
        date = dateMillis,
        description = description,
        categoryId = categoryId,
        loanId = loanId,
        installmentNumber = installmentNumber,
        originalAmountMinor = original.minor,
        currencyCode = original.currency.currencyCode,
        accountAmountMinor = accountAmount.minor,
        accountRate = accountRate.value,
        accountRateSource = accountRate.source,
        amountMinor = amount.minor,
        mainRate = mainRate.value,
        mainRateSource = mainRate.source,
        isCleared = isCleared,
        hasReminder = hasReminder,
        reminderDate = computedReminderDate,
        reminderSecondsBefore = reminderSecondsBefore,
        isRecurrent = isRecurrent,
        recurrenceFrequency = recurrenceFrequency?.name,
        recurrenceInterval = recurrenceInterval,
        nextOccurrenceDate = nextOccurrenceDate?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        parentRecurrenceId = parentRecurrenceId,
        transferId = transferId
    )
}
