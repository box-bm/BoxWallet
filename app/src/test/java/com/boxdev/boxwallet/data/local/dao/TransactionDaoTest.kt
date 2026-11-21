package com.boxdev.boxwallet.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionDaoTest {

    private lateinit var db: BoxWalletDatabase
    private lateinit var dao: TransactionDao
    private lateinit var accountDao: AccountDao
    private var accountId: Long = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BoxWalletDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.transactionDao()
        accountDao = db.accountDao()
        accountId = accountDao.insert(
            AccountEntity(name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L)
        )
    }

    @After
    fun tearDown() = db.close()

    private companion object {
        /** Start of "tomorrow" for these tests: cards count movements dated before it. */
        const val END_OF_TODAY = 86_400_000L
    }

    private fun tx(
        amountMinor: Long = 100,
        currencyCode: String = "GTQ",
        date: Long = 0L,
        type: String = "EXPENSE",
        isCleared: Boolean = true,
    ) = TransactionEntity(
        accountId = accountId,
        type = type,
        date = date,
        originalAmountMinor = amountMinor,
        currencyCode = currencyCode,
        accountAmountMinor = amountMinor,
        accountRate = BigDecimal.ONE,
        accountRateSource = RateSource.AUTO,
        amountMinor = amountMinor,
        mainRate = BigDecimal.ONE,
        mainRateSource = RateSource.AUTO,
        isCleared = isCleared,
    )

    @Test
    fun `sums use amountMinor`() = runTest {
        dao.insert(tx(amountMinor = 1050))
        dao.insert(tx(amountMinor = 250))

        assertEquals(1300L, dao.getTotalExpense(END_OF_TODAY).first())
    }

    @Test
    fun `adjustments do not change income or expense totals`() = runTest {
        dao.insert(tx(amountMinor = 1000, type = "INCOME"))
        dao.insert(tx(amountMinor = 300, type = "EXPENSE"))
        dao.insert(tx(amountMinor = 5000, type = "ADJUSTMENT_IN"))
        dao.insert(tx(amountMinor = 200, type = "ADJUSTMENT_OUT"))

        assertEquals(1000L, dao.getTotalIncome(END_OF_TODAY).first())
        assertEquals(300L, dao.getTotalExpense(END_OF_TODAY).first())
    }

    @Test
    fun `card movements count in totals whether cleared or not, up to today`() = runTest {
        val card = accountDao.insert(AccountEntity(name = "Visa", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L))
        dao.insert(tx(amountMinor = 300, isCleared = false, date = 1_000L).copy(accountId = card))
        dao.insert(tx(amountMinor = 50, isCleared = false, date = END_OF_TODAY).copy(accountId = card)) // tomorrow
        dao.insert(tx(amountMinor = 70, isCleared = false, date = 1_000L)) // pending on a checking account
        dao.insert(tx(amountMinor = 20, isCleared = true, date = 1_000L))

        assertEquals(320L, dao.getTotalExpense(END_OF_TODAY).first())
        assertEquals(320L, dao.getTotalExpenseBetweenDates(0L, END_OF_TODAY + 10_000L, END_OF_TODAY).first())
    }

    @Test
    fun `card income counts in month totals whether cleared or not`() = runTest {
        val card = accountDao.insert(AccountEntity(name = "Visa", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L))
        dao.insert(tx(amountMinor = 400, type = "INCOME", isCleared = false, date = 1_000L).copy(accountId = card))

        assertEquals(400L, dao.getTotalIncome(END_OF_TODAY).first())
        assertEquals(400L, dao.getTotalIncomeBetweenDates(0L, 2_000L, END_OF_TODAY).first())
    }

    @Test
    fun `recentCurrencies orders by uses then recency`() = runTest {
        repeat(3) { dao.insert(tx(currencyCode = "GTQ", date = 1_000L + it)) }
        dao.insert(tx(currencyCode = "USD", date = 5_000L))
        dao.insert(tx(currencyCode = "EUR", date = 100L))

        val codes = dao.recentCurrencies(limit = 10).first().map { it.currencyCode }

        assertEquals(listOf("GTQ", "USD", "EUR"), codes)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `account delete is restricted while transactions exist`() = runTest {
        dao.insert(tx())

        accountDao.deleteById(accountId)
    }
}
