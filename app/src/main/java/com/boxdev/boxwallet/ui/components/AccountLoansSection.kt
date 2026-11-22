@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Loan

/**
 * An account's loans under its balance: one row per open loan, paid-off ones folded behind
 * "Show settled (n)", and the action to lend or borrow from this account ([onNewLoan] is null when the
 * account can't take a new one, e.g. archived). With no loans only the action shows.
 */
@Composable
fun AccountLoansSection(
    /** Keys the "show settled" choice, so another account's detail starts folded. */
    accountId: Long,
    active: List<Loan>,
    settled: List<Loan>,
    onOpenLoan: (Long) -> Unit,
    onNewLoan: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (active.isEmpty() && settled.isEmpty() && onNewLoan == null) return
    var showSettled by rememberSaveable(accountId) { mutableStateOf(false) }
    val shown = if (showSettled) active + settled else active
    Column(modifier) {
        SectionHeader(
            title = stringResource(R.string.account_loans_title),
            actionLabel = if (settled.isNotEmpty()) {
                if (showSettled) stringResource(R.string.account_loans_hide_settled)
                else stringResource(R.string.account_loans_show_settled, settled.size)
            } else null,
            onAction = { showSettled = !showSettled },
        )
        shown.forEachIndexed { index, loan ->
            LoanRow(loan = loan, index = index, count = shown.size, onClick = { onOpenLoan(loan.id) })
        }
        if (onNewLoan != null) {
            FilledTonalButton(
                onClick = onNewLoan,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(top = if (shown.isEmpty()) 0.dp else 6.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.account_loans_new))
            }
        }
    }
}
