package com.boxdev.boxwallet

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.boxdev.boxwallet.data.datastore.ThemePreferences
import com.boxdev.boxwallet.data.datastore.ThemeSettings
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.ui.LockScreen
import com.boxdev.boxwallet.ui.navigation.NavigationTab
import com.boxdev.boxwallet.ui.screens.onboarding.OnboardingScreen
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var userPreferences: UserPreferences

    private var isAuthenticated by mutableStateOf(false)
    private var authError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeSettings by themePreferences.themeSettings.collectAsState(initial = ThemeSettings())
            val isBiometricEnabled by userPreferences.isBiometricEnabled.collectAsState(initial = false)
            val isFirstLaunch by userPreferences.isFirstLaunch.collectAsState(initial = false)

            BoxWalletTheme(themeSettings = themeSettings) {
                when {
                    isFirstLaunch -> OnboardingScreen(
                        onComplete = { /* State updates automatically via DataStore flow */ }
                    )
                    isBiometricEnabled && !isAuthenticated -> LockScreen(
                        onUnlockClick = { showBiometricPrompt() },
                        errorMessage = authError
                    )
                    else -> NavigationTab(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-lock when coming back from background
        val isBiometricEnabled = userPreferences.isBiometricEnabled
        // We use a simple flag: if auth was done, keep it valid for the session
        // The lock triggers again on next cold start or when explicitly toggled
    }

    private fun showBiometricPrompt() {
        authError = null
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock BoxWallet")
            .setSubtitle("Use your fingerprint or device credentials")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticated = true
                    authError = null
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        authError = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    authError = "Authentication failed. Try again."
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }
}
