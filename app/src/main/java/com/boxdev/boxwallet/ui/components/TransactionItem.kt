package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.previewParams.TransactionPreviewParameterProvider
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.util.categoryIconVector
import com.boxdev.boxwallet.util.toCurrencySymbol
import java.time.format.DateTimeFormatter

@Preview()
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransactionItem(
    @PreviewParameter(TransactionPreviewParameterProvider::class) transaction: Transaction,
    modifier: Modifier = Modifier,
    category: Category? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    val typeColor = when (transaction.type) {
        TransactionType.INCOME -> MaterialTheme.colorScheme.onPrimaryContainer
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.onErrorContainer
    }
    val containerTypeColor = when (transaction.type) {
        TransactionType.INCOME -> MaterialTheme.colorScheme.primaryContainer
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.errorContainer
    }
    val displayIcon = if (category != null) {
        categoryIconVector(category.icon)
    } else {
        when (transaction.type) {
            TransactionType.INCOME -> Icons.AutoMirrored.Default.TrendingUp
            TransactionType.EXPENSE -> Icons.AutoMirrored.Default.TrendingDown
        }
    }

    val sign = if (transaction.type == TransactionType.EXPENSE) "-" else "+"
    val symbol = transaction.currency.toCurrencySymbol()

    ListItem(
        content = {
            Text(
                text = transaction.description.ifEmpty { transaction.type.name },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = transaction.date.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (category != null) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (transaction.hasReminder) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.transaction_has_reminder),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier.background(color = containerTypeColor, shape = CircleShape)
            ) {
                Icon(
                    imageVector = displayIcon,
                    contentDescription = category?.name ?: transaction.type.name,
                    tint = typeColor,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(12.dp)
                )
            }
        },
        trailingContent = {
            Text(
                text = "$sign$symbol${"%.2f".format(transaction.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = typeColor
            )
        },
        onClick = {
            onClick()
        },
        onLongClick = {
            onLongClick()
        },
        selected = false,
        modifier = modifier,
    )
}
