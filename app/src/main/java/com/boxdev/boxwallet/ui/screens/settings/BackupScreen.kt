@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.ui.components.segmentShapes

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedListItem
import com.boxdev.boxwallet.ui.components.ConfirmDeleteDialog
import com.boxdev.boxwallet.ui.components.EmptyState
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }
    var showRestoreDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleSignInResult(result.data)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackBarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    showRestoreDialog?.let { fileId ->
        AlertDialog(
            onDismissRequest = { showRestoreDialog = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreDialog = null
                    viewModel.restoreBackup(fileId)
                }) {
                    Text(stringResource(R.string.backup_restore_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    showDeleteDialog?.let { fileId ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.backup_delete_title),
            message = stringResource(R.string.backup_delete_message),
            onConfirm = { viewModel.deleteBackup(fileId) },
            onDismiss = { showDeleteDialog = null },
        )
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }

    SettingsScaffold(
        title = stringResource(R.string.backup_title),
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackBarHostState,
    ) {
        if (!uiState.isSignedIn) {
            EmptyState(
                icon = Icons.Outlined.CloudUpload,
                title = stringResource(R.string.backup_drive_title),
                message = stringResource(R.string.backup_drive_description),
                modifier = Modifier.padding(top = 32.dp),
                action = {
                    Button(
                        onClick = { signInLauncher.launch(viewModel.getSignInIntent()) },
                        enabled = !uiState.isLoading,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.MediumContentPadding,
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) {
                        if (uiState.isLoading) LoadingIndicator(Modifier.size(24.dp).padding(end = 8.dp))
                        Text(stringResource(R.string.backup_sign_in), style = MaterialTheme.typography.titleMedium)
                    }
                },
            )
            return@SettingsScaffold
        }

        SettingsGroup(title = null) {
            SettingsRow(
                index = 0, count = 2,
                icon = Icons.Outlined.AccountCircle,
                title = uiState.accountDisplayName ?: (uiState.accountEmail ?: ""),
                subtitle = uiState.accountEmail?.takeIf { uiState.accountDisplayName != null },
                trailing = {
                    TextButton(onClick = viewModel::signOut) { Text(stringResource(R.string.backup_sign_out)) }
                },
            )
            SettingsRow(
                index = 1, count = 2,
                icon = Icons.Outlined.CloudUpload,
                iconContainer = MaterialTheme.colorScheme.primary,
                iconContent = MaterialTheme.colorScheme.onPrimary,
                title = stringResource(R.string.backup_to_drive),
                onClick = if (uiState.isLoading) null else viewModel::createBackup,
                trailing = if (uiState.isLoading) ({ LoadingIndicator(Modifier.size(32.dp)) }) else null,
            )
        }

        SettingsGroup(stringResource(R.string.backup_available)) {
            when {
                uiState.isLoadingBackups -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                uiState.backupFiles.isEmpty() -> Text(
                    stringResource(R.string.backup_no_backups),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                else -> uiState.backupFiles.forEachIndexed { index, file ->
                    SegmentedListItem(
                        onClick = { showRestoreDialog = file.id },
                        shapes = segmentShapes(index = index, count = uiState.backupFiles.size),
                        colors = settingsRowColors(),
                        leadingContent = { SettingsIcon(Icons.Outlined.Restore) },
                        supportingContent = { Text(file.formattedSize) },
                        trailingContent = {
                            IconButton(onClick = { showDeleteDialog = file.id }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                            }
                        },
                    ) { Text(dateFormat.format(Date(file.createdTime))) }
                }
            }
        }
    }
}
