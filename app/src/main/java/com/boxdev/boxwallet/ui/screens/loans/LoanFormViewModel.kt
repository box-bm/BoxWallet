package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class LoanFormState(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val totalAmount: String = "",
    val installments: String = "",
    val startDate: LocalDateTime = LocalDateTime.now(),
    val categoryId: Long? = null,
    val currency: String = "USD",
    val loanType: LoanType = LoanType.EXPENSE,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val frequencyInterval: String = "1",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
) {
    val installmentAmount: Double?
        get() {
            val amount = totalAmount.toDoubleOrNull() ?: return null
            val count = installments.toIntOrNull() ?: return null
            return if (count > 0) amount / count else null
        }

    val isValid: Boolean
        get() = name.isNotBlank() &&
                totalAmount.toDoubleOrNull()?.let { it > 0 } == true &&
                (installments.toIntOrNull() ?: 0) > 0 &&
                (frequency != RecurrenceFrequency.CUSTOM_MONTHS || (frequencyInterval.toIntOrNull() ?: 0) > 0)
}

@HiltViewModel
class LoanFormViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _formState = MutableStateFlow(LoanFormState())
    val formState: StateFlow<LoanFormState> = _formState.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { _categories.value = it }
        }
        viewModelScope.launch {
            userPreferences.defaultCurrency.collect { currency ->
                _formState.update { state ->
                    if (!state.isEditing) state.copy(currency = currency.name) else state
                }
            }
        }
    }

    fun loadLoan(loanId: Long?) {
        if (loanId == null || loanId == -1L) {
            _formState.value = LoanFormState(currency = _formState.value.currency)
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            val loan = loanRepository.getLoanById(loanId)
            if (loan != null) {
                _formState.value = LoanFormState(
                    id = loan.id,
                    name = loan.name,
                    description = loan.description,
                    totalAmount = loan.totalAmount.toString(),
                    installments = loan.totalInstallments.toString(),
                    startDate = loan.startDate,
                    categoryId = loan.categoryId,
                    currency = loan.currency,
                    loanType = loan.loanType,
                    frequency = loan.frequency,
                    frequencyInterval = loan.frequencyInterval.toString(),
                    isEditing = true,
                    isLoading = false
                )
            } else {
                _formState.update { it.copy(isLoading = false, error = "Loan not found") }
            }
        }
    }

    fun updateName(value: String) = _formState.update { it.copy(name = value, error = null) }
    fun updateDescription(value: String) = _formState.update { it.copy(description = value) }
    fun updateTotalAmount(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update { it.copy(totalAmount = value, error = null) }
        }
    }
    fun updateInstallments(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
            _formState.update { it.copy(installments = value, error = null) }
        }
    }
    fun updateStartDate(value: LocalDateTime) = _formState.update { it.copy(startDate = value) }
    fun updateTime(hour: Int, minute: Int) = _formState.update { it.copy(startDate = it.startDate.withHour(hour).withMinute(minute)) }
    fun updateCategory(id: Long?) = _formState.update { it.copy(categoryId = id) }
    fun updateLoanType(type: LoanType) = _formState.update { it.copy(loanType = type) }
    fun updateFrequency(freq: RecurrenceFrequency) = _formState.update { it.copy(frequency = freq) }
    fun updateFrequencyInterval(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
            _formState.update { it.copy(frequencyInterval = value, error = null) }
        }
    }

    fun save() {
        val s = _formState.value
        if (!s.isValid) {
            _formState.update { it.copy(error = "Please fill in all required fields") }
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            try {
                val loan = Loan(
                    id = if (s.isEditing) s.id else 0,
                    name = s.name,
                    description = s.description,
                    totalAmount = s.totalAmount.toDouble(),
                    currency = s.currency,
                    totalInstallments = s.installments.toInt(),
                    categoryId = s.categoryId,
                    startDate = s.startDate,
                    loanType = s.loanType,
                    frequency = s.frequency,
                    frequencyInterval = s.frequencyInterval.toIntOrNull() ?: 1
                )
                if (s.isEditing) loanRepository.updateLoan(loan)
                else loanRepository.insertLoanWithInstallments(loan)
                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message ?: "Error saving loan") }
            }
        }
    }

    fun delete() {
        val s = _formState.value
        if (!s.isEditing) return
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            try {
                loanRepository.deleteLoan(s.id)
                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message ?: "Error deleting loan") }
            }
        }
    }
}
