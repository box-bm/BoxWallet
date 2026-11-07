package com.boxdev.boxwallet.data.repository

import androidx.room.withTransaction
import com.boxdev.boxwallet.data.local.dao.CategoryDao
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataManagementRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val loanDao: LoanDao,
    private val database: BoxWalletDatabase,
) {

    /** One transaction, so observers see a single change instead of three. */
    suspend fun clearAll() = database.withTransaction {
        transactionDao.deleteAll()
        loanDao.deleteAll()
        categoryDao.deleteAll()
    }

    suspend fun clearTransactions() {
        transactionDao.deleteAll()
    }

    suspend fun clearCategories() {
        categoryDao.deleteAll()
    }
}
