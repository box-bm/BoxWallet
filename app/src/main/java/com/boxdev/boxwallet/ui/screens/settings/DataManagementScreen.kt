@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Warning Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.data_management_caution),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.data_management_caution_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Device Data Section
            DataManagementSection(title = stringResource(R.string.data_management_device_data)) {
                DangerousActionCard(
                    title = stringResource(R.string.data_management_clear_all_title),
                    description = stringResource(R.string.data_management_clear_all_description),
                    buttonText = stringResource(R.string.data_management_clear_all_button),
                    isLoading = uiState.isClearing,
                    onClick = { showConfirmDialog = ClearDataType.ALL_DEVICE_DATA }
                )

                DangerousActionCard(
                    title = stringResource(R.string.data_management_clear_transactions_title),
                    description = stringResource(R.string.data_management_clear_transactions_description),
                    buttonText = stringResource(R.string.data_management_clear_transactions_button),
                    isLoading = uiState.isClearing,
                    onClick = { showConfirmDialog = ClearDataType.TRANSACTIONS_ONLY }
                )

                DangerousActionCard(
                    title = stringResource(R.string.data_management_clear_categories_title),
                    description = stringResource(R.string.data_management_clear_categories_description),
                    buttonText = stringResource(R.string.data_management_clear_categories_button),
                    isLoading = uiState.isClearing,
                    onClick = { showConfirmDialog = ClearDataType.CATEGORIES_ONLY }
                )
            }

            // Backup Shortcut
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.data_management_backup_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.data_management_backup_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onNavigateToBackup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.data_management_go_to_backup))
                    }
                }
            }

            // Error Display
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    // Confirmation Dialog
    showConfirmDialog?.let { clearType ->
        ClearDataConfirmationDialog(
            clearType = clearType,
            onConfirm = {
                when (clearType) {
                    ClearDataType.ALL_DEVICE_DATA -> viewModel.clearAll()
                    ClearDataType.TRANSACTIONS_ONLY -> viewModel.clearTransactions()
                    ClearDataType.CATEGORIES_ONLY -> viewModel.clearCategories()
                }

            },
            onDismiss = {  }
        )
    }
}

@Composable
private fun DataManagementSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun DangerousActionCard(
    title: String,
    description: String,
    buttonText: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onClick,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(buttonText)
                }
            }
        }
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
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
