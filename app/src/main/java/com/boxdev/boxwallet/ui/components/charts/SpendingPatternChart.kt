package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.AmountKind
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.screens.analytics.DailySpend
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Expense per day across the period as round-topped bars, with a dashed line at the daily
 * average. Days that have not happened yet show as empty stubs. Tap or drag to inspect a day.
 */
@Composable
fun SpendingPatternChart(
    data: List<DailySpend>,
    average: Double,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 160.dp,
) {
    if (data.isEmpty()) return
    var selected by remember(data) { mutableIntStateOf(data.indexOfLast { !it.isFuture }.coerceAtLeast(0)) }
    val expense = BoxTheme.money.expense
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val avgColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedAxisStyle = axisStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    val previousLabel = stringResource(R.string.chart_a11y_previous)
    val nextLabel = stringResource(R.string.chart_a11y_next)
    val formatter = rememberCurrencyFormatter()
    val reveal = rememberChartReveal(data)
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()) }
    val point = data[selected]
    val dayLabel = point.date.format(dayFormatter).replaceFirstChar { it.titlecase() }
    val avgText = stringResource(R.string.analytics_average_short, formatter.formatCompact(average))
    val weekly = data.size <= 7

    val axisMax = niceCeiling(maxOf(data.maxOf { it.amount }, average))

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            AmountText(point.amount, kind = AmountKind.Expense, style = BoxTheme.moneyType.small)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight)
                .semantics {
                    contentDescription = "$dayLabel: ${formatter.format(point.amount)}. $avgText"
                    // TalkBack can't drag; these step the inspected value instead.
                    customActions = listOf(
                        CustomAccessibilityAction(previousLabel) { (selected > 0).also { if (it) selected-- } },
                        CustomAccessibilityAction(nextLabel) { (selected < data.lastIndex).also { if (it) selected++ } },
                    )
                }
                .pointerInput(data) {
                    detectTapGestures { selected = (it.x / (size.width / data.size)).toInt().coerceIn(0, data.lastIndex) }
                }
                .pointerInput(data) {
                    detectHorizontalDragGestures { change, _ ->
                        selected = (change.position.x / (size.width / data.size)).toInt().coerceIn(0, data.lastIndex)
                    }
                },
        ) {
            val bottomPad = 22.dp.toPx()
            val topPad = 22.dp.toPx()
            val plotH = size.height - bottomPad - topPad
            val baseline = topPad + plotH
            val slot = size.width / data.size
            val barW = (slot * if (weekly) 0.5f else 0.62f).coerceAtMost(28.dp.toPx())
            val stub = 3.dp.toPx()
            fun h(v: Double) = (v / axisMax * plotH).toFloat()

            drawLine(gridColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())

            data.forEachIndexed { i, d ->
                val x = slot * i + (slot - barW) / 2f
                val isSelected = i == selected
                if (d.isFuture || d.amount <= 0.0) {
                    drawRoundRect(
                        track,
                        topLeft = Offset(x, baseline - stub),
                        size = Size(barW, stub),
                        cornerRadius = CornerRadius(stub / 2f),
                    )
                } else {
                    val color = if (isSelected) expense else expense.copy(alpha = 0.4f)
                    drawTopRoundedBar(color, x, baseline, barW, (h(d.amount) * reveal).coerceAtLeast(stub))
                }
            }

            // Average line with its value in a pill at the right edge.
            if (average > 0) {
                val ay = baseline - h(average) * reveal
                drawLine(
                    avgColor, Offset(0f, ay), Offset(size.width, ay),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
                val label = measurer.measure(avgText, axisStyle)
                val padH = 6.dp.toPx()
                val padV = 2.dp.toPx()
                val pillW = label.size.width + padH * 2
                val pillH = label.size.height + padV * 2
                val pillTop = (ay - pillH - 4.dp.toPx()).coerceAtLeast(0f)
                drawRoundRect(
                    pillColor,
                    topLeft = Offset(size.width - pillW, pillTop),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(pillH / 2f),
                )
                drawText(label, topLeft = Offset(size.width - pillW + padH, pillTop + padV))
            }

            // Weekday initials for a week; for a month, the 1st, 8th, 15th, 22nd and 29th plus the inspected day.
            data.forEachIndexed { i, d ->
                val show = weekly || (d.date.dayOfMonth - 1) % 7 == 0 || i == selected
                if (!show) return@forEachIndexed
                if (!weekly && i != selected && kotlin.math.abs(i - selected) < 3) return@forEachIndexed
                val text = if (weekly) {
                    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replace(".", "")
                } else d.date.dayOfMonth.toString()
                val layout = measurer.measure(text, if (i == selected) selectedAxisStyle else axisStyle)
                val cx = slot * i + slot / 2f
                val lx = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(lx, size.height - layout.size.height))
            }
        }
    }
}
