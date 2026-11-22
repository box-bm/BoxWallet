@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.ui.components.segmentShapes
import com.boxdev.boxwallet.ui.theme.BoxTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.ui.components.BackButton
import com.boxdev.boxwallet.ui.components.readableWidth

/**
 * Shared frame for every settings page: large flexible title that collapses on scroll, content
 * centered at a readable width so it never stretches across an unfolded screen.
 * A null [onNavigateBack] embeds the page in a pane (Settings detail on wide windows): no back
 * arrow, and a transparent background so the page sits on the pane.
 */
@Composable
fun SettingsScaffold(
    title: String,
    onNavigateBack: (() -> Unit)?,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    /** False hides the back arrow, for steps the user must not leave. */
    showBack: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val inPane = onNavigateBack == null
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = if (inPane) Color.Transparent else MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            LargeFlexibleTopAppBar(
                // Long titles ("Respaldo y Restauración") wrap to a second line with a gap before the
                // screen edge instead of running into it.
                title = {
                    Text(
                        title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = { if (showBack && onNavigateBack != null) BackButton(onNavigateBack) },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = if (inPane) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .readableWidth()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Spacer(Modifier.height(0.dp))
                content()
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/** Titled group of segmented rows. */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        content()
    }
}

/** Leading icon in a tonal circle, the Android settings idiom. */
@Composable
fun SettingsIcon(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp)) }
}

/**
 * Navigational or informational row. A null [onClick] renders a static row. [selected] marks the
 * section open in the detail pane (wide windows), in the same tint as a selected loan card.
 */
@Composable
fun SettingsRow(
    index: Int,
    count: Int,
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    iconContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    titleColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = if (onClick != null) {
        { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
    } else null,
    selected: Boolean = false,
) {
    val colors = if (selected) {
        val container = MaterialTheme.colorScheme.secondaryContainer
        val content = MaterialTheme.colorScheme.onSecondaryContainer
        ListItemDefaults.segmentedColors(
            containerColor = container,
            contentColor = content,
            supportingContentColor = content,
            trailingContentColor = content,
            selectedContainerColor = container,
        )
    } else {
        settingsRowColors()
    }
    SegmentedListItem(
        onClick = onClick ?: {},
        enabled = true,
        shapes = segmentShapes(index = index, count = count),
        colors = colors,
        // On the selected tint the default secondaryContainer circle would vanish.
        leadingContent = icon?.let {
            {
                if (selected) {
                    SettingsIcon(it, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
                } else {
                    SettingsIcon(it, iconContainer, iconContent)
                }
            }
        },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = trailing,
    ) { Text(title, color = titleColor) }
}

/** Row with a switch; the whole row toggles. */
@Composable
fun SettingsSwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector?,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        shapes = segmentShapes(index = index, count = count),
        colors = settingsRowColors(),
        leadingContent = icon?.let { { SettingsIcon(it) } },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    ) { Text(title) }
}

/** Rows sit on one tonal container; a switch row's state lives in the switch, not the row tint. */
@Composable
fun settingsRowColors(): androidx.compose.material3.ListItemColors {
    val container = BoxTheme.cardColor
    return ListItemDefaults.segmentedColors(
        containerColor = container,
        selectedContainerColor = container,
        disabledContainerColor = container,
    )
}
