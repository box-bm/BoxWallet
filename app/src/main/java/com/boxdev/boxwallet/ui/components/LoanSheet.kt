package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.ui.screens.loans.LoanFormViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanSheet(
    loanId: Long?,
    onDismiss: () -> Unit,
    viewModel: LoanFormViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")

    val handleCloseDatePicker = { showDatePicker = false }
    val handleCloseDeleteDialog = { showDeleteDialog = false }
    val handleCloseCategoryDialog = { showCategoryDialog = false }


    LaunchedEffect(loanId) { viewModel.loadLoan(loanId) }
    LaunchedEffect(formState.isSaved) { if (formState.isSaved) onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (formState.isEditing) "Edit Loan" else "New Loan",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (formState.isEditing) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (formState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = formState.name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("Loan Name") },
                        placeholder = { Text("e.g. Car loan, Mortgage") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = formState.description,
                        onValueChange = { viewModel.updateDescription(it) },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    OutlinedTextField(
                        value = formState.totalAmount,
                        onValueChange = { viewModel.updateTotalAmount(it) },
                        label = { Text("Total Amount") },
                        placeholder = { Text("0.00") },
                        leadingIcon = { Text(formState.currency, style = MaterialTheme.typography.bodyLarge) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = formState.error != null && formState.totalAmount.isEmpty()
                    )

                    OutlinedTextField(
                        value = formState.installments,
                        onValueChange = { viewModel.updateInstallments(it) },
                        label = { Text("Number of Installments") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    formState.installmentAmount?.let { amount ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                text = "Each installment: ${formState.currency} ${"%.2f".format(amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = formState.startDate.format(dateFormatter),
                        onValueChange = {},
                        label = { Text("Start Date") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = formState.categoryId?.let { id -> categories.find { it.id == id }?.name } ?: "None",
                            onValueChange = {},
                            label = { Text("Category (Optional)") },
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.matchParentSize().clickable { showCategoryDialog = true })
                    }
                    if (formState.categoryId != null) {
                        TextButton(onClick = { viewModel.updateCategory(null) }, modifier = Modifier.align(Alignment.End)) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.save()
                                  },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = formState.isValid
                    ) {
                        Text(if (formState.isEditing) "Update" else "Create Loan", style = MaterialTheme.typography.titleMedium)
                    }
                    if (formState.isEditing) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Delete Loan")
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = formState.startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = handleCloseDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.updateStartDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }
                    handleCloseDatePicker()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = handleCloseDatePicker) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = handleCloseDeleteDialog,
            title = { Text("Delete Loan") },
            text = { Text("This will also delete all ${formState.installments} installments. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    handleCloseDeleteDialog()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = handleCloseDeleteDialog) { Text("Cancel") } }
        )
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = handleCloseCategoryDialog,
            title = { Text("Select Category") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (categories.isEmpty()) {
                        Text("No categories available.\nCreate one in Settings.", modifier = Modifier.padding(16.dp))
                    } else {
                        categories.forEach { category ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { viewModel.updateCategory(category.id); }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(category.name, modifier = Modifier.weight(1f))
                                if (formState.categoryId == category.id) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = handleCloseCategoryDialog) { Text("Close") } }
        )
    }
}
