package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.data.local.dao.CurrencyUsage
import android.content.Context
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.FinancialSummary
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.util.CurrencyFormatter
import com.boxdev.boxwallet.worker.ReminderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferences,
    @param:ApplicationContext private val context: Context,
    private val transactionAmounts: TransactionAmounts,
) {

    /** Maps entities to the domain with their account's currency and the current main currency. */
    private fun Flow<List<TransactionEntity>>.toDomain(): Flow<List<Transaction>> = combine(
        this,
        accountRepository.observeCurrencies(),
        userPreferences.mainCurrency,
    ) { rows, accounts, main ->
        rows.map { it.toDomain(accounts[it.accountId] ?: main, main) }
    }

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

    /**
     * Marks a loan installment paid or unpaid. On payment its conversion is re-resolved for its
     * date (the payment date), replacing the estimate made when it was generated; MANUAL is kept.
     */
    suspend fun setInstallmentCleared(installment: Transaction, cleared: Boolean) {
        val updated = installment.copy(isCleared = cleared)
        val manual = installment.accountRate.source == RateSource.MANUAL ||
            installment.mainRate.source == RateSource.MANUAL
        val converted = if (cleared && !manual) transactionAmounts.refresh(updated) ?: updated else updated
        updateTransaction(converted)
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
                    append(": ${CurrencyFormatter(transaction.amount.currency).format(transaction.original)}")
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

    suspend fun getTransactionById(id: Long): Transaction? {
        val entity = transactionDao.getById(id) ?: return null
        val main = userPreferences.mainCurrency.first()
        val accountCurrency = accountRepository.observeCurrencies().first()[entity.accountId] ?: main
        return entity.toDomain(accountCurrency, main)
    }

    /** Currencies of past movements, most used and most recent first. */
    fun recentCurrencies(limit: Int = 12): Flow<List<CurrencyUsage>> = transactionDao.recentCurrencies(limit)

    fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions().toDomain()

    fun getLatestTransactions(limit: Int = 3): Flow<List<Transaction>> =
        transactionDao.getLatestTransactions(limit).toDomain()

    fun getTransactionsBetweenDates(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> {
        val startMillis = startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return transactionDao.getTransactionsBetweenDates(startMillis, endMillis)
            .toDomain()
    }

    /** Loan installments due between [startDate] and [endDate], both inclusive, oldest first. */
    fun getInstallmentsBetweenDates(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> {
        val startMillis = startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return transactionDao.getInstallmentsBetweenDates(startMillis, endMillis)
            .toDomain()
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
                totalIncome = totalIncome ?: 0L,
                totalExpense = totalExpense ?: 0L,
                monthlyIncome = monthlyIncome ?: 0L,
                monthlyExpense = monthlyExpense ?: 0L
            )
        }
    }

    fun getUpcomingReminders(): Flow<List<Transaction>> =
        transactionDao.getUpcomingReminders(System.currentTimeMillis())
            .toDomain()

    fun getInstallmentsByLoanId(loanId: Long): Flow<List<Transaction>> =
        transactionDao.getInstallmentsByLoanIdFlow(loanId).toDomain()

    fun getRecurrentTransactions(): Flow<List<Transaction>> =
        transactionDao.getRecurrentTransactions().toDomain()

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
        // Each occurrence converts at the rates of its own date; MANUAL legs are copied from the template.
        // Without any rate at all, keep the template's conversion rather than lose the occurrence.
        val converted = transactionAmounts.refresh(nextTransaction) ?: nextTransaction

        transactionDao.insert(converted.toEntity())
    }
}
