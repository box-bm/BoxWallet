package com.boxdev.boxwallet.ui.screens.loans

import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.LoanPlan
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import java.time.LocalDate
import androidx.lifecycle.SavedStateHandle
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.money.MissingRateException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
    private val analytics = mock<AnalyticsHelper>()

    private val eur = Currency.getInstance("EUR")
    private val cash = Account(id = 1, name = "Cash", type = AccountType.CASH, currency = usd, createdAt = 0)
    private val savings = Account(id = 2, name = "Savings", type = AccountType.SAVINGS, currency = usd, createdAt = 0)
    private val euros = Account(id = 3, name = "Euros", type = AccountType.CHECKING, currency = eur, createdAt = 0)
    private val card = Account(id = 4, name = "Card", type = AccountType.CREDIT_CARD, currency = usd, createdAt = 0)
    private val old = Account(id = 5, name = "Old", type = AccountType.CASH, currency = usd, isArchived = true, createdAt = 0)
    private var accounts = listOf(cash, savings, euros, card, old)

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(accountRepository.observeAll()).thenReturn(flowOf(accounts))
        whenever(accountRepository.defaultAccount()).thenReturn(cash)
        whenever(loanRepository.get(7L)).thenReturn(
            Loan(id = 7, name = "Car", loanType = LoanType.EXPENSE, total = Money(100_00, usd), accountId = 1,
                date = LocalDateTime.of(2026, 1, 1, 0, 0),
                plan = LoanPlan(10, RecurrenceFrequency.MONTHLY, 1, LocalDate.of(2026, 3, 15))),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(args: Map<String, Any?>) = LoanFormViewModel(
        SavedStateHandle(args), loanRepository, accountRepository, analytics,
    )

    @Test
    fun loadsTheLoanNamedByTheIdArgument() = runTest {
        val vm = vm(mapOf("id" to 7L))
        advanceUntilIdle()
        verify(loanRepository).get(7L)
        assertTrue(vm.formState.value.isEditing)
    }

    @Test
    fun withoutAnIdStartsANewLoan() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        assertFalse(vm.formState.value.isEditing)
        assertFalse(vm.formState.value.isLoading)
    }

    @Test
    fun `account from the route is preselected`() = runTest {
        val vm = vm(mapOf("accountId" to 2L))
        advanceUntilIdle()
        assertEquals(2L, vm.formState.value.accountId)
    }

    @Test
    fun `a card or archived route account is not preselected`() = runTest {
        assertEquals(1L, vm(mapOf("accountId" to 4L)).also { advanceUntilIdle() }.formState.value.accountId)
        assertEquals(1L, vm(mapOf("accountId" to 5L)).also { advanceUntilIdle() }.formState.value.accountId)
    }

    @Test
    fun `cards and archived accounts are not offered`() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), vm.formState.value.accounts.map { it.id })
    }

    @Test
    fun `when editing, only accounts in the loan currency are offered`() = runTest {
        val vm = vm(mapOf("id" to 7L))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.formState.value.accounts.map { it.id })
    }

    @Test
    fun `editing keeps an archived account the loan already uses`() = runTest {
        whenever(loanRepository.get(8L)).thenReturn(
            Loan(id = 8, name = "Old", loanType = LoanType.EXPENSE, total = Money(100_00, usd), accountId = 5,
                date = LocalDateTime.of(2026, 1, 1, 0, 0)),
        )
        val vm = vm(mapOf("id" to 8L))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 5L), vm.formState.value.accounts.map { it.id })
        assertEquals(5L, vm.formState.value.accountId)
    }

    @Test
    fun `new loan defaults to the default account unless it is a card, then the first usable one`() = runTest {
        whenever(accountRepository.defaultAccount()).thenReturn(savings)
        assertEquals(2L, vm(emptyMap()).also { advanceUntilIdle() }.formState.value.accountId)
        whenever(accountRepository.defaultAccount()).thenReturn(card)
        assertEquals(1L, vm(emptyMap()).also { advanceUntilIdle() }.formState.value.accountId)
    }

    @Test
    fun `turning the plan on defaults the first due to one period after the disbursement`() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.updateDate(LocalDateTime.of(2026, 3, 31, 9, 0))
        vm.setHasPlan(true)
        assertEquals(LocalDate.of(2026, 4, 30), vm.formState.value.firstDueDate)
    }

    @Test
    fun `saving without a plan stores the expected date and no plan`() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.updateName("Ana")
        vm.updateTotalAmount("50")
        vm.updateExpectedDate(LocalDate.of(2027, 1, 1))
        vm.save()
        advanceUntilIdle()
        val captor = argumentCaptor<Loan>()
        verify(loanRepository).create(captor.capture())
        assertNull(captor.firstValue.plan)
        assertEquals(LocalDate.of(2027, 1, 1), captor.firstValue.expectedDate)
        assertEquals(1L, captor.firstValue.accountId)
    }

    @Test
    fun `saving with a plan drops the expected date`() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.updateName("Ana")
        vm.updateTotalAmount("50")
        vm.updateExpectedDate(LocalDate.of(2027, 1, 1))
        vm.setHasPlan(true)
        vm.updateInstallments("5")
        vm.save()
        advanceUntilIdle()
        val captor = argumentCaptor<Loan>()
        verify(loanRepository).create(captor.capture())
        assertEquals(5, captor.firstValue.plan?.installments)
        assertNull(captor.firstValue.expectedDate)
    }

    @Test
    fun `editing carries over the stored first due and expected dates`() = runTest {
        val vm = vm(mapOf("id" to 7L))
        advanceUntilIdle()
        vm.updateName("Car 2")
        vm.save()
        advanceUntilIdle()
        val captor = argumentCaptor<Loan>()
        verify(loanRepository).update(captor.capture())
        assertEquals(LocalDate.of(2026, 3, 15), captor.firstValue.plan?.firstDue)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.savedNew(): LoanFormViewModel {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.updateName("Ana")
        vm.updateTotalAmount("50")
        vm.save()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `a repository refusal becomes a localized form error`() = runTest {
        whenever(loanRepository.create(any())).thenThrow(IllegalArgumentException("nope"))
        val vm = savedNew()
        assertEquals(R.string.loan_save_refused, vm.formState.value.error)
        assertFalse(vm.formState.value.isSaved)
    }

    @Test
    fun `a missing rate becomes the missing rate error`() = runTest {
        whenever(loanRepository.create(any())).thenAnswer { throw MissingRateException(eur, usd) }
        assertEquals(R.string.account_error_missing_rate, savedNew().formState.value.error)
    }

    @Test
    fun `any other failure becomes the generic error`() = runTest {
        whenever(loanRepository.create(any())).thenAnswer { throw IllegalStateException("db") }
        assertEquals(R.string.account_error_generic, savedNew().formState.value.error)
    }

    @Test
    fun `a failed delete becomes a localized error`() = runTest {
        whenever(loanRepository.delete(7L)).thenAnswer { throw IllegalStateException("db") }
        val vm = vm(mapOf("id" to 7L))
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        assertEquals(R.string.loan_delete_failed, vm.formState.value.error)
        assertFalse(vm.formState.value.deleted)
    }

    @Test
    fun `saving with an empty received amount uses the total`() = runTest {
        savedNew()
        val captor = argumentCaptor<Loan>()
        verify(loanRepository).create(captor.capture())
        assertEquals(Money(50_00, usd), captor.firstValue.total)
        assertEquals(Money(50_00, usd), captor.firstValue.disbursed)
    }

    @Test
    fun `saving a received amount apart from the total`() = runTest {
        val vm = vm(emptyMap())
        advanceUntilIdle()
        vm.updateName("Banco")
        vm.updateTotalAmount("11000")
        vm.updateDisbursedAmount("10000")
        assertTrue(vm.formState.value.isValid)
        vm.updateDisbursedAmount("0")
        assertFalse(vm.formState.value.isValid)
        vm.updateDisbursedAmount("10000")
        vm.save()
        advanceUntilIdle()
        val captor = argumentCaptor<Loan>()
        verify(loanRepository).create(captor.capture())
        assertEquals(Money(1_100_000, usd), captor.firstValue.total)
        assertEquals(Money(1_000_000, usd), captor.firstValue.disbursed)
    }

    @Test
    fun `editing shows the received amount only when it differs from the total`() = runTest {
        assertEquals("", vm(mapOf("id" to 7L)).also { advanceUntilIdle() }.formState.value.disbursedAmount)
        whenever(loanRepository.get(9L)).thenReturn(
            Loan(id = 9, name = "Bank", loanType = LoanType.EXPENSE, total = Money(110_00, usd),
                disbursed = Money(100_00, usd), accountId = 1, date = LocalDateTime.of(2026, 1, 1, 0, 0)),
        )
        val vm = vm(mapOf("id" to 9L))
        backgroundScope.launch { vm.hasUnsavedChanges.collect {} }
        advanceUntilIdle()
        assertEquals("100.00", vm.formState.value.disbursedAmount)
        assertFalse(vm.hasUnsavedChanges.value)
        vm.updateDisbursedAmount("90")
        advanceUntilIdle()
        assertTrue(vm.hasUnsavedChanges.value)
    }

    @Test
    fun `direction is locked once the loan has payments`() = runTest {
        assertFalse(vm(mapOf("id" to 7L)).also { advanceUntilIdle() }.formState.value.directionLocked)
        whenever(loanRepository.get(10L)).thenReturn(
            Loan(id = 10, name = "Paid", loanType = LoanType.EXPENSE, total = Money(100_00, usd), accountId = 1,
                date = LocalDateTime.of(2026, 1, 1, 0, 0), paid = Money(10_00, usd)),
        )
        val vm = vm(mapOf("id" to 10L))
        advanceUntilIdle()
        assertTrue(vm.formState.value.directionLocked)
        vm.updateLoanType(LoanType.INCOME)
        assertEquals(LoanType.EXPENSE, vm.formState.value.loanType)
        assertFalse(vm(emptyMap()).also { advanceUntilIdle() }.formState.value.directionLocked)
    }

    @Test
    fun deleteFlagsDeletedNotSaved() = runTest {
        val vm = vm(mapOf("id" to 7L))
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        verify(loanRepository).delete(7L)
        assertTrue(vm.formState.value.deleted)
        assertFalse(vm.formState.value.isSaved)
    }

    @Test
    fun theNavHostDefaultIdStartsANewLoan() = runTest {
        val vm = vm(mapOf("id" to -1L))
        advanceUntilIdle()
        verify(loanRepository, never()).get(any())
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
