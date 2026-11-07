@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.FinancialSummary
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Home's lead panel: the total balance on an indigo slab, then this month's money in and out as a
 * proportion bar with the two amounts beneath it.
 */
@Composable
fun BalancePanel(
    summary: FinancialSummary,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val money = BoxTheme.money
    val formatter = rememberCurrencyFormatter()
    val month = remember {
        YearMonth.now().month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
    val totalFlow = summary.monthlyIncome + summary.monthlyExpense
    val incomeShare by animateFloatAsState(
        targetValue = if (totalFlow > 0) (summary.monthlyIncome / totalFlow).toFloat() else 0.5f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "incomeShare",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.primary,
        contentColor = colors.onPrimary,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Text(
                text = stringResource(R.string.summary_total_balance),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onPrimary.copy(alpha = 0.86f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatter.format(summary.balance),
                style = BoxTheme.moneyType.hero,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = BoxTheme.moneyType.hero.fontSize),
                modifier = Modifier.semantics {
                    contentDescription = formatter.format(summary.balance)
                },
            )

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_this_month, month),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimary.copy(alpha = 0.86f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.home_month_net),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimary.copy(alpha = 0.86f),
                )
                Spacer(Modifier.width(8.dp))
                // Signed so the month reads as gain or loss; on the indigo slab the sign carries it, not hue.
                AmountText(
                    amount = kotlin.math.abs(summary.monthlyBalance),
                    kind = amountKind(summary.monthlyBalance >= 0),
                    signed = true,
                    style = BoxTheme.moneyType.small,
                    color = colors.onPrimary,
                )
            }
            Spacer(Modifier.height(10.dp))

            // Proportion bar: income share vs expense share of this month's flow.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val incomeWeight = incomeShare.coerceIn(0.04f, 0.96f)
                Box(
                    Modifier
                        .weight(incomeWeight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp, topEnd = 3.dp, bottomEnd = 3.dp))
                        .background(if (totalFlow > 0) money.incomeContainer else colors.onPrimary.copy(alpha = 0.24f))
                )
                Box(
                    Modifier
                        .weight(1f - incomeWeight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 7.dp, bottomEnd = 7.dp))
                        .background(if (totalFlow > 0) money.expenseContainer else colors.onPrimary.copy(alpha = 0.24f))
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowTile(
                    label = stringResource(R.string.summary_income),
                    amount = summary.monthlyIncome,
                    kind = AmountKind.Income,
                    dot = money.incomeContainer,
                    modifier = Modifier.weight(1f),
                )
                FlowTile(
                    label = stringResource(R.string.summary_expense),
                    amount = summary.monthlyExpense,
                    kind = AmountKind.Expense,
                    dot = money.expenseContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FlowTile(
    label: String,
    amount: Double,
    kind: AmountKind,
    dot: Color,
    modifier: Modifier = Modifier,
) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = trendIcon(kind == AmountKind.Income),
                contentDescription = null,
                tint = onPrimary.copy(alpha = 0.86f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = onPrimary.copy(alpha = 0.86f))
        }
        Spacer(Modifier.height(2.dp))
        AmountText(
            amount = amount,
            kind = kind,
            signed = true,
            style = BoxTheme.moneyType.large,
            color = onPrimary,
            autoSize = true,
        )
    }
}
