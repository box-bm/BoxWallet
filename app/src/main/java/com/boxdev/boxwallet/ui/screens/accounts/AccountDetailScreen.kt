@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.accounts

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.isAdjustment
import com.boxdev.boxwallet.data.local.entity.isTransfer
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.CardBalance
import com.boxdev.boxwallet.domain.model.CardSummary
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.ui.components.AdjustmentDeleteDialog
import com.boxdev.boxwallet.ui.components.transferTitle
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.BackButton
import com.boxdev.boxwallet.ui.components.CardBlock
import com.boxdev.boxwallet.ui.components.payRoute
import com.boxdev.boxwallet.ui.components.ConfirmDeleteDialog
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.SectionHeader
import com.boxdev.boxwallet.ui.components.TransactionItem
import com.boxdev.boxwallet.ui.screens.settings.SettingsGroup
import com.boxdev.boxwallet.ui.screens.settings.SettingsRow
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import java.util.Currency

/**
 * One account: balance, its actions and its movements. A null [onNavigateBack] embeds it in the
 * Accounts detail pane, bound to [accountId]; on its own route the view model reads the argument.
 * [onDeleted] runs once the account is gone.
 */
@Composable
fun AccountDetailScreen(
    onNavigateBack: (() -> Unit)?,
    onEditAccount: (Long) -> Unit,
    onEditTransaction: (Long) -> Unit,
    /** Switches to the Movements tab, which the view model has already asked to filter by this account. */
    onSeeInMovements: () -> Unit,
    onDeleted: () -> Unit,
    onOpenTransfer: (Long) -> Unit = {},
    /** Opens a new transfer from the given account. */
    onNewTransfer: (Long) -> Unit = {},
    /** Opens a route; a card's Pay opens its prefilled transfer ([payRoute]). */
    onNavigate: (String) -> Unit = {},
    accountId: Long? = null,
    viewModel: AccountDetailViewModel = hiltViewModel(key = accountId?.let { "account_detail_$it" }),
) {
    LaunchedEffect(accountId) { accountId?.let(viewModel::bind) }
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val confirmDelete by viewModel.confirmDelete.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    /** The account whose balance is being adjusted: this one, or one currency of a card. */
    var adjusting by remember { mutableStateOf<AccountWithBalance?>(null) }
    var pendingAdjustment by remember { mutableStateOf<Transaction?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(context.getString(it))
            viewModel.messageShown()
        }
    }
    LaunchedEffect(deleted) { if (deleted) currentOnDeleted() }

    val item = uiState.account
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = {
                    Text(item?.account?.name ?: stringResource(R.string.accounts_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                subtitle = item?.account?.description?.takeIf { it.isNotBlank() }?.let { d ->
                    { Text(d, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                navigationIcon = { if (onNavigateBack != null) BackButton(onNavigateBack) },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            item == null -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.account_not_found),
                modifier = Modifier.padding(padding),
            )
            else -> AccountDetailContent(
                item = item,
                isDefault = uiState.isDefault,
                movements = uiState.movements,
                categories = uiState.categories,
                card = uiState.card,
                transferTitleOf = { tx -> tx.transferId?.let { transferTitle(uiState.transferPairs[it], uiState.accountsById) } },
                onAdjust = { adjusting = item },
                onAdjustCard = { adjusting = AccountWithBalance(it.account, it.balance) },
                onPay = { balance -> uiState.card?.let { onNavigate(payRoute(it.card, balance, uiState.cardPayer)) } },
                onEdit = { onEditAccount(item.account.id) },
                // An archived account can't take part in a new transfer; one account alone has nowhere to send.
                onTransfer = if (uiState.canTransfer && !item.account.isArchived) {
                    { onNewTransfer(item.account.id) }
                } else null,
                onSetDefault = viewModel::setDefault,
                onArchive = { viewModel.setArchived(!item.account.isArchived) },
                onDelete = viewModel::requestDelete,
                onOpenMovement = { tx ->
                    when {
                        tx.type.isAdjustment -> pendingAdjustment = tx
                        tx.type.isTransfer -> onOpenTransfer(tx.id)
                        else -> onEditTransaction(tx.id)
                    }
                },
                onSeeInMovements = { viewModel.seeInMovements(); onSeeInMovements() },
                modifier = Modifier.padding(padding),
            )
        }
    }

    adjusting?.let { target ->
        AdjustBalanceSheet(
            current = target.balance,
            onSave = { viewModel.adjustBalance(target.account.id, it); adjusting = null },
            onDismiss = { adjusting = null },
        )
    }
    pendingAdjustment?.let { adjustment ->
        AdjustmentDeleteDialog(
            adjustment = adjustment,
            onDelete = { viewModel.deleteAdjustment(adjustment); pendingAdjustment = null },
            onDismiss = { pendingAdjustment = null },
        )
    }
    if (confirmDelete && item != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.account_delete_title, item.account.name),
            message = stringResource(R.string.account_delete_message),
            onConfirm = viewModel::delete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun AccountDetailContent(
    item: AccountWithBalance,
    isDefault: Boolean,
    movements: List<Transaction>,
    categories: Map<Long, com.boxdev.boxwallet.domain.model.Category>,
    card: CardSummary?,
    transferTitleOf: (Transaction) -> String?,
    onAdjust: () -> Unit,
    onAdjustCard: (CardBalance) -> Unit,
    onPay: (CardBalance) -> Unit,
    onEdit: () -> Unit,
    onTransfer: (() -> Unit)?,
    onSetDefault: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onOpenMovement: (Transaction) -> Unit,
    onSeeInMovements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = item.account
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "hero") {
            if (card != null) {
                CardBlock(
                    summary = card,
                    onPay = onPay,
                    onAdjust = onAdjustCard,
                    tags = { AccountTags(account, isDefault) },
                    actions = { EditAndTransfer(onEdit, onTransfer) },
                )
            } else {
                BalanceHero(item, isDefault, onAdjust, onEdit, onTransfer)
            }
        }
        item(key = "actions") {
            // Only what the account can do right now: the default can't become the default again,
            // an archived account must come back before it can be the default.
            val rows = buildList {
                if (!isDefault && !account.isArchived) add(ActionRow.Default)
                // The default can't be archived either; an archived account can always come back.
                if (!isDefault || account.isArchived) add(ActionRow.Archive)
                add(ActionRow.Delete)
            }
            SettingsGroup(title = null, modifier = Modifier.padding(top = 16.dp)) {
                rows.forEachIndexed { index, row ->
                    when (row) {
                        ActionRow.Default -> SettingsRow(
                            index, rows.size, Icons.Outlined.Star,
                            stringResource(R.string.account_set_default),
                            stringResource(R.string.account_set_default_help), onSetDefault,
                        )
                        ActionRow.Archive -> SettingsRow(
                            index, rows.size,
                            if (account.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            stringResource(if (account.isArchived) R.string.account_unarchive else R.string.account_archive),
                            stringResource(if (account.isArchived) R.string.account_unarchive_help else R.string.account_archive_help),
                            onArchive,
                        )
                        ActionRow.Delete -> SettingsRow(
                            index = index, count = rows.size,
                            icon = Icons.Outlined.DeleteOutline,
                            iconContainer = MaterialTheme.colorScheme.errorContainer,
                            iconContent = MaterialTheme.colorScheme.onErrorContainer,
                            title = stringResource(R.string.account_delete),
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = onDelete,
                            trailing = null,
                        )
                    }
                }
            }
        }
        item(key = "movements_header") {
            SectionHeader(
                title = stringResource(R.string.account_movements),
                modifier = Modifier.padding(top = 20.dp),
                actionLabel = stringResource(R.string.account_see_in_movements).takeIf { movements.isNotEmpty() },
                onAction = onSeeInMovements,
            )
        }
        if (movements.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.account_no_movements),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
        itemsIndexed(movements, key = { _, t -> t.id }) { index, tx ->
            TransactionItem(
                transaction = tx,
                category = tx.categoryId?.let(categories::get),
                index = index,
                count = movements.size,
                transferTitle = transferTitleOf(tx),
                // Only this account's movements: a transfer reads as money in or out of it.
                signedTransfer = true,
                onClick = { onOpenMovement(tx) },
            )
        }
    }
}

private enum class ActionRow { Default, Archive, Delete }

/** The account's balance with its everyday actions; [onTransfer] is null when no transfer can be made. */
@Composable
private fun BalanceHero(
    item: AccountWithBalance,
    isDefault: Boolean,
    onAdjust: () -> Unit,
    onEdit: () -> Unit,
    onTransfer: (() -> Unit)?,
) {
    Surface(
        color = BoxTheme.cardColor,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AccountBadge(item.account, size = 56)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.account_balance_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(amount = item.balance, style = BoxTheme.moneyType.large, autoSize = true)
                }
            }
            AccountTags(item.account, isDefault)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAdjust, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.account_adjust_balance), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = onEdit, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.account_edit), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TransferButton(onTransfer)
            }
        }
    }
}

