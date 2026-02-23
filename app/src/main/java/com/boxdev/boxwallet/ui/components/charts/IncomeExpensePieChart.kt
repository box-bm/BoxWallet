package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun IncomeExpensePieChart(
    income: Double,
    expense: Double,
    incomeColor: Color = MaterialTheme.colorScheme.primary,
    expenseColor: Color = MaterialTheme.colorScheme.error,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val total = income + expense
        if (total == 0.0) return@Canvas

        val strokeWidth = size.minDimension * 0.18f
        val radius = (size.minDimension - strokeWidth) / 2f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        val incomeAngle = (income / total * 360f).toFloat()
        val expenseAngle = (expense / total * 360f).toFloat()

        drawArc(
            color = incomeColor,
            startAngle = -90f,
            sweepAngle = incomeAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = expenseColor,
            startAngle = -90f + incomeAngle,
            sweepAngle = expenseAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
    }
}
