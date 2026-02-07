package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.domain.model.FinancialSummary
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SummaryCard(
    summary: FinancialSummary,
    modifier: Modifier = Modifier
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
    val currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = currentMonth,
                style = MaterialTheme.typography.titleMedium,
            )

            // Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "Balance",
                    )
                    Text(
                        text = "Monthly Balance",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(
                    text = currencyFormat.format(summary.monthlyBalance),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.monthlyBalance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Income
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Income",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = currencyFormat.format(summary.monthlyIncome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Expense
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Expense",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Expense",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = currencyFormat.format(summary.monthlyExpense),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Total balance
            Card(
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Balance",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = currencyFormat.format(summary.balance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
