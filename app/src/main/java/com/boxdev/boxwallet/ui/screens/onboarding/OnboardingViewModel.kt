package com.boxdev.boxwallet.ui.screens.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.remote.DriveBackupFile
import com.boxdev.boxwallet.data.remote.GoogleAuthManager
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.BackupRepository
import com.boxdev.boxwallet.domain.money.Country
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

data class OnboardingUiState(
    val isSigningIn: Boolean = false,
    val isSignedIn: Boolean = false,
    val isLoadingBackups: Boolean = false,
    val isRestoring: Boolean = false,
    val backupFiles: List<DriveBackupFile> = emptyList(),
    val error: String? = null,
    val restoreSuccess: Boolean = false,
    val country: Country? = null,
    /** Main currency to save: the country's, unless the user overrode it. */
    val currency: Currency? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val googleAuthManager: GoogleAuthManager,
    private val googleDriveManager: GoogleDriveManager,
    private val backupRepository: BackupRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    val countries: List<Country> = CurrencyCatalog.countries(Locale.getDefault())

    private val _uiState = MutableStateFlow(
        initialCountry(Locale.getDefault().country, countries).let {
            OnboardingUiState(country = it, currency = it.currency)
        }
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun selectCountry(code: String) {
        val country = countries.firstOrNull { it.code == code } ?: return
        _uiState.value = _uiState.value.copy(country = country, currency = country.currency)
    }

    fun overrideCurrency(currency: Currency) {
        _uiState.value = _uiState.value.copy(currency = currency)
    }

    fun completeOnboarding(analyticsEnabled: Boolean = false, saveCurrency: Boolean = true) {
        viewModelScope.launch {
            userPreferences.setAnalyticsEnabled(analyticsEnabled)
            // Before the default account exists: it is created in the current main currency.
            if (saveCurrency) _uiState.value.currency?.let { userPreferences.setMainCurrency(it.currencyCode) }
            accountRepository.ensureDefaultAccount()
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

                // A restored backup brings its own main currency.
                completeOnboarding(saveCurrency = false)
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
