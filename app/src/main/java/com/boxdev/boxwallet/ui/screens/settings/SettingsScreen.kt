@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.boxdev.boxwallet.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToGeneral: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToDataManagement: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isAnalyticsEnabled by viewModel.isAnalyticsEnabled.collectAsState()
    val biometricManager = BiometricManager.from(context)
    val biometricAvailable = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ) == BiometricManager.BIOMETRIC_SUCCESS || biometricManager.canAuthenticate(
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ) == BiometricManager.BIOMETRIC_SUCCESS
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val notificationsEnabled = remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled.value = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    val appVersion = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.versionName
    } catch (e: Exception) {
        "Unknown"
    }

    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    SettingsScaffold(title = stringResource(R.string.settings_title), onNavigateBack = onNavigateBack) {
        if (!notificationsEnabled.value) {
            SettingsGroup(title = null) {
                SettingsRow(
                    index = 0, count = 1,
                    icon = Icons.Outlined.NotificationsOff,
                    iconContainer = MaterialTheme.colorScheme.error,
                    iconContent = MaterialTheme.colorScheme.onError,
                    title = stringResource(R.string.settings_notifications_disabled_title),
                    subtitle = stringResource(R.string.settings_notifications_disabled_subtitle),
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    },
                )
            }
        }

        SettingsGroup(stringResource(R.string.settings_preferences_section)) {
            SettingsRow(0, 3, Icons.Outlined.Tune, stringResource(R.string.settings_general_title),
                stringResource(R.string.settings_general_subtitle), onNavigateToGeneral)
            SettingsRow(1, 3, Icons.Outlined.Category, stringResource(R.string.settings_categories_title),
                stringResource(R.string.settings_categories_subtitle), onNavigateToCategories)
            SettingsRow(2, 3, Icons.Outlined.Palette, stringResource(R.string.settings_theme_title),
                stringResource(R.string.settings_theme_subtitle), onNavigateToTheme)
        }

        SettingsGroup(stringResource(R.string.settings_security_section)) {
            SettingsSwitchRow(
                index = 0, count = 1,
                icon = Icons.Outlined.Fingerprint,
                title = stringResource(R.string.settings_app_lock_title),
                subtitle = stringResource(
                    if (biometricAvailable) R.string.settings_app_lock_available else R.string.settings_app_lock_unavailable
                ),
                checked = isBiometricEnabled,
                enabled = biometricAvailable,
                onCheckedChange = viewModel::setBiometricEnabled,
            )
        }

        SettingsGroup(stringResource(R.string.settings_privacy_section)) {
            SettingsSwitchRow(
                index = 0, count = 1,
                icon = Icons.Outlined.QueryStats,
                title = stringResource(R.string.settings_analytics_title),
                subtitle = stringResource(R.string.settings_analytics_subtitle),
                checked = isAnalyticsEnabled,
                onCheckedChange = viewModel::setAnalyticsEnabled,
            )
        }

        SettingsGroup(stringResource(R.string.settings_data_section)) {
            SettingsRow(0, 2, Icons.Outlined.Backup, stringResource(R.string.settings_backup_title),
                stringResource(R.string.settings_backup_subtitle), onNavigateToBackup)
            SettingsRow(1, 2, Icons.Outlined.DeleteForever, stringResource(R.string.settings_data_management_title),
                stringResource(R.string.settings_data_management_subtitle), onNavigateToDataManagement)
        }

        SettingsGroup(stringResource(R.string.settings_about_section)) {
            SettingsRow(0, 4, Icons.Outlined.Info, stringResource(R.string.settings_app_version_title), appVersion ?: "—")
            SettingsRow(1, 4, Icons.Outlined.Code, stringResource(R.string.settings_github_title),
                stringResource(R.string.settings_github_subtitle), { open("https://github.com/box-bm") },
                trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) })
            SettingsRow(2, 4, Icons.Outlined.Description, stringResource(R.string.settings_privacy_policy_title),
                stringResource(R.string.settings_privacy_policy_subtitle),
                { open("https://boxdev.me/en/legal/box-wallet/privacy-policy/") },
                trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) })
            SettingsRow(
                index = 3, count = 4,
                icon = Icons.Outlined.Coffee,
                iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                iconContent = MaterialTheme.colorScheme.onTertiaryContainer,
                title = stringResource(R.string.settings_buy_coffee),
                subtitle = stringResource(R.string.settings_buy_coffee_subtitle),
                onClick = { open("https://buymeacoffee.com/boxdev") },
                trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
            )
        }
    }
}