/** A card's Edit and Transfer, below its per-currency Pay and Adjust. */
@Composable
private fun EditAndTransfer(onEdit: () -> Unit, onTransfer: (() -> Unit)?) {
    OutlinedButton(onClick = onEdit, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.account_edit), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    TransferButton(onTransfer)
}

@Composable
private fun TransferButton(onTransfer: (() -> Unit)?) {
    if (onTransfer == null) return
    // Its own row: three buttons side by side would truncate their labels on a phone.
    FilledTonalButton(onClick = onTransfer, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.account_action_transfer), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AccountDetailPreviewContent() {
    BoxWalletTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            val usd = Currency.getInstance("USD")
            val account = Account(
                id = 1, name = "Banco", type = AccountType.CHECKING, currency = usd, createdAt = 0,
                description = "Ahorros", icon = "Savings", color = AccountColor.TEAL,
            )
            AccountDetailContent(
                item = AccountWithBalance(account, Money(125000, usd)),
                isDefault = false,
                movements = emptyList(),
                categories = emptyMap(),
                card = null,
                transferTitleOf = { null },
                onAdjust = {}, onAdjustCard = {}, onPay = {}, onEdit = {}, onTransfer = {}, onSetDefault = {}, onArchive = {}, onDelete = {}, onOpenMovement = {}, onSeeInMovements = {},
            )
        }
    }
}

@Preview(name = "Light", widthDp = 411, heightDp = 700)
@Composable
private fun AccountDetailPreview() = AccountDetailPreviewContent()

@Preview(name = "Dark", widthDp = 411, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountDetailDarkPreview() = AccountDetailPreviewContent()
