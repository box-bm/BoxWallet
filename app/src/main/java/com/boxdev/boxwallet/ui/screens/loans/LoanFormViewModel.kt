package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.data.local.dao.CurrencyUsage
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.InstallmentState
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanPlan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.parseMajor
import com.boxdev.boxwallet.ui.screens.transaction.differsFrom
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Currency
import javax.inject.Inject

data class LoanFormState(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    /** Typed in major units of [currency]. */
    val totalAmount: String = "",
    val currency: Currency = Currency.getInstance("USD"),
    val installments: String = "",
    val startDate: LocalDateTime = LocalDateTime.now(),
    val categoryId: Long? = null,
    val loanType: LoanType = LoanType.EXPENSE,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val frequencyInterval: String = "1",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    /** Set once a confirmed delete went through; the form then leaves the loan's screens too. */
    val deleted: Boolean = false,
    val error: String? = null,
    /** Set when a foreign-currency loan had no rate at all; the form shows a localized message. */
    val missingRateCurrency: Currency? = null,
    /** Installments already paid; the currency can't change once there is any. */
    val paidInstallments: Int = 0,
) {
    val currencyLocked: Boolean get() = isEditing && paidInstallments > 0

    val total: Money? get() = parseMajor(totalAmount, currency)

    /** The regular installment; the last one also absorbs the division remainder. */
    val installmentAmount: Money?
        get() {
            val amount = total ?: return null
            val count = installments.toIntOrNull() ?: return null
            return if (count > 0) Money(amount.minor / count, currency) else null
        }

    val isValid: Boolean
        get() = name.isNotBlank() &&
                total != null &&
                (installments.toIntOrNull() ?: 0) > 0 &&
                (frequency != RecurrenceFrequency.CUSTOM_MONTHS || (frequencyInterval.toIntOrNull() ?: 0) > 0)
}

@HiltViewModel
class LoanFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loanRepository: LoanRepository,
    categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    /** The loan being edited, so fields the form doesn't show (account) survive the save. */
    private var editing: Loan? = null

    private val _formState = MutableStateFlow(LoanFormState())
    val formState: StateFlow<LoanFormState> = _formState.asStateFlow()

    /** The form as it stood when loading finished; null while loading, so no edit can be detected yet. */
    private val initial = MutableStateFlow<LoanFormState?>(null)

    /** True once the user has edited a field since loading finished. */
    val hasUnsavedChanges: StateFlow<Boolean> = combine(_formState, initial) { state, base ->
        base != null && state.differsFrom(base)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentCurrencies: StateFlow<List<CurrencyUsage>> = transactionRepository.recentCurrencies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadLoan(savedStateHandle.get<Long>("id"))
    }

    private fun loadLoan(loanId: Long?) {
        editing = null
        initial.value = null
        if (loanId == null || loanId == -1L) {
            // A new loan defaults to its account's currency; loading until known so a save can't use a placeholder.
            _formState.value = LoanFormState(isLoading = true)
            viewModelScope.launch {
                val account = accountRepository.defaultAccount()
                _formState.update { it.copy(currency = account.currency, isLoading = false) }
                initial.value = _formState.value
            }
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            val loan = loanRepository.get(loanId)
            editing = loan
            if (loan != null) {
                _formState.value = LoanFormState(
                    id = loan.id,
                    name = loan.name,
                    description = loan.description,
                    totalAmount = loan.total.toMajor().toPlainString(),
                    currency = loan.total.currency,
                    installments = (loan.plan?.installments ?: 0).toString(),
                    startDate = loan.date,
                    loanType = loan.loanType,
                    frequency = loan.plan?.frequency ?: RecurrenceFrequency.MONTHLY,
                    frequencyInterval = (loan.plan?.interval ?: 1).toString(),
                    isEditing = true,
                    isLoading = false,
                    paidInstallments = loan.schedule().count { it.state == InstallmentState.PAID },
                )
                initial.value = _formState.value
            } else {
                _formState.update { it.copy(isLoading = false, error = "Loan not found") }
            }
        }
    }

    fun updateCurrency(currency: Currency) {
        if (_formState.value.currencyLocked) return
        _formState.update { it.copy(currency = currency, error = null, missingRateCurrency = null) }
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
        if (s.isLoading) return
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true, missingRateCurrency = null) }
            try {
                val loan = Loan(
                    id = if (s.isEditing) s.id else 0,
                    name = s.name,
                    description = s.description,
                    total = s.total!!,
                    accountId = editing?.accountId ?: accountRepository.defaultAccount().id,
                    date = s.startDate,
                    loanType = s.loanType,
                    plan = LoanPlan(
                        installments = s.installments.toInt(),
                        frequency = s.frequency,
                        interval = s.frequencyInterval.toIntOrNull() ?: 1,
                        firstDue = s.startDate.toLocalDate(),
                    ),
                )
                if (s.isEditing) loanRepository.update(loan)
                else loanRepository.create(loan)

                analyticsHelper.logEvent(
                    if (s.isEditing) "loan_updated" else "loan_created",
                    mapOf(
                        "type" to s.loanType.name,
                        "installments" to s.installments.toIntOrNull().let { it ?: 0 }
                    )
                )

                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: MissingRateException) {
                _formState.update { it.copy(isLoading = false, missingRateCurrency = e.from) }
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
                loanRepository.delete(s.id)
                analyticsHelper.logEvent("loan_deleted")
                _formState.update { it.copy(isLoading = false, deleted = true) }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message ?: "Error deleting loan") }
            }
        }
    }
}
