package com.boxdev.boxwallet.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

data class ThemeSettings(
    val isDarkMode: Boolean = false,
    val useDynamicColors: Boolean = true
)

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
    }

    val themeSettings: Flow<ThemeSettings> = context.dataStore.data.map { preferences ->
        ThemeSettings(
            isDarkMode = preferences[PreferencesKeys.IS_DARK_MODE] ?: false,
            useDynamicColors = preferences[PreferencesKeys.USE_DYNAMIC_COLORS] ?: true
        )
    }

    suspend fun updateDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_MODE] = isDark
        }
    }

    suspend fun updateUseDynamicColors(useDynamic: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLORS] = useDynamic
        }
    }

    suspend fun updateTheme(settings: ThemeSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_MODE] = settings.isDarkMode
            preferences[PreferencesKeys.USE_DYNAMIC_COLORS] = settings.useDynamicColors
        }
    }
}
