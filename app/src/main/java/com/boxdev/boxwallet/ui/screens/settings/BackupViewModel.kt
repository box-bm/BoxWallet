package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.analytics.AnalyticsHelper
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.remote.DriveBackupFile
import com.boxdev.boxwallet.data.remote.GoogleAuthManager
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val isLoading: Boolean = false,
    val isLoadingBackups: Boolean = false,
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
    val accountDisplayName: String? = null,
    val backupFiles: List<DriveBackupFile> = emptyList(),
    val message: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val googleAuthManager: GoogleAuthManager,
    private val googleDriveManager: GoogleDriveManager,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        val account = googleAuthManager.getLastSignedInAccount()
        if (account != null) {
            val email = account.email
            if (email != null) {
                googleDriveManager.initialize(email)
                _uiState.value = _uiState.value.copy(
                    isSignedIn = true,
                    accountEmail = email,
                    accountDisplayName = account.displayName
                )
                loadBackups()
            }
        }
    }

    fun getSignInIntent(): Intent = googleAuthManager.getSignInIntent()

    /**
     * Runs a user-triggered operation with the loading flag up. A non-null result becomes the
     * success message; a failure shows "[errorPrefix]: reason".
     */
    private fun runOp(errorPrefix: String, block: suspend () -> String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                val success = block()
                _uiState.update {
                    if (success == null) it.copy(isLoading = false)
                    else it.copy(isLoading = false, message = success, isSuccess = true)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, message = "$errorPrefix: ${e.message}", isSuccess = false) }
            }
        }
    }

    fun handleSignInResult(data: Intent?) = runOp("Sign-in failed") {
        val account = googleAuthManager.handleSignInResult(data)
        val email = account.email ?: throw IllegalStateException("No email in account")
        googleDriveManager.initialize(email)
        _uiState.update { it.copy(isSignedIn = true, accountEmail = email, accountDisplayName = account.displayName) }
        loadBackups()
        null
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                googleAuthManager.signOut()
                googleDriveManager.clear()
                _uiState.value = BackupUiState()
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Sign-out failed: ${e.message}", isSuccess = false) }
            }
        }
    }

    fun createBackup() = runOp("Error creating backup") {
        backupRepository.createBackup()
        loadBackups()
        analyticsHelper.logEvent("backup_created")
        "Backup created successfully"
    }

    fun restoreBackup(fileId: String) = runOp("Error restoring backup") {
        val backupData = backupRepository.restoreBackup(fileId)
        analyticsHelper.logEvent("backup_restored")
        "Backup restored (${backupData.transactions.size} movements, ${backupData.categories.size} categories, ${backupData.loans.size} loans)"
    }

    fun deleteBackup(fileId: String) = runOp("Error deleting backup") {
        googleDriveManager.deleteBackup(fileId)
        loadBackups()
        "Backup deleted"
    }

    private fun loadBackups() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingBackups = true)
            try {
                val files = googleDriveManager.listBackups()
                _uiState.value = _uiState.value.copy(backupFiles = files, isLoadingBackups = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingBackups = false,
                    message = "Error loading backups: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
