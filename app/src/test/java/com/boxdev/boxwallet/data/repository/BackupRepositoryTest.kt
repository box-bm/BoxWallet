package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.domain.model.BackupData
import com.boxdev.boxwallet.domain.money.RateSource
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
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
            context, db, db.transactionDao(), db.categoryDao(), db.loanDao(), db.accountDao(), prefs, lock,
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
        assertEquals(3, JsonParser.parseString(json).asJsonObject.get("version").asInt)
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
    fun `local snapshot is written in the v3 shape with accounts`() = runTest {
        seedLocalData()
        val file: File = repo.writeLocalSnapshot()
        val root = JsonParser.parseString(file.readText()).asJsonObject
        assertEquals(3, root.get("version").asInt)
        assertEquals("USD", root.get("mainCurrencyCode").asString)
        assertEquals("Old", root.getAsJsonArray("accounts")[0].asJsonObject.get("name").asString)
    }
}
