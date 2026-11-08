package com.boxdev.boxwallet.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.CurrencyUsage
import com.boxdev.boxwallet.data.rates.ExchangeRateRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

data class RateStats(
    val current: BigDecimal,
    val min: BigDecimal,
    val max: BigDecimal,
    /** Change of the newest day against the previous one, in percent (2 decimals), if computable. */
    val dailyChangePercent: BigDecimal?,
)

enum class RateRange(val days: Long?) { M1(30), M3(90), Y1(365), ALL(null) }

/** Change from [previous] to [current] in percent, 2 decimals HALF_EVEN; null when [previous] is zero. */
fun percentChange(current: BigDecimal, previous: BigDecimal): BigDecimal? =
    previous.takeIf { it.signum() != 0 }
        ?.let { current.subtract(it).multiply(BigDecimal(100)).divide(it, 2, RoundingMode.HALF_EVEN) }

fun rateStats(series: List<Pair<LocalDate, BigDecimal>>): RateStats? {
    if (series.isEmpty()) return null
    val sorted = series.sortedBy { it.first }
    val current = sorted.last().second
    val previous = sorted.getOrNull(sorted.lastIndex - 1)?.second
    val values = sorted.map { it.second }
    return RateStats(current, values.min(), values.max(), previous?.let { percentChange(current, it) })
}

data class CurrencyRateRow(val currency: Currency, val current: BigDecimal, val dailyChangePercent: BigDecimal?)

data class CurrenciesUiState(
    val main: Currency = Currency.getInstance("USD"),
    val lastSyncAt: Long? = null,
    val rows: List<CurrencyRateRow> = emptyList(),
    val recent: List<CurrencyUsage> = emptyList(),
    val syncing: Boolean = false,
    val syncFailed: Boolean = false,
)

@HiltViewModel
class CurrenciesViewModel @Inject constructor(
    private val rates: ExchangeRateRepository,
    prefs: UserPreferences,
    transactions: TransactionRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(false to false)

    val uiState: StateFlow<CurrenciesUiState> = combine(
        prefs.mainCurrency,
        prefs.lastRateSyncAt,
        transactions.recentCurrencies(),
        transient,
    ) { main, lastSync, recent, (syncing, failed) ->
        val rows = recent
            .filter { it.currencyCode != main.currencyCode }
            .mapNotNull { usage ->
                val currency = runCatching { Currency.getInstance(usage.currencyCode) }.getOrNull()
                    ?: return@mapNotNull null
                val (current, previous) = rates.latest(usage.currencyCode, main) ?: return@mapNotNull null
                CurrencyRateRow(currency, current, previous?.let { percentChange(current, it) })
            }
        CurrenciesUiState(main, lastSync, rows, recent, syncing, failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrenciesUiState())

    fun refresh() {
        if (transient.value.first) return
        viewModelScope.launch {
            transient.update { true to false }
            val ok = runCatching { rates.syncLatest() }.getOrDefault(false)
            transient.update { false to !ok }
        }
    }

    fun failureShown() = transient.update { it.first to false }
}
