package com.boxdev.boxwallet.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
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
import com.boxdev.boxwallet.util.DateFormats
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import com.boxdev.boxwallet.data.local.entity.isAdjustment
import com.boxdev.boxwallet.data.local.entity.isInflow
import com.boxdev.boxwallet.data.local.entity.isTransfer
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.ui.theme.BoxTheme

/**
 * One movement in a connected list group. [index] / [count] give the group position so the
 * outer corners round and the inner ones stay tight.
 *
 * A transfer row is titled [transferTitle] ("<from> → <to>") when it has no note, and shows its
 * amount in its own account's currency, signed only when [signedTransfer] (a list scoped to one account).
 * A visacuotas installment is titled "<description> · installment k/N" once its plan's [installmentCount] (N) is known.
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
    containerColor: Color = BoxTheme.cardColor,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    transferTitle: String? = null,
    signedTransfer: Boolean = false,
    installmentCount: Int? = null,
) {
    val isAdjustment = transaction.type.isAdjustment
    val isTransfer = transaction.type.isTransfer
    val isNeutral = isAdjustment || isTransfer
    val isIncome = transaction.type.isInflow
    val kind = amountKind(isIncome)
    // Adjustments and transfers only move money around, so they stay neutral instead of reading as income or expense.
    val (badgeContainer, badgeContent) = if (isNeutral) {
        MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    } else directionColors(isIncome)
    val interaction = remember { MutableInteractionSource() }
    // Short, locale-correct day ("6 nov" / "Nov 6"); the year only appears when it isn't this year.
    val dateFormatter = DateFormats.shortDay(withYear = transaction.date.year != java.time.LocalDate.now().year)
    val timeFormatter = DateFormats.shortTime
    val baseTitle = transaction.description.ifBlank {
        when {
            isAdjustment -> stringResource(R.string.transaction_adjustment)
            isTransfer -> transferTitle ?: stringResource(R.string.transaction_transfer)
            else -> category?.name ?: stringResource(if (isIncome) R.string.transaction_type_income else R.string.transaction_type_expense)
        }
    }
    val number = transaction.installmentNumber
    val title = if (transaction.installmentPlanId != null && number != null && installmentCount != null) {
        stringResource(R.string.installment_row_title, baseTitle, number, installmentCount)
    } else baseTitle

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
                fallbackIcon = when {
                    isAdjustment -> Icons.Outlined.Tune
                    isTransfer -> Icons.Outlined.SwapHoriz
                    else -> trendIcon(isIncome)
                },
                fallbackContainer = badgeContainer,
                fallbackContent = badgeContent,
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
            val formatter = rememberCurrencyFormatter()
            // A transfer reads in the currency of the account its row belongs to.
            val shown = if (isTransfer) transaction.accountAmount else transaction.original
            val equivalent = formatter.withEquivalent(shown, transaction.amount).second
            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    amount = shown,
                    kind = kind,
                    signed = !isTransfer || signedTransfer,
                    color = if (isNeutral) MaterialTheme.colorScheme.onSurfaceVariant else kind.color(),
                )
                if (equivalent != null) EquivalentText(equivalent)
            }
        },
    ) {
        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
