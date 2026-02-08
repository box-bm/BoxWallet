@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.ui.screens.transaction.TransactionFormViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransactionSheet(
    transactionId: Long?,
    onDismiss: () -> Unit,
    viewModel: TransactionFormViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }
    var showReminderDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")

    LaunchedEffect(transactionId) { viewModel.loadTransaction(transactionId) }
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
                    text = if (formState.isEditing) "Edit Transaction" else "New Transaction",
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
                    LoadingIndicator()
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilterChip(
                                selected = formState.type == TransactionType.EXPENSE,
                                onClick = { viewModel.updateType(TransactionType.EXPENSE) },
                                label = { Text("Expense") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = formState.type == TransactionType.INCOME,
                                onClick = { viewModel.updateType(TransactionType.INCOME) },
                                label = { Text("Income") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = formState.amount,
                        onValueChange = { viewModel.updateAmount(it) },
                        label = { Text("Amount") },
                        placeholder = { Text("0.00") },
                        leadingIcon = { Text(formState.currency, style = MaterialTheme.typography.bodyLarge) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = formState.error != null && formState.amount.isEmpty()
                    )

                    OutlinedTextField(
                        value = formState.description,
                        onValueChange = { viewModel.updateDescription(it) },
                        label = { Text("Description") },
                        placeholder = { Text("Enter a description") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = formState.categoryId?.let { id -> categories.find { it.id == id }?.name } ?: "None",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category (Optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = { viewModel.updateCategory(null); categoryExpanded = false },
                                leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null) }
                            )
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = { viewModel.updateCategory(category.id); categoryExpanded = false },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = com.boxdev.boxwallet.util.categoryIconVector(category.icon),
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = {
                                        if (formState.categoryId == category.id) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = formState.date.format(dateFormatter),
                        onValueChange = {},
                        label = { Text("Date") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Cleared", style = MaterialTheme.typography.titleMedium)
                                Text("Mark as reconciled", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = formState.isCleared, onCheckedChange = { viewModel.updateIsCleared(it) })
                        }
                    }

                    if (!formState.isCleared)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Reminder", style = MaterialTheme.typography.titleMedium)
                                    Text("Get notified", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = formState.hasReminder, onCheckedChange = { viewModel.updateHasReminder(it) })
                            }
                            if (formState.hasReminder) {
                                OutlinedTextField(
                                    value = formState.reminderDate?.format(dateFormatter) ?: "",
                                    onValueChange = {},
                                    label = { Text("Reminder Date") },
                                    readOnly = true,
                                    trailingIcon = {
                                        IconButton(onClick = { showReminderDatePicker = true }) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)
                                )
                            }
                        }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.save() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = formState.isValid
                    ) {
                        Text(if (formState.isEditing) "Update" else "Save", style = MaterialTheme.typography.titleMedium)
                    }
                    if (formState.isEditing) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = formState.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.updateDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showReminderDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (formState.reminderDate ?: formState.date)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showReminderDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.updateReminderDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }
                    showReminderDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showReminderDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(); showDeleteDialog = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}
