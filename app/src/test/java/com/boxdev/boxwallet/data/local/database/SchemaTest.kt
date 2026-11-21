package com.boxdev.boxwallet.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.InstallmentPlanEntity
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.domain.money.RateSource
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchemaTest {

    private lateinit var db: BoxWalletDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `deleting a plan deletes its installments`() = runTest {
        val account = db.accountDao().insert(
            AccountEntity(name = "Visa", type = "CREDIT_CARD", currencyCode = "GTQ", createdAt = 0L),
        )
        val planId = db.installmentPlanDao().insert(
            InstallmentPlanEntity(
                accountId = account,
                description = "TV",
                currencyCode = "GTQ",
                originalMinor = 300_00,
                accountTotalMinor = 300_00,
                accountRate = BigDecimal.ONE,
                accountRateSource = RateSource.AUTO,
                installments = 3,
                purchaseDate = 0L,
            ),
        )
        val txId = db.transactionDao().insert(
            TransactionEntity(
                accountId = account,
                type = "EXPENSE",
                date = 0L,
                originalAmountMinor = 100_00,
                currencyCode = "GTQ",
                accountAmountMinor = 100_00,
                accountRate = BigDecimal.ONE,
                accountRateSource = RateSource.AUTO,
                amountMinor = 100_00,
                mainRate = BigDecimal.ONE,
                mainRateSource = RateSource.AUTO,
                installmentPlanId = planId,
            ),
        )
        assertNotNull(db.transactionDao().getById(txId))

        db.installmentPlanDao().deleteById(planId)

        assertNull(db.transactionDao().getById(txId))
    }

    @Test
    fun `a new loan defaults to SCHEDULED`() = runTest {
        val id = db.loanDao().insert(
            LoanEntity(name = "Car", totalAmountMinor = 1_000_00, currencyCode = "GTQ", totalInstallments = 10, startDate = 0L),
        )
        val loan = db.loanDao().getById(id)!!
        assertEquals("SCHEDULED", loan.kind)
        assertNull(loan.expectedDate)
    }
}
