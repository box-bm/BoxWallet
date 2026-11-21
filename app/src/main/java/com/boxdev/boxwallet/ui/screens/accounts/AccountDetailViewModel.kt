package com.boxdev.boxwallet.ui.screens.accounts

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.AccountRuleError
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.data.repository.TransferRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.CardSummary
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.TransferPair
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.ui.navigation.MovementsFilterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountDetailUiState(
    val account: AccountWithBalance? = null,
    val isDefault: Boolean = false,
    val movements: List<Transaction> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    /** Every account by id, archived included, to name both sides of a transfer. */
    val accountsById: Map<Long, Account> = emptyMap(),
    /** Each transfer's two accounts by transferId. */
    val transferPairs: Map<String, TransferPair> = emptyMap(),
    /** At least two active accounts exist, so a transfer can be made. */
    val canTransfer: Boolean = false,
    /** The account's card summary (both currencies) when it is a credit card. */
    val card: CardSummary? = null,
    /** Where a card payment comes from: the last account that paid it, else the default account. */
    val cardPayer: Long? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    transferRepository: TransferRepository,
    private val movementsFilter: MovementsFilterRequest,
) : ViewModel() {

    /** From the nav route on phones; set with [bind] when the detail lives in a pane. */
    private val accountId = MutableStateFlow(savedStateHandle.get<Long>("accountId"))

    fun bind(id: Long) {
        if (accountId.value == id) return
        accountId.value = id
        _confirmDelete.value = false
    }

    /** [id]'s movements, newest first, with those of its card's second currency when it has one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun movementsOf(id: Long): Flow<List<Transaction>> =
        accountRepository.secondCurrencyOf(id).map { it?.id }.distinctUntilChanged().flatMapLatest { childId ->
            if (childId == null) {
                transactionRepository.observeByAccount(id)
            } else {
                combine(transactionRepository.observeByAccount(id), transactionRepository.observeByAccount(childId)) { own, child ->
                    (own + child).sortedByDescending { it.date }
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AccountDetailUiState> = accountId.filterNotNull().flatMapLatest { id ->
        combine(
            combine(accountRepository.observeAccountsWithBalance(), accountRepository.observeDefaultAccountId(), ::Pair),
            movementsOf(id),
            categoryRepository.getAllCategories(),
            transferRepository.observePairs(),
            accountRepository.observeCardSummaries(),
        ) { (accounts, defaultId), movements, categories, pairs, cards ->
            val card = cards[id]
            AccountDetailUiState(
                account = accounts.firstOrNull { it.account.id == id },
                isDefault = defaultId == id,
                movements = movements,
                categories = categories.associateBy { it.id },
                accountsById = accounts.associate { it.account.id to it.account },
                transferPairs = pairs,
                canTransfer = accounts.count { !it.account.isArchived } >= 2,
                card = card,
                cardPayer = card?.let { accountRepository.lastPayerOf(id, it.second?.account?.id) ?: defaultId },
                isLoading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState())

    /** A one-off message for the snackbar; cleared with [messageShown]. */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    /** True while the delete confirmation should show; only set once the rules allow the delete. */
    private val _confirmDelete = MutableStateFlow(false)
    val confirmDelete: StateFlow<Boolean> = _confirmDelete.asStateFlow()

    /** Set once the account is gone, so the screen can leave it. */
    private val deletedId = MutableStateFlow<Long?>(null)

    /** True only for the bound account, so a pane reused for the next account is never closed by a stale flag. */
    val deleted: StateFlow<Boolean> = combine(deletedId, accountId) { gone, bound -> gone != null && gone == bound }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun messageShown() {
        _message.value = null
    }

    /** Asks the Movements tab to open filtered to this account; the screen then switches tabs. */
    fun seeInMovements() {
        accountId.value?.let(movementsFilter::requestAccount)
    }

    /** Adjusts the bound account, or one currency of a card: [targetId] is the account itself or its second currency. */
    fun adjustBalance(targetId: Long, realBalance: Money) = onAccount { id ->
        val child = accountRepository.secondCurrencyOf(id).first()
        require(targetId == id || targetId == child?.id) { "Account $targetId isn't part of account $id" }
        accountRepository.adjustBalance(targetId, realBalance)
    }

    fun setDefault() = onAccount { accountRepository.setDefault(it) }

    fun setArchived(archived: Boolean) = onAccount { accountRepository.archive(it, archived) }

    fun deleteAdjustment(adjustment: Transaction) {
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(adjustment.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = R.string.account_error_generic
            }
        }
    }

    /** Checks the delete rules first, so the user is never asked to confirm a delete that would fail. */
    fun requestDelete() = onAccount { id ->
        val rule = when {
            accountRepository.observeDefaultAccountId().first() == id -> AccountRuleError.IsDefault
            accountRepository.count() <= 1 -> AccountRuleError.LastAccount
            // A card's second currency goes with it, so its movements count too.
            accountRepository.hasNonAdjustmentMovements(id) ||
                accountRepository.secondCurrencyOf(id).first()?.let { accountRepository.hasNonAdjustmentMovements(it.id) } == true ->
                AccountRuleError.HasMovements
            else -> null
        }
        if (rule != null) throw rule
        _confirmDelete.value = true
    }

    fun dismissDelete() {
        _confirmDelete.value = false
    }

    fun delete() = onAccount { id ->
        _confirmDelete.value = false
        accountRepository.delete(id)
        deletedId.value = id
    }

    /** Runs [action] on the bound account, turning the rules it can break into a message. */
    private fun onAccount(action: suspend (Long) -> Unit) {
        val id = accountId.value ?: return
        viewModelScope.launch {
            try {
                action(id)
            } catch (e: AccountRuleError) {
                _message.value = messageFor(e)
            } catch (_: MissingRateException) {
                _message.value = R.string.account_error_missing_rate
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = R.string.account_error_generic
            }
        }
    }

    @StringRes
    private fun messageFor(error: AccountRuleError): Int = when (error) {
        AccountRuleError.IsDefault -> R.string.account_error_is_default
        AccountRuleError.LastAccount -> R.string.account_error_last
        AccountRuleError.HasMovements -> R.string.account_error_has_movements
        AccountRuleError.SecondCurrencyHasMovements -> R.string.account_error_second_currency_has_movements
        AccountRuleError.RemoveSecondCurrencyFirst -> R.string.account_error_remove_second_currency_first
    }
}
