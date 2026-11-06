package com.boxdev.boxwallet.ui.screens.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.remote.DriveBackupFile
import com.boxdev.boxwallet.data.remote.GoogleAuthManager
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val isSigningIn: Boolean = false,
    val isSignedIn: Boolean = false,
    val isLoadingBackups: Boolean = false,
    val isRestoring: Boolean = false,
    val backupFiles: List<DriveBackupFile> = emptyList(),
    val error: String? = null,
    val restoreSuccess: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val googleAuthManager: GoogleAuthManager,
    private val googleDriveManager: GoogleDriveManager,
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun completeOnboarding(analyticsEnabled: Boolean = false) {
        viewModelScope.launch {
            userPreferences.setAnalyticsEnabled(analyticsEnabled)
            userPreferences.completeFirstLaunch()
        }
    }

    fun getSignInIntent(): Intent = googleAuthManager.getSignInIntent()

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSigningIn = true, error = null)
            try {
                val account = googleAuthManager.handleSignInResult(data)
                val email = account.email ?: throw IllegalStateException("No email in account")
                googleDriveManager.initialize(email)

                _uiState.value = _uiState.value.copy(
                    isSigningIn = false,
                    isSignedIn = true
                )
                loadBackups()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSigningIn = false,
                    error = "Sign-in failed: ${e.message}"
                )
            }
        }
    }

    private fun loadBackups() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBackups = true)
            try {
                val files = googleDriveManager.listBackups()
                _uiState.value = _uiState.value.copy(
                    backupFiles = files,
                    isLoadingBackups = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingBackups = false,
                    error = "Failed to load backups: ${e.message}"
                )
            }
        }
    }

    fun restoreBackup(fileId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true, error = null)
            try {
                backupRepository.restoreBackup(fileId)

                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    restoreSuccess = true
                )

                completeOnboarding()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    error = "Restore failed: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
