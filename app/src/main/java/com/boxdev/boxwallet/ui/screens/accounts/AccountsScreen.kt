@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.boxdev.boxwallet.ui.screens.accounts

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.CardSummary
import com.boxdev.boxwallet.domain.model.primaryUsed
import com.boxdev.boxwallet.domain.model.secondUsed
import com.boxdev.boxwallet.domain.model.TotalBalance
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.BackButton
import com.boxdev.boxwallet.ui.components.CardUsageBar
import com.boxdev.boxwallet.ui.components.usageFraction
import com.boxdev.boxwallet.ui.components.PanePlaceholder
import com.boxdev.boxwallet.ui.components.PaneRow
import com.boxdev.boxwallet.ui.components.segmentShapes
import com.boxdev.boxwallet.ui.screens.settings.SettingsGroup
import com.boxdev.boxwallet.ui.screens.settings.SettingsScaffold
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import com.boxdev.boxwallet.ui.theme.container
import com.boxdev.boxwallet.ui.theme.onContainer
import com.boxdev.boxwallet.util.accountIcon
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.util.Currency

/**
 * The accounts list: total balance, active accounts, then "Archived" folded away.
 * One pane: a tapped account opens its own route ([onOpenAccount]). Two panes: the list 360dp
 * beside the selected account's detail, or a hint while none is selected; folding with one open
 * hands it to its route.
 */
@Composable
fun AccountsScreen(
    onNavigateBack: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onNewAccount: () -> Unit,
    onEditAccount: (Long) -> Unit,
    onEditTransaction: (Long) -> Unit,
    onSeeInMovements: () -> Unit,
    onOpenTransfer: (Long) -> Unit = {},
    onOpenInstallmentPlan: (Long) -> Unit = {},
    onOpenLoan: (Long) -> Unit = {},
    /** Opens a new loan in the given account. */
    onNewLoan: (Long) -> Unit = {},
    /** Opens a new transfer from the given account. */
    onNewTransfer: (Long) -> Unit = {},
    /** Opens a route from the detail pane (a card's Pay). */
    onNavigate: (String) -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val twoPane = BoxTheme.layout.twoPane
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(twoPane) {
        if (!twoPane) selectedId?.let {
            selectedId = null
            onOpenAccount(it)
        }
    }

    if (!twoPane) {
        SettingsScaffold(title = stringResource(R.string.accounts_title), onNavigateBack = onNavigateBack) {
            AccountsList(uiState, selectedId = null, onOpen = onOpenAccount, onNewAccount = onNewAccount)
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = { BackButton(onNavigateBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        PaneRow(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            firstWidth = 360.dp,
            containSecond = true,
            first = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Spacer(Modifier.height(0.dp))
                    AccountsList(uiState, selectedId, onOpen = { selectedId = it }, onNewAccount = onNewAccount)
                    Spacer(Modifier.height(16.dp))
                }
            },
            second = {
                val id = selectedId
                if (id == null) {
                    PanePlaceholder(Icons.Outlined.AccountBalanceWallet, stringResource(R.string.accounts_select_hint))
                } else {
                    AccountDetailScreen(
                        onNavigateBack = null,
                        onEditAccount = onEditAccount,
                        onEditTransaction = onEditTransaction,
                        onSeeInMovements = onSeeInMovements,
                        onDeleted = { selectedId = null },
                        onOpenTransfer = onOpenTransfer,
                        onOpenInstallmentPlan = onOpenInstallmentPlan,
                        onOpenLoan = onOpenLoan,
                        onNewLoan = onNewLoan,
                        onNewTransfer = onNewTransfer,
                        onNavigate = onNavigate,
                        accountId = id,
                        viewModel = hiltViewModel(key = "account_detail_pane"),
                    )
                }
            },
        )
    }
}

/** Total, active accounts, the new-account button and the folded archived group. */
@Composable
private fun ColumnScope.AccountsList(
    uiState: AccountsUiState,
    selectedId: Long?,
    onOpen: (Long) -> Unit,
    onNewAccount: () -> Unit,
) {
    if (uiState.isLoading) {
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    uiState.total?.let { TotalPanel(it) }
    SettingsGroup(title = null) {
        uiState.active.forEachIndexed { index, item ->
            AccountRow(
                item, uiState.cards[item.account.id], index, uiState.active.size,
                item.account.id == uiState.defaultAccountId, item.account.id == selectedId,
            ) {
                onOpen(item.account.id)
            }
        }
    }
    FilledTonalButton(onClick = onNewAccount, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.accounts_new))
    }
    if (uiState.archived.isNotEmpty()) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        Column {
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(R.string.accounts_archived, uiState.archived.size))
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            AnimatedVisibility(expanded) {
                SettingsGroup(title = null, modifier = Modifier.padding(top = 8.dp)) {
                    uiState.archived.forEachIndexed { index, item ->
                        AccountRow(
                            item, uiState.cards[item.account.id], index, uiState.archived.size,
                            isDefault = false, selected = item.account.id == selectedId,
                        ) {
                            onOpen(item.account.id)
                        }
                    }
                }
            }
        }
    }
}

