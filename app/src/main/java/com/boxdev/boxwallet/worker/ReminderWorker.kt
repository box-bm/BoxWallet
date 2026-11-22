package com.boxdev.boxwallet.worker

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.dao.AppMetaDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.AppMetaEntity
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.domain.model.CardSummary
import com.boxdev.boxwallet.domain.model.cardReminderStatement
import com.boxdev.boxwallet.domain.model.cardReminderKey
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.util.CurrencyFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.concurrent.TimeUnit

/**
 * What is left to pay of each of [summary]'s statements, joined with " + " ("Q1,980.00 + $120.00"); a
 * currency with nothing to pay (or no statement yet) is left out.
 */
internal fun cardAmountsToPay(summary: CardSummary, formatter: CurrencyFormatter): String =
    listOfNotNull(summary.primary, summary.second)
        .mapNotNull { balance ->
            balance.statement?.takeIf { !it.noStatementYet && it.toPayMinor > 0 }
                ?.let { formatter.format(Money(it.toPayMinor, balance.balance.currency)) }
        }
        .joinToString(" + ")

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val accountRepository: AccountRepository,
    private val appMetaDao: AppMetaDao,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            checkUpcomingReminders()
            checkCardDueDates()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun checkUpcomingReminders() {
        val currentTime = System.currentTimeMillis()
        val oneDayFromNow = currentTime + TimeUnit.DAYS.toMillis(1)

        val upcomingReminders = transactionDao.getUpcomingReminders(currentTime).first()
        val formatter = CurrencyFormatter()

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
                    append(formatter.format(Money(transaction.originalAmountMinor, Currency.getInstance(transaction.currencyCode))))
                    if (transaction.installmentNumber != null) {
                        append(context.getString(R.string.reminder_installment_suffix, transaction.installmentNumber))
                    }
                }

                showNotification(id = transaction.id.toInt(), title = title, message = message)
            }
    }

    /**
     * Notifies once per statement for each card whose due date is within its reminder window. A card that
     * fails is logged and skipped, so the others still get their reminder.
     */
    private suspend fun checkCardDueDates() {
        val today = LocalDate.now()
        val formatter = CurrencyFormatter()
        accountRepository.cardSummariesOn(today).first().values.forEach { summary ->
            try {
                remindCard(summary, today, formatter)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Card ${summary.card.id} reminder failed", e)
            }
        }
    }

    private suspend fun remindCard(summary: CardSummary, today: LocalDate, formatter: CurrencyFormatter) {
        val key = cardReminderKey(summary.card.id)
        val notified = appMetaDao.get(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val statement = cardReminderStatement(summary, today, notified) ?: return
        val message = context.getString(
            R.string.card_reminder_text, summary.card.name, cardAmountsToPay(summary, formatter),
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(statement.dueDate),
        )
        showNotification(
            id = (Int.MAX_VALUE - summary.card.id).toInt(),
            title = context.getString(R.string.reminder_upcoming_payment),
            message = message,
        )
        appMetaDao.put(AppMetaEntity(key, statement.lastClosing.toString()))
    }

    private fun showNotification(id: Int, title: String, message: String) {
        ReminderAlarmReceiver.ensureNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, ReminderAlarmReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, notification)
    }

    companion object {
        private const val TAG = "ReminderWorker"
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
