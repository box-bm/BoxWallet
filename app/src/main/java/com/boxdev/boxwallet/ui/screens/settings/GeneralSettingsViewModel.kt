package com.boxdev.boxwallet.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.CentralAmericanCurrency
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GeneralSettingsUiState(
    val selectedCurrency: CentralAmericanCurrency = CentralAmericanCurrency.USD,
    val language: String = "en",
    val categories: List<Category> = emptyList(),
    val defaultCategoryId: Long? = null
)

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val categoryRepository: CategoryRepository,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneralSettingsUiState())
    val uiState: StateFlow<GeneralSettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        loadCategories()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            userPreferences.currency.collect { currencyCode ->
                val currency = CentralAmericanCurrency.entries.find {
                    it.currencyCode == currencyCode
                } ?: CentralAmericanCurrency.USD

                _uiState.value = _uiState.value.copy(selectedCurrency = currency)
            }
        }

        viewModelScope.launch {
            userPreferences.language.collect { lang ->
                _uiState.value = _uiState.value.copy(language = lang)
            }
        }

        viewModelScope.launch {
            userPreferences.defaultCategoryId.collect { categoryId ->
                _uiState.value = _uiState.value.copy(defaultCategoryId = categoryId)
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.value = _uiState.value.copy(categories = categories)
            }
        }
    }

    fun updateCurrency(currency: CentralAmericanCurrency) {
        viewModelScope.launch {
            userPreferences.setCurrency(currency.currencyCode, currency.symbol)
            analyticsHelper.logEvent("currency_changed", mapOf("currency" to currency.currencyCode))
            _uiState.value = _uiState.value.copy(selectedCurrency = currency)
        }
    }

    fun updateLanguage(languageCode: String) {
        viewModelScope.launch {
            userPreferences.setLanguage(languageCode)
            _uiState.value = _uiState.value.copy(language = languageCode)
        }
    }

    fun setDefaultCategory(categoryId: Long?) {
        viewModelScope.launch {
            userPreferences.setDefaultCategory(categoryId)
            _uiState.value = _uiState.value.copy(defaultCategoryId = categoryId)
        }
    }
}
