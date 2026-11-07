package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.ui.components.Panel
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.ConnectedChoiceRow
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.data.datastore.ThemeMode


@Composable
fun ThemeCustomizationScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel()
) {
    val themeSettings by viewModel.themeSettings.collectAsState()

    SettingsScaffold(title = stringResource(R.string.theme_title), onNavigateBack = onNavigateBack) {
        SettingsGroup(stringResource(R.string.theme_appearance)) {
            Panel {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.theme_appearance_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ConnectedChoiceRow(
                        options = ThemeMode.entries,
                        selected = themeSettings.themeMode,
                        onSelect = viewModel::updateThemeMode,
                        label = {
                            when (it) {
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                            }
                        },
                        icon = {
                            when (it) {
                                ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                ThemeMode.DARK -> Icons.Outlined.DarkMode
                                ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                            }
                        },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.theme_preview),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PaletteStrip()
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsGroup(title = null) {
                SettingsSwitchRow(
                    index = 0, count = 1,
                    icon = Icons.Outlined.Wallpaper,
                    title = stringResource(R.string.theme_dynamic_colors),
                    subtitle = stringResource(R.string.theme_dynamic_colors_description),
                    checked = themeSettings.useDynamicColors,
                    onCheckedChange = viewModel::updateUseDynamicColors,
                )
            }
        }
    }
}

/** The live palette: what the current choice paints the app with. */
@Composable
private fun PaletteStrip() {
    val scheme = MaterialTheme.colorScheme
    val money = BoxTheme.money
    val swatches = listOf(scheme.primary, scheme.secondaryContainer, scheme.tertiaryContainer, money.income, money.expense)
    Row(Modifier.fillMaxWidth().height(32.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        swatches.forEachIndexed { i, color ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(
                        when (i) {
                            0 -> RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                            swatches.lastIndex -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp)
                            else -> RoundedCornerShape(6.dp)
                        }
                    )
                    .background(color)
            )
        }
    }
}
