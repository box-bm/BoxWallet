package com.boxdev.boxwallet.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val themeSettings: StateFlow<ThemeSettings> = themePreferences.themeSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeSettings()
        )

    fun updateDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            themePreferences.updateDarkMode(isDark)
        }
    }

    fun updateUseDynamicColors(useDynamic: Boolean) {
        viewModelScope.launch {
            themePreferences.updateUseDynamicColors(useDynamic)
        }
    }
}
