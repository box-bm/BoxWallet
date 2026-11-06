package com.boxdev.boxwallet.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.boxdev.boxwallet.data.local.dao.CategoryDao
import com.boxdev.boxwallet.data.local.dao.LoanDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.data.local.entity.LoanEntity
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * DEBUG BUILDS ONLY. Fills an EMPTY database with synthetic sample data for design review and
 * store screenshots. Never overwrites existing data.
 *
 *   adb shell am broadcast -a com.boxdev.boxwallet.debug.SEED_DEMO -n com.boxdev.boxwallet/.debug.DemoDataReceiver
 */
@AndroidEntryPoint
class DemoDataReceiver : BroadcastReceiver() {
    @Inject lateinit var categoryDao: CategoryDao
    @Inject lateinit var loanDao: LoanDao
    @Inject lateinit var transactionDao: TransactionDao

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (transactionDao.getAllTransactionsSync().isNotEmpty() || loanDao.getAllLoansSync().isNotEmpty()) {
                    Log.w(TAG, "Database not empty; demo data not inserted.")
                    return@launch
                }
                seed()
                Log.i(TAG, "Demo data inserted.")
            } finally {
                pending.finish()
            }
        }
    }

    private fun LocalDateTime.ms() = toInstant(ZoneOffset.UTC).toEpochMilli()

    private suspend fun seed() {
        val cats = listOf(
            "Groceries" to "LocalGroceryStore", "Restaurants" to "Restaurant", "Transport" to "DirectionsBus",
            "Salary" to "Work", "Home" to "Home", "Utilities" to "Bolt", "Health" to "LocalHospital",
            "Coffee" to "LocalCafe", "Phone" to "Smartphone", "Freelance" to "Computer",
        )
        val existing = categoryDao.getAllCategoriesSync().associateBy { it.name }
        val ids = cats.associate { (name, icon) ->
            name to (existing[name]?.id ?: categoryDao.insert(CategoryEntity(name = name, description = "", icon = icon)))
        }
        val now = LocalDateTime.now().withMinute(15)
        data class T(val amount: Double, val desc: String, val cat: String, val income: Boolean, val daysAgo: Long, val hour: Int, val recurrent: Boolean = false)
        listOf(
            T(350.0, "Supermercado La Torre", "Groceries", false, 0, 9),
            T(2450.0, "Salario noviembre", "Salary", true, 1, 8, recurrent = true),
            T(18.5, "Café Barista", "Coffee", false, 1, 15),
            T(42.75, "Pollo Campero", "Restaurants", false, 1, 20),
            T(12.0, "Bus Transmetro", "Transport", false, 2, 7),
            T(85.0, "Energía eléctrica", "Utilities", false, 3, 10),
            T(600.0, "Diseño de logo", "Freelance", true, 4, 17),
            T(27.9, "Farmacia", "Health", false, 5, 12),
            T(120.0, "Supermercado", "Groceries", false, 6, 11),
            T(35.0, "Plan celular", "Phone", false, 8, 9, recurrent = true),
            T(9.5, "Desayuno", "Coffee", false, 9, 8),
            T(64.0, "Cena familiar", "Restaurants", false, 10, 20),
            T(2450.0, "Salario octubre", "Salary", true, 32, 8, recurrent = true),
            T(410.0, "Supermercado", "Groceries", false, 35, 10),
            T(95.0, "Gasolina", "Transport", false, 40, 18),
        ).forEach { t ->
            transactionDao.insert(
                TransactionEntity(
                    amount = t.amount, description = t.desc,
                    date = now.minusDays(t.daysAgo).withHour(t.hour).ms(),
                    type = if (t.income) "INCOME" else "EXPENSE", isCleared = true,
                    categoryId = ids[t.cat], isRecurrent = t.recurrent,
                    recurrenceFrequency = if (t.recurrent) "MONTHLY" else null,
                    recurrenceInterval = if (t.recurrent) 1 else null,
                )
            )
        }
        data class L(val name: String, val desc: String, val total: Double, val n: Int, val cat: String?, val monthsAgo: Long, val income: Boolean, val paid: Int)
        listOf(
            L("Moto Italika", "Crédito Banco Industrial", 3600.0, 12, "Transport", 5, false, 5),
            L("Préstamo a Carlos", "Para su laptop", 900.0, 6, null, 3, true, 2),
            L("Refrigeradora", "Tienda Max, 0% interés", 1200.0, 6, "Home", 6, false, 6),
        ).forEach { l ->
            val start = now.minusMonths(l.monthsAgo).withDayOfMonth(1).withHour(9)
            val loanId = loanDao.insert(
                LoanEntity(
                    name = l.name, description = l.desc, totalAmount = l.total, totalInstallments = l.n,
                    categoryId = l.cat?.let { ids[it] }, startDate = start.ms(),
                    loanType = if (l.income) "INCOME" else "EXPENSE", frequency = "MONTHLY", frequencyInterval = 1,
                )
            )
            (1..l.n).forEach { k ->
                transactionDao.insert(
                    TransactionEntity(
                        amount = l.total / l.n, description = l.name,
                        date = start.plusMonths(k.toLong() - 1).ms(),
                        type = if (l.income) "INCOME" else "EXPENSE", isCleared = k <= l.paid,
                        categoryId = l.cat?.let { ids[it] }, loanId = loanId, installmentNumber = k,
                    )
                )
            }
        }
    }

    private companion object { const val TAG = "DemoData" }
}
