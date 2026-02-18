package com.boxdev.boxwallet.di

import android.content.Context
import androidx.room.Room
import com.boxdev.boxwallet.data.local.dao.CategoryDao
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BoxWalletDatabase =
        Room.databaseBuilder(context, BoxWalletDatabase::class.java, BoxWalletDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideTransactionDao(db: BoxWalletDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: BoxWalletDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideLoanDao(db: BoxWalletDatabase): LoanDao = db.loanDao()
}
