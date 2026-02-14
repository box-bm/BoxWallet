package com.boxdev.boxwallet.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.remote.DriveBackupFile
import com.boxdev.boxwallet.data.remote.GoogleAuthManager
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.DataManagementRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.BackupData
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val loanRepository: LoanRepository,
    private val dataManagementRepository: DataManagementRepository,
    private val googleAuthManager: GoogleAuthManager,
    private val googleDriveManager: GoogleDriveManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val gson = Gson()

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

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                val account = googleAuthManager.handleSignInResult(data)
                val email = account.email ?: throw IllegalStateException("No email in account")

                googleDriveManager.initialize(email)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSignedIn = true,
                    accountEmail = email,
                    accountDisplayName = account.displayName
                )

                loadBackups()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Sign-in failed: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                googleAuthManager.signOut()
                googleDriveManager.clear()
                _uiState.value = BackupUiState()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "Sign-out failed: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                val transactions = transactionRepository.getAllTransactionsSync()
                val categories = categoryRepository.getAllCategoriesSync()
                val loans = loanRepository.getAllLoansSync()

                val backupData = BackupData(
                    version = 2,
                    createdAt = System.currentTimeMillis(),
                    transactions = transactions,
                    categories = categories,
                    loans = loans
                )

                val json = gson.toJson(backupData)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "boxwallet_backup_$timestamp.json"

                googleDriveManager.uploadBackup(fileName, json)
                loadBackups()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Backup created successfully",
                    isSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Error creating backup: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun restoreBackup(fileId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                val json = googleDriveManager.downloadBackup(fileId)
                val backupData = gson.fromJson(json, BackupData::class.java)

                // Clear all existing data
                dataManagementRepository.clearAll()

                // Restore in FK order: categories → loans → transactions
                categoryRepository.restoreCategories(backupData.categories)
                loanRepository.restoreLoans(backupData.loans)
                transactionRepository.restoreTransactions(backupData.transactions)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Backup restored (${backupData.transactions.size} transactions, ${backupData.categories.size} categories, ${backupData.loans.size} loans)",
                    isSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Error restoring backup: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun deleteBackup(fileId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                googleDriveManager.deleteBackup(fileId)
                loadBackups()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Backup deleted",
                    isSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Error deleting backup: ${e.message}",
                    isSuccess = false
                )
            }
        }
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
