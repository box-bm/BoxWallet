@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.boxdev.boxwallet.ui.screens.transaction.TransactionFormErrorText
import com.boxdev.boxwallet.ui.screens.transaction.TransactionFormFields
import com.boxdev.boxwallet.ui.screens.transaction.TransactionFormViewModel

@Composable
fun TransactionSheet(
    transactionId: Long?,
    onDismiss: () -> Unit,
    viewModel: TransactionFormViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) { viewModel.loadTransaction(transactionId) }
    LaunchedEffect(formState.isSaved) { if (formState.isSaved) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SheetHeader(
                title = stringResource(if (formState.isEditing) R.string.transaction_edit_title else R.string.transaction_new_title),
                onDelete = if (formState.isEditing) ({ showDeleteDialog = true }) else null,
            )

            if (formState.isLoading) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@Column
            }

            TransactionFormFields(
                viewModel = viewModel,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            )

            // Outside the scrolling fields so it's visible right above the button that failed.
            formState.error?.let { TransactionFormErrorText(it) }

            SheetPrimaryButton(
                text = stringResource(if (formState.isEditing) R.string.transaction_update else R.string.transaction_save),
                enabled = formState.canSave,
                onClick = viewModel::save,
            )
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.transaction_delete_title),
            message = stringResource(R.string.transaction_delete_message),
            onConfirm = viewModel::delete,
            onDismiss = { showDeleteDialog = false }
        )
    }
}
