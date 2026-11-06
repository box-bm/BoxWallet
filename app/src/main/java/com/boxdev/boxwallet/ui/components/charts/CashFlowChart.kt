package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
import com.boxdev.boxwallet.ui.screens.analytics.MonthlyData
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * Income and expense per month as paired, round-topped bars on a labelled axis. Tap or drag to
 * inspect a month; its bars stay at full strength, the rest recede, and the readout above the
 * plot gives that month's income, expense and net (defaults to the latest month).
 */
@Composable
fun CashFlowChart(
    data: List<MonthlyData>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 200.dp,
) {
    if (data.isEmpty()) return
    var selected by remember(data) { mutableIntStateOf(data.indexOfLast { it.income > 0 || it.expense > 0 }.coerceAtLeast(0)) }
    val income = BoxTheme.money.income
    val expense = BoxTheme.money.expense
    val incomeMuted = mutedOnCard(income)
    val expenseMuted = mutedOnCard(expense)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedAxisStyle = axisStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    val previousLabel = stringResource(R.string.chart_a11y_previous)
    val nextLabel = stringResource(R.string.chart_a11y_next)
    val formatter = rememberCurrencyFormatter()
    val reveal = rememberChartReveal(data)
    val point = data[selected]
    val net = point.income - point.expense
    val monthName = point.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
        .replaceFirstChar { it.titlecase() } + " " + point.month.year

    val axisMax = niceCeiling(data.maxOf { maxOf(it.income, it.expense) })
    val axisLabels = listOf(0.0, axisMax / 2, axisMax).map { measurer.measure(formatter.formatCompact(it), axisStyle) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(monthName, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.analytics_net_label_short),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(
                        amount = kotlin.math.abs(net),
                        kind = if (net >= 0) AmountKind.Income else AmountKind.Expense,
                        style = BoxTheme.moneyType.small,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SeriesValue(income, point.income, AmountKind.Income)
                SeriesValue(expense, point.expense, AmountKind.Expense)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight)
                .semantics {
                    contentDescription = "$monthName: +${formatter.format(point.income)}, −${formatter.format(point.expense)}"
                    // TalkBack can't drag; these step the inspected value instead.
                    customActions = listOf(
                        CustomAccessibilityAction(previousLabel) { (selected > 0).also { if (it) selected-- } },
                        CustomAccessibilityAction(nextLabel) { (selected < data.lastIndex).also { if (it) selected++ } },
                    )
                }
                .pointerInput(data) {
                    val left = axisLabels.maxOf { it.size.width } + 8.dp.toPx()
                    fun pick(x: Float) {
                        val slot = (size.width - left) / data.size
                        selected = ((x - left) / slot).toInt().coerceIn(0, data.lastIndex)
                    }
                    detectTapGestures { pick(it.x) }
                }
                .pointerInput(data) {
                    val left = axisLabels.maxOf { it.size.width } + 8.dp.toPx()
                    detectHorizontalDragGestures { change, _ ->
                        val slot = (size.width - left) / data.size
                        selected = ((change.position.x - left) / slot).toInt().coerceIn(0, data.lastIndex)
                    }
                },
        ) {
            val left = axisLabels.maxOf { it.size.width } + 8.dp.toPx()
            val bottomPad = 22.dp.toPx()
            val topPad = 8.dp.toPx()
            val plotW = size.width - left
            val plotH = size.height - bottomPad - topPad
            val baseline = topPad + plotH
            val slot = plotW / data.size
            fun y(v: Double) = (v / axisMax * plotH).toFloat()

            // Gridlines with compact values: solid baseline, dashed guides.
            listOf(0f, 0.5f, 1f).forEachIndexed { i, f ->
                val gy = baseline - plotH * f
                drawLine(
                    gridColor, Offset(left, gy), Offset(size.width, gy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
                val label = axisLabels[i]
                drawText(label, topLeft = Offset(left - 8.dp.toPx() - label.size.width, gy - label.size.height / 2f))
            }

            // Selected month sits on a soft column.
            drawRoundRect(
                highlight,
                topLeft = Offset(left + slot * selected + slot * 0.08f, topPad - 4.dp.toPx()),
                size = Size(slot * 0.84f, plotH + 4.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx()),
            )

            val gap = 2.dp.toPx()
            val barW = ((slot * 0.64f - gap) / 2f).coerceAtMost(14.dp.toPx())
            data.forEachIndexed { i, d ->
                val isSelected = i == selected
                val groupLeft = left + slot * i + (slot - (barW * 2 + gap)) / 2f
                drawTopRoundedBar(if (isSelected) income else incomeMuted, groupLeft, baseline, barW, y(d.income) * reveal)
                drawTopRoundedBar(if (isSelected) expense else expenseMuted, groupLeft + barW + gap, baseline, barW, y(d.expense) * reveal)
            }

            // Month initials, thinned when they would collide; the latest month always keeps its label.
            val layouts = data.mapIndexed { i, d ->
                measurer.measure(
                    d.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replace(".", ""),
                    if (i == selected) selectedAxisStyle else axisStyle,
                )
            }
            val widest = layouts.maxOf { it.size.width } + 6.dp.toPx()
            val stride = ceil(widest / slot).toInt().coerceAtLeast(1)
            layouts.forEachIndexed { i, layout ->
                if ((data.lastIndex - i) % stride != 0 && i != selected) return@forEachIndexed
                val cx = left + slot * i + slot / 2f
                val lx = (cx - layout.size.width / 2f).coerceIn(left, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(lx, size.height - layout.size.height))
            }
        }
    }
}

@Composable
private fun SeriesValue(color: Color, amount: Double, kind: AmountKind) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        AmountText(amount, kind = kind, style = BoxTheme.moneyType.small)
    }
}
