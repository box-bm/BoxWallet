package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.AppMetaEntity
import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.domain.model.BackupData
import com.boxdev.boxwallet.domain.money.RateSource
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
            context, db, db.transactionDao(), db.categoryDao(), db.loanDao(), db.accountDao(), db.appMetaDao(), prefs, lock,
            GoogleDriveManager(context),
        )
    }

    /** DataStore outlives the test; leave it as other tests expect it (main USD). */
    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
    }

    private fun fixture(): String = checkNotNull(javaClass.classLoader?.getResource("backup-v2.json")).readText()

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
    fun `v2 backup replaces local data in the device currency with references intact`() = runTest {
        seedLocalData()

        val result = repo.restoreJson(fixture(), gtq)

        assertTrue(result is RestoreResult.Restored)
        val account = db.accountDao().getAll().single()
        assertEquals("GTQ", account.currencyCode)
        assertEquals(account.id, prefs.defaultAccountId.first())
        assertEquals(gtq, prefs.mainCurrency.first())
        val rows = db.transactionDao().getAllTransactionsSync().associateBy { it.id }
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L, 6L, 7L), rows.keys)
        assertEquals(1250L, rows.getValue(1).amountMinor)
        assertEquals(30L, rows.getValue(2).amountMinor)
        assertEquals(3L, rows.getValue(4).parentRecurrenceId)
        assertEquals(1L, rows.getValue(6).loanId)
        assertEquals(1L, rows.getValue(1).categoryId)
        rows.values.forEach { assertEquals(account.id, it.accountId) }
        assertEquals(100000L, db.loanDao().getAllLoansSync().single().totalAmountMinor)
        assertEquals(listOf(1L, 2L), db.categoryDao().getAllCategoriesSync().map { it.id }.sorted())
    }

    @Test
    fun `v2 backup defaults to the stored main currency`() = runTest {
        repo.restoreJson(fixture())
        assertEquals("USD", db.accountDao().getAll().single().currencyCode)
        assertTrue(db.transactionDao().getAllTransactionsSync().all { it.currencyCode == "USD" })
    }

    @Test
    fun `v3 snapshot round-trips, accounts and currencies included`() = runTest {
        prefs.setMainCurrency("GTQ")
        val main = db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 1L))
        val savings = db.accountDao().insert(AccountEntity(name = "USD savings", type = "SAVINGS", currencyCode = "USD", createdAt = 2L, sortOrder = 1))
        db.transactionDao().insert(
            tx(id = 10, accountId = savings, amount = 1000, code = "USD").copy(
                amountMinor = 7712, mainRate = BigDecimal("7.712345678901"), mainRateSource = RateSource.MANUAL,
            )
        )
        val json = Gson().toJson(repo.snapshot())
        assertEquals(4, JsonParser.parseString(json).asJsonObject.get("version").asInt)
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
    fun `v3 with another main currency asks and writes nothing`() = runTest {
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
    fun `movements and loans of an account missing from the backup go to an existing account`() = runTest {
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
            loans = listOf(
                LoanEntity(id = 1, name = "L", totalAmountMinor = 1, currencyCode = "USD", accountId = 404, totalInstallments = 1, startDate = 0L),
            ),
        )

        repo.restore(backup)

        val rows = db.transactionDao().getAllTransactionsSync().associateBy { it.id }
        assertEquals(1L, rows.getValue(1).accountId)
        assertEquals(2L, rows.getValue(2).accountId) // the account in the movement's own currency
        assertEquals(2L, rows.getValue(3).accountId)
        assertEquals(1L, db.loanDao().getAllLoansSync().single().accountId)
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

        assertTrue(result is RestoreResult.Restored)
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
            loans = listOf(
                LoanEntity(id = 1, name = "USD", totalAmountMinor = 1, currencyCode = "USD", accountId = 99, totalInstallments = 1, startDate = 0L),
                LoanEntity(id = 2, name = "EUR", totalAmountMinor = 1, currencyCode = "EUR", accountId = 99, totalInstallments = 1, startDate = 0L),
            ),
        )

        repo.restore(backup)

        val rows = db.transactionDao().getAllTransactionsSync().associateBy { it.id }
        assertEquals(2L, rows.getValue(1).accountId)
        assertEquals(1L, rows.getValue(2).accountId)
        val loans = db.loanDao().getAllLoansSync().associateBy { it.id }
        assertEquals(2L, loans.getValue(1).accountId)
        assertEquals(1L, loans.getValue(2).accountId)
        assertEquals(1L, prefs.defaultAccountId.first())
    }

    @Test
    fun `a v3 backup from v1 restores with default account customization`() = runTest {
        val json = """
            {"version":3,"mainCurrencyCode":"USD",
             "accounts":[{"id":1,"name":"Main","type":"CHECKING","currencyCode":"USD","isArchived":false,"sortOrder":0,"createdAt":0}],
             "transactions":[{"id":1,"accountId":1,"type":"EXPENSE","date":0,"description":"","originalAmountMinor":5,"currencyCode":"USD",
               "accountAmountMinor":5,"accountRate":1,"accountRateSource":"AUTO","amountMinor":5,"mainRate":1,"mainRateSource":"AUTO",
               "isCleared":false,"hasReminder":false,"isRecurrent":false}],
             "categories":[],"loans":[]}
        """.trimIndent()

        val result = repo.restoreJson(json, usd)

        assertTrue(result is RestoreResult.Restored)
        val account = db.accountDao().getAll().single()
        assertEquals(listOf("", "AccountBalance", "DEFAULT"), listOf(account.description, account.icon, account.color))
        assertTrue(account.includeInTotal)
        assertEquals(1L, db.transactionDao().getAllTransactionsSync().single().accountId)
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
    fun `local snapshot is written in the v4 shape with accounts`() = runTest {
        seedLocalData()
        val file: File = repo.writeLocalSnapshot()
        val root = JsonParser.parseString(file.readText()).asJsonObject
        assertEquals(4, root.get("version").asInt)
        assertEquals("USD", root.get("mainCurrencyCode").asString)
        assertEquals("Old", root.getAsJsonArray("accounts")[0].asJsonObject.get("name").asString)
    }

    @Test
    fun `restore applies its main currency and default account and clears the marks`() = runTest {
        repo.restoreJson(fixture(), gtq)

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
}
