package com.boxdev.boxwallet.data.local.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BoxWalletDatabase::class.java,
    )

    private fun createV1WithAccountAndMovement() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO accounts (id, name, type, currencyCode, isArchived, sortOrder, createdAt) " +
                    "VALUES (1, 'Main', 'CHECKING', 'GTQ', 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO transactions (id, accountId, type, date, description, originalAmountMinor, " +
                    "currencyCode, accountAmountMinor, accountRate, accountRateSource, amountMinor, mainRate, " +
                    "mainRateSource, isCleared, hasReminder, isRecurrent) " +
                    "VALUES (1, 1, 'EXPENSE', 0, 'Lunch', 12345, 'GTQ', 12345, '1', 'AUTO', 12345, '1', " +
                    "'AUTO', 1, 0, 0)"
            )
            close()
        }
    }

    @Test
    fun migrate1To2_keepsRowsAndFillsDefaults() {
        createV1WithAccountAndMovement()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query(
            "SELECT name, currencyCode, description, icon, color, includeInTotal, parentAccountId, " +
                "creditLimitMinor, statementClosingDay, paymentDueDay, dueReminderDaysBefore " +
                "FROM accounts WHERE id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Main", c.getString(0))
            assertEquals("GTQ", c.getString(1))
            assertEquals("", c.getString(2))
            assertEquals("AccountBalance", c.getString(3))
            assertEquals("DEFAULT", c.getString(4))
            assertEquals(1, c.getInt(5))
            for (i in 6..10) assertTrue("column $i should be NULL", c.isNull(i))
        }
        db.query("SELECT amountMinor, transferId FROM transactions WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(12345L, c.getLong(0))
            assertTrue(c.isNull(1))
        }
    }

    @Test
    fun migrate1To2_keepsTransactionForeignKeys() {
        createV1WithAccountAndMovement()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        db.execSQL("PRAGMA foreign_keys = ON")

        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL("DELETE FROM accounts WHERE id = 1")
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
