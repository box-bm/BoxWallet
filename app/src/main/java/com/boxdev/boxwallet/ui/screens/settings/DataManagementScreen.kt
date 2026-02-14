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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

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
                title = { Text("Data Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            text = "Caution",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Clearing data is permanent and cannot be undone. Make sure you have a backup before proceeding.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Device Data Section
            DataManagementSection(title = "Device Data") {
                DangerousActionCard(
                    title = "Clear All Device Data",
                    description = "Delete all transactions, categories, and settings from this device",
                    buttonText = "Clear All Data",
                    isLoading = uiState.isClearing,
                    onClick = { showConfirmDialog = ClearDataType.ALL_DEVICE_DATA }
                )

                DangerousActionCard(
                    title = "Clear Transactions",
                    description = "Delete all transaction records while keeping categories and settings",
                    buttonText = "Clear Transactions",
                    isLoading = uiState.isClearing,
                    onClick = { showConfirmDialog = ClearDataType.TRANSACTIONS_ONLY }
                )

                DangerousActionCard(
                    title = "Clear Categories",
                    description = "Delete all categories. Transactions will keep their category references.",
                    buttonText = "Clear Categories",
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
                                text = "Backup & Restore",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Back up your data to Google Drive before clearing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onNavigateToBackup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Go to Backup & Restore")
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
            "Clear All Device Data?",
            "This will permanently delete ALL transactions, categories, and settings from this device. This action cannot be undone."
        )
        ClearDataType.TRANSACTIONS_ONLY -> Pair(
            "Clear All Transactions?",
            "This will permanently delete ALL transaction records from this device. Categories and settings will be preserved. This action cannot be undone."
        )
        ClearDataType.CATEGORIES_ONLY -> Pair(
            "Clear All Categories?",
            "This will permanently delete ALL categories from this device. Transactions will keep their category references but won't show category names. This action cannot be undone."
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
                Text("Clear Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
