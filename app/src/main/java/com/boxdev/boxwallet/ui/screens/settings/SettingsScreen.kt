@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3AdaptiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.AppPane
import com.boxdev.boxwallet.ui.components.PanePlaceholder
import com.boxdev.boxwallet.ui.components.BackButton
import com.boxdev.boxwallet.ui.theme.BoxTheme
import kotlinx.coroutines.launch

/**
 * Settings. Single pane: the list, and each section opens as its own route ([onOpenSection]).
 * Two panes (expanded): the list beside the selected section, or a hint while none is selected.
 * Folding with a section open pushes its route, so one pane keeps showing it; unfolding on a
 * section route comes back here with [preselected] (see NavGraph). Links out of a section (a
 * currency, the main currency change) stay full routes.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenCurrency: (String) -> Unit,
    onChangeMainCurrency: (String) -> Unit,
    preselected: SettingsSection? = null,
    onPreselectedConsumed: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val twoPane = BoxTheme.layout.twoPane
    // The section open in the detail pane, kept outside it so a fold can hand it to its route.
    var openSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    LaunchedEffect(twoPane) {
        if (!twoPane) openSection?.let {
            openSection = null
            onOpenSection(it)
        }
    }

    if (!twoPane) {
        SettingsScaffold(title = stringResource(R.string.settings_title), onNavigateBack = onNavigateBack) {
            SettingsList(selected = null, onOpenSection = onOpenSection, viewModel = viewModel)
        }
    } else {
        SettingsListDetail(
            preselected = preselected,
            onPreselectedConsumed = onPreselectedConsumed,
            onSelectionChange = { openSection = it },
            onNavigateBack = onNavigateBack,
            onOpenCurrency = onOpenCurrency,
            onChangeMainCurrency = onChangeMainCurrency,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsListDetail(
    preselected: SettingsSection?,
    onPreselectedConsumed: () -> Unit,
    onSelectionChange: (SettingsSection?) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenCurrency: (String) -> Unit,
    onChangeMainCurrency: (String) -> Unit,
    viewModel: SettingsViewModel,
) {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).copy(
        maxHorizontalPartitions = 2,
        horizontalPartitionSpacerSize = 24.dp,
        defaultPanePreferredWidth = 360.dp,
    )
    // Lives only while two panes show: a fold drops it, and the open section moves to its route.
    val navigator = rememberListDetailPaneScaffoldNavigator<SettingsSection>(scaffoldDirective = directive)
    val scope = rememberCoroutineScope()
    val listShown = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val detailShown = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val bothShown = listShown && detailShown
    val selected = navigator.currentDestination?.contentKey
    val margin = BoxTheme.layout.margin
    LaunchedEffect(selected) { onSelectionChange(selected) }
    LaunchedEffect(preselected) {
        if (preselected != null) {
            // Navigate first: consuming changes this effect's key, which would cancel a navigation still running.
            if (preselected != selected) navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, preselected)
            onPreselectedConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onNavigateBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                // No bottom bar here: the panes stop above the system navigation bar.
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            listPane = {
                AnimatedPane {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = margin, end = if (bothShown) 0.dp else margin)
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            Spacer(Modifier.height(0.dp))
                            SettingsList(
                                selected = if (bothShown) selected else null,
                                onOpenSection = { section ->
                                    if (section != selected) {
                                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, section) }
                                    }
                                },
                                viewModel = viewModel,
                            )
                            Spacer(Modifier.height(0.dp))
                        }
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    // Beside the list the page is the contained pane; on its own it sits on the surface body.
                    DetailPane(
                        contained = bothShown,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = if (bothShown) 0.dp else margin, end = margin, bottom = if (bothShown) margin else 0.dp),
                    ) {
                        when (selected) {
                            null -> PanePlaceholder(
                                icon = Icons.Outlined.Settings,
                                title = stringResource(R.string.settings_select_hint),
                            )
                            SettingsSection.GENERAL -> GeneralSettingsScreen(onNavigateBack = null)
                            SettingsSection.CURRENCIES -> CurrenciesScreen(
                                onNavigateBack = null,
                                onOpenCurrency = onOpenCurrency,
                                onChangeMainCurrency = onChangeMainCurrency,
                            )
                            SettingsSection.CATEGORIES -> CategoriesScreen(onNavigateBack = null)
                            SettingsSection.THEME -> ThemeCustomizationScreen(onNavigateBack = null)
                            SettingsSection.BACKUP -> BackupScreen(
                                onNavigateBack = null,
                                onChangeMainCurrency = onChangeMainCurrency,
                            )
                            SettingsSection.DATA -> DataManagementScreen(
                                onNavigateBack = null,
                                onNavigateToBackup = {
                                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, SettingsSection.BACKUP) }
                                },
                            )
                        }
                    }
                }
            },
        )
    }
}

/** The Settings rows; [selected] highlights the section open in the detail pane. */
@Composable
private fun ColumnScope.SettingsList(
    selected: SettingsSection?,
    onOpenSection: (SettingsSection) -> Unit,
    viewModel: SettingsViewModel,
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
        SectionRow(SettingsSection.GENERAL, 0, 4, Icons.Outlined.Tune, R.string.settings_general_title,
            R.string.settings_general_subtitle, selected, onOpenSection)
        SectionRow(SettingsSection.CURRENCIES, 1, 4, Icons.Outlined.CurrencyExchange, R.string.settings_currencies_title,
            R.string.settings_currencies_subtitle, selected, onOpenSection)
        SectionRow(SettingsSection.CATEGORIES, 2, 4, Icons.Outlined.Category, R.string.settings_categories_title,
            R.string.settings_categories_subtitle, selected, onOpenSection)
        SectionRow(SettingsSection.THEME, 3, 4, Icons.Outlined.Palette, R.string.settings_theme_title,
            R.string.settings_theme_subtitle, selected, onOpenSection)
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
        SectionRow(SettingsSection.BACKUP, 0, 2, Icons.Outlined.Backup, R.string.settings_backup_title,
            R.string.settings_backup_subtitle, selected, onOpenSection)
        SectionRow(SettingsSection.DATA, 1, 2, Icons.Outlined.DeleteForever, R.string.settings_data_management_title,
            R.string.settings_data_management_subtitle, selected, onOpenSection)
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

/** A row that opens [section]. */
@Composable
private fun SectionRow(
    section: SettingsSection,
    index: Int,
    count: Int,
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    selected: SettingsSection?,
    onOpenSection: (SettingsSection) -> Unit,
) {
    SettingsRow(
        index = index,
        count = count,
        icon = icon,
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        onClick = { onOpenSection(section) },
        selected = section == selected,
    )
}

@Composable
private fun DetailPane(contained: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AppPane(modifier, contained) { content() }
}
