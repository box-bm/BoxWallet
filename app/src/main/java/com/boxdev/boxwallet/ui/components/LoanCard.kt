@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.util.DateFormats
import com.boxdev.boxwallet.domain.model.InstallmentState
import com.boxdev.boxwallet.domain.model.LoanStatus
import java.time.LocalDate
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.ui.theme.BoxTheme
import java.time.format.DateTimeFormatter

/**
 * A loan at a glance: who/what, what is left, a wavy progress line that settles flat when paid off,
 * and when the next installment is due.
 */
@Composable
fun LoanCard(
    loan: Loan,
    modifier: Modifier = Modifier,
    category: Category? = null,
    selected: Boolean = false,
    containerColor: Color = BoxTheme.cardColor,
    onClick: () -> Unit = {},
) {
    val dateFormatter = DateFormats.mediumDate
    val status = loan.status(LocalDate.now())
    val interaction = remember { MutableInteractionSource() }
    val progress by animateFloatAsState(
        targetValue = if (loan.total.minor <= 0) 0f else (loan.paid.minor.toFloat() / loan.total.minor).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "loanProgress",
    )
    val isIncome = loan.loanType == LoanType.INCOME
    val (badgeContainer, badgeContent) = directionColors(isIncome)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else containerColor,
        shape = MaterialTheme.shapes.extraLarge,
        interactionSource = interaction,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(
                    category = category,
                    fallbackIcon = trendIcon(isIncome),
                    fallbackContainer = badgeContainer,
                    fallbackContent = badgeContent,
                    interactionSource = interaction,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = loan.description.ifBlank { category?.name.orEmpty() }
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                LoanStatusChip(status = status)
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(loan.loanType.remainingLabelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(
                        amount = loan.remaining,
                        style = BoxTheme.moneyType.large,
                        autoSize = true,
                    )
                    if (!loan.isSettled) LoanEquivalentText(loan)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.loan_total_amount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(
                        amount = loan.total,
                        style = BoxTheme.moneyType.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = status.accent(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                amplitude = { p -> if (status == LoanStatus.PAID) 0f else WavyProgressIndicatorDefaults.indicatorAmplitude(p) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val due = loan.nextDue()?.due
                Text(
                    text = if (!loan.isSettled && due != null) {
                        stringResource(R.string.loan_next_due, due.format(dateFormatter))
                    } else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status == LoanStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (loan.plan != null) {
                    Text(
                        text = stringResource(R.string.loan_paid_progress, loan.schedule().count { it.state == InstallmentState.PAID }, loan.plan?.installments ?: 0),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * A loan in one line for summaries such as Home: badge, name, what is left in its direction, and
 * the next due date (or how long it has been overdue). The full card lives on the Loans tab.
 */
@Composable
fun LoanRow(
    loan: Loan,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    category: Category? = null,
    /** Highlights the loan open in the detail pane beside the list, like [LoanCard]. */
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    // Short "Oct 1" dates, as in Reports, so the line fits beside the amount.
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()) }
    val status = loan.status(LocalDate.now())
    val isIncome = loan.loanType == LoanType.INCOME
    val (badgeContainer, badgeContent) = directionColors(isIncome)
    val due = loan.nextDue()?.due
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentShapes(index, count),
        colors = if (selected) {
            val container = MaterialTheme.colorScheme.secondaryContainer
            val content = MaterialTheme.colorScheme.onSecondaryContainer
            ListItemDefaults.segmentedColors(
                containerColor = container,
                contentColor = content,
                supportingContentColor = content,
                trailingContentColor = content,
                selectedContainerColor = container,
            )
        } else {
            ListItemDefaults.colors(containerColor = BoxTheme.cardColor)
        },
        modifier = modifier.padding(bottom = ListItemDefaults.SegmentedGap),
        leadingContent = {
            CategoryBadge(
                category = category,
                fallbackIcon = trendIcon(isIncome),
                fallbackContainer = badgeContainer,
                fallbackContent = badgeContent,
            )
        },
        supportingContent = {
            val line = when {
                loan.isSettled -> stringResource(R.string.loan_status_paid)
                status == LoanStatus.OVERDUE && due != null ->
                    stringResource(R.string.loan_row_overdue, stringResource(loan.loanType.remainingLabelRes), due.format(dateFormatter))
                due != null && !loan.isSettled ->
                    stringResource(R.string.loan_row_next, stringResource(loan.loanType.remainingLabelRes), due.format(dateFormatter))
                else -> stringResource(loan.loanType.remainingLabelRes)
            }
            Text(
                line,
                color = if (status == LoanStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            // A paid-off loan shows what it came to; an open one, what is still left in its direction.
            if (loan.isSettled) {
                AmountText(
                    amount = loan.total,
                    style = BoxTheme.moneyType.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    AmountText(
                        amount = loan.remaining,
                        kind = amountKind(isIncome),
                        style = BoxTheme.moneyType.small,
                    )
                    LoanEquivalentText(loan, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        },
    ) {
        Text(loan.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
