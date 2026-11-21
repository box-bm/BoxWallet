package com.boxdev.boxwallet.data.repository

import android.content.Context
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.datastore.UserPreferences
import androidx.room.withTransaction
import com.boxdev.boxwallet.data.local.dao.AccountDao
import com.boxdev.boxwallet.data.local.dao.TransactionDao
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.AccountEntity
import com.boxdev.boxwallet.data.local.dao.CardMovementRow
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.local.entity.isInflow
import com.boxdev.boxwallet.data.rates.MovementWriteGuard
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.CardMovement
import com.boxdev.boxwallet.domain.model.CardSummary
import com.boxdev.boxwallet.domain.model.cardBalance
import com.boxdev.boxwallet.domain.model.cardSummary
import com.boxdev.boxwallet.domain.model.statementStatus
import com.boxdev.boxwallet.domain.model.TotalBalance
import com.boxdev.boxwallet.domain.model.lastClosingDate
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.domain.model.totalBalance
import com.boxdev.boxwallet.domain.money.AmountInput
import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.MissingRateException
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.RateSource
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import com.boxdev.boxwallet.domain.money.withAmounts
import com.boxdev.boxwallet.util.CurrentDay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/** A rule the account screens turn into a message instead of a crash. */
sealed class AccountRuleError : Exception() {
    /** The default account can't be deleted or archived. */
    data object IsDefault : AccountRuleError()

    /** At least one account must remain. */
    data object LastAccount : AccountRuleError()

    /** Accounts with movements (other than balance adjustments) are archived, not deleted. */
    data object HasMovements : AccountRuleError()

    /** The second currency of a card can only go while it has nothing but balance adjustments. */
    data object SecondCurrencyHasMovements : AccountRuleError()

    /** A card with a second currency can't stop being a card until that currency is removed. */
    data object RemoveSecondCurrencyFirst : AccountRuleError()
}

/** A card's second currency: [currentBalance] is signed like the card's (a debt is negative). */
data class SecondCurrencyInput(val currency: Currency, val currentBalance: Money?)

