package com.boxdev.boxwallet.data.rates

import androidx.room.withTransaction
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.InstallmentPlanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.domain.model.splitEvenly
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.Money
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/**
 * The one place that rewrites a plan's purchase conversion (spec 5.1): the purchase converts once,
 * at its purchase day, and every installment shares that account rate. Callers hold
 * [CurrencyChangeLock]; this never re-converts an installment at its own date.
 */
class InstallmentPlanRates @Inject constructor(private val database: BoxWalletDatabase) {

    private val planDao get() = database.installmentPlanDao()
    private val transactionDao get() = database.transactionDao()

    suspend fun all(): List<InstallmentPlanEntity> = planDao.getAllSync()

    suspend fun withApproximateRate(): List<InstallmentPlanEntity> = planDao.getWithApproximateRate()

    /**
     * Re-converts [plan] into [accountCurrency] at [accountRate] (the purchase day's rate), stores the
     * new total and rate on the plan, and re-splits the account amount across its installments in
     * installment order (the last absorbs the remainder). [mainLeg] gives each installment its main
     * rate for its own date; its main amount follows from its new account part. One DB transaction.
     * Returns the rewritten installments.
     */
    suspend fun reconvert(
        plan: InstallmentPlanEntity,
        accountCurrency: Currency,
        accountRate: ExchangeRate,
        main: Currency,
        mainLeg: suspend (row: TransactionEntity, day: LocalDate) -> ExchangeRate,
    ): List<TransactionEntity> = database.withTransaction {
        val total = Money(plan.originalMinor, Currency.getInstance(plan.currencyCode))
            .convert(accountCurrency, accountRate.value)
        val parts = splitEvenly(total.minor, plan.installments)
        val rows = transactionDao.getByPlanId(plan.id).sortedBy { it.installmentNumber }
        val updated = rows.mapIndexed { index, row ->
            val part = Money(parts[((row.installmentNumber ?: (index + 1)) - 1).coerceIn(parts.indices)], accountCurrency)
            val mainRate = mainLeg(row, row.day())
            row.copy(
                accountAmountMinor = part.minor,
                accountRate = accountRate.value,
                accountRateSource = accountRate.source,
                amountMinor = part.convert(main, mainRate.value).minor,
                mainRate = mainRate.value,
                mainRateSource = mainRate.source,
            )
        }
        planDao.update(
            plan.copy(
                accountTotalMinor = total.minor,
                accountRate = accountRate.value,
                accountRateSource = accountRate.source,
            )
        )
        updated.forEach { transactionDao.update(it) }
        updated
    }

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L

        fun InstallmentPlanEntity.purchaseDay(): LocalDate =
            LocalDate.ofEpochDay(Math.floorDiv(purchaseDate, MILLIS_PER_DAY))

        private fun TransactionEntity.day(): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(date, MILLIS_PER_DAY))
    }
}
