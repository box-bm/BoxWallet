package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanPlan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.parseMajor
import com.boxdev.boxwallet.ui.screens.transaction.differsFrom
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency
import javax.inject.Inject

data class LoanFormState(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    /** Typed in major units of [currency]. */
    val totalAmount: String = "",
    val loanType: LoanType = LoanType.EXPENSE,
    /** The loan's single account; its currency is the loan's. */
    val accountId: Long? = null,
    /** The accounts the loan can use (no cards; when editing, only the loan's currency). */
    val accounts: List<Account> = emptyList(),
    /** Disbursement date. */
    val date: LocalDateTime = LocalDateTime.now(),
    val hasPlan: Boolean = false,
    val installments: String = "",
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val frequencyInterval: String = "1",
    val firstDueDate: LocalDate = LocalDate.now(),
    /** Only used while there is no plan. */
    val expectedDate: LocalDate? = null,
    /** What has been paid so far, when editing. */
    val paid: Money? = null,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    /** Set once a confirmed delete went through; the form then leaves the loan's screens too. */
    val deleted: Boolean = false,
    val error: String? = null,
) {
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }

    val currency: Currency get() = account?.currency ?: Currency.getInstance("USD")

    val total: Money? get() = parseMajor(totalAmount, currency)

    /** The regular installment; the last one also absorbs the division remainder. */
    val installmentAmount: Money?
        get() {
            if (!hasPlan) return null
            val amount = total ?: return null
            val count = installments.toIntOrNull() ?: return null
            return if (count > 0) Money(amount.minor / count, currency) else null
        }

    val isValid: Boolean
        get() {
            val amount = total ?: return false
            return name.isNotBlank() &&
                    accountId != null &&
                    amount.minor >= (paid?.minor ?: 0L) &&
                    (!hasPlan || (
                            (installments.toIntOrNull() ?: 0) > 0 &&
                                    (frequency != RecurrenceFrequency.CUSTOM_MONTHS || (frequencyInterval.toIntOrNull() ?: 0) > 0)
                            ))
        }
}

