package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.boxdev.boxwallet.ui.screens.analytics.BalanceTrendData

@Composable
fun BalanceTrendChart(
    data: List<BalanceTrendData>,
    modifier: Modifier = Modifier
) {
    val trendColor = if (data.lastOrNull()?.isIncreasing == true) {
        Color(0xFF4CAF50)
    } else {
        Color(0xFFF44336)
    }

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val values = data.map { it.balance }
        val maxVal = values.max()
        val minVal = values.min()
        val range = if (maxVal == minVal) 1.0 else (maxVal - minVal)
        val padding = 16f

        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2

        // Draw zero line if range crosses zero
        if (minVal < 0 && maxVal > 0) {
            val zeroY = padding + chartHeight - ((0.0 - minVal) / range * chartHeight).toFloat()
            drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(padding, zeroY),
                end = androidx.compose.ui.geometry.Offset(size.width - padding, zeroY),
                strokeWidth = 1f
            )
        }

        // Draw trend line
        val path = Path()
        data.forEachIndexed { index, point ->
            val x = padding + (index.toFloat() / (data.size - 1)) * chartWidth
            val y = padding + chartHeight - ((point.balance - minVal) / range * chartHeight).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = trendColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw dots at data points
        data.forEachIndexed { index, point ->
            val x = padding + (index.toFloat() / (data.size - 1)) * chartWidth
            val y = padding + chartHeight - ((point.balance - minVal) / range * chartHeight).toFloat()
            drawCircle(
                color = trendColor,
                radius = 4f,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}
