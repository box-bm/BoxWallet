package com.boxdev.boxwallet.ui.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TransactionFormState(
    val id: Long = 0,
    val amount: String = "",
    val description: String = "",
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val type: TransactionType = TransactionType.EXPENSE,
    val isCleared: Boolean = false,
    val hasReminder: Boolean = false,
    val reminderDate: LocalDate? = null,
    val currency: String = "USD",
    val isRecurrent: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency? = null,
    val recurrenceInterval: String = "1",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val wasCleared: Boolean = false
) {
    val isValid: Boolean
        get() = amount.toDoubleOrNull()?.let { it > 0 } == true &&
                (!isRecurrent || recurrenceFrequency != null) &&
                (!isRecurrent || recurrenceFrequency != RecurrenceFrequency.CUSTOM_MONTHS || (recurrenceInterval.toIntOrNull() ?: 0) > 0)
}

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { _categories.value = it }
        }
        viewModelScope.launch {
            val currency = userPreferences.defaultCurrency.first()
            _formState.update { it.copy(currency = currency.name) }
        }
    }

    fun loadTransaction(transactionId: Long?) {
        if (transactionId == null || transactionId == -1L) {
            _formState.value = TransactionFormState(currency = _formState.value.currency)
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            val t = transactionRepository.getTransactionById(transactionId)
            if (t != null) {
                _formState.value = TransactionFormState(
                    id = t.id,
                    amount = t.amount.toString(),
                    description = t.description,
                    categoryId = t.categoryId,
                    date = t.date,
                    type = t.type,
                    isCleared = t.isCleared,
                    hasReminder = t.hasReminder,
                    reminderDate = t.reminderDate,
                    currency = t.currency,
                    isRecurrent = t.isRecurrent,
                    recurrenceFrequency = t.recurrenceFrequency,
                    recurrenceInterval = t.recurrenceInterval?.toString() ?: "1",
                    isEditing = true,
                    isLoading = false,
                    wasCleared = t.isCleared
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
    fun updateDate(value: LocalDate) = _formState.update { it.copy(date = value) }
    fun updateType(value: TransactionType) = _formState.update { it.copy(type = value) }
    fun updateIsCleared(value: Boolean) = _formState.update { it.copy(isCleared = value) }
    fun updateHasReminder(value: Boolean) = _formState.update { it.copy(hasReminder = value, reminderDate = if (value) LocalDate.now() else null) }
    fun updateReminderDate(value: LocalDate?) = _formState.update { it.copy(reminderDate = value) }
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
                // Calculate next occurrence date if recurrent
                val nextOccurrenceDate = if (s.isRecurrent && s.recurrenceFrequency != null) {
                    val interval = s.recurrenceInterval.toIntOrNull() ?: 1
                    when (s.recurrenceFrequency) {
                        RecurrenceFrequency.DAILY -> s.date.plusDays(interval.toLong())
                        RecurrenceFrequency.WEEKLY -> s.date.plusWeeks(interval.toLong())
                        RecurrenceFrequency.BIWEEKLY -> s.date.plusWeeks(2)
                        RecurrenceFrequency.MONTHLY -> s.date.plusMonths(1)
                        RecurrenceFrequency.YEARLY -> s.date.plusYears(1)
                        RecurrenceFrequency.CUSTOM_MONTHS -> s.date.plusMonths(interval.toLong())
                    }
                } else null

                val transaction = Transaction(
                    id = if (s.isEditing) s.id else 0,
                    amount = s.amount.toDouble(),
                    currency = s.currency,
                    description = s.description,
                    date = s.date,
                    type = s.type,
                    isCleared = s.isCleared,
                    hasReminder = s.hasReminder,
                    reminderDate = s.reminderDate,
                    categoryId = s.categoryId,
                    isRecurrent = s.isRecurrent,
                    recurrenceFrequency = s.recurrenceFrequency,
                    recurrenceInterval = s.recurrenceInterval.toIntOrNull(),
                    nextOccurrenceDate = nextOccurrenceDate
                )

                if (s.isEditing) {
                    transactionRepository.updateTransaction(transaction)
                    // If marking a recurrent transaction as cleared, create next instance
                    if (transaction.isRecurrent && transaction.isCleared && !s.wasCleared) {
                        transactionRepository.createNextRecurrence(transaction)
                    }
                } else {
                    transactionRepository.insertTransaction(transaction)
                }

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
                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message ?: "Error deleting") }
            }
        }
    }
}
