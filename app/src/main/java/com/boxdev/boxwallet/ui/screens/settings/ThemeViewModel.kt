package com.boxdev.boxwallet.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.ThemeMode
import com.boxdev.boxwallet.data.datastore.ThemePreferences
import com.boxdev.boxwallet.data.datastore.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    val themeSettings: StateFlow<ThemeSettings> = themePreferences.themeSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeSettings()
        )

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.updateThemeMode(mode)
            analyticsHelper.logEvent("theme_changed", mapOf("dark_mode" to mode.name))
        }
    }

    fun updateUseDynamicColors(useDynamic: Boolean) {
        viewModelScope.launch {
            themePreferences.updateUseDynamicColors(useDynamic)
            analyticsHelper.logEvent("theme_changed", mapOf("dynamic_colors" to useDynamic))
        }
    }
}
