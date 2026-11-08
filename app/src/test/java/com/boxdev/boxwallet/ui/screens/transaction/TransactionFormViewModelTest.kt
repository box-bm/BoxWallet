package com.boxdev.boxwallet.ui.screens.transaction

import androidx.lifecycle.SavedStateHandle
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.TransactionAmounts
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
class TransactionFormViewModelTest {

    private val usd = Currency.getInstance("USD")
    private val transactionRepository = mock<TransactionRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val categoryRepository = mock<CategoryRepository>()
    private val userPreferences = mock<UserPreferences>()
    private val amounts = mock<TransactionAmounts>()
    private val analytics = mock<AnalyticsHelper>()

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(categoryRepository.getAllCategories()).thenReturn(flowOf(emptyList()))
        whenever(transactionRepository.recentCurrencies()).thenReturn(flowOf(emptyList()))
        whenever(accountRepository.defaultAccount())
            .thenReturn(Account(id = 1, name = "Cash", type = AccountType.CASH, currency = usd, createdAt = 0))
        whenever(transactionRepository.getTransactionById(3L)).thenReturn(
            Transaction.singleCurrency(1, TransactionType.EXPENSE, LocalDateTime.of(2026, 1, 1, 0, 0), Money(5_00, usd))
                .copy(id = 3),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(args: Map<String, Any?>) = TransactionFormViewModel(
        SavedStateHandle(args), transactionRepository, categoryRepository, accountRepository,
        userPreferences, amounts, analytics,
    )

    @Test
    fun loadsTheMovementNamedByTheIdArgument() = runTest {
        val vm = vm(mapOf("id" to 3L))
        advanceUntilIdle()
        verify(transactionRepository).getTransactionById(3L)
        assertTrue(vm.formState.value.isEditing)
    }

    @Test
    fun withoutAnIdStartsANewMovement() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        verify(accountRepository).defaultAccount()
        assertFalse(vm.formState.value.isEditing)
        assertFalse(vm.formState.value.isLoading)
    }

    @Test
    fun theNavHostDefaultIdStartsANewMovement() = runTest {
        val vm = vm(mapOf("id" to -1L))
        advanceUntilIdle()
        verify(accountRepository).defaultAccount()
        verify(transactionRepository, never()).getTransactionById(any())
        assertFalse(vm.formState.value.isEditing)
        assertFalse(vm.formState.value.isLoading)
    }

    @Test
    fun hasUnsavedChangesOnlyAfterAnEditOnceLoaded() = runTest {
        val vm = vm(emptyMap())
        backgroundScope.launch { vm.hasUnsavedChanges.collect {} }
        advanceUntilIdle()
        assertFalse(vm.hasUnsavedChanges.value)
        vm.updateAmount("12")
        advanceUntilIdle()
        assertTrue(vm.hasUnsavedChanges.value)
    }
}
