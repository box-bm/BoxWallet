@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.loans

import com.boxdev.boxwallet.ui.components.trendIcon
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.ui.components.segmentShapes

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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.ui.components.AmountKind
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.BackButton
import com.boxdev.boxwallet.ui.components.CategoryBadge
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.InfoChip
import com.boxdev.boxwallet.ui.components.EquivalentText
import com.boxdev.boxwallet.ui.components.LoanEquivalentText
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import com.boxdev.boxwallet.domain.model.LoanStatus
import com.boxdev.boxwallet.ui.components.LoanStatusChip
import com.boxdev.boxwallet.ui.components.SectionHeader
import com.boxdev.boxwallet.ui.components.accent
import com.boxdev.boxwallet.ui.components.directionColors
import com.boxdev.boxwallet.util.DateFormats
import com.boxdev.boxwallet.ui.theme.BoxTheme
import java.time.LocalDate
import com.boxdev.boxwallet.ui.screens.analytics.InstallmentState
import com.boxdev.boxwallet.ui.screens.analytics.installmentState
import java.time.temporal.ChronoUnit

/**
 * Loan detail, drawn transparent so it sits on the [com.boxdev.boxwallet.ui.components.AppPane] around it.
 * In the Loans list-detail it is the detail pane bound to [loanId] (back button only in single pane);
 * opened from elsewhere it is its own destination with a back button. [onEditLoan] opens the loan form.
 */
