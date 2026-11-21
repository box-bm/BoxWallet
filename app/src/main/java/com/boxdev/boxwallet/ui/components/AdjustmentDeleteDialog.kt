package com.boxdev.boxwallet.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.isInflow
import com.boxdev.boxwallet.domain.model.Transaction

/**
 * Asks before deleting a balance adjustment. Adjustments have no editor, because the movement
 * form only knows income and expense, so tapping one in a list opens this instead.
 */
@Composable
fun AdjustmentDeleteDialog(
    adjustment: Transaction,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transaction_adjustment)) },
        text = {
            AmountText(
                amount = adjustment.original,
                kind = amountKind(adjustment.type.isInflow),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
