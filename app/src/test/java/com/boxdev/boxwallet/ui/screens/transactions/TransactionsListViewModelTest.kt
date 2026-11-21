package com.boxdev.boxwallet.ui.screens.transactions

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.ui.navigation.MovementsFilterRequest
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsListViewModelTest {

    private val usd = Currency.getInstance("USD")
    private val repository = mock<TransactionRepository>()
    private val categoryRepository = mock<CategoryRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val filterRequest = MovementsFilterRequest()
    private val cash = Account(id = 1, name = "Cash", type = AccountType.CASH, currency = usd, createdAt = 0)
    private val bank = Account(id = 2, name = "Bank", type = AccountType.CHECKING, currency = usd, createdAt = 0)

    private fun movement(id: Long, accountId: Long) =
        Transaction.singleCurrency(accountId, TransactionType.EXPENSE, LocalDateTime.of(2026, 1, id.toInt(), 0, 0), Money(1_00, usd))
            .copy(id = id)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(repository.getAllTransactions())
            .thenReturn(flowOf(listOf(movement(1, 1), movement(2, 2), movement(3, 1), movement(4, 2))))
        whenever(categoryRepository.getAllCategories()).thenReturn(flowOf(emptyList()))
        whenever(accountRepository.observeAll()).thenReturn(flowOf(listOf(cash, bank)))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = TransactionsListViewModel(repository, categoryRepository, accountRepository, filterRequest)

    @Test
    fun accountFilterKeepsOnlyThatAccountsMovements() = runTest {
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.updateAccountFilter(2L)
        advanceUntilIdle()
        assertEquals(listOf(4L, 2L), vm.uiState.value.filteredTransactions.map { it.id })
    }

    @Test
    fun accountRequestedByAnotherScreenFiltersTheList() = runTest {
        filterRequest.requestAccount(1L)
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.filterState.accountId)
        assertEquals(listOf(3L, 1L), vm.uiState.value.filteredTransactions.map { it.id })
        assertNull(filterRequest.filter.value)
    }

    @Test
    fun clearingTheAccountFilterShowsEveryMovement() = runTest {
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.updateAccountFilter(2L)
        vm.updateAccountFilter(null)
        advanceUntilIdle()
        assertEquals(4, vm.uiState.value.filteredTransactions.size)
    }

    @Test
    fun exposesTheAccountsForTheFilter() = runTest {
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(cash, bank), vm.uiState.value.accounts)
    }
}
