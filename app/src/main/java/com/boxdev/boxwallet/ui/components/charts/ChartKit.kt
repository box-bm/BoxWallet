package com.boxdev.boxwallet.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.boxdev.boxwallet.ui.components.rememberReduceMotion
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Entry animation for a chart: 0 → 1 on the slow spatial spring each time [key] changes, or 1
 * straight away when the user has turned animations off.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberChartReveal(key: Any?): () -> Float {
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(key) {
        if (reduceMotion) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, spec)
        }
    }
    // Read lazily, inside draw, so the reveal only re-draws the chart instead of recomposing it each frame.
    return remember(progress) { { progress.value.coerceIn(0f, 1f) } }
}

/**
 * Sequential single-hue ramp for ranked slices: the largest slice takes full primary and each
 * following rank steps toward the surface. "Other" is always neutral.
 */
@Composable
internal fun rankedSliceColors(count: Int, otherIndex: Int? = null): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val neutral = MaterialTheme.colorScheme.outline
    val ranked = (count - if (otherIndex != null) 1 else 0).coerceAtLeast(1)
    return List(count) { i ->
        if (i == otherIndex) neutral
        // Capped at 45% toward the surface so the lowest rank still stands off the pill track in dark theme.
        else lerp(primary, surface, if (ranked == 1) 0f else 0.45f * i / (ranked - 1))
    }
}

/**
 * A de-emphasised series colour for the cards charts sit on: mixed toward the card surface rather
 * than made transparent, so red and green stay recognisable hues in dark theme instead of going
 * brown and olive.
 */
@Composable
internal fun mutedOnCard(color: Color): Color = lerp(color, MaterialTheme.colorScheme.surfaceContainer, 0.45f)

/** A bar with rounded top corners and a flat base sitting on [bottom]. */
internal fun DrawScope.drawTopRoundedBar(
    color: Color,
    left: Float,
    bottom: Float,
    width: Float,
    height: Float,
    radius: Float = width / 2f,
) {
    if (height <= 0f || width <= 0f) return
    val r = radius.coerceAtMost(height).coerceAtMost(width / 2f)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(left, bottom - height, left + width, bottom),
                topLeft = CornerRadius(r, r),
                topRight = CornerRadius(r, r),
                bottomLeft = CornerRadius.Zero,
                bottomRight = CornerRadius.Zero,
            )
        )
    }
    drawPath(path, color)
}

/** Rounds an axis maximum up to 1, 2, 2.5 or 5 × 10ⁿ so the gridlines land on readable values. */
internal fun niceCeiling(value: Double): Double {
    if (value <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val fraction = value / magnitude
    val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= fraction - 1e-9 }
    return ceil(nice * magnitude)
}
