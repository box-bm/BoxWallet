@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.onboarding

import com.boxdev.boxwallet.ui.screens.settings.CurrencyMismatchDialog
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.graphics.shapes.Morph
import com.boxdev.boxwallet.ui.components.MorphShape
import com.boxdev.boxwallet.ui.components.rememberReduceMotion
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.isWide
import kotlin.math.roundToInt
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.domain.money.pickerSections
import com.boxdev.boxwallet.ui.components.CurrencyPickerSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.boxdev.boxwallet.domain.money.Country
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

private data class OnboardingPage(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Outlined.AccountBalanceWallet,
        titleRes = R.string.onboarding_welcome_title,
        descriptionRes = R.string.onboarding_welcome_description
    ),
    OnboardingPage(
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        titleRes = R.string.onboarding_track_title,
        descriptionRes = R.string.onboarding_track_description
    ),
    OnboardingPage(
        icon = Icons.Outlined.Shield,
        titleRes = R.string.onboarding_privacy_title,
        descriptionRes = R.string.onboarding_privacy_description
    ),
    OnboardingPage(
        icon = Icons.Outlined.Public,
        titleRes = R.string.onboarding_country_title,
        descriptionRes = R.string.onboarding_country_description
    )
)

private val PRIVACY_PAGE_INDEX = onboardingPages.indexOfFirst { it.titleRes == R.string.onboarding_privacy_title }
private val COUNTRY_PAGE_INDEX = onboardingPages.indexOfFirst { it.titleRes == R.string.onboarding_country_title }

@Composable
fun OnboardingScreen(
    /** Called once onboarding ends; the code, when set, is a main currency change to open next. */
    onComplete: (currencyChangeTo: String?) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val totalPages = onboardingPages.size + 1
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    var showRestoreDialog by remember { mutableStateOf(false) }
    var analyticsEnabled by remember { mutableStateOf(true) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleSignInResult(result.data)
        }
    }

    LaunchedEffect(uiState.restoreSuccess) {
        if (uiState.restoreSuccess) {
            onComplete(uiState.currencyChangeTo)
        }
    }

    val mismatch = uiState.currencyMismatch
    if (mismatch != null) {
        CurrencyMismatchDialog(
            backup = mismatch.backup,
            device = mismatch.device,
            onKeepBackup = viewModel::keepBackupCurrency,
            onKeepDevice = viewModel::keepDeviceCurrency,
            onDismiss = viewModel::dismissCurrencyMismatch,
        )
    } else if (showRestoreDialog) {
        RestoreFromBackupDialog(
            uiState = uiState,
            onSignIn = { signInLauncher.launch(viewModel.getSignInIntent()) },
            onRestore = { fileId -> viewModel.restoreBackup(fileId) },
            onDismiss = {
                showRestoreDialog = false
                viewModel.clearError()
            }
        )
    }

    val isLastPage by remember {
        derivedStateOf { pagerState.currentPage == totalPages - 1 }
    }

    val wide = BoxTheme.widthClass.isWide
    val heroPolygons = remember {
        listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.SoftBurst)
    }
    val heroIcons = onboardingPages.map { it.icon } + Icons.Outlined.RocketLaunch
    val scheme = MaterialTheme.colorScheme
    val heroColors = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiary to scheme.onTertiary,
        scheme.primary to scheme.onPrimary,
    )

    val hero: @Composable (Modifier) -> Unit = { modifier ->
        OnboardingHero(pagerState, heroPolygons, heroIcons, heroColors, modifier)
    }
    val pager: @Composable (Modifier) -> Unit = { modifier ->
        HorizontalPager(state = pagerState, modifier = modifier, verticalAlignment = Alignment.Top) { page ->
            val title: Int
            val body: Int
            if (page < onboardingPages.size) {
                title = onboardingPages[page].titleRes
                body = onboardingPages[page].descriptionRes
            } else {
                title = R.string.onboarding_get_started_title
                body = R.string.onboarding_get_started_description
            }
            // The country page lets its list be the only scroller; other pages scroll as a whole.
            val pageModifier = if (page == COUNTRY_PAGE_INDEX) Modifier.fillMaxSize()
            else Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            Column(
                pageModifier.padding(horizontal = 28.dp),
                horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(title),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                )
                if (page == PRIVACY_PAGE_INDEX) {
                    Spacer(Modifier.height(28.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(analyticsEnabled, role = Role.Switch, onValueChange = { analyticsEnabled = it }),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.onboarding_analytics_toggle),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = analyticsEnabled, onCheckedChange = null)
                        }
                    }
                }
                if (page == COUNTRY_PAGE_INDEX) {
                    Spacer(Modifier.height(20.dp))
                    CountryStep(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        countries = viewModel.countries,
                        selected = uiState.country,
                        currency = uiState.currency,
                        onSelect = viewModel::selectCountry,
                        onOverrideCurrency = viewModel::overrideCurrency,
                    )
                }
            }
        }
    }
    val controls: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageDots(pagerState.currentPage, totalPages)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLastPage) {
                    Button(
                        onClick = { viewModel.completeOnboarding(analyticsEnabled); onComplete(null) },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.MediumContentPadding,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.onboarding_get_started_button), style = MaterialTheme.typography.titleMedium) }
                    FilledTonalButton(
                        onClick = { showRestoreDialog = true },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.MediumContentPadding,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.onboarding_restore_from_backup), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Button(
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.MediumContentPadding,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.onboarding_next), style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (!isLastPage) {
                    TextButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(if (pagerState.currentPage < COUNTRY_PAGE_INDEX) COUNTRY_PAGE_INDEX else totalPages - 1) } }) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
            }
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    hero(Modifier.weight(1f))
                    Column(Modifier.weight(1f).fillMaxHeight().widthIn(max = 520.dp), verticalArrangement = Arrangement.Center) {
                        pager(Modifier.weight(1f, fill = false).heightIn(min = 240.dp))
                        controls()
                    }
                }
            } else {
                hero(Modifier.fillMaxWidth().weight(0.9f))
                pager(Modifier.fillMaxWidth().weight(1f))
                controls()
            }
        }
    }
}

