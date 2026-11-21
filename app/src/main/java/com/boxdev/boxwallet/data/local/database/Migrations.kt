package com.boxdev.boxwallet.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Accounts gain presentation, grouping and credit-card fields; movements gain a transfer link. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `icon` TEXT NOT NULL DEFAULT 'AccountBalance'")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `color` TEXT NOT NULL DEFAULT 'DEFAULT'")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `includeInTotal` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `parentAccountId` INTEGER")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `creditLimitMinor` INTEGER")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `statementClosingDay` INTEGER")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `paymentDueDay` INTEGER")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `dueReminderDaysBefore` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_parentAccountId` ON `accounts` (`parentAccountId`)")

        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `transferId` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transferId` ON `transactions` (`transferId`)")
    }
}
