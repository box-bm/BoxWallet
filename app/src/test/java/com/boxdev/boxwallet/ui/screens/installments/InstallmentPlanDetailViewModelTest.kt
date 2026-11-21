package com.boxdev.boxwallet.ui.screens.installments

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.InstallmentPlanRepository
import com.boxdev.boxwallet.data.repository.NewInstallmentPlan
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.util.CurrentDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import com.boxdev.boxwallet.R
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency

/** Real repositories over an in-memory database, on a fixed today. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallmentPlanDetailViewModelTest {

    private val gtq = Currency.getInstance("GTQ")
    private val today = LocalDate.of(2026, 11, 20)
    private val lookup = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String) = if (code == "GTQ") BigDecimal("7.7") else null
        override suspend fun nearest(day: LocalDate, code: String) = exact(day, code)
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var plans: InstallmentPlanRepository
    private var card = 0L
    private var planId = 0L

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        prefs = UserPreferences(context)
        prefs.setMainCurrency("GTQ")
        val resolver = ExchangeRateResolver(lookup) { today }
        val amounts = TransactionAmounts(resolver)
        plans = InstallmentPlanRepository(
            db, db.installmentPlanDao(), db.transactionDao(), db.accountDao(), amounts, resolver,
            MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts), prefs,
            CurrentDay(flowOf(today)),
        )
        card = db.accountDao().insert(
            AccountEntity(
                name = "Visa", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L,
                creditLimitMinor = 1_000_000, statementClosingDay = 15, paymentDueDay = 5,
            )
        )
        // Closings on Oct 15, Nov 15 (charged), Dec 15 (pending): 33333 + 33333 charged, 33334 pending.
        planId = plans.create(NewInstallmentPlan(card, "TV", null, Money(100_000, gtq), 3, LocalDateTime.of(2026, 10, 2, 10, 0)))
    }

    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(repository: InstallmentPlanRepository = plans) = InstallmentPlanDetailViewModel(
        SavedStateHandle(mapOf("planId" to planId)),
        repository,
        CategoryRepository(db.categoryDao()),
        CurrentDay(flowOf(today)),
    )

    @Test
    fun `marks installments dated up to today as charged`() = runTest {
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.first { !it.isLoading && it.installments.size == 3 }

        assertEquals("TV", state.plan?.description)
        assertEquals(listOf(1, 2, 3), state.installments.map { it.installment.installmentNumber })
        assertEquals(listOf(true, true, false), state.installments.map { it.charged })
    }

    @Test
    fun `save propagates description and category`() = runTest {
        val food = db.categoryDao().insert(CategoryEntity(name = "Hogar"))
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading && it.plan != null }

        vm.save("Televisor", food)

        val state = vm.uiState.first { it.plan?.description == "Televisor" && it.installments.all { r -> r.installment.description == "Televisor" } }
        assertEquals(food, state.plan?.categoryId)
        assertTrue(state.installments.all { it.installment.categoryId == food })
        assertEquals(3, state.installments.size)
    }

    @Test
    fun `delete reports the charged amount and the credit freed`() = runTest {
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading && it.installments.size == 3 }

        vm.requestDelete()
        val confirmation = vm.confirmDelete.value!!
        assertEquals(3, confirmation.installments)
        assertEquals(Money(66_666, gtq), confirmation.debtDrop)
        assertEquals(Money(33_334, gtq), confirmation.creditFreed)

        vm.delete()

        assertTrue(vm.deleted.first { it })
        assertNull(plans.observePlan(planId).first())
        assertTrue(db.transactionDao().getByPlanId(planId).isEmpty())
    }

    @Test
    fun `a failed delete shows a message instead of crashing`() = runTest {
        val failing = spy(plans)
        doThrow(IllegalStateException("disk full")).whenever(failing).delete(planId)
        val vm = vm(failing)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.uiState.first { !it.isLoading && it.plan != null }

        vm.delete()

        assertEquals(R.string.installment_plan_error_delete, vm.message.first { it != null })
        assertFalse(vm.deleted.value)
    }

    @Test
    fun `a failed save shows a message instead of crashing`() = runTest {
        val failing = spy(plans)
        doThrow(IllegalStateException("disk full")).whenever(failing).updateDetails(planId, "X", null)
        val vm = vm(failing)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.save("X", null)

        assertEquals(R.string.installment_plan_error_save, vm.message.first { it != null })
    }
}
