package com.boxdev.boxwallet.ui.screens.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.rates.ExchangeRateApi
import com.boxdev.boxwallet.data.rates.ExchangeRateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

data class CurrencyDetailUiState(
    val code: String,
    val main: Currency = Currency.getInstance("USD"),
    val range: RateRange = RateRange.M3,
    val series: List<Pair<LocalDate, BigDecimal>> = emptyList(),
    val stats: RateStats? = null,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CurrencyDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    rates: ExchangeRateRepository,
    prefs: UserPreferences,
) : ViewModel() {

    val code: String = checkNotNull(savedState.get<String>("code"))
    private val range = MutableStateFlow(RateRange.M3)

    val uiState: StateFlow<CurrencyDetailUiState> = combine(prefs.mainCurrency, range) { main, r -> main to r }
        .flatMapLatest { (main, r) ->
            val from = r.days?.let { LocalDate.now().minusDays(it) } ?: ExchangeRateApi.HISTORY_START
            rates.series(code, main, from).map { s ->
                CurrencyDetailUiState(code, main, r, s, rateStats(s), loaded = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrencyDetailUiState(code))

    fun selectRange(r: RateRange) { range.value = r }
}
