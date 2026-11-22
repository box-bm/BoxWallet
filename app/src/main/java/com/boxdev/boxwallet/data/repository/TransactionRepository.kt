package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.data.local.dao.CurrencyUsage
import android.content.Context
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.local.entity.isTransfer
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.model.FinancialSummary
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.util.CurrencyFormatter
import com.boxdev.boxwallet.util.CurrentDay
import com.boxdev.boxwallet.worker.ReminderWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
    private val guard: MovementWriteGuard,
    private val currentDay: CurrentDay = CurrentDay(),
) {

    /** Maps entities to the domain with their account's currency and the current main currency. */
    private fun Flow<List<TransactionEntity>>.toDomain(): Flow<List<Transaction>> = combine(
        this,
        accountRepository.observeCurrencies(),
        userPreferences.mainCurrency,
    ) { rows, accounts, main ->
        rows.map { it.toDomain(accounts[it.accountId] ?: main, main) }
    }

    /** Every write goes through [guard], so a row computed before a currency change is recomputed. */
    suspend fun insertTransaction(transaction: Transaction): Long {
        val saved = guard.write { current ->
            val checked = current.revalidate(transaction)
            checked.copy(id = transactionDao.insert(checked.toEntity()))
        }
        scheduleOrCancelReminder(saved)
        return saved.id
    }

    /** Transfer legs are edited as a pair through [TransferRepository.update]. */
    suspend fun updateTransaction(transaction: Transaction) {
        val stored = transactionDao.getById(transaction.id)
        require(!transaction.type.isTransfer && transaction.transferId == null && stored?.transferId == null) {
            "Transfer ${transaction.id} must be edited through TransferRepository"
        }
        require(transaction.installmentPlanId == null && stored?.installmentPlanId == null) {
            "Installment ${transaction.id} must be changed through its plan"
        }
        val saved = guard.write { current ->
            current.revalidate(transaction).also { transactionDao.update(it.toEntity()) }
        }
        scheduleOrCancelReminder(saved)
    }

    /** Deleting either leg of a transfer deletes both. A plan installment only goes with its plan. */
    suspend fun deleteTransaction(id: Long) {
        val stored = transactionDao.getById(id)
        require(stored?.installmentPlanId == null) { "Installment $id must be changed through its plan" }
        ReminderWorker.cancel(context, id)
        val transferId = stored?.transferId
        if (transferId != null) transactionDao.deleteByTransferId(transferId) else transactionDao.deleteById(id)
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

    /** Start of tomorrow, as UTC millis of the local wall-clock: plan installments before it are listed. */
    private fun endOfToday(today: LocalDate): Long =
        today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    /** A plan installment dated after today is never listed; it appears from its closing day on. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllTransactions(): Flow<List<Transaction>> =
        currentDay.days.flatMapLatest { transactionDao.getAllTransactions(endOfToday(it)).toDomain() }

    /** [accountId]'s movements, newest first, without loan installments or future plan installments. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeByAccount(accountId: Long): Flow<List<Transaction>> =
        currentDay.days.flatMapLatest { transactionDao.observeByAccount(accountId, endOfToday(it)).toDomain() }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getLatestTransactions(limit: Int = 3): Flow<List<Transaction>> =
        currentDay.days.flatMapLatest { transactionDao.getLatestTransactions(limit, endOfToday(it)).toDomain() }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTransactionsBetweenDates(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> {
        val startMillis = startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return currentDay.days.flatMapLatest {
            transactionDao.getTransactionsBetweenDates(startMillis, endMillis, endOfToday(it)).toDomain()
        }
    }

    /**
     * Income and expense, all-time and for the current month. Counts what balances count: cleared
     * movements, plus card movements dated up to today whether cleared or not. Follows the day, so the
     * month and the card cutoff move on at midnight.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getFinancialSummary(): Flow<FinancialSummary> = currentDay.days.flatMapLatest { today ->
        val currentMonth = YearMonth.from(today)
        val startOfMonth = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endOfMonth = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val endOfToday = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

        combine(
            transactionDao.getTotalIncome(endOfToday),
            transactionDao.getTotalExpense(endOfToday),
            transactionDao.getTotalIncomeBetweenDates(startOfMonth, endOfMonth, endOfToday),
            transactionDao.getTotalExpenseBetweenDates(startOfMonth, endOfMonth, endOfToday)
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

    fun getRecurrentTransactions(): Flow<List<Transaction>> =
        transactionDao.getRecurrentTransactions().toDomain()

    suspend fun createNextRecurrence(transaction: Transaction) {
        require(transaction.isRecurrent) { "Transaction must be recurrent" }
        require(transaction.recurrenceFrequency != null) { "Recurrence frequency must be set" }

        val frequency = transaction.recurrenceFrequency!!
        val interval = transaction.recurrenceInterval ?: 1
        val nextDate = frequency.advance(transaction.date, interval)

        // An archived (or missing) account no longer takes movements: the occurrence goes to the default
        // account, and revalidate() below converts it for that account's currency (spec 10).
        val usable = accountRepository.observeAll().first().any { it.id == transaction.accountId && !it.isArchived }
        val accountId = if (usable) transaction.accountId else accountRepository.defaultAccount().id
        val nextTransaction = transaction.copy(
            id = 0,
            accountId = accountId,
            date = nextDate,
            isCleared = false,
            nextOccurrenceDate = frequency.advance(nextDate, interval),
            parentRecurrenceId = if (transaction.parentRecurrenceId != null) transaction.parentRecurrenceId else transaction.id
        )
        // Each occurrence converts at the rates of its own date; MANUAL legs are copied from the template.
        // Without any rate at all, keep the template's conversion rather than lose the occurrence.
        guard.write { current ->
            // A template saved before a currency change is first brought to the current currencies.
            val checked = current.revalidate(nextTransaction)
            val converted = transactionAmounts.refresh(checked) ?: checked
            transactionDao.insert(converted.toEntity())
        }
    }
}
