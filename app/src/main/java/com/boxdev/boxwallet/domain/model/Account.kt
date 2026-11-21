package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.AccountEntity
import java.util.Currency

enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD, OTHER }

/** Persisted by [key]; unknown keys read back as [DEFAULT]. */
enum class AccountColor(val key: String) {
    DEFAULT("DEFAULT"),
    RED("RED"),
    ORANGE("ORANGE"),
    AMBER("AMBER"),
    LIME("LIME"),
    GREEN("GREEN"),
    TEAL("TEAL"),
    CYAN("CYAN"),
    BLUE("BLUE"),
    INDIGO("INDIGO"),
    PURPLE("PURPLE"),
    PINK("PINK");

    companion object {
        fun fromKey(key: String?): AccountColor = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    /** Epoch millis. */
    val createdAt: Long,
    val description: String = "",
    /** Material icon name. */
    val icon: String = "AccountBalance",
    val color: AccountColor = AccountColor.DEFAULT,
    val includeInTotal: Boolean = true,
    val parentAccountId: Long? = null,
    /** Credit cards only, in the account's currency. */
    val creditLimitMinor: Long? = null,
    /** Day of month (1–31), credit cards only. */
    val statementClosingDay: Int? = null,
    /** Day of month (1–31), credit cards only. */
    val paymentDueDay: Int? = null,
    val dueReminderDaysBefore: Int? = null,
)

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    currency = Currency.getInstance(currencyCode),
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    description = description,
    icon = icon,
    color = AccountColor.fromKey(color),
    includeInTotal = includeInTotal,
    parentAccountId = parentAccountId,
    creditLimitMinor = creditLimitMinor,
    statementClosingDay = statementClosingDay,
    paymentDueDay = paymentDueDay,
    dueReminderDaysBefore = dueReminderDaysBefore,
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    currencyCode = currency.currencyCode,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    description = description,
    icon = icon,
    color = color.key,
    includeInTotal = includeInTotal,
    parentAccountId = parentAccountId,
    creditLimitMinor = creditLimitMinor,
    statementClosingDay = statementClosingDay,
    paymentDueDay = paymentDueDay,
    dueReminderDaysBefore = dueReminderDaysBefore,
)
