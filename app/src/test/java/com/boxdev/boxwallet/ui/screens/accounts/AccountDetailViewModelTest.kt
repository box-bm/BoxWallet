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
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.data.repository.TransferRepository
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.ui.navigation.MovementsFilterRequest
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

/** Real repositories over an in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountDetailViewModelTest {

    private val lookup = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String) = if (code == "GTQ") BigDecimal("7.64") else null
        override suspend fun nearest(day: LocalDate, code: String) = exact(day, code)
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var accounts: AccountRepository
    private lateinit var transactions: TransactionRepository
    private lateinit var transfers: TransferRepository
    private var banco = 0L
    private var card = 0L
    private var child = 0L

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        banco = db.accountDao().insert(AccountEntity(name = "Banco", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L))
        card = db.accountDao().insert(
            AccountEntity(
                name = "Visa", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L, sortOrder = 1,
                creditLimitMinor = 10_000_00,
            )
        )
        child = db.accountDao().insert(
            AccountEntity(name = "Visa", type = "CREDIT_CARD", currencyCode = "USD", createdAt = 0L, sortOrder = 1, parentAccountId = card)
        )
        prefs = UserPreferences(context)
        prefs.setMainCurrency("GTQ")
        prefs.setDefaultAccountId(banco)
        val amounts = TransactionAmounts(ExchangeRateResolver(lookup))
        val guard = MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts)
        accounts = AccountRepository(db.accountDao(), prefs, lookup, context, db.transactionDao(), db, amounts, guard)
        transactions = TransactionRepository(db.transactionDao(), accounts, prefs, context, amounts, guard)
        transfers = TransferRepository(db, db.transactionDao(), db.accountDao(), prefs, amounts, guard)
    }

    /** DataStore outlives the test; restore the default main currency. */
    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(id: Long) = AccountDetailViewModel(
        SavedStateHandle(mapOf("accountId" to id)),
        accounts,
        transactions,
        CategoryRepository(db.categoryDao()),
        transfers,
        MovementsFilterRequest(),
    )

    private suspend fun expense(accountId: Long, code: String, minor: Long) = db.transactionDao().insert(
        TransactionEntity(
            accountId = accountId,
            type = TransactionType.EXPENSE.name,
            date = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            originalAmountMinor = minor,
            currencyCode = code,
            accountAmountMinor = minor,
            accountRate = BigDecimal.ONE,
            accountRateSource = RateSource.AUTO,
            amountMinor = minor,
            mainRate = BigDecimal.ONE,
            mainRateSource = RateSource.AUTO,
        )
    )

    @Test
    fun cardDetailIncludesItsSecondCurrency() = runTest {
        val own = expense(card, "GTQ", 500_00)
        val second = expense(child, "USD", 20_00)

        val state = vm(card).uiState.first { !it.isLoading && it.movements.size == 2 && it.card != null }

        assertEquals(setOf(own, second), state.movements.map { it.id }.toSet())
        val summary = state.card!!
        assertEquals(500_00L, summary.primary.usedMinor)
        assertEquals(20_00L, summary.second?.usedMinor)
        // Nobody has paid the card yet: the payment comes from the default account.
        assertEquals(banco, state.cardPayer)
    }

    @Test
    fun sameDayMovementsOfBothCurrenciesListNewestFirst() = runTest {
        val first = expense(card, "GTQ", 1_00)
        val second = expense(child, "USD", 2_00)
        val third = expense(card, "GTQ", 3_00)

        val state = vm(card).uiState.first { !it.isLoading && it.movements.size == 3 }

        assertEquals(listOf(third, second, first), state.movements.map { it.id })
    }

    @Test
    fun deletingACardWhoseSecondCurrencyHasMovementsIsRefused() = runTest {
        expense(child, "USD", 20_00)
        val vm = vm(card)

        vm.requestDelete()

        assertEquals(R.string.account_error_has_movements, vm.message.first { it != null })
        assertFalse(vm.confirmDelete.value)
    }
}
