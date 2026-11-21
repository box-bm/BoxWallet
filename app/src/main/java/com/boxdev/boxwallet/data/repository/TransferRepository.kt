package com.boxdev.boxwallet.data.repository

import androidx.room.withTransaction
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.Transfer
import com.boxdev.boxwallet.domain.model.TransferDraft
import com.boxdev.boxwallet.domain.model.TransferPair
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.domain.money.withAmounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.MathContext
import java.util.Currency
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transfers between accounts: a TRANSFER_OUT row in the source and a TRANSFER_IN row in the
 * destination sharing one transferId. Both rows are always written, edited and deleted together.
 */
@Singleton
class TransferRepository @Inject constructor(
    private val database: BoxWalletDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val userPreferences: UserPreferences,
    private val amounts: TransactionAmounts,
    private val guard: MovementWriteGuard,
) {

    /** Saves [draft] as a new pair of rows; returns its transferId. */
    suspend fun create(draft: TransferDraft): String = guard.write { current ->
        database.withTransaction {
            requireUsable(draft, stored = null)
            val transferId = UUID.randomUUID().toString()
            val (out, into) = legs(current, draft, transferId, previousOut = null, previousIn = null)
            transactionDao.insert(out.toEntity())
            transactionDao.insert(into.toEntity())
            transferId
        }
    }

    /** Rewrites both rows of [transferId] from [draft], keeping their ids and the transferId. */
    suspend fun update(transferId: String, draft: TransferDraft) {
        guard.write { current ->
            database.withTransaction {
                val stored = storedLegs(transferId) ?: throw IllegalArgumentException("Transfer $transferId does not exist")
                requireUsable(draft, stored)
                val (storedOut, storedIn) = stored
                val previousOut = storedOut.toDomain(current.accountCurrency(storedOut.accountId), current.main)
                val previousIn = storedIn.toDomain(current.accountCurrency(storedIn.accountId), current.main)
                val (out, into) = legs(current, draft, transferId, previousOut, previousIn)
                transactionDao.deleteByTransferId(transferId)
                transactionDao.insert(out.copy(id = storedOut.id).toEntity())
                transactionDao.insert(into.copy(id = storedIn.id).toEntity())
            }
        }
    }

    suspend fun delete(transferId: String) = transactionDao.deleteByTransferId(transferId)

    suspend fun get(transferId: String): Transfer? {
        val (out, into) = storedLegs(transferId) ?: return null
        val main = userPreferences.mainCurrency.first()
        val sent = out.toDomain(accountCurrency(out.accountId, main), main)
        val received = into.toDomain(accountCurrency(into.accountId, main), main)
        return Transfer(
            id = transferId,
            fromAccountId = sent.accountId,
            toAccountId = received.accountId,
            sent = sent.accountAmount,
            received = received.accountAmount,
            rate = received.accountRate,
            date = sent.date,
            note = sent.description,
            outLegId = sent.id,
            inLegId = received.id,
        )
    }

    /** The transfer that the row [transactionId] is a leg of; null for any other movement. */
    suspend fun getByLegId(transactionId: Long): Transfer? =
        transactionDao.getById(transactionId)?.transferId?.let { get(it) }

    /** Every transfer's accounts by transferId. */
    fun observePairs(): Flow<Map<String, TransferPair>> =
        transactionDao.observeTransferPairs().map { pairs -> pairs.associateBy { it.transferId } }

    private suspend fun accountCurrency(accountId: Long, main: Currency): Currency =
        accountDao.getById(accountId)?.let { Currency.getInstance(it.currencyCode) } ?: main

    /** The OUT and IN rows of [transferId]; null unless both exist. */
    private suspend fun storedLegs(transferId: String): Pair<TransactionEntity, TransactionEntity>? {
        val rows = transactionDao.getByTransferId(transferId)
        val out = rows.singleOrNull { it.type == TransactionType.TRANSFER_OUT.name } ?: return null
        val into = rows.singleOrNull { it.type == TransactionType.TRANSFER_IN.name } ?: return null
        return out to into
    }

    /**
     * Source ≠ destination, both exist and aren't archived, and something is sent. When editing
     * ([stored]), an account archived since is still allowed on the side that already used it.
     */
    private suspend fun requireUsable(draft: TransferDraft, stored: Pair<TransactionEntity, TransactionEntity>?) {
        require(draft.sent.minor > 0) { "A transfer must send a positive amount" }
        require(draft.fromAccountId != draft.toAccountId) { "Source and destination must differ" }
        requireAccount(draft.fromAccountId, keptFrom = stored?.first?.accountId)
        requireAccount(draft.toAccountId, keptFrom = stored?.second?.accountId)
    }

    private suspend fun requireAccount(accountId: Long, keptFrom: Long?) {
        val account = accountDao.getById(accountId) ?: throw IllegalArgumentException("Account $accountId does not exist")
        require(!account.isArchived || accountId == keptFrom) { "Account $accountId is archived" }
    }

    /**
     * The OUT and IN rows of [draft] with their conversions frozen by [TransactionAmounts.forSave];
     * [previousOut]/[previousIn] are the stored rows when editing.
     */
    private suspend fun legs(
        current: MovementWriteGuard.Current,
        draft: TransferDraft,
        transferId: String,
        previousOut: Transaction?,
        previousIn: Transaction?,
    ): Pair<Transaction, Transaction> {
        val from = current.accountCurrency(draft.fromAccountId)
        val to = current.accountCurrency(draft.toAccountId)
        require(draft.sent.currency == from) { "The sent amount must be in the source account's currency" }
        val base = Transaction(
            accountId = draft.fromAccountId,
            type = TransactionType.TRANSFER_OUT,
            date = draft.date,
            description = draft.note,
            original = draft.sent,
            accountAmount = draft.sent,
            accountRate = ExchangeRate.IDENTITY,
            amount = draft.sent,
            mainRate = ExchangeRate.IDENTITY,
            isCleared = true,
            transferId = transferId,
        )
        val main = current.main
        val manual = draft.manualRate.takeIf { from != to }
        // When one side is the main currency, the typed rate is also the other side's main rate, so
        // a currency with no stored rate at all can still be saved. A MANUAL main rate on the OUT row
        // only ever comes from that, so it follows the typed rate when it changes.
        val out = amounts.forSave(base, previousOut, from, main, manualAccountRate = null).let { saved ->
            if (manual != null && to == main && (saved == null || saved.mainRate.source == RateSource.MANUAL)) {
                val mainRate = ExchangeRate(manual, RateSource.MANUAL)
                base.withAmounts(AmountResult.Ready(draft.sent, ExchangeRate.IDENTITY, draft.sent.convert(main, manual), mainRate))
            } else {
                saved
            }
        } ?: throw MissingRateException(from, main)
        val inBase = base.copy(accountId = draft.toAccountId, type = TransactionType.TRANSFER_IN)
        val into = amounts.forSave(inBase, previousIn, to, main, manualAccountRate = manual)
            ?: manual?.takeIf { from == main }?.let { rate ->
                val received = draft.sent.convert(to, rate)
                val back = ExchangeRate(BigDecimal.ONE.divide(rate, MathContext.DECIMAL64), RateSource.MANUAL)
                inBase.withAmounts(AmountResult.Ready(received, ExchangeRate(rate, RateSource.MANUAL), received.convert(main, back.value), back))
            }
            // With a typed rate, only the destination's main leg can be the missing one.
            ?: throw if (manual != null) MissingRateException(to, main) else MissingRateException(from, to)
        return out to into
    }
}
