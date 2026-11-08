package com.boxdev.boxwallet.ui.screens.settings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.boxdev.boxwallet.data.rates.ChangePreview
import com.boxdev.boxwallet.data.rates.ChangeSummary
import com.boxdev.boxwallet.data.rates.MainCurrencyChanger
import com.boxdev.boxwallet.worker.MainCurrencyChangeWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.UUID
import javax.inject.Inject

sealed interface MainCurrencyChangeState {
    data object Loading : MainCurrencyChangeState
    data class Warning(val preview: ChangePreview) : MainCurrencyChangeState
    /** Phase 1: downloading rates, cancellable. */
    data class Downloading(val done: Int, val total: Int) : MainCurrencyChangeState
    /** Phase 2: the database transaction, not cancellable. */
    data class Converting(val done: Int, val total: Int) : MainCurrencyChangeState
    data class Failed(val reason: String) : MainCurrencyChangeState
    data class Done(val to: Currency, val summary: ChangeSummary) : MainCurrencyChangeState
}

@HiltViewModel
class MainCurrencyChangeViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val changer: MainCurrencyChanger,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    val to: Currency = Currency.getInstance(checkNotNull(savedState.get<String>("code")))

    private val _state = MutableStateFlow<MainCurrencyChangeState>(MainCurrencyChangeState.Loading)
    val state: StateFlow<MainCurrencyChangeState> = _state.asStateFlow()

    /** The run this screen follows, so an older finished run is never shown as this one. */
    private var runId: UUID? = null

    init {
        loadPreview()
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(MainCurrencyChangeWorker.WORK_NAME)
                .collectLatest { infos -> infos.forEach(::onWork) }
        }
    }

    fun confirm() {
        _state.value = MainCurrencyChangeState.Downloading(0, 0)
        runId = MainCurrencyChangeWorker.enqueue(context, to)
    }

    /** Only offered during the download phase; the worker ignores it once converting. */
    fun cancel() {
        MainCurrencyChangeWorker.cancel(context)
    }

    private fun loadPreview() {
        viewModelScope.launch {
            val preview = changer.preview(to)
            if (runId == null) _state.value = MainCurrencyChangeState.Warning(preview)
        }
    }

    private fun onWork(info: WorkInfo) {
        // A run already in progress (e.g. the screen was reopened) is adopted.
        if (runId == null && !info.state.isFinished) runId = info.id
        if (info.id != runId) return
        val data = if (info.state.isFinished) info.outputData else info.progress
        _state.value = when (info.state) {
            WorkInfo.State.SUCCEEDED -> MainCurrencyChangeState.Done(
                to,
                ChangeSummary(
                    converted = data.getInt(MainCurrencyChangeWorker.KEY_CONVERTED, 0),
                    approximate = data.getInt(MainCurrencyChangeWorker.KEY_APPROXIMATE, 0),
                    manualReplaced = data.getInt(MainCurrencyChangeWorker.KEY_MANUAL_REPLACED, 0),
                ),
            )
            WorkInfo.State.FAILED -> MainCurrencyChangeState.Failed(
                data.getString(MainCurrencyChangeWorker.KEY_ERROR) ?: MainCurrencyChangeWorker.ERROR_UNKNOWN
            )
            WorkInfo.State.CANCELLED -> {
                runId = null
                loadPreview()
                return
            }
            else -> {
                val done = data.getInt(MainCurrencyChangeWorker.KEY_DONE, 0)
                val total = data.getInt(MainCurrencyChangeWorker.KEY_TOTAL, 0)
                when (data.getString(MainCurrencyChangeWorker.KEY_PHASE)) {
                    MainCurrencyChangeWorker.PHASE_APPLY -> MainCurrencyChangeState.Converting(done, total)
                    else -> MainCurrencyChangeState.Downloading(done, total)
                }
            }
        }
    }
}
