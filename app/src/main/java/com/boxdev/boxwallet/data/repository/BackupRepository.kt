package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.data.local.dao.CategoryDao
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.domain.model.BackupData
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of reading a backup for restore. */
sealed interface RestoreResult {
    val data: BackupData

    /** The data replaced the local data; the main currency is the backup's. */
    data class Restored(override val data: BackupData) : RestoreResult

    /**
     * Nothing was written: the backup was made with another main currency. The UI asks which one to
     * keep and then calls [BackupRepository.restore] (and, to keep [device], a main currency change).
     */
    data class CurrencyMismatch(val backup: Currency, val device: Currency, override val data: BackupData) : RestoreResult
}

/** Single owner of the backup file format and the restore order. */
@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: BoxWalletDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val loanDao: LoanDao,
    private val accountDao: AccountDao,
    private val prefs: UserPreferences,
    private val lock: CurrencyChangeLock,
    private val googleDriveManager: GoogleDriveManager
) {
    private val gson = Gson()

    suspend fun createBackup() {
        val json = withContext(Dispatchers.Default) { gson.toJson(snapshot()) }
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

    /** The current data in the v3 shape; exchange rates are left out (spec 6.3). */
    internal suspend fun snapshot(): BackupData = coroutineScope {
        val accounts = async { accountDao.getAll() }
        val transactions = async { transactionDao.getAllTransactionsSync() }
        val categories = async { categoryDao.getAllCategoriesSync() }
        val loans = async { loanDao.getAllLoansSync() }
        BackupData(
            createdAt = System.currentTimeMillis(),
            mainCurrencyCode = prefs.mainCurrency.first().currencyCode,
            accounts = accounts.await(),
            transactions = transactions.await(),
            categories = categories.await(),
            loans = loans.await()
        )
    }

    /**
     * Downloads and reads a backup. A v2 backup (no currencies) is restored in [device]; a v3 backup
     * with the same main currency is restored; otherwise [RestoreResult.CurrencyMismatch] is returned
     * and nothing is written. [device] defaults to the stored main currency (onboarding passes the
     * one being chosen).
     */
    suspend fun restoreBackup(fileId: String, device: Currency? = null): RestoreResult =
        restoreJson(googleDriveManager.downloadBackup(fileId), device)

    internal suspend fun restoreJson(json: String, device: Currency? = null): RestoreResult {
        val deviceCurrency = device ?: prefs.mainCurrency.first()
        val accountName = context.getString(R.string.account_default_name)
        val data = withContext(Dispatchers.Default) { LegacyBackupConverter.decode(json, deviceCurrency, accountName) }
        val backupCurrency = Currency.getInstance(data.mainCurrencyCode)
        if (backupCurrency != deviceCurrency) return RestoreResult.CurrencyMismatch(backupCurrency, deviceCurrency, data)
        return RestoreResult.Restored(restore(data))
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
                loanDao.deleteAll()
                categoryDao.deleteAll()
                accountDao.deleteAll()
                // FK order: accounts → categories → loans → transactions (recurrence parents first).
                accountDao.insertAll(sane.accounts)
                categoryDao.insertAll(sane.categories)
                loanDao.insertAll(sane.loans)
                transactionDao.insertAll(sane.transactions)
            }
            prefs.setMainCurrency(sane.mainCurrencyCode)
            accountDao.getAll().firstOrNull()?.let { prefs.setDefaultAccountId(it.id) }
        }
        sane
    }

    private companion object {
        const val LOCAL_BACKUP_DIR = "backups"
        const val PRE_CURRENCY_CHANGE_FILE = "pre-currency-change.json"

        /**
         * Old backups come from a schema without foreign keys: drop references to rows the backup
         * doesn't contain and order recurrences so a parent is inserted before its occurrences.
         */
        fun sanitized(data: BackupData): BackupData {
            val accountIds = data.accounts.mapTo(HashSet()) { it.id }
            val categoryIds = data.categories.mapTo(HashSet()) { it.id }
            val loanIds = data.loans.mapTo(HashSet()) { it.id }
            val txIds = data.transactions.mapTo(HashSet()) { it.id }
            val loans = data.loans.map { loan ->
                loan.copy(
                    categoryId = loan.categoryId?.takeIf { it in categoryIds },
                    accountId = loan.accountId?.takeIf { it in accountIds },
                )
            }
            val transactions = data.transactions.map { tx ->
                val loanKept = tx.loanId?.let { it in loanIds } ?: true
                tx.copy(
                    categoryId = tx.categoryId?.takeIf { it in categoryIds },
                    loanId = if (loanKept) tx.loanId else null,
                    installmentNumber = if (loanKept) tx.installmentNumber else null,
                    parentRecurrenceId = tx.parentRecurrenceId?.takeIf { it in txIds && it != tx.id },
                )
            }
            return data.copy(loans = loans, transactions = parentsFirst(transactions))
        }

        fun parentsFirst(rows: List<TransactionEntity>): List<TransactionEntity> {
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
