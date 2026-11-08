package com.boxdev.boxwallet.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.minorDigits
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

    /** Records paired with their folded search text, rebuilt only when the data changes, not per keystroke. */
    private class Index(
        val transactions: List<Pair<Transaction, String>>,
        val loans: List<Pair<Loan, String>>,
        val categories: List<Category>,
    )

    private val index = combine(
        transactionRepository.getAllTransactions(),
        loanRepository.getAllLoans(),
        categoryRepository.getAllCategories(),
    ) { transactions, loans, categories ->
        val categoryNames = categories.associate { it.id to it.name }
        Index(
            transactions = transactions.map { t -> t to haystack(t.description, categoryNames[t.categoryId], t.original) },
            loans = loans.map { l -> l to haystack(l.name + " " + l.description, categoryNames[l.categoryId], l.totalAmount) },
            categories = categories,
        )
    }.flowOn(Dispatchers.Default)

    // The DAOs already return movements newest first and loans by start date, newest first.
    val uiState: StateFlow<SearchUiState> = combine(index, query, scope) { idx, q, s ->
        val terms = q.trim().fold().split(WHITESPACE).filter { it.isNotEmpty() }
        if (terms.isEmpty()) {
            return@combine SearchUiState(query = q, scope = s, categories = idx.categories, isLoading = false)
        }
        fun <T> List<Pair<T, String>>.matching() = filter { (_, text) -> terms.all { text.contains(it) } }.map { it.first }
        SearchUiState(
            query = q,
            scope = s,
            transactions = if (s == SearchScope.LOANS) emptyList() else idx.transactions.matching(),
            loans = if (s == SearchScope.MOVEMENTS) emptyList() else idx.loans.matching(),
            categories = idx.categories,
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

    private fun haystack(text: String, categoryName: String?, amount: Money): String =
        amount.currency.minorDigits().let { digits ->
        buildString {
            append(text.fold())
            append(' ')
            categoryName?.let { append(it.fold()) }
            append(' ')
            // Both "1830.35" and "1,830.35" find the same record.
            append(String.format(Locale.US, "%.${digits}f", amount.toMajor()))
            append(' ')
            append(String.format(Locale.US, "%,.${digits}f", amount.toMajor()))
        }
        }

    /** Lowercase without accents, so "cafe" finds "Café" and "credito" finds "Crédito". */
    private fun String.fold(): String =
        Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase(Locale.getDefault())

    private companion object {
        val DIACRITICS = Regex("\\p{Mn}+")
        val WHITESPACE = Regex("\\s+")
    }
}
