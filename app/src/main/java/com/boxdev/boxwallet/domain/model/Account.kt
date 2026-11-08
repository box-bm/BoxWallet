package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.AccountEntity
import java.util.Currency

enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD, OTHER }

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: Currency,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    /** Epoch millis. */
    val createdAt: Long,
)

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    currency = Currency.getInstance(currencyCode),
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAt = createdAt,
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type.name,
    currencyCode = currency.currencyCode,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAt = createdAt,
)
