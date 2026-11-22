@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.ui.components.CategoryBadge
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.Panel
import com.boxdev.boxwallet.ui.components.SheetHeader
import com.boxdev.boxwallet.ui.components.segmentShapes
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import kotlinx.coroutines.launch

// What the category sheet is open for: closed, a new category, or else the id being edited.
private const val SHEET_CLOSED = -2L
private const val SHEET_NEW = -1L

@Composable
fun CategoriesScreen(
    onNavigateBack: (() -> Unit)?,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // An id, not the object, so the open sheet survives rotation and folding.
    var sheetFor by rememberSaveable { mutableStateOf(SHEET_CLOSED) }
    var pickingDefault by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()
    val errorMessage = uiState.error?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // Clear first so the same failure can show again; the snackbar runs in [scope] so
            // the key flipping back to null doesn't cancel it.
            viewModel.clearError()
            scope.launch { snackbarHostState.showSnackbar(errorMessage) }
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.categories_title),
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(onClick = { sheetFor = SHEET_NEW }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.categories_new))
            }
        },
    ) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            uiState.categories.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Category,
                title = stringResource(R.string.categories_no_categories),
                message = stringResource(R.string.categories_tap_to_create),
            )
            else -> {
                DefaultCategoryCard(category = uiState.defaultCategory, onChange = { pickingDefault = true })
                SettingsGroup(title = null) {
                    val count = uiState.categories.size
                    uiState.categories.forEachIndexed { index, category ->
                        val interaction = remember { MutableInteractionSource() }
                        SegmentedListItem(
                            onClick = { sheetFor = category.id },
                            shapes = segmentShapes(index = index, count = count),
                            colors = settingsRowColors(),
                            interactionSource = interaction,
                            leadingContent = { CategoryBadge(category = category, interactionSource = interaction) },
                            supportingContent = category.description.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 2) } },
                        ) { Text(category.name) }
                    }
                }
            }
        }
        // Clears the FAB.
        Spacer(Modifier.height(72.dp))
    }

    if (sheetFor != SHEET_CLOSED && !uiState.isLoading) {
        val editing = uiState.categories.firstOrNull { it.id == sheetFor }
        if (sheetFor == SHEET_NEW || editing != null) {
            CategoryFormSheet(
                category = editing,
                onDismiss = { sheetFor = SHEET_CLOSED },
                onSave = { form -> viewModel.save(form.id, form.name, form.description, form.icon, form.color) },
                onDelete = editing?.let { { viewModel.delete(it.id) } },
            )
        } else {
            // The category went away under the sheet (e.g. cleared from Data management).
            LaunchedEffect(Unit) { sheetFor = SHEET_CLOSED }
        }
    }

    if (pickingDefault) {
        DefaultCategoryPickerSheet(
            categories = uiState.categories,
            current = uiState.defaultCategory,
            onPick = viewModel::setDefaultCategory,
            onDismiss = { pickingDefault = false },
        )
    }
}

/** The tonal card at the top: which category new movements start with, and a way to change it. */
@Composable
private fun DefaultCategoryCard(category: Category?, onChange: () -> Unit) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryBadge(category = category, fallbackIcon = Icons.Outlined.Category, size = 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.categories_default_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        category?.name ?: stringResource(R.string.categories_default_none),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (category != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalButton(onClick = onChange, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.categories_default_change))
                }
            }
            Text(
                stringResource(R.string.categories_default_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "None" plus every category; the current default is checked, and tapping one picks it and closes. */
@Composable
private fun DefaultCategoryPickerSheet(
    categories: List<Category>,
    current: Category?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val pick = { id: Long? ->
        onPick(id)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    val count = categories.size + 1

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            SheetHeader(title = stringResource(R.string.categories_default_title))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                DefaultChoiceRow(category = null, selected = current == null, index = 0, count = count) { pick(null) }
                categories.forEachIndexed { index, category ->
                    DefaultChoiceRow(category = category, selected = category.id == current?.id, index = index + 1, count = count) {
                        pick(category.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultChoiceRow(category: Category?, selected: Boolean, index: Int, count: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = segmentShapes(index = index, count = count),
        colors = settingsRowColors(),
        interactionSource = interaction,
        leadingContent = {
            CategoryBadge(category = category, fallbackIcon = Icons.Outlined.Category, interactionSource = interaction, selected = selected)
        },
        trailingContent = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null,
    ) { Text(category?.name ?: stringResource(R.string.categories_default_none)) }
}

@Composable
private fun DefaultCategoryCardPreviewContent() {
    BoxWalletTheme(dynamicColor = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DefaultCategoryCard(Category(id = 2, name = "Groceries", icon = "ShoppingCart", color = AccountColor.TEAL), onChange = {})
            DefaultCategoryCard(null, onChange = {})
        }
    }
}

@Preview(name = "Default card", widthDp = 411)
@Composable
private fun DefaultCategoryCardPreview() = DefaultCategoryCardPreviewContent()

@Preview(name = "Default card, dark", widthDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DefaultCategoryCardDarkPreview() = DefaultCategoryCardPreviewContent()
