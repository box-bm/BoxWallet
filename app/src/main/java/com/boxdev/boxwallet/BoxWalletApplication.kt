package com.boxdev.boxwallet

import android.app.Application
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.boxdev.boxwallet.worker.ReminderAlarmReceiver
import com.boxdev.boxwallet.worker.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BoxWalletApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
    }
}
