package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.boxdev.boxwallet.data.local.dao.CategoryDao
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.domain.model.BackupData
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Single owner of the backup file format and the restore order. */
@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: BoxWalletDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val loanDao: LoanDao,
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

    private suspend fun snapshot(): BackupData = coroutineScope {
        val transactions = async { transactionDao.getAllTransactionsSync() }
        val categories = async { categoryDao.getAllCategoriesSync() }
        val loans = async { loanDao.getAllLoansSync() }
        BackupData(
            version = 2,
            createdAt = System.currentTimeMillis(),
            transactions = transactions.await(),
            categories = categories.await(),
            loans = loans.await()
        )
    }

    /** Replaces all local data with the backup's contents and returns what was restored. */
    suspend fun restoreBackup(fileId: String): BackupData {
        val json = googleDriveManager.downloadBackup(fileId)
        val backupData = withContext(Dispatchers.Default) { gson.fromJson(json, BackupData::class.java) }

        database.withTransaction {
            transactionDao.deleteAll()
            loanDao.deleteAll()
            categoryDao.deleteAll()
            // FK order: categories → loans → transactions
            categoryDao.insertAll(backupData.categories)
            loanDao.insertAll(backupData.loans)
            transactionDao.insertAll(backupData.transactions)
        }
        return backupData
    }

    private companion object {
        const val LOCAL_BACKUP_DIR = "backups"
        const val PRE_CURRENCY_CHANGE_FILE = "pre-currency-change.json"
    }
}
