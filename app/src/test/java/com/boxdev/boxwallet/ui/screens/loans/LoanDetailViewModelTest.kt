package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.SavedStateHandle
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanPlan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency

@OptIn(ExperimentalCoroutinesApi::class)
class LoanDetailViewModelTest {
    private val usd = Currency.getInstance("USD")
    private val repository = mock<LoanRepository>()
    private val accounts = mock<AccountRepository>()
    private val analytics = mock<AnalyticsHelper>()

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun loan(paid: Long, plan: LoanPlan?) = Loan(
        id = 5, name = "Bank", loanType = LoanType.EXPENSE, total = Money(300_000, usd), accountId = 1,
        date = LocalDateTime.of(2026, 1, 1, 0, 0), plan = plan, paid = Money(paid, usd),
    )

    private val monthly = LoanPlan(3, RecurrenceFrequency.MONTHLY, 1, LocalDate.of(2026, 2, 1))

    private fun viewModel(loan: Loan): LoanDetailViewModel {
        whenever(repository.observe(5)).thenReturn(flowOf(loan))
        whenever(repository.observeMovements(5)).thenReturn(flowOf(emptyList()))
        whenever(accounts.observeAll()).thenReturn(flowOf(emptyList()))
        return LoanDetailViewModel(SavedStateHandle(mapOf("loanId" to 5L)), repository, accounts, analytics)
    }

    @Test
    fun `prefill is the next installment's uncovered part`() = runTest {
        val vm = viewModel(loan(paid = 130_000, plan = monthly))
        val job = backgroundCollect(vm)
        advanceUntilIdle()
        assertEquals(Money(70_000, usd), vm.paymentPrefill())
        job.cancel()
    }

    @Test
    fun `prefill without plan is everything left`() = runTest {
        val vm = viewModel(loan(paid = 50_000, plan = null))
        val job = backgroundCollect(vm)
        advanceUntilIdle()
        assertEquals(Money(250_000, usd), vm.paymentPrefill())
        job.cancel()
    }

    @Test
    fun `mark paid pays the installment`() = runTest {
        val vm = viewModel(loan(paid = 0, plan = monthly))
        val job = backgroundCollect(vm)
        advanceUntilIdle()
        vm.markPaid(1)
        advanceUntilIdle()
        verify(repository).payInstallment(eq(5L), eq(1), any())
        job.cancel()
    }

    @Test
    fun `a refused payment is reported and the sheet stays open`() = runTest {
        val vm = viewModel(loan(paid = 0, plan = null))
        val job = backgroundCollect(vm)
        advanceUntilIdle()
        whenever(repository.addPayment(eq(5L), any(), any())).thenThrow(IllegalArgumentException("too much"))
        vm.openNewPayment()
        vm.savePayment()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.payment)
        vm.dismissError()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        job.cancel()
    }

    private fun kotlinx.coroutines.test.TestScope.backgroundCollect(vm: LoanDetailViewModel) =
        backgroundScope.let { scope ->
            scope.launch(StandardTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }
}
