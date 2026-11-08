package com.boxdev.boxwallet.ui.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.ReminderDuration
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.parseMajor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Currency
import javax.inject.Inject

data class TransactionFormState(
    val id: Long = 0,
    /** Typed in major units of [currency]. */
    val amount: String = "",
    val currency: Currency = Currency.getInstance("USD"),
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
    val error: String? = null,
    val wasCleared: Boolean = false
) {
    val money: Money? get() = parseMajor(amount, currency)

    val isValid: Boolean
        get() = money != null &&
                (!isRecurrent || recurrenceFrequency != null) &&
                (!isRecurrent || recurrenceFrequency != RecurrenceFrequency.CUSTOM_MONTHS || (recurrenceInterval.toIntOrNull() ?: 0) > 0)
}

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferences,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    /** The movement being edited, so fields the form doesn't show (account, recurrence parent) survive the save. */
    private var editing: Transaction? = null

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadTransaction(transactionId: Long?) {
        editing = null
        if (transactionId == null || transactionId == -1L) {
            _formState.value = TransactionFormState()
            viewModelScope.launch {
                val main = userPreferences.mainCurrency.first()
                _formState.update { it.copy(currency = main) }
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
                _formState.update { it.copy(isLoading = false, error = "Transaction not found") }
            }
        }
    }

    fun updateAmount(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update { it.copy(amount = value, error = null) }
        }
    }

    fun updateDescription(value: String) = _formState.update { it.copy(description = value) }
    fun updateCategory(id: Long?) = _formState.update { it.copy(categoryId = id) }
    fun updateDate(value: LocalDateTime) = _formState.update { it.copy(date = value) }
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
            _formState.update { it.copy(error = "Please enter a valid amount") }
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            try {
                val nextOccurrenceDate = if (s.isRecurrent && s.recurrenceFrequency != null) {
                    s.recurrenceFrequency.advance(s.date, s.recurrenceInterval.toIntOrNull() ?: 1)
                } else null

                val previous = editing
                val accountId = previous?.accountId ?: accountRepository.defaultAccount().id
                // Single currency until rates exist (Task 7): movement, account and main share identity rates.
                val transaction = Transaction.singleCurrency(accountId, s.type, s.date, s.money!!).copy(
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
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message ?: "Error saving") }
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
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message ?: "Error deleting") }
            }
        }
    }
}
