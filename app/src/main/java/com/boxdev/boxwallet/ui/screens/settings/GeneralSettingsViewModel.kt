package com.boxdev.boxwallet.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GeneralSettingsUiState(
    val categories: List<Category> = emptyList(),
    val defaultCategoryId: Long? = null
)

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    categoryRepository: CategoryRepository,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    val uiState: StateFlow<GeneralSettingsUiState> = combine(
        categoryRepository.getAllCategories(),
        userPreferences.defaultCategoryId,
        ::GeneralSettingsUiState
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GeneralSettingsUiState())

    fun setDefaultCategory(categoryId: Long?) {
        viewModelScope.launch {
            userPreferences.setDefaultCategory(categoryId)
        }
    }
}
