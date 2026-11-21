package com.boxdev.boxwallet.ui.screens.accounts

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.CurrencyUsage
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.AccountRuleError
import com.boxdev.boxwallet.data.repository.SecondCurrencyChange
import com.boxdev.boxwallet.data.repository.SecondCurrencyInput
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Currency
import javax.inject.Inject

/** Loading, saving and error state around the form; kept apart so [AccountFormState] stays the fields alone. */
data class AccountFormStatus(
    val isLoading: Boolean = true,
    /** Set after a save was attempted, so field errors only show once the user tried. */
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    @param:StringRes val error: Int? = null,
)

@HiltViewModel
class AccountFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferences,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    private val accountId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it != -1L }

    /** The stored account when editing, so fields the form doesn't show (type, card fields) survive the save. */
    private var editing: Account? = null

    /** The stored card's second currency when editing, so the save knows whether it was added or removed. */
    private var storedSecond: Currency? = null

    private val _form = MutableStateFlow(AccountFormState(isEditing = accountId != null))
    val form: StateFlow<AccountFormState> = _form.asStateFlow()

    private val _status = MutableStateFlow(AccountFormStatus())
    val status: StateFlow<AccountFormStatus> = _status.asStateFlow()

    /** The form as loaded; null while loading so no edit can be detected yet. */
    private val initial = MutableStateFlow<AccountFormState?>(null)

    val hasUnsavedChanges: StateFlow<Boolean> = combine(_form, initial) { form, base -> base != null && form != base }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val recentCurrencies: StateFlow<List<CurrencyUsage>> = transactionRepository.recentCurrencies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val loaded = if (accountId == null) {
            AccountFormState(currency = userPreferences.mainCurrency.first())
        } else {
            val account = accountRepository.observeAll().first().firstOrNull { it.id == accountId }
            if (account == null) {
                _status.update { it.copy(isLoading = false, error = R.string.account_not_found) }
                return
            }
            editing = account
            val isCard = account.type == AccountType.CREDIT_CARD
            val child = accountRepository.secondCurrencyOf(account.id).first()
            storedSecond = child?.currency
            AccountFormState(
                name = account.name,
                description = account.description,
                currency = account.currency,
                icon = account.icon,
                color = account.color,
                includeInTotal = account.includeInTotal,
                currencyLocked = accountRepository.hasNonAdjustmentMovements(account.id),
                isEditing = true,
                isCard = isCard,
                creditLimit = account.creditLimitMinor?.let { Money(it, account.currency).toMajor().toPlainString() }.orEmpty(),
                closingDay = account.statementClosingDay?.toString().orEmpty(),
                dueDay = account.paymentDueDay?.toString().orEmpty(),
                // A card keeps its choice (empty is "no reminder"); anything else starts at the default.
                reminderDays = if (isCard) account.dueReminderDaysBefore?.toString().orEmpty() else AccountFormState().reminderDays,
                secondCurrency = child?.currency,
                hasSecondCurrency = child != null,
                secondCurrencyRemovable = child == null || !accountRepository.hasNonAdjustmentMovements(child.id),
            )
        }
        _form.value = loaded
        initial.value = loaded
        _status.update { it.copy(isLoading = false) }
    }

    fun updateName(value: String) = _form.update { it.copy(name = value) }
    fun updateDescription(value: String) = _form.update { it.copy(description = value) }
    fun updateIcon(key: String) = _form.update { it.copy(icon = key) }
    fun updateColor(color: AccountColor) = _form.update { it.copy(color = color) }
    fun updateIncludeInTotal(value: Boolean) = _form.update { it.copy(includeInTotal = value) }
    fun updateNegativeBalance(value: Boolean) = _form.update { it.copy(negativeBalance = value) }

    fun updateCurrency(currency: Currency) {
        val form = _form.value
        if (form.currencyLocked || currency == form.secondCurrency) return
        _form.update { it.copy(currency = currency) }
        _status.update { it.copy(error = null) }
    }

    /** A card with a second currency stays a card until that currency is removed. */
    fun updateIsCard(value: Boolean) {
        if (!value && _form.value.secondCurrency != null) {
            _status.update { it.copy(error = R.string.account_error_remove_second_currency_first) }
            return
        }
        _form.update { it.copy(isCard = value) }
        _status.update { it.copy(error = null) }
    }

    fun updateCreditLimit(value: String) {
        if (isBalanceInput(value)) _form.update { it.copy(creditLimit = value) }
    }

    fun updateClosingDay(value: String) {
        if (isDayInput(value)) _form.update { it.copy(closingDay = value) }
    }

    fun updateDueDay(value: String) {
        if (isDayInput(value)) _form.update { it.copy(dueDay = value) }
    }

    fun updateReminderDays(value: String) {
        if (isDayInput(value)) _form.update { it.copy(reminderDays = value) }
    }

    /**
     * Picks the card's second currency; never the card's own. A stored one can't be swapped for another
     * here: it's removed (and saved) first.
     */
    fun updateSecondCurrency(currency: Currency) {
        val form = _form.value
        if (currency == form.currency || form.hasSecondCurrency) return
        _form.update { it.copy(secondCurrency = currency) }
        _status.update { it.copy(error = null) }
    }

    /** Drops the second currency; a stored one only while it has nothing but adjustments. */
    fun removeSecondCurrency() {
        if (!_form.value.secondCurrencyRemovable) return
        _form.update { it.copy(secondCurrency = null, secondDebt = "") }
        _status.update { it.copy(error = null) }
    }

    fun updateSecondDebt(value: String) {
        if (isBalanceInput(value)) _form.update { it.copy(secondDebt = value) }
    }

    /** Keeps only what can become a balance ([isBalanceInput]); the sign comes from the toggle. */
    fun updateBalance(value: String) {
        if (isBalanceInput(value)) {
            _form.update { it.copy(currentBalance = value) }
        }
    }

    fun save() {
        val s = _form.value
        val status = _status.value
        if (status.isLoading || status.isSaving) return
        if (!s.canSave) {
            _status.update { it.copy(showErrors = true) }
            return
        }
        _status.update { it.copy(showErrors = true, isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val stored = editing
                if (stored != null) {
                    // A card switched off becomes a checking account; any other type is kept.
                    val type = when {
                        s.isCard -> AccountType.CREDIT_CARD
                        stored.type == AccountType.CREDIT_CARD -> AccountType.CHECKING
                        else -> stored.type
                    }
                    accountRepository.update(
                        stored.copy(
                            name = s.name.trim(),
                            description = s.description.trim(),
                            currency = s.currency,
                            icon = s.icon,
                            color = s.color,
                            includeInTotal = s.includeInTotal,
                        ).withCardFields(s, type),
                        secondCurrencyChange(s),
                    )
                } else {
                    val account = Account(
                        name = s.name.trim(),
                        type = AccountType.CHECKING,
                        currency = s.currency,
                        createdAt = System.currentTimeMillis(),
                        description = s.description.trim(),
                        icon = s.icon,
                        color = s.color,
                        includeInTotal = s.includeInTotal,
                    ).withCardFields(s, if (s.isCard) AccountType.CREDIT_CARD else AccountType.CHECKING)
                    val second = s.secondCurrency?.takeIf { s.isCard }?.let { SecondCurrencyInput(it, s.secondBalance) }
                    accountRepository.create(account, s.balance, second)
                }
                _status.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: AccountRuleError) {
                _status.update { it.copy(isSaving = false, error = messageFor(e)) }
            } catch (_: MissingRateException) {
                _status.update { it.copy(isSaving = false, error = R.string.account_error_missing_rate) }
            } catch (_: IllegalStateException) {
                // update() refuses a missing account or a currency change once there are movements.
                val stored = editing
                val locked = stored != null && s.currency != stored.currency &&
                    accountRepository.hasNonAdjustmentMovements(stored.id)
                if (locked) {
                    // The account gained movements while the form was open: its currency is now locked.
                    _form.update { it.copy(currency = stored.currency, currencyLocked = true) }
                    _status.update { it.copy(isSaving = false, error = R.string.account_currency_locked) }
                } else {
                    _status.update { it.copy(isSaving = false, error = R.string.account_not_found) }
                }
            }
        }
    }

    /** What the save does with the card's second currency, from the stored one to the form's. */
    private fun secondCurrencyChange(s: AccountFormState): SecondCurrencyChange {
        val wanted = s.secondCurrency?.takeIf { s.isCard }
        return when {
            storedSecond == null && wanted != null -> SecondCurrencyChange.Add(SecondCurrencyInput(wanted, s.secondBalance))
            storedSecond != null && wanted == null -> SecondCurrencyChange.Remove
            else -> SecondCurrencyChange.Keep
        }
    }

    @StringRes
    private fun messageFor(error: AccountRuleError): Int = when (error) {
        AccountRuleError.IsDefault -> R.string.account_error_is_default
        AccountRuleError.LastAccount -> R.string.account_error_last
        AccountRuleError.HasMovements -> R.string.account_error_has_movements
        AccountRuleError.SecondCurrencyHasMovements -> R.string.account_error_second_currency_has_movements
        AccountRuleError.RemoveSecondCurrencyFirst -> R.string.account_error_remove_second_currency_first
        AccountRuleError.HasPendingInstallments -> R.string.account_error_has_pending_installments
    }
}

/** [type] with the form's card fields; any other type clears them. */
private fun Account.withCardFields(s: AccountFormState, type: AccountType): Account {
    val card = type == AccountType.CREDIT_CARD
    return copy(
        type = type,
        creditLimitMinor = s.creditLimitMinor.takeIf { card },
        statementClosingDay = s.closingDayValue.takeIf { card },
        paymentDueDay = s.dueDayValue.takeIf { card },
        dueReminderDaysBefore = s.reminderDaysValue.takeIf { card },
    )
}