@Composable
fun LoanDetailScreen(
    onNavigateBack: (() -> Unit)?,
    onEditLoan: (Long) -> Unit,
    loanId: Long? = null,
    viewModel: LoanDetailViewModel = hiltViewModel(key = loanId?.let { "loan_detail_$it" }),
) {
    LaunchedEffect(loanId) { loanId?.let(viewModel::bind) }
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

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
                        uiState.loan?.name ?: stringResource(R.string.loan_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = uiState.loan?.description?.takeIf { it.isNotBlank() }?.let { d ->
                    { Text(d, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                navigationIcon = { if (onNavigateBack != null) BackButton(onNavigateBack) },
                actions = {
                    uiState.loan?.let { loan ->
                        IconButton(onClick = { onEditLoan(loan.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.loan_detail_edit))
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

            uiState.loan == null -> EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = stringResource(R.string.loan_not_found),
                modifier = Modifier.padding(padding),
            )

            else -> {
                val loan = uiState.loan!!
                val today = remember { LocalDate.now() }
                val states = remember(uiState.installments, today) {
                    uiState.installments.associate { it.id to installmentState(it.isCleared, it.date.toLocalDate(), today) }
                }
                // "Next payment" marks the first installment still ahead; overdue ones carry their own state.
                val nextUnpaidId = uiState.installments.firstOrNull { states[it.id] == InstallmentState.OPEN }?.id
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                ) {
                    item(key = "hero") {
                        LoanHero(loan = loan, categoryName = uiState.category?.name, category = uiState.category)
                    }
                    item(key = "installments_header") {
                        SectionHeader(
                            title = stringResource(R.string.loan_installments_section),
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                    itemsIndexed(uiState.installments, key = { _, t -> t.id }) { index, installment ->
                        InstallmentRow(
                            installment = installment,
                            index = index,
                            count = uiState.installments.size,
                            isNext = installment.id == nextUnpaidId,
                            isOverdue = states[installment.id] == InstallmentState.OVERDUE,
                            onToggleCleared = { viewModel.toggleInstallmentCleared(installment) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanHero(
    loan: Loan,
    categoryName: String?,
    category: com.boxdev.boxwallet.domain.model.Category?,
) {
    val today = LocalDate.now()
    val status = loan.status(today)
    val dateFormatter = DateFormats.mediumDate
    val progress by animateFloatAsState(
        targetValue = loan.progressFraction,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "loanHeroProgress",
    )
    val density = LocalDensity.current
    val stroke = remember(density) { with(density) { Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) } }

    Surface(
        color = BoxTheme.cardColor,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoanStatusChip(status = status)
                InfoChip(stringResource(loan.loanType.displayNameRes))
                if (categoryName != null) InfoChip(categoryName)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(132.dp),
                        color = status.accent(),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        stroke = stroke,
                        trackStroke = stroke,
                        amplitude = { p -> if (status == LoanStatus.PAID) 0f else WavyProgressIndicatorDefaults.indicatorAmplitude(p) * 0.6f },
                        wavelength = 36.dp,
                    )
                    val (badgeContainer, badgeContent) = directionColors(loan.loanType == LoanType.INCOME)
                    CategoryBadge(
                        category = category,
                        size = 64.dp,
                        fallbackIcon = trendIcon(loan.loanType == LoanType.INCOME),
                        fallbackContainer = badgeContainer,
                        fallbackContent = badgeContent,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(loan.loanType.remainingLabelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(
                        amount = loan.remainingAmount,
                        style = BoxTheme.moneyType.large,
                        autoSize = true,
                    )
                    if (!loan.isComplete) LoanEquivalentText(loan)
                    Text(
                        stringResource(R.string.loan_paid_progress, loan.paidInstallments, loan.totalInstallments),
                        style = MaterialTheme.typography.titleSmall,
                        color = status.accent(),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(
                    label = stringResource(loan.loanType.settledLabelRes),
                    modifier = Modifier.weight(1f),
                ) {
                    AmountText(
                        amount = loan.settledAmount,
                        kind = AmountKind.Income,
                        signed = false,
                        style = BoxTheme.moneyType.medium,
                        autoSize = true,
                    )
                }
                Stat(
                    label = stringResource(R.string.loan_total_amount),
                    modifier = Modifier.weight(1f),
                ) {
                    AmountText(amount = loan.totalAmount, style = BoxTheme.moneyType.medium, autoSize = true)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(label = stringResource(R.string.loan_detail_start_date), modifier = Modifier.weight(1f)) {
                    Text(loan.startDate.toLocalDate().format(dateFormatter), style = MaterialTheme.typography.titleMedium)
                }
                Stat(label = stringResource(R.string.loan_next_payment), modifier = Modifier.weight(1f)) {
                    val due = loan.nextDueDate
                    if (due == null || loan.isComplete) {
                        Text("—", style = MaterialTheme.typography.titleMedium)
                    } else {
                        val days = ChronoUnit.DAYS.between(today, due).toInt()
                        Text(due.format(dateFormatter), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = when {
                                days < 0 -> pluralStringResource(R.plurals.loan_days_overdue, -days, -days)
                                days == 0 -> stringResource(R.string.date_today)
                                else -> pluralStringResource(R.plurals.loan_days_until, days, days)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (days < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Box(modifier = modifier) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            value()
        }
    }
}

@Composable
private fun InstallmentRow(
    installment: Transaction,
    index: Int,
    count: Int,
    isNext: Boolean,
    isOverdue: Boolean,
    onToggleCleared: () -> Unit,
) {
    val dateFormatter = DateFormats.mediumDate
    val money = BoxTheme.money
    SegmentedListItem(
        checked = installment.isCleared,
        onCheckedChange = { onToggleCleared() },
        shapes = segmentShapes(index = index, count = count),
        colors = ListItemDefaults.colors(
            containerColor = when {
                isOverdue -> MaterialTheme.colorScheme.errorContainer
                isNext -> MaterialTheme.colorScheme.tertiaryContainer
                else -> BoxTheme.cardColor
            },
        ),
        leadingContent = { Checkbox(checked = installment.isCleared, onCheckedChange = null) },
        supportingContent = {
            val date = installment.date.toLocalDate().format(dateFormatter)
            Text(
                when {
                    isOverdue -> stringResource(R.string.loan_installment_overdue_since, date)
                    isNext -> stringResource(R.string.loan_next_payment) + " · " + date
                    else -> date
                },
                color = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            val equivalent = rememberCurrencyFormatter().withEquivalent(installment.original, installment.amount).second
            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    amount = installment.original,
                    style = BoxTheme.moneyType.medium,
                    color = if (installment.isCleared) money.income else MaterialTheme.colorScheme.onSurface,
                )
                if (equivalent != null) EquivalentText(equivalent)
            }
        },
        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(stringResource(R.string.loan_installment_number, installment.installmentNumber ?: 0))
    }
}
