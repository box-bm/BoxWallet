package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.SavedStateHandle
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
class LoanFormViewModelTest {

    private val usd = Currency.getInstance("USD")
    private val loanRepository = mock<LoanRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val categoryRepository = mock<CategoryRepository>()
    private val transactionRepository = mock<TransactionRepository>()
    private val analytics = mock<AnalyticsHelper>()

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(categoryRepository.getAllCategories()).thenReturn(flowOf(emptyList()))
        whenever(transactionRepository.recentCurrencies()).thenReturn(flowOf(emptyList()))
        whenever(accountRepository.defaultAccount())
            .thenReturn(Account(id = 1, name = "Cash", type = AccountType.CASH, currency = usd, createdAt = 0))
        whenever(loanRepository.getLoanById(7L)).thenReturn(
            Loan(id = 7, name = "Car", totalAmount = Money(100_00, usd), totalInstallments = 10,
                startDate = LocalDateTime.of(2026, 1, 1, 0, 0)),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(args: Map<String, Any?>) = LoanFormViewModel(
        SavedStateHandle(args), loanRepository, categoryRepository, accountRepository, transactionRepository, analytics,
    )

    @Test
    fun loadsTheLoanNamedByTheIdArgument() = runTest {
        val vm = vm(mapOf("id" to 7L))
        advanceUntilIdle()
        verify(loanRepository).getLoanById(7L)
        assertTrue(vm.formState.value.isEditing)
    }

    @Test
    fun withoutAnIdStartsANewLoan() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        verify(accountRepository).defaultAccount()
        assertFalse(vm.formState.value.isEditing)
        assertFalse(vm.formState.value.isLoading)
    }

    @Test
    fun deleteFlagsDeletedNotSaved() = runTest {
        val vm = vm(mapOf("id" to 7L))
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        verify(loanRepository).deleteLoan(7L)
        assertTrue(vm.formState.value.deleted)
        assertFalse(vm.formState.value.isSaved)
    }

    @Test
    fun theNavHostDefaultIdStartsANewLoan() = runTest {
        val vm = vm(mapOf("id" to -1L))
        advanceUntilIdle()
        verify(accountRepository).defaultAccount()
        verify(loanRepository, never()).getLoanById(any())
        assertFalse(vm.formState.value.isEditing)
        assertFalse(vm.formState.value.isLoading)
    }

    @Test
    fun hasUnsavedChangesOnlyAfterAnEditOnceLoaded() = runTest {
        val vm = vm(mapOf("id" to 7L))
        backgroundScope.launch { vm.hasUnsavedChanges.collect {} }
        advanceUntilIdle()
        assertFalse(vm.hasUnsavedChanges.value)
        vm.updateName("x")
        advanceUntilIdle()
        assertTrue(vm.hasUnsavedChanges.value)
    }
}
