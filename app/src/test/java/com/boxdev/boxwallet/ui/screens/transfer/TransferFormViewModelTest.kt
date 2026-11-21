package com.boxdev.boxwallet.ui.screens.transfer

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.TransferRepository
import com.boxdev.boxwallet.domain.model.TransferDraft
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/** Real repositories over an in-memory database, so a save is checked by the rows it writes. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransferFormViewModelTest {

    private val gtq = Currency.getInstance("GTQ")
    private val november = LocalDate.of(2026, 11, 5)

    /** Units per USD. */
    private val lookup = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String) = if (code == "GTQ") BigDecimal("7.64") else null
        override suspend fun nearest(day: LocalDate, code: String) = exact(day, code)
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var amounts: TransactionAmounts
    private lateinit var accounts: AccountRepository
    private lateinit var transfers: TransferRepository
    private val analytics = mock<AnalyticsHelper>()
    private var banco = 0L
    private var dolares = 0L

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        banco = db.accountDao().insert(AccountEntity(name = "Banco", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L))
        dolares = db.accountDao().insert(AccountEntity(name = "Dólares", type = "CHECKING", currencyCode = "USD", createdAt = 0L, sortOrder = 1))
        prefs = UserPreferences(context)
        prefs.setMainCurrency("GTQ")
        prefs.setDefaultAccountId(banco)
        amounts = TransactionAmounts(ExchangeRateResolver(lookup) { LocalDate.of(2026, 11, 7) })
        val guard = MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts)
        accounts = AccountRepository(db.accountDao(), prefs, lookup, context, db.transactionDao(), db, amounts, guard)
        transfers = TransferRepository(db, db.transactionDao(), db.accountDao(), prefs, amounts, guard)
    }

    /** DataStore outlives the test; restore the default main currency. */
    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(args: Map<String, Any?>) =
        TransferFormViewModel(SavedStateHandle(args), transfers, accounts, prefs, amounts, analytics)

    private suspend fun TransferFormViewModel.loaded() =
        formState.first { !it.isLoading && it.fromAccountId != null && it.accounts.isNotEmpty() }

    /** Lets every launched save or delete finish; Room runs them on its own threads. */
    private suspend fun TestScope.settle() = repeat(10) {
        advanceUntilIdle()
        withContext(Dispatchers.IO) { Thread.sleep(50) }
    }

    @Test
    fun saveCreatesPair() = runTest {
        val vm = vm(emptyMap())
        vm.loaded()
        vm.updateAmount("771")
        vm.save()
        assertTrue(vm.formState.first { it.isSaved || it.error != null }.isSaved)
        val rows = db.transactionDao().getAllTransactionsSync()
        assertEquals(2, rows.size)
        assertEquals(setOf(TransactionType.TRANSFER_OUT.name, TransactionType.TRANSFER_IN.name), rows.map { it.type }.toSet())
        assertEquals(1, rows.map { it.transferId }.distinct().size)
        assertEquals(banco, rows.single { it.type == TransactionType.TRANSFER_OUT.name }.accountId)
        assertEquals(dolares, rows.single { it.type == TransactionType.TRANSFER_IN.name }.accountId)
    }

    @Test
    fun openByInLegLoadsTransfer() = runTest {
        val id = transfers.create(TransferDraft(banco, dolares, Money(771_00, gtq), november.atTime(10, 0), note = "Ahorro"))
        val inLeg = transfers.get(id)!!.inLegId
        val vm = vm(emptyMap())
        vm.loaded()
        vm.open(inLeg)
        val state = vm.formState.first { it.isEditing && !it.isLoading }
        assertEquals(banco, state.fromAccountId)
        assertEquals(dolares, state.toAccountId)
        assertEquals("771.00", state.amount)
        assertEquals("Ahorro", state.note)
    }

    @Test
    fun prefillFromArguments() = runTest {
        val vm = vm(mapOf("from" to dolares, "to" to banco, "amount" to "250.00"))
        val state = vm.loaded()
        assertEquals(dolares, state.fromAccountId)
        assertEquals(banco, state.toAccountId)
        assertEquals("250.00", state.amount)
    }

    @Test
    fun defaultsToTheDefaultAccountAndTheFirstOtherOne() = runTest {
        val state = vm(mapOf("id" to -1L, "from" to -1L, "to" to -1L)).loaded()
        assertEquals(banco, state.fromAccountId)
        assertEquals(dolares, state.toAccountId)
    }

    @Test
    fun typedRateWithAMissingMainLegShowsTheMissingCurrencyNotNeedsRate() = runTest {
        val euros = db.accountDao().insert(AccountEntity(name = "Euros", type = "CASH", currencyCode = "EUR", createdAt = 0L, sortOrder = 2))
        val vm = vm(mapOf("from" to dolares, "to" to euros, "amount" to "100"))
        vm.loaded()
        vm.formState.first { it.needsManualRate }
        vm.updateManualRate("0.92")

        vm.save()

        val state = vm.formState.first { it.error != null }
        assertEquals(TransferFormError.MissingRate(Currency.getInstance("EUR")), state.error)
        assertEquals(0, db.transactionDao().countAll())
    }

    @Test
    fun aDoubleTapOnSaveCreatesOnePair() = runTest {
        val vm = vm(emptyMap())
        vm.loaded()
        vm.updateAmount("771")
        vm.save()
        vm.save()
        settle()
        assertTrue(vm.formState.value.isSaved)
        assertEquals(2, db.transactionDao().countAll())
    }

    @Test
    fun aDoubleTapOnDeleteDeletesOnce() = runTest {
        val id = transfers.create(TransferDraft(banco, dolares, Money(771_00, gtq), november.atTime(10, 0)))
        val vm = vm(mapOf("id" to transfers.get(id)!!.outLegId))
        vm.formState.first { it.isEditing && !it.isLoading }
        vm.delete()
        vm.delete()
        settle()
        assertTrue(vm.formState.value.isSaved)
        verify(analytics, times(1)).logEvent(eq("transfer_deleted"), any())
    }
}
