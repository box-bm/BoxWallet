package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.boxdev.boxwallet.ui.screens.analytics.ProjectionData

@Composable
fun ProjectionLineChart(
    data: List<ProjectionData>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val allValues = data.map { it.projected } + data.mapNotNull { it.actual }
        val maxVal = allValues.maxOrNull() ?: return@Canvas
        val minVal = allValues.minOrNull() ?: return@Canvas
        val range = if (maxVal == minVal) 1.0 else (maxVal - minVal)
        val padding = 16f

        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2

        // Draw projected line (dashed)
        val projectedPath = Path()
        data.forEachIndexed { index, point ->
            val x = padding + (index.toFloat() / (data.size - 1)) * chartWidth
            val y = padding + chartHeight - ((point.projected - minVal) / range * chartHeight).toFloat()
            if (index == 0) projectedPath.moveTo(x, y) else projectedPath.lineTo(x, y)
        }
        drawPath(
            projectedPath,
            color = secondaryColor,
            style = Stroke(
                width = 3f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )
        )

        // Draw actual line (solid, only for non-null values)
        val actualData = data.filter { it.actual != null }
        if (actualData.size >= 2) {
            val actualPath = Path()
            actualData.forEachIndexed { index, point ->
                val dataIndex = data.indexOf(point)
                val x = padding + (dataIndex.toFloat() / (data.size - 1)) * chartWidth
                val y = padding + chartHeight - ((point.actual!! - minVal) / range * chartHeight).toFloat()
                if (index == 0) actualPath.moveTo(x, y) else actualPath.lineTo(x, y)
            }
            drawPath(
                actualPath,
                color = primaryColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
