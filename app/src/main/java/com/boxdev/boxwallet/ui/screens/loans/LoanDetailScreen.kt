@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.loans

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.boxdev.boxwallet.domain.model.InstallmentState
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanStatus
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.ScheduledInstallment
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.ui.components.AmountKind
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.BackButton
import com.boxdev.boxwallet.ui.components.CategoryBadge
import com.boxdev.boxwallet.ui.components.ConfirmDeleteDialog
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.InfoChip
import com.boxdev.boxwallet.ui.components.LoanEquivalentText
import com.boxdev.boxwallet.ui.components.LoanStatusChip
import com.boxdev.boxwallet.ui.components.SectionHeader
import com.boxdev.boxwallet.ui.components.accent
import com.boxdev.boxwallet.ui.components.directionColors
import com.boxdev.boxwallet.ui.components.loanMovementLabel
import com.boxdev.boxwallet.ui.components.segmentShapes
import com.boxdev.boxwallet.ui.components.trendIcon
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.DateFormats
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Loan detail, drawn transparent so it sits on the [com.boxdev.boxwallet.ui.components.AppPane] around it.
 * Opened as its own destination with a back button; [onEditLoan] opens the loan form. Shows what is left,
 * the installments (when there is a plan), the payments (tap one to edit it) and "Record payment".
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
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onNavigateBack?.invoke() }
    // A refusal outside the payment sheet (marking an installment, deleting) comes as a snackbar.
    val errorText = uiState.error?.let { stringResource(it) }
    LaunchedEffect(errorText, uiState.payment == null) {
        if (errorText != null && uiState.payment == null) {
            snackbar.showSnackbar(errorText)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            val loan = uiState.loan
            if (loan != null && !loan.isSettled) {
                MediumExtendedFloatingActionButton(
                    onClick = viewModel::openNewPayment,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.loan_record_payment)) },
                )
            }
        },
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
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.loan_detail_edit)) },
                                    onClick = { menuOpen = false; onEditLoan(loan.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.loan_delete_button), color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
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
                val schedule = remember(loan, today) { loan.schedule(today) }
                val next = remember(loan, today) { loan.nextDue(today) }
                // Newest payment first, like the movement lists.
                val payments = remember(uiState.payments) { uiState.payments.asReversed() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                ) {
                    item(key = "hero") {
                        LoanHero(loan = loan, accountName = uiState.accountName)
                    }
                    if (schedule.isNotEmpty()) {
                        item(key = "installments_header") {
                            SectionHeader(
                                title = stringResource(R.string.loan_installments_section),
                                modifier = Modifier.padding(top = 20.dp),
                            )
                        }
                        itemsIndexed(schedule, key = { _, i -> "installment_${i.number}" }) { index, installment ->
                            InstallmentRow(
                                installment = installment,
                                index = index,
                                count = schedule.size,
                                isNext = installment.number == next?.number,
                                onMarkPaid = { viewModel.markPaid(installment.number) },
                            )
                        }
                    }
                    item(key = "payments_header") {
                        SectionHeader(
                            title = stringResource(R.string.loan_payments_section),
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                    if (payments.isEmpty()) {
                        item(key = "payments_empty") {
                            Text(
                                stringResource(R.string.loan_payments_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        itemsIndexed(payments, key = { _, t -> "payment_${t.id}" }) { index, payment ->
                            PaymentRow(
                                loan = loan,
                                payment = payment,
                                index = index,
                                count = payments.size,
                                onClick = { viewModel.openPayment(payment.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.payment?.let { sheet ->
        LoanPaymentSheet(
            state = sheet,
            error = errorText,
            onAmountChange = viewModel::updatePaymentAmount,
            onDateChange = viewModel::updatePaymentDate,
            onSave = viewModel::savePayment,
            onDelete = viewModel::deleteOpenPayment,
            onDismiss = viewModel::dismissPayment,
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.loan_delete_title),
            message = stringResource(R.string.loan_delete_message),
            onConfirm = viewModel::deleteLoan,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun LoanHero(loan: Loan, accountName: String?) {
    val today = LocalDate.now()
    val status = loan.status(today)
    val dateFormatter = DateFormats.mediumDate
    val formatter = rememberCurrencyFormatter()
    val progress by animateFloatAsState(
        targetValue = loan.progressFraction,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "loanHeroProgress",
    )
    val density = LocalDensity.current
    val stroke = remember(density) { with(density) { Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round) } }

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
                if (accountName != null) InfoChip(accountName)
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
                        category = null,
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
                        amount = loan.remaining,
                        style = BoxTheme.moneyType.large,
                        autoSize = true,
                    )
                    if (!loan.isSettled) LoanEquivalentText(loan)
                    Text(
                        stringResource(R.string.loan_of_total, formatter.format(loan.total)),
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
                        amount = loan.paid,
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
                    AmountText(amount = loan.total, style = BoxTheme.moneyType.medium, autoSize = true)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(label = stringResource(R.string.loan_detail_start_date), modifier = Modifier.weight(1f)) {
                    Text(loan.date.toLocalDate().format(dateFormatter), style = MaterialTheme.typography.titleMedium)
                }
                // With a plan the next installment; without one the expected date.
                val due = if (loan.isSettled) null else loan.nextDue(today)?.due ?: loan.expectedDate
                Stat(
                    label = stringResource(if (loan.plan != null) R.string.loan_next_payment else R.string.loan_expected_date),
                    modifier = Modifier.weight(1f),
                ) {
                    if (due == null) {
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

/** An installment with how much of it the payments cover; the next unpaid one can be marked paid. */
@Composable
private fun InstallmentRow(
    installment: ScheduledInstallment,
    index: Int,
    count: Int,
    isNext: Boolean,
    onMarkPaid: () -> Unit,
) {
    val formatter = rememberCurrencyFormatter()
    val date = installment.due.format(DateFormats.mediumDate)
    val overdue = installment.state == InstallmentState.OVERDUE
    // Not clickable: only "Mark as paid" acts, so the row has no ripple.
    ListItem(
        headlineContent = { Text(stringResource(R.string.loan_installment_number, installment.number)) },
        colors = ListItemDefaults.colors(
            containerColor = when {
                overdue -> MaterialTheme.colorScheme.errorContainer
                isNext -> MaterialTheme.colorScheme.tertiaryContainer
                else -> BoxTheme.cardColor
            },
        ),
        leadingContent = {
            val (icon, tint) = when (installment.state) {
                InstallmentState.PAID -> Icons.Filled.CheckCircle to BoxTheme.money.income
                InstallmentState.PARTIAL -> Icons.Outlined.Timelapse to MaterialTheme.colorScheme.primary
                InstallmentState.OVERDUE -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.onErrorContainer
                InstallmentState.PENDING -> Icons.Outlined.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = null, tint = tint)
        },
        supportingContent = {
            Column {
                Text(
                    when {
                        installment.state == InstallmentState.PAID -> stringResource(R.string.loan_installment_paid_label)
                        overdue -> stringResource(R.string.loan_installment_overdue_since, date)
                        isNext -> stringResource(R.string.loan_next_payment) + " · " + date
                        else -> date
                    },
                    color = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (installment.state == InstallmentState.PARTIAL || (overdue && installment.covered.minor > 0)) {
                    Text(
                        stringResource(
                            R.string.loan_installment_covered,
                            formatter.format(installment.covered),
                            formatter.format(installment.amount),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AmountText(
                    amount = installment.amount,
                    style = BoxTheme.moneyType.medium,
                    color = if (installment.state == InstallmentState.PAID) BoxTheme.money.income else MaterialTheme.colorScheme.onSurface,
                )
                if (isNext) {
                    FilledTonalButton(onClick = onMarkPaid) { Text(stringResource(R.string.loan_mark_as_paid)) }
                }
            }
        },
        modifier = Modifier
            .padding(bottom = ListItemDefaults.SegmentedGap)
            .clip(segmentShapes(index = index, count = count).shape),
    )
}

/** A payment of the loan; tapping it opens the sheet to edit or delete it. */
@Composable
private fun PaymentRow(
    loan: Loan,
    payment: Transaction,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val label = loanMovementLabel(payment.type, loan.loanType)
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentShapes(index = index, count = count),
        colors = ListItemDefaults.colors(containerColor = BoxTheme.cardColor),
        supportingContent = { Text(payment.date.toLocalDate().format(DateFormats.mediumDate)) },
        trailingContent = {
            AmountText(
                amount = payment.accountAmount,
                kind = if (loan.loanType == LoanType.INCOME) AmountKind.Income else AmountKind.Expense,
                style = BoxTheme.moneyType.medium,
            )
        },
        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(stringResource(label.title, loan.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
