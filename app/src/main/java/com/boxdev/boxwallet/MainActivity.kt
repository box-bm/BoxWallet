package com.boxdev.boxwallet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat as CoreContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.ThemePreferences
import com.boxdev.boxwallet.data.datastore.ThemeSettings
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.ui.LockScreen
import com.boxdev.boxwallet.ui.navigation.NavigationTab
import com.boxdev.boxwallet.ui.screens.onboarding.OnboardingScreen
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import com.boxdev.boxwallet.util.CurrencyFormatter
import com.boxdev.boxwallet.util.LocalCurrencyFormatter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var analyticsHelper: AnalyticsHelper

    private var isAuthenticated by mutableStateOf(false)
    private var authError by mutableStateOf<String?>(null)
    private var biometricEnabledCache = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No action needed on result — permission is optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        setContent {
            val themeSettings by themePreferences.themeSettings.collectAsState(initial = ThemeSettings())
            val isBiometricEnabled by userPreferences.isBiometricEnabled.collectAsState(initial = false)
            val isFirstLaunch by userPreferences.isFirstLaunch.collectAsState(initial = false)
            val isAnalyticsEnabled by userPreferences.isAnalyticsEnabled.collectAsState(initial = true)
            val currencySymbol by userPreferences.currencySymbol.collectAsState(initial = "$")
            val currencyFormatter = remember(currencySymbol) { CurrencyFormatter(currencySymbol) }

            LaunchedEffect(isBiometricEnabled) { biometricEnabledCache = isBiometricEnabled }
            LaunchedEffect(isAnalyticsEnabled) { analyticsHelper.setCollectionEnabled(isAnalyticsEnabled) }

            CompositionLocalProvider(LocalCurrencyFormatter provides currencyFormatter) {
                BoxWalletTheme(themeSettings = themeSettings) {
                    when {
                        isFirstLaunch -> OnboardingScreen(
                            onComplete = { /* State updates automatically via DataStore flow */ }
                        )
                        isBiometricEnabled && !isAuthenticated -> LockScreen(
                            onUnlockClick = { showBiometricPrompt() },
                            errorMessage = authError
                        )
                        else -> NavigationTab(analyticsHelper = analyticsHelper, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (biometricEnabledCache) {
            isAuthenticated = false
        }
    }

    private fun showBiometricPrompt() {
        authError = null
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
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
                    authError = getString(R.string.biometric_failed)
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (CoreContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
