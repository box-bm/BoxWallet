package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.data.local.dao.AppMetaDao
import com.boxdev.boxwallet.data.local.dao.CategoryDao
import com.boxdev.boxwallet.data.local.dao.InstallmentPlanDao
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.AppMetaEntity
import com.boxdev.boxwallet.data.local.entity.InstallmentPlanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.BackupData
import com.google.gson.Gson
import com.google.gson.JsonParseException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of reading a backup for restore. */
sealed interface RestoreResult {
    /** The data replaced the local data; the main currency is the backup's. */
    data class Restored(val data: BackupData) : RestoreResult

    /** Nothing was written: the file isn't a backup in the current format (older version or not a backup). */
    data object Incompatible : RestoreResult

    /**
     * Nothing was written: the backup was made with another main currency. The UI asks which one to
     * keep and then calls [BackupRepository.restore] (and, to keep [device], a main currency change).
     */
    data class CurrencyMismatch(val backup: Currency, val device: Currency, val data: BackupData) : RestoreResult
}

/** Single owner of the backup file format and the restore order. */
@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: BoxWalletDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val loanDao: LoanDao,
    private val installmentPlanDao: InstallmentPlanDao,
    private val accountDao: AccountDao,
    private val appMetaDao: AppMetaDao,
    private val prefs: UserPreferences,
    private val lock: CurrencyChangeLock,
    private val googleDriveManager: GoogleDriveManager
) {
    private val gson = Gson()

    suspend fun createBackup() {
        val data = consistentSnapshot()
        val json = withContext(Dispatchers.Default) { gson.toJson(data) }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        googleDriveManager.uploadBackup("boxwallet_backup_$timestamp.json", json)
    }

    /**
     * Writes the current data to `filesDir/backups/pre-currency-change.json`, replacing the previous
     * one, before the main currency change rewrites every movement (spec 4, step 3).
     */
    suspend fun writeLocalSnapshot(): File {
        val json = withContext(Dispatchers.Default) { gson.toJson(snapshot()) }
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, LOCAL_BACKUP_DIR).apply { mkdirs() }
            val target = File(dir, PRE_CURRENCY_CHANGE_FILE)
            // Write aside and rename so a crash never leaves a truncated snapshot in place.
            val tmp = File(dir, "$PRE_CURRENCY_CHANGE_FILE.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        }
    }

    /**
     * [snapshot] under [CurrencyChangeLock] and in one read transaction, so the label
     * (`mainCurrencyCode`) and the amounts come from the same state. Not for [writeLocalSnapshot]:
     * that one already runs under the lock (the lock isn't reentrant).
     */
    internal suspend fun consistentSnapshot(): BackupData =
        lock.mutex.withLock { database.withTransaction { snapshot() } }

    /** The current data in the current format; exchange rates are left out (spec 6.3). */
    internal suspend fun snapshot(): BackupData = coroutineScope {
        val accounts = async { accountDao.getAll() }
        val transactions = async { transactionDao.getAllTransactionsSync() }
        val categories = async { categoryDao.getAllCategoriesSync() }
        val loans = async { loanDao.getAllLoansSync() }
        val installmentPlans = async { installmentPlanDao.getAllSync() }
        val defaultAccountId = async { prefs.defaultAccountId.first() }
        BackupData(
            createdAt = System.currentTimeMillis(),
            mainCurrencyCode = prefs.mainCurrency.first().currencyCode,
            accounts = accounts.await(),
            transactions = transactions.await(),
            categories = categories.await(),
            loans = loans.await(),
            installmentPlans = installmentPlans.await(),
            defaultAccountId = defaultAccountId.await(),
        )
    }

    /**
     * Downloads and reads a backup. Only the current format is accepted: any other file is
     * [RestoreResult.Incompatible]. With the same main currency it is restored; otherwise
     * [RestoreResult.CurrencyMismatch] is returned and nothing is written. [device] defaults to the
     * stored main currency (onboarding passes the one being chosen).
     */
    suspend fun restoreBackup(fileId: String, device: Currency? = null): RestoreResult =
        restoreJson(googleDriveManager.downloadBackup(fileId), device)

    internal suspend fun restoreJson(json: String, device: Currency? = null): RestoreResult {
        val deviceCurrency = device ?: prefs.mainCurrency.first()
        val data = withContext(Dispatchers.Default) { parse(json) } ?: return RestoreResult.Incompatible
        val backupCurrency = runCatching { Currency.getInstance(data.mainCurrencyCode) }.getOrNull()
            ?: return RestoreResult.Incompatible
        if (backupCurrency != deviceCurrency) return RestoreResult.CurrencyMismatch(backupCurrency, deviceCurrency, data)
        return RestoreResult.Restored(restore(data))
    }

    /** The backup in [json], or null when it isn't one in the current format. */
    private fun parse(json: String): BackupData? {
        val data = try {
            // Gson leaves non-null fields null when the file lacks them.
            gson.fromJson(json, BackupData::class.java)
        } catch (_: JsonParseException) {
            null
        }
        return data?.takeIf { it.version == BackupData.CURRENT_VERSION && (it.mainCurrencyCode as String?) != null }
    }

    /**
     * Replaces all local data with [data] in one transaction and makes its currency the main one.
     * Runs under [CurrencyChangeLock], so it never interleaves with a main currency change or an
     * approximate-rate fix. References missing from the backup are cleared rather than failing.
     */
    suspend fun restore(data: BackupData): BackupData = lock.mutex.withLock {
        val sane = sanitized(data)
        withContext(NonCancellable) {
            database.withTransaction {
                transactionDao.deleteAll()
                installmentPlanDao.deleteAll()
                loanDao.deleteAll()
                categoryDao.deleteAll()
                accountDao.deleteAll()
                // FK order: accounts → categories → loans → installment plans → transactions (recurrence parents first).
                accountDao.insertAll(sane.accounts)
                categoryDao.insertAll(sane.categories)
                loanDao.insertAll(sane.loans)
                installmentPlanDao.insertAll(sane.installmentPlans)
                transactionDao.insertAll(sane.transactions)
                // Committed with the data: a crash before the DataStore writes is finished at startup.
                appMetaDao.put(AppMetaEntity(META_MAIN_CURRENCY, sane.mainCurrencyCode))
                sane.defaultAccountId?.let { appMetaDao.put(AppMetaEntity(META_DEFAULT_ACCOUNT_ID, it.toString())) }
            }
            applyPendingRestore()
        }
        sane
    }

    /**
     * Finishes a restore whose transaction committed but whose main currency and default account
     * never reached DataStore (the process died in between). Call at startup.
     */
    suspend fun completePendingRestore(): Unit = lock.mutex.withLock {
        withContext(NonCancellable) { applyPendingRestore() }
    }

    /** Copies the restore marks to DataStore, then deletes them. Caller holds the lock. */
    private suspend fun applyPendingRestore() {
        val currency = appMetaDao.get(META_MAIN_CURRENCY)
        val accountId = appMetaDao.get(META_DEFAULT_ACCOUNT_ID)?.toLongOrNull()
        if (currency == null && accountId == null) return
        currency?.let { prefs.setMainCurrency(it) }
        accountId?.let { prefs.setDefaultAccountId(it) }
        appMetaDao.delete(listOf(META_MAIN_CURRENCY, META_DEFAULT_ACCOUNT_ID))
    }

    internal companion object {
        private const val LOCAL_BACKUP_DIR = "backups"
        private const val PRE_CURRENCY_CHANGE_FILE = "pre-currency-change.json"
        const val META_MAIN_CURRENCY = "restore_main_currency"
        const val META_DEFAULT_ACCOUNT_ID = "restore_default_account_id"

        /**
         * Drops references to rows the backup doesn't contain and orders recurrences so a parent is
         * inserted before its occurrences. The default account is the backup's if it has that account,
         * else the first one.
         *
         * A movement whose account is missing goes to the first account in that account's currency.
         * The row doesn't store it, but with `accountRate == 1` and equal original and account
         * amounts it was the movement's own currency; otherwise (or with no account in it) the
         * movement goes to the default account. A loan has no account amount: its own currency is
         * used. With no accounts at all the insert fails and the restore rolls back.
         *
         * An installment plan whose account is missing is dropped with its installments (a card
         * purchase isn't moved to another account); one whose category is missing loses the category.
         * An installment whose plan is missing becomes a plain expense.
         *
         * A card keeps at most one second-currency child: a child whose parent is missing, is itself a
         * child or already has an earlier child (by id) becomes a plain account.
         */
        private fun sanitized(data: BackupData): BackupData {
            val accountIds = data.accounts.mapTo(HashSet()) { it.id }
            val categoryIds = data.categories.mapTo(HashSet()) { it.id }
            val loanIds = data.loans.mapTo(HashSet()) { it.id }
            val plans = data.installmentPlans.filter { it.accountId in accountIds }
                .map { plan -> plan.copy(categoryId = plan.categoryId?.takeIf { it in categoryIds }) }
            val planIds = plans.mapTo(HashSet()) { it.id }
            val droppedPlanIds = data.installmentPlans.mapTo(HashSet()) { it.id } - planIds
            val txIds = data.transactions.mapTo(HashSet()) { it.id }
            val inOrder = data.accounts.sortedWith(compareBy({ it.sortOrder }, { it.id }))
            val firstByCurrency = inOrder.reversed().associate { it.currencyCode to it.id }
            val defaultAccount = data.defaultAccountId?.takeIf { it in accountIds } ?: inOrder.firstOrNull()?.id
            fun existingAccount(id: Long, accountCurrency: String?): Long =
                if (id in accountIds) id else accountCurrency?.let { firstByCurrency[it] } ?: defaultAccount ?: id
            val loans = data.loans.map { loan ->
                loan.copy(
                    categoryId = loan.categoryId?.takeIf { it in categoryIds },
                    accountId = loan.accountId?.let { existingAccount(it, loan.currencyCode) },
                )
            }
            val transactions = data.transactions.filter { it.installmentPlanId !in droppedPlanIds }.map { tx ->
                val loanKept = tx.loanId?.let { it in loanIds } ?: true
                val planKept = tx.installmentPlanId?.let { it in planIds } ?: true
                val inOwnCurrency = tx.accountRate.compareTo(BigDecimal.ONE) == 0 && tx.originalAmountMinor == tx.accountAmountMinor
                tx.copy(
                    accountId = existingAccount(tx.accountId, tx.currencyCode.takeIf { inOwnCurrency }),
                    categoryId = tx.categoryId?.takeIf { it in categoryIds },
                    loanId = if (loanKept) tx.loanId else null,
                    installmentPlanId = if (planKept) tx.installmentPlanId else null,
                    installmentNumber = if (loanKept && planKept) tx.installmentNumber else null,
                    parentRecurrenceId = tx.parentRecurrenceId?.takeIf { it in txIds && it != tx.id },
                )
            }
            return data.copy(
                accounts = singleChildren(data.accounts),
                loans = loans,
                installmentPlans = plans,
                transactions = parentsFirst(wholeTransfers(transactions)),
                defaultAccountId = defaultAccount,
            )
        }

        /**
         * Keeps at most one second currency per card: a child stays attached only when it and its top-level
         * parent are both cards in different currencies (the lowest id wins). It then follows its card's
         * archived and `includeInTotal` flags; any other child becomes a top-level account.
         */
        private fun singleChildren(accounts: List<AccountEntity>): List<AccountEntity> {
            val card = AccountType.CREDIT_CARD.name
            val cards = accounts.filter { it.parentAccountId == null && it.type == card }.associateBy { it.id }
            val withChild = HashSet<Long>()
            val keptChildren = accounts.sortedBy { it.id }
                .filter { child ->
                    val parent = child.parentAccountId?.let(cards::get) ?: return@filter false
                    child.type == card && child.currencyCode != parent.currencyCode && withChild.add(parent.id)
                }
                .mapTo(HashSet()) { it.id }
            return accounts.map {
                when {
                    it.parentAccountId == null -> it
                    it.id in keptChildren -> cards.getValue(it.parentAccountId).let { parent ->
                        it.copy(isArchived = parent.isArchived, includeInTotal = parent.includeInTotal)
                    }
                    else -> it.copy(parentAccountId = null)
                }
            }
        }

        /**
         * A transfer is one OUT row plus one IN row sharing a `transferId`. A group that isn't exactly
         * that (a half-lost backup) keeps its rows as balance adjustments so no money vanishes. Only
         * transfer rows can carry a `transferId`; any other movement loses a stray one.
         */
        private fun wholeTransfers(rows: List<TransactionEntity>): List<TransactionEntity> {
            val groups = rows.filter { it.transferId != null && it.type.isTransferName() }.groupBy { it.transferId }
            val broken = groups.filterValues { group ->
                group.size != 2 || group.count { it.type == TransactionType.TRANSFER_OUT.name } != 1 ||
                    group.count { it.type == TransactionType.TRANSFER_IN.name } != 1
            }.keys
            return rows.map { tx ->
                if (!tx.type.isTransferName()) return@map if (tx.transferId == null) tx else tx.copy(transferId = null)
                if (tx.transferId != null && tx.transferId !in broken) return@map tx
                val type = if (tx.type == TransactionType.TRANSFER_IN.name) {
                    TransactionType.ADJUSTMENT_IN
                } else {
                    TransactionType.ADJUSTMENT_OUT
                }
                tx.copy(type = type.name, transferId = null)
            }
        }

        private fun String.isTransferName() =
            this == TransactionType.TRANSFER_IN.name || this == TransactionType.TRANSFER_OUT.name

        private fun parentsFirst(rows: List<TransactionEntity>): List<TransactionEntity> {
            val ordered = ArrayList<TransactionEntity>(rows.size)
            val placed = HashSet<Long>()
            var pending = rows
            while (pending.isNotEmpty()) {
                var (ready, rest) = pending.partition { tx -> tx.parentRecurrenceId.let { it == null || it in placed } }
                if (ready.isEmpty()) {
                    // A cycle: break it at the first row.
                    ready = listOf(rest.first().copy(parentRecurrenceId = null))
                    rest = rest.drop(1)
                }
                ready.forEach { ordered += it; placed += it.id }
                pending = rest
            }
            return ordered
        }
    }
}
