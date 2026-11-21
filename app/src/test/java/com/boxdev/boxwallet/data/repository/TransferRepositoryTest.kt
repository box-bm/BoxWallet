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
import com.boxdev.boxwallet.domain.model.TransferDraft
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransferRepositoryTest {

    private val usd = Currency.getInstance("USD")
    private val gtq = Currency.getInstance("GTQ")
    private val november = LocalDate.of(2026, 11, 5)

    /** Units per USD on 2026-11-05. */
    private val units = mapOf(
        november to mapOf(
            "EUR" to BigDecimal.ONE.divide(BigDecimal("1.0852"), MathContext.DECIMAL64),
            "GTQ" to BigDecimal("7.64"),
        ),
    )

    private val lookup = object : RateLookup {
        override suspend fun exact(day: LocalDate, code: String) = units[day]?.get(code)
        override suspend fun nearest(day: LocalDate, code: String) = units[day]?.get(code)
        override suspend fun download(day: LocalDate) = false
    }

    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var transfers: TransferRepository
    private lateinit var transactions: TransactionRepository
    private var banco = 0L
    private var dolares = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        banco = db.accountDao().insert(AccountEntity(name = "Banco", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L))
        dolares = db.accountDao().insert(AccountEntity(name = "Dólares", type = "CHECKING", currencyCode = "USD", createdAt = 0L, sortOrder = 1))
        prefs = UserPreferences(context)
        prefs.setMainCurrency("GTQ")
        val amounts = TransactionAmounts(ExchangeRateResolver(lookup) { LocalDate.of(2026, 11, 7) })
        val guard = MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts)
        transfers = TransferRepository(db, db.transactionDao(), db.accountDao(), prefs, amounts, guard)
        transactions = TransactionRepository(
            db.transactionDao(),
            AccountRepository(db.accountDao(), prefs, lookup, context, db.transactionDao(), db, amounts, guard),
            prefs,
            context,
            amounts,
            guard,
        )
    }

    /** DataStore outlives the test; restore the default main currency. */
    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
    }

    private suspend fun account(name: String, code: String): Long =
        db.accountDao().insert(AccountEntity(name = name, type = "CASH", currencyCode = code, createdAt = 0L, sortOrder = 2))

    private fun draft(to: Long, sentMinor: Long, manualRate: BigDecimal? = null, from: Long = banco) = TransferDraft(
        fromAccountId = from,
        toAccountId = to,
        sent = Money(sentMinor, gtq),
        date = november.atTime(10, 0),
        note = "Ahorro",
        manualRate = manualRate,
    )

    private suspend fun legs(transferId: String): Pair<TransactionEntity, TransactionEntity> {
        val rows = db.transactionDao().getByTransferId(transferId)
        assertEquals(2, rows.size)
        return rows.single { it.type == TransactionType.TRANSFER_OUT.name } to
            rows.single { it.type == TransactionType.TRANSFER_IN.name }
    }

    private suspend fun assertRejected(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    private val typedRate = BigDecimal("100").divide(BigDecimal("771"), MathContext.DECIMAL64)

    @Test
    fun sameCurrencyTransferWritesTwoIdentityLegs() = runTest {
        val caja = account("Caja", "GTQ")

        val id = transfers.create(draft(caja, 50000))

        val (out, into) = legs(id)
        listOf(out to banco, into to caja).forEach { (row, accountId) ->
            assertEquals(accountId, row.accountId)
            assertEquals(50000L, row.originalAmountMinor)
            assertEquals(50000L, row.accountAmountMinor)
            assertEquals(50000L, row.amountMinor)
            assertEquals(0, BigDecimal.ONE.compareTo(row.accountRate))
            assertEquals(id, row.transferId)
            assertTrue(row.isCleared)
            assertNull(row.categoryId)
            assertEquals("Ahorro", row.description)
        }
        assertEquals(-50000L, db.accountDao().getBalance(banco, Long.MAX_VALUE))
        assertEquals(50000L, db.accountDao().getBalance(caja, Long.MAX_VALUE))
    }

    @Test
    fun crossCurrencyTransferUsesRateOfItsDate() = runTest {
        val id = transfers.create(draft(dolares, 77100))

        val (out, into) = legs(id)
        assertEquals("GTQ", out.currencyCode)
        assertEquals(77100L, out.originalAmountMinor)
        assertEquals(77100L, out.accountAmountMinor)
        assertEquals(77100L, out.amountMinor)

        assertEquals("GTQ", into.currencyCode)
        assertEquals(77100L, into.originalAmountMinor)
        assertEquals(10092L, into.accountAmountMinor) // 771 / 7.64 = 100.916…
        assertEquals(RateSource.AUTO, into.accountRateSource)
        assertEquals(77103L, into.amountMinor) // 100.92 × 7.64 = 771.0288

        val transfer = transfers.get(id)!!
        assertEquals(Money(77100, gtq), transfer.sent)
        assertEquals(Money(10092, usd), transfer.received)
        assertEquals(out.id, transfer.outLegId)
        assertEquals(into.id, transfer.inLegId)
        assertEquals(transfer, transfers.getByLegId(into.id))
        assertEquals(mapOf(id to com.boxdev.boxwallet.domain.model.TransferPair(id, banco, dolares)), transfers.observePairs().first())
    }

    @Test
    fun typedReceivedAmountIsManual() = runTest {
        val id = transfers.create(draft(dolares, 77100, manualRate = typedRate))

        val (_, into) = legs(id)
        assertEquals(10000L, into.accountAmountMinor)
        assertEquals(RateSource.MANUAL, into.accountRateSource)
        assertEquals(76400L, into.amountMinor) // 100.00 × 7.64
    }

    @Test
    fun editingSentKeepsManualRate() = runTest {
        val id = transfers.create(draft(dolares, 77100, manualRate = typedRate))
        val (outBefore, inBefore) = legs(id)

        transfers.update(id, draft(dolares, 80000, manualRate = typedRate))

        val (out, into) = legs(id)
        assertEquals(outBefore.id, out.id)
        assertEquals(inBefore.id, into.id)
        assertEquals(80000L, out.accountAmountMinor)
        assertEquals(80000L, into.originalAmountMinor)
        assertEquals(Money(80000, gtq).convert(usd, typedRate).minor, into.accountAmountMinor)
        assertEquals(RateSource.MANUAL, into.accountRateSource)
        assertEquals(0, typedRate.compareTo(into.accountRate))
    }

    @Test
    fun changingDestinationCurrencyRecomputesRate() = runTest {
        val euros = account("Euros", "EUR")
        val id = transfers.create(draft(dolares, 77100, manualRate = typedRate))

        transfers.update(id, draft(euros, 77100))

        val (_, into) = legs(id)
        assertEquals(euros, into.accountId)
        assertEquals(RateSource.AUTO, into.accountRateSource)
        assertEquals(1, db.transactionDao().getByTransferId(id).count { it.type == TransactionType.TRANSFER_IN.name })
        assertEquals(0L, db.accountDao().getBalance(dolares, Long.MAX_VALUE))
    }

    /** A repository whose lookup knows no rate at all: offline before any download. */
    private fun offlineTransfers(): TransferRepository {
        val none = object : RateLookup {
            override suspend fun exact(day: LocalDate, code: String): BigDecimal? = null
            override suspend fun nearest(day: LocalDate, code: String): BigDecimal? = null
            override suspend fun download(day: LocalDate) = false
        }
        val amounts = TransactionAmounts(ExchangeRateResolver(none) { LocalDate.of(2026, 11, 7) })
        val guard = MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts)
        return TransferRepository(db, db.transactionDao(), db.accountDao(), prefs, amounts, guard)
    }

    @Test
    fun manualRateFromMainCurrencySavesWithoutAnyStoredRate() = runTest {
        val id = offlineTransfers().create(draft(dolares, 77100, manualRate = typedRate))

        val (out, into) = legs(id)
        assertEquals(77100L, out.amountMinor)
        assertEquals(10000L, into.accountAmountMinor)
        assertEquals(RateSource.MANUAL, into.accountRateSource)
        assertEquals(RateSource.MANUAL, into.mainRateSource)
        assertEquals(0, BigDecimal.ONE.divide(typedRate, MathContext.DECIMAL64).compareTo(into.mainRate))
        assertEquals(77100L, into.amountMinor) // 100.00 × 7.71: what was sent
    }

    @Test
    fun manualRateIntoMainCurrencySavesWithoutAnyStoredRate() = runTest {
        val rate = BigDecimal("7.71")
        val sent = TransferDraft(dolares, banco, Money(10000, usd), november.atTime(10, 0), note = "", manualRate = rate)

        val id = offlineTransfers().create(sent)

        val (out, into) = legs(id)
        assertEquals(10000L, out.accountAmountMinor)
        assertEquals(RateSource.MANUAL, out.mainRateSource)
        assertEquals(0, rate.compareTo(out.mainRate))
        assertEquals(77100L, out.amountMinor)
        assertEquals(77100L, into.accountAmountMinor)
        assertEquals(77100L, into.amountMinor)
    }

    @Test
    fun editingTheManualRateIntoMainCurrencyRederivesTheSourceMainLeg() = runTest {
        val offline = offlineTransfers()
        val sent = TransferDraft(dolares, banco, Money(10000, usd), november.atTime(10, 0), note = "", manualRate = BigDecimal("7.71"))
        val id = offline.create(sent)

        offline.update(id, sent.copy(manualRate = BigDecimal("7.80")))

        val (out, into) = legs(id)
        assertEquals(78000L, out.amountMinor)
        assertEquals(78000L, into.amountMinor)
    }

    @Test
    fun manualRateWithoutTheDestinationMainRateNamesThatLeg() = runTest {
        val euros = account("Euros", "EUR")
        val noEur = object : RateLookup {
            override suspend fun exact(day: LocalDate, code: String) = if (code == "GTQ") BigDecimal("7.64") else null
            override suspend fun nearest(day: LocalDate, code: String) = exact(day, code)
            override suspend fun download(day: LocalDate) = false
        }
        val amounts = TransactionAmounts(ExchangeRateResolver(noEur) { LocalDate.of(2026, 11, 7) })
        val guard = MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts)
        val repo = TransferRepository(db, db.transactionDao(), db.accountDao(), prefs, amounts, guard)
        val sent = TransferDraft(dolares, euros, Money(10000, usd), november.atTime(10, 0), note = "", manualRate = BigDecimal("0.92"))

        try {
            repo.create(sent)
            fail("Expected MissingRateException")
        } catch (e: MissingRateException) {
            assertEquals(Currency.getInstance("EUR"), e.from)
            assertEquals(gtq, e.to)
        }
        assertEquals(0, db.transactionDao().countAll())
    }

    @Test
    fun deleteFromEitherLegDeletesBoth() = runTest {
        val id = transfers.create(draft(dolares, 77100))
        val (_, into) = legs(id)

        transactions.deleteTransaction(into.id)

        assertEquals(0, db.transactionDao().getByTransferId(id).size)
    }

    @Test
    fun rejectsSameOrArchivedAccount() = runTest {
        val archived = account("Vieja", "GTQ")
        db.accountDao().update(db.accountDao().getById(archived)!!.copy(isArchived = true))

        assertRejected { transfers.create(draft(banco, 100)) }
        assertRejected { transfers.create(draft(archived, 100)) }
        assertRejected { transfers.create(draft(dolares, 0)) }
        assertRejected { transfers.create(draft(999, 100)) }

        assertEquals(0, db.transactionDao().countAll())
    }

    @Test
    fun updateAllowsAnAccountArchivedSinceOnlyWhenUnchanged() = runTest {
        val caja = account("Caja", "GTQ")
        val id = transfers.create(draft(caja, 50000))
        db.accountDao().update(db.accountDao().getById(caja)!!.copy(isArchived = true))

        transfers.update(id, draft(caja, 60000))
        assertEquals(60000L, legs(id).second.accountAmountMinor)

        val other = account("Otra", "GTQ")
        transfers.update(id, draft(other, 60000))
        assertRejected { transfers.update(id, draft(caja, 60000)) }
        assertEquals(other, legs(id).second.accountId)
    }

    @Test
    fun transfersDoNotChangeFinancialSummary() = runTest {
        val before = transactions.getFinancialSummary().first()

        transfers.create(draft(dolares, 77100))

        assertEquals(before, transactions.getFinancialSummary().first())
    }

    @Test
    fun updateTransactionRejectsTransferLeg() = runTest {
        val id = transfers.create(draft(dolares, 77100))
        val (out, _) = legs(id)
        val leg = out.toDomain(gtq, gtq)

        assertRejected { transactions.updateTransaction(leg.copy(description = "x")) }
        assertEquals("Ahorro", db.transactionDao().getById(out.id)!!.description)
    }
}
