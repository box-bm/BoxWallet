package com.boxdev.boxwallet.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.rates.ChangeSummary
import com.boxdev.boxwallet.worker.MainCurrencyChangeWorker

/** Spec 4: warning, two-phase progress (cancellable only while downloading) and summary. */
@Composable
fun MainCurrencyChangeScreen(
    onNavigateBack: () -> Unit,
    viewModel: MainCurrencyChangeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Leaving mid-transaction would let a stale form save race the change; stay until it ends.
    val converting = state is MainCurrencyChangeState.Converting
    BackHandler(enabled = converting) {}
    SettingsScaffold(
        title = stringResource(R.string.main_currency_change_title),
        onNavigateBack = onNavigateBack,
        showBack = !converting,
    ) {
        when (val s = state) {
            MainCurrencyChangeState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is MainCurrencyChangeState.Warning -> Warning(s, onConfirm = viewModel::confirm, onCancel = onNavigateBack)
            is MainCurrencyChangeState.Downloading -> Phase(
                title = stringResource(R.string.main_currency_change_phase_download),
                detail = if (s.total == 0) stringResource(R.string.main_currency_change_phase_download_preparing)
                else stringResource(R.string.main_currency_change_phase_download_progress, s.done, s.total),
                note = stringResource(R.string.main_currency_change_phase_download_note),
                done = s.done,
                total = s.total,
            ) {
                OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.main_currency_change_cancel))
                }
            }
            is MainCurrencyChangeState.Converting -> Phase(
                title = stringResource(R.string.main_currency_change_phase_apply),
                detail = if (s.total == 0) stringResource(R.string.main_currency_change_phase_apply_preparing)
                else stringResource(R.string.main_currency_change_phase_apply_progress, s.done, s.total),
                note = stringResource(R.string.main_currency_change_phase_apply_note),
                done = s.done,
                total = s.total,
            )
            is MainCurrencyChangeState.Failed -> Failed(s.reason, onRetry = viewModel::confirm, onCancel = onNavigateBack)
            is MainCurrencyChangeState.Done -> Done(s.to.currencyCode, s.summary, onDone = onNavigateBack)
        }
    }
}

@Composable
private fun Warning(state: MainCurrencyChangeState.Warning, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val p = state.preview
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(
                R.string.main_currency_change_warning,
                pluralStringResource(R.plurals.main_currency_change_movements, p.transactions, p.transactions),
                pluralStringResource(R.plurals.main_currency_change_loans, p.loans, p.loans),
                p.from.currencyCode,
                p.to.currencyCode,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(R.string.main_currency_change_warning_detail, p.from.currencyCode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Actions {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.main_currency_change_cancel))
            }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.main_currency_change_confirm, p.to.currencyCode))
            }
        }
    }
}

@Composable
private fun Phase(
    title: String,
    detail: String,
    note: String,
    done: Int,
    total: Int,
    action: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
        if (total == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
private fun Failed(reason: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    val message = when (reason) {
        MainCurrencyChangeWorker.ERROR_NETWORK -> R.string.main_currency_change_failed_network
        MainCurrencyChangeWorker.ERROR_MISSING_RATE -> R.string.main_currency_change_failed_missing_rate
        else -> R.string.main_currency_change_failed_unknown
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Actions {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.main_currency_change_cancel))
            }
            Button(onClick = onRetry, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.main_currency_change_retry))
            }
        }
    }
}

@Composable
private fun Done(code: String, summary: ChangeSummary, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.main_currency_change_done_title, code),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        SummaryLine(stringResource(R.string.main_currency_change_done_converted), summary.converted)
        SummaryLine(stringResource(R.string.main_currency_change_done_approximate), summary.approximate)
        SummaryLine(stringResource(R.string.main_currency_change_done_manual), summary.manualReplaced)
        if (summary.approximate > 0) {
            Text(
                stringResource(R.string.main_currency_change_done_approximate_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.main_currency_change_done))
        }
    }
}

/** Label and count read as one item by TalkBack. */
@Composable
private fun SummaryLine(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Actions(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
