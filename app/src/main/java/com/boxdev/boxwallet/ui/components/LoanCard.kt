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
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.ui.theme.BoxTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: () -> Unit = {},
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val status = loan.loanStatus()
    val interaction = remember { MutableInteractionSource() }
    val progress by animateFloatAsState(
        targetValue = loan.progressFraction,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "loanProgress",
    )
    val isIncome = loan.loanType == LoanType.INCOME
    val money = BoxTheme.money

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else containerColor,
        shape = MaterialTheme.shapes.large,
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
                    fallbackContainer = if (isIncome) money.incomeContainer else money.expenseContainer,
                    fallbackContent = if (isIncome) money.onIncomeContainer else money.onExpenseContainer,
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
                        text = stringResource(R.string.loan_remaining),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(
                        amount = loan.remainingInstallments * loan.installmentAmount,
                        style = BoxTheme.moneyType.large,
                        autoSize = true,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.loan_total_amount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(
                        amount = loan.totalAmount,
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
                val due = loan.nextDueDate
                Text(
                    text = if (!loan.isComplete && due != null) {
                        stringResource(R.string.loan_next_due, due.format(dateFormatter))
                    } else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status == LoanStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.loan_paid_progress, loan.paidInstallments, loan.totalInstallments),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
