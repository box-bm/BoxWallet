package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.analytics.AnalyticsHelper
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.remote.DriveBackupFile
import com.boxdev.boxwallet.data.remote.GoogleAuthManager
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.data.repository.BackupRepository
import com.boxdev.boxwallet.data.repository.RestoreResult
import com.boxdev.boxwallet.domain.model.BackupData
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
    /** A message from string resources, for outcomes the view model has no context to word. */
    @StringRes val messageRes: Int? = null,
    val isSuccess: Boolean = false,
    /** A backup with another main currency, waiting for the user to choose which one to keep. */
    val currencyMismatch: RestoreResult.CurrencyMismatch? = null,
    /** Main currency change to open after restoring (the device's currency was kept). */
    val currencyChangeTo: String? = null
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
        when (val result = backupRepository.restoreBackup(fileId)) {
            is RestoreResult.Restored -> restored(result.data)
            is RestoreResult.CurrencyMismatch -> {
                _uiState.update { it.copy(currencyMismatch = result) }
                null
            }
            RestoreResult.Incompatible -> {
                _uiState.update { it.copy(messageRes = R.string.backup_error_incompatible, isSuccess = false) }
                null
            }
        }
    }

    /** Restores the pending backup and keeps its main currency. */
    fun keepBackupCurrency() {
        val mismatch = _uiState.value.currencyMismatch ?: return
        _uiState.update { it.copy(currencyMismatch = null) }
        runOp("Error restoring backup") { restored(backupRepository.restore(mismatch.data)) }
    }

    /**
     * Restores the pending backup (in its own currency, so its amounts stay consistent) and then
     * opens the main currency change to the device's currency, which converts every movement.
     */
    fun keepDeviceCurrency() {
        val mismatch = _uiState.value.currencyMismatch ?: return
        _uiState.update { it.copy(currencyMismatch = null) }
        runOp("Error restoring backup") {
            val message = restored(backupRepository.restore(mismatch.data))
            _uiState.update { it.copy(currencyChangeTo = mismatch.device.currencyCode) }
            message
        }
    }

    fun dismissCurrencyMismatch() {
        _uiState.update { it.copy(currencyMismatch = null) }
    }

    fun onCurrencyChangeOpened() {
        _uiState.update { it.copy(currencyChangeTo = null) }
    }

    private fun restored(data: BackupData): String {
        analyticsHelper.logEvent("backup_restored")
        return "Backup restored (${data.transactions.size} movements, ${data.categories.size} categories, ${data.loans.size} loans)"
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
        _uiState.value = _uiState.value.copy(message = null, messageRes = null)
    }
}
