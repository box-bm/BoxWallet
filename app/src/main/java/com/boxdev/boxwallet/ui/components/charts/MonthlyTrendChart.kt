package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import com.boxdev.boxwallet.ui.screens.analytics.MonthlyData
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthlyTrendChart(
    data: List<MonthlyData>,
    incomeColor: Color = MaterialTheme.colorScheme.primary,
    expenseColor: Color = MaterialTheme.colorScheme.error,
    modifier: Modifier = Modifier
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val bottomPadding = 24.sp.toPx()
        val topPadding = 8f
        val chartHeight = size.height - bottomPadding - topPadding
        val chartWidth = size.width

        val maxValue = maxOf(
            data.maxOfOrNull { it.income } ?: 0.0,
            data.maxOfOrNull { it.expense } ?: 0.0
        )
        if (maxValue == 0.0) return@Canvas

        val stepX = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth
        val dotRadius = 4f
        val lineWidth = 2.5f

        fun yForValue(value: Double): Float =
            topPadding + chartHeight - (value / maxValue * chartHeight).toFloat()

        // Draw income line
        val incomePath = Path()
        data.forEachIndexed { i, d ->
            val x = i * stepX
            val y = yForValue(d.income)
            if (i == 0) incomePath.moveTo(x, y) else incomePath.lineTo(x, y)
        }
        drawPath(incomePath, incomeColor, style = Stroke(width = lineWidth))

        // Draw expense line
        val expensePath = Path()
        data.forEachIndexed { i, d ->
            val x = i * stepX
            val y = yForValue(d.expense)
            if (i == 0) expensePath.moveTo(x, y) else expensePath.lineTo(x, y)
        }
        drawPath(expensePath, expenseColor, style = Stroke(width = lineWidth))

        // Draw dots and labels
        val paint = android.graphics.Paint().apply {
            color = labelColor.hashCode()
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        data.forEachIndexed { i, d ->
            val x = i * stepX

            // Income dot
            drawCircle(incomeColor, dotRadius, Offset(x, yForValue(d.income)))
            // Expense dot
            drawCircle(expenseColor, dotRadius, Offset(x, yForValue(d.expense)))

            // Month label
            val label = d.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                size.height - 4f,
                paint
            )
        }
    }
}
