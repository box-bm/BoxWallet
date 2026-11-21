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
 * With [isCard] on, [currentBalance] is the card's debt and is saved negated.
 */
data class AccountFormState(
    val name: String = "",
    val description: String = "",
    val currency: Currency = Currency.getInstance("USD"),
    val currentBalance: String = "",
    /** The "−/+" toggle beside the balance: flips the sign of what was typed. */
    val negativeBalance: Boolean = false,
    val icon: String = "AccountBalance",
    val color: AccountColor = AccountColor.DEFAULT,
    val includeInTotal: Boolean = true,
    /** The account has movements besides adjustments, so its currency can't change. */
    val currencyLocked: Boolean = false,
    val isEditing: Boolean = false,
    val isCard: Boolean = false,
    /** Major units of [currency]; empty means no limit. */
    val creditLimit: String = "",
    val closingDay: String = "",
    val dueDay: String = "",
    /** Days before the due date to remind; empty means no reminder. */
    val reminderDays: String = "3",
    /** The card's second currency, stored or just picked; null when it has none. */
    val secondCurrency: Currency? = null,
    /** Debt typed for a newly added [secondCurrency], in its major units. */
    val secondDebt: String = "",
    /** The stored card already has its second currency (as loaded), so [secondDebt] doesn't apply. */
    val hasSecondCurrency: Boolean = false,
    /** The stored second currency has nothing but adjustments, so it can be removed. */
    val secondCurrencyRemovable: Boolean = true,
) {
    @get:StringRes
    val nameError: Int?
        get() = when {
            name.isBlank() -> R.string.account_name_required
            name.trim().length > ACCOUNT_NAME_MAX -> R.string.account_name_too_long
            else -> null
        }

    /** The typed balance; null when empty (no opening balance), invalid or editing. A card's debt is negative. */
    val balance: Money?
        get() = if (isEditing || currentBalance.isBlank()) null
        else signedBalance(currentBalance, currency, negative = isCard || negativeBalance)

    @get:StringRes
    val balanceError: Int?
        get() = if (isEditing || currentBalance.isBlank() || balance != null) null else R.string.account_balance_invalid

    @get:StringRes
    val closingDayError: Int? get() = dayError(closingDay)

    @get:StringRes
    val dueDayError: Int? get() = dayError(dueDay)

    /** The limit in minor units of [currency]; null when empty or invalid. */
    val creditLimitMinor: Long?
        get() = creditLimit.takeIf { isCard && it.isNotBlank() }?.let { parseBalance(it, currency) }?.minor?.takeIf { it > 0 }

    @get:StringRes
    val limitError: Int?
        get() = if (!isCard || creditLimit.isBlank() || creditLimitMinor != null) null else R.string.account_error_limit

    val closingDayValue: Int? get() = closingDay.takeIf { isCard }?.trim()?.toIntOrNull()
    val dueDayValue: Int? get() = dueDay.takeIf { isCard }?.trim()?.toIntOrNull()
    val reminderDaysValue: Int? get() = reminderDays.takeIf { isCard }?.trim()?.toIntOrNull()

    /** A second currency picked in this form (not the stored one), whose debt becomes an opening adjustment. */
    private val addsSecondCurrency: Boolean get() = isCard && secondCurrency != null && !hasSecondCurrency

    /** The typed debt in the new second currency, negated; null when empty, invalid or not being added. */
    val secondBalance: Money?
        get() = secondCurrency?.takeIf { addsSecondCurrency && secondDebt.isNotBlank() }
            ?.let { signedBalance(secondDebt, it, negative = true) }

    @get:StringRes
    val secondDebtError: Int?
        get() = if (!addsSecondCurrency || secondDebt.isBlank() || secondBalance != null) null else R.string.account_balance_invalid

    val canSave: Boolean
        get() = nameError == null && balanceError == null && closingDayError == null && dueDayError == null &&
            limitError == null && secondDebtError == null

    /** Empty is fine (the day is optional); anything else must be a day of the month. */
    @StringRes
    private fun dayError(value: String): Int? =
        if (!isCard || value.isBlank() || value.trim().toIntOrNull() in 1..31) null else R.string.account_error_day
}

private val DAY_INPUT = Regex("""^\d{0,2}$""")

/** Whether [value] can stay in a day field while typing: up to two digits. */
fun isDayInput(value: String): Boolean = value.matches(DAY_INPUT)

private val BALANCE_INPUT = Regex("""^[\d,]*\.?\d*$""")

/**
 * Whether [value] can stay in a balance field while typing: digits, "," and one ".". No minus:
 * the "−/+" toggle sets the sign, so a typed "-" can't negate it twice.
 */
fun isBalanceInput(value: String): Boolean = value.matches(BALANCE_INPUT)

/**
 * Parses a balance the user typed: like [parseMajor] (decimals follow the currency, "," groups
 * thousands) but a balance may also be zero. Unsigned; the toggle applies the sign
 * ([signedBalance]). Null when it isn't a number.
 */
fun parseBalance(input: String, currency: Currency): Money? {
    val digits = input.trim()
    val zero = digits.replace(",", "").let { Regex("""\d+(\.\d+)?""").matches(it) && BigDecimal(it).signum() == 0 }
    if (zero) return Money(0, currency)
    return parseMajor(digits, currency)
}

/** [parseBalance] with the sign toggle applied: [negative] flips the sign of the parsed amount. */
fun signedBalance(input: String, currency: Currency, negative: Boolean): Money? =
    parseBalance(input, currency)?.let { if (negative) it.copy(minor = -it.minor) else it }
