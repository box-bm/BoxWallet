package com.boxdev.boxwallet.ui.screens.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.CurrencyUsage
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.AccountRepository
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.InstallmentPlanRepository
import com.boxdev.boxwallet.data.repository.NewInstallmentPlan
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.model.ReminderDuration
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.firstClosingOnOrAfter
import com.boxdev.boxwallet.domain.model.splitEvenly
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.domain.money.parseMajor
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency
import javax.inject.Inject

/** Why the form can't save, shown under the fields so a failed save never leaves the form silently open. */
sealed interface TransactionFormError {
    data object InvalidAmount : TransactionFormError
    data object NeedsRate : TransactionFormError
    data object AccountLoading : TransactionFormError
    data object NotFound : TransactionFormError
    /** No rate is known to convert [from] (e.g. the account → main leg) and nothing can be typed for it. */
    data class MissingRate(val from: Currency) : TransactionFormError
    data object SaveFailed : TransactionFormError
    data object DeleteFailed : TransactionFormError
}

/** What an installment purchase looks like before saving: the first part and the closing it lands on. */
data class InstallmentPreview(val perClosing: Money, val firstClosing: LocalDate)

const val MIN_INSTALLMENTS = 2
const val MAX_INSTALLMENTS = 60

data class TransactionFormState(
    val id: Long = 0,
    /** Typed in major units of [currency]. */
    val amount: String = "",
    /** The movement's currency; starts as the account's once [accountCurrency] is known. */
    val currency: Currency = Currency.getInstance("USD"),
    /** Currency of the account the movement goes to; null until loaded, and saving waits for it. */
    val accountCurrency: Currency? = null,
    /** The account the movement goes to (after routing [pickedAccountId]); null until loaded. */
    val accountId: Long? = null,
    /**
     * The account the user picked (or the default / stored one); [accountId] is derived from it by [routed]
     * whenever the account or the currency changes.
     */
    val pickedAccountId: Long? = null,
    /** Accounts the movement can go to (not archived); the account chip shows only with more than one. */
    val accounts: List<Account> = emptyList(),
    /** True once the user picked [currency] explicitly, so choosing another account no longer moves it. */
    val currencyTouched: Boolean = false,
    /** Original → account rate typed by the user (overrides the automatic one), or null. */
    val manualRate: String? = null,
    /** The amount in [accountCurrency], in major units; typing it derives [manualRate]. */
    val equivalent: String? = null,
    /** The rate currently applied original → account, for display; null when none is known. */
    val rate: ExchangeRate? = null,
    /** No rate exists for this currency and date: saving requires [manualRate] or [equivalent]. */
    val needsManualRate: Boolean = false,
    val description: String = "",
    val categoryId: Long? = null,
    val date: LocalDateTime = LocalDateTime.now(),
    val type: TransactionType = TransactionType.EXPENSE,
    val isCleared: Boolean = false,
    val hasReminder: Boolean = false,
    val reminderDuration: ReminderDuration? = null,
    val isRecurrent: Boolean = false,
    val recurrenceFrequency: RecurrenceFrequency? = null,
    val recurrenceInterval: String = "1",
    val inInstallments: Boolean = false,
    val installmentCount: String = "3",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: TransactionFormError? = null,
    val wasCleared: Boolean = false
) {
    val money: Money? get() = parseMajor(amount, currency)

    val isForeign: Boolean get() = accountCurrency != null && currency != accountCurrency

    /** The typed rate when it is a positive number and the movement is foreign to its account. */
    val manualRateValue: BigDecimal?
        get() = manualRate?.takeIf { isForeign }?.trim()?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    /** Closing day of the routed account (a child uses its parent's card), or null when it is not a card with one. */
    private val closingDay: Int?
        get() {
            val account = accounts.firstOrNull { it.id == accountId } ?: return null
            val card = if (account.parentAccountId != null) accounts.firstOrNull { it.id == account.parentAccountId } else account
            return card?.takeIf { it.type == AccountType.CREDIT_CARD }?.statementClosingDay
        }

    /** Only a new, non-recurrent expense on a card with a closing day can be bought in installments. */
    val canUseInstallments: Boolean
        get() = !isEditing && !isRecurrent && type == TransactionType.EXPENSE && closingDay != null

    /** The switch is on and still allowed for the current account and type. */
    val installmentsActive: Boolean get() = inInstallments && canUseInstallments

    private val installments: Int? get() = installmentCount.toIntOrNull()?.takeIf { it in MIN_INSTALLMENTS..MAX_INSTALLMENTS }

    val installmentPreview: InstallmentPreview?
        get() {
            if (!installmentsActive) return null
            val total = money ?: return null
            val count = installments ?: return null
            val day = closingDay ?: return null
            return InstallmentPreview(
                perClosing = Money(splitEvenly(total.minor, count).first(), total.currency),
                firstClosing = firstClosingOnOrAfter(day, date.toLocalDate()),
            )
        }

    val isValid: Boolean
        get() = money != null &&
                (!installmentsActive || installments != null) &&
                (!isRecurrent || recurrenceFrequency != null) &&
                (!isRecurrent || recurrenceFrequency != RecurrenceFrequency.CUSTOM_MONTHS || (recurrenceInterval.toIntOrNull() ?: 0) > 0)

    /** Valid, the account currency is known, and a missing automatic rate has been replaced by a manual one. */
    val canSave: Boolean
        get() = isValid && accountCurrency != null && (!needsManualRate || manualRateValue != null)

    /**
     * The state with [accountId] derived from [pickedAccountId] and [currency] (§3.4, see [routedAccount]).
     * A typed rate belonged to the old account's currency and is dropped when the account changes.
     */
    fun routed(): TransactionFormState {
        val picked = accounts.firstOrNull { it.id == (pickedAccountId ?: accountId) } ?: return this
        val target = routedAccount(picked, currency, accounts)
        if (target.id == accountId) return this
        return copy(accountId = target.id, accountCurrency = target.currency, manualRate = null, equivalent = null)
    }

    /** Sets the manual rate and recomputes [equivalent] from it. */
    fun withManualRate(value: String): TransactionFormState = copy(
        manualRate = value,
        equivalent = ManualConversion.equivalentAt(value, money, accountCurrency) ?: equivalent,
        error = null,
    )

    /** Sets the equivalent in the account currency and derives the manual rate = equivalent / original. */
    fun withEquivalent(value: String): TransactionFormState {
        val target = accountCurrency ?: return copy(equivalent = value)
        return copy(equivalent = value, manualRate = ManualConversion.rateFor(value, money, target) ?: manualRate, error = null)
    }
}

