# Visacuotas (card installment purchases) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** You can buy something on a credit card "a cuotas". Each installment is charged at the card's statement closing and counts as that month's expense. The card's debt and available credit reflect the whole pending amount from the day of purchase. The card's detail lists its installment purchases.

**Architecture:**
- A new `installment_plans` table holds one row per purchase.
- Each installment is an ordinary `EXPENSE` row in `transactions`, linked by the new `installmentPlanId` column and dated on its closing day.
- Because cards count every movement dated up to today (the §6 card rule), installments enter the card balance, the statement and Home totals by themselves on their date. Nothing gets marked paid.
- What's new:
  - the pure date and split math in `domain`;
  - pending installments in the available credit;
  - hiding future installments from lists;
  - the card-edit rules;
  - the UI.
- The app is not in production, so the schema stays at **Room version 1**: this PR folds the PR 1 migration and the new tables into a single v1 schema, with no migrations. It also adds the loans columns (`kind`, `expectedDate`) the next PR (person-to-person loans) needs.
- Old backups are discarded: restore accepts only the current backup format.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 Expressive, Room, Hilt, JUnit4 + Robolectric (sdk 34).

**Spec:** `docs/superpowers/specs/2026-11-21-visacuotas-and-personal-loans-design.md` (§2, §3, §5, §7 as amended, §8 PR 1). It builds on `docs/superpowers/specs/2026-11-20-multi-account-design.md` (§6 card rule, §8 statement math).

Branch `feat/visacuotas` from `main` at `3ed3b81`, which has PR 1–3 merged. The design doc is already committed (`b9666d3`). PR title: `feat: compras a cuotas (Visacuotas) en tarjetas de crédito`.

## Global Constraints

- **Installment dates (§5.1).**
  - Installment 1 falls on the first closing date that is **≥ the purchase date**. A purchase made on the closing day falls into that closing.
  - Installment k falls on the k-th closing date from there.
  - A closing day past the month's length clamps to that month's last day: use the existing `clampDay`.
  - Each installment keeps the purchase's time of day.
- **Split (§5.1).** The amount is split into equal parts, and the last installment absorbs the remainder. Example: 100000 / 3 → 33333, 33333, 33334. Apply this to both `originalAmountMinor` and `accountAmountMinor`.
- **Conversion (§5.1).**
  - The purchase converts to the account currency **once**, at the purchase day's rate. Every installment copies that `accountRate` and its source.
  - Each installment's `mainRate` is resolved for its own date. For a future date, the nearest rate is used, with source `APPROXIMATE`.
- **Requirements.**
  - Plans exist only on a `CREDIT_CARD` account, or on its second-currency child, whose card has a `statementClosingDay`.
  - A plan has between 2 and 60 installments.
  - The plan's form toggle appears only for a new, non-recurrent `EXPENSE`.
- **Balances (§5.2).**
  - Statement math (§8) is unchanged.
  - Available = limit − (used₁ + pending₁) − (used₂ + pending₂) × rate. Pending = Σ `accountAmountMinor` of plan installments dated ≥ start of tomorrow.
  - The card shows "Usado Q X · Cuotas por cobrar Q Y", and the usage bar includes both.
- **Lists (§5.3).**
  - A plan installment dated after today is never listed: not in Movements, account detail, Home's latest, search, Excel, or the month's movements.
  - A dated installment's label is `"<description> · cuota k/N"`.
  - Tapping it opens the plan detail.
- **Card edits (§5.1).**
  - While a card (or its child) has pending installments, these are refused with `AccountRuleError.HasPendingInstallments`:
    - turning "Tarjeta de crédito" off;
    - clearing `statementClosingDay`;
    - removing a second currency that has plans.
  - Changing `statementClosingDay` re-dates every pending installment of the card and its child in the same DB transaction. The first pending installment goes on the first new closing ≥ tomorrow, and the rest follow one closing apart. Re-dated installments get their `mainRate` re-resolved.
