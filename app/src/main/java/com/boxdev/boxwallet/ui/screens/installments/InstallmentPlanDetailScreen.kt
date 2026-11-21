@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.installments

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.InstallmentPlan
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.BackButton
import com.boxdev.boxwallet.ui.components.CategoryBadge
import com.boxdev.boxwallet.ui.components.CategoryPicker
import com.boxdev.boxwallet.ui.components.ConfirmDeleteDialog
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.EquivalentText
import com.boxdev.boxwallet.ui.components.SectionHeader
import com.boxdev.boxwallet.ui.components.SheetHeader
import com.boxdev.boxwallet.ui.components.installmentProgress
import com.boxdev.boxwallet.ui.components.segmentShapes
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import com.boxdev.boxwallet.util.DateFormats
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency

/**
 * One installment purchase on its own route: the total, its progress, every installment (charged or still
 * pending), editing its description and category, and deleting it. [onDeleted] runs once it is gone.
 */
@Composable
fun InstallmentPlanDetailScreen(
    onNavigateBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: InstallmentPlanDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val confirmDelete by viewModel.confirmDelete.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    var editing by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    LaunchedEffect(deleted) { if (deleted) currentOnDeleted() }

    val plan = uiState.plan
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            LargeFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = {
                    Text(
                        plan?.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.installment_plan_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onNavigateBack) },
                actions = {
                    if (plan != null) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.installment_plan_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            plan == null -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.installment_plan_not_found),
                modifier = Modifier.padding(padding),
            )
            else -> InstallmentPlanDetailContent(
                plan = plan,
                category = uiState.category,
                installments = uiState.installments,
                onDelete = viewModel::requestDelete,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (editing && plan != null) {
        EditPlanSheet(
            plan = plan,
            categories = uiState.categories,
            onSave = { description, categoryId ->
                viewModel.save(description, categoryId)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
    confirmDelete?.let { confirmation ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.installment_plan_delete),
            message = stringResource(
                R.string.installment_delete_confirm,
                confirmation.installments,
                rememberCurrencyFormatter().format(confirmation.debtDrop),
            ),
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun InstallmentPlanDetailContent(
    plan: InstallmentPlan,
    category: Category?,
    installments: List<InstallmentRow>,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
    ) {
        item(key = "hero") { PlanHero(plan, category) }
        item(key = "installments_header") {
            SectionHeader(
                title = stringResource(R.string.loan_installments_section),
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        itemsIndexed(installments, key = { _, row -> row.installment.id }) { index, row ->
            InstallmentRowItem(row, total = plan.installments, index = index, count = installments.size)
        }
        item(key = "delete") {
            // Delete sits at the foot, as in the forms, never in the top bar.
            Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.installment_plan_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** The total bought, its account-currency equivalent when it differs, and how far along the plan is. */
@Composable
private fun PlanHero(plan: InstallmentPlan, category: Category?) {
    val formatter = rememberCurrencyFormatter()
    val progress by animateFloatAsState(
        targetValue = plan.charged.toFloat() / plan.installments,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "planProgress",
    )
    Surface(
        color = BoxTheme.cardColor,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryBadge(category = category, fallbackIcon = Icons.Outlined.CreditCard, size = 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        category?.name ?: stringResource(R.string.installment_plan_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(amount = plan.original, style = BoxTheme.moneyType.large, autoSize = true)
                    if (plan.original.currency != plan.accountTotal.currency) {
                        EquivalentText("≈ " + formatter.format(plan.accountTotal))
                    }
                }
            }
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                amplitude = { p -> if (plan.charged == plan.installments) 0f else WavyProgressIndicatorDefaults.indicatorAmplitude(p) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(installmentProgress(plan), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.installment_plan_bought_on, plan.purchaseDate.toLocalDate().format(DateFormats.mediumDate)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Installment 2/3", its closing date with Charged or Pending (icon and label), and its account amount. */
@Composable
private fun InstallmentRowItem(row: InstallmentRow, total: Int, index: Int, count: Int) {
    val installment = row.installment
    // Not tappable: an installment only changes through its plan.
    Surface(
        color = BoxTheme.cardColor,
        shape = segmentShapes(index = index, count = count).shape,
        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = if (row.charged) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (row.charged) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (row.charged) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            headlineContent = { Text(stringResource(R.string.installment_plan_number, installment.installmentNumber ?: 0, total)) },
            supportingContent = {
                Text(
                    installment.date.toLocalDate().format(DateFormats.mediumDate) + " · " +
                        stringResource(if (row.charged) R.string.installment_charged else R.string.installment_pending),
                )
            },
            trailingContent = {
                AmountText(
                    amount = installment.accountAmount,
                    signed = false,
                    color = if (row.charged) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

/** Only the description and category of a plan can change; every installment follows. */
@Composable
private fun EditPlanSheet(
    plan: InstallmentPlan,
    categories: List<Category>,
    onSave: (String, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var description by rememberSaveable { mutableStateOf(plan.description) }
    var categoryId by rememberSaveable { mutableStateOf(plan.categoryId) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.imePadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(title = stringResource(R.string.installment_plan_edit))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.transaction_description_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryPicker(categories = categories, selectedCategoryId = categoryId, onCategorySelected = { categoryId = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSave(description, categoryId) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun InstallmentPlanDetailPreviewContent() {
    BoxWalletTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            val gtq = Currency.getInstance("GTQ")
            val plan = InstallmentPlan(
                id = 1, accountId = 1, description = "Televisor", categoryId = null,
                original = Money(3_000_00, gtq), accountTotal = Money(3_000_00, gtq), installments = 3,
                purchaseDate = LocalDateTime.of(2026, 10, 2, 10, 0), charged = 2, pendingMinor = 1_000_00,
            )
            val rows = (1..3).map { k ->
                val date = LocalDate.of(2026, 9 + k, 15).atTime(10, 0)
                InstallmentRow(
                    Transaction.singleCurrency(1, TransactionType.EXPENSE, date, Money(1_000_00, gtq))
                        .copy(id = k.toLong(), installmentPlanId = 1, installmentNumber = k, description = "Televisor"),
                    charged = k <= 2,
                )
            }
            InstallmentPlanDetailContent(plan = plan, category = null, installments = rows, onDelete = {})
        }
    }
}

@Preview(name = "Plan detail", widthDp = 411, heightDp = 760)
@Composable
private fun InstallmentPlanDetailPreview() = InstallmentPlanDetailPreviewContent()

@Preview(name = "Plan detail, dark", widthDp = 411, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InstallmentPlanDetailDarkPreview() = InstallmentPlanDetailPreviewContent()
