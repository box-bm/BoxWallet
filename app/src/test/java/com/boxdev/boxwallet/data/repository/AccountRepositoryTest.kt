package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.money.Money
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

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        repository = AccountRepository(db.accountDao(), UserPreferences(context), noRates, context)
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
}