- **Plan edits (§5.3).**
  - Only `description` and `categoryId` can change, and the change propagates to every installment.
  - Delete removes the plan and all its installments, charged ones included (FK CASCADE). Ask for confirmation first, stating how much the debt changes.
- **Data (user ruling, overrides spec §7).**
  - Room `version = 1`, always. No `Migration` objects. The builder uses `fallbackToDestructiveMigration(dropAllTables = true)` and `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`, so an install still on v2 is wiped on first open.
  - The backup format is version 5. Restore accepts **only** version 5; any other version, or a file that doesn't parse as `BackupData`, is refused with `RestoreResult.Incompatible` and nothing is written. Legacy conversion is removed.
- Visacuotas never show in the Loans tab or in loan queries. Installments have `loanId = null`.
- All copy goes in both `values/strings.xml` (English) and `values-es/strings.xml`.
- UI follows `DESIGN.md` and `ui/components/Expressive.kt`.
- Run tests with `./gradlew testDebugUnitTest --tests <Class>`. Commits are Conventional Commits and end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **The `ApproximateRateFixer` account leg.** An installment saved with an approximate *account* rate must not be re-converted at its own (later) date. The plan's conversion is fixed at purchase. Only its main leg gets upgraded. *Pinned in Task 5.*
2. **An old or foreign backup file.** A v3/v4 file, a legacy Double-amounts file or junk JSON must be refused with a clear message and leave the current data untouched, both from Settings and from onboarding. *Pinned in Task 7.*
3. **A dual-currency card.** A plan on the USD child must be counted in the child's pending total and converted into the available credit. It must not be counted as primary. *Pinned in Task 4.*
4. **A closing-day change on a card with both charged and pending installments.** Charged rows must keep their dates exactly. *Pinned in Task 6.*
5. **The installment dated today.** It is charged (counted and listed) today, not tomorrow. *Pinned in Task 5 and Task 2.*

---

### Task 1: One v1 schema with installment plans

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/data/local/entity/InstallmentPlanEntity.kt`
- Create: `app/src/main/java/com/boxdev/boxwallet/data/local/dao/InstallmentPlanDao.kt`
- Modify: `data/local/entity/TransactionEntity.kt`, `data/local/entity/LoanEntity.kt`, `data/local/database/BoxWalletDatabase.kt` (`version = 1`, new entity and DAO, KDoc says "not in production: one schema version, destructive fallback"), `di/DatabaseModule.kt` (drop `addMigrations`, add both destructive fallbacks, provide the DAO), `domain/model/Transaction.kt` (field + mappers)
- Delete: `data/local/database/Migrations.kt`, `app/schemas/com.boxdev.boxwallet.data.local.database.BoxWalletDatabase/2.json`, `app/src/test/java/com/boxdev/boxwallet/data/local/database/MigrationTest.kt`
- Regenerate: `app/schemas/com.boxdev.boxwallet.data.local.database.BoxWalletDatabase/1.json` (by the build)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/local/database/SchemaTest.kt`

**Interfaces:**
- Produces:
  - `InstallmentPlanEntity`, table `installment_plans`:
    - `id: Long` (autogenerated)
    - `accountId: Long` (FK `accounts`, RESTRICT, indexed)
    - `description: String`
    - `categoryId: Long?` (FK `categories`, SET_NULL, indexed)
    - `currencyCode: String` and `originalMinor: Long`: the purchase's original currency and amount
    - `accountTotalMinor: Long`: the amount in the account's currency
    - `accountRate: BigDecimal`
    - `accountRateSource: RateSource`
    - `installments: Int`
    - `purchaseDate: Long`: local wall-clock time stored as UTC millis, like `transactions.date`
  - `TransactionEntity.installmentPlanId: Long?`: FK `installment_plans.id`, CASCADE, indexed.
  - `LoanEntity.kind: String = "SCHEDULED"` and `LoanEntity.expectedDate: Long? = null`.
  - `Transaction.installmentPlanId: Long? = null`, mapped both ways.
  - `InstallmentPlanDao`:
    - `insert(plan): Long`
    - `insertAll(plans)`
    - `update(plan)`
    - `getById(id): InstallmentPlanEntity?`
    - `observeById(id): Flow<InstallmentPlanEntity?>`
    - `observeByAccounts(accountIds: List<Long>): Flow<List<InstallmentPlanEntity>>`
    - `getAllSync(): List<InstallmentPlanEntity>`
    - `deleteById(id)`
    - `deleteAll()`

