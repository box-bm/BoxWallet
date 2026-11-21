package com.boxdev.boxwallet.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.TotalBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AccountsUiState(
    val active: List<AccountWithBalance> = emptyList(),
    val archived: List<AccountWithBalance> = emptyList(),
    val total: TotalBalance? = null,
    val defaultAccountId: Long? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accountRepository: AccountRepository,
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.observeAccountsWithBalance(),
        accountRepository.observeTotalBalance(),
        accountRepository.observeDefaultAccountId(),
    ) { accounts, total, defaultId ->
        // A card's second-currency sub-account shows inside its card, never as an account of its own.
        val (archived, active) = accounts.filter { it.account.parentAccountId == null }
            .partition { it.account.isArchived }
        AccountsUiState(active = active, archived = archived, total = total, defaultAccountId = defaultId, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())
}