/** What [AccountRepository.update] does with a card's second currency. */
sealed interface SecondCurrencyChange {
    data object Keep : SecondCurrencyChange
    data class Add(val input: SecondCurrencyInput) : SecondCurrencyChange
    data object Remove : SecondCurrencyChange
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val userPreferences: UserPreferences,
    private val rates: RateLookup,
    @param:ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val database: BoxWalletDatabase,
    private val amounts: TransactionAmounts,
    private val guard: MovementWriteGuard,
    private val currentDay: CurrentDay = CurrentDay(),
) {
    private val mutex = Mutex()

    /**
     * Returns the default account, creating it (CHECKING, main currency) when the database has none.
     * Idempotent and safe to call concurrently; the id is kept in DataStore as `defaultAccountId`.
     */
    suspend fun ensureDefaultAccount(): Account = mutex.withLock {
        userPreferences.defaultAccountId.first()
            ?.let { accountDao.getById(it) }
            ?.let { return@withLock it.toDomain() }

        val existing = accountDao.getAll().firstOrNull()
        val account = existing?.toDomain() ?: run {
            val created = Account(
                name = context.getString(R.string.account_default_name),
                type = AccountType.CHECKING,
                currency = userPreferences.mainCurrency.first(),
                createdAt = System.currentTimeMillis(),
            )
            created.copy(id = accountDao.insert(created.toEntity()))
        }
        userPreferences.setDefaultAccountId(account.id)
        account
    }

    /** The account new movements go to; created on demand so writes never miss the foreign key. */
    suspend fun defaultAccount(): Account = ensureDefaultAccount()

    suspend fun count(): Int = accountDao.count()

    /** Account id → currency, for mapping movements to the domain. */
    fun observeCurrencies(): Flow<Map<Long, Currency>> =
        accountDao.observeAll().map { rows -> rows.associate { it.id to Currency.getInstance(it.currencyCode) } }

    /** Start of the day after [day] as stored dates (local wall-clock as UTC millis): cards count movements before it. */
    private fun cutoffAfter(day: LocalDate): Long =
        day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun endOfToday(): Long = cutoffAfter(LocalDate.now())

    /**
     * Accounts (by `sortOrder, id`) with their balance (cleared only, except cards); accounts without movements
     * read 0. Re-queried when the day changes, so a card's movement dated tomorrow counts from midnight.
     */
    fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>> = currentDay.days.flatMapLatest(::accountsWithBalanceOn)

    private fun accountsWithBalanceOn(today: LocalDate): Flow<List<AccountWithBalance>> =
        combine(accountDao.observeAll(), accountDao.observeBalances(cutoffAfter(today))) { accounts, balances ->
            val byAccount = balances.associate { it.accountId to it.balanceMinor }
            accounts.map {
                val account = it.toDomain()
                AccountWithBalance(account, Money(byAccount[account.id] ?: 0L, account.currency))
            }
        }

    /** Total in the main currency at today's rates, from local data only (nearest rate as fallback, never downloads). */
    fun observeTotalBalance(): Flow<TotalBalance> = currentDay.days.flatMapLatest { today ->
        combine(accountsWithBalanceOn(today), userPreferences.mainCurrency) { accounts, main -> accounts to main }
            .map { (accounts, main) ->
                val resolver = ExchangeRateResolver(offline(rates))
                totalBalance(accounts, main) { currency -> resolver.resolve(today, currency, main) }
            }
    }

    /**
     * Each top-level card (archived ones too) by id, with its second currency: balances from the same source
     * as the accounts list, statements when the card has both days, and available converted at today's
     * offline rate (second → card currency). Recomputed when the day changes.
     */
    fun observeCardSummaries(): Flow<Map<Long, CardSummary>> = currentDay.days.flatMapLatest(::cardSummariesOn)

    /** [observeCardSummaries] as of [today]: its cutoff and statement math both use that day. */
    fun cardSummariesOn(today: LocalDate): Flow<Map<Long, CardSummary>> {
        val cutoff = cutoffAfter(today)
        return combine(
            accountDao.observeAll(),
            accountDao.observeBalances(cutoff),
            transactionDao.observeCardMovements(cutoff),
        ) { accounts, balances, movements -> Triple(accounts, balances, movements) }
            .map { (entities, balances, movements) ->
                val resolver = ExchangeRateResolver(offline(rates))
                val balanceOf = balances.associate { it.accountId to it.balanceMinor }
                val movementsOf = movements.groupBy { it.accountId }
                val accounts = entities.map { it.toDomain() }.filter { it.type == AccountType.CREDIT_CARD }
                val childOf = accounts.filter { it.parentAccountId != null }.associateBy { it.parentAccountId }
                accounts.filter { it.parentAccountId == null }.associate { card ->
                    fun balanceFor(account: Account) = cardBalance(
                        account,
                        Money(balanceOf[account.id] ?: 0L, account.currency),
                        statementOf(card, movementsOf[account.id].orEmpty(), today),
                    )
                    val child = childOf[card.id]
                    val rate = child?.let { resolver.resolve(today, it.currency, card.currency) }
                    card.id to cardSummary(
                        card,
                        balanceFor(card),
                        child?.let { balanceFor(it) },
                        rate?.value,
                        approximate = rate?.source == RateSource.APPROXIMATE,
                    )
                }
            }
    }

    /** [card]'s closing and due days applied to [rows] (the card's or its child's); null without both days. */
    private fun statementOf(card: Account, rows: List<CardMovementRow>, today: LocalDate) =
        card.statementClosingDay?.let { closing ->
            card.paymentDueDay?.let { due -> statementStatus(rows.map { it.toCardMovement() }, closing, due, today) }
        }

    private fun CardMovementRow.toCardMovement(): CardMovement {
        val inflow = TransactionType.valueOf(type).isInflow
        return CardMovement(
            date = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneOffset.UTC).toLocalDate(),
            signedMinor = if (inflow) accountAmountMinor else -accountAmountMinor,
            isInflow = inflow,
        )
    }

    /** Same lookup without the network: the resolver then falls back from exact to nearest rates. */
    private fun offline(delegate: RateLookup) = object : RateLookup by delegate {
        override suspend fun download(day: LocalDate) = false
    }

    /** How many accounts aren't archived; transfers need at least two. */
    fun observeActiveCount(): Flow<Int> =
        accountDao.observeAll().map { rows -> rows.count { !it.isArchived } }.distinctUntilChanged()

    fun observeAll(): Flow<List<Account>> = accountDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeDefaultAccountId(): Flow<Long?> = userPreferences.defaultAccountId

    suspend fun hasNonAdjustmentMovements(accountId: Long): Boolean =
        transactionDao.countNonAdjustmentByAccount(accountId) > 0

    /** The account that last paid card [cardId] (or its second currency [childId]), never one of the card's own. */
    suspend fun lastPayerOf(cardId: Long, childId: Long?): Long? =
        transactionDao.lastPayerAccountId(listOfNotNull(cardId, childId))

    /** The second-currency child of card [cardId], or null. */
    fun secondCurrencyOf(cardId: Long): Flow<Account?> = accountDao.observeChildOf(cardId).map { it?.toDomain() }

    /**
     * Inserts [account]; a non-zero [currentBalance] (in the account's currency, signed: a card's debt is
     * negative) becomes an opening adjustment. A card can also get its [second] currency, as a child account.
     */
    suspend fun create(account: Account, currentBalance: Money?, second: SecondCurrencyInput? = null): Long =
        guard.write { current ->
            database.withTransaction {
                validateCardFields(account)
                second?.let { requireSecondAllowed(account, it) }
                val id = accountDao.insert(account.toEntity())
                val opening = openingDate(account)
                if (currentBalance != null && currentBalance.minor != 0L) {
                    insertAdjustment(id, account.currency, current.main, currentBalance.minor, opening)
                }
                second?.let { insertChild(account.copy(id = id), it, current.main, opening) }
                id
            }
        }

    /**
     * The currency is frozen once the account has movements besides adjustments. While it only has
     * adjustments, changing it rewrites them into the new currency keeping their typed major amount;
     * a missing main rate ([MissingRateException]) rolls the whole update back.
     */
    /**
     * A card's second currency follows [second]; a kept one gets the card's name, icon, color,
     * `includeInTotal` and order. Removing it ([AccountRuleError.SecondCurrencyHasMovements] while it has
     * movements besides adjustments) takes its adjustments along.
     */
    suspend fun update(account: Account, second: SecondCurrencyChange = SecondCurrencyChange.Keep) = guard.write { current ->
        database.withTransaction {
            val stored = accountDao.getById(account.id)
                ?: throw IllegalStateException("Account ${account.id} does not exist")
            require(stored.isArchived == account.isArchived) { "use archive()" }
            validateCardFields(account)
            val child = accountDao.getChildOf(account.id)
            val kept = child.takeIf { second != SecondCurrencyChange.Remove }
            if (kept != null && account.type != AccountType.CREDIT_CARD) throw AccountRuleError.RemoveSecondCurrencyFirst
            require(kept == null || kept.currencyCode != account.currency.currencyCode) {
                "The second currency must differ from the card's"
            }
            if (second is SecondCurrencyChange.Add) {
                require(child == null) { "Card ${account.id} already has a second currency" }
                requireSecondAllowed(account.copy(parentAccountId = stored.parentAccountId), second.input)
            }
            val currencyChanged = stored.currencyCode != account.currency.currencyCode
            check(!currencyChanged || !hasNonAdjustmentMovements(account.id)) {
                "Currency of account ${account.id} can't change once it has movements"
            }
            if (child != null && second == SecondCurrencyChange.Remove) {
                if (hasNonAdjustmentMovements(child.id)) throw AccountRuleError.SecondCurrencyHasMovements
                transactionDao.deleteAdjustmentsByAccount(child.id)
                accountDao.deleteById(child.id)
            }
            accountDao.update(account.toEntity())
            if (currencyChanged) rewriteAdjustments(account.id, account.currency, current.main)
            kept?.let { accountDao.update(childEntity(account, Currency.getInstance(it.currencyCode)).copy(id = it.id)) }
            if (second is SecondCurrencyChange.Add) insertChild(account, second.input, current.main, openingDate(account))
        }
    }

    /** Days are 1–31 and a limit is positive. */
    private fun validateCardFields(account: Account) {
        account.statementClosingDay?.let { require(it in 1..31) { "Closing day $it is outside 1–31" } }
        account.paymentDueDay?.let { require(it in 1..31) { "Due day $it is outside 1–31" } }
        account.creditLimitMinor?.let { require(it > 0) { "Credit limit must be positive" } }
    }

    private fun requireSecondAllowed(account: Account, input: SecondCurrencyInput) {
        require(account.type == AccountType.CREDIT_CARD) { "Only cards have a second currency" }
        require(account.parentAccountId == null) { "A second currency can't have its own" }
        require(input.currency != account.currency) { "The second currency must differ from the card's" }
        require(input.currentBalance == null || input.currentBalance.currency == input.currency) {
            "Second-currency balance must be in that currency"
        }
    }

    /** A card's opening adjustments are dated at the end of its last closing day when it has one, else now. */
    private fun openingDate(account: Account): LocalDateTime {
        val closingDay = account.statementClosingDay.takeIf { account.type == AccountType.CREDIT_CARD }
        return closingDay?.let { lastClosingDate(it, LocalDate.now()).atTime(23, 59, 59) } ?: LocalDateTime.now()
    }

    /** The child row of [card] in [currency]: no card fields, the rest copied from the card. */
    private fun childEntity(card: Account, currency: Currency): AccountEntity = card.copy(
        id = 0,
        type = AccountType.CREDIT_CARD,
        currency = currency,
        description = "",
        parentAccountId = card.id,
        creditLimitMinor = null,
        statementClosingDay = null,
        paymentDueDay = null,
        dueReminderDaysBefore = null,
    ).toEntity()

    private suspend fun insertChild(card: Account, input: SecondCurrencyInput, main: Currency, date: LocalDateTime) {
        val childId = accountDao.insert(childEntity(card, input.currency))
        val balance = input.currentBalance
        if (balance != null && balance.minor != 0L) insertAdjustment(childId, input.currency, main, balance.minor, date)
    }

    /** Re-expresses the account's adjustments in [currency] (same major amount, HALF_EVEN) with fresh legs. */
    private suspend fun rewriteAdjustments(accountId: Long, currency: Currency, main: Currency) {
        transactionDao.getAdjustmentsByAccount(accountId).forEach { row ->
            val old = row.toDomain(Currency.getInstance(row.currencyCode), main)
            val original = Money.of(old.original.toMajor(), currency)
            val ready = amounts.compute(AmountInput(original, old.date.toLocalDate(), currency, main))
                as? AmountResult.Ready ?: throw MissingRateException(currency, main)
            transactionDao.update(old.copy(original = original).withAmounts(ready).toEntity())
        }
    }

    /** Adds the adjustment that makes the cleared balance equal [realBalance]; nothing when already equal. */
    suspend fun adjustBalance(accountId: Long, realBalance: Money) {
        guard.write { current ->
            database.withTransaction {
                val currency = current.accountCurrency(accountId)
                require(realBalance.currency == currency) { "Balance must be in the account currency" }
                val difference = realBalance.minor - accountDao.getBalance(accountId, endOfToday())
                if (difference != 0L) insertAdjustment(accountId, currency, current.main, difference, LocalDateTime.now())
            }
        }
    }

    /** A card's second currency is archived and unarchived with it. */
    suspend fun archive(accountId: Long, archived: Boolean) {
        if (archived && userPreferences.defaultAccountId.first() == accountId) throw AccountRuleError.IsDefault
        database.withTransaction {
            val stored = accountDao.getById(accountId) ?: return@withTransaction
            accountDao.update(stored.copy(isArchived = archived))
            accountDao.getChildOf(accountId)?.let { accountDao.update(it.copy(isArchived = archived)) }
        }
    }

    /**
     * Removes an account with no movements besides its adjustments (those go with it). A card's second
     * currency goes too, under the same rule.
     */
    suspend fun delete(accountId: Long) {
        if (userPreferences.defaultAccountId.first() == accountId) throw AccountRuleError.IsDefault
        if (accountDao.getAll().count { it.parentAccountId == null } <= 1) throw AccountRuleError.LastAccount
        database.withTransaction {
            val child = accountDao.getChildOf(accountId)
            if (hasNonAdjustmentMovements(accountId) || (child != null && hasNonAdjustmentMovements(child.id))) {
                throw AccountRuleError.HasMovements
            }
            child?.let {
                transactionDao.deleteAdjustmentsByAccount(it.id)
                accountDao.deleteById(it.id)
            }
            transactionDao.deleteAdjustmentsByAccount(accountId)
            accountDao.deleteById(accountId)
        }
    }

    /** An archived account can't become the default; unarchive it first. */
    suspend fun setDefault(accountId: Long) {
        val account = accountDao.getById(accountId) ?: throw IllegalStateException("Account $accountId does not exist")
        check(!account.isArchived) { "Archived account $accountId can't be the default" }
        userPreferences.setDefaultAccountId(accountId)
    }

    /** A cleared adjustment of [deltaMinor] (sign picks the direction) dated [date], in the account's currency. */
    private suspend fun insertAdjustment(
        accountId: Long,
        currency: Currency,
        main: Currency,
        deltaMinor: Long,
        date: LocalDateTime,
    ) {
        val original = Money(kotlin.math.abs(deltaMinor), currency)
        val ready = amounts.compute(AmountInput(original, date.toLocalDate(), currency, main)) as? AmountResult.Ready
            ?: throw MissingRateException(currency, main)
        val adjustment = Transaction(
            accountId = accountId,
            type = if (deltaMinor > 0) TransactionType.ADJUSTMENT_IN else TransactionType.ADJUSTMENT_OUT,
            date = date,
            original = original,
            accountAmount = ready.accountAmount,
            accountRate = ready.accountRate,
            amount = ready.amount,
            mainRate = ready.mainRate,
            isCleared = true,
        )
        transactionDao.insert(adjustment.toEntity())
    }
}
