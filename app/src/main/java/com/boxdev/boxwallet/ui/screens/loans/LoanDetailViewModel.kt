package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LoanDetailUiState(
    val loan: Loan? = null,
    /** The disbursement and payments, oldest first. */
    val movements: List<Transaction> = emptyList(),
    val category: Category? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loanRepository: LoanRepository,
    private val categoryRepository: CategoryRepository,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    /** From the nav route on phones; set with [bind] when the detail lives in a pane. */
    private val loanId = MutableStateFlow(savedStateHandle.get<Long>("loanId"))

    fun bind(id: Long) {
        loanId.value = id
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LoanDetailUiState> = loanId.filterNotNull().flatMapLatest { id ->
        combine(
            loanRepository.observe(id),
            loanRepository.observeMovements(id),
            categoryRepository.getAllCategories()
        ) { loan, movements, _ ->
            LoanDetailUiState(
                loan = loan,
                movements = movements,
                category = null,
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoanDetailUiState()
    )
}
