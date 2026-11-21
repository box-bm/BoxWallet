package com.boxdev.boxwallet.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Whether a transfer can be made at all: it needs two active accounts. Drives the FAB's Transfer item. */
@HiltViewModel
class TransferAvailabilityViewModel @Inject constructor(accountRepository: AccountRepository) : ViewModel() {
    val canTransfer: StateFlow<Boolean> = accountRepository.observeActiveCount()
        .map { it >= 2 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
