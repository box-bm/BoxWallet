package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import com.boxdev.boxwallet.ui.theme.BoxTheme

/**
 * Line of a rate over time on the same visual language as the other charts: a single primary
 * line over a soft fill, a baseline, and a tap/drag marker. [selected] is reported so the caller
 * can show the day's value; [summary] is the TalkBack description of the whole series.
 * Doubles appear only here, at the drawing edge.
 */
@Composable
fun RateLineChart(
    series: List<Pair<LocalDate, BigDecimal>>,
    summary: String,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 200.dp,
) {
    if (series.isEmpty()) return
    val line = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.onSurface
    val surface = BoxTheme.cardColor
    val reveal = rememberChartReveal(series)
    val values = remember(series) { series.map { it.second.toDouble() } }
    val lo = values.min()
    val hi = values.max()
    val pad = if (hi - lo < 1e-12) (kotlin.math.abs(hi) * 0.05).coerceAtLeast(1e-6) else (hi - lo) * 0.12
    val yMin = lo - pad
    val yMax = hi + pad
    val last = series.lastIndex
    val index = selected.coerceIn(0, last)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(plotHeight)
            .semantics { contentDescription = summary }
            .pointerInput(series) {
                detectTapGestures { onSelect(indexAt(it.x, size.width.toFloat(), last)) }
            }
            .pointerInput(series) {
                detectHorizontalDragGestures { change, _ ->
                    onSelect(indexAt(change.position.x, size.width.toFloat(), last))
                }
            },
    ) {
        val inset = 6.dp.toPx()
        val w = size.width - inset * 2
        val h = size.height - inset * 2
        fun x(i: Int) = inset + if (last == 0) w / 2f else w * i / last
        fun y(v: Double) = inset + h - (((v - yMin) / (yMax - yMin)).toFloat() * h)

        drawLine(baseline, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx())

        val visibleTo = ((last + 1) * reveal()).toInt().coerceIn(1, last + 1) - 1
        if (last == 0) {
            drawCircle(line, 5.dp.toPx(), Offset(x(0), y(values[0])))
        } else {
            val path = Path()
            for (i in 0..visibleTo) {
                if (i == 0) path.moveTo(x(0), y(values[0])) else path.lineTo(x(i), y(values[i]))
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(x(visibleTo), size.height)
                lineTo(x(0), size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.22f), Color.Transparent)))
            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        if (index <= visibleTo) {
            val cx = x(index)
            val cy = y(values[index])
            drawLine(marker.copy(alpha = 0.35f), Offset(cx, 0f), Offset(cx, size.height), 1.dp.toPx())
            drawCircle(surface, 7.dp.toPx(), Offset(cx, cy))
            drawCircle(marker, 4.5.dp.toPx(), Offset(cx, cy))
        }
    }
}

private fun indexAt(x: Float, width: Float, last: Int): Int =
    if (last == 0 || width <= 0f) 0 else ((x / width) * last).let { Math.round(it) }.coerceIn(0, last)
