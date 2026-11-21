# Multi-account PR 2 — Transfers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move money between two accounts, in the same or different currencies, without it ever counting as income or expense.

**Architecture:** A transfer is two `transactions` rows (`TRANSFER_OUT` in the source account, `TRANSFER_IN` in the destination) sharing a UUID `transferId`. The columns were already added by `MIGRATION_1_2` in PR 1, so there is **no schema change**. A new `TransferRepository` writes, edits and deletes both rows in one Room transaction under `MovementWriteGuard`. A new `TransferForm` route provides the UI, and the lists show one row per transfer.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 Expressive, Room, Hilt, JUnit4 + Robolectric (sdk 34).

**Spec:** `docs/superpowers/specs/2026-11-20-multi-account-design.md` (§3.2, §5.2, §6 "Listas", §7, §9, §10, §11 PR 2)

Branch `feat/transfers` from `main` at `10ef7df` (PR 1 merged as box-bm/BoxWallet#34). PR title: `feat: transfer money between your accounts, in any currency`.

## Global Constraints

- Money is `Long` minor units; conversions use `Money.convert` (`HALF_EVEN`). Derived rates use `MathContext.DECIMAL64`.
- **`TRANSFER_OUT` row** in A:
  - `original` = sent amount (currency a).
  - `accountAmount` = sent, with `accountRate` = 1.
  - `amountMinor` = sent converted to the main currency (normal main leg).
- **`TRANSFER_IN` row** in B:
  - `original` = sent amount (currency a), and `accountAmount` = received (currency b).
  - `accountRate`: AUTO/APPROXIMATE from `ExchangeRateResolver` for the transfer's date (a → b). When the user edits the received amount, the rate is received/sent and `MANUAL`.
  - `amountMinor` = received converted to the main currency.
- **Both rows:** same `date`, same `transferId`, `categoryId = NULL`, `isCleared = true`, no reminder, no recurrence, `description` = the note.
- Write both rows inside one `database.withTransaction` that runs inside `MovementWriteGuard.write`. Editing or deleting always acts on both rows by `transferId`.
- Source ≠ destination, and both accounts must be non-archived. On edit, an account that was archived after the transfer was made stays allowed only if it is unchanged.
- `TransactionType.isInflow` includes `TRANSFER_IN`; `countsInReports` stays exactly `INCOME`/`EXPENSE`; new `isTransfer` = `TRANSFER_IN`/`TRANSFER_OUT`.
- `MainCurrencyChanger` recomputes only the main leg of transfer rows. It never changes the `accountRate` of `TRANSFER_IN`. This already holds for the multi-account branch, so only a test is needed.
- **Lists:**
  - Without an account filter (Movements, Search, Home "latest"), show only the `TRANSFER_OUT` row, titled "<from> → <to>".
  - With an account filter (Movements filter, account detail), show that account's own row.
  - Color is neutral (like adjustments), with the `Icons.Outlined.SwapHoriz` icon. No category.
- Someone with a single active account never sees "Transfer" (FAB item and account-detail action hidden).
- User-facing copy exists in EN (`values/strings.xml`) and ES (`values-es/strings.xml`).
- Don't edit `version.properties`; tests sit beside the package they cover (`app/src/test/java/<same package>/<Class>Test.kt`).

## Review Focus

1. **Editing only the sent amount of a transfer whose received amount was typed (MANUAL).** The manual rate is kept and the received amount is recomputed from it, the same as the movement form. Test in Task 2.
2. **Editing a transfer so the destination becomes an account in another currency.** The rate is resolved afresh and is no longer MANUAL. Test in Task 2.
3. **Deleting a transfer from either row** (the form opened from the IN row in the destination's detail) deletes both rows. `TransactionRepository.deleteTransaction` on a leg deletes the pair. Test in Task 2.
4. **A backup where one row of a transfer is missing** (hand-edited or older partial data). The lone row becomes an `ADJUSTMENT_IN/OUT` with `transferId = null`, so the balance is kept and nothing renders as a broken transfer. Test in Task 3.
5. **The main currency changes while transfers exist.** Both rows get new main legs, and the IN row's `accountRate`, `accountRateSource` and `accountAmountMinor` are unchanged. Test in Task 3.

---

### Task 1: Transfer types and balances

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/entity/TransactionEntity.kt` (enum and helpers)
- Modify: `app/src/main/java/com/boxdev/boxwallet/domain/model/Transaction.kt` (field and both mappers)
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/dao/AccountDao.kt` (`CLEARED_BALANCE`)
- Modify: `app/src/main/java/com/boxdev/boxwallet/util/ExcelReportGenerator.kt` (Type cell)
- Modify: every other exhaustive `when (type)` that stops compiling
- Test: `app/src/test/java/com/boxdev/boxwallet/data/local/entity/TransactionTypeTest.kt`, `.../data/repository/AccountRepositoryTest.kt`, `.../util/ExcelReportGeneratorTest.kt`

**Interfaces:**
- Produces:
  - `TransactionType.TRANSFER_OUT` and `TransactionType.TRANSFER_IN`.
  - `val TransactionType.isTransfer: Boolean`.
  - `Transaction.transferId: String? = null`, round-tripped by `toDomain()`/`toEntity()`.

- [ ] **Step 1: Write failing tests**
  - `TransactionTypeTest.transferHelpers`:
    - `TRANSFER_IN.isInflow` is true and `TRANSFER_OUT.isInflow` is false.
    - Both have `isTransfer` true and `countsInReports` false.
    - `INCOME.isTransfer` is false.
  - `TransactionTypeTest.transferIdRoundTrips`: a `Transaction` with `transferId = "t-1"` keeps it through `toEntity().toDomain(usd, usd)`.
  - `AccountRepositoryTest.balanceCountsTransferLegs`: a USD account with cleared `INCOME` 100.00, `TRANSFER_OUT` 30.00 and `TRANSFER_IN` 5.00 (accountAmount) has a balance of 75.00 (`7500`).
  - `ExcelReportGeneratorTest.transfersAreListedButNotTotaled`: one INCOME 100 plus a transfer pair. The Movements sheet has 3 rows, with Type "Transfer" on both legs. Summary income is 100 and expense is 0.
- [ ] **Step 2: Run them and see them fail.** Run `./gradlew testDebugUnitTest --tests '*TransactionTypeTest' --tests '*AccountRepositoryTest' --tests '*ExcelReportGeneratorTest'`. Expected: compile failure (`TRANSFER_IN` unresolved).
- [ ] **Step 3: Implement**
  - Add the enum values and update `isInflow`. Add `isTransfer`, plus `transferId` on `Transaction` and in both mappers.
  - `CLEARED_BALANCE` inflow list becomes `('INCOME','ADJUSTMENT_IN','TRANSFER_IN')`.
  - The Excel Type cell maps `TRANSFER_IN`/`TRANSFER_OUT` to `"Transfer"`.
  - For other `when`s that fail to compile, map transfers the same way as adjustments, and leave UI presentation to Task 4.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: add transfer movement types`

### Task 2: TransferRepository

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/model/Transfer.kt`
- Create: `app/src/main/java/com/boxdev/boxwallet/data/repository/TransferRepository.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/repository/TransactionRepository.kt` (`deleteTransaction`, `updateTransaction`)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/TransferRepositoryTest.kt` (Robolectric with an in-memory DB, set up like `TransactionRepositoryTest`)

**Interfaces:**
- Consumes: Task 1 types, `MovementWriteGuard.write`, `TransactionAmounts.forSave`, `BoxWalletDatabase.withTransaction`.
- Produces:
  - `data class TransferDraft(val fromAccountId: Long, val toAccountId: Long, val sent: Money, val date: LocalDateTime, val note: String = "", val manualRate: BigDecimal? = null)`. `manualRate` is the sent → received rate typed through the received amount; it is ignored when the currencies match.
  - `data class Transfer(val id: String, val fromAccountId: Long, val toAccountId: Long, val sent: Money, val received: Money, val rate: ExchangeRate, val date: LocalDateTime, val note: String, val outLegId: Long, val inLegId: Long)`.
  - `data class TransferPair(val transferId: String, val fromAccountId: Long, val toAccountId: Long)`, a Room POJO.
  - `TransferRepository`:
    - `suspend fun create(draft: TransferDraft): String` returns the transferId.
    - `suspend fun update(transferId: String, draft: TransferDraft)`.
    - `suspend fun delete(transferId: String)`.
    - `suspend fun get(transferId: String): Transfer?`.
    - `suspend fun getByLegId(transactionId: Long): Transfer?`.
    - `fun observePairs(): Flow<Map<String, TransferPair>>`.
    - Throws `IllegalArgumentException` for same, archived or missing accounts (see Global Constraints) and for `sent.minor <= 0`. Throws `MissingRateException` when no rate is known and none was typed.
  - DAO additions:
    - `getByTransferId(transferId: String): List<TransactionEntity>`.
    - `deleteByTransferId(transferId: String)`.
    - `observeTransferPairs(): Flow<List<TransferPair>>`, a self-join of OUT and IN rows on `transferId`.
  - `TransactionRepository.deleteTransaction(id)` deletes both rows when the row has a `transferId`. `updateTransaction` throws `IllegalArgumentException` for a transfer row.

- [ ] **Step 1: Write failing tests.** Fixture: main currency GTQ, accounts "Banco" (GTQ) and "Dólares" (USD). The lookup gives GTQ = 7.64 per USD on 2026-11-05.
  - `sameCurrencyTransferWritesTwoIdentityLegs`: a second GTQ account, sending Q 500.00. Both rows have amount 50000 in every leg, `accountRate` 1, the same `transferId`, `isCleared`, and `categoryId` null. Banco's balance goes down by 50000 and the other account's goes up by 50000.
  - `crossCurrencyTransferUsesRateOfItsDate`: sending Q 771.00 to USD.
    - OUT: `original` 77100 GTQ, `accountAmount` 77100, `amountMinor` 77100.
    - IN: `original` 77100 GTQ, `accountAmount` 10092 USD, `accountRateSource` AUTO, `amountMinor` 77103.
  - `typedReceivedAmountIsManual`: the same transfer with `manualRate = 100/771` (DECIMAL64). IN `accountAmount` is 10000, `accountRateSource` MANUAL, and `amountMinor` is 76400.
  - `editingSentKeepsManualRate`: the manual transfer above updated to sent Q 800.00 with the same `manualRate`. IN `accountAmount` = `Money(80000, GTQ).convert(USD, rate)` and stays MANUAL (Review Focus 1).
  - `changingDestinationCurrencyRecomputesRate`: the manual transfer updated to a third account in EUR (lookup EUR for that day), with `manualRate = null`. The IN rate is AUTO and there is still exactly one IN row (Review Focus 2).
  - `deleteFromEitherLegDeletesBoth`: `TransactionRepository.deleteTransaction(inLegId)` leaves 0 rows with that `transferId` (Review Focus 3).
  - `rejectsSameOrArchivedAccount`: from == to throws; to an archived account throws; nothing is written.
  - `transfersDoNotChangeFinancialSummary`: `getFinancialSummary()` is the same before and after a transfer.
  - `updateTransactionRejectsTransferLeg`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*TransferRepositoryTest'`. Expected: compile failure.
- [ ] **Step 3: Implement**
  - Build each row as a `Transaction` draft and freeze its amounts with `TransactionAmounts.forSave(draft, previousLeg, accountCurrency, main, manualAccountRate)`:
    - The OUT row has `manualAccountRate = null` (same currency, identity).
    - The IN row uses `draft.manualRate`.
  - Use `current.accountCurrency(...)` and `current.main` inside the guard. `forSave` returning null throws `MissingRateException`.
  - Run `update` as delete plus re-insert of both rows, reusing their ids and the `transferId`, so the pair is always rewritten together.
  - Generate `transferId` with `UUID.randomUUID().toString()`.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: save, edit and delete transfers as one pair`

### Task 3: Backup and currency change keep transfers whole

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/repository/BackupRepository.kt` (`sanitized`)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/BackupRepositoryTest.kt`, `.../data/rates/MainCurrencyChangerTest.kt`

**Interfaces:**
- Consumes: Task 1 types. `BackupData` already carries `TransactionEntity.transferId`, so the version stays 4.

- [ ] **Step 1: Write failing tests**
  - `BackupRepositoryTest.transferPairRoundTrips`: snapshot → restore keeps both rows with the same `transferId`, types and amounts.
  - `BackupRepositoryTest.loneTransferLegBecomesAdjustment`: a backup with only a `TRANSFER_OUT` row restores it as `ADJUSTMENT_OUT` with `transferId == null` and the same amounts. A lone `TRANSFER_IN` becomes `ADJUSTMENT_IN` (Review Focus 4).
  - `MainCurrencyChangerTest.transferInKeepsAccountLeg`: GTQ and USD accounts with a GTQ → USD transfer, then main GTQ → USD. The IN row's `accountRate`, `accountRateSource` and `accountAmountMinor` are unchanged, and both main legs are recomputed (Review Focus 5).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*BackupRepositoryTest' --tests '*MainCurrencyChangerTest'`. Expected: `loneTransferLegBecomesAdjustment` FAILS. The other two may already pass and stay as regression tests.
- [ ] **Step 3: Implement.** In `sanitized`, group transactions by `transferId`. A group that isn't exactly one OUT plus one IN has each of its rows converted to the matching adjustment type with `transferId = null`.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** `fix: restore a transfer missing one side as a balance adjustment`

### Task 4: One row per transfer in the lists

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/components/TransferLabels.kt`
- Modify:
  - `app/src/main/java/com/boxdev/boxwallet/ui/components/TransactionItem.kt`
  - `.../ui/screens/transactions/TransactionsListViewModel.kt` and `TransactionsListScreen.kt`
  - `.../ui/screens/search/SearchViewModel.kt` and `SearchScreen.kt`
  - `.../ui/screens/home/HomeScreen.kt`, and `HomeViewModel.kt` if it feeds the latest list
  - `.../ui/screens/accounts/AccountDetailScreen.kt`
  - `.../data/local/dao/TransactionDao.kt` (`getLatestTransactions` adds `AND type != 'TRANSFER_IN'`)
  - `res/values/strings.xml` and `res/values-es/strings.xml`
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/transactions/TransactionsListViewModelTest.kt`, `.../ui/components/TransferLabelsTest.kt`

**Interfaces:**
- Consumes: `TransferRepository.observePairs()` and `Transaction.transferId`/`isTransfer`.
- Produces:
  - `fun transferTitle(pair: TransferPair?, accounts: Map<Long, Account>): String?` returns `"<from> → <to>"`, or null when either account is unknown.
  - `TransactionItem` gains `transferTitle: String? = null` and `signedTransfer: Boolean = false`. For transfer rows:
    - The title is `description.ifBlank { transferTitle ?: R.string.transaction_transfer }`.
    - The badge uses the neutral adjustment colors with `Icons.Outlined.SwapHoriz`.
    - The amount is `accountAmount` in `onSurfaceVariant`. It carries a +/− sign only when `signedTransfer` is true, which screens set when the list is scoped to one account.
  - Every screen that opens a tapped movement routes `tx.type.isTransfer` to a new `onOpenTransfer(transactionId: Long)` callback instead of the movement form. That covers Home, Movements (both panes), Search and Account detail. Task 5 wires the callback to the `TransferForm` route.

- [ ] **Step 1: Write failing tests**
  - `TransactionsListViewModelTest.globalListShowsOneRowPerTransfer`: with a transfer pair and no account filter, `filteredTransactions` holds only the OUT row.
  - `TransactionsListViewModelTest.accountFilterShowsThatAccountsLeg`: filtering on the destination returns the IN row.
  - `TransactionsListViewModelTest.typeFiltersExcludeTransfers`: the INCOME and EXPENSE filters return no transfer rows.
  - `TransferLabelsTest`: names resolve to `"Banco → Dólares"`, and null comes back for an unknown account.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*TransactionsListViewModelTest' --tests '*TransferLabelsTest'`. Expected: FAIL.
- [ ] **Step 3: Implement the filtering**
  - `applyFilters`: when `accountId == null`, drop `TRANSFER_IN`.
  - Search drops `TRANSFER_IN` and adds both account names to a transfer's search text.
- [ ] **Step 4: Implement the row presentation.** Add `TransactionItem` presentation and the routing, plus strings `transaction_transfer` ("Transfer" / "Transferencia").
- [ ] **Step 5: Run the same command.** Expected: PASS. Also run `./gradlew compileDebugKotlin`, which should give BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `feat: show each transfer once, between its two accounts`

### Task 5: Transfer form, route and entry points

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/screens/transfer/TransferFormState.kt`, `TransferFormViewModel.kt` and `TransferFormScreen.kt`
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/TransferAvailabilityViewModel.kt`
- Modify:
  - `.../ui/navigation/NavRoutes.kt`, `NavGraph.kt` and `NavigationTab.kt`
  - `.../ui/screens/accounts/AccountDetailScreen.kt` (the `BalanceHero` action)
  - `.../data/repository/AccountRepository.kt`
  - strings in EN and ES
- Test:
  - `app/src/test/java/com/boxdev/boxwallet/ui/screens/transfer/TransferFormStateTest.kt`
  - `.../ui/screens/transfer/TransferFormViewModelTest.kt`, set up like `TransactionFormViewModelTest`
  - `.../ui/navigation/NavRoutesTest.kt`

**Interfaces:**
- Consumes: Task 2 `TransferRepository`, Task 4 `onOpenTransfer`, and `ConversionRow` (`ui/components/ConversionRow.kt`) as-is.
- Produces:
  - Route: `NavRoutes.TransferForm` = `"transfer_form?id={id}&from={from}&to={to}&amount={amount}"`.
    - `createRoute(id: Long? = null, from: Long? = null, to: Long? = null, amount: String? = null)` leaves out null arguments.
    - `id` is either leg's transaction id. `amount` is plain major units in the source currency.
    - PR 3's "Pay card" button builds this route.
  - `AccountRepository.observeActiveCount(): Flow<Int>` counts non-archived accounts.
  - `TransferFormState`:
    - Fields: `fromAccountId`, `toAccountId`, `amount: String`, `date`, `note`, `manualRate: String?`, `equivalent: String?`, `rate: ExchangeRate?`, `needsManualRate`, `accounts`, `isEditing` and `error`.
    - `withManualRate` and `withEquivalent` work exactly like `TransactionFormState`'s, with the destination currency as the target. Moving those two into a shared function is fine.
    - `isValid` requires from ≠ to, amount > 0, and a manual rate when `needsManualRate`.
- **Form layout, top to bottom (spec §3.2):**
  - "From" and "To" account pickers, which reuse the account picker sheet of the movement form. Each option shows its balance, and a swap button sits between them.
  - Amount in the source currency.
  - `ConversionRow` only when the currencies differ, where the received amount is editable.
  - Date and note.
  - When editing, a Delete action with a confirmation, which deletes both rows.
  - Defaults: From = the `from` argument, else the default account. To = the `to` argument, else the first other active account.
- **Entry points:**
  - A third FAB item, "Transfer" (`Icons.Outlined.SwapHoriz`), goes in both the bottom-bar `FloatingActionButtonMenu` and the rail `DropdownMenu`. It shows only when `TransferAvailabilityViewModel.canTransfer` (active count ≥ 2) is true.
  - Account detail gets a "Transfer" action next to Adjust/Edit, opening `createRoute(from = accountId)`, also hidden below 2 active accounts.
  - `onOpenTransfer(id)` from Task 4 opens `createRoute(id = id)`. In Movements' two-pane layout it navigates to the route too, instead of opening in the detail pane.

- [ ] **Step 1: Write failing tests**
  - `TransferFormStateTest.equivalentSetsManualRate`: amount "771", GTQ → USD, `withEquivalent("100")` gives `manualRate` = `100/771` in DECIMAL64.
  - `TransferFormStateTest.sameAccountIsInvalid` and `zeroAmountIsInvalid`.
  - `TransferFormViewModelTest.saveCreatesPair`: after `save()`, two rows exist and `isSaved` is true.
  - `TransferFormViewModelTest.openByInLegLoadsTransfer`: `open(inLegId)` fills from, to, amount and note from the pair.
  - `TransferFormViewModelTest.prefillFromArguments`: `from`, `to` and `amount = "250.00"` show in the state.
  - `NavRoutesTest.transferFormRoute`:
    - `createRoute()` returns `"transfer_form"`.
    - `createRoute(from = 2, amount = "10.50")` returns `"transfer_form?from=2&amount=10.50"`.
    - `createRoute(id = 7)` returns `"transfer_form?id=7"`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*TransferForm*' --tests '*NavRoutesTest'`. Expected: FAIL.
- [ ] **Step 3: Implement the state, the VM and the route.** The VM maps `IllegalArgumentException`/`MissingRateException` to `error` and never crashes.
- [ ] **Step 4: Implement the screen and entry points.** Build the screen with `FullScreenForm` like the movement form, then wire the entry points.
  - EN/ES strings: `fab_add_transfer` ("Transfer" / "Transferencia"), `transfer_from` ("From" / "Desde"), `transfer_to` ("To" / "Hacia"), `transfer_swap`, `transfer_delete_title`, `transfer_delete_body` ("Both accounts will be updated." / "Se actualizarán las dos cuentas."), `account_action_transfer`, `transfer_error_same_account`.
- [ ] **Step 5: Run** `./gradlew testDebugUnitTest`. Expected: all tests pass.
- [ ] **Step 6: Commit** `feat: transfer form with conversion between currencies`

---

**Manual check after Task 5** (emulator; spec §11, without cards):
- Create a USD account and transfer Q 771 from GTQ to USD. Check that the conversion row shows ~$100.92, edit it to $100, and save.
- Both balances change. Home income and expense and Analytics don't change.
- Movements shows one row, "Banco → Dólares". The USD account's detail shows +$100.00, and tapping it opens the transfer. Deleting it restores both balances.
- With only one account, there's no Transfer item in the FAB.
