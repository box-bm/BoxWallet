package com.boxdev.boxwallet.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.InstallmentPlanRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.data.repository.TransferRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.CardSummary
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.FinancialSummary
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.TotalBalance
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.TransferPair
import com.boxdev.boxwallet.domain.money.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Currency
import javax.inject.Inject

data class HomeUiState(
    val financialSummary: FinancialSummary = FinancialSummary(),
    val latestTransactions: List<Transaction> = emptyList(),
    val activeLoans: List<Loan> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Total of the accounts counted in the total, in the main currency; replaces the summary's balance. */
    val totalBalance: TotalBalance = TotalBalance(Money(0, Currency.getInstance("USD")), approximate = false),
    /** Accounts not archived, with their balance; the row only shows with more than one. */
    val accounts: List<AccountWithBalance> = emptyList(),
    /** Card summaries by card id, for the card tiles' used amount and limit bar. */
    val cards: Map<Long, CardSummary> = emptyMap(),
    /** Every account by id, archived included, to name both sides of a transfer. */
    val accountsById: Map<Long, Account> = emptyMap(),
    /** Each transfer's two accounts by transferId. */
    val transferPairs: Map<String, TransferPair> = emptyMap(),
    /** Each installment plan's installment count by plan id, for a listed installment's "k/N". */
    val installmentCounts: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val loanRepository: LoanRepository,
    private val categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    transferRepository: TransferRepository,
    installmentPlanRepository: InstallmentPlanRepository,
) : ViewModel() {

    private class Balances(
        val total: TotalBalance,
        val active: List<AccountWithBalance>,
        val byId: Map<Long, Account>,
        val pairs: Map<String, TransferPair>,
        val cards: Map<Long, CardSummary>,
        val installmentCounts: Map<Long, Int>,
    )

    private val balances = combine(
        accountRepository.observeTotalBalance(),
        accountRepository.observeAccountsWithBalance(),
        transferRepository.observePairs(),
        accountRepository.observeCardSummaries(),
        installmentPlanRepository.observeInstallmentCounts(),
    ) { total, accounts, pairs, cards, counts ->
        Balances(
            total = total,
            // A card's second currency belongs to its card, never a row of its own.
            active = accounts.filter { !it.account.isArchived && it.account.parentAccountId == null },
            byId = accounts.associate { it.account.id to it.account },
            pairs = pairs,
            cards = cards,
            installmentCounts = counts,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        transactionRepository.getFinancialSummary(),
        transactionRepository.getLatestTransactions(5),
        loanRepository.getAllLoans(),
        categoryRepository.getAllCategories(),
        balances,
    ) { summary, latest, loans, categories, balances ->
        HomeUiState(
            financialSummary = summary,
            latestTransactions = latest,
            activeLoans = loans.filter { !it.isSettled },
            categories = categories,
            totalBalance = balances.total,
            accounts = balances.active,
            cards = balances.cards,
            accountsById = balances.byId,
            transferPairs = balances.pairs,
            installmentCounts = balances.installmentCounts,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { transactionRepository.deleteTransaction(id) }
    }
}
