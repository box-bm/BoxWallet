package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Loan
import java.time.LocalDate

enum class LoanStatus {
    PAID, OVERDUE, ACTIVE
}

fun Loan.loanStatus(): LoanStatus = when {
    isComplete -> LoanStatus.PAID
    nextDueDate?.isBefore(LocalDate.now()) == true -> LoanStatus.OVERDUE
    else -> LoanStatus.ACTIVE
}

@Composable
fun LoanCategoryChip(
    categoryName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = categoryName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun LoanStatusChip(
    status: LoanStatus,
    modifier: Modifier = Modifier
) {
    val label = when (status) {
        LoanStatus.PAID -> stringResource(R.string.loan_status_paid)
        LoanStatus.OVERDUE -> stringResource(R.string.loan_overdue)
        LoanStatus.ACTIVE -> stringResource(R.string.loan_status_active)
    }
    val containerColor = when (status) {
        LoanStatus.PAID -> MaterialTheme.colorScheme.primaryContainer
        LoanStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        LoanStatus.ACTIVE -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when (status) {
        LoanStatus.PAID -> MaterialTheme.colorScheme.onPrimaryContainer
        LoanStatus.OVERDUE -> MaterialTheme.colorScheme.onErrorContainer
        LoanStatus.ACTIVE -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Box(
        modifier = modifier
            .background(color = containerColor, shape = RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}
