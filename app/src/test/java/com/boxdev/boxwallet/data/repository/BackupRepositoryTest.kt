package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.AppMetaEntity
import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.data.local.entity.InstallmentPlanEntity
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.domain.model.BackupData
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanPlan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
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
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var lock: CurrencyChangeLock
    private lateinit var repo: BackupRepository
    private val gtq = Currency.getInstance("GTQ")
    private val usd = Currency.getInstance("USD")

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        prefs = UserPreferences(context)
        prefs.setMainCurrency("USD")
        lock = CurrencyChangeLock()
        repo = BackupRepository(
            context, db, db.transactionDao(), db.categoryDao(), db.loanDao(), db.installmentPlanDao(), db.accountDao(), db.appMetaDao(), prefs, lock,
            GoogleDriveManager(context),
        )
    }

    /** DataStore outlives the test; leave it as other tests expect it (main USD). */
    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
    }

    private suspend fun seedLocalData() {
        val acc = db.accountDao().insert(AccountEntity(name = "Old", type = "CASH", currencyCode = "USD", createdAt = 0L))
        db.categoryDao().insert(CategoryEntity(id = 50, name = "Old category"))
        db.transactionDao().insert(tx(id = 99, accountId = acc, amount = 4200, code = "USD"))
    }

    private fun tx(id: Long, accountId: Long, amount: Long, code: String, parent: Long? = null, categoryId: Long? = null) =
        TransactionEntity(
            id = id, accountId = accountId, type = "EXPENSE", date = 0L,
            categoryId = categoryId,
            originalAmountMinor = amount, currencyCode = code,
            accountAmountMinor = amount, accountRate = BigDecimal.ONE, accountRateSource = RateSource.AUTO,
            amountMinor = amount, mainRate = BigDecimal.ONE, mainRateSource = RateSource.AUTO,
            parentRecurrenceId = parent,
        )

    @Test
    fun `snapshot round-trips, accounts and currencies included`() = runTest {
        prefs.setMainCurrency("GTQ")
        val main = db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 1L))
        val savings = db.accountDao().insert(AccountEntity(name = "USD savings", type = "SAVINGS", currencyCode = "USD", createdAt = 2L, sortOrder = 1))
        db.categoryDao().insert(CategoryEntity(id = 51, name = "Teal one", color = "TEAL"))
        db.transactionDao().insert(
            tx(id = 10, accountId = savings, amount = 1000, code = "USD").copy(
                amountMinor = 7712, mainRate = BigDecimal("7.712345678901"), mainRateSource = RateSource.MANUAL,
            )
        )
        val json = Gson().toJson(repo.snapshot())
        assertEquals(7, JsonParser.parseString(json).asJsonObject.get("version").asInt)
        db.transactionDao().deleteAll()
        db.accountDao().deleteAll()

        val result = repo.restoreJson(json, gtq)

        assertTrue(result is RestoreResult.Restored)
        assertEquals(listOf(main to "GTQ", savings to "USD"), db.accountDao().getAll().map { it.id to it.currencyCode })
        val row = db.transactionDao().getAllTransactionsSync().single()
        assertEquals(savings, row.accountId)
        assertEquals(7712L, row.amountMinor)
        assertEquals(BigDecimal("7.712345678901"), row.mainRate)
        assertEquals(RateSource.MANUAL, row.mainRateSource)
        assertEquals(main, prefs.defaultAccountId.first())
        assertEquals("TEAL", db.categoryDao().getAllCategoriesSync().first { it.id == 51L }.color)
    }

    @Test
    fun `a category without color in the file restores as DEFAULT`() = runTest {
        val root = JsonParser.parseString(Gson().toJson(repo.snapshot().copy(categories = listOf(CategoryEntity(id = 60, name = "Old file"))))).asJsonObject
        root.getAsJsonArray("categories").forEach { it.asJsonObject.remove("color") }

        val result = repo.restoreJson(root.toString(), usd)

        assertTrue(result is RestoreResult.Restored)
        assertEquals("DEFAULT", db.categoryDao().getAllCategoriesSync().single { it.id == 60L }.color)
    }

    @Test
    fun transferPairRoundTrips() = runTest {
        val gtqAcc = db.accountDao().insert(AccountEntity(name = "GTQ", type = "CHECKING", currencyCode = "GTQ", createdAt = 1L))
        val usdAcc = db.accountDao().insert(AccountEntity(name = "USD", type = "SAVINGS", currencyCode = "USD", createdAt = 2L, sortOrder = 1))
        db.transactionDao().insert(
            tx(id = 10, accountId = gtqAcc, amount = 8000, code = "GTQ").copy(type = "TRANSFER_OUT", transferId = "t1")
        )
        db.transactionDao().insert(
            tx(id = 11, accountId = usdAcc, amount = 8000, code = "GTQ")
                .copy(type = "TRANSFER_IN", transferId = "t1", accountAmountMinor = 1000, accountRate = BigDecimal("0.125"))
        )
        val json = Gson().toJson(repo.snapshot())
        db.transactionDao().deleteAll()
        db.accountDao().deleteAll()

        assertTrue(repo.restoreJson(json, usd) is RestoreResult.Restored)

        val rows = db.transactionDao().getAllTransactionsSync().sortedBy { it.id }
        assertEquals(listOf("TRANSFER_OUT", "TRANSFER_IN"), rows.map { it.type })
        assertEquals(listOf("t1", "t1"), rows.map { it.transferId })
        assertEquals(listOf(8000L, 8000L), rows.map { it.originalAmountMinor })
        assertEquals(listOf(8000L, 1000L), rows.map { it.accountAmountMinor })
    }

    @Test
    fun loneTransferLegBecomesAdjustment() = runTest {
        val acc = db.accountDao().insert(AccountEntity(name = "USD", type = "CHECKING", currencyCode = "USD", createdAt = 1L))
        db.transactionDao().insert(tx(id = 10, accountId = acc, amount = 500, code = "USD").copy(type = "TRANSFER_OUT", transferId = "a"))
        db.transactionDao().insert(tx(id = 11, accountId = acc, amount = 700, code = "USD").copy(type = "TRANSFER_IN", transferId = "b"))
        val json = Gson().toJson(repo.snapshot())
        db.transactionDao().deleteAll()
        db.accountDao().deleteAll()

        assertTrue(repo.restoreJson(json, usd) is RestoreResult.Restored)

        val rows = db.transactionDao().getAllTransactionsSync().sortedBy { it.id }
        assertEquals(listOf("ADJUSTMENT_OUT", "ADJUSTMENT_IN"), rows.map { it.type })
        assertEquals(listOf(null, null), rows.map { it.transferId })
        assertEquals(listOf(500L, 700L), rows.map { it.amountMinor })
    }

    @Test
    fun strayTransferIdOnAnotherMovementIsDropped() = runTest {
        val acc = db.accountDao().insert(AccountEntity(name = "USD", type = "CHECKING", currencyCode = "USD", createdAt = 1L))
        db.transactionDao().insert(tx(id = 10, accountId = acc, amount = 500, code = "USD").copy(type = "EXPENSE", transferId = "x"))
        db.transactionDao().insert(tx(id = 11, accountId = acc, amount = 700, code = "USD").copy(type = "TRANSFER_OUT", transferId = "x"))
        val json = Gson().toJson(repo.snapshot())
        db.transactionDao().deleteAll()
        db.accountDao().deleteAll()

        assertTrue(repo.restoreJson(json, usd) is RestoreResult.Restored)

        val rows = db.transactionDao().getAllTransactionsSync().sortedBy { it.id }
        assertEquals(listOf("EXPENSE", "ADJUSTMENT_OUT"), rows.map { it.type })
        assertEquals(listOf(null, null), rows.map { it.transferId })
    }

    @Test
    fun `a backup with another main currency asks and writes nothing`() = runTest {
        seedLocalData()
        val backup = BackupData(
            mainCurrencyCode = "GTQ",
            accounts = listOf(AccountEntity(id = 1, name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L)),
            transactions = listOf(tx(id = 1, accountId = 1, amount = 500, code = "GTQ")),
        )

        val result = repo.restoreJson(Gson().toJson(backup), usd)

        result as RestoreResult.CurrencyMismatch
        assertEquals(gtq, result.backup)
        assertEquals(usd, result.device)
        assertEquals(99L, db.transactionDao().getAllTransactionsSync().single().id)
        assertEquals(usd, prefs.mainCurrency.first())

        // Keeping the backup's currency: restore it and adopt GTQ.
        repo.restore(result.data)
        assertEquals(gtq, prefs.mainCurrency.first())
        assertEquals(1L, db.transactionDao().getAllTransactionsSync().single().id)
        assertEquals("GTQ", db.accountDao().getAll().single().currencyCode)
    }

    @Test
    fun `dangling references are cleared and recurrence parents go first`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(AccountEntity(id = 1, name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L)),
            // The occurrence comes before its parent and points to a category the backup lacks.
            transactions = listOf(
                tx(id = 1, accountId = 1, amount = 1, code = "USD", parent = 2, categoryId = 77),
                tx(id = 2, accountId = 1, amount = 2, code = "USD"),
                tx(id = 3, accountId = 1, amount = 3, code = "USD", parent = 404),
            ),
        )

        repo.restore(backup)

        val rows = db.transactionDao().getAllTransactionsSync().associateBy { it.id }
        assertEquals(2L, rows.getValue(1).parentRecurrenceId)
        assertNull(rows.getValue(1).categoryId)
        assertNull(rows.getValue(3).parentRecurrenceId)
    }

    @Test
    fun `movements of an account missing from the backup go to an existing account`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(
                AccountEntity(id = 1, name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L),
                AccountEntity(id = 2, name = "Quetzales", type = "CASH", currencyCode = "GTQ", createdAt = 0L),
            ),
            transactions = listOf(
                tx(id = 1, accountId = 404, amount = 1, code = "USD"),
                tx(id = 2, accountId = 404, amount = 2, code = "GTQ"),
                tx(id = 3, accountId = 2, amount = 3, code = "GTQ"),
            ),
        )

        repo.restore(backup)

        val rows = db.transactionDao().getAllTransactionsSync().associateBy { it.id }
        assertEquals(1L, rows.getValue(1).accountId)
        assertEquals(2L, rows.getValue(2).accountId) // the account in the movement's own currency
        assertEquals(2L, rows.getValue(3).accountId)
    }

    @Test
    fun `a loan whose account is missing is dropped with its movements`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(AccountEntity(id = 1, name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L)),
            loans = listOf(
                LoanEntity(id = 1, name = "Kept", totalAmountMinor = 1, currencyCode = "USD", accountId = 1, startDate = 0L),
                LoanEntity(id = 2, name = "Lost", totalAmountMinor = 1, currencyCode = "USD", accountId = 404, startDate = 0L),
            ),
            transactions = listOf(
                tx(id = 1, accountId = 1, amount = 1, code = "USD").copy(type = "LOAN_OUT", loanId = 1),
                tx(id = 2, accountId = 404, amount = 1, code = "USD").copy(type = "LOAN_OUT", loanId = 2),
                tx(id = 3, accountId = 1, amount = 1, code = "USD").copy(type = "LOAN_IN", loanId = 2),
                tx(id = 4, accountId = 1, amount = 1, code = "USD"),
            ),
        )

        repo.restore(backup)

        assertEquals(listOf(1L), db.loanDao().getAllLoansSync().map { it.id })
        assertEquals(listOf(1L, 4L), db.transactionDao().getAllTransactionsSync().map { it.id }.sorted())
    }

    @Test
    fun `a loan movement goes to its loan's account`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(
                AccountEntity(id = 1, name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L),
                AccountEntity(id = 2, name = "Other", type = "CASH", currencyCode = "USD", createdAt = 0L, sortOrder = 1),
            ),
            loans = listOf(LoanEntity(id = 1, name = "Juan", totalAmountMinor = 1, currencyCode = "USD", accountId = 1, startDate = 0L)),
            transactions = listOf(
                tx(id = 1, accountId = 1, amount = 1, code = "USD").copy(type = "LOAN_OUT", loanId = 1),
                tx(id = 2, accountId = 2, amount = 1, code = "USD").copy(type = "LOAN_IN", loanId = 1),
                tx(id = 3, accountId = 404, amount = 1, code = "USD").copy(type = "LOAN_IN", loanId = 1),
            ),
        )

        repo.restore(backup)

        assertEquals(listOf(1L, 1L, 1L), db.transactionDao().getAllTransactionsSync().sortedBy { it.id }.map { it.accountId })
    }

    @Test
    fun `backup round-trips a loan with its payments`() = runTest {
        val acc = db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L))
        val noRates = object : RateLookup {
            override suspend fun exact(day: LocalDate, code: String): BigDecimal? = null
            override suspend fun nearest(day: LocalDate, code: String): BigDecimal? = null
            override suspend fun download(day: LocalDate) = false
        }
        val amounts = TransactionAmounts(ExchangeRateResolver(noRates) { LocalDate.of(2026, 11, 21) })
        val loans = LoanRepository(
            db, db.loanDao(), db.transactionDao(), db.accountDao(), prefs, amounts,
            MovementWriteGuard(lock, prefs, db.accountDao(), amounts),
        )
        val usdMoney = { major: Long -> Money(major * 100, usd) }
        val id = loans.create(
            Loan(
                name = "Juan", loanType = LoanType.INCOME, total = usdMoney(1100), disbursed = usdMoney(1000), accountId = acc,
                date = LocalDateTime.of(2026, 11, 5, 10, 0),
                plan = LoanPlan(4, RecurrenceFrequency.MONTHLY, 1, LocalDate.of(2026, 12, 5)),
                expectedDate = LocalDate.of(2027, 3, 5),
            )
        )
        loans.addPayment(id, usdMoney(250), LocalDateTime.of(2026, 11, 10, 9, 30))
        loans.addPayment(id, usdMoney(100), LocalDateTime.of(2026, 11, 12, 9, 30))
        val loanBefore = loans.observe(id).first()
        val movementsBefore = db.transactionDao().getAllTransactionsSync().sortedBy { it.id }
        val json = Gson().toJson(repo.snapshot())
        db.transactionDao().deleteAll()
        db.loanDao().deleteAll()

        assertTrue(repo.restoreJson(json, usd) is RestoreResult.Restored)

        assertEquals(loanBefore, loans.observe(id).first())
        assertEquals(usdMoney(1000), loans.observe(id).first()!!.disbursed)
        assertEquals(usdMoney(1100), loans.observe(id).first()!!.total)
        assertEquals(3, movementsBefore.size)
        assertEquals(movementsBefore, db.transactionDao().getAllTransactionsSync().sortedBy { it.id })
    }

    @Test
    fun roundTripKeepsAccountCustomizationAndDefault() = runTest {
        prefs.setMainCurrency("GTQ")
        val first = db.accountDao().insert(
            AccountEntity(
                name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 1L,
                description = "Nómina", icon = "Wallet", color = "BLUE",
            )
        )
        val second = db.accountDao().insert(
            AccountEntity(
                name = "Savings", type = "SAVINGS", currencyCode = "USD", createdAt = 2L, sortOrder = 1,
                description = "Ahorro", icon = "Savings", color = "GREEN", includeInTotal = false,
            )
        )
        prefs.setDefaultAccountId(second)
        val before = db.accountDao().getAll()
        val json = Gson().toJson(repo.snapshot())
        assertEquals(second, JsonParser.parseString(json).asJsonObject.get("defaultAccountId").asLong)
        db.accountDao().deleteAll()
        prefs.setDefaultAccountId(first)

        val result = repo.restoreJson(json, gtq)

        result as RestoreResult.Restored
        assertEquals(before, db.accountDao().getAll())
        assertEquals(second, result.data.defaultAccountId)
        assertEquals(second, prefs.defaultAccountId.first())
    }

    @Test
    fun childWithMissingParentBecomesPlainCard() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(
                AccountEntity(id = 1, name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L),
                AccountEntity(id = 2, name = "Visa", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L, parentAccountId = 99),
            ),
        )

        repo.restore(backup)

        assertNull(db.accountDao().getById(2)!!.parentAccountId)
    }

    @Test
    fun secondChildIsDetached() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(
                AccountEntity(id = 1, name = "Visa", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L),
                AccountEntity(id = 2, name = "Visa", type = "CREDIT_CARD", currencyCode = "USD", createdAt = 0L, parentAccountId = 1),
                AccountEntity(id = 3, name = "Visa", type = "CREDIT_CARD", currencyCode = "EUR", createdAt = 0L, parentAccountId = 1),
            ),
        )

        repo.restore(backup)

        assertEquals(1L, db.accountDao().getById(2)!!.parentAccountId)
        assertNull(db.accountDao().getById(3)!!.parentAccountId)
    }

    @Test
    fun invalidChildrenAreDetachedAndKeptOnesFollowTheirCard() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(
                AccountEntity(id = 1, name = "Main", type = "CHECKING", currencyCode = "USD", createdAt = 0L),
                // Parent isn't a card.
                AccountEntity(id = 2, name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L, parentAccountId = 1),
                AccountEntity(
                    id = 3, name = "Visa", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L,
                    isArchived = true, includeInTotal = false,
                ),
                // Child isn't a card.
                AccountEntity(id = 4, name = "Visa", type = "CASH", currencyCode = "USD", createdAt = 0L, parentAccountId = 3),
                // Same currency as its card.
                AccountEntity(id = 5, name = "Visa", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L, parentAccountId = 3),
                // The valid one, with flags out of step with its card.
                AccountEntity(id = 6, name = "Visa", type = "CREDIT_CARD", currencyCode = "USD", createdAt = 0L, parentAccountId = 3),
            ),
        )

        repo.restore(backup)

        listOf(2L, 4L, 5L).forEach { assertNull("account $it", db.accountDao().getById(it)!!.parentAccountId) }
        val kept = db.accountDao().getById(6)!!
        assertEquals(3L, kept.parentAccountId)
        assertTrue(kept.isArchived)
        assertFalse(kept.includeInTotal)
    }

    @Test
    fun `a backup default missing from its accounts falls back to the first account`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            defaultAccountId = 404,
            accounts = listOf(
                AccountEntity(id = 2, name = "B", type = "CASH", currencyCode = "USD", createdAt = 0L, sortOrder = 1),
                AccountEntity(id = 1, name = "A", type = "CASH", currencyCode = "USD", createdAt = 0L),
            ),
        )

        repo.restore(backup)

        assertEquals(1L, prefs.defaultAccountId.first())
    }

    @Test
    fun orphanMovementGoesToAccountOfItsAccountCurrency() = runTest {
        // The backup's default (GTQ) is not its first account, so the fallback is the default, not the first.
        val backup = BackupData(
            mainCurrencyCode = "GTQ",
            defaultAccountId = 1,
            accounts = listOf(
                AccountEntity(id = 2, name = "Dollars", type = "SAVINGS", currencyCode = "USD", createdAt = 0L),
                AccountEntity(id = 1, name = "Quetzales", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L, sortOrder = 1),
            ),
            transactions = listOf(
                // Rate 1 and same amounts: its account was in USD.
                tx(id = 1, accountId = 99, amount = 1000, code = "USD"),
                // Converted at 7.71: its account was in another currency, unknown → the default account.
                tx(id = 2, accountId = 99, amount = 1000, code = "EUR").copy(
                    accountAmountMinor = 7710, accountRate = BigDecimal("7.71"), amountMinor = 7710,
                ),
            ),
        )

        repo.restore(backup)

        val rows = db.transactionDao().getAllTransactionsSync().associateBy { it.id }
        assertEquals(2L, rows.getValue(1).accountId)
        assertEquals(1L, rows.getValue(2).accountId)
        assertEquals(1L, prefs.defaultAccountId.first())
    }

    @Test
    fun `a failing restore rolls back and keeps local data`() = runTest {
        seedLocalData()
        val broken = BackupData(
            mainCurrencyCode = "USD",
            accounts = emptyList(),
            transactions = listOf(tx(id = 1, accountId = 5, amount = 1, code = "USD")), // no such account
        )

        val failed = runCatching { repo.restore(broken) }.isFailure

        assertTrue(failed)
        assertEquals(99L, db.transactionDao().getAllTransactionsSync().single().id)
        assertEquals("Old", db.accountDao().getAll().single().name)
        assertFalse(lock.mutex.isLocked)
    }

    @Test
    fun `local snapshot is written in the v7 shape with accounts`() = runTest {
        seedLocalData()
        val file: File = repo.writeLocalSnapshot()
        val root = JsonParser.parseString(file.readText()).asJsonObject
        assertEquals(7, root.get("version").asInt)
        assertEquals("USD", root.get("mainCurrencyCode").asString)
        assertEquals("Old", root.getAsJsonArray("accounts")[0].asJsonObject.get("name").asString)
    }

    @Test
    fun `restore applies its main currency and default account and clears the marks`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "GTQ", defaultAccountId = 1,
            accounts = listOf(AccountEntity(id = 1, name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L)),
        )
        repo.restoreJson(Gson().toJson(backup), gtq)

        assertNull(db.appMetaDao().get(BackupRepository.META_MAIN_CURRENCY))
        assertNull(db.appMetaDao().get(BackupRepository.META_DEFAULT_ACCOUNT_ID))
        assertEquals(gtq, prefs.mainCurrency.first())
        assertEquals(db.accountDao().getAll().single().id, prefs.defaultAccountId.first())
    }

    @Test
    fun `a restore committed before a crash is finished at startup`() = runTest {
        // What the restore transaction leaves when the process dies before the DataStore writes.
        val acc = db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L))
        db.appMetaDao().put(AppMetaEntity(BackupRepository.META_MAIN_CURRENCY, "GTQ"))
        db.appMetaDao().put(AppMetaEntity(BackupRepository.META_DEFAULT_ACCOUNT_ID, acc.toString()))
        prefs.setDefaultAccountId(12345)
        assertEquals(usd, prefs.mainCurrency.first())

        repo.completePendingRestore()

        assertEquals(gtq, prefs.mainCurrency.first())
        assertEquals(acc, prefs.defaultAccountId.first())
        assertNull(db.appMetaDao().get(BackupRepository.META_MAIN_CURRENCY))
        assertNull(db.appMetaDao().get(BackupRepository.META_DEFAULT_ACCOUNT_ID))
    }

    @Test
    fun `the default category round-trips through a backup`() = runTest {
        db.categoryDao().insert(CategoryEntity(id = 51, name = "A"))
        db.categoryDao().insert(CategoryEntity(id = 52, name = "B"))
        prefs.setDefaultCategory(52)
        val json = Gson().toJson(repo.snapshot())
        assertEquals(52L, JsonParser.parseString(json).asJsonObject.get("defaultCategoryId").asLong)
        prefs.setDefaultCategory(null)

        val result = repo.restoreJson(json, usd)

        assertTrue(result is RestoreResult.Restored)
        assertEquals(52L, prefs.defaultCategoryId.first())
        assertNull(db.appMetaDao().get(BackupRepository.META_DEFAULT_CATEGORY_ID))
        prefs.setDefaultCategory(null)
    }

    @Test
    fun `a backup without a default category clears the device one`() = runTest {
        prefs.setDefaultCategory(3)
        val backup = BackupData(mainCurrencyCode = "USD", categories = listOf(CategoryEntity(id = 3, name = "Other")))

        repo.restoreJson(Gson().toJson(backup), usd)

        assertNull(prefs.defaultCategoryId.first())
    }

    @Test
    fun `a backup default category missing from its categories gives none`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD", defaultCategoryId = 404,
            categories = listOf(CategoryEntity(id = 3, name = "Other")),
        )

        val result = repo.restoreJson(Gson().toJson(backup), usd) as RestoreResult.Restored

        assertNull(result.data.defaultCategoryId)
        assertNull(prefs.defaultCategoryId.first())
    }

    @Test
    fun `startup finishes the default category of a restore committed before a crash`() = runTest {
        prefs.setDefaultCategory(1)
        db.appMetaDao().put(AppMetaEntity(BackupRepository.META_DEFAULT_CATEGORY_ID, "7"))
        repo.completePendingRestore()
        assertEquals(7L, prefs.defaultCategoryId.first())
        assertNull(db.appMetaDao().get(BackupRepository.META_DEFAULT_CATEGORY_ID))

        db.appMetaDao().put(AppMetaEntity(BackupRepository.META_DEFAULT_CATEGORY_ID, ""))
        repo.completePendingRestore()
        assertNull(prefs.defaultCategoryId.first())
        assertNull(db.appMetaDao().get(BackupRepository.META_DEFAULT_CATEGORY_ID))
    }

    @Test
    fun `without marks the startup step changes nothing`() = runTest {
        prefs.setDefaultAccountId(42)
        repo.completePendingRestore()
        assertEquals(usd, prefs.mainCurrency.first())
        assertEquals(42L, prefs.defaultAccountId.first())
    }

    @Test
    fun `the Drive snapshot waits for a running currency change`() = runTest {
        seedLocalData()
        lock.mutex.lock()
        val snapshot = async { repo.consistentSnapshot() }
        runCurrent()
        yield()
        assertFalse(snapshot.isCompleted)
        lock.mutex.unlock()

        val data = snapshot.await()
        assertEquals("USD", data.mainCurrencyCode)
        assertEquals(1, data.accounts.size)
        assertFalse(lock.mutex.isLocked)
    }

    private fun plan(id: Long, accountId: Long, categoryId: Long? = null) = InstallmentPlanEntity(
        id = id, accountId = accountId, description = "TV", categoryId = categoryId, currencyCode = "USD",
        originalMinor = 3000, accountTotalMinor = 3000, accountRate = BigDecimal.ONE, accountRateSource = RateSource.AUTO,
        installments = 3, purchaseDate = 0L,
    )

    private fun cardAccount(id: Long = 1) =
        AccountEntity(id = id, name = "Visa", type = "CREDIT_CARD", currencyCode = "USD", createdAt = 0L, statementClosingDay = 10)

    @Test
    fun `a backup round-trips installment plans`() = runTest {
        val card = db.accountDao().insert(cardAccount(1))
        db.categoryDao().insert(CategoryEntity(id = 5, name = "Home"))
        db.installmentPlanDao().insert(plan(7, card, categoryId = 5))
        (1..3).forEach { k ->
            db.transactionDao().insert(tx(id = 100L + k, accountId = card, amount = 1000, code = "USD").copy(installmentPlanId = 7, installmentNumber = k))
        }
        val before = db.installmentPlanDao().getAllSync()
        val json = Gson().toJson(repo.snapshot())
        db.transactionDao().deleteAll()
        db.installmentPlanDao().deleteAll()
        db.categoryDao().deleteAll()
        db.accountDao().deleteAll()

        assertTrue(repo.restoreJson(json, usd) is RestoreResult.Restored)

        assertEquals(before, db.installmentPlanDao().getAllSync())
        val rows = db.transactionDao().getAllTransactionsSync().sortedBy { it.id }
        assertEquals(listOf(7L, 7L, 7L), rows.map { it.installmentPlanId })
        assertEquals(listOf(1, 2, 3), rows.map { it.installmentNumber })
    }

    @Test
    fun `an older backup version is refused and nothing changes`() = runTest {
        seedLocalData()
        val json = Gson().toJson(BackupData(version = 4, mainCurrencyCode = "USD"))

        assertEquals(RestoreResult.Incompatible, repo.restoreJson(json, usd))

        assertEquals(99L, db.transactionDao().getAllTransactionsSync().single().id)
        assertEquals("Old", db.accountDao().getAll().single().name)
    }

    @Test
    fun `version 5 backups are refused`() = runTest {
        seedLocalData()
        val json = Gson().toJson(BackupData(version = 5, mainCurrencyCode = "USD"))

        assertEquals(RestoreResult.Incompatible, repo.restoreJson(json, usd))

        assertEquals(99L, db.transactionDao().getAllTransactionsSync().single().id)
        assertEquals("Old", db.accountDao().getAll().single().name)
    }

    @Test
    fun `version 6 backups are refused`() = runTest {
        seedLocalData()
        val json = Gson().toJson(BackupData(version = 6, mainCurrencyCode = "USD"))

        assertEquals(RestoreResult.Incompatible, repo.restoreJson(json, usd))

        assertEquals(99L, db.transactionDao().getAllTransactionsSync().single().id)
    }

    @Test
    fun `a file that isn't a backup is refused`() = runTest {
        seedLocalData()
        listOf("{}", "not json", """{"version":7}""", """{"version":7,"mainCurrencyCode":"USD"}""", "[]", "").forEach {
            assertEquals(it, RestoreResult.Incompatible, repo.restoreJson(it, usd))
        }
        assertEquals(99L, db.transactionDao().getAllTransactionsSync().single().id)
    }

    @Test
    fun `an installment whose plan is missing becomes a plain expense`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(cardAccount(1)),
            transactions = listOf(tx(id = 1, accountId = 1, amount = 10, code = "USD").copy(installmentPlanId = 404, installmentNumber = 2)),
        )

        repo.restore(backup)

        val row = db.transactionDao().getAllTransactionsSync().single()
        assertNull(row.installmentPlanId)
        assertNull(row.installmentNumber)
    }

    @Test
    fun `a plan whose category is missing loses it and one whose account is missing is dropped with its installments`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(cardAccount(1)),
            installmentPlans = listOf(plan(1, accountId = 1, categoryId = 77), plan(2, accountId = 404)),
            transactions = listOf(
                tx(id = 1, accountId = 1, amount = 10, code = "USD").copy(installmentPlanId = 1, installmentNumber = 1),
                tx(id = 2, accountId = 404, amount = 10, code = "USD").copy(installmentPlanId = 2, installmentNumber = 1),
                tx(id = 3, accountId = 1, amount = 10, code = "USD"),
            ),
        )

        repo.restore(backup)

        val plan = db.installmentPlanDao().getAllSync().single()
        assertEquals(1L, plan.id)
        assertNull(plan.categoryId)
        assertEquals(listOf(1L, 3L), db.transactionDao().getAllTransactionsSync().map { it.id }.sorted())
    }

    @Test
    fun `a plan whose account is missing is dropped with its installments`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(cardAccount(1)),
            installmentPlans = listOf(plan(2, accountId = 404)),
            transactions = listOf(
                tx(id = 2, accountId = 1, amount = 10, code = "USD").copy(installmentPlanId = 2, installmentNumber = 1),
                tx(id = 3, accountId = 1, amount = 10, code = "USD"),
            ),
        )

        repo.restore(backup)

        assertTrue(db.installmentPlanDao().getAllSync().isEmpty())
        assertEquals(listOf(3L), db.transactionDao().getAllTransactionsSync().map { it.id })
    }

    @Test
    fun `a recurrence link to an installment of a dropped plan is cleared`() = runTest {
        val backup = BackupData(
            mainCurrencyCode = "USD",
            accounts = listOf(cardAccount(1)),
            installmentPlans = listOf(plan(2, accountId = 404)),
            transactions = listOf(
                tx(id = 2, accountId = 1, amount = 10, code = "USD").copy(installmentPlanId = 2, installmentNumber = 1),
                tx(id = 3, accountId = 1, amount = 10, code = "USD", parent = 2),
            ),
        )

        repo.restore(backup)

        assertNull(db.transactionDao().getAllTransactionsSync().single().parentRecurrenceId)
    }
}
