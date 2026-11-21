@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.boxdev.boxwallet.ui.screens.accounts

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.domain.money.pickerSections
import com.boxdev.boxwallet.ui.components.CurrencyPickerSheet
import com.boxdev.boxwallet.ui.components.FullScreenForm
import com.boxdev.boxwallet.ui.components.OptionGroup
import com.boxdev.boxwallet.ui.components.SwitchRow
import com.boxdev.boxwallet.ui.components.ValueRow
import com.boxdev.boxwallet.ui.components.groupShape
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import com.boxdev.boxwallet.ui.theme.container
import com.boxdev.boxwallet.ui.theme.onContainer
import com.boxdev.boxwallet.util.accountIconOptions
import java.util.Currency
import java.util.Locale

/**
 * Full-screen create/edit form for an account; the view model reads the `id` navigation argument.
 * [onDone] runs once, after ✕ or a save, and returns whether it navigated (see LoanFormScreen).
 */
@Composable
fun AccountFormScreen(
    onDone: () -> Boolean,
    viewModel: AccountFormViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsState()
    val status by viewModel.status.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val recentCurrencies by viewModel.recentCurrencies.collectAsState()
    val currentOnDone by rememberUpdatedState(onDone)
    var finished by rememberSaveable { mutableStateOf(false) }
    val finish: () -> Unit = { if (!finished && currentOnDone()) finished = true }
    val scrollState = rememberScrollState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    val locale = remember { Locale.getDefault() }

    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        .isAtLeast(Lifecycle.State.RESUMED)
    // A save that lands while the form is still animating in exits once it settles.
    LaunchedEffect(status.isSaved, resumed) { if (status.isSaved && resumed) finish() }
    LaunchedEffect(status.error) { if (status.error != null) scrollState.animateScrollTo(0) }

    FullScreenForm(
        title = stringResource(if (form.isEditing) R.string.account_edit_title else R.string.accounts_new),
        onClose = finish,
        onSave = viewModel::save,
        onDelete = null,
        hasUnsavedChanges = hasUnsavedChanges,
        scrollState = scrollState,
    ) {
        if (status.isLoading) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@FullScreenForm
        }
        AccountFormFields(
            form = form,
            showErrors = status.showErrors,
            error = status.error?.let { stringResource(it) },
            onName = viewModel::updateName,
            onCurrencyClick = { showCurrencyPicker = true },
            onBalance = viewModel::updateBalance,
            onDescription = viewModel::updateDescription,
            onIcon = viewModel::updateIcon,
            onColor = viewModel::updateColor,
            onIncludeInTotal = viewModel::updateIncludeInTotal,
        )
    }

    if (showCurrencyPicker) {
        var query by remember { mutableStateOf("") }
        val all = remember(locale) { CurrencyCatalog.all(locale) }
        val sections = remember(query, recentCurrencies, all) { pickerSections(all, recentCurrencies, query, locale) }
        CurrencyPickerSheet(
            selected = form.currency,
            sections = sections,
            onQuery = { query = it },
            onPick = { viewModel.updateCurrency(it); showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false },
        )
    }
}

