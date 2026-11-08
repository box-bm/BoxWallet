package com.boxdev.boxwallet.ui.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.CurrencyUsage
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.ReminderDuration
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.domain.money.parseMajor
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDateTime
import java.util.Currency
import javax.inject.Inject

/** Why the form can't save, shown under the fields so a failed save never leaves the sheet silently open. */
sealed interface TransactionFormError {
    data object InvalidAmount : TransactionFormError
    data object NeedsRate : TransactionFormError
    data object AccountLoading : TransactionFormError
    data object NotFound : TransactionFormError
    /** No rate is known to convert [from] (e.g. the account → main leg) and nothing can be typed for it. */
    data class MissingRate(val from: Currency) : TransactionFormError
    data object SaveFailed : TransactionFormError
    data object DeleteFailed : TransactionFormError
}

data class TransactionFormState(
    val id: Long = 0,
    /** Typed in major units of [currency]. */
    val amount: String = "",
    /** The movement's currency; starts as the account's once [accountCurrency] is known. */
    val currency: Currency = Currency.getInstance("USD"),
    /** Currency of the account the movement goes to; null until loaded, and saving waits for it. */
    val accountCurrency: Currency? = null,
    /** Original → account rate typed by the user (overrides the automatic one), or null. */
    val manualRate: String? = null,
    /** The amount in [accountCurrency], in major units; typing it derives [manualRate]. */
    val equivalent: String? = null,
    /** The rate currently applied original → account, for display; null when none is known. */
    val rate: ExchangeRate? = null,
    /** No rate exists for this currency and date: saving requires [manualRate] or [equivalent]. */
    val needsManualRate: Boolean = false,
    val description: String = "",
    val categoryId: Long? = null,
    val date: LocalDateTime = LocalDateTime.now(),
    val type: TransactionType = TransactionType.EXPENSE,
    val isCleared: Boolean = false,
    val hasReminder: Boolean = false,
    val reminderDuration: ReminderDuration? = null,
    val isRecurrent: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency? = null,
    val recurrenceInterval: String = "1",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: TransactionFormError? = null,
    val wasCleared: Boolean = false
) {
    val money: Money? get() = parseMajor(amount, currency)

    val isForeign: Boolean get() = accountCurrency != null && currency != accountCurrency

    /** The typed rate when it is a positive number and the movement is foreign to its account. */
    val manualRateValue: BigDecimal?
        get() = manualRate?.takeIf { isForeign }?.trim()?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    val isValid: Boolean
        get() = money != null &&
                (!isRecurrent || recurrenceFrequency != null) &&
                (!isRecurrent || recurrenceFrequency != RecurrenceFrequency.CUSTOM_MONTHS || (recurrenceInterval.toIntOrNull() ?: 0) > 0)

    /** Valid, the account currency is known, and a missing automatic rate has been replaced by a manual one. */
    val canSave: Boolean
        get() = isValid && accountCurrency != null && (!needsManualRate || manualRateValue != null)

    /** Sets the manual rate and recomputes [equivalent] from it. */
    fun withManualRate(value: String): TransactionFormState {
        val rate = value.trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val target = accountCurrency
        val original = money
        val equivalent = if (rate != null && target != null && original != null) {
            original.convert(target, rate).toMajor().toPlainString()
        } else equivalent
        return copy(manualRate = value, equivalent = equivalent, error = null)
    }

    /** Sets the equivalent in the account currency and derives the manual rate = equivalent / original. */
    fun withEquivalent(value: String): TransactionFormState {
        val target = accountCurrency ?: return copy(equivalent = value)
        val converted = parseMajor(value, target)
        val original = money
        val rate = if (converted != null && original != null) {
            converted.toMajor().divide(original.toMajor(), MathContext.DECIMAL64).toPlainString()
        } else manualRate
        return copy(equivalent = value, manualRate = rate, error = null)
    }
}

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferences,
    private val transactionAmounts: TransactionAmounts,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    /** The movement being edited, so fields the form doesn't show (account, recurrence parent) survive the save. */
    private var editing: Transaction? = null

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentCurrencies: StateFlow<List<CurrencyUsage>> = transactionRepository.recentCurrencies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadTransaction(transactionId: Long?) {
        editing = null
        if (transactionId == null || transactionId == -1L) {
            // Loading until the account is known, so a quick save can't store a placeholder currency.
            _formState.value = TransactionFormState(isLoading = true)
            viewModelScope.launch {
                val account = accountRepository.defaultAccount()
                _formState.update {
                    it.copy(currency = account.currency, accountCurrency = account.currency, isLoading = false)
                }
            }
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            val t = transactionRepository.getTransactionById(transactionId)
            editing = t
            if (t != null) {
                _formState.value = TransactionFormState(
                    id = t.id,
                    amount = t.original.toMajor().toPlainString(),
                    currency = t.original.currency,
                    accountCurrency = t.accountAmount.currency,
                    manualRate = t.accountRate.takeIf { it.source == RateSource.MANUAL }?.value?.toPlainString(),
                    equivalent = t.accountAmount.toMajor().toPlainString()
                        .takeIf { t.original.currency != t.accountAmount.currency },
                    rate = t.accountRate,
                    description = t.description,
                    categoryId = t.categoryId,
                    date = t.date,
                    type = t.type,
                    isCleared = t.isCleared,
                    hasReminder = t.hasReminder,
                    reminderDuration = ReminderDuration.fromSeconds(t.reminderSecondsBefore),
                    isRecurrent = t.isRecurrent,
                    recurrenceFrequency = t.recurrenceFrequency,
                    recurrenceInterval = t.recurrenceInterval?.toString() ?: "1",
                    isEditing = true,
                    isLoading = false,
                    wasCleared = t.isCleared,
                )
            } else {
                _formState.update { it.copy(isLoading = false, error = TransactionFormError.NotFound) }
            }
        }
    }

    fun updateAmount(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update { s ->
                val updated = s.copy(amount = value, error = null)
                updated.manualRate?.let { updated.withManualRate(it) } ?: updated
            }
            refreshRate()
        }
    }

    /** Changes the movement's currency; any typed rate belonged to the old one and is dropped. */
    fun updateCurrency(value: Currency) {
        _formState.update { it.copy(currency = value, manualRate = null, equivalent = null, error = null) }
        refreshRate()
    }

    fun updateManualRate(value: String) = _formState.update { it.withManualRate(value) }

    fun updateEquivalent(value: String) = _formState.update { it.withEquivalent(value) }

    /** Drops the typed rate and goes back to the rate of the movement's date. */
    fun useAutomaticRate() {
        _formState.update { it.copy(manualRate = null, equivalent = null) }
        refreshRate()
    }

    private var rateJob: Job? = null

    /** Rates resolved per currency pair and day, so typing an amount doesn't resolve (or download) again. */
    private val rateCache = FormRateCache(transactionAmounts, viewModelScope)

    /**
     * Previews the automatic rate for the current currency and date (unless the user typed one).
     * The rate comes from [rateCache]; only a new currency or date triggers a resolve, and the
     * equivalent is always recomputed locally from the latest typed amount.
     */
    private fun refreshRate() {
        rateJob?.cancel()
        val s = _formState.value
        val accountCurrency = s.accountCurrency ?: return
        if (!s.isForeign) {
            _formState.update { it.copy(rate = ExchangeRate.IDENTITY, needsManualRate = false, equivalent = null) }
            return
        }
        if (s.manualRate != null) return
        val day = s.date.toLocalDate()
        rateJob = viewModelScope.launch {
            val main = userPreferences.mainCurrency.first()
            val result = rateCache.rates(FormRateCache.Key(s.currency, accountCurrency, main, day))
            _formState.update {
                val stale = it.currency != s.currency || it.accountCurrency != accountCurrency || it.date.toLocalDate() != day
                if (it.manualRate != null || stale) return@update it
                when (result) {
                    is AmountResult.Ready -> it.copy(
                        rate = result.accountRate,
                        needsManualRate = false,
                        equivalent = FormRateCache.equivalent(it.amount, it.currency, accountCurrency, result),
                    )
                    AmountResult.NeedsManualRate -> it.copy(rate = null, needsManualRate = true, equivalent = null)
                }
            }
        }
    }

    fun updateDescription(value: String) = _formState.update { it.copy(description = value) }
    fun updateCategory(id: Long?) = _formState.update { it.copy(categoryId = id) }
    fun updateDate(value: LocalDateTime) {
        _formState.update { it.copy(date = value) }
        refreshRate()
    }
    fun updateTime(hour: Int, minute: Int) = _formState.update { it.copy(date = it.date.withHour(hour).withMinute(minute)) }
    fun updateType(value: TransactionType) = _formState.update { it.copy(type = value) }
    fun updateIsCleared(value: Boolean) = _formState.update { it.copy(isCleared = value) }
    fun updateHasReminder(value: Boolean) = _formState.update {
        it.copy(
            hasReminder = value,
            reminderDuration = if (value) ReminderDuration.FIFTEEN_MINUTES else null
        )
    }
    fun updateReminderDuration(duration: ReminderDuration?) = _formState.update { it.copy(reminderDuration = duration) }
    fun updateIsRecurrent(value: Boolean) = _formState.update {
        it.copy(
            isRecurrent = value,
            recurrenceFrequency = if (value && it.recurrenceFrequency == null) RecurrenceFrequency.MONTHLY else it.recurrenceFrequency
        )
    }
    fun updateRecurrenceFrequency(freq: RecurrenceFrequency) = _formState.update { it.copy(recurrenceFrequency = freq) }
    fun updateRecurrenceInterval(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
            _formState.update { it.copy(recurrenceInterval = value, error = null) }
        }
    }

    fun save() {
        val s = _formState.value
        if (!s.isValid) {
            _formState.update { it.copy(error = TransactionFormError.InvalidAmount) }
            return
        }
        if (!s.canSave) {
            _formState.update {
                it.copy(
                    error = if (s.needsManualRate) TransactionFormError.NeedsRate else TransactionFormError.AccountLoading
                )
            }
            return
        }
        val accountCurrency = s.accountCurrency!!
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            try {
                val nextOccurrenceDate = if (s.isRecurrent && s.recurrenceFrequency != null) {
                    s.recurrenceFrequency.advance(s.date, s.recurrenceInterval.toIntOrNull() ?: 1)
                } else null

                val previous = editing
                val accountId = previous?.accountId ?: accountRepository.defaultAccount().id
                val draft = Transaction.singleCurrency(accountId, s.type, s.date, s.money!!).copy(
                    id = if (s.isEditing) s.id else 0,
                    description = s.description,
                    loanId = previous?.loanId,
                    installmentNumber = previous?.installmentNumber,
                    parentRecurrenceId = previous?.parentRecurrenceId,
                    isCleared = s.isCleared,
                    hasReminder = s.hasReminder,
                    reminderSecondsBefore = s.reminderDuration?.secondsBefore,
                    categoryId = s.categoryId,
                    isRecurrent = s.isRecurrent,
                    recurrenceFrequency = s.recurrenceFrequency,
                    recurrenceInterval = s.recurrenceInterval.toIntOrNull(),
                    nextOccurrenceDate = nextOccurrenceDate
                )
                val main = userPreferences.mainCurrency.first()
                val transaction = transactionAmounts.forSave(draft, previous, accountCurrency, main, s.manualRateValue)
                if (transaction == null) {
                    _formState.update {
                        it.copy(isLoading = false, needsManualRate = true, rate = null, error = TransactionFormError.NeedsRate)
                    }
                    return@launch
                }

                if (s.isEditing) {
                    transactionRepository.updateTransaction(transaction)
                    if (transaction.isRecurrent && transaction.isCleared && !s.wasCleared) {
                        transactionRepository.createNextRecurrence(transaction)
                    }
                } else {
                    transactionRepository.insertTransaction(transaction)
                }

                analyticsHelper.logEvent(
                    if (s.isEditing) "transaction_updated" else "transaction_created",
                    mapOf(
                        "type" to s.type.name,
                        "has_category" to (s.categoryId != null),
                        "has_reminder" to s.hasReminder
                    )
                )

                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: MissingRateException) {
                _formState.update { it.copy(isLoading = false, error = TransactionFormError.MissingRate(e.from)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saving the movement failed", e)
                _formState.update { it.copy(isLoading = false, error = TransactionFormError.SaveFailed) }
            }
        }
    }

    fun delete() {
        val s = _formState.value
        if (!s.isEditing) return
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            try {
                transactionRepository.deleteTransaction(s.id)
                analyticsHelper.logEvent("transaction_deleted", mapOf("type" to s.type.name))
                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Deleting the movement failed", e)
                _formState.update { it.copy(isLoading = false, error = TransactionFormError.DeleteFailed) }
            }
        }
    }

    private companion object {
        const val TAG = "TransactionForm"
    }
}
