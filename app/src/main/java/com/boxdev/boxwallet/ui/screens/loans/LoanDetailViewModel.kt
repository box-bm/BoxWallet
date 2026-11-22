package com.boxdev.boxwallet.ui.screens.loans

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class LoanDetailUiState(
    val loan: Loan? = null,
    /** Name of the loan's account. */
    val accountName: String? = null,
    /** The disbursement and payments, oldest first. */
    val movements: List<Transaction> = emptyList(),
    /** The payment sheet, open while set. */
    val payment: LoanPaymentState? = null,
    /** Why the last action was refused. */
    @StringRes val error: Int? = null,
    /** Set once the loan was deleted; the screen then leaves. */
    val deleted: Boolean = false,
    val isLoading: Boolean = true,
) {
    /** Payments only: the disbursement goes with its loan. */
    val payments: List<Transaction>
        get() = loan?.let { l -> movements.filter { it.type == l.paymentType } } ?: emptyList()
}

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loanRepository: LoanRepository,
    private val accountRepository: AccountRepository,
    private val analyticsHelper: AnalyticsHelper,
) : ViewModel() {

    /** From the nav route on phones; set with [bind] when the detail lives in a pane. */
    private val loanId = MutableStateFlow(savedStateHandle.get<Long>("loanId"))
    private val local = MutableStateFlow(Local())

    private data class Local(
        val payment: LoanPaymentState? = null,
        @StringRes val error: Int? = null,
        val deleted: Boolean = false,
    )

    fun bind(id: Long) {
        loanId.value = id
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LoanDetailUiState> = loanId.filterNotNull().flatMapLatest { id ->
        combine(
            loanRepository.observe(id),
            loanRepository.observeMovements(id),
            accountRepository.observeAll(),
            local,
        ) { loan, movements, accounts, local ->
            LoanDetailUiState(
                loan = loan,
                accountName = accounts.firstOrNull { it.id == loan?.accountId }?.name,
                movements = movements,
                payment = local.payment,
                error = local.error,
                deleted = local.deleted,
                isLoading = false,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoanDetailUiState()
    )

    /** What a new payment starts with: the next installment's missing part, or everything left without a plan. */
    fun paymentPrefill(): Money {
        val loan = uiState.value.loan ?: error("No loan loaded")
        return loan.nextDue()?.uncovered ?: loan.remaining
    }

    fun openNewPayment() {
        val loan = uiState.value.loan ?: return
        local.update { it.copy(payment = LoanPaymentState.forNew(loan, paymentPrefill(), LocalDateTime.now()), error = null) }
    }

    fun openPayment(paymentId: Long) {
        val state = uiState.value
        val loan = state.loan ?: return
        val payment = state.payments.firstOrNull { it.id == paymentId } ?: return
        local.update { it.copy(payment = LoanPaymentState.forEdit(loan, payment), error = null) }
    }

    fun updatePaymentAmount(value: String) {
        if (value.all { it.isDigit() || it == '.' || it == ',' }) {
            local.update { s -> s.copy(payment = s.payment?.copy(amount = value), error = null) }
        }
    }

    fun updatePaymentDate(date: LocalDateTime) =
        local.update { s -> s.copy(payment = s.payment?.copy(date = date), error = null) }

    fun dismissPayment() = local.update { it.copy(payment = null, error = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    /** Saves the open sheet: a new payment, or the edit of one. A refusal keeps the sheet open with its reason. */
    fun savePayment() {
        val loan = uiState.value.loan ?: return
        val sheet = local.value.payment?.takeIf { it.isValid } ?: return
        val amount = sheet.parsed ?: return
        attempt(closesSheet = true, refused = R.string.loan_payment_refused) {
            if (sheet.editingId == null) {
                loanRepository.addPayment(loan.id, amount, sheet.date)
                analyticsHelper.logEvent("loan_payment_added")
            } else {
                loanRepository.updatePayment(sheet.editingId, amount, sheet.date)
                analyticsHelper.logEvent("loan_payment_edited")
            }
        }
    }

    fun deleteOpenPayment() {
        val id = local.value.payment?.editingId ?: return
        attempt(closesSheet = true, refused = R.string.loan_payment_delete_failed) {
            loanRepository.deletePayment(id)
            analyticsHelper.logEvent("loan_payment_deleted")
        }
    }

    /**
     * "Mark as paid": opens the payment sheet with what installment [number] still lacks, dated
     * now; the amount and date can be changed before saving.
     */
    fun markPaid(number: Int) {
        val loan = uiState.value.loan ?: return
        val lacking = loan.schedule().firstOrNull { it.number == number }?.uncovered ?: return
        if (lacking.minor <= 0) return
        local.update { it.copy(payment = LoanPaymentState.forNew(loan, lacking, LocalDateTime.now()), error = null) }
    }

    fun deleteLoan() {
        val loan = uiState.value.loan ?: return
        attempt(closesSheet = false, refused = R.string.loan_delete_failed, failed = R.string.loan_delete_failed) {
            loanRepository.delete(loan.id)
            analyticsHelper.logEvent("loan_deleted")
            local.update { it.copy(deleted = true) }
        }
    }

    /**
     * Runs [block], reporting a repository refusal (IllegalArgumentException) with [refused], a
     * missing rate with its own message, and anything else with [failed].
     */
    private fun attempt(
        closesSheet: Boolean,
        @StringRes refused: Int,
        @StringRes failed: Int = R.string.account_error_generic,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
                if (closesSheet) local.update { it.copy(payment = null, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: MissingRateException) {
                local.update { it.copy(error = R.string.account_error_missing_rate) }
            } catch (_: IllegalArgumentException) {
                local.update { it.copy(error = refused) }
            } catch (_: Exception) {
                local.update { it.copy(error = failed) }
            }
        }
    }
}
