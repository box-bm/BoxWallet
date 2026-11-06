package com.boxdev.boxwallet.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/** Material window width classes. Phones and folded foldables are compact; unfolded foldables are medium/expanded. */
enum class WidthClass { Compact, Medium, Expanded }

val WidthClass.isWide: Boolean get() = this != WidthClass.Compact

val LocalWidthClass = staticCompositionLocalOf { WidthClass.Compact }

@Composable
@ReadOnlyComposable
fun computeWidthClass(): WidthClass {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width < 600 -> WidthClass.Compact
        width < 840 -> WidthClass.Medium
        else -> WidthClass.Expanded
    }
}
