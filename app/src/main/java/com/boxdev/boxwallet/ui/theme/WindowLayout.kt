package com.boxdev.boxwallet.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material window width classes: compact < 600dp ≤ medium < 840dp ≤ expanded. Phones in portrait and
 * folded foldables are compact; small phones in landscape are medium; Pixel-class phones in landscape,
 * unfolded foldables and tablets are expanded.
 */
enum class WidthClass { Compact, Medium, Expanded }

val WidthClass.isWide: Boolean get() = this != WidthClass.Compact

enum class NavKind { Bar, Rail }

/** Everything screens need to adapt to the current window, derived from the M3 window-size breakpoints. */
data class AppLayout(
    val widthClass: WidthClass,
    val nav: NavKind,
    val railExpanded: Boolean,
    val twoPane: Boolean,
    val margin: Dp,
    val compactHeight: Boolean,
)

/** Pure mapping from window size in dp to [AppLayout]. */
fun appLayout(widthDp: Int, heightDp: Int): AppLayout {
    val widthClass = when {
        widthDp < 600 -> WidthClass.Compact
        widthDp < 840 -> WidthClass.Medium
        else -> WidthClass.Expanded
    }
    return AppLayout(
        widthClass = widthClass,
        nav = if (widthClass == WidthClass.Compact) NavKind.Bar else NavKind.Rail,
        railExpanded = widthDp >= 1200,
        twoPane = widthClass == WidthClass.Expanded,
        margin = if (widthClass == WidthClass.Compact) 16.dp else 24.dp,
        compactHeight = heightDp < 480,
    )
}

val LocalAppLayout = staticCompositionLocalOf { appLayout(widthDp = 411, heightDp = 891) }

@Composable
fun computeAppLayout(): AppLayout {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return appLayout(
        widthDp = with(density) { size.width.toDp() }.value.toInt(),
        heightDp = with(density) { size.height.toDp() }.value.toInt(),
    )
}
