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
import com.boxdev.boxwallet.domain.money.MissingRateException
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
import java.time.ZoneOffset
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
        repository = repositoryWith(noRates)
        runBlocking { prefs.setMainCurrency("USD") }
    }

    private fun repositoryWith(lookup: RateLookup): AccountRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val amounts = TransactionAmounts(ExchangeRateResolver(lookup) { LocalDate.of(2026, 11, 7) })
        return AccountRepository(
            db.accountDao(), prefs, lookup, context,
            db.transactionDao(), db, amounts,
            MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun move(accountId: Long, type: TransactionType, minor: Long, cleared: Boolean, date: Long = 0L) =
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                type = type.name,
                date = date,
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
    fun balanceCountsTransferLegs() = runTest {
        val id = db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L))
        move(id, TransactionType.INCOME, 10000, cleared = true)
        move(id, TransactionType.TRANSFER_OUT, 3000, cleared = true)
        move(id, TransactionType.TRANSFER_IN, 500, cleared = true)

        assertEquals(Money(7500, usd), repository.observeAccountsWithBalance().first().single().balance)
    }

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
        val repository = repositoryWith(knownRates)
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

    @Test
    fun foreignCurrencyAccountGetsApproximateMainLeg() = runTest {
        val nearestOnly = object : RateLookup {
            override suspend fun exact(day: LocalDate, code: String): BigDecimal? = null
            override suspend fun nearest(day: LocalDate, code: String): BigDecimal? =
                if (code == "EUR") BigDecimal("0.50") else null
            override suspend fun download(day: LocalDate) = false
        }
        val id = repositoryWith(nearestOnly).create(account("Euros", "EUR"), Money(1000, Currency.getInstance("EUR")))

        val row = rows(id).single()
        assertEquals("EUR", row.currencyCode)
        assertEquals(1000L, row.accountAmountMinor)
        assertEquals(2000L, row.amountMinor)
        assertEquals(RateSource.APPROXIMATE, row.mainRateSource)
    }

    @Test
    fun createRollsBackWhenNoRateExists() = runTest {
        val error = runCatching {
            repository.create(account("Euros", "EUR"), Money(1000, Currency.getInstance("EUR")))
        }.exceptionOrNull()

        assertTrue(error is MissingRateException)
        assertEquals(0, db.accountDao().count())
    }

    private val jpy = Currency.getInstance("JPY")
    private val knownRates = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String): BigDecimal? = when (code) {
            "JPY" -> BigDecimal("150")
            "EUR" -> BigDecimal("0.50")
            else -> null
        }
        override suspend fun nearest(day: LocalDate, code: String): BigDecimal? = exact(day, code)
        override suspend fun download(day: LocalDate) = false
    }

    @Test
    fun currencyChangeRewritesAdjustmentsInNewCurrency() = runTest {
        val repo = repositoryWith(knownRates)
        val id = repo.create(account("Dollars"), Money(10000, usd))
        val stored = db.accountDao().getById(id)!!.toDomain()

        repo.update(stored.copy(currency = jpy))

        val row = rows(id).single()
        assertEquals("ADJUSTMENT_IN", row.type)
        assertEquals("JPY", row.currencyCode)
        assertEquals(100L, row.originalAmountMinor)
        assertEquals(100L, row.accountAmountMinor)
        assertEquals(0, BigDecimal.ONE.compareTo(row.accountRate))
        assertEquals(RateSource.AUTO, row.accountRateSource)
        // ¥100 at 150 JPY per USD = US$0.67 (HALF_EVEN), the main currency being USD.
        assertEquals(67L, row.amountMinor)
        assertEquals(RateSource.AUTO, row.mainRateSource)
        assertEquals(Money(100, jpy), repo.observeAccountsWithBalance().first().single().balance)
    }

    @Test
    fun currencyChangeRollsBackWhenNoRateExists() = runTest {
        val id = repository.create(account("Dollars"), Money(10000, usd))
        val stored = db.accountDao().getById(id)!!.toDomain()

        val error = runCatching { repository.update(stored.copy(currency = jpy, name = "Yen")) }.exceptionOrNull()

        assertTrue(error is MissingRateException)
        val account = db.accountDao().getById(id)!!
        assertEquals("USD", account.currencyCode)
        assertEquals("Dollars", account.name)
        val row = rows(id).single()
        assertEquals("USD", row.currencyCode)
        assertEquals(10000L, row.originalAmountMinor)
        assertEquals(10000L, row.accountAmountMinor)
        assertEquals(10000L, row.amountMinor)
    }

    private class CountingLookup(private val gtqPerUsd: BigDecimal?) : RateLookup {
        var downloads = 0
        override suspend fun exact(day: LocalDate, code: String): BigDecimal? = if (code == "GTQ") gtqPerUsd else null
        override suspend fun nearest(day: LocalDate, code: String): BigDecimal? = exact(day, code)
        override suspend fun download(day: LocalDate): Boolean {
            downloads++
            return true
        }
    }

    private suspend fun gtqAndUsdAccounts() {
        prefs.setMainCurrency("GTQ")
        val quetzales = db.accountDao().insert(AccountEntity(name = "Q", type = "CASH", currencyCode = "GTQ", createdAt = 0L))
        val dollars = db.accountDao().insert(AccountEntity(name = "D", type = "CASH", currencyCode = "USD", createdAt = 0L, sortOrder = 1))
        move(quetzales, TransactionType.INCOME, 10000, cleared = true)
        move(dollars, TransactionType.INCOME, 1000, cleared = true)
    }

    @Test
    fun totalBalanceConvertsOtherCurrenciesWithStoredRates() = runTest {
        val lookup = CountingLookup(BigDecimal("7.80"))
        gtqAndUsdAccounts()

        val total = repositoryWith(lookup).observeTotalBalance().first()

        // Q100.00 + US$10.00 × 7.80 = Q178.00; another currency always marks the total "≈".
        assertEquals(Money(17800, Currency.getInstance("GTQ")), total.amount)
        assertTrue(total.approximate)
        assertEquals(0, lookup.downloads)
    }

    @Test
    fun totalBalanceSkipsAccountsWithoutAnyRate() = runTest {
        val lookup = CountingLookup(null)
        gtqAndUsdAccounts()

        val total = repositoryWith(lookup).observeTotalBalance().first()

        assertEquals(Money(10000, Currency.getInstance("GTQ")), total.amount)
        assertTrue(total.approximate)
        assertEquals(0, lookup.downloads)
    }

    @Test
    fun updateCannotChangeArchivedFlag() = runTest {
        val id = repository.create(account("Side"), null)
        val stored = db.accountDao().getById(id)!!.toDomain()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update(stored.copy(isArchived = true)) }
        }
        assertFalse(db.accountDao().getById(id)!!.isArchived)
    }

    private fun startOfDay(offsetDays: Long) =
        LocalDate.now().plusDays(offsetDays).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun cardBalanceIgnoresClearedButNotFuture() = runTest {
        val card = db.accountDao().insert(AccountEntity(name = "Visa", type = "CREDIT_CARD", currencyCode = "USD", createdAt = 0L))
        val checking = db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L, sortOrder = 1))
        move(card, TransactionType.EXPENSE, 10000, cleared = false, date = startOfDay(0))
        move(card, TransactionType.EXPENSE, 5000, cleared = true, date = startOfDay(1))
        move(card, TransactionType.INCOME, 1000, cleared = false, date = startOfDay(-1))
        // Same rows on a checking account, all uncleared: none counts there.
        move(checking, TransactionType.EXPENSE, 10000, cleared = false, date = startOfDay(0))
        move(checking, TransactionType.EXPENSE, 5000, cleared = false, date = startOfDay(1))
        move(checking, TransactionType.INCOME, 1000, cleared = false, date = startOfDay(-1))

        val rows = repository.observeAccountsWithBalance().first()
        assertEquals(Money(-9000, usd), rows.first { it.account.id == card }.balance)
        assertEquals(Money(0, usd), rows.first { it.account.id == checking }.balance)
    }
}
