package com.boxdev.boxwallet.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.TransactionType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionDao: TransactionDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            checkUpcomingReminders()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun checkUpcomingReminders() {
        val currentTime = System.currentTimeMillis()
        val oneDayFromNow = currentTime + TimeUnit.DAYS.toMillis(1)

        val upcomingReminders = transactionDao.getUpcomingReminders(currentTime).first()
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())

        upcomingReminders
            .filter { it.reminderDate != null && it.reminderDate <= oneDayFromNow }
            .forEach { transaction ->
                val title = if (transaction.type == TransactionType.EXPENSE) {
                    "Upcoming Payment"
                } else {
                    "Expected Income"
                }

                val message = buildString {
                    append(transaction.comment.ifEmpty { transaction.type.name })
                    append(": ")
                    append(currencyFormat.format(transaction.amount))
                    if (transaction.currentInstallment != null) {
                        append(" (${transaction.currentInstallment}/${transaction.totalInstallments})")
                    }
                }

                showNotification(
                    id = transaction.id.toInt(),
                    title = title,
                    message = message
                )
            }
    }

    private fun showNotification(id: Int, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Payment Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for upcoming payments and incomes"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    companion object {
        private const val CHANNEL_ID = "payment_reminders"
        private const val WORK_NAME = "reminder_work"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
                12, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
