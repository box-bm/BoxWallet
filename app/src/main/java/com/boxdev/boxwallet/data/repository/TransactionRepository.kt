package com.boxdev.boxwallet.data.repository

import android.content.Context
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.FinancialSummary
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.worker.ReminderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    @ApplicationContext private val context: Context
) {

    suspend fun insertTransaction(transaction: Transaction): Long {
        val entity = transaction.toEntity()
        val id = transactionDao.insert(entity)
        scheduleOrCancelReminder(transaction.copy(id = id))
        return id
    }

    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.update(transaction.toEntity())
        scheduleOrCancelReminder(transaction)
    }

    suspend fun deleteTransaction(id: Long) {
        ReminderWorker.cancel(context, id)
        transactionDao.deleteById(id)
    }

    private fun scheduleOrCancelReminder(transaction: Transaction) {
        if (transaction.hasReminder && transaction.reminderSecondsBefore != null && !transaction.isCleared) {
            // Use system default timezone to get the actual wall-clock moment,
            // since transaction.date is a LocalDateTime representing local time
            val transactionMillis = transaction.date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val reminderMillis = transactionMillis - (transaction.reminderSecondsBefore * 1000)
            if (reminderMillis >= System.currentTimeMillis()) {
                val title = if (transaction.type == TransactionType.EXPENSE) {
                    context.getString(R.string.reminder_upcoming_payment)
                } else {
                    context.getString(R.string.reminder_expected_income)
                }
                val message = buildString {
                    append(transaction.description.ifEmpty { transaction.type.name })
                    append(": ${"%.2f".format(transaction.amount)}")
                    if (transaction.installmentNumber != null) {
                        append(context.getString(R.string.reminder_installment_suffix, transaction.installmentNumber))
                    }
                }
                ReminderWorker.scheduleExact(context, transaction.id, reminderMillis, title, message)
            }
        } else {
            ReminderWorker.cancel(context, transaction.id)
        }
    }

    suspend fun getTransactionById(id: Long): Transaction? = transactionDao.getById(id)?.toDomain()

    fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions().map { it.map { e -> e.toDomain() } }

    fun getLatestTransactions(limit: Int = 3): Flow<List<Transaction>> =
        transactionDao.getLatestTransactions(limit).map { it.map { e -> e.toDomain() } }

    fun getTransactionsBetweenDates(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> {
        val startMillis = startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return transactionDao.getTransactionsBetweenDates(startMillis, endMillis)
            .map { it.map { e -> e.toDomain() } }
    }

    fun getFinancialSummary(): Flow<FinancialSummary> {
        val currentMonth = YearMonth.now()
        val startOfMonth = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endOfMonth = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

        return combine(
            transactionDao.getTotalIncome(),
            transactionDao.getTotalExpense(),
            transactionDao.getTotalIncomeBetweenDates(startOfMonth, endOfMonth),
            transactionDao.getTotalExpenseBetweenDates(startOfMonth, endOfMonth)
        ) { totalIncome, totalExpense, monthlyIncome, monthlyExpense ->
            FinancialSummary(
                totalIncome = totalIncome ?: 0.0,
                totalExpense = totalExpense ?: 0.0,
                monthlyIncome = monthlyIncome ?: 0.0,
                monthlyExpense = monthlyExpense ?: 0.0
            )
        }
    }

    fun getUpcomingReminders(): Flow<List<Transaction>> =
        transactionDao.getUpcomingReminders(System.currentTimeMillis())
            .map { it.map { e -> e.toDomain() } }

    fun getInstallmentsByLoanId(loanId: Long): Flow<List<Transaction>> =
        transactionDao.getInstallmentsByLoanIdFlow(loanId).map { it.map { e -> e.toDomain() } }

    fun getRecurrentTransactions(): Flow<List<Transaction>> =
        transactionDao.getRecurrentTransactions().map { it.map { e -> e.toDomain() } }

    suspend fun createNextRecurrence(transaction: Transaction) {
        require(transaction.isRecurrent) { "Transaction must be recurrent" }
        require(transaction.recurrenceFrequency != null) { "Recurrence frequency must be set" }

        val frequency = transaction.recurrenceFrequency!!
        val interval = transaction.recurrenceInterval ?: 1
        val nextDate = frequency.advance(transaction.date, interval)

        val nextTransaction = transaction.copy(
            id = 0,
            date = nextDate,
            isCleared = false,
            nextOccurrenceDate = frequency.advance(nextDate, interval),
            parentRecurrenceId = if (transaction.parentRecurrenceId != null) transaction.parentRecurrenceId else transaction.id
        )

        transactionDao.insert(nextTransaction.toEntity())
    }
}
