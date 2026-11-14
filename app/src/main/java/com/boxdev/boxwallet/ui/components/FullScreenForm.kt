@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.remember
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
 * M3 full-screen dialog scaffold for create/edit forms: ✕ close, title and one filled Save in the top
 * bar, with the [content] in a scrolling column capped at 640dp and centered. Save is never disabled:
 * the form validates on save and reports what is missing in line. Delete is not a top-bar action; it
 * closes the form as a text button in the error color, labelled [deleteLabel].
 *
 * ✕ and system back both close; when [hasUnsavedChanges] they first ask to discard the edits.
 * Without edits, system back is left to the host (the NavHost pops the entry with the predictive-back
 * animation, and so never calls [onClose]). Pass [scrollState] to scroll the form from outside, for
 * example back to an error reported at the top.
 *
 * [embedded] draws the same form inside a pane beside a list: transparent, without window insets
 * and without the ✕, since the selection in the list is what closes it.
 */
@Composable
fun FullScreenForm(
    title: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    hasUnsavedChanges: Boolean,
    deleteLabel: String = stringResource(R.string.action_delete),
    embedded: Boolean = false,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val requestClose: () -> Unit = {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    BackHandler(enabled = hasUnsavedChanges, onBack = requestClose)

    Scaffold(
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentWindowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (!embedded) {
                        IconButton(onClick = requestClose) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = onSave,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                    ) { Text(stringResource(R.string.action_save)) }
                },
                windowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                colors = if (embedded) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    )
                } else TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        // In a pane the shell already keeps clear of the navigation bar; the keyboard still has to be cleared.
        val insets = if (embedded) Modifier.imePadding() else Modifier.imePadding().navigationBarsPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .then(insets)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (embedded) 16.dp else BoxTheme.layout.margin)
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                content()
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(top = 24.dp).align(Alignment.CenterHorizontally),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(deleteLabel)
                    }
                }
            }
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
