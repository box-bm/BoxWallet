package com.boxdev.boxwallet.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private val LEGACY_CURRENCY_KEY = stringPreferencesKey("currency")
        private val MAIN_CURRENCY_KEY = stringPreferencesKey("main_currency")
        private val DEFAULT_ACCOUNT_ID_KEY = longPreferencesKey("default_account_id")
        private val LEGACY_PENDING_MAIN_CURRENCY_KEY = stringPreferencesKey("pending_main_currency")
        private val LAST_RATE_SYNC_AT_KEY = longPreferencesKey("last_rate_sync_at")
        private val USD: Currency = Currency.getInstance("USD")
        private val DEFAULT_CATEGORY_ID_KEY = stringPreferencesKey("default_category_id")
        private val IS_FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")
        private val IS_BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("is_biometric_enabled")
        private val IS_ANALYTICS_ENABLED_KEY = booleanPreferencesKey("is_analytics_enabled")
    }

    /** Falls back to the legacy "currency" key (pre multi-currency), then USD. */
    val mainCurrency: Flow<Currency> = context.userPreferencesDataStore.data
        .map { preferences ->
            val code = preferences[MAIN_CURRENCY_KEY] ?: preferences[LEGACY_CURRENCY_KEY]
            code?.takeIf { CurrencyCatalog.isSupported(it) }?.let { Currency.getInstance(it) } ?: USD
        }

    val defaultAccountId: Flow<Long?> = context.userPreferencesDataStore.data
        .map { it[DEFAULT_ACCOUNT_ID_KEY] }

    val lastRateSyncAt: Flow<Long?> = context.userPreferencesDataStore.data
        .map { it[LAST_RATE_SYNC_AT_KEY] }

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

    suspend fun setMainCurrency(code: String) {
        context.userPreferencesDataStore.edit { it[MAIN_CURRENCY_KEY] = code }
    }

    suspend fun setDefaultAccountId(id: Long) {
        context.userPreferencesDataStore.edit { it[DEFAULT_ACCOUNT_ID_KEY] = id }
    }

    /** The pending main currency marker now lives in `app_meta`; drops the stale pre-v2 key. */
    suspend fun clearLegacyPendingMainCurrency() {
        context.userPreferencesDataStore.edit { it.remove(LEGACY_PENDING_MAIN_CURRENCY_KEY) }
    }

    suspend fun setLastRateSyncAt(millis: Long) {
        context.userPreferencesDataStore.edit { it[LAST_RATE_SYNC_AT_KEY] = millis }
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
