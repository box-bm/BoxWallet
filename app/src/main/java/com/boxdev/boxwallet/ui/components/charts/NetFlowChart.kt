package com.boxdev.boxwallet.ui.components.charts

import com.boxdev.boxwallet.ui.components.amountKind
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
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
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.screens.analytics.MonthlyData
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Net per month for a year as diverging bars: months that saved rise above the zero line in the
 * income role, months that overspent drop below it in the expense role. Months still ahead stay
 * empty. Tap or drag to inspect a month.
 */
@Composable
fun NetFlowChart(
    data: List<MonthlyData>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 180.dp,
) {
    if (data.isEmpty()) return
    val now = remember { YearMonth.now() }
    var selected by remember(data) {
        mutableIntStateOf(data.indexOfLast { it.income > 0 || it.expense > 0 }.coerceAtLeast(0))
    }
    val income = BoxTheme.money.income
    val expense = BoxTheme.money.expense
    val incomeMuted = mutedOnCard(income)
    val expenseMuted = mutedOnCard(expense)
    val baseline = MaterialTheme.colorScheme.outline
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedAxisStyle = axisStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    val formatter = rememberCurrencyFormatter()
    val reveal = rememberChartReveal(data)
    val previousLabel = stringResource(R.string.chart_a11y_previous)
    val nextLabel = stringResource(R.string.chart_a11y_next)

    val nets = data.map { it.income - it.expense }
    val point = data[selected]
    val net = nets[selected]
    val monthName = point.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
        .replaceFirstChar { it.titlecase() } + " " + point.month.year
    val status = if (net >= 0) stringResource(R.string.analytics_saved) else stringResource(R.string.analytics_overspent)
    // No gridlines here, so the tallest month fills its half instead of a rounded axis maximum.
    val maxAbs = nets.maxOf { abs(it) }.coerceAtLeast(1.0)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(monthName, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AmountText(
                amount = abs(net),
                kind = amountKind(net >= 0),
                style = BoxTheme.moneyType.small,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight)
                .semantics {
                    contentDescription = "$monthName: $status ${formatter.format(abs(net))}"
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
            val plotH = size.height - bottomPad
            val zeroY = plotH / 2f
            val half = plotH / 2f - 4.dp.toPx()
            val slot = size.width / data.size
            val barW = (slot * 0.56f).coerceAtMost(24.dp.toPx())
            val r = barW / 2f

            drawRoundRect(
                highlight,
                topLeft = Offset(slot * selected + slot * 0.08f, 0f),
                size = Size(slot * 0.84f, plotH),
                cornerRadius = CornerRadius(10.dp.toPx()),
            )

            nets.forEachIndexed { i, v ->
                if (data[i].month.isAfter(now) || v == 0.0) return@forEachIndexed
                val h = (abs(v) / maxAbs * half).toFloat().coerceAtLeast(3.dp.toPx()) * reveal()
                val left = slot * i + (slot - barW) / 2f
                // Rounded on the end away from the zero line, flat where it meets it.
                val rect = if (v > 0) Rect(left, zeroY - h, left + barW, zeroY) else Rect(left, zeroY, left + barW, zeroY + h)
                val cr = CornerRadius(r.coerceAtMost(h), r.coerceAtMost(h))
                val shape = if (v > 0) {
                    RoundRect(rect, topLeft = cr, topRight = cr, bottomLeft = CornerRadius.Zero, bottomRight = CornerRadius.Zero)
                } else {
                    RoundRect(rect, topLeft = CornerRadius.Zero, topRight = CornerRadius.Zero, bottomLeft = cr, bottomRight = cr)
                }
                drawPath(Path().apply { addRoundRect(shape) }, when {
                    i == selected -> if (v > 0) income else expense
                    else -> if (v > 0) incomeMuted else expenseMuted
                })
            }

            drawLine(baseline, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())

            data.forEachIndexed { i, d ->
                val text = d.month.month.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault())
                val layout = measurer.measure(text, if (i == selected) selectedAxisStyle else axisStyle)
                val cx = slot * i + slot / 2f
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, size.height - layout.size.height))
            }
        }
    }
}
