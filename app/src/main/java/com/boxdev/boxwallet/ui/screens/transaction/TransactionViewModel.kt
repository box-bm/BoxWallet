package com.boxdev.boxwallet.ui.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TransactionFormState(
    val id: Long = 0,
    val amount: String = "",
    val comment: String = "",
    val date: LocalDate = LocalDate.now(),
    val type: TransactionType = TransactionType.EXPENSE,
    val isLoan: Boolean = false,
    val installments: String = "",
    val hasReminder: Boolean = false,
    val reminderDate: LocalDate? = null,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) {
    val installmentAmount: Double?
        get() {
            val amountValue = amount.toDoubleOrNull() ?: return null
            val installmentCount = installments.toIntOrNull() ?: return null
            return if (installmentCount > 0) amountValue / installmentCount else null
        }

    val isValid: Boolean
        get() = amount.toDoubleOrNull() != null &&
                amount.toDoubleOrNull()!! > 0 &&
                (!isLoan || (installments.toIntOrNull() ?: 0) > 0)
}

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    fun loadTransaction(transactionId: Long?) {
        if (transactionId == null || transactionId == -1L) {
            _formState.value = TransactionFormState()
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            val transaction = transactionRepository.getTransactionById(transactionId)
            if (transaction != null) {
                _formState.update {
                    TransactionFormState(
                        id = transaction.id,
                        amount = transaction.amount.toString(),
                        comment = transaction.comment,
                        date = transaction.date,
                        type = transaction.type,
                        isLoan = transaction.isLoan,
                        installments = transaction.totalInstallments?.toString() ?: "",
                        hasReminder = transaction.hasReminder,
                        reminderDate = transaction.reminderDate,
                        isEditing = true,
                        isLoading = false
                    )
                }
            } else {
                _formState.update { it.copy(isLoading = false, error = "Transaction not found") }
            }
        }
    }

    fun updateAmount(value: String) {
        // Allow only valid decimal numbers
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update { it.copy(amount = value, error = null) }
        }
    }

    fun updateComment(value: String) {
        _formState.update { it.copy(comment = value, error = null) }
    }

    fun updateDate(value: LocalDate) {
        _formState.update { it.copy(date = value, error = null) }
    }

    fun updateType(value: TransactionType) {
        _formState.update { it.copy(type = value, error = null) }
    }

    fun updateIsLoan(value: Boolean) {
        _formState.update {
            it.copy(
                isLoan = value,
                installments = if (!value) "" else it.installments,
                error = null
            )
        }
    }

    fun updateInstallments(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
            _formState.update { it.copy(installments = value, error = null) }
        }
    }

    fun updateHasReminder(value: Boolean) {
        _formState.update {
            it.copy(
                hasReminder = value,
                reminderDate = if (value && it.reminderDate == null) it.date.minusDays(1) else if (!value) null else it.reminderDate,
                error = null
            )
        }
    }

    fun updateReminderDate(value: LocalDate) {
        _formState.update { it.copy(reminderDate = value, error = null) }
    }

    fun saveTransaction() {
        val state = _formState.value

        if (!state.isValid) {
            _formState.update { it.copy(error = "Please enter a valid amount") }
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }

            try {
                val transaction = Transaction(
                    id = if (state.isEditing) state.id else 0,
                    amount = state.amount.toDouble(),
                    comment = state.comment,
                    date = state.date,
                    type = state.type,
                    isLoan = state.isLoan,
                    totalInstallments = if (state.isLoan) state.installments.toIntOrNull() else null,
                    installmentAmount = state.installmentAmount,
                    hasReminder = state.hasReminder,
                    reminderDate = state.reminderDate
                )

                if (state.isEditing) {
                    transactionRepository.updateTransaction(transaction)
                } else {
                    if (state.isLoan && (state.installments.toIntOrNull() ?: 0) > 1) {
                        transactionRepository.insertTransactionWithInstallments(transaction)
                    } else {
                        transactionRepository.insertTransaction(transaction)
                    }
                }

                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message ?: "Error saving transaction") }
            }
        }
    }

    fun deleteTransaction() {
        val state = _formState.value
        if (!state.isEditing) return

        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            try {
                transactionRepository.deleteTransaction(state.id)
                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message ?: "Error deleting transaction") }
            }
        }
    }
}
