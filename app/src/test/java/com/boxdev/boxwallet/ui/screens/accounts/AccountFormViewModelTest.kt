package com.boxdev.boxwallet.ui.screens.accounts

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/** Real repositories over an in-memory database, so a save is checked by the rows it writes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountFormViewModelTest {

    private val usd = Currency.getInstance("USD")

    /** Units per USD. */
    private val lookup = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String) = if (code == "GTQ") BigDecimal("7.64") else null
        override suspend fun nearest(day: LocalDate, code: String) = exact(day, code)
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var accounts: AccountRepository
    private lateinit var transactions: TransactionRepository
    private var card = 0L
    private var child = 0L

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        val banco = db.accountDao().insert(AccountEntity(name = "Banco", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L))
        card = db.accountDao().insert(
            AccountEntity(
                name = "Visa BAC", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L, sortOrder = 1,
                creditLimitMinor = 10_000_00, statementClosingDay = 20, paymentDueDay = 5,
            )
        )
        child = db.accountDao().insert(
            AccountEntity(name = "Visa BAC", type = "CREDIT_CARD", currencyCode = "USD", createdAt = 0L, sortOrder = 1, parentAccountId = card)
        )
        prefs = UserPreferences(context)
        prefs.setMainCurrency("GTQ")
        prefs.setDefaultAccountId(banco)
        val amounts = TransactionAmounts(ExchangeRateResolver(lookup))
        val guard = MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts)
        accounts = AccountRepository(db.accountDao(), prefs, lookup, context, db.transactionDao(), db, amounts, guard)
        transactions = TransactionRepository(db.transactionDao(), accounts, prefs, context, amounts, guard)
    }

    /** DataStore outlives the test; restore the default main currency. */
    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm(id: Long): AccountFormViewModel {
        val vm = AccountFormViewModel(SavedStateHandle(mapOf("id" to id)), accounts, prefs, transactions)
        vm.status.first { !it.isLoading }
        return vm
    }

    private suspend fun AccountFormViewModel.saved() = status.first { it.isSaved || (!it.isSaving && it.error != null) }

    private suspend fun childOf(id: Long) = db.accountDao().getAll().singleOrNull { it.parentAccountId == id }

    @Test
    fun loadsACardWithItsSecondCurrency() = runTest {
        val form = vm(card).form.value

        assertTrue(form.isCard)
        assertEquals("10000.00", form.creditLimit)
        assertEquals("20", form.closingDay)
        assertEquals("5", form.dueDay)
        assertEquals(usd, form.secondCurrency)
        assertTrue(form.hasSecondCurrency)
        assertTrue(form.secondCurrencyRemovable)
    }

    @Test
    fun aSecondCurrencyWithMovementsCantBeRemoved() = runTest {
        db.transactionDao().insert(
            TransactionEntity(
                accountId = child, type = TransactionType.EXPENSE.name, date = 0L,
                originalAmountMinor = 1_00, currencyCode = "USD", accountAmountMinor = 1_00,
                accountRate = BigDecimal.ONE, accountRateSource = RateSource.AUTO,
                amountMinor = 7_64, mainRate = BigDecimal("7.64"), mainRateSource = RateSource.AUTO,
            )
        )
        val vm = vm(card)
        assertFalse(vm.form.value.secondCurrencyRemovable)

        vm.removeSecondCurrency()

        assertEquals(usd, vm.form.value.secondCurrency)
    }

    @Test
    fun turningTheCardOffWithASecondCurrencyAsksToRemoveItFirst() = runTest {
        val vm = vm(card)

        vm.updateIsCard(false)

        assertEquals(R.string.account_error_remove_second_currency_first, vm.status.value.error)
        assertTrue(vm.form.value.isCard)
    }

    @Test
    fun savingKeepsTheStoredSecondCurrency() = runTest {
        val vm = vm(card)
        vm.updateName("Visa Oro")

        vm.save()

        assertTrue(vm.saved().isSaved)
        assertEquals(child, childOf(card)?.id)
        assertEquals("Visa Oro", childOf(card)?.name)
    }

    @Test
    fun removingTheSecondCurrencyThenSavingDeletesIt() = runTest {
        val vm = vm(card)
        vm.removeSecondCurrency()
        vm.updateIsCard(false)

        vm.save()

        assertTrue(vm.saved().isSaved)
        assertNull(childOf(card))
        assertNull(db.accountDao().getById(child))
        assertEquals("CHECKING", db.accountDao().getById(card)!!.type)
    }

    @Test
    fun pickingASecondCurrencyThenSavingAddsIt() = runTest {
        val vm = vm(card)
        vm.removeSecondCurrency()
        vm.save()
        vm.saved()
        val again = vm(card)
        assertFalse(again.form.value.hasSecondCurrency)

        again.updateSecondCurrency(usd)
        again.updateSecondDebt("50")
        again.save()

        assertTrue(again.saved().isSaved)
        val added = childOf(card)!!
        assertEquals("USD", added.currencyCode)
        val opening = db.transactionDao().getAllTransactionsSync().single { it.accountId == added.id }
        assertEquals(TransactionType.ADJUSTMENT_OUT.name, opening.type)
        assertEquals(50_00L, opening.accountAmountMinor)
    }
}
