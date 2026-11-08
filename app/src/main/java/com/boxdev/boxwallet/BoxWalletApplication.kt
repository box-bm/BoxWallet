package com.boxdev.boxwallet

import android.app.Application
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.rates.MainCurrencyChanger
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.worker.ReminderAlarmReceiver
import com.boxdev.boxwallet.worker.ExchangeRateSyncWorker
import com.boxdev.boxwallet.worker.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BoxWalletApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var mainCurrencyChanger: MainCurrencyChanger

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Delete old channel if it was created with wrong importance, then recreate
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.getNotificationChannel(ReminderAlarmReceiver.CHANNEL_ID)?.let { existing ->
            if (existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                nm.deleteNotificationChannel(ReminderAlarmReceiver.CHANNEL_ID)
            }
        }
        ReminderAlarmReceiver.ensureNotificationChannel(this)
        // Schedule periodic reminder check as fallback
        ReminderWorker.schedule(this)
        ExchangeRateSyncWorker.schedule(this)
        // Installs that finished onboarding before the database rebuild start with an empty
        // database and no account. Fresh installs get theirs when onboarding completes, once the
        // main currency is chosen. Write paths also go through AccountRepository.defaultAccount().
        appScope.launch {
            // First: a main currency change may have committed without saving the new currency.
            mainCurrencyChanger.completePending()
            if (!userPreferences.isFirstLaunch.first()) accountRepository.ensureDefaultAccount()
            val lastSync = userPreferences.lastRateSyncAt.first()
            if (lastSync == null || System.currentTimeMillis() - lastSync > 24 * 60 * 60 * 1000L) {
                ExchangeRateSyncWorker.syncNow(this@BoxWalletApplication)
            }
        }
    }
}