@HiltViewModel
class LoanFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loanRepository: LoanRepository,
    private val accountRepository: AccountRepository,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    private val _formState = MutableStateFlow(LoanFormState())
    val formState: StateFlow<LoanFormState> = _formState.asStateFlow()

    /** The form as it stood when loading finished; null while loading, so no edit can be detected yet. */
    private val initial = MutableStateFlow<LoanFormState?>(null)

    /** True once the user has edited a field since loading finished. */
    val hasUnsavedChanges: StateFlow<Boolean> = combine(_formState, initial) { state, base ->
        base != null && state.differsFrom(base)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        loadLoan(savedStateHandle.get<Long>("id"), savedStateHandle.get<Long>("accountId"))
    }

    private fun Account.usableForLoan() = type != AccountType.CREDIT_CARD && !isArchived

    private fun loadLoan(loanId: Long?, routeAccountId: Long?) {
        initial.value = null
        _formState.value = LoanFormState(isLoading = true)
        viewModelScope.launch {
            val all = accountRepository.observeAll().first()
            if (loanId == null || loanId == -1L) {
                val usable = all.filter { it.usableForLoan() }
                val preferred = usable.firstOrNull { it.id == routeAccountId }
                    ?: accountRepository.defaultAccount().takeIf { it.usableForLoan() }?.let { d -> usable.firstOrNull { it.id == d.id } }
                    ?: usable.firstOrNull()
                _formState.value = LoanFormState(accounts = usable, accountId = preferred?.id)
                initial.value = _formState.value
                return@launch
            }
            val loan = loanRepository.get(loanId)
            if (loan == null) {
                _formState.update { it.copy(isLoading = false, error = "Loan not found") }
                return@launch
            }
            // Same currency only; the account already used stays even if archived since.
            val offered = all.filter {
                it.type != AccountType.CREDIT_CARD && it.currency == loan.total.currency && (!it.isArchived || it.id == loan.accountId)
            }
            val frequency = loan.plan?.frequency ?: RecurrenceFrequency.MONTHLY
            val interval = loan.plan?.interval ?: 1
            _formState.value = LoanFormState(
                id = loan.id,
                name = loan.name,
                description = loan.description,
                totalAmount = loan.total.toMajor().toPlainString(),
                loanType = loan.loanType,
                accountId = loan.accountId,
                accounts = offered,
                date = loan.date,
                hasPlan = loan.plan != null,
                installments = loan.plan?.installments?.toString() ?: "",
                frequency = frequency,
                frequencyInterval = interval.toString(),
                firstDueDate = loan.plan?.firstDue ?: frequency.advance(loan.date.toLocalDate(), interval, 1),
                expectedDate = loan.expectedDate,
                paid = loan.paid,
                isEditing = true,
            )
            initial.value = _formState.value
        }
    }

    fun setAccount(id: Long) {
        if (_formState.value.accounts.none { it.id == id }) return
        _formState.update { it.copy(accountId = id, error = null) }
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
    fun updateDate(value: LocalDateTime) = _formState.update { it.copy(date = value) }
    fun updateTime(hour: Int, minute: Int) = _formState.update { it.copy(date = it.date.withHour(hour).withMinute(minute)) }
    fun updateLoanType(type: LoanType) = _formState.update { it.copy(loanType = type, error = null) }
    fun updateFrequency(freq: RecurrenceFrequency) = _formState.update { it.copy(frequency = freq) }
    fun updateFrequencyInterval(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
            _formState.update { it.copy(frequencyInterval = value, error = null) }
        }
    }
    fun updateFirstDueDate(value: LocalDate) = _formState.update { it.copy(firstDueDate = value) }
    fun updateExpectedDate(value: LocalDate?) = _formState.update { it.copy(expectedDate = value) }

    /**
     * Turning the plan on proposes the first due one period after the disbursement, unless the loan
     * already had a plan (then its stored first due stays).
     */
    fun setHasPlan(on: Boolean) = _formState.update { s ->
        if (on && !s.hasPlan && initial.value?.hasPlan != true) {
            s.copy(
                hasPlan = true,
                firstDueDate = s.frequency.advance(s.date.toLocalDate(), s.frequencyInterval.toIntOrNull() ?: 1, 1),
                error = null,
            )
        } else s.copy(hasPlan = on, error = null)
    }

    fun save() {
        val s = _formState.value
        if (!s.isValid) {
            _formState.update { it.copy(error = "Please fill in all required fields") }
            return
        }
        if (s.isLoading) return
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            try {
                val loan = Loan(
                    id = if (s.isEditing) s.id else 0,
                    name = s.name.trim(),
                    description = s.description,
                    total = s.total!!,
                    accountId = s.accountId!!,
                    date = s.date,
                    loanType = s.loanType,
                    plan = if (s.hasPlan) {
                        LoanPlan(
                            installments = s.installments.toInt(),
                            frequency = s.frequency,
                            interval = s.frequencyInterval.toIntOrNull() ?: 1,
                            firstDue = s.firstDueDate,
                        )
                    } else null,
                    expectedDate = if (s.hasPlan) null else s.expectedDate,
                )
                if (s.isEditing) loanRepository.update(loan)
                else loanRepository.create(loan)

                analyticsHelper.logEvent(
                    if (s.isEditing) "loan_updated" else "loan_created",
                    mapOf(
                        "type" to s.loanType.name,
                        "installments" to (if (s.hasPlan) s.installments.toIntOrNull() ?: 0 else 0)
                    )
                )

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
                loanRepository.delete(s.id)
                analyticsHelper.logEvent("loan_deleted")
                _formState.update { it.copy(isLoading = false, deleted = true) }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message ?: "Error deleting loan") }
            }
        }
    }
}
