package com.boxdev.boxwallet.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.InstallmentPlanRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.data.repository.TransferRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.TransferPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.boxdev.boxwallet.ui.navigation.MovementsFilterRequest
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class TransactionTypeFilter { ALL, INCOME, EXPENSE }
enum class SortBy { DATE_ASC, DATE_DESC, AMOUNT_ASC, AMOUNT_DESC }

data class TransactionsFilterState(
    val transactionType: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val sortBy: SortBy = SortBy.DATE_DESC,
    val showOnlyRecurrent: Boolean = false
)

data class TransactionsListUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    /** Every account, archived included, so a filter on an archived one still shows its name. */
    val accounts: List<Account> = emptyList(),
    /** Each transfer's two accounts by transferId, for the "<from> → <to>" title. */
    val transferPairs: Map<String, TransferPair> = emptyMap(),
    /** Each installment plan's installment count by plan id, for a listed installment's "k/N". */
    val installmentCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val filterState: TransactionsFilterState = TransactionsFilterState()
)

@HiltViewModel
class TransactionsListViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    transferRepository: TransferRepository,
    installmentPlanRepository: InstallmentPlanRepository,
    private val filterRequest: MovementsFilterRequest,
) : ViewModel() {

    private val filterState = MutableStateFlow(TransactionsFilterState())

    val uiState: StateFlow<TransactionsListUiState> = combine(
        repository.getAllTransactions(),
        categoryRepository.getAllCategories(),
        accountRepository.observeAll(),
        combine(transferRepository.observePairs(), installmentPlanRepository.observeInstallmentCounts(), ::Pair),
        filterState,
    ) { transactions, categories, accounts, (pairs, counts), filter ->
        TransactionsListUiState(
            transactions = transactions,
            filteredTransactions = applyFilters(transactions, filter, accounts),
            availableCategories = categories,
            accounts = accounts,
            transferPairs = pairs,
            installmentCounts = counts,
            isLoading = false,
            filterState = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsListUiState())

    init {
        // Reports can open this tab filtered to a category within the period it was showing, an account's detail to it.
        viewModelScope.launch {
            filterRequest.filter.filterNotNull().collect { f ->
                filterState.value = TransactionsFilterState(
                    categoryId = f.categoryId,
                    accountId = f.accountId,
                    startDate = f.start,
                    endDate = f.end,
                )
                filterRequest.consume()
            }
        }
    }

    fun updateTypeFilter(type: TransactionTypeFilter) = filterState.update { it.copy(transactionType = type) }

    fun updateCategoryFilter(categoryId: Long?) = filterState.update { it.copy(categoryId = categoryId) }

    fun updateAccountFilter(accountId: Long?) = filterState.update { it.copy(accountId = accountId) }

    fun updateSortBy(sortBy: SortBy) = filterState.update { it.copy(sortBy = sortBy) }

    fun updateRecurrentFilter(showOnlyRecurrent: Boolean) = filterState.update { it.copy(showOnlyRecurrent = showOnlyRecurrent) }

    fun clearFilters() {
        filterState.value = TransactionsFilterState()
    }

    private fun applyFilters(transactions: List<Transaction>, f: TransactionsFilterState, accounts: List<Account>): List<Transaction> {
        // A card's filter covers its second currency too.
        val accountIds = f.accountId?.let { id -> setOf(id) + accounts.filter { it.parentAccountId == id }.map { it.id } }
        val filtered = transactions.filter { tx ->
            val date = tx.date.toLocalDate()
            // Unscoped, a transfer is one row: its OUT leg. Scoped to an account, that account's own leg.
            (f.accountId != null || tx.type != TransactionType.TRANSFER_IN) &&
            when (f.transactionType) {
                TransactionTypeFilter.INCOME -> tx.type == TransactionType.INCOME
                TransactionTypeFilter.EXPENSE -> tx.type == TransactionType.EXPENSE
                TransactionTypeFilter.ALL -> true
            } &&
                (f.categoryId == null || tx.categoryId == f.categoryId) &&
                (accountIds == null || tx.accountId in accountIds) &&
                (f.startDate == null || !date.isBefore(f.startDate)) &&
                (f.endDate == null || !date.isAfter(f.endDate)) &&
                (!f.showOnlyRecurrent || tx.isRecurrent)
        }
        return when (f.sortBy) {
            SortBy.DATE_ASC -> filtered.sortedBy { it.date }
            SortBy.DATE_DESC -> filtered.sortedByDescending { it.date }
            SortBy.AMOUNT_ASC -> filtered.sortedBy { it.amount.minor }
            SortBy.AMOUNT_DESC -> filtered.sortedByDescending { it.amount.minor }
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }
}
