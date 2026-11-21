package com.boxdev.boxwallet.ui.screens.onboarding

import java.time.LocalDate
import java.math.BigDecimal
import com.boxdev.boxwallet.domain.money.RateLookup
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.entity.AppMetaEntity
import com.boxdev.boxwallet.data.rates.CurrencyChangeLock
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.data.remote.GoogleAuthManager
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.BackupRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingViewModelTest {

    private lateinit var context: Context
    private lateinit var db: BoxWalletDatabase
    private lateinit var prefs: UserPreferences
    private lateinit var vm: OnboardingViewModel
    private val gtq = Currency.getInstance("GTQ")
    private val eur = Currency.getInstance("EUR")

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        prefs = UserPreferences(context)
        prefs.setMainCurrency("USD")
        val backup = BackupRepository(
            context, db, db.transactionDao(), db.categoryDao(), db.loanDao(), db.accountDao(), db.appMetaDao(),
            prefs, CurrencyChangeLock(), GoogleDriveManager(context),
        )
        val lookup = object : RateLookup {
            override suspend fun exact(day: LocalDate, code: String): BigDecimal? = null
            override suspend fun nearest(day: LocalDate, code: String): BigDecimal? = null
            override suspend fun download(day: LocalDate) = false
        }
        val amounts = TransactionAmounts(ExchangeRateResolver(lookup))
        vm = OnboardingViewModel(
            prefs, GoogleAuthManager(context), GoogleDriveManager(context), backup,
            AccountRepository(
                db.accountDao(), prefs, lookup, context,
                db.transactionDao(), db, amounts,
                MovementWriteGuard(CurrencyChangeLock(), prefs, db.accountDao(), amounts),
            ),
        )
        vm.overrideCurrency(eur)
    }

    /** DataStore outlives the test; leave it as other tests expect it (main USD). */
    @After
    fun tearDown() = runTest {
        prefs.setMainCurrency("USD")
        db.close()
    }

    @Test
    fun `without a restore the chosen currency becomes the main one`() = runTest {
        vm.finishOnboarding(analyticsEnabled = false, saveCurrency = true)
        assertEquals(eur, prefs.mainCurrency.first())
        assertEquals("EUR", db.accountDao().getAll().single().currencyCode)
    }

    @Test
    fun `after a restore the chosen currency does not overwrite the backup's`() = runTest {
        db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L))
        prefs.setMainCurrency("GTQ")

        vm.finishOnboarding(analyticsEnabled = false, saveCurrency = true)

        assertEquals(gtq, prefs.mainCurrency.first())
    }

    @Test
    fun `a restore committed before a crash is finished, not overwritten`() = runTest {
        val acc = db.accountDao().insert(AccountEntity(name = "Main", type = "CHECKING", currencyCode = "GTQ", createdAt = 0L))
        db.appMetaDao().put(AppMetaEntity(BackupRepository.META_MAIN_CURRENCY, "GTQ"))
        db.appMetaDao().put(AppMetaEntity(BackupRepository.META_DEFAULT_ACCOUNT_ID, acc.toString()))

        vm.finishOnboarding(analyticsEnabled = false, saveCurrency = true)

        assertEquals(gtq, prefs.mainCurrency.first())
        assertEquals(acc, prefs.defaultAccountId.first())
    }
}