/** "Total balance" in the main currency; "≈" when it involved a conversion or a skipped account. */
@Composable
private fun TotalPanel(total: TotalBalance) {
    val formatter = rememberCurrencyFormatter()
    Surface(color = BoxTheme.cardColor, shape = MaterialTheme.shapes.extraLargeIncreased, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.accounts_total_balance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                (if (total.approximate) "≈ " else "") + formatter.format(total.amount),
                style = BoxTheme.moneyType.large,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One account; a credit card ([card]) shows what is used (both currencies), with a thin bar of its limit. */
@Composable
private fun AccountRow(
    item: AccountWithBalance,
    card: CardSummary?,
    index: Int,
    count: Int,
    isDefault: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val account = item.account
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else BoxTheme.cardColor
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = container, selectedContainerColor = container),
        leadingContent = { AccountBadge(account) },
        supportingContent = if (isDefault || !account.includeInTotal || account.description.isNotBlank()) {
            { AccountTags(account, isDefault, compact = true) }
        } else null,
        trailingContent = {
            if (card == null) {
                AmountText(amount = item.balance)
            } else {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        AmountText(amount = card.primaryUsed)
                        card.secondUsed?.let { AmountText(amount = it, style = BoxTheme.moneyType.small) }
                    }
                    usageFraction(card)?.let { CardUsageBar(it, Modifier.width(72.dp)) }
                }
            }
        },
    ) {
        Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The account's icon in a circle of its color. */
@Composable
internal fun AccountBadge(account: Account, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(account.color.container()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            accountIcon(account.icon),
            contentDescription = null,
            tint = account.color.onContainer(),
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

/** Short facts about an account: default, archived, left out of the total; [compact] adds the description. */
@Composable
internal fun AccountTags(account: Account, isDefault: Boolean, compact: Boolean = false) {
    val tags = buildList {
        if (isDefault) add(stringResource(R.string.accounts_default_badge))
        if (account.isArchived && !compact) add(stringResource(R.string.accounts_archived_badge))
        if (!account.includeInTotal) add(stringResource(R.string.accounts_not_in_total))
        if (compact && account.description.isNotBlank()) add(account.description)
    }
    if (tags.isEmpty()) return
    if (compact) {
        Text(tags.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountsPreviewContent() {
    BoxWalletTheme(dynamicColor = false) {
        val gtq = Currency.getInstance("GTQ")
        val usd = Currency.getInstance("USD")
        fun account(id: Long, name: String, currency: Currency, color: AccountColor, icon: String, archived: Boolean = false) =
            Account(id = id, name = name, type = AccountType.CHECKING, currency = currency, createdAt = 0, color = color, icon = icon, isArchived = archived)
        val state = AccountsUiState(
            active = listOf(
                AccountWithBalance(account(1, "Principal", gtq, AccountColor.DEFAULT, "AccountBalance"), Money(1_250_00, gtq)),
                AccountWithBalance(account(2, "Ahorro USD", usd, AccountColor.TEAL, "Savings"), Money(300_00, usd)),
            ),
            archived = listOf(AccountWithBalance(account(3, "Viaje", usd, AccountColor.PINK, "Flight", archived = true), Money(0, usd))),
            total = TotalBalance(Money(3_560_00, gtq), approximate = true),
            defaultAccountId = 1,
            isLoading = false,
        )
        SettingsScaffold(title = "Accounts", onNavigateBack = {}) {
            AccountsList(state, selectedId = null, onOpen = {}, onNewAccount = {})
        }
    }
}

@Preview(name = "Light", widthDp = 411, heightDp = 700)
@Composable
private fun AccountsPreview() = AccountsPreviewContent()

@Preview(name = "Dark", widthDp = 411, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountsDarkPreview() = AccountsPreviewContent()
