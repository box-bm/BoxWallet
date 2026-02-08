package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.data.local.dao.CategoryDao
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataManagementRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val loanDao: LoanDao
) {

    suspend fun clearAll() {
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