- [ ] **Step 1: Write the failing test** `SchemaTest`, using the in-memory DB:
  - `deleting a plan deletes its installments`: insert an account, a plan and an installment that references it; delete the plan; the installment is gone (CASCADE).
  - `a new loan defaults to SCHEDULED`: insert a `LoanEntity` without `kind`, read it back, `kind == "SCHEDULED"` and `expectedDate == null`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.data.local.database.SchemaTest`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement** the entities, DAO, version 1, fallbacks and deletions above. Check that `1.json` now has `installment_plans` and that no test or source still references `MIGRATION_1_2`.
- [ ] **Step 4: Run** it again, then `./gradlew testDebugUnitTest` (full suite: schema change). Expected: PASS.
- [ ] **Step 5: Commit** `feat: one v1 database schema with installment plans`.

### Task 2: Installment math in `domain`

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/model/InstallmentPlan.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/model/InstallmentPlanTest.kt`

**Interfaces:**
- Consumes: `clampDay(year, month, day)` from `CreditCardCycle.kt`.
- Produces:
  - `fun firstClosingOnOrAfter(closingDay: Int, date: LocalDate): LocalDate`
  - `fun installmentClosingDates(closingDay: Int, from: LocalDate, count: Int): List<LocalDate>` (the first one is `firstClosingOnOrAfter(closingDay, from)`)
  - `fun splitEvenly(totalMinor: Long, count: Int): List<Long>`
  - `data class InstallmentPlan(id, accountId, description, categoryId, original: Money, accountTotal: Money, installments: Int, purchaseDate: LocalDateTime, charged: Int, pendingMinor: Long)`, where `charged` is the number of installments dated ≤ today and `pendingMinor` is the account-currency sum of the rest
  - `val InstallmentPlan.perClosing: Money` (installment 1's account amount) and `val InstallmentPlan.isFinished: Boolean` (`charged == installments`)

- [ ] **Step 1: Write the failing tests:**
  - `purchase on the closing day falls in that closing`: `installmentClosingDates(15, 2026-11-15, 3)` == [2026-11-15, 2026-12-15, 2027-01-15].
  - `purchase the day after closing waits for the next one`: from 2026-11-16 → first is 2026-12-15.
  - `closing day 31 clamps in february`: `installmentClosingDates(31, 2027-01-31, 3)` == [2027-01-31, 2027-02-28, 2027-03-31].
  - `last installment absorbs the remainder`: `splitEvenly(100000, 3)` == [33333, 33333, 33334]; `splitEvenly(300000, 3)` == [100000, 100000, 100000].
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.domain.model.InstallmentPlanTest`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement** the functions above in `InstallmentPlan.kt`. Use pure Kotlin with no Android imports.
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat: installment dates and split`.

### Task 3: `InstallmentPlanRepository`

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/data/repository/InstallmentPlanRepository.kt`
- Modify: `data/local/dao/TransactionDao.kt` (installment queries)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/InstallmentPlanRepositoryTest.kt`

**Interfaces:**
- Consumes:
  - Task 1's DAO and entities.
  - Task 2's `installmentClosingDates` and `splitEvenly`.
  - `TransactionAmounts.compute(AmountInput)` → `AmountResult.Ready` for the purchase-day conversion.
  - `ExchangeRateResolver.resolve(day, from, to)` for each installment's main leg.
  - `MovementWriteGuard.write { current -> }`.
  - `CurrentDay`.
- Produces:
  - `data class NewInstallmentPlan(val accountId: Long, val description: String, val categoryId: Long?, val original: Money, val installments: Int, val purchaseDate: LocalDateTime)`.
  - `suspend fun create(input: NewInstallmentPlan): Long`:
    - throws `IllegalArgumentException` for an account that is not a card or card child, a card without a closing day, or a count outside 2..60;
    - throws `MissingRateException` with nothing written.
  - `suspend fun updateDetails(planId: Long, description: String, categoryId: Long?)`.
  - `suspend fun delete(planId: Long)`.
  - `fun observePlan(planId: Long): Flow<InstallmentPlan?>`.
  - `fun observeInstallments(planId: Long): Flow<List<Transaction>>`.
  - `fun observePlansOfCard(cardId: Long): Flow<List<InstallmentPlan>>` (the card and its child, newest purchase first).
  - The installments' `charged` and `pendingMinor` follow `CurrentDay`.
  - The closing day of a child is its parent card's.
  - New DAO queries:
    - `getByPlanId(planId): List<TransactionEntity>`
    - `observeByPlanId(planId): Flow<List<TransactionEntity>>`
    - `updateDetailsByPlanId(planId, description, categoryId)`
  - Installment rows:
    - `description = plan.description`, `installmentNumber = k`, `isCleared = false`, `loanId = null`;
    - `currencyCode` and `originalAmountMinor` hold the split of the original amount;
    - `accountAmountMinor` holds the split of the account total;
    - the account rate is the purchase's.

- [ ] **Step 1: Write the failing tests.** Use the in-memory DB and the fixed `RateLookup` pattern from `TransactionRepositoryTest`.
  - `creates one expense per closing with the remainder on the last`:
    - GTQ card, closing day 15, Q1,000.00 (100000) in 3, bought 2026-11-15 10:30.
    - Rows on 2026-11-15, 12-15 and 2027-01-15, all at 10:30.
    - Amounts 33333, 33333, 33334.
    - Type `EXPENSE`, `installmentPlanId` set, `loanId` null.
  - `foreign purchase converts once at the purchase day`:
    - €500 on a USD card, bought on a day with EUR→USD 1.10.
    - The plan's `accountTotalMinor` is 55000.
    - Every installment has `accountRate` 1.10, and the account amounts sum to 55000.
  - `future installment main leg is approximate`: an installment dated after the lookup's last rate day has `mainRateSource == APPROXIMATE`.
  - `rejects a card without a closing day, a checking account and 1 installment`: each throws `IllegalArgumentException`, and no rows are written.
  - `updateDetails propagates to every installment`, and `delete removes the plan and its installments`.
  - `plans of a card include its second currency's`: with a plan on the child, `observePlansOfCard(cardId)` returns it.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.data.repository.InstallmentPlanRepositoryTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `InstallmentPlanRepository` (`@Singleton`, `@Inject`). Compute everything before writing. Then write inside `guard.write` and `database.withTransaction`: first the plan, then `insertAll` of the installments.
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat: create and manage installment plans`.

### Task 4: Pending installments in the card's available

**Files:**
- Modify: `domain/model/CardSummary.kt`, `data/repository/AccountRepository.kt` (`cardSummariesOn`), `data/local/dao/TransactionDao.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/model/CardSummaryTest.kt`, `app/src/test/java/com/boxdev/boxwallet/data/repository/AccountRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `data class PendingInstallmentsRow(val accountId: Long, val pendingMinor: Long)`.
  - `TransactionDao.observePendingInstallments(endOfToday: Long): Flow<List<PendingInstallmentsRow>>`: rows with `installmentPlanId IS NOT NULL AND date >= :endOfToday`, grouped by account.
  - `CardBalance.pendingInstallmentsMinor: Long` (default 0). `cardBalance(account, balance, statement, pendingInstallmentsMinor = 0)`.
  - `cardSummary(...)`: available = limit − (used₁ + pending₁) − convert(used₂ + pending₂).
  - `val CardSummary.pendingInstallments: Money?`: the primary pending plus the converted secondary pending, in the card currency; null when zero.

- [ ] **Step 1: Write the failing tests:**
  - `CardSummaryTest`, `available subtracts pending installments`: limit 500000, used 100000, pending 66667 → available 333333.
  - `CardSummaryTest`, `second currency pending converts into available`: second pending 10000 USD at 7.7 → 77000 off the available.
  - `AccountRepositoryTest`, `statement to pay includes only the installment of that closing`:
    - card with closing day 15 and due day 5, today 2026-11-20;
    - a plan of 3×Q1,000 bought 2026-11-15;
    - `toPayMinor == 100000`, `primary.usedMinor == 100000`, `primary.pendingInstallmentsMinor == 200000`;
    - available = limit − 300000.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.domain.model.CardSummaryTest --tests com.boxdev.boxwallet.data.repository.AccountRepositoryTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Add the DAO query, combine it in `cardSummariesOn` with the same cutoff, and pass the pending totals into `cardBalance` and `cardSummary`.
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat: pending installments reduce a card's available credit`.

### Task 5: Lists hide future installments; the rate fixer keeps the plan's conversion

**Files:**
- Modify:
  - `data/local/dao/TransactionDao.kt`: `getAllTransactions`, `observeByAccount`, `getLatestTransactions` and `getTransactionsBetweenDates` get the `endOfToday: Long` parameter and `AND (installmentPlanId IS NULL OR date < :endOfToday)`.
  - `data/repository/TransactionRepository.kt`: each of those flows is wrapped in `currentDay.days.flatMapLatest`.
  - The Excel export (`AnalyticsViewModel.kt:484`) and Analysis already read `getTransactionsBetweenDates`, so they need no change of their own.
  - `data/rates/ApproximateRateFixer.kt`.
- Test: `app/src/test/java/com/boxdev/boxwallet/data/local/dao/TransactionDaoTest.kt`, `app/src/test/java/com/boxdev/boxwallet/data/repository/TransactionRepositoryTest.kt`, `app/src/test/java/com/boxdev/boxwallet/data/rates/ApproximateRateFixerTest.kt` (create it if it is missing)

**Interfaces:**
- Consumes: `CurrentDay` (already in `TransactionRepository`).
- Produces: the `TransactionRepository` public signatures are unchanged. Callers don't change.

- [ ] **Step 1: Write the failing tests:**
  - `TransactionDaoTest`, `a future installment is hidden and today's is listed`: installments dated `END_OF_TODAY - 1` and `END_OF_TODAY`. `getAllTransactions(END_OF_TODAY)` returns only the first. A plain future expense (no plan) is still listed.
  - `TransactionRepositoryTest`, `an installment counts in its own month, not the purchase month`:
    - 3×Q1,000 bought 2026-11-15 on a card with closing day 15;
    - `CurrentDay` on 2026-11-20 gives a `monthlyExpense` of 100000;
    - on 2026-12-20 it is 100000 again, from the December installment only.
  - `ApproximateRateFixerTest`, `an installment keeps its account rate and upgrades only its main rate`:
    - an installment with both sources `APPROXIMATE`, and a lookup that now has the exact rates for its day;
    - after `fix()`, `accountRate`, `accountRateSource` and `accountAmountMinor` are unchanged;
    - `mainRateSource == AUTO`, and `amountMinor` is recomputed.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.data.local.dao.TransactionDaoTest --tests com.boxdev.boxwallet.data.repository.TransactionRepositoryTest --tests com.boxdev.boxwallet.data.rates.ApproximateRateFixerTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** In `ApproximateRateFixer.correct`, skip the account leg when `tx.installmentPlanId != null`. Update every DAO call site the compiler flags.
- [ ] **Step 4: Run** it again, then the full suite. Expected: PASS.
- [ ] **Step 5: Commit** `feat: list installments from their closing day on`.

### Task 6: Card edits respect pending installments

**Files:**
- Modify: `data/repository/AccountRepository.kt` (`update`, `AccountRuleError`), `ui/screens/accounts/AccountFormViewModel.kt` (error → string), both `strings.xml`
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/AccountRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 2's `installmentClosingDates`, Task 1's DAO, and `ExchangeRateResolver`.
- Produces:
  - `AccountRuleError.HasPendingInstallments`, with the string `account_error_has_pending_installments`. EN: "This card has installments still to be charged". ES: "Esta tarjeta tiene cuotas por cobrar".
  - `TransactionDao.getPendingInstallments(accountIds: List<Long>, endOfToday: Long): List<TransactionEntity>`, ordered by `installmentPlanId`, then `installmentNumber`.

- [ ] **Step 1: Write the failing tests** in `AccountRepositoryTest`:
  - `changing the closing day moves pending installments to the new closings`:
    - closing day 15, plan 3×Q1,000 bought 2026-11-15, today 2026-11-20;
    - update the closing day to 25;
    - installment 1 stays on 2026-11-15; installments 2 and 3 are on 2026-11-25 and 2026-12-25, at the same time of day.
  - `turning off the card with pending installments is refused`: `update(card.copy(type = CHECKING, statementClosingDay = null, ...))` throws `HasPendingInstallments`, and the card is unchanged.
  - `clearing the closing day with pending installments is refused`: same, for `statementClosingDay = null` only.
  - `removing a second currency that has plans is refused`: `update(card, SecondCurrencyChange.Remove)` throws `HasPendingInstallments`. Check it before `SecondCurrencyHasMovements`.
  - `the card can change once every installment is charged`: with `CurrentDay` after the last installment, the type change succeeds.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.data.repository.AccountRepositoryTest`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Inside `update`'s transaction, use the injected `CurrentDay` for "today".
  - Group the pending installments per plan. Number k, the first pending one, goes to `installmentClosingDates(newDay, today + 1, pendingCount)` in order, keeping each row's time of day.
  - Re-resolve `mainRate` and `amountMinor` with the resolver (`APPROXIMATE` for future days).
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat: keep installments in step with card edits`.

### Task 7: Backups: only the current format

**Files:**
- Modify: `domain/model/BackupData.kt`, `data/repository/BackupRepository.kt`, `ui/screens/settings/BackupViewModel.kt`, `ui/screens/onboarding/OnboardingViewModel.kt` (and their screens' message), both `strings.xml`
- Delete: `data/repository/LegacyBackupConverter.kt`, `app/src/test/java/com/boxdev/boxwallet/data/repository/LegacyBackupConverterTest.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/BackupRepositoryTest.kt`, plus the existing `OnboardingViewModelTest` cases that restore

**Interfaces:**
- Produces:
  - `BackupData.installmentPlans: List<InstallmentPlanEntity> = emptyList()` and `CURRENT_VERSION = 5`. Rewrite the KDoc: one format, older ones are refused.
  - `RestoreResult.Incompatible` (data object). `restoreJson` parses with Gson; a parse error, `version != CURRENT_VERSION`, or a null `mainCurrencyCode` → `Incompatible`, nothing written.
  - Snapshot reads `installmentPlanDao.getAllSync()`.
  - Restore inserts accounts → categories → loans → installment plans → transactions; `deleteAll` runs in reverse.
  - Sanitize keeps its existing reference cleanup and adds: a plan whose category is missing drops it; an installment whose plan is missing drops `installmentPlanId` (and `installmentNumber` when it has no `loanId`). Remove the comments and branches that only existed for legacy files.
  - Copy `backup_error_incompatible`: EN "This backup is from an older version of BoxWallet and can't be restored", ES "Este respaldo es de una versión anterior de BoxWallet y no se puede restaurar".

- [ ] **Step 1: Write the failing tests** in `BackupRepositoryTest`:
  - `a backup round-trips installment plans`: snapshot → JSON → `restoreJson` → the same plan and installment links.
  - `an older backup version is refused and nothing changes`: JSON with `"version": 4` → `Incompatible`; the existing rows are still there.
  - `a file that isn't a backup is refused`: `"{}"` and `"not json"` → `Incompatible`.
  - `an installment whose plan is missing becomes a plain expense`.
  - Delete the tests that restored v3 or legacy files.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.data.repository.BackupRepositoryTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the changes above; Settings and onboarding show `backup_error_incompatible` for `Incompatible`.
- [ ] **Step 4: Run** it again, then the full suite. Expected: PASS.
- [ ] **Step 5: Commit** `feat: back up installment plans and refuse old backups`.

### Task 8: "A cuotas" in the movement form

**Files:**
- Modify: `ui/screens/transaction/TransactionFormViewModel.kt`, `ui/screens/transaction/TransactionFormScreen.kt`, both `strings.xml`
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/transaction/TransactionFormViewModelTest.kt`, `TransactionFormStateTest.kt`

**Interfaces:**
- Consumes: Task 3's `InstallmentPlanRepository.create(NewInstallmentPlan)`, and Task 2's `firstClosingOnOrAfter` and `splitEvenly`.
- Produces: in `TransactionFormState`:
  - `inInstallments: Boolean = false` and `installmentCount: String = "3"`;
  - `val canUseInstallments: Boolean`: true for a new, non-recurrent `EXPENSE` whose routed account is a card, or a card's child, whose card has a closing day;
  - `val installmentPreview: InstallmentPreview?`, with `data class InstallmentPreview(val perClosing: Money, val firstClosing: LocalDate)`.
- In the ViewModel:
  - `updateInInstallments(Boolean)` and `updateInstallmentCount(String)`, where the count accepts digits only and must be 2..60 to be valid;
  - turning recurrence on turns installments off, and the reverse.
- Copy:
  - `transaction_in_installments`: EN "In installments", ES "A cuotas".
  - `transaction_installments_preview`: EN "%1$s per statement · first on %2$s", ES "%1$s por corte · primera el %2$s".

- [ ] **Step 1: Write the failing tests:**
  - `TransactionFormStateTest`, `installments are offered only for a new card expense with a closing day`: false for a checking account, an income, editing, recurrent, or a card without a closing day; true for a card with closing day 15.
  - `TransactionFormStateTest`, `preview splits the amount and finds the first closing`: Q1,000.00, 3 installments, date 2026-11-16, closing day 15 → `perClosing` Q333.33 and `firstClosing` 2026-12-15.
  - `TransactionFormViewModelTest`, `saving in installments creates a plan instead of a movement`: no `insertTransaction`; one plan with 3 installments; `isSaved`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.ui.screens.transaction.TransactionFormStateTest --tests com.boxdev.boxwallet.ui.screens.transaction.TransactionFormViewModelTest`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - In `save()`, when `inInstallments && canUseInstallments`, build a `NewInstallmentPlan` from the state (the `pickedAccountId` routing gives the account) and call `create`. `MissingRateException` maps to the existing error.
  - In the screen, the switch, the count field and the preview line go next to the recurrence switch, and show only when `canUseInstallments`.
- [ ] **Step 4: Run** it again. Expected: PASS.
- [ ] **Step 5: Commit** `feat: buy in installments from the movement form`.

### Task 9: The card's installment section, plan detail and taps

**Files:**
- Create:
  - `app/src/main/java/com/boxdev/boxwallet/ui/screens/installments/InstallmentPlanDetailScreen.kt`
  - `app/src/main/java/com/boxdev/boxwallet/ui/screens/installments/InstallmentPlanDetailViewModel.kt`
  - `app/src/main/java/com/boxdev/boxwallet/ui/components/CardInstallmentsSection.kt`
- Modify:
  - `ui/navigation/NavRoutes.kt`: `InstallmentPlanDetail : NavRoutes("installment_plan/{planId}")` with `createRoute(planId)`.
  - `ui/navigation/NavGraph.kt`.
  - `ui/components/CardBlock.kt`: the "Cuotas por cobrar" line and the bar.
  - `ui/screens/accounts/AccountDetailViewModel.kt` and `AccountDetailScreen.kt`.
  - The four tap sites: `HomeScreen.kt:158`, `SearchScreen.kt:226`, `AccountDetailScreen.kt:190` and `TransactionsListScreen.kt:286`. Each gets a branch before the transfer one: `installmentPlanId != null -> onOpenInstallmentPlan(planId)`.
  - The movement row title, which becomes `"<description> · cuota k/N"`.
  - Both `strings.xml`.
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/installments/InstallmentPlanDetailViewModelTest.kt` and `app/src/test/java/com/boxdev/boxwallet/ui/screens/accounts/AccountDetailViewModelTest.kt`

**Interfaces:**
- Consumes:
  - Task 3: `observePlansOfCard`, `observePlan`, `observeInstallments`, `updateDetails`, `delete`.
  - Task 4: `CardSummary.pendingInstallments`.
- Produces:
  - `AccountDetailUiState.installmentPlans: List<InstallmentPlan>`: only for cards; the unfinished plans first.
  - `InstallmentPlanDetailViewModel`: `uiState` (plan, its installments with charged or pending, categories), `save(description, categoryId)` and `delete()`.
  - The delete confirmation shows the amount the debt drops: the sum of the charged installments.
- Copy (EN / ES):
  - `card_installments_title`: "Installment purchases" / "Compras a cuotas"
  - `card_installments_pending`: "%1$s still to be charged" / "Cuotas por cobrar %1$s"
  - `installment_progress`: "%1$d/%2$d · %3$s per statement · %4$s left" / "%1$d/%2$d · %3$s por corte · faltan %4$s"
  - `installment_row_title`: "%1$s · installment %2$d/%3$d" / "%1$s · cuota %2$d/%3$d"
  - `installments_show_finished`: "Show finished" / "Ver terminadas"
  - `installment_charged`: "Charged" / "Cobrada"
  - `installment_pending`: "Pending" / "Por cobrar"
  - `installment_delete_confirm`: "Delete this purchase and its %1$d installments? The card's debt drops by %2$s." / "¿Eliminar esta compra y sus %1$d cuotas? La deuda de la tarjeta baja %2$s."

- [ ] **Step 1: Write the failing tests:**
  - `AccountDetailViewModelTest`, `a card lists its unfinished plans first`.
  - `InstallmentPlanDetailViewModelTest`, `marks installments dated up to today as charged`.
  - `InstallmentPlanDetailViewModelTest`, `save propagates description and category`.
  - `InstallmentPlanDetailViewModelTest`, `delete reports the charged amount`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.ui.screens.installments.InstallmentPlanDetailViewModelTest --tests com.boxdev.boxwallet.ui.screens.accounts.AccountDetailViewModelTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the ViewModels, the card section under the statement in the card's detail, the plan detail screen (screen or bottom sheet is the builder's call, per spec §9), the route and the tap branches.
- [ ] **Step 4: Run** it again, then `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: show a card's installment purchases`.

### Task 10: Emulator check

- [ ] **Step 1: Prepare the emulator.** On emulator-5554, install `assembleDebug`. Create a GTQ card with closing day 15, due day 5 and a limit of Q5,000.
- [ ] **Step 2: Buy in installments.** Record a Q3,000 expense on it, "A cuotas", 3 installments, dated today. Check:
  - only today's installment shows in Movements;
  - the card shows "Cuotas por cobrar";
  - available = Q5,000 − Q3,000;
  - Home's monthly expense rose by one installment only.
- [ ] **Step 3: Edit the plan.** Open the plan from the card and from the listed installment, and rename it. Then change the closing day and check that the pending installments moved.
- [ ] **Step 4: Delete the plan.** Check that the debt and available are restored.
