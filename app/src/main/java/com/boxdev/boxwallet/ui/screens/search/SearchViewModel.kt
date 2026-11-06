package com.boxdev.boxwallet.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

/** Which kinds of records the global search returns. */
enum class SearchScope { ALL, MOVEMENTS, LOANS }

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val transactions: List<Transaction> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val hasResults: Boolean get() = transactions.isNotEmpty() || loans.isNotEmpty()
}

/**
 * One search over everything the user records: movements and loans, matched on description,
 * name, category and amount. Every word in the query has to match somewhere in the record.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    loanRepository: LoanRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val scope = MutableStateFlow(SearchScope.ALL)

    val uiState: StateFlow<SearchUiState> = combine(
        transactionRepository.getAllTransactions(),
        loanRepository.getAllLoans(),
        categoryRepository.getAllCategories(),
        query,
        scope,
    ) { transactions, loans, categories, q, s ->
        val terms = q.trim().fold().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) {
            return@combine SearchUiState(query = q, scope = s, categories = categories, isLoading = false)
        }
        val categoryNames = categories.associate { it.id to it.name }
        val matchedTransactions = if (s == SearchScope.LOANS) emptyList() else transactions
            .filter { t -> terms.matchAll(t.description, categoryNames[t.categoryId], t.amount) }
            .sortedByDescending { it.date }
        val matchedLoans = if (s == SearchScope.MOVEMENTS) emptyList() else loans
            .filter { l -> terms.matchAll(l.name + " " + l.description, categoryNames[l.categoryId], l.totalAmount) }
            .sortedByDescending { it.startDate }
        SearchUiState(
            query = q,
            scope = s,
            transactions = matchedTransactions,
            loans = matchedLoans,
            categories = categories,
            isLoading = false,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateScope(value: SearchScope) {
        scope.value = value
    }

    private fun List<String>.matchAll(text: String, categoryName: String?, amount: Double): Boolean {
        val haystack = buildString {
            append(text.fold())
            append(' ')
            categoryName?.let { append(it.fold()) }
            append(' ')
            // Both "1830.35" and "1,830.35" find the same record.
            append(String.format(Locale.US, "%.2f", amount))
            append(' ')
            append(String.format(Locale.US, "%,.2f", amount))
        }
        return all { haystack.contains(it) }
    }

    /** Lowercase without accents, so "cafe" finds "Café" and "credito" finds "Crédito". */
    private fun String.fold(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase(Locale.getDefault())

    private companion object {
        val DIACRITICS = Regex("\\p{Mn}+")
    }
}
