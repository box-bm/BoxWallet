@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.ui.components.segmentShapes

import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.boxdev.boxwallet.ui.components.CategoryBadge
import com.boxdev.boxwallet.ui.theme.BoxTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.CentralAmericanCurrency

@Composable
fun GeneralSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: GeneralSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    val currency = uiState.selectedCurrency
    val defaultCategory = uiState.categories.find { it.id == uiState.defaultCategoryId }

    SettingsScaffold(title = stringResource(R.string.general_settings_title), onNavigateBack = onNavigateBack) {
        SettingsGroup(stringResource(R.string.general_settings_regional)) {
            SettingsRow(
                index = 0, count = 1,
                icon = Icons.Outlined.Payments,
                title = stringResource(R.string.general_settings_currency),
                subtitle = "${currency.currencyName} · ${currency.currencyCode}",
                onClick = { showCurrencyDialog = true },
                trailing = {
                    Text(
                        currency.symbol,
                        style = BoxTheme.moneyType.large,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            Text(
                stringResource(R.string.general_settings_currency_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }

        SettingsGroup(stringResource(R.string.general_settings_transactions)) {
            SegmentedListItem(
                onClick = { showCategoryDialog = true },
                shapes = segmentShapes(index = 0, count = 1),
                colors = settingsRowColors(),
                leadingContent = { CategoryBadge(category = defaultCategory, fallbackIcon = Icons.Outlined.Category, size = 40.dp) },
                supportingContent = { Text(defaultCategory?.name ?: stringResource(R.string.action_none)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            ) { Text(stringResource(R.string.general_settings_default_category)) }
        }
    }

    if (showCurrencyDialog) {
        CurrencySelectionDialog(
            currentCurrency = currency,
            onCurrencySelected = { viewModel.updateCurrency(it); showCurrencyDialog = false },
            onDismiss = { showCurrencyDialog = false }
        )
    }

    if (showCategoryDialog) {
        DefaultCategorySelectionDialog(
            categories = uiState.categories,
            currentCategoryId = uiState.defaultCategoryId,
            onCategorySelected = { viewModel.setDefaultCategory(it); showCategoryDialog = false },
            onDismiss = { showCategoryDialog = false }
        )
    }
}

@Composable
private fun CurrencySelectionDialog(
    currentCurrency: CentralAmericanCurrency,
    onCurrencySelected: (CentralAmericanCurrency) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.general_settings_select_currency),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                CentralAmericanCurrency.entries.forEach { currency ->
                    CurrencyItem(
                        currency = currency,
                        isSelected = currency == currentCurrency,
                        onClick = { onCurrencySelected(currency) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun CurrencyItem(
    currency: CentralAmericanCurrency,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "${currency.country} - ${currency.currencyName}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "${currency.currencyCode} (${currency.symbol})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.action_selected),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DefaultCategorySelectionDialog(
    categories: List<com.boxdev.boxwallet.domain.model.Category>,
    currentCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.general_settings_select_default_category),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // None option
                CategorySelectionItem(
                    name = stringResource(R.string.action_none),
                    description = stringResource(R.string.general_settings_no_default_category),
                    isSelected = currentCategoryId == null,
                    onClick = { onCategorySelected(null) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Categories
                categories.forEach { category ->
                    CategorySelectionItem(
                        name = category.name,
                        description = category.description.ifBlank { null },
                        isSelected = category.id == currentCategoryId,
                        onClick = { onCategorySelected(category.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun CategorySelectionItem(
    name: String,
    description: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.action_selected),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
