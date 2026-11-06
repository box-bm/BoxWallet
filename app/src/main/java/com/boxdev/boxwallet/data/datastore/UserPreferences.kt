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

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private val CURRENCY_KEY = stringPreferencesKey("currency")
        private val DEFAULT_CATEGORY_ID_KEY = stringPreferencesKey("default_category_id")
        private val IS_FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")
        private val IS_BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("is_biometric_enabled")
        private val IS_ANALYTICS_ENABLED_KEY = booleanPreferencesKey("is_analytics_enabled")
    }

    val currency: Flow<String> = context.userPreferencesDataStore.data
        .map { preferences ->
            preferences[CURRENCY_KEY] ?: "USD"
        }

    val selectedCurrency: Flow<CentralAmericanCurrency> = currency.map { code ->
        CentralAmericanCurrency.entries.find { it.currencyCode == code } ?: CentralAmericanCurrency.USD
    }

    /** Derived from the stored currency code, so it can never drift from it. */
    val currencySymbol: Flow<String> = selectedCurrency.map { it.symbol }

    val defaultCategoryId: Flow<Long?> = context.userPreferencesDataStore.data
        .map { preferences ->
            preferences[DEFAULT_CATEGORY_ID_KEY]?.toLongOrNull()
        }

    val isFirstLaunch: Flow<Boolean> = context.userPreferencesDataStore.data
        .map { preferences ->
            preferences[IS_FIRST_LAUNCH_KEY] ?: true
        }

    val isBiometricEnabled: Flow<Boolean> = context.userPreferencesDataStore.data
        .map { preferences ->
            preferences[IS_BIOMETRIC_ENABLED_KEY] ?: false
        }

    val isAnalyticsEnabled: Flow<Boolean> = context.userPreferencesDataStore.data
        .map { preferences ->
            preferences[IS_ANALYTICS_ENABLED_KEY] ?: true
        }

    suspend fun setCurrency(currencyCode: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[CURRENCY_KEY] = currencyCode
        }
    }

    suspend fun setDefaultCategory(categoryId: Long?) {
        context.userPreferencesDataStore.edit { preferences ->
            if (categoryId != null) {
                preferences[DEFAULT_CATEGORY_ID_KEY] = categoryId.toString()
            } else {
                preferences.remove(DEFAULT_CATEGORY_ID_KEY)
            }
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[IS_BIOMETRIC_ENABLED_KEY] = enabled
        }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[IS_ANALYTICS_ENABLED_KEY] = enabled
        }
    }

    suspend fun completeFirstLaunch() {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[IS_FIRST_LAUNCH_KEY] = false
        }
    }
}

enum class CentralAmericanCurrency(
    val currencyCode: String,
    val currencyName: String,
    val symbol: String,
    val country: String
) {
    USD("USD", "US Dollar", "$", "United States"),
    GTQ("GTQ", "Guatemalan Quetzal", "Q", "Guatemala"),
    HNL("HNL", "Honduran Lempira", "L", "Honduras"),
    NIO("NIO", "Nicaraguan Córdoba", "C$", "Nicaragua"),
    CRC("CRC", "Costa Rican Colón", "₡", "Costa Rica"),
    PAB("PAB", "Panamanian Balboa", "B/.", "Panama"),
    BZD("BZD", "Belize Dollar", "BZ$", "Belize"),
    SVC("SVC", "Salvadoran Colón", "₡", "El Salvador")
}