/** The form's fields, stateless so previews can draw them. */
@Composable
private fun AccountFormFields(
    form: AccountFormState,
    showErrors: Boolean,
    error: String?,
    onName: (String) -> Unit,
    onCurrencyClick: () -> Unit,
    onBalance: (String) -> Unit,
    onDescription: (String) -> Unit,
    onIcon: (String) -> Unit,
    onColor: (AccountColor) -> Unit,
    onIncludeInTotal: (Boolean) -> Unit,
) {
    val locale = remember { Locale.getDefault() }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        val nameError = form.nameError?.takeIf { showErrors }
        OutlinedTextField(
            value = form.name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.account_name_label)) },
            placeholder = { Text(stringResource(R.string.account_name_placeholder)) },
            isError = nameError != null,
            supportingText = {
                Text(
                    if (nameError != null) stringResource(nameError, ACCOUNT_NAME_MAX)
                    else "${form.name.trim().length}/$ACCOUNT_NAME_MAX"
                )
            },
            shape = MaterialTheme.shapes.extraLarge,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionGroup {
                ValueRow(
                    shape = groupShape(0, 1),
                    icon = if (form.currencyLocked) Icons.Outlined.Lock else Icons.Outlined.CurrencyExchange,
                    label = stringResource(R.string.account_currency_label),
                    value = "${form.currency.currencyCode} · ${CurrencyCatalog.displayName(form.currency, locale)}",
                    onClick = { if (!form.currencyLocked) onCurrencyClick() },
                )
            }
            if (form.currencyLocked) HelpText(stringResource(R.string.account_currency_locked))
        }

        if (!form.isEditing) {
            val balanceError = form.balanceError?.takeIf { showErrors }
            OutlinedTextField(
                value = form.currentBalance,
                onValueChange = onBalance,
                label = { Text(stringResource(R.string.account_balance_label)) },
                prefix = { Text(CurrencyCatalog.symbol(form.currency, locale)) },
                placeholder = { Text("0") },
                isError = balanceError != null,
                supportingText = {
                    Text(stringResource(balanceError ?: R.string.account_balance_help))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = form.description,
            onValueChange = onDescription,
            label = { Text(stringResource(R.string.description_optional)) },
            shape = MaterialTheme.shapes.extraLarge,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        LabeledBlock(stringResource(R.string.account_icon_label)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accountIconOptions.forEach { (key, icon) ->
                    val selected = key == form.icon
                    ChoiceToken(
                        selected = selected,
                        container = if (selected) form.color.container() else MaterialTheme.colorScheme.surfaceContainerHigh,
                        description = key,
                        onClick = { onIcon(key) },
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (selected) form.color.onContainer() else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        LabeledBlock(stringResource(R.string.account_color_label)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountColor.entries.forEachIndexed { index, color ->
                    val selected = color == form.color
                    ChoiceToken(
                        selected = selected,
                        container = color.container(),
                        description = stringResource(R.string.account_color_option, index + 1),
                        onClick = { onColor(color) },
                    ) {
                        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = color.onContainer())
                    }
                }
            }
        }

        OptionGroup {
            SwitchRow(
                shape = groupShape(0, 1),
                icon = Icons.Outlined.Functions,
                label = stringResource(R.string.account_include_in_total),
                supporting = stringResource(R.string.account_include_in_total_help),
                checked = form.includeInTotal,
                onCheckedChange = onIncludeInTotal,
            )
        }
    }
}

@Composable
private fun HelpText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun LabeledBlock(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

/** A 48dp round choice in a grid; the chosen one gets the 2dp primary ring the category picker uses. */
@Composable
private fun ChoiceToken(
    selected: Boolean,
    container: Color,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { this.contentDescription = description },
    ) {
        Surface(shape = CircleShape, color = container, modifier = Modifier.padding(3.dp).size(46.dp)) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable
private fun AccountFormPreviewContent() {
    BoxWalletTheme(dynamicColor = false) {
        FullScreenForm(title = "New account", onClose = {}, onSave = {}, onDelete = null, hasUnsavedChanges = false) {
            AccountFormFields(
                form = AccountFormState(name = "Banco", currency = Currency.getInstance("GTQ"), currentBalance = "1,250.00", color = AccountColor.TEAL),
                showErrors = false,
                error = null,
                onName = {}, onCurrencyClick = {}, onBalance = {}, onDescription = {},
                onIcon = {}, onColor = {}, onIncludeInTotal = {},
            )
        }
    }
}

@Preview(name = "Light", widthDp = 411, heightDp = 1100)
@Composable
private fun AccountFormPreview() = AccountFormPreviewContent()

@Preview(name = "Dark", widthDp = 411, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountFormDarkPreview() = AccountFormPreviewContent()
