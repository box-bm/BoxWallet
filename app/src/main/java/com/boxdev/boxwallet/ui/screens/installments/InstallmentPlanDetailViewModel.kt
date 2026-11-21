package com.boxdev.boxwallet.ui.screens.installments

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.InstallmentPlanRepository
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.InstallmentPlan
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.util.CurrentDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One installment of the plan; [charged] once its closing day is today or behind. */
data class InstallmentRow(val installment: Transaction, val charged: Boolean)

data class InstallmentPlanDetailUiState(
    val plan: InstallmentPlan? = null,
    /** Every installment, future ones included, in date order. */
    val installments: List<InstallmentRow> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
) {
    val category: Category? get() = plan?.categoryId?.let { id -> categories.firstOrNull { it.id == id } }

    /** What deleting the plan takes off the card's debt: the charged installments (the pending ones were never debt). */
    val debtDrop: Money? get() = plan?.let { p ->
        Money(installments.filter { it.charged }.sumOf { it.installment.accountAmount.minor }, p.accountTotal.currency)
    }
}

/** What the delete confirmation states: how many installments go, and how much the card's debt drops. */
data class PlanDeleteConfirmation(val installments: Int, val debtDrop: Money)

/** A visacuotas purchase: its installments, its description and category (the only editable parts) and its delete. */
@HiltViewModel
class InstallmentPlanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: InstallmentPlanRepository,
    categoryRepository: CategoryRepository,
    currentDay: CurrentDay,
) : ViewModel() {

    private val planId: Long = checkNotNull(savedStateHandle.get<Long>("planId")) { "No planId" }

    val uiState: StateFlow<InstallmentPlanDetailUiState> = combine(
        repository.observePlan(planId),
        repository.observeInstallments(planId),
        categoryRepository.getAllCategories(),
        currentDay.days,
    ) { plan, installments, categories, today ->
        InstallmentPlanDetailUiState(
            plan = plan,
            installments = installments.map { InstallmentRow(it, charged = !it.date.toLocalDate().isAfter(today)) },
            categories = categories,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InstallmentPlanDetailUiState())

    private val _confirmDelete = MutableStateFlow<PlanDeleteConfirmation?>(null)
    /** Set while the delete confirmation shows. */
    val confirmDelete: StateFlow<PlanDeleteConfirmation?> = _confirmDelete.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    /** True once the plan is gone, so the screen can leave. */
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** A one-off message for the snackbar; cleared with [messageShown]. */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    /** Changes the description and category of the plan and every installment. */
    fun save(description: String, categoryId: Long?) = attempt(R.string.installment_plan_error_save) {
        repository.updateDetails(planId, description.trim(), categoryId)
    }

    fun requestDelete() {
        val state = uiState.value
        val plan = state.plan ?: return
        _confirmDelete.value = PlanDeleteConfirmation(plan.installments, state.debtDrop ?: return)
    }

    fun dismissDelete() {
        _confirmDelete.value = null
    }

    /** Removes the plan and all its installments, charged ones included. */
    fun delete() {
        _confirmDelete.value = null
        attempt(R.string.installment_plan_error_delete) {
            repository.delete(planId)
            _deleted.value = true
        }
    }

    /** Runs [action], turning a failure into [error] on the snackbar instead of a crash. */
    private fun attempt(@StringRes error: Int, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = error
            }
        }
    }
}
