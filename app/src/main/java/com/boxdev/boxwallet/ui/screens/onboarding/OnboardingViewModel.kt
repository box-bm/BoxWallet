package com.boxdev.boxwallet.ui.screens.onboarding

import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.remote.DriveBackupFile
import com.boxdev.boxwallet.data.remote.GoogleAuthManager
import com.boxdev.boxwallet.data.remote.GoogleDriveManager
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.BackupRepository
import com.boxdev.boxwallet.data.repository.RestoreResult
import com.boxdev.boxwallet.domain.model.BackupData
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
    /** An error from string resources, for outcomes the view model has no context to word. */
    @StringRes val errorRes: Int? = null,
    val restoreSuccess: Boolean = false,
    val country: Country? = null,
    /** Main currency to save: the country's, unless the user overrode it. */
    val currency: Currency? = null,
    /** A backup with another main currency, waiting for the user to choose which one to keep. */
    val currencyMismatch: RestoreResult.CurrencyMismatch? = null,
    /** Set with [restoreSuccess] when the device's currency was kept: open the main currency change to it. */
    val currencyChangeTo: String? = null
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
        viewModelScope.launch { finishOnboarding(analyticsEnabled, saveCurrency) }
    }

    internal suspend fun finishOnboarding(analyticsEnabled: Boolean, saveCurrency: Boolean) {
        userPreferences.setAnalyticsEnabled(analyticsEnabled)
        // A restore that committed (even before a crash) already decided the main currency: its
        // amounts are in it, so the onboarding choice must not overwrite it.
        backupRepository.completePendingRestore()
        val restored = accountRepository.count() > 0
        // Before the default account exists: it is created in the current main currency.
        if (saveCurrency && !restored) _uiState.value.currency?.let { userPreferences.setMainCurrency(it.currencyCode) }
        accountRepository.ensureDefaultAccount()
        userPreferences.completeFirstLaunch()
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

    fun restoreBackup(fileId: String) = restoring {
        // The device currency during onboarding is the one being chosen, not yet saved.
        when (val result = backupRepository.restoreBackup(fileId, _uiState.value.currency)) {
            is RestoreResult.Restored -> finishRestore(currencyChangeTo = null)
            is RestoreResult.CurrencyMismatch -> _uiState.value = _uiState.value.copy(currencyMismatch = result)
            RestoreResult.Incompatible -> _uiState.value = _uiState.value.copy(errorRes = R.string.backup_error_incompatible)
        }
    }

    /** Restores the pending backup and keeps its main currency. */
    fun keepBackupCurrency() {
        val mismatch = _uiState.value.currencyMismatch ?: return
        restoring { restore(mismatch.data, currencyChangeTo = null) }
    }

    /**
     * Restores the pending backup in its own currency, then (after onboarding) opens the main
     * currency change to the device's currency, which converts every movement.
     */
    fun keepDeviceCurrency() {
        val mismatch = _uiState.value.currencyMismatch ?: return
        restoring { restore(mismatch.data, currencyChangeTo = mismatch.device.currencyCode) }
    }

    fun dismissCurrencyMismatch() {
        _uiState.value = _uiState.value.copy(currencyMismatch = null)
    }

    private suspend fun restore(data: BackupData, currencyChangeTo: String?) {
        backupRepository.restore(data)
        finishRestore(currencyChangeTo)
    }

    private fun finishRestore(currencyChangeTo: String?) {
        _uiState.value = _uiState.value.copy(
            isRestoring = false,
            currencyMismatch = null,
            currencyChangeTo = currencyChangeTo,
            restoreSuccess = true
        )
        // A restored backup brings its own main currency.
        completeOnboarding(saveCurrency = false)
    }

    private fun restoring(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true, currencyMismatch = null, error = null, errorRes = null)
            try {
                block()
                _uiState.value = _uiState.value.copy(isRestoring = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    error = "Restore failed: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, errorRes = null)
    }
}
