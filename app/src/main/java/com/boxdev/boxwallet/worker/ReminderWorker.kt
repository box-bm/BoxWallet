package com.boxdev.boxwallet.worker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.dao.TransactionDao
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
                val title = if (transaction.type == "EXPENSE") {
                    context.getString(R.string.reminder_upcoming_payment)
                } else {
                    context.getString(R.string.reminder_expected_income)
                }

                val message = buildString {
                    append(transaction.description.ifEmpty { transaction.type })
                    append(": ")
                    append(currencyFormat.format(transaction.amount))
                    if (transaction.installmentNumber != null) {
                        append(context.getString(R.string.reminder_installment_suffix, transaction.installmentNumber))
                    }
                }

                showNotification(id = transaction.id.toInt(), title = title, message = message)
            }
    }

    private fun showNotification(id: Int, title: String, message: String) {
        ReminderAlarmReceiver.ensureNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, ReminderAlarmReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, notification)
    }

    companion object {
        private const val WORK_NAME = "reminder_work"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun scheduleExact(
            context: Context,
            transactionId: Long,
            triggerAtMillis: Long,
            title: String,
            message: String
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                putExtra(ReminderAlarmReceiver.EXTRA_NOTIFICATION_ID, transactionId.toInt())
                putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title)
                putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, message)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                transactionId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                // Fallback: inexact but still fires in Doze mode
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }

        fun cancel(context: Context, transactionId: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                transactionId.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
    }
}
