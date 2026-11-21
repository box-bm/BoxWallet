package com.boxdev.boxwallet.ui.screens.transfer

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.TransferRepository
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.TransferDraft
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.domain.money.minorDigits
import com.boxdev.boxwallet.domain.money.parseMajor
import com.boxdev.boxwallet.ui.screens.transaction.FormRateCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.boxdev.boxwallet.domain.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.Currency
import javax.inject.Inject

private val DECIMAL = Regex("^\\d*\\.?\\d*$")

/**
 * Create/edit form for a transfer between two accounts. Route arguments: `id` (either leg's
 * transaction id) edits a stored transfer; otherwise `from`, `to` and `amount` prefill a new one, and
 * `receive` (destination major units, e.g. a card statement) fills the amount so at least that arrives.
 * The rate shown is a preview: [TransferRepository] resolves the real one when saving, and only a
 * rate the user typed (directly or through the received amount) is sent along.
 */
@HiltViewModel
class TransferFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transferRepository: TransferRepository,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferences,
    transactionAmounts: TransactionAmounts,
    private val analyticsHelper: AnalyticsHelper,
) : ViewModel() {

    private val _formState = MutableStateFlow(TransferFormState(isLoading = true))
    val formState: StateFlow<TransferFormState> = _formState.asStateFlow()

    /** The form as it stood when loading finished; null while loading, so no edit can be detected yet. */
    private val initial = MutableStateFlow<TransferFormState?>(null)

    /** True once the user has edited a field since loading finished. */
    val hasUnsavedChanges: StateFlow<Boolean> = combine(_formState, initial) { state, base ->
        base != null && state.differsFrom(base)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Every account with its balance, archived included; [pickable] narrows it for the pickers. */
    private var allAccounts: List<AccountWithBalance> = emptyList()

    /** Accounts the edited transfer already uses, kept selectable (each on its own side) even if archived since. */
    private var kept: Set<Long> = emptySet()

    private fun pickable() = allAccounts.filter { !it.account.isArchived || it.account.id in kept }

    private var loadJob: Job? = null

    private var rateJob: Job? = null

    /**
     * A `receive` argument still waiting for its rate: applied on the first automatic rate, until the user types
     * an amount or picks another destination.
     */
    private var pendingReceive: String? = null

    /** Rates resolved per currency pair and day, so typing an amount doesn't resolve (or download) again. */
    private val rateCache = FormRateCache(transactionAmounts, viewModelScope)

    init {
        viewModelScope.launch {
            accountRepository.observeAccountsWithBalance().collect { all ->
                allAccounts = all
                _formState.update { it.copy(accounts = pickable()) }
            }
        }
        val id = savedStateHandle.get<Long>("id")?.takeIf { it != UNSET }
        if (id != null) {
            open(id)
        } else {
            startNew(
                from = savedStateHandle.get<Long>("from")?.takeIf { it != UNSET },
                to = savedStateHandle.get<Long>("to")?.takeIf { it != UNSET },
                amount = savedStateHandle.get<String>("amount")?.takeIf { DECIMAL.matches(it) }.orEmpty(),
                receive = savedStateHandle.get<String>("receive")?.takeIf { DECIMAL.matches(it) },
            )
        }
    }

    /** Shows the transfer that the row [transactionId] is a leg of. */
    fun open(transactionId: Long) {
        loadJob?.cancel()
        initial.value = null
        _formState.value = TransferFormState(isLoading = true, accounts = pickable())
        loadJob = viewModelScope.launch {
            val transfer = transferRepository.getByLegId(transactionId)
            if (transfer == null) {
                _formState.update { it.copy(isLoading = false, error = TransferFormError.NotFound) }
                return@launch
            }
            kept = setOf(transfer.fromAccountId, transfer.toAccountId)
            allAccounts = accountRepository.observeAccountsWithBalance().first()
            val foreign = transfer.sent.currency != transfer.received.currency
            _formState.value = TransferFormState(
                transferId = transfer.id,
                fromAccountId = transfer.fromAccountId,
                toAccountId = transfer.toAccountId,
                amount = transfer.sent.toMajor().toPlainString(),
                date = transfer.date,
                note = transfer.note,
                manualRate = transfer.rate.takeIf { foreign && it.source == RateSource.MANUAL }?.value?.toPlainString(),
                equivalent = transfer.received.toMajor().toPlainString().takeIf { foreign },
                rate = transfer.rate,
                accounts = pickable(),
                keptFromId = transfer.fromAccountId,
                keptToId = transfer.toAccountId,
                isEditing = true,
            )
            initial.value = _formState.value
        }
    }

    /**
     * A new transfer: from [from] (else the default account) to [to] (else the first other active account).
     * Without [from], a default that is [to] or another currency of the same card gives way to the first other
     * active account, so paying a card never starts as a transfer out of it. [receive] replaces [amount] when
     * given (see [amountToReceive]); without a rate yet it waits in [pendingReceive].
     */
    private fun startNew(from: Long?, to: Long?, amount: String, receive: String?) {
        loadJob = viewModelScope.launch {
            allAccounts = accountRepository.observeAccountsWithBalance().first()
            val active = pickable().map { it.account.id }
            val family = to?.takeIf { it in active }?.let(::cardFamilyOf).orEmpty()
            val fallback = accountRepository.defaultAccount().id
            val source = from?.takeIf { it in active }
                ?: fallback.takeIf { it !in family }
                ?: active.firstOrNull { it !in family }
                ?: fallback
            val destination = to?.takeIf { it in active && it != source } ?: active.firstOrNull { it != source }
            val start = TransferFormState(fromAccountId = source, toAccountId = destination, amount = amount, accounts = pickable())
            val prefilled = receive?.let { amountToReceive(start, it) }
            pendingReceive = receive.takeIf { prefilled == null }
            _formState.value = prefilled?.let { start.copy(amount = it) } ?: start
            initial.value = _formState.value
            refreshRate()
        }
    }

    /** [id] with its card's other currency: the card and its second-currency child, or just [id]. */
    private fun cardFamilyOf(id: Long): Set<Long> {
        val account = allAccounts.firstOrNull { it.account.id == id }?.account ?: return setOf(id)
        val root = account.parentAccountId ?: account.id
        return allAccounts.map { it.account }.filter { it.id == root || it.parentAccountId == root }.mapTo(HashSet()) { it.id } + id
    }

    /**
     * The sent amount for [receive] to arrive: [receive] itself in the same currency, otherwise from the
     * automatic rate (see [sentToReceive]). Null without a rate.
     */
    private suspend fun amountToReceive(state: TransferFormState, receive: String): String? {
        val from = state.fromCurrency ?: return null
        val to = state.toCurrency ?: return null
        val target = parseMajor(receive, to) ?: return null
        if (from == to) return target.toMajor().toPlainString()
        val main = userPreferences.mainCurrency.first()
        val result = rateCache.rates(FormRateCache.Key(from, to, main, state.date.toLocalDate()))
        if (result !is AmountResult.Ready) return null
        return sentToReceive(target, from, result.accountRate.value)
    }

    /**
     * The smallest amount in [from] that converts (as the transfer converts it) to at least [target] at [rate]:
     * the quotient rounded down, then raised one minor unit at a time while it still falls short.
     */
    private fun sentToReceive(target: Money, from: Currency, rate: BigDecimal): String {
        var sent = Money.of(target.toMajor().divide(rate, from.minorDigits(), RoundingMode.DOWN), from)
        while (sent.convert(target.currency, rate).minor < target.minor) sent = Money(sent.minor + 1, from)
        return sent.toMajor().toPlainString()
    }

    fun updateAmount(value: String) {
        if (value.isEmpty() || DECIMAL.matches(value)) {
            pendingReceive = null
            _formState.update { s ->
                val updated = s.copy(amount = value, error = null)
                updated.manualRate?.let { updated.withManualRate(it) } ?: updated
            }
            refreshRate()
        }
    }

    /** Picking the account on the other side swaps the two instead. */
    fun setFrom(id: Long) {
        val s = _formState.value
        if (id == s.fromAccountId || s.fromOptions.none { it.account.id == id }) return
        if (id == s.toAccountId) return swap()
        _formState.update { it.copy(fromAccountId = id, manualRate = null, equivalent = null, error = null) }
        refreshRate()
    }

    /** Picking the account on the other side swaps the two instead. */
    fun setTo(id: Long) {
        val s = _formState.value
        if (id == s.toAccountId || s.toOptions.none { it.account.id == id }) return
        if (id == s.fromAccountId) return swap()
        pendingReceive = null
        _formState.update { it.copy(toAccountId = id, manualRate = null, equivalent = null, error = null) }
        refreshRate()
    }

    /**
     * Swaps source and destination; a typed rate belonged to the old direction and is dropped.
     * Refused when it would move an archived account to the side it was never on.
     */
    fun swap() {
        if (!_formState.value.canSwap) return
        pendingReceive = null
        _formState.update {
            it.copy(fromAccountId = it.toAccountId, toAccountId = it.fromAccountId, manualRate = null, equivalent = null, error = null)
        }
        refreshRate()
    }

    fun updateManualRate(value: String) = _formState.update { it.withManualRate(value) }

    fun updateEquivalent(value: String) = _formState.update { it.withEquivalent(value) }

    /** Drops the typed rate and goes back to the rate of the transfer's date. */
    fun useAutomaticRate() {
        _formState.update { it.copy(manualRate = null, equivalent = null) }
        refreshRate()
    }

    fun updateDate(value: LocalDateTime) {
        _formState.update { it.copy(date = value, error = null) }
        refreshRate()
    }

    fun updateTime(hour: Int, minute: Int) = _formState.update { it.copy(date = it.date.withHour(hour).withMinute(minute)) }

    fun updateNote(value: String) = _formState.update { it.copy(note = value) }

    /** Previews the automatic source → destination rate for the transfer's date (unless one was typed). */
    private fun refreshRate() {
        rateJob?.cancel()
        val s = _formState.value
        val from = s.fromCurrency ?: return
        val to = s.toCurrency ?: return
        if (!s.isForeign) {
            _formState.update { it.copy(rate = ExchangeRate.IDENTITY, needsManualRate = false, equivalent = null) }
            return
        }
        if (s.manualRate != null) return
        val day = s.date.toLocalDate()
        rateJob = viewModelScope.launch {
            val main = userPreferences.mainCurrency.first()
            val result = rateCache.rates(FormRateCache.Key(from, to, main, day))
            // A `receive` that had no rate at start is applied with the first one.
            val pending = (result as? AmountResult.Ready)?.let { ready ->
                pendingReceive?.let { parseMajor(it, to) }?.let { sentToReceive(it, from, ready.accountRate.value) }
            }
            _formState.update {
                val stale = it.fromCurrency != from || it.toCurrency != to || it.date.toLocalDate() != day
                if (it.manualRate != null || stale) return@update it
                when (result) {
                    is AmountResult.Ready -> {
                        val amount = pending ?: it.amount
                        it.copy(
                            amount = amount,
                            rate = result.accountRate,
                            needsManualRate = false,
                            equivalent = FormRateCache.equivalent(amount, from, to, result),
                        )
                    }
                    AmountResult.NeedsManualRate -> it.copy(rate = null, needsManualRate = true, equivalent = null)
                }
            }
            if (pending != null && _formState.value.amount == pending) {
                pendingReceive = null
                // The prefill is where the form starts, not an edit.
                initial.update { base -> base?.copy(amount = pending) }
            }
        }
    }

    fun save() {
        val s = _formState.value
        // A second tap while saving (or after) must not write the transfer again.
        if (s.isLoading || s.isSaved) return
        val error = when {
            s.fromAccount == null || s.toAccount == null -> TransferFormError.AccountLoading
            s.isSameAccount -> TransferFormError.SameAccount
            s.money == null -> TransferFormError.InvalidAmount
            !s.isValid -> TransferFormError.NeedsRate
            else -> null
        }
        if (error != null) {
            _formState.update { it.copy(error = error) }
            return
        }
        val draft = TransferDraft(
            fromAccountId = s.fromAccountId!!,
            toAccountId = s.toAccountId!!,
            sent = s.money!!,
            date = s.date,
            note = s.note.trim(),
            manualRate = s.manualRateValue,
        )
        _formState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val transferId = s.transferId
                if (s.isEditing && transferId != null) transferRepository.update(transferId, draft)
                else transferRepository.create(draft)
                analyticsHelper.logEvent(
                    if (s.isEditing) "transfer_updated" else "transfer_created",
                    mapOf("cross_currency" to s.isForeign),
                )
                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: MissingRateException) {
                _formState.update {
                    // Only the source → destination rate can be typed here, and only once: a rate
                    // still missing after one was typed is a main-currency leg, which can't be.
                    val asksForTypedRate = s.manualRateValue == null && e.from == s.fromCurrency && e.to == s.toCurrency
                    if (s.isForeign && asksForTypedRate) {
                        it.copy(isLoading = false, needsManualRate = true, rate = null, error = TransferFormError.NeedsRate)
                    } else {
                        it.copy(isLoading = false, error = TransferFormError.MissingRate(e.from))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "The transfer was rejected", e)
                _formState.update {
                    it.copy(isLoading = false, error = if (s.isSameAccount) TransferFormError.SameAccount else TransferFormError.SaveFailed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Saving the transfer failed", e)
                _formState.update { it.copy(isLoading = false, error = TransferFormError.SaveFailed) }
            }
        }
    }

    /** Deletes both rows of the edited transfer. */
    fun delete() {
        val s = _formState.value
        val transferId = s.transferId ?: return
        if (s.isLoading || s.isSaved) return
        _formState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                transferRepository.delete(transferId)
                analyticsHelper.logEvent("transfer_deleted", emptyMap())
                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Deleting the transfer failed", e)
                _formState.update { it.copy(isLoading = false, error = TransferFormError.DeleteFailed) }
            }
        }
    }

    private companion object {
        const val TAG = "TransferForm"
        /** The NavHost's default for an absent Long argument. */
        const val UNSET = -1L
    }
}
