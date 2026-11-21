package com.boxdev.boxwallet.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.FinancialSummary
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.TotalBalance
import com.boxdev.boxwallet.domain.model.Transaction
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
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val loanRepository: LoanRepository,
    private val categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val balances = combine(
        accountRepository.observeTotalBalance(),
        accountRepository.observeAccountsWithBalance(),
    ) { total, accounts -> total to accounts.filter { !it.account.isArchived } }

    val uiState: StateFlow<HomeUiState> = combine(
        transactionRepository.getFinancialSummary(),
        transactionRepository.getLatestTransactions(5),
        loanRepository.getAllLoans(),
        categoryRepository.getAllCategories(),
        balances,
    ) { summary, latest, loans, categories, (total, accounts) ->
        HomeUiState(
            financialSummary = summary,
            latestTransactions = latest,
            activeLoans = loans.filter { !it.isComplete },
            categories = categories,
            totalBalance = total,
            accounts = accounts,
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
