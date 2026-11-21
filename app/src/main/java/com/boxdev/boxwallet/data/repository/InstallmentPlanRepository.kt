package com.boxdev.boxwallet.data.repository

import androidx.room.withTransaction
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.data.local.dao.InstallmentPlanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.InstallmentPlanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.InstallmentPlan
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.installmentClosingDates
import com.boxdev.boxwallet.domain.model.splitEvenly
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.money.AmountInput
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.util.CurrentDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/** A purchase to split into [installments] parts, one per statement closing of [accountId]'s card. */
data class NewInstallmentPlan(
    val accountId: Long,
    val description: String,
    val categoryId: Long?,
    val original: Money,
    val installments: Int,
    val purchaseDate: LocalDateTime,
)

@Singleton
class InstallmentPlanRepository @Inject constructor(
    private val database: BoxWalletDatabase,
    private val planDao: InstallmentPlanDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val amounts: TransactionAmounts,
    private val resolver: ExchangeRateResolver,
    private val guard: MovementWriteGuard,
    private val prefs: UserPreferences,
    private val currentDay: CurrentDay,
) {

    /**
     * Creates the plan and its installments (one expense per closing). The purchase converts to the
     * account currency once; each installment resolves its own main-currency leg. Everything is
     * computed before anything is written.
     */
    suspend fun create(input: NewInstallmentPlan): Long {
        require(input.installments in MIN_INSTALLMENTS..MAX_INSTALLMENTS) {
            "Installments must be between $MIN_INSTALLMENTS and $MAX_INSTALLMENTS"
        }
        val account = requireNotNull(accountDao.getById(input.accountId)) { "Unknown account ${input.accountId}" }
        require(account.type == AccountType.CREDIT_CARD.name) { "Installments need a credit card" }
        val card = account.parentAccountId?.let { accountDao.getById(it) } ?: account
        val closingDay = requireNotNull(card.statementClosingDay) { "The card has no statement closing day" }
        val accountCurrency = Currency.getInstance(account.currencyCode)

        return guard.write { current ->
            val purchaseDay = input.purchaseDate.toLocalDate()
            val purchase = amounts.compute(AmountInput(input.original, purchaseDay, accountCurrency, current.main))
                as? AmountResult.Ready ?: throw MissingRateException(input.original.currency, accountCurrency)

            val dates = installmentClosingDates(closingDay, purchaseDay, input.installments)
            val originals = splitEvenly(input.original.minor, input.installments)
            val accountParts = splitEvenly(purchase.accountAmount.minor, input.installments)
            val rows = dates.mapIndexed { index, date ->
                val mainRate = resolver.resolve(date, accountCurrency, current.main)
                    ?: throw MissingRateException(accountCurrency, current.main)
                val accountPart = Money(accountParts[index], accountCurrency)
                TransactionEntity(
                    accountId = input.accountId,
                    type = TransactionType.EXPENSE.name,
                    date = date.atTime(input.purchaseDate.toLocalTime()).toStoredMillis(),
                    description = input.description,
                    categoryId = input.categoryId,
                    loanId = null,
                    installmentNumber = index + 1,
                    originalAmountMinor = originals[index],
                    currencyCode = input.original.currency.currencyCode,
                    accountAmountMinor = accountPart.minor,
                    accountRate = purchase.accountRate.value,
                    accountRateSource = purchase.accountRate.source,
                    amountMinor = accountPart.convert(current.main, mainRate.value).minor,
                    mainRate = mainRate.value,
                    mainRateSource = mainRate.source,
                    isCleared = false,
                )
            }

            database.withTransaction {
                val planId = planDao.insert(
                    InstallmentPlanEntity(
                        accountId = input.accountId,
                        description = input.description,
                        categoryId = input.categoryId,
                        currencyCode = input.original.currency.currencyCode,
                        originalMinor = input.original.minor,
                        accountTotalMinor = purchase.accountAmount.minor,
                        accountRate = purchase.accountRate.value,
                        accountRateSource = purchase.accountRate.source,
                        installments = input.installments,
                        purchaseDate = input.purchaseDate.toStoredMillis(),
                    )
                )
                transactionDao.insertAll(rows.map { it.copy(installmentPlanId = planId) })
                planId
            }
        }
    }

    /** Only the description and category change; every installment follows. */
    suspend fun updateDetails(planId: Long, description: String, categoryId: Long?) {
        database.withTransaction {
            val plan = planDao.getById(planId) ?: return@withTransaction
            planDao.update(plan.copy(description = description, categoryId = categoryId))
            transactionDao.updateDetailsByPlanId(planId, description, categoryId)
        }
    }

    /** Removes the plan and, by cascade, all its installments (charged ones included). */
    suspend fun delete(planId: Long) {
        planDao.deleteById(planId)
    }

    fun observePlan(planId: Long): Flow<InstallmentPlan?> = combine(
        planDao.observeById(planId),
        transactionDao.observeByPlanId(planId),
        accountDao.observeAll(),
        currentDay.days,
    ) { plan, rows, accounts, today ->
        plan?.toPlan(rows, accounts.associate { it.id to it.currencyCode }, today)
    }

    fun observeInstallments(planId: Long): Flow<List<Transaction>> = combine(
        transactionDao.observeByPlanId(planId),
        accountDao.observeAll(),
        prefs.mainCurrency,
    ) { rows, accounts, main ->
        val currencies = accounts.associate { it.id to Currency.getInstance(it.currencyCode) }
        rows.map { it.toDomain(currencies[it.accountId] ?: main, main) }
    }

    /** The plans of the card and of its second currency, newest purchase first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePlansOfCard(cardId: Long): Flow<List<InstallmentPlan>> =
        accountDao.observeAll().flatMapLatest { accounts ->
            val ids = accounts.filter { it.id == cardId || it.parentAccountId == cardId }.map { it.id }
            val currencies = accounts.associate { it.id to it.currencyCode }
            combine(
                planDao.observeByAccounts(ids),
                transactionDao.observePlanInstallmentsOfAccounts(ids),
                currentDay.days,
            ) { plans, rows, today ->
                val byPlan = rows.groupBy { it.installmentPlanId }
                plans.sortedByDescending { it.purchaseDate }
                    .mapNotNull { it.toPlan(byPlan[it.id].orEmpty(), currencies, today) }
            }
        }

    private fun InstallmentPlanEntity.toPlan(
        rows: List<TransactionEntity>,
        currencies: Map<Long, String>,
        today: LocalDate,
    ): InstallmentPlan? {
        // The accounts flow can emit before a deleted account's plans disappear.
        val accountCurrency = Currency.getInstance(currencies[accountId] ?: return null)
        val cutoff = today.plusDays(1).atStartOfDay().toStoredMillis()
        val pending = rows.filter { it.date >= cutoff }
        return InstallmentPlan(
            id = id,
            accountId = accountId,
            description = description,
            categoryId = categoryId,
            original = Money(originalMinor, Currency.getInstance(currencyCode)),
            accountTotal = Money(accountTotalMinor, accountCurrency),
            installments = installments,
            purchaseDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(purchaseDate), ZoneOffset.UTC),
            charged = rows.size - pending.size,
            pendingMinor = pending.sumOf { it.accountAmountMinor },
        )
    }

    private fun LocalDateTime.toStoredMillis(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

    private companion object {
        const val MIN_INSTALLMENTS = 2
        const val MAX_INSTALLMENTS = 60
    }
}
