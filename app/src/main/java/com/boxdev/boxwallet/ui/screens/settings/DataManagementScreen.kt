@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R

enum class ClearDataType {
    ALL_DEVICE_DATA,
    TRANSACTIONS_ONLY,
    CATEGORIES_ONLY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBackup: () -> Unit,
    viewModel: DataManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showConfirmDialog by remember { mutableStateOf<ClearDataType?>(null) }

    LaunchedEffect(uiState.clearSuccess) {
        if (uiState.clearSuccess) {
            viewModel.resetState()
        }
    }

    SettingsScaffold(title = stringResource(R.string.data_management_title), onNavigateBack = onNavigateBack) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.data_management_caution), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.data_management_caution_message), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SettingsGroup(title = null) {
            SettingsRow(
                index = 0, count = 1,
                icon = Icons.Outlined.Backup,
                title = stringResource(R.string.data_management_backup_title),
                subtitle = stringResource(R.string.data_management_backup_description),
                onClick = onNavigateToBackup,
            )
        }

        SettingsGroup(stringResource(R.string.data_management_device_data)) {
            val rows = listOf(
                Triple(ClearDataType.TRANSACTIONS_ONLY, R.string.data_management_clear_transactions_title, R.string.data_management_clear_transactions_description),
                Triple(ClearDataType.CATEGORIES_ONLY, R.string.data_management_clear_categories_title, R.string.data_management_clear_categories_description),
                Triple(ClearDataType.ALL_DEVICE_DATA, R.string.data_management_clear_all_title, R.string.data_management_clear_all_description),
            )
            rows.forEachIndexed { index, (type, title, description) ->
                SettingsRow(
                    index = index, count = rows.size,
                    icon = if (type == ClearDataType.ALL_DEVICE_DATA) Icons.Outlined.DeleteForever else Icons.Outlined.DeleteSweep,
                    iconContainer = MaterialTheme.colorScheme.errorContainer,
                    iconContent = MaterialTheme.colorScheme.onErrorContainer,
                    title = stringResource(title),
                    titleColor = MaterialTheme.colorScheme.error,
                    subtitle = stringResource(description),
                    onClick = if (uiState.isClearing) null else ({ showConfirmDialog = type }),
                    trailing = if (uiState.isClearing) ({ LoadingIndicator(Modifier.size(32.dp)) }) else null,
                )
            }
        }

        uiState.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

    showConfirmDialog?.let { clearType ->
        ClearDataConfirmationDialog(
            clearType = clearType,
            onConfirm = {
                when (clearType) {
                    ClearDataType.ALL_DEVICE_DATA -> viewModel.clearAll()
                    ClearDataType.TRANSACTIONS_ONLY -> viewModel.clearTransactions()
                    ClearDataType.CATEGORIES_ONLY -> viewModel.clearCategories()
                }
                showConfirmDialog = null
            },
            onDismiss = { showConfirmDialog = null }
        )
    }
}

@Composable
private fun ClearDataConfirmationDialog(
    clearType: ClearDataType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, message) = when (clearType) {
        ClearDataType.ALL_DEVICE_DATA -> Pair(
            stringResource(R.string.data_management_confirm_all_title),
            stringResource(R.string.data_management_confirm_all_message)
        )
        ClearDataType.TRANSACTIONS_ONLY -> Pair(
            stringResource(R.string.data_management_confirm_transactions_title),
            stringResource(R.string.data_management_confirm_transactions_message)
        )
        ClearDataType.CATEGORIES_ONLY -> Pair(
            stringResource(R.string.data_management_confirm_categories_title),
            stringResource(R.string.data_management_confirm_categories_message)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            ) {
                Text(stringResource(R.string.data_management_clear_data))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
