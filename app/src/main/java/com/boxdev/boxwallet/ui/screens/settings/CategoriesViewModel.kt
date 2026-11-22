package com.boxdev.boxwallet.ui.screens.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    /** The default category, or null when none is set or it no longer exists. */
    val defaultCategory: Category? = null,
    val isLoading: Boolean = true,
    @StringRes val error: Int? = null,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferences,
    private val analyticsHelper: AnalyticsHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(categoryRepository.getAllCategories(), userPreferences.defaultCategoryId) { categories, defaultId ->
                categories to categories.firstOrNull { it.id == defaultId }
            }.collect { (categories, default) ->
                _uiState.update { it.copy(categories = categories, defaultCategory = default, isLoading = false) }
            }
        }
    }

    /** Creates a category when [id] is null, otherwise updates that one. A blank name is ignored. */
    fun save(id: Long?, name: String, description: String, icon: String, color: AccountColor) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val category = Category(id = id ?: 0, name = trimmed, description = description.trim(), icon = icon, color = color)
        run(R.string.categories_save_failed) {
            if (id == null) {
                categoryRepository.insertCategory(category)
                analyticsHelper.logEvent("category_created")
            } else {
                categoryRepository.updateCategory(category)
                analyticsHelper.logEvent("category_updated")
            }
        }
    }

    fun delete(id: Long) {
        run(R.string.categories_delete_failed) {
            categoryRepository.deleteCategory(id)
            if (userPreferences.defaultCategoryId.first() == id) userPreferences.setDefaultCategory(null)
            analyticsHelper.logEvent("category_deleted")
        }
    }

    fun setDefaultCategory(id: Long?) {
        viewModelScope.launch { userPreferences.setDefaultCategory(id) }
        analyticsHelper.logEvent("default_category_changed")
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun run(@StringRes failure: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = failure) }
            }
        }
    }
}
