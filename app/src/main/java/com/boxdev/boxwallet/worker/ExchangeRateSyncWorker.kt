package com.boxdev.boxwallet.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boxdev.boxwallet.data.rates.RateSync
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ExchangeRateSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rateSync: RateSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return if (rateSync.syncAndFix()) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "exchange_rate_sync"
        private const val WORK_NAME_NOW = "exchange_rate_sync_now"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExchangeRateSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One-off sync, used at startup when the last one is older than 24 h. */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ExchangeRateSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME_NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
