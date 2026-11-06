package com.boxdev.boxwallet.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.boxdev.boxwallet.R

/**
 * Roboto Flex carries the display voice: wide, heavy and with tabular figures for money.
 * Body and labels stay on the system Roboto so reading text follows the platform.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun flex(weight: Int, width: Float, opticalSize: Float? = null) = Font(
    resId = R.font.roboto_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        *listOfNotNull(
            FontVariation.weight(weight),
            FontVariation.width(width),
            opticalSize?.let { FontVariation.Setting("opsz", it) },
        ).toTypedArray()
    ),
)

/** Display family: headings and titles. */
val FlexDisplay = FontFamily(
    flex(400, 100f),
    flex(500, 100f),
    flex(600, 108f),
    flex(700, 112f),
    flex(800, 118f),
)

/** Numerals family: amounts. Wide and heavy, cut for large sizes. */
val FlexNumerals = FontFamily(
    flex(500, 112f, 72f),
    flex(600, 116f, 72f),
    flex(700, 120f, 96f),
    flex(800, 124f, 120f),
)

private const val TABULAR = "tnum"

val Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = FlexDisplay, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp),
        displayMedium = base.displayMedium.copy(fontFamily = FlexDisplay, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        displaySmall = base.displaySmall.copy(fontFamily = FlexDisplay, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = FlexDisplay, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = FlexDisplay, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = FlexDisplay, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = FlexDisplay, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Amount styles. Tabular figures so columns of money align. */
@Immutable
data class MoneyType(
    val hero: TextStyle,
    val large: TextStyle,
    val medium: TextStyle,
    val small: TextStyle,
)

val BoxMoneyType = MoneyType(
    hero = TextStyle(
        fontFamily = FlexNumerals, fontWeight = FontWeight.ExtraBold,
        fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = TABULAR,
    ),
    large = TextStyle(
        fontFamily = FlexNumerals, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR,
    ),
    medium = TextStyle(
        fontFamily = FlexNumerals, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR,
    ),
    small = TextStyle(
        fontFamily = FlexNumerals, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR,
    ),
)

val LocalMoneyType = staticCompositionLocalOf { BoxMoneyType }
