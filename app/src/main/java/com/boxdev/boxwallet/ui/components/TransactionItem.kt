package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Transaction
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    val (icon, color) = when (transaction.type) {
        TransactionType.INCOME -> Icons.Default.ArrowDownward to MaterialTheme.colorScheme.primary
        TransactionType.EXPENSE -> Icons.Default.ArrowUpward to MaterialTheme.colorScheme.error
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = transaction.type.name,
                        tint = color,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = transaction.comment.ifEmpty { transaction.type.name },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = transaction.date.format(dateFormatter),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (transaction.isLoan && transaction.currentInstallment != null) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Installment",
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "${transaction.currentInstallment}/${transaction.totalInstallments}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (transaction.hasReminder) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Has reminder",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Text(
                text = "${if (transaction.type == TransactionType.EXPENSE) "-" else "+"} ${currencyFormat.format(transaction.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
