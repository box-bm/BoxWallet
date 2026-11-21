package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountRepositoryTest {

    private val usd = Currency.getInstance("USD")
    private val noRates = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String): BigDecimal? = null
        override suspend fun nearest(day: LocalDate, code: String): BigDecimal? = null
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var repository: AccountRepository
    private lateinit var prefs: UserPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        prefs = UserPreferences(context)
        val amounts = TransactionAmounts(ExchangeRateResolver(noRates) { LocalDate.of(2026, 11, 7) })
        repository = AccountRepository(
            db.accountDao(), prefs, noRates, context,
            db.transactionDao(), db, amounts,
            MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts),
        )
        runBlocking { prefs.setMainCurrency("USD") }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun move(accountId: Long, type: TransactionType, minor: Long, cleared: Boolean) =
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                type = type.name,
                date = 0L,
                originalAmountMinor = minor,
                currencyCode = "USD",
                accountAmountMinor = minor,
                accountRate = BigDecimal.ONE,
                accountRateSource = RateSource.AUTO,
                amountMinor = minor,
                mainRate = BigDecimal.ONE,
                mainRateSource = RateSource.AUTO,
                isCleared = cleared,
            )
        )

    @Test
    fun balanceCountsClearedInflowsMinusOutflows() = runTest {
        val id = db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L))
        val empty = db.accountDao().insert(AccountEntity(name = "Empty", type = "CASH", currencyCode = "USD", createdAt = 0L, sortOrder = 1))
        move(id, TransactionType.INCOME, 1000, cleared = true)
        move(id, TransactionType.EXPENSE, 300, cleared = true)
        move(id, TransactionType.EXPENSE, 50, cleared = false)
        move(id, TransactionType.ADJUSTMENT_IN, 200, cleared = true)

        val rows = repository.observeAccountsWithBalance().first()
        assertEquals(listOf(id, empty), rows.map { it.account.id })
        assertEquals(Money(900, usd), rows[0].balance)
        assertEquals(Money(0, usd), rows[1].balance)
    }

    private fun account(name: String, currency: String = "USD") =
        Account(name = name, type = AccountType.CASH, currency = Currency.getInstance(currency), createdAt = 0L)

    private suspend fun rows(accountId: Long) =
        db.transactionDao().getAllTransactions().first().filter { it.accountId == accountId }

    private suspend fun makeDefault(id: Long) = prefs.setDefaultAccountId(id)

    @Test
    fun createWithBalanceAddsAdjustmentInAccountCurrency() = runTest {
        val id = repository.create(account("Dollars"), Money(2500, usd))

        val row = rows(id).single()
        assertEquals("ADJUSTMENT_IN", row.type)
        assertEquals(2500L, row.originalAmountMinor)
        assertEquals("USD", row.currencyCode)
        assertEquals(2500L, row.accountAmountMinor)
        assertEquals(2500L, row.amountMinor)
        assertTrue(row.isCleared)
        assertEquals(Money(2500, usd), repository.observeAccountsWithBalance().first().single().balance)
    }

    @Test
    fun createWithNegativeBalanceAddsAdjustmentOut() = runTest {
        val id = repository.create(account("Card"), Money(-4000, usd))

        val row = rows(id).single()
        assertEquals("ADJUSTMENT_OUT", row.type)
        assertEquals(4000L, row.originalAmountMinor)
        assertEquals(Money(-4000, usd), repository.observeAccountsWithBalance().first().single().balance)
    }

    @Test
    fun createWithoutBalanceAddsNoAdjustment() = runTest {
        val a = repository.create(account("Empty"), null)
        val b = repository.create(account("Zero"), Money(0, usd))

        assertTrue(rows(a).isEmpty())
        assertTrue(rows(b).isEmpty())
    }

    @Test
    fun adjustBalanceCreatesDifference() = runTest {
        val id = db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L))
        move(id, TransactionType.INCOME, 1000, cleared = true)
        move(id, TransactionType.EXPENSE, 100, cleared = true)

        repository.adjustBalance(id, Money(1000, usd))
        val adjustment = rows(id).single { it.type.startsWith("ADJUSTMENT") }
        assertEquals("ADJUSTMENT_IN", adjustment.type)
        assertEquals(100L, adjustment.accountAmountMinor)

        repository.adjustBalance(id, Money(1000, usd))
        assertEquals(3, rows(id).size)

        repository.adjustBalance(id, Money(700, usd))
        assertEquals("ADJUSTMENT_OUT", rows(id).first { it.type == "ADJUSTMENT_OUT" }.type)
        assertEquals(Money(700, usd), repository.observeAccountsWithBalance().first().single().balance)
    }

    @Test
    fun currencyLockedOnlyByNonAdjustmentMovements() = runTest {
        val id = repository.create(account("Dollars"), Money(500, usd))
        val stored = db.accountDao().getById(id)!!.toDomain()

        repository.update(stored.copy(currency = Currency.getInstance("EUR")))
        assertEquals("EUR", db.accountDao().getById(id)!!.currencyCode)

        move(id, TransactionType.EXPENSE, 100, cleared = true)
        assertTrue(repository.hasNonAdjustmentMovements(id))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.update(stored.copy(currency = usd)) }
        }
        repository.update(stored.copy(currency = Currency.getInstance("EUR"), name = "Renamed"))
        assertEquals("Renamed", db.accountDao().getById(id)!!.name)
    }

    @Test
    fun deleteRemovesAdjustmentsAtomically() = runTest {
        val keep = repository.create(account("Keep"), null)
        makeDefault(keep)
        val id = repository.create(account("Gone"), Money(900, usd))

        repository.delete(id)

        assertEquals(null, db.accountDao().getById(id))
        assertTrue(rows(id).isEmpty())
    }

    @Test
    fun deleteWithMovementsFails() = runTest {
        val keep = repository.create(account("Keep"), null)
        makeDefault(keep)
        val id = repository.create(account("Used"), Money(900, usd))
        move(id, TransactionType.EXPENSE, 100, cleared = true)

        val error = runCatching { repository.delete(id) }.exceptionOrNull()

        assertEquals(AccountRuleError.HasMovements, error)
        assertEquals(2, rows(id).size)
        assertEquals("Used", db.accountDao().getById(id)!!.name)
    }

    @Test
    fun cannotDeleteOrArchiveDefault() = runTest {
        val id = repository.create(account("Main"), null)
        repository.create(account("Other"), null)
        makeDefault(id)

        assertEquals(AccountRuleError.IsDefault, runCatching { repository.delete(id) }.exceptionOrNull())
        assertEquals(AccountRuleError.IsDefault, runCatching { repository.archive(id, true) }.exceptionOrNull())
        repository.archive(id, false)
        assertFalse(db.accountDao().getById(id)!!.isArchived)
    }

    @Test
    fun cannotDeleteLastAccount() = runTest {
        val id = repository.create(account("Only"), null)
        makeDefault(id + 100)

        assertEquals(AccountRuleError.LastAccount, runCatching { repository.delete(id) }.exceptionOrNull())
    }

    @Test
    fun archiveToggles() = runTest {
        val main = repository.create(account("Main"), null)
        makeDefault(main)
        val id = repository.create(account("Side"), null)

        repository.archive(id, true)
        assertTrue(db.accountDao().getById(id)!!.isArchived)
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.setDefault(id) } }
        repository.archive(id, false)
        assertFalse(db.accountDao().getById(id)!!.isArchived)
    }

    @Test
    fun setDefaultPersistsId() = runTest {
        val id = repository.create(account("Second"), null)

        repository.setDefault(id)

        assertEquals(id, repository.observeDefaultAccountId().first())
    }
}
