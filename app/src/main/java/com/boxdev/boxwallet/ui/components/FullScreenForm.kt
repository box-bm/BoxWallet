@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme

/**
 * M3 full-screen dialog scaffold for create/edit forms: ✕ close, title, optional delete and a filled
 * Save in the top bar, with the [content] in a scrolling column capped at 640dp and centered.
 *
 * ✕ and system back both close; when [hasUnsavedChanges] they first ask to discard the edits.
 */
@Composable
fun FullScreenForm(
    title: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    onDelete: (() -> Unit)?,
    hasUnsavedChanges: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val requestClose: () -> Unit = {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler(enabled = true, onBack = requestClose)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Button(
                        onClick = onSave,
                        enabled = saveEnabled,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                    ) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = BoxTheme.layout.margin)
                    .padding(top = 8.dp, bottom = 24.dp),
                content = content,
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.form_discard_title)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onClose() }) {
                    Text(stringResource(R.string.form_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.form_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun FullScreenFormPreviewContent() {
    BoxWalletTheme(dynamicColor = false) {
        FullScreenForm(
            title = "Edit movement",
            onClose = {},
            onSave = {},
            saveEnabled = true,
            onDelete = {},
            hasUnsavedChanges = false,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                OutlinedTextField(value = "42.00", onValueChange = {}, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = "Groceries", onValueChange = {}, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(name = "Light", widthDp = 411, heightDp = 600)
@Composable
private fun FullScreenFormPreview() = FullScreenFormPreviewContent()

@Preview(name = "Dark", widthDp = 411, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FullScreenFormDarkPreview() = FullScreenFormPreviewContent()
