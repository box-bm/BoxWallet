package com.boxdev.boxwallet.ui.screens.settings

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.local.entity.TransactionEntity
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class BackupUiState(
    val isLoading: Boolean = false,
    val backupFiles: List<File> = emptyList(),
    val message: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private val backupFolder: File
        get() {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val boxWalletDir = File(documentsDir, "BoxWallet")
            if (!boxWalletDir.exists()) {
                boxWalletDir.mkdirs()
            }
            return boxWalletDir
        }

    init {
        loadBackupFiles()
    }

    fun loadBackupFiles() {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                backupFolder.listFiles { file ->
                    file.extension == "json" && file.name.startsWith("boxwallet_backup_")
                }?.sortedByDescending { it.lastModified() } ?: emptyList()
            }
            _uiState.value = _uiState.value.copy(backupFiles = files)
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)

            try {
                val transactions = transactionRepository.getAllTransactionsSync()
                val backupData = BackupData(
                    version = 1,
                    createdAt = System.currentTimeMillis(),
                    transactions = transactions
                )

                val json = gson.toJson(backupData)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(backupFolder, "boxwallet_backup_$timestamp.json")

                withContext(Dispatchers.IO) {
                    backupFile.writeText(json)
                }

                loadBackupFiles()
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

    fun restoreBackup(file: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)

            try {
                val json = withContext(Dispatchers.IO) { file.readText() }
                val backupData = gson.fromJson(json, BackupData::class.java)

                transactionRepository.restoreTransactions(backupData.transactions)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Backup restored successfully (${backupData.transactions.size} transactions)",
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

    fun deleteBackup(file: File) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    file.delete()
                }
                loadBackupFiles()
                _uiState.value = _uiState.value.copy(
                    message = "Backup deleted",
                    isSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = "Error deleting backup: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

data class BackupData(
    val version: Int,
    val createdAt: Long,
    val transactions: List<TransactionEntity>
)
