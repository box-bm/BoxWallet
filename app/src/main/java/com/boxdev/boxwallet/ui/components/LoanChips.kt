package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.ui.theme.BoxTheme
import java.time.LocalDate

enum class LoanStatus {
    PAID, OVERDUE, ACTIVE
}

fun Loan.loanStatus(): LoanStatus = when {
    isComplete -> LoanStatus.PAID
    nextDueDate?.isBefore(LocalDate.now()) == true -> LoanStatus.OVERDUE
    else -> LoanStatus.ACTIVE
}

/**
 * Indicator color for a loan's progress. Progress is what has already been settled, so it never
 * turns red: an overdue loan says so with its status chip and due-date line instead.
 */
@Composable
fun LoanStatus.accent(): Color = when (this) {
    LoanStatus.PAID -> BoxTheme.money.income
    LoanStatus.OVERDUE, LoanStatus.ACTIVE -> MaterialTheme.colorScheme.primary
}

/** Status pill with icon + label, so status never depends on color alone. */
@Composable
fun LoanStatusChip(
    status: LoanStatus,
    modifier: Modifier = Modifier,
) {
    val (label, icon) = when (status) {
        LoanStatus.PAID -> stringResource(R.string.loan_status_paid) to Icons.Filled.CheckCircle
        LoanStatus.OVERDUE -> stringResource(R.string.loan_overdue) to Icons.Filled.ErrorOutline
        LoanStatus.ACTIVE -> stringResource(R.string.loan_status_active) to Icons.Filled.Autorenew
    }
    val (container, content) = when (status) {
        LoanStatus.PAID -> BoxTheme.money.incomeContainer to BoxTheme.money.onIncomeContainer
        LoanStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LoanStatus.ACTIVE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Row(
            modifier = Modifier
                .heightIn(min = 28.dp)
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Neutral pill for a category or a loan direction. */
@Composable
fun InfoChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .heightIn(min = 28.dp)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}
