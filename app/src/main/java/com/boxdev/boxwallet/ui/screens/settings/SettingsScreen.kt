package com.boxdev.boxwallet.ui.screens.settings

import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGeneral: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToDataManagement: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val biometricAvailable = BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ) == BiometricManager.BIOMETRIC_SUCCESS

    Scaffold(
        contentWindowInsets = WindowInsets(bottom = 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                    )
                },


            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Section
            SettingsSection(title = "General") {
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "General Settings",
                    subtitle = "Currency, language, and regional settings",
                    onClick = onNavigateToGeneral
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsItem(
                    icon = Icons.Default.Category,
                    title = "Categories",
                    subtitle = "Manage transaction categories",
                    onClick = onNavigateToCategories
                )
            }

            // Appearance Section
            SettingsSection(title = "Appearance") {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "Theme Customization",
                    subtitle = "Change app colors and dark mode",
                    onClick = onNavigateToTheme
                )
            }

            // Security Section
            SettingsSection(title = "Security") {
                SettingsToggleItem(
                    icon = Icons.Default.Fingerprint,
                    title = "App Lock",
                    subtitle = if (biometricAvailable) "Require fingerprint or device credentials on open"
                               else "No biometric or screen lock configured on this device",
                    checked = isBiometricEnabled,
                    enabled = biometricAvailable,
                    onCheckedChange = { viewModel.setBiometricEnabled(it) }
                )
            }

            // Data Section
            SettingsSection(title = "Data") {
                SettingsItem(
                    icon = Icons.Default.Backup,
                    title = "Backup & Restore",
                    subtitle = "Save and restore your data",
                    onClick = onNavigateToBackup
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsItem(
                    icon = Icons.Default.Description,
                    title = "Export Reports",
                    subtitle = "Generate Excel and PDF reports",
                    onClick = onNavigateToExport
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Data Management",
                    subtitle = "Clear device and cloud data",
                    onClick = onNavigateToDataManagement
                )
            }

            // About Section
            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "App Version",
                    subtitle = "1.0.0",
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buy Me a Coffee Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW,
                            "https://buymeacoffee.com/boxdev".toUri())
                        context.startActivity(intent)
                    },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFDD00)
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Coffee,
                        contentDescription = "Coffee",
                        modifier = Modifier.size(32.dp),
                        tint = Color.Black
                    )
                    Column(
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Text(
                            text = "Buy Me a Coffee",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "Support the development!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onClick?.invoke() },
                enabled = onClick != null)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
