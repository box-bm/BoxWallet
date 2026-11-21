package com.boxdev.boxwallet.ui.screens.accounts

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.CurrencyUsage
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.money.MissingRateException
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
            AccountFormState(
                name = account.name,
                description = account.description,
                currency = account.currency,
                icon = account.icon,
                color = account.color,
                includeInTotal = account.includeInTotal,
                currencyLocked = accountRepository.hasNonAdjustmentMovements(account.id),
                isEditing = true,
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

    fun updateCurrency(currency: Currency) {
        if (_form.value.currencyLocked) return
        _form.update { it.copy(currency = currency) }
        _status.update { it.copy(error = null) }
    }

    /** Keeps only what can become a balance: an optional leading minus, digits, "," and one ".". */
    fun updateBalance(value: String) {
        if (value.isEmpty() || value.matches(Regex("""^-?[\d,]*\.?\d*$"""))) {
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
                    accountRepository.update(
                        stored.copy(
                            name = s.name.trim(),
                            description = s.description.trim(),
                            currency = s.currency,
                            icon = s.icon,
                            color = s.color,
                            includeInTotal = s.includeInTotal,
                        )
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
                    )
                    accountRepository.create(account, s.balance)
                }
                _status.update { it.copy(isSaving = false, isSaved = true) }
            } catch (_: MissingRateException) {
                _status.update { it.copy(isSaving = false, error = R.string.account_error_missing_rate) }
            } catch (_: IllegalStateException) {
                // The account gained movements while the form was open: its currency is now locked.
                _form.update { it.copy(currency = editing?.currency ?: it.currency, currencyLocked = true) }
                _status.update { it.copy(isSaving = false, error = R.string.account_currency_locked) }
            }
        }
    }
}
