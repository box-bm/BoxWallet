package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxdev.boxwallet.ui.screens.analytics.CategoryExpenseData
import com.boxdev.boxwallet.util.categoryIconVector

@Composable
fun CategoryBarChart(
    data: List<CategoryExpenseData>,
    modifier: Modifier = Modifier
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        if (data.isEmpty()) return@Canvas

        val maxAmount = data.maxOf { it.amount }
        if (maxAmount == 0.0) return@Canvas

        val bottomPadding = 40.sp.toPx()
        val topPadding = 24.sp.toPx()
        val chartHeight = size.height - bottomPadding - topPadding
        val barCount = data.size
        val totalGapRatio = 0.3f
        val barWidth = size.width / (barCount + (barCount - 1) * totalGapRatio / (1f - totalGapRatio)).coerceAtLeast(1f)
        val gap = barWidth * totalGapRatio / (1f - totalGapRatio)

        val paint = android.graphics.Paint().apply {
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        val amountPaint = android.graphics.Paint().apply {
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        data.forEachIndexed { i, category ->
            val x = i * (barWidth + gap)
            val barHeight = (category.amount / maxAmount * chartHeight).toFloat()
            val barTop = topPadding + chartHeight - barHeight

            // Bar
            drawRect(
                color = category.color,
                topLeft = Offset(x, barTop),
                size = Size(barWidth, barHeight)
            )

            // Amount label above bar
            amountPaint.color = category.color.hashCode()
            drawContext.canvas.nativeCanvas.drawText(
                "%.0f".format(category.amount),
                x + barWidth / 2,
                barTop - 6f,
                amountPaint
            )

            // Category name below bar
            paint.color = labelColor.hashCode()
            val label = if (category.categoryName.length > 6) {
                category.categoryName.take(5) + "…"
            } else {
                category.categoryName
            }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x + barWidth / 2,
                size.height - 4f,
                paint
            )
        }
    }

    // Legend with icons below the chart
    Spacer(modifier = Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        data.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = categoryIconVector(category.iconName),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = category.color
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${category.categoryName}: ${"%.2f".format(category.amount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
