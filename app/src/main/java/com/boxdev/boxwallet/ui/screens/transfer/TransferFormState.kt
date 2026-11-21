package com.boxdev.boxwallet.ui.screens.transfer

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.parseMajor
import com.boxdev.boxwallet.ui.screens.transaction.ManualConversion
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Currency

/** Why the transfer form can't save, shown above the fields. */
sealed interface TransferFormError {
    data object SameAccount : TransferFormError
    data object InvalidAmount : TransferFormError
    data object NeedsRate : TransferFormError
    data object AccountLoading : TransferFormError
    data object NotFound : TransferFormError
    /** No rate is known to convert [from] and nothing can be typed for it (the main-currency leg). */
    data class MissingRate(val from: Currency) : TransferFormError
    data object SaveFailed : TransferFormError
    data object DeleteFailed : TransferFormError
}

data class TransferFormState(
    /** The transfer being edited; null for a new one. */
    val transferId: String? = null,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    /** Typed in major units of the source account's currency. */
    val amount: String = "",
    val date: LocalDateTime = LocalDateTime.now(),
    val note: String = "",
    /** Source → destination rate typed by the user (or derived from [equivalent]), or null. */
    val manualRate: String? = null,
    /** The received amount in the destination currency, in major units; typing it derives [manualRate]. */
    val equivalent: String? = null,
    /** The rate currently applied source → destination, for display; null when none is known. */
    val rate: ExchangeRate? = null,
    /** No rate exists for these currencies and date: saving requires [manualRate] or [equivalent]. */
    val needsManualRate: Boolean = false,
    /** The active accounts, plus an archived one the edited transfer already uses. */
    val accounts: List<AccountWithBalance> = emptyList(),
    /** The edited transfer's stored source and destination: each stays pickable on its own side if archived since. */
    val keptFromId: Long? = null,
    val keptToId: Long? = null,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: TransferFormError? = null,
) {
    val fromAccount: Account? get() = accounts.firstOrNull { it.account.id == fromAccountId }?.account
    val toAccount: Account? get() = accounts.firstOrNull { it.account.id == toAccountId }?.account
    val fromCurrency: Currency? get() = fromAccount?.currency
    val toCurrency: Currency? get() = toAccount?.currency

    /** The sent amount; null until the source account is known or while the amount isn't positive. */
    val money: Money? get() = fromCurrency?.let { parseMajor(amount, it) }

    val isForeign: Boolean get() = fromCurrency != null && toCurrency != null && fromCurrency != toCurrency

    /** The typed rate when it is a positive number and the currencies differ. */
    val manualRateValue: BigDecimal?
        get() = manualRate?.takeIf { isForeign }?.trim()?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    val isSameAccount: Boolean get() = fromAccountId != null && fromAccountId == toAccountId

    /** Accounts the source can be: an archived one only where the edited transfer already had it. */
    val fromOptions: List<AccountWithBalance> get() = accounts.filter { it.account.allowedOn(keptFromId) }
    val toOptions: List<AccountWithBalance> get() = accounts.filter { it.account.allowedOn(keptToId) }

    /** Swapping must not move an archived account to the side it was never on. */
    val canSwap: Boolean
        get() = fromAccount?.allowedOn(keptToId) != false && toAccount?.allowedOn(keptFromId) != false

    private fun Account.allowedOn(kept: Long?): Boolean = !isArchived || id == kept

    val isValid: Boolean
        get() = fromAccount != null && toAccount != null && !isSameAccount && money != null &&
            (!needsManualRate || !isForeign || manualRateValue != null)

    /** Sets the manual rate and recomputes [equivalent] from it. */
    fun withManualRate(value: String): TransferFormState = copy(
        manualRate = value,
        equivalent = ManualConversion.equivalentAt(value, money, toCurrency) ?: equivalent,
        error = null,
    )

    /** Sets the received amount and derives the manual rate = received / sent. */
    fun withEquivalent(value: String): TransferFormState {
        val target = toCurrency ?: return copy(equivalent = value)
        return copy(equivalent = value, manualRate = ManualConversion.rateFor(value, money, target) ?: manualRate, error = null)
    }

    /** True when a user-editable field differs from [initial]; a previewed equivalent alone doesn't count. */
    fun differsFrom(initial: TransferFormState): Boolean =
        fromAccountId != initial.fromAccountId ||
            toAccountId != initial.toAccountId ||
            amount != initial.amount ||
            date != initial.date ||
            note != initial.note ||
            manualRate != initial.manualRate ||
            (manualRate != null && equivalent != initial.equivalent)
}
