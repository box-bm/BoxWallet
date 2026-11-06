package com.boxdev.boxwallet.data.repository

import androidx.room.withTransaction
import com.boxdev.boxwallet.data.local.dao.CategoryDao
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.domain.model.BackupData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Single owner of the backup file format and the restore order. */
@Singleton
class BackupRepository @Inject constructor(
    private val database: BoxWalletDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val loanDao: LoanDao,
    private val googleDriveManager: GoogleDriveManager
) {
    private val gson = Gson()

    suspend fun createBackup() {
        val backupData = coroutineScope {
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
        val json = withContext(Dispatchers.Default) { gson.toJson(backupData) }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        googleDriveManager.uploadBackup("boxwallet_backup_$timestamp.json", json)
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
}
