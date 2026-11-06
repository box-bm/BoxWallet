package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.ui.components.AmountKind
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.screens.analytics.MonthlyData
import com.boxdev.boxwallet.ui.theme.BoxTheme
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Income and expense per month as two lines on one axis. Touch or drag to inspect a month; the
 * readout above the plot shows that month's values (defaults to the latest month).
 */
@Composable
fun MonthlyTrendChart(
    data: List<MonthlyData>,
    modifier: Modifier = Modifier,
    incomeColor: Color = BoxTheme.money.income,
    expenseColor: Color = BoxTheme.money.expense,
    plotHeight: Dp = 180.dp,
) {
    if (data.isEmpty()) return
    var selected by remember(data) { mutableStateOf(data.lastIndex) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val crosshair = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val measurer = rememberTextMeasurer()
    val point = data[selected]

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                point.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
                    .replaceFirstChar { it.titlecase() } + " " + point.month.year,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            AmountText(point.income, kind = AmountKind.Income, style = BoxTheme.moneyType.small)
            AmountText(point.expense, kind = AmountKind.Expense, style = BoxTheme.moneyType.small)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight)
                .pointerInput(data) {
                    fun pick(x: Float) {
                        val step = if (data.size > 1) size.width / (data.size - 1).toFloat() else size.width.toFloat()
                        selected = (x / step).roundToInt().coerceIn(0, data.lastIndex)
                    }
                    detectTapGestures { pick(it.x) }
                }
                .pointerInput(data) {
                    detectDragGestures { change, _ ->
                        val step = if (data.size > 1) size.width / (data.size - 1).toFloat() else size.width.toFloat()
                        selected = (change.position.x / step).roundToInt().coerceIn(0, data.lastIndex)
                    }
                },
        ) {
            val bottom = 22.dp.toPx()
            val top = 8.dp.toPx()
            val h = size.height - bottom - top
            val max = maxOf(data.maxOf { it.income }, data.maxOf { it.expense }).takeIf { it > 0 } ?: 1.0
            val step = if (data.size > 1) size.width / (data.size - 1) else size.width
            fun x(i: Int) = if (data.size > 1) i * step else size.width / 2
            fun y(v: Double) = top + h - (v / max * h).toFloat()

            // Recessive grid: baseline + two guides.
            listOf(0f, 0.5f, 1f).forEach { f ->
                val gy = top + h * (1 - f)
                drawLine(
                    gridColor, Offset(0f, gy), Offset(size.width, gy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (f == 0f) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
            // Crosshair at the inspected month.
            drawLine(crosshair, Offset(x(selected), top), Offset(x(selected), top + h), strokeWidth = 1.dp.toPx())

            fun series(values: List<Double>, color: Color) {
                val path = Path()
                values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Marker on the inspected month, ringed with the surface so overlaps stay legible.
                val c = Offset(x(selected), y(values[selected]))
                drawCircle(surface, radius = 6.dp.toPx(), center = c)
                drawCircle(color, radius = 4.dp.toPx(), center = c)
            }
            series(data.map { it.income }, incomeColor)
            series(data.map { it.expense }, expenseColor)

            // Thin the month labels when they would collide; count back from the latest month so it
            // always keeps its label.
            val layouts = data.mapIndexed { i, d ->
                measurer.measure(
                    d.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    if (i == selected) selectedLabelStyle else labelStyle,
                )
            }
            val widest = layouts.maxOf { it.size.width } + 8.dp.toPx()
            val stride = if (data.size > 1) kotlin.math.ceil(widest / step).toInt().coerceAtLeast(1) else 1
            layouts.forEachIndexed { i, layout ->
                if ((data.lastIndex - i) % stride != 0) return@forEachIndexed
                val lx = (x(i) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(lx, size.height - layout.size.height))
            }
        }
    }
}
