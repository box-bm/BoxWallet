package com.boxdev.boxwallet.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.DataManagementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataManagementUiState(
    val isClearing: Boolean = false,
    val clearSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val dataManagementRepository: DataManagementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

    fun clearAll() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isClearing = true, error = null)
                dataManagementRepository.clearAll()
                _uiState.value = _uiState.value.copy(isClearing = false, clearSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isClearing = false, error = e.message ?: "Failed to clear data")
            }
        }
    }

    fun clearTransactions() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isClearing = true, error = null)
                dataManagementRepository.clearTransactions()
                _uiState.value = _uiState.value.copy(isClearing = false, clearSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isClearing = false, error = e.message ?: "Failed to clear transactions")
            }
        }
    }

    fun clearCategories() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isClearing = true, error = null)
                dataManagementRepository.clearCategories()
                _uiState.value = _uiState.value.copy(isClearing = false, clearSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isClearing = false, error = e.message ?: "Failed to clear categories")
            }
        }
    }

    fun resetState() {
        _uiState.value = DataManagementUiState()
    }
}