/**
 * The account a movement in [currency] lands on when [selected] is picked: a card with a second currency
 * sends a movement in that currency to it. An explicit pick of the second currency (or any other account)
 * stays as picked, whatever the currency.
 */
fun routedAccount(selected: Account, currency: Currency, accounts: List<Account>): Account {
    if (selected.parentAccountId != null) return selected
    val child = accounts.firstOrNull { it.parentAccountId == selected.id } ?: return selected
    return if (currency == child.currency) child else selected
}

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferences,
    private val transactionAmounts: TransactionAmounts,
    private val analyticsHelper: AnalyticsHelper,
    private val installmentPlanRepository: InstallmentPlanRepository,
) : ViewModel() {

    /** The movement being edited, so fields the form doesn't show (account, recurrence parent) survive the save. */
    private var editing: Transaction? = null

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    /** The form as it stood when loading finished; null while loading, so no edit can be detected yet. */
    private val initial = MutableStateFlow<TransactionFormState?>(null)

    /** True once the user has edited a field since loading finished. */
    val hasUnsavedChanges: StateFlow<Boolean> = combine(_formState, initial) { state, base ->
        base != null && state.differsFrom(base)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentCurrencies: StateFlow<List<CurrencyUsage>> = transactionRepository.recentCurrencies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Latest non-archived accounts, carried into every fresh state so a reload never hides the chip. */
    private var accounts: List<Account> = emptyList()

    init {
        viewModelScope.launch {
            accountRepository.observeAll().collect { all ->
                accounts = all.filter { !it.isArchived }
                _formState.update { it.copy(accounts = accounts) }
            }
        }
        loadTransaction(savedStateHandle.get<Long>("id"))
    }

    /**
     * Shows another movement in this same form: the editor pane beside the Movements list reuses one
     * view model as the selection changes. Opening the movement already on screen keeps its edits.
     */
    fun open(transactionId: Long) {
        val current = _formState.value
        if (current.id == transactionId && !current.isSaved && (current.isLoading || initial.value != null)) return
        // Reset at once, so the last movement's saved flag can't close the form for this one.
        _formState.value = TransactionFormState(id = transactionId, isLoading = true, accounts = accounts)
        loadTransaction(transactionId)
    }

    private var loadJob: Job? = null

    private fun loadTransaction(transactionId: Long?) {
        // A newer load wins: an earlier one still in flight must not write over it.
        loadJob?.cancel()
        editing = null
        initial.value = null
        if (transactionId == null || transactionId == -1L) {
            // Loading until the account is known, so a quick save can't store a placeholder currency.
            _formState.value = TransactionFormState(isLoading = true, accounts = accounts)
            loadJob = viewModelScope.launch {
                val account = accountRepository.defaultAccount()
                _formState.update {
                    it.copy(
                        currency = account.currency,
                        accountCurrency = account.currency,
                        accountId = account.id,
                        pickedAccountId = account.id,
                        isLoading = false,
                    )
                }
                initial.value = _formState.value
            }
            return
        }
        loadJob = viewModelScope.launch {
            _formState.update { it.copy(isLoading = true) }
            val t = transactionRepository.getTransactionById(transactionId)
            editing = t
            if (t != null) {
                _formState.value = TransactionFormState(
                    id = t.id,
                    amount = t.original.toMajor().toPlainString(),
                    currency = t.original.currency,
                    accountCurrency = t.accountAmount.currency,
                    accountId = t.accountId,
                    // Routing only runs once the account or currency changes, so an untouched edit stays put.
                    pickedAccountId = t.accountId,
                    accounts = accounts,
                    // The stored currency was chosen for this movement: moving it to another account keeps it.
                    currencyTouched = true,
                    manualRate = t.accountRate.takeIf { it.source == RateSource.MANUAL }?.value?.toPlainString(),
                    equivalent = t.accountAmount.toMajor().toPlainString()
                        .takeIf { t.original.currency != t.accountAmount.currency },
                    rate = t.accountRate,
                    description = t.description,
                    categoryId = t.categoryId,
                    date = t.date,
                    type = t.type,
                    isCleared = t.isCleared,
                    hasReminder = t.hasReminder,
                    reminderDuration = ReminderDuration.fromSeconds(t.reminderSecondsBefore),
                    isRecurrent = t.isRecurrent,
                    recurrenceFrequency = t.recurrenceFrequency,
                    recurrenceInterval = t.recurrenceInterval?.toString() ?: "1",
                    isEditing = true,
                    isLoading = false,
                    wasCleared = t.isCleared,
                )
                initial.value = _formState.value
            } else {
                _formState.update { it.copy(isLoading = false, error = TransactionFormError.NotFound) }
            }
        }
    }

    fun updateAmount(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _formState.update { s ->
                val updated = s.copy(amount = value, error = null)
                updated.manualRate?.let { updated.withManualRate(it) } ?: updated
            }
            refreshRate()
        }
    }

    /** Changes the movement's currency; any typed rate belonged to the old one and is dropped. */
    fun updateCurrency(value: Currency) {
        _formState.update {
            it.copy(currency = value, currencyTouched = true, manualRate = null, equivalent = null, error = null).routed()
        }
        refreshRate()
    }

    /**
     * Moves the movement to another account. Its currency follows the account's unless the user
     * picked one; a typed rate belonged to the old account's currency and is dropped.
     */
    fun setAccount(id: Long) {
        val account = _formState.value.accounts.firstOrNull { it.id == id } ?: return
        _formState.update {
            // Already routed there: the pick becomes explicit (a picked second currency stays put).
            if (it.accountId == id) return@update it.copy(pickedAccountId = id)
            it.copy(
                accountId = account.id,
                pickedAccountId = account.id,
                accountCurrency = account.currency,
                currency = if (it.currencyTouched) it.currency else account.currency,
                manualRate = null,
                equivalent = null,
                error = null,
            ).routed()
        }
        refreshRate()
    }

    fun updateManualRate(value: String) = _formState.update { it.withManualRate(value) }

    fun updateEquivalent(value: String) = _formState.update { it.withEquivalent(value) }

    /** Drops the typed rate and goes back to the rate of the movement's date. */
    fun useAutomaticRate() {
        _formState.update { it.copy(manualRate = null, equivalent = null) }
        refreshRate()
    }

    private var rateJob: Job? = null

    /** Rates resolved per currency pair and day, so typing an amount doesn't resolve (or download) again. */
    private val rateCache = FormRateCache(transactionAmounts, viewModelScope)

    /**
     * Previews the automatic rate for the current currency and date (unless the user typed one).
     * The rate comes from [rateCache]; only a new currency or date triggers a resolve, and the
     * equivalent is always recomputed locally from the latest typed amount.
     */
    private fun refreshRate() {
        rateJob?.cancel()
        val s = _formState.value
        val accountCurrency = s.accountCurrency ?: return
        if (!s.isForeign) {
            _formState.update { it.copy(rate = ExchangeRate.IDENTITY, needsManualRate = false, equivalent = null) }
            return
        }
        if (s.manualRate != null) return
        val day = s.date.toLocalDate()
        rateJob = viewModelScope.launch {
            val main = userPreferences.mainCurrency.first()
            val result = rateCache.rates(FormRateCache.Key(s.currency, accountCurrency, main, day))
            _formState.update {
                val stale = it.currency != s.currency || it.accountCurrency != accountCurrency || it.date.toLocalDate() != day
                if (it.manualRate != null || stale) return@update it
                when (result) {
                    is AmountResult.Ready -> it.copy(
                        rate = result.accountRate,
                        needsManualRate = false,
                        equivalent = FormRateCache.equivalent(it.amount, it.currency, accountCurrency, result),
                    )
                    AmountResult.NeedsManualRate -> it.copy(rate = null, needsManualRate = true, equivalent = null)
                }
            }
        }
    }

    fun updateDescription(value: String) = _formState.update { it.copy(description = value) }
    fun updateCategory(id: Long?) = _formState.update { it.copy(categoryId = id) }
    fun updateDate(value: LocalDateTime) {
        _formState.update { it.copy(date = value, error = null) }
        refreshRate()
    }
    fun updateTime(hour: Int, minute: Int) = _formState.update { it.copy(date = it.date.withHour(hour).withMinute(minute)) }
    fun updateType(value: TransactionType) = _formState.update { it.copy(type = value) }
    fun updateIsCleared(value: Boolean) = _formState.update { it.copy(isCleared = value) }
    fun updateHasReminder(value: Boolean) = _formState.update {
        it.copy(
            hasReminder = value,
            reminderDuration = if (value) ReminderDuration.FIFTEEN_MINUTES else null
        )
    }
    fun updateReminderDuration(duration: ReminderDuration?) = _formState.update { it.copy(reminderDuration = duration) }
    fun updateInInstallments(value: Boolean) = _formState.update {
        it.copy(inInstallments = value, isRecurrent = it.isRecurrent && !value, error = null)
    }
    fun updateInstallmentCount(value: String) {
        if (value.matches(Regex("^\\d*$"))) _formState.update { it.copy(installmentCount = value, error = null) }
    }
    fun updateIsRecurrent(value: Boolean) = _formState.update {
        it.copy(
            isRecurrent = value,
            inInstallments = it.inInstallments && !value,
            recurrenceFrequency = if (value && it.recurrenceFrequency == null) RecurrenceFrequency.MONTHLY else it.recurrenceFrequency
        )
    }
    fun updateRecurrenceFrequency(freq: RecurrenceFrequency) = _formState.update { it.copy(recurrenceFrequency = freq) }
    fun updateRecurrenceInterval(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d+$"))) {
            _formState.update { it.copy(recurrenceInterval = value, error = null) }
        }
    }

    fun save() {
        val s = _formState.value
        // A second tap while saving (or after) must not write the movement again.
        if (s.isLoading || s.isSaved) return
        if (!s.isValid) {
            _formState.update { it.copy(error = TransactionFormError.InvalidAmount) }
            return
        }
        if (!s.canSave) {
            _formState.update {
                it.copy(
                    error = if (s.needsManualRate) TransactionFormError.NeedsRate else TransactionFormError.AccountLoading
                )
            }
            return
        }
        val accountCurrency = s.accountCurrency!!
        _formState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val nextOccurrenceDate = if (s.isRecurrent && s.recurrenceFrequency != null) {
                    s.recurrenceFrequency.advance(s.date, s.recurrenceInterval.toIntOrNull() ?: 1)
                } else null

                if (s.installmentsActive) {
                    installmentPlanRepository.create(
                        NewInstallmentPlan(
                            accountId = s.accountId ?: accountRepository.defaultAccount().id,
                            description = s.description,
                            categoryId = s.categoryId,
                            original = s.money!!,
                            installments = s.installmentCount.toInt(),
                            purchaseDate = s.date,
                        ),
                    )
                    analyticsHelper.logEvent(
                        "installment_plan_created",
                        mapOf("installments" to s.installmentCount.toInt(), "has_category" to (s.categoryId != null)),
                    )
                    _formState.update { it.copy(isLoading = false, isSaved = true) }
                    return@launch
                }

                val previous = editing
                val accountId = s.accountId ?: previous?.accountId ?: accountRepository.defaultAccount().id
                val draft = Transaction.singleCurrency(accountId, s.type, s.date, s.money!!).copy(
                    id = if (s.isEditing) s.id else 0,
                    description = s.description,
                    loanId = previous?.loanId,
                    installmentNumber = previous?.installmentNumber,
                    parentRecurrenceId = previous?.parentRecurrenceId,
                    isCleared = s.isCleared,
                    hasReminder = s.hasReminder,
                    reminderSecondsBefore = s.reminderDuration?.secondsBefore,
                    categoryId = s.categoryId,
                    isRecurrent = s.isRecurrent,
                    recurrenceFrequency = s.recurrenceFrequency,
                    recurrenceInterval = s.recurrenceInterval.toIntOrNull(),
                    nextOccurrenceDate = nextOccurrenceDate
                )
                val main = userPreferences.mainCurrency.first()
                val transaction = transactionAmounts.forSave(draft, previous, accountCurrency, main, s.manualRateValue)
                if (transaction == null) {
                    _formState.update {
                        it.copy(isLoading = false, needsManualRate = true, rate = null, error = TransactionFormError.NeedsRate)
                    }
                    return@launch
                }

                if (s.isEditing) {
                    transactionRepository.updateTransaction(transaction)
                    if (transaction.isRecurrent && transaction.isCleared && !s.wasCleared) {
                        transactionRepository.createNextRecurrence(transaction)
                    }
                } else {
                    transactionRepository.insertTransaction(transaction)
                }

                analyticsHelper.logEvent(
                    if (s.isEditing) "transaction_updated" else "transaction_created",
                    mapOf(
                        "type" to s.type.name,
                        "has_category" to (s.categoryId != null),
                        "has_reminder" to s.hasReminder
                    )
                )

                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: MissingRateException) {
                _formState.update { it.copy(isLoading = false, error = TransactionFormError.MissingRate(e.from)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saving the movement failed", e)
                _formState.update { it.copy(isLoading = false, error = TransactionFormError.SaveFailed) }
            }
        }
    }

    fun delete() {
        val s = _formState.value
        if (!s.isEditing) return
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true, error = null) }
            try {
                transactionRepository.deleteTransaction(s.id)
                analyticsHelper.logEvent("transaction_deleted", mapOf("type" to s.type.name))
                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Deleting the movement failed", e)
                _formState.update { it.copy(isLoading = false, error = TransactionFormError.DeleteFailed) }
            }
        }
    }

    private companion object {
        const val TAG = "TransactionForm"
    }
}
