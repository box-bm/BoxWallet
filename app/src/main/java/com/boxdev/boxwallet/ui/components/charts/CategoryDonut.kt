@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import com.boxdev.boxwallet.domain.model.Category
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.CategoryBadge
import com.boxdev.boxwallet.ui.screens.analytics.CategoryExpenseData
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Spending by category: a donut ranked in one hue (largest slice darkest) with the period total
 * in its hole, above a ranked legend. Category identity stays with the shape badge and name;
 * tapping a slice or a legend row focuses that category.
 */
@Composable
fun CategoryDonut(
    data: List<CategoryExpenseData>,
    modifier: Modifier = Modifier,
    onOpenCategory: ((Category) -> Unit)? = null,
) {
    if (data.isEmpty()) return
    val total = data.sumOf { it.amount }
    val colors = rankedSliceColors(data.size, data.indexOfFirst { it.isOther }.takeIf { it >= 0 })
    var selected by remember(data) { mutableStateOf<Int?>(null) }
    val reveal = rememberChartReveal(data)
    val dimmed = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier
                    .size(220.dp)
                    .clearAndSetSemantics { }
                    .pointerInput(data) {
                        detectTapGestures { tap ->
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val outer = min(size.width, size.height) / 2f
                            val inner = outer - 44.dp.toPx()
                            val dist = hypot(tap.x - c.x, tap.y - c.y)
                            if (dist < inner || dist > outer) {
                                selected = null
                                return@detectTapGestures
                            }
                            var angle = Math.toDegrees(atan2((tap.y - c.y).toDouble(), (tap.x - c.x).toDouble())) + 90.0
                            if (angle < 0) angle += 360.0
                            var acc = 0.0
                            val hit = data.indexOfFirst { item ->
                                acc += item.amount / total * 360.0
                                angle <= acc
                            }.takeIf { it >= 0 }
                            selected = if (hit == selected) null else hit
                        }
                    },
            ) {
                val thick = 30.dp.toPx()
                val thickSelected = 40.dp.toPx()
                val diameter = min(size.width, size.height) - thickSelected
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                val gap = if (data.size > 1) 3f else 0f
                var start = -90f
                data.forEachIndexed { i, item ->
                    val sweep = (item.amount / total * 360.0).toFloat() * reveal()
                    val isSelected = selected == i
                    val color = when {
                        selected == null || isSelected -> colors[i]
                        else -> dimmed
                    }
                    drawArc(
                        color = color,
                        startAngle = start + gap / 2f,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.6f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = if (isSelected) thickSelected else thick, cap = StrokeCap.Butt),
                    )
                    start += sweep
                }
            }
            val focus = selected?.let { data[it] }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 120.dp),
            ) {
                Text(
                    focus?.categoryName ?: stringResource(R.string.analytics_total_spent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                AmountText(
                    amount = focus?.amount ?: total,
                    style = BoxTheme.moneyType.medium,
                    autoSize = true,
                    textAlign = TextAlign.Center,
                )
                if (focus != null) {
                    Text(
                        "%.0f%%".format(focus.percentage),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // A focused real category can be opened as a filtered Movements list.
        val focusedCategory = selected?.let { data[it].category }
        AnimatedVisibility(
            visible = onOpenCategory != null && focusedCategory != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val category = focusedCategory ?: return@AnimatedVisibility
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FilledTonalButton(onClick = { onOpenCategory?.invoke(category) }) {
                    Text(stringResource(R.string.analytics_see_category_movements, category.name))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            data.forEachIndexed { i, item ->
                LegendRow(
                    item = item,
                    color = colors[i],
                    fraction = (item.amount / data.first().amount).toFloat(),
                    selected = selected == i,
                    onClick = { selected = if (selected == i) null else i },
                )
            }
        }
    }
}

@Composable
private fun LegendRow(
    item: CategoryExpenseData,
    color: Color,
    fraction: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val formatter = rememberCurrencyFormatter()
    val bar by animateFloatAsState(
        targetValue = fraction.coerceIn(0.02f, 1f),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "legendBar",
    )
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        label = "legendBg",
    )
    val label = "${item.categoryName}: ${formatter.format(item.amount)}, ${"%.0f".format(item.percentage)}%"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(
            category = item.category,
            fallbackIcon = if (item.isOther) Icons.Outlined.MoreHoriz else Icons.Outlined.Category,
            size = 40.dp,
            selected = selected,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.categoryName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AmountText(amount = item.amount, style = BoxTheme.moneyType.small)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(bar)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(color)
                    )
                }
                Text(
                    "%.0f%%".format(item.percentage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 36.dp),
                )
            }
        }
    }
}
