package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.ui.theme.BoxTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One movement in a connected list group. [index] / [count] give the group position so the
 * outer corners round and the inner ones stay tight.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    category: Category? = null,
    index: Int = 0,
    count: Int = 1,
    showDate: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val kind = if (isIncome) AmountKind.Income else AmountKind.Expense
    val money = BoxTheme.money
    val interaction = remember { MutableInteractionSource() }
    // Short, locale-correct day ("6 nov" / "Nov 6"); the year only appears when it isn't this year.
    val dateFormatter = remember(transaction.date.year) {
        val locale = java.util.Locale.getDefault()
        val skeleton = if (transaction.date.year == java.time.LocalDate.now().year) "MMMd" else "yMMMd"
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val title = transaction.description.ifBlank {
        category?.name ?: stringResource(if (isIncome) R.string.transaction_type_income else R.string.transaction_type_expense)
    }

    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = segmentShapes(index = index, count = count),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        interactionSource = interaction,
        modifier = modifier,
        leadingContent = {
            CategoryBadge(
                category = category,
                fallbackIcon = trendIcon(isIncome),
                fallbackContainer = if (isIncome) money.incomeContainer else money.expenseContainer,
                fallbackContent = if (isIncome) money.onIncomeContainer else money.onExpenseContainer,
                interactionSource = interaction,
            )
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val meta = buildList {
                    if (category != null && transaction.description.isNotBlank()) add(category.name)
                    add(transaction.date.format(if (showDate) dateFormatter else timeFormatter))
                }.joinToString(" · ")
                Text(
                    text = meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (transaction.isRecurrent) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = stringResource(R.string.transaction_recurring),
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (transaction.hasReminder) {
                    Icon(
                        Icons.Outlined.NotificationsActive,
                        contentDescription = stringResource(R.string.transaction_has_reminder),
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (!transaction.isCleared && transaction.date.isAfter(java.time.LocalDateTime.now())) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = stringResource(R.string.transaction_scheduled),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        },
        trailingContent = {
            AmountText(amount = transaction.amount, kind = kind)
        },
    ) {
        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