/**
 * One hero shape for the whole flow: it morphs between each page's polygon as the pager moves,
 * so swiping feels like reshaping one object rather than flipping cards.
 */
@Composable
private fun OnboardingHero(
    pagerState: androidx.compose.foundation.pager.PagerState,
    polygons: List<androidx.graphics.shapes.RoundedPolygon>,
    icons: List<ImageVector>,
    colors: List<Pair<Color, Color>>,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val morphs = remember(polygons) { polygons.zipWithNext { a, b -> Morph(a, b) } }
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val from = position.toInt().coerceIn(0, morphs.lastIndex)
    val fraction = (position - from).coerceIn(0f, 1f)
    val nearest = position.roundToInt().coerceIn(0, icons.lastIndex)
    val container = lerp(colors[from].first, colors[(from + 1).coerceAtMost(colors.lastIndex)].first, fraction)
    val iconTint = colors[nearest].second
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxHeight(0.82f)
                .aspectRatio(1f)
                .graphicsLayer { rotationZ = if (reduceMotion) 0f else position * 45f }
                .clip(MorphShape(morphs[from], fraction))
                .background(container),
        )
        Crossfade(targetState = nearest, label = "heroIcon") { i ->
            Icon(icons[i], contentDescription = null, tint = iconTint, modifier = Modifier.size(88.dp))
        }
    }
}

@Composable
private fun PageDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { index ->
            val selected = current == index
            val width by animateDpAsState(
                if (selected) 28.dp else 8.dp,
                MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "dot",
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun RestoreFromBackupDialog(
    uiState: OnboardingUiState,
    onSignIn: () -> Unit,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!uiState.isRestoring) onDismiss()
        },
        title = { Text(stringResource(R.string.onboarding_restore_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isRestoring) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LoadingIndicator()
                        Text(
                            text = stringResource(R.string.onboarding_restoring),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (!uiState.isSignedIn) {
                    Text(
                        text = stringResource(R.string.onboarding_sign_in_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (uiState.isSigningIn) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    } else {
                        Button(
                            onClick = onSignIn,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.backup_sign_in))
                        }
                    }
                } else if (uiState.isLoadingBackups) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                } else if (uiState.backupFiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.onboarding_no_backups),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = stringResource(R.string.onboarding_select_backup),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
                    LazyColumn(
                        modifier = Modifier.height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.backupFiles, key = { it.id }) { file ->
                            Card(
                                onClick = { onRestore(file.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = dateFormat.format(Date(file.createdTime)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = file.formattedSize,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                uiState.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!uiState.isRestoring) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@Composable
private fun CountryStep(
    modifier: Modifier,
    countries: List<Country>,
    selected: Country?,
    currency: Currency?,
    onSelect: (String) -> Unit,
    onOverrideCurrency: (Currency) -> Unit,
) {
    val locale = remember { Locale.getDefault() }
    var query by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    val visible = remember(query, countries) { filterCountries(countries, query, locale) }
    val listState = rememberLazyListState()
    // Bring the preselected (device region) country into view on first show.
    LaunchedEffect(Unit) {
        val index = countries.indexOfFirst { it.code == selected?.code }
        if (index > 0) listState.scrollToItem(index)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.onboarding_country_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            shape = MaterialTheme.shapes.large,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.onboarding_country_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(state = listState) {
                    items(visible, key = { it.code }) { country ->
                        val isSelected = country.code == selected?.code
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(country.code) })
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                country.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        if (currency != null) {
            val label = "${CurrencyCatalog.displayName(currency, locale)} (${currency.currencyCode})"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.onboarding_country_currency, label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                val changeDescription = stringResource(R.string.onboarding_country_change_description)
                TextButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = changeDescription },
                ) { Text(stringResource(R.string.onboarding_country_change)) }
            }
        }
    }

    if (showPicker && currency != null) {
        var pickerQuery by remember { mutableStateOf("") }
        val allCurrencies = remember(locale) { CurrencyCatalog.all(locale) }
        val sections = remember(pickerQuery, allCurrencies) {
            pickerSections(allCurrencies, emptyList(), pickerQuery, locale)
        }
        CurrencyPickerSheet(
            selected = currency,
            sections = sections,
            onQuery = { pickerQuery = it },
            onPick = { onOverrideCurrency(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}
