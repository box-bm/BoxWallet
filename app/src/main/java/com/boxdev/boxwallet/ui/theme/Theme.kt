package com.boxdev.boxwallet.ui.theme

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.boxdev.boxwallet.data.datastore.ThemeMode
import com.boxdev.boxwallet.data.datastore.ThemeSettings

private enum class ContrastLevel { Standard, Medium, High }

/** Honors the system contrast setting (Android 14+), so the brand scheme has accessible variants. */
private fun systemContrast(context: Context): ContrastLevel {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return ContrastLevel.Standard
    val contrast = (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).contrast
    return when {
        contrast >= 0.75f -> ContrastLevel.High
        contrast >= 0.25f -> ContrastLevel.Medium
        else -> ContrastLevel.Standard
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxWalletTheme(
    themeSettings: ThemeSettings = ThemeSettings(),
    darkTheme: Boolean = when (themeSettings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = themeSettings.useDynamicColors,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val contrast = remember(context) { systemContrast(context) }

    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> when (contrast) {
            ContrastLevel.Standard -> darkScheme
            ContrastLevel.Medium -> darkMediumContrastScheme
            ContrastLevel.High -> darkHighContrastScheme
        }
        else -> when (contrast) {
            ContrastLevel.Standard -> lightScheme
            ContrastLevel.Medium -> lightMediumContrastScheme
            ContrastLevel.High -> lightHighContrastScheme
        }
    }
    val moneyColors = when {
        darkTheme -> when (contrast) {
            ContrastLevel.Standard -> darkMoneyColors
            ContrastLevel.Medium -> darkMediumContrastMoneyColors
            ContrastLevel.High -> darkHighContrastMoneyColors
        }
        else -> when (contrast) {
            ContrastLevel.Standard -> lightMoneyColors
            ContrastLevel.Medium -> lightMediumContrastMoneyColors
            ContrastLevel.High -> lightHighContrastMoneyColors
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalMoneyColors provides moneyColors,
        LocalMoneyType provides BoxMoneyType,
        LocalWidthClass provides computeWidthClass(),
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = BoxShapes,
            typography = Typography,
            content = content
        )
    }
}

/** Shorthand accessors that read like MaterialTheme. */
object BoxTheme {
    val money: MoneyColors
        @Composable get() = LocalMoneyColors.current
    val moneyType: MoneyType
        @Composable get() = LocalMoneyType.current
    val widthClass: WidthClass
        @Composable get() = LocalWidthClass.current
}
