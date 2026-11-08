@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.ui.components.segmentShapes

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.SegmentedListItem
import com.boxdev.boxwallet.ui.components.CategoryBadge
import com.boxdev.boxwallet.ui.components.ConfirmDeleteDialog
import com.boxdev.boxwallet.ui.components.EmptyState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.util.categoryIconOptions

@Composable
fun CategoriesScreen(
    onNavigateBack: (() -> Unit)?,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deleteConfirmCategory by remember { mutableStateOf<Category?>(null) }

    SettingsScaffold(
        title = stringResource(R.string.categories_title),
        onNavigateBack = onNavigateBack,
        floatingActionButton = {
            MediumExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.categories_add)) },
            )
        },
    ) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            uiState.categories.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Category,
                title = stringResource(R.string.categories_no_categories),
                message = stringResource(R.string.categories_tap_to_create),
            )
            else -> SettingsGroup(title = null) {
                val count = uiState.categories.size
                uiState.categories.forEachIndexed { index, category ->
                    val interaction = remember { MutableInteractionSource() }
                    SegmentedListItem(
                        onClick = { editingCategory = category },
                        shapes = segmentShapes(index = index, count = count),
                        colors = settingsRowColors(),
                        interactionSource = interaction,
                        leadingContent = { CategoryBadge(category = category, interactionSource = interaction) },
                        supportingContent = category.description.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 2) } },
                        trailingContent = {
                            IconButton(onClick = { deleteConfirmCategory = category }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                            }
                        },
                    ) { Text(category.name) }
                }
            }
        }
        Spacer(Modifier.height(88.dp))
    }

    if (showAddDialog) {
        AddEditCategoryDialog(
            category = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, description, icon ->
                viewModel.addCategory(name, description, icon)
                showAddDialog = false
            }
        )
    }

    editingCategory?.let { category ->
        AddEditCategoryDialog(
            category = category,
            onDismiss = { editingCategory = null },
            onSave = { name, description, icon ->
                viewModel.updateCategory(category.copy(name = name, description = description, icon = icon))
                editingCategory = null
            }
        )
    }

    deleteConfirmCategory?.let { category ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.categories_delete_title),
            message = stringResource(R.string.categories_delete_message, category.name),
            onConfirm = { viewModel.deleteCategory(category.id) },
            onDismiss = { deleteConfirmCategory = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditCategoryDialog(
    category: Category?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var description by remember { mutableStateOf(category?.description ?: "") }
    var selectedIcon by remember { mutableStateOf(category?.icon ?: "Category") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (category == null) stringResource(R.string.categories_add_title)
                       else stringResource(R.string.categories_edit_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.categories_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )

                // Description field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_optional)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )

                // Icon selection section
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.categories_icon_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categoryIconOptions) { (iconName, iconVector) ->
                            val isSelected = selectedIcon == iconName
                            FilledIconButton(
                                onClick = { selectedIcon = iconName },
                                modifier = Modifier
                                    .size(48.dp)
                                    .aspectRatio(1f),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = iconName,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description, selectedIcon) },
                enabled = name.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
