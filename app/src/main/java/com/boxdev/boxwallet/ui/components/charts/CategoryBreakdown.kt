@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.CategoryBadge
import com.boxdev.boxwallet.ui.screens.analytics.CategoryExpenseData
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.rememberCurrencyFormatter

/**
 * Spending by category as a ranked bar list. Magnitude is one hue (primary); identity is carried by
 * each category's shape badge and its name, so color never has to tell categories apart.
 */
@Composable
fun CategoryBreakdown(
    data: List<CategoryExpenseData>,
    modifier: Modifier = Modifier,
) {
    val max = data.maxOfOrNull { it.amount } ?: return
    val formatter = rememberCurrencyFormatter()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        data.forEach { item ->
            val fraction by animateFloatAsState(
                targetValue = if (max > 0) (item.amount / max).toFloat() else 0f,
                animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                label = "categoryBar",
            )
            val label = "${item.categoryName}: ${formatter.format(item.amount)}, ${"%.0f".format(item.percentage)}%"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = label },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(
                    category = item.category,
                    fallbackIcon = Icons.Outlined.Category,
                    size = 40.dp,
                    modifier = Modifier.clearAndSetSemantics { },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.categoryName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%.0f%%".format(item.percentage),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                AmountText(amount = item.amount, style = BoxTheme.moneyType.small)
            }
        }
    }
}
