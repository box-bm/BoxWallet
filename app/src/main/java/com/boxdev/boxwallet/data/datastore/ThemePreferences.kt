package com.boxdev.boxwallet.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColors: Boolean = false
)

@Singleton
class ThemePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        // Legacy key for migration
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    }

    val themeSettings: Flow<ThemeSettings> = context.dataStore.data.map { preferences ->
        val themeMode = preferences[PreferencesKeys.THEME_MODE]?.let { value ->
            ThemeMode.entries.find { it.name.equals(value, ignoreCase = true) }
        } ?: run {
            // Migrate from old isDarkMode boolean if present
            val isDark = preferences[PreferencesKeys.IS_DARK_MODE]
            if (isDark == true) ThemeMode.DARK else ThemeMode.SYSTEM
        }

        ThemeSettings(
            themeMode = themeMode,
            useDynamicColors = preferences[PreferencesKeys.USE_DYNAMIC_COLORS] ?: false
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
            // Remove legacy key
            preferences.remove(PreferencesKeys.IS_DARK_MODE)
        }
    }

    suspend fun updateUseDynamicColors(useDynamic: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLORS] = useDynamic
        }
    }
}
