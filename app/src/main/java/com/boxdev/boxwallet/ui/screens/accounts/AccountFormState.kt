package com.boxdev.boxwallet.ui.screens.accounts

import androidx.annotation.StringRes
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.parseMajor
import java.math.BigDecimal
import java.util.Currency

/** Longest account name the form accepts. */
const val ACCOUNT_NAME_MAX = 40

/**
 * What the account form edits. Only user-editable fields live here, so "unsaved changes" is a
 * plain comparison with the state as loaded. [currentBalance] is typed in major units of
 * [currency] and only applies when creating; an edited account is corrected with "Adjust balance".
 */
data class AccountFormState(
    val name: String = "",
    val description: String = "",
    val currency: Currency = Currency.getInstance("USD"),
    val currentBalance: String = "",
    val icon: String = "AccountBalance",
    val color: AccountColor = AccountColor.DEFAULT,
    val includeInTotal: Boolean = true,
    /** The account has movements besides adjustments, so its currency can't change. */
    val currencyLocked: Boolean = false,
    val isEditing: Boolean = false,
) {
    @get:StringRes
    val nameError: Int?
        get() = when {
            name.isBlank() -> R.string.account_name_required
            name.trim().length > ACCOUNT_NAME_MAX -> R.string.account_name_too_long
            else -> null
        }

    /** The typed balance; null when empty (no opening balance), invalid or editing. */
    val balance: Money?
        get() = if (isEditing || currentBalance.isBlank()) null else parseBalance(currentBalance, currency)

    @get:StringRes
    val balanceError: Int?
        get() = if (isEditing || currentBalance.isBlank() || balance != null) null else R.string.account_balance_invalid

    val canSave: Boolean get() = nameError == null && balanceError == null
}

/**
 * Parses a balance the user typed: like [parseMajor] (decimals follow the currency, "," groups
 * thousands) but a balance may also be zero or negative ("-50"). Null when it isn't a number.
 */
fun parseBalance(input: String, currency: Currency): Money? {
    val trimmed = input.trim()
    val negative = trimmed.startsWith("-")
    val digits = if (negative) trimmed.substring(1).trimStart() else trimmed
    val zero = digits.replace(",", "").let { Regex("""\d+(\.\d+)?""").matches(it) && BigDecimal(it).signum() == 0 }
    if (zero) return Money(0, currency)
    val money = parseMajor(digits, currency) ?: return null
    return if (negative) money.copy(minor = -money.minor) else money
}
