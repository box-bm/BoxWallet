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
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionFormViewModelTest {

    private val usd = Currency.getInstance("USD")
    private val gtq = Currency.getInstance("GTQ")
    private val eur = Currency.getInstance("EUR")
    private val cash = Account(id = 1, name = "Cash", type = AccountType.CASH, currency = gtq, createdAt = 0)
    private val bank = Account(id = 2, name = "Bank", type = AccountType.CHECKING, currency = usd, createdAt = 0)
    private val archived = Account(id = 9, name = "Old", type = AccountType.CASH, currency = gtq, createdAt = 0, isArchived = true)
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
        whenever(accountRepository.defaultAccount()).thenReturn(cash)
        whenever(accountRepository.observeAll()).thenReturn(flowOf(listOf(cash, bank, archived)))
        whenever(userPreferences.mainCurrency).thenReturn(flowOf(gtq))
        whenever(amounts.compute(any())).thenReturn(AmountResult.NeedsManualRate)
        whenever(amounts.forSave(any(), anyOrNull(), any(), any(), anyOrNull())).thenAnswer { it.arguments[0] as Transaction }
        whenever(transactionRepository.getTransactionById(3L)).thenReturn(
            Transaction.singleCurrency(1, TransactionType.EXPENSE, LocalDateTime.of(2026, 1, 1, 0, 0), Money(5_00, gtq))
                .copy(id = 3),
        )
        whenever(transactionRepository.getTransactionById(4L)).thenReturn(
            Transaction.singleCurrency(2, TransactionType.EXPENSE, LocalDateTime.of(2026, 1, 1, 0, 0), Money(7_00, usd))
                .copy(id = 4),
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

    @Test
    fun openLoadsAnotherMovementIntoTheSameForm() = runTest {
        val vm = vm(emptyMap())
        vm.open(3L)
        advanceUntilIdle()
        assertTrue(vm.formState.value.isEditing)
        assertEquals(3L, vm.formState.value.id)
        assertFalse(vm.formState.value.isLoading)
    }

    @Test
    fun openingTheMovementAlreadyShownKeepsItsEdits() = runTest {
        val vm = vm(emptyMap())
        vm.open(3L)
        advanceUntilIdle()
        vm.updateAmount("12")
        vm.open(3L)
        advanceUntilIdle()
        assertEquals("12", vm.formState.value.amount)
    }

    @Test
    fun openDropsTheSavedFlagOfThePreviousMovementAtOnce() = runTest {
        val vm = vm(mapOf("id" to 3L))
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        assertTrue(vm.formState.value.isSaved)
        vm.open(3L)
        assertFalse(vm.formState.value.isSaved)
    }

    @Test
    fun newMovementStartsOnDefaultAccount() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        val s = vm.formState.value
        assertEquals(1L, s.accountId)
        assertEquals(gtq, s.currency)
        assertEquals(gtq, s.accountCurrency)
        assertFalse(s.currencyTouched)
        assertEquals(listOf(cash, bank), s.accounts)
    }

    @Test
    fun setAccountMovesCurrencyWhenUntouched() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.setAccount(2L)
        advanceUntilIdle()
        val s = vm.formState.value
        assertEquals(2L, s.accountId)
        assertEquals(usd, s.currency)
        assertEquals(usd, s.accountCurrency)
        assertFalse(s.currencyTouched)
    }

    @Test
    fun setAccountKeepsTouchedCurrency() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.updateCurrency(eur)
        vm.setAccount(2L)
        advanceUntilIdle()
        val s = vm.formState.value
        assertTrue(s.currencyTouched)
        assertEquals(eur, s.currency)
        assertEquals(usd, s.accountCurrency)
    }

    @Test
    fun saveUsesSelectedAccount() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.setAccount(2L)
        vm.updateAmount("12")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        val saved = argumentCaptor<Transaction>()
        verify(transactionRepository).insertTransaction(saved.capture())
        assertEquals(2L, saved.firstValue.accountId)
        assertEquals(Money(12_00, usd), saved.firstValue.original)
    }

    @Test
    fun editingKeepsMovementAccount() = runTest {
        val vm = vm(mapOf("id" to 4L))
        advanceUntilIdle()
        assertEquals(2L, vm.formState.value.accountId)
        assertEquals(usd, vm.formState.value.accountCurrency)
        vm.updateDescription("Lunch")
        vm.save()
        advanceUntilIdle()
        val saved = argumentCaptor<Transaction>()
        verify(transactionRepository).updateTransaction(saved.capture())
        assertEquals(2L, saved.firstValue.accountId)
    }

    @Test
    fun changingTheAccountIsAnUnsavedChange() = runTest {
        val vm = vm(emptyMap())
        backgroundScope.launch { vm.hasUnsavedChanges.collect {} }
        advanceUntilIdle()
        vm.setAccount(2L)
        advanceUntilIdle()
        assertTrue(vm.hasUnsavedChanges.value)
    }

    @Test
    fun aDoubleTapOnSaveInsertsOnce() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.updateAmount("12")
        advanceUntilIdle()
        vm.save()
        vm.save()
        advanceUntilIdle()
        verify(transactionRepository).insertTransaction(any())
    }

    private val visa = Account(id = 5, name = "Visa", type = AccountType.CREDIT_CARD, currency = gtq, createdAt = 0)
    private val visaUsd = Account(
        id = 6, name = "Visa", type = AccountType.CREDIT_CARD, currency = usd, createdAt = 0, parentAccountId = 5,
    )

    /** Types an amount, saves, and returns the account the inserted movement went to. */
    private suspend fun TestScope.savedAccountId(vm: TransactionFormViewModel): Long {
        vm.updateAmount("12")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        val saved = argumentCaptor<Transaction>()
        verify(transactionRepository).insertTransaction(saved.capture())
        return saved.firstValue.accountId
    }

    @Test
    fun usdExpenseOnDualCardGoesToChild() = runTest {
        whenever(accountRepository.observeAll()).thenReturn(flowOf(listOf(cash, bank, visa, visaUsd)))
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.setAccount(5L)
        vm.updateCurrency(usd)
        advanceUntilIdle()
        assertEquals(6L, vm.formState.value.accountId)
        assertEquals(usd, vm.formState.value.accountCurrency)
        assertEquals(6L, savedAccountId(vm))
    }

    @Test
    fun gtqExpenseOnDualCardStaysOnParent() = runTest {
        whenever(accountRepository.observeAll()).thenReturn(flowOf(listOf(cash, bank, visa, visaUsd)))
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.setAccount(5L)
        advanceUntilIdle()
        assertEquals(5L, vm.formState.value.accountId)
        assertEquals(5L, savedAccountId(vm))
    }

    @Test
    fun otherCurrencyOnTheChildGoesBackToTheParent() = runTest {
        whenever(accountRepository.observeAll()).thenReturn(flowOf(listOf(cash, bank, visa, visaUsd)))
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.setAccount(6L)
        advanceUntilIdle()
        // Picking the child keeps it: the currency follows it to USD.
        assertEquals(6L, vm.formState.value.accountId)
        assertEquals(usd, vm.formState.value.currency)
        vm.updateCurrency(eur)
        advanceUntilIdle()
        assertEquals(5L, vm.formState.value.accountId)
        assertEquals(gtq, vm.formState.value.accountCurrency)
    }

    @Test
    fun aTouchedUsdCurrencyPickingTheCardLandsOnTheChild() = runTest {
        whenever(accountRepository.observeAll()).thenReturn(flowOf(listOf(cash, bank, visa, visaUsd)))
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.updateCurrency(usd)
        vm.setAccount(5L)
        advanceUntilIdle()
        assertEquals(6L, savedAccountId(vm))
    }
}
