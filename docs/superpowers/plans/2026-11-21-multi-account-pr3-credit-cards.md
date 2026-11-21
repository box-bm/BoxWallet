# Multi-account PR 3 — Credit Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An account can be marked as a credit card. A card shows what you've used, what's available, and how much to pay this statement and by when. A card can also carry a second currency with one shared limit, it can be paid with one tap, and it can remind you before the due date.

**Architecture:**
- Everything lives in the existing `accounts`/`transactions` tables. The card columns and `parentAccountId` were added by PR 1's migration, so there is **no schema change**.
- The statement math is a pure Kotlin function in `domain` with no Android dependencies.
- A dual-currency card is a parent `CREDIT_CARD` account plus one child account in the second currency, linked by `parentAccountId`.
- Paying a card is a PR 2 transfer opened through the existing `TransferForm` route.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 Expressive, Room, Hilt, WorkManager, JUnit4 + Robolectric (sdk 34).

**Spec:** `docs/superpowers/specs/2026-11-20-multi-account-design.md` (§3.1 card switch, §3.3, §3.4, §3.5, §5.1, §6 card rule, §8, §8.5, §9, §11 PR 3)

Branch `feat/credit-cards` from `main` at `1252aa8`, which contains PR 1 (box-bm/BoxWallet#34) and PR 2 (box-bm/BoxWallet#35). PR title: `feat: credit cards with statement, due date and two currencies`.

## Global Constraints

- **Card balance rule (§6).** For a `CREDIT_CARD` account, and for its child, `isCleared` is ignored. The balance counts every movement dated on or before today. In storage that is `date < start of tomorrow` (local wall-clock stored as UTC millis).
  - Debt = −balance.
  - Other account types keep the existing cleared-only rule.
- **Used and available.**
  - Used = max(0, debt).
  - Available = limit − used, and is shown only when there is a limit.
  - A positive card balance shows as "Saldo a favor" / "Credit balance".
- **Statement math (§8).** `C` is the closing day and `D` the due day. A day larger than the month's length clamps to that month's last day.
  - `Lc` = the latest date with day `C` that is ≤ today.
  - `S` = debt at the end of `Lc`, counting movements dated ≤ `Lc`.
  - `P` = the sum of inflows (`INCOME`, `ADJUSTMENT_IN`, `TRANSFER_IN`) dated after `Lc` and ≤ today.
  - To pay = max(0, `S` − `P`).
  - Due date = the first date with day `D` strictly after `Lc`.
  - If `S` = 0 and there is no movement dated ≤ `Lc`, it is "no statement yet".
  - Without both `C` and `D`, show only used and available.
- **Dual currency (§3.4).**
  - At most one child per card, and children never have children.
  - The child's currency is different from the parent's.
  - The child's card fields are NULL.
  - Name, icon, color and `includeInTotal` come from the parent. The repository keeps the child's `includeInTotal` and `isArchived` equal to the parent's.
  - Available = limit − used₁ − used₂ × today's rate (currency 2 → currency 1), using the offline resolver as `observeTotalBalance` does. If there is no rate, available is null.
  - The same `Lc` and due date apply to both currencies. "To pay" is computed for each one.
- **Children are not stand-alone accounts.**
  - They never appear in the accounts list, the Home row or the Movements account filter.
  - Movement and transfer account pickers show the child as its own option labelled `"<parent name> · <currency symbol>"` (for example "Visa BAC · US$").
  - A card's detail and Movements filtered by the card show the movements of both the parent and the child.
- **Opening debt (§3.5).** The form's "Deuda actual" / "Current debt" becomes an `ADJUSTMENT_OUT`. It is dated at the end of `Lc` when `C` is set, otherwise now. The second-currency debt uses the same date.
- **Switching (§3.1).** Turning the card switch off requires removing the second currency first. The second currency can be removed only when the child has no movements besides adjustments; its adjustments go with it.
- Strings exist in EN (`values/strings.xml`) and ES (`values-es/strings.xml`). Use the copy given in the tasks verbatim.
- Don't edit `version.properties`. Tests sit beside the package they cover.

## Review Focus

1. **A closing day of 29–31 in February and in 30-day months.** `Lc` and the due date clamp to the month's last day, both inside the statement logic and across a year boundary. Tests are in Task 1.
2. **A purchase that is not marked "Liquidado", or one dated in the future, on a card.** The first counts as used. The second doesn't count until its date. Test in Task 1 (SQL) and Task 2.
3. **Paying more than the statement.** "To pay" is 0 ("Corte pagado ✓"), and when the debt goes below zero the card shows a credit balance. The excess never makes "to pay" negative. Test in Task 1.
4. **A USD expense on a dual-currency card.** It lands on the USD child; a GTQ or EUR expense lands on the parent. The total balance and the card's available amount both include the child. Tests in Task 2 and Task 4.
5. **The reminder.** It fires at most once per statement, not before N days, not when "to pay" is 0, and never for archived cards. Test in Task 6.

---

### Task 1: Card balance rule and statement math

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/model/CreditCardCycle.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/dao/AccountDao.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/model/CreditCardCycleTest.kt`, `.../data/repository/AccountRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `data class CardMovement(val date: LocalDate, val signedMinor: Long, val isInflow: Boolean)`. Here `signedMinor` is + for an inflow and − for an outflow, in the account's currency.
  - `fun lastClosingDate(closingDay: Int, today: LocalDate): LocalDate`
  - `fun dueDateAfter(lastClosing: LocalDate, dueDay: Int): LocalDate`
  - `data class StatementStatus(val lastClosing: LocalDate, val dueDate: LocalDate, val statementDebtMinor: Long, val paidSinceMinor: Long, val toPayMinor: Long, val noStatementYet: Boolean)`
  - `fun statementStatus(movements: List<CardMovement>, closingDay: Int, dueDay: Int, today: LocalDate): StatementStatus`
  - `fun clampDay(year: Int, month: Int, day: Int): LocalDate`
  - In the DAO, `observeBalances(endOfToday: Long)` and `getBalance(accountId, endOfToday: Long)` now apply the card rule. A card is any account whose `type = 'CREDIT_CARD'`, which includes children.

- [ ] **Step 1: Write failing tests** in `CreditCardCycleTest`. Today is 2026-11-21 unless noted.
  - `lastClosingSameMonth`: C=15 gives Lc 2026-11-15.
  - `lastClosingPreviousMonth`: C=25 gives Lc 2026-10-25.
  - `lastClosingClampsFebruary`: C=31 with today 2027-03-10 gives 2027-02-28. C=29 with today 2028-02-29 gives 2028-02-29.
  - `lastClosingAcrossYear`: C=28 with today 2027-01-05 gives 2026-12-28.
  - `dueNextMonthWhenDueDayNotAfterClosing`: Lc 2026-11-15 and D=5 give 2026-12-05.
  - `dueSameMonthClamped`: Lc 2026-11-10 and D=31 give 2026-11-30.
  - `dueAcrossYear`: Lc 2026-12-28 and D=10 give 2027-01-10.
  - `toPayIsStatementMinusPaymentsSince`:
    - Movements: EXPENSE −50000 on 11-10, EXPENSE −30000 on 11-18, TRANSFER_IN +20000 on 11-19.
    - With C=15 and D=5: `statementDebtMinor` 50000, `paidSinceMinor` 20000, `toPayMinor` 30000, `noStatementYet` false.
  - `overpaidStatementIsZero`: a payment of +60000 after Lc gives `toPayMinor` 0.
  - `noStatementYetWhenNothingBeforeClosing`: the only movement is on 11-18, so `noStatementYet` is true and `toPayMinor` is 0.
  - In `AccountRepositoryTest`, `cardBalanceIgnoresClearedButNotFuture`. A CREDIT_CARD account has:
    - an uncleared EXPENSE of 100.00 today, which counts;
    - a cleared EXPENSE of 50.00 tomorrow, which doesn't count;
    - an uncleared INCOME of 10.00 yesterday, which counts.
    - The balance is −9000. A CHECKING account with the same rows reads 0.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CreditCardCycleTest' --tests '*AccountRepositoryTest'`. Expected: FAIL (compile).
- [ ] **Step 3: Implement the statement math.** Write the pure functions. Callers derive `today` with `LocalDate.now()`.
- [ ] **Step 4: Implement the card rule in the DAO.** Its balance expression becomes a join on `accounts`. Card rows use `t.date < :endOfToday`; others use `t.isCleared = 1`.
  - Update both callers in `AccountRepository` to pass `endOfToday = LocalDate.now().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()`.
  - The balance flow recomputes on any table change. It doesn't need to tick at midnight.
- [ ] **Step 5: Run the same command.** Expected: PASS.
- [ ] **Step 6: Run the full suite** `./gradlew testDebugUnitTest`. Expected: all pass.
- [ ] **Step 7: Commit** `feat: card balances and statement math`.

### Task 2: Card accounts and the second-currency child in the repository

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/repository/AccountRepository.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/repository/BackupRepository.kt` (`sanitized`)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/AccountRepositoryTest.kt`, `.../BackupRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1's `lastClosingDate`.
- Produces in `AccountRepository`:
  - `suspend fun create(account: Account, currentBalance: Money?, second: SecondCurrencyInput? = null): Long`
    - `data class SecondCurrencyInput(val currency: Currency, val currentBalance: Money?)`.
    - For a card, `currentBalance` is a signed balance: a debt is negative, and the form negates what the user typed.
  - `suspend fun update(account: Account, second: SecondCurrencyChange = SecondCurrencyChange.Keep)`
    - `sealed interface SecondCurrencyChange { Keep; data class Add(val input: SecondCurrencyInput); Remove }`.
  - `fun secondCurrencyOf(cardId: Long): Flow<Account?>` returns the child.
  - `AccountRuleError` gains `SecondCurrencyHasMovements` and `RemoveSecondCurrencyFirst`.
  - Validation throws `IllegalArgumentException` when:
    - a day is outside 1–31;
    - a limit is ≤ 0;
    - the second currency equals the card's currency;
    - `Add` targets a card that already has a child;
    - `Add` or `create(second=…)` is used on a non-card.
  - Opening adjustments: `insertAdjustment` gains a `date: LocalDateTime` parameter. For a card with `C` set, that date is `lastClosingDate(C, today).atTime(23, 59, 59)`; otherwise it is now.
  - The parent's `update` and `archive` copy `includeInTotal` and `isArchived` to the child.
  - `delete(card)` refuses while the child has non-adjustment movements, and otherwise deletes the child and its adjustments with the parent.
  - `adjustBalance` works on a child id too, using the card rule from Task 1.

- [ ] **Step 1: Write failing tests** in `AccountRepositoryTest`:
  - `createCardWithDebtDatesAdjustmentAtLastClosing`: a card with C=15, today's real date, and debt 1980.00 GTQ. The adjustment is ADJUSTMENT_OUT 198000 dated `lastClosingDate(15, today)` 23:59:59.
  - `createCardWithoutClosingDatesAdjustmentNow`: the adjustment is dated today.
  - `createDualCurrencyCardCreatesChild`: a GTQ card with second currency USD and debt 50.00. The result is one child with `parentAccountId` = card, type CREDIT_CARD, USD, null card fields, the same `includeInTotal`, and an ADJUSTMENT_OUT 5000 on the same date as the parent's adjustment.
  - `secondCurrencyCannotEqualCardCurrency`, `onlyOneChild` and `secondCurrencyOnlyOnCards` all throw.
  - `archiveAndIncludeInTotalCascadeToChild`.
  - `removeSecondCurrencyRefusedWithMovements`: an EXPENSE on the child makes `Remove` throw `SecondCurrencyHasMovements`. With only adjustments, `Remove` deletes the child and its adjustments.
  - `turningCardOffRequiresRemovingSecondCurrency`: updating `type` to CHECKING while a child exists throws `RemoveSecondCurrencyFirst`.
  - `totalBalanceIncludesChild`: the total converts the child's balance like any other account (Review Focus 4).
  - In `BackupRepositoryTest`:
    - `childWithMissingParentBecomesPlainCard`: a restored child whose parent is absent gets `parentAccountId = null`.
    - `secondChildIsDetached`: a second child of the same card is detached the same way.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*AccountRepositoryTest' --tests '*BackupRepositoryTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Every multi-row change runs inside the existing `guard.write { database.withTransaction { … } }` shape.
- [ ] **Step 4: Run the same command, then the full suite.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: credit card accounts with an optional second currency`.

### Task 3: Card summary

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/model/CardSummary.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/repository/AccountRepository.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/dao/TransactionDao.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/model/CardSummaryTest.kt`, `.../data/repository/AccountRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1's functions and Task 2's parent/child model.
- Produces:
  - `data class CardBalance(val account: Account, val balance: Money, val usedMinor: Long, val statement: StatementStatus?)`. `statement` is null without both `C` and `D`.
  - `data class CardSummary(val card: Account, val primary: CardBalance, val second: CardBalance?, val limit: Money?, val available: Money?, val availableApproximate: Boolean)`
  - `fun cardSummary(card: Account, primary: CardBalance, second: CardBalance?, rateSecondToPrimary: BigDecimal?, approximate: Boolean): CardSummary`. This is pure: available = limit − used₁ − used₂ × rate, rounded `HALF_EVEN`, and null when there is no limit or when there is a child but no rate.
  - `AccountRepository.observeCardSummaries(): Flow<Map<Long, CardSummary>>`, keyed by the parent card id, for non-child cards.
    - It reads each card's movements through `TransactionDao.observeCardMovements(endOfToday: Long): Flow<List<CardMovementRow>>`.
    - `CardMovementRow(accountId, date, type, accountAmountMinor)` covers every row of a `CREDIT_CARD` account dated before `endOfToday`, installments included.
    - The rate comes from the offline resolver for today.
  - `TransactionDao.lastPayerAccountId(cardIds: List<Long>): Long?`. It finds the OUT-leg account of the newest `TRANSFER_IN` into any of `cardIds` whose OUT account isn't archived.

- [ ] **Step 1: Write failing tests**
  - `CardSummaryTest.availableSingleCurrency`: limit 10000.00, used 2500.00, so available is 7500.00.
  - `CardSummaryTest.availableDualCurrency`: limit Q 10000.00, used Q 2500.00, used US$ 100.00, and a rate of 7.64. Available is 750000 − 250000 − 76400 = 423600.
  - `CardSummaryTest.noRateMeansNoAvailable` and `noLimitMeansNoAvailable`.
  - `CardSummaryTest.creditBalanceUsesZero`: a balance of +50.00 gives `usedMinor` 0, and available equals the limit.
  - `AccountRepositoryTest.cardSummaryCombinesParentAndChild`: a dual card with C=15 and D=5, and movements on both balances. Each `CardBalance.statement.toPayMinor` matches `statementStatus` for that balance's rows.
  - `AccountRepositoryTest.lastPayerIsNewestPaymentSource`: two payments come from different accounts, and the newest one's source is returned. When that source is archived, the older one is returned.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CardSummaryTest' --tests '*AccountRepositoryTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the same command, then the full suite.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: card used, available and statement summary`.

### Task 4: Card form and the child in pickers and lists

**Files:**
- Modify:
  - `app/src/main/java/com/boxdev/boxwallet/ui/screens/accounts/AccountFormState.kt`, `AccountFormViewModel.kt` and `AccountFormScreen.kt`
  - `.../ui/screens/accounts/AccountsScreen.kt` and `AccountsViewModel.kt`
  - `.../ui/screens/home/HomeAccounts.kt` and `HomeViewModel.kt`
  - `.../ui/screens/transaction/TransactionFormViewModel.kt` and `TransactionFormScreen.kt` (`AccountPickerSheet`)
  - `.../ui/screens/transfer/TransferFormViewModel.kt` and `TransferFormScreen.kt`
  - `.../ui/screens/transactions/TransactionsListScreen.kt` and `TransactionsListViewModel.kt`
  - strings in EN and ES
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/accounts/AccountFormStateTest.kt`, `.../ui/screens/transaction/TransactionFormViewModelTest.kt`, `.../ui/screens/transactions/TransactionsListViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2's `create`/`update` with `SecondCurrencyInput`/`SecondCurrencyChange`, and `secondCurrencyOf`.
- Produces:
  - New `AccountFormState` fields:
    - `isCard: Boolean`
    - `creditLimit: String`
    - `closingDay: String`
    - `dueDay: String`
    - `reminderDays: String = "3"`
    - `secondCurrency: Currency?`
    - `secondDebt: String`
    - `hasSecondCurrency: Boolean` (stored)
    - `secondCurrencyRemovable: Boolean`
  - Validation errors use the existing `@StringRes Int?` pattern:
    - `closingDayError`/`dueDayError`: `account_error_day` ("Enter a day from 1 to 31" / "Escribe un día del 1 al 31").
    - `limitError`: `account_error_limit` ("Enter a limit above zero" / "Escribe un límite mayor que cero").
  - When `isCard` is on, the balance field label becomes `account_current_debt` ("Current debt" / "Deuda actual"). What is typed is a debt, so `negativeBalance` is hidden and the value is negated on save.
  - `fun accountLabel(account: Account, parent: Account?): String` returns `"${parent.name} · ${currency symbol}"` for a child and the name otherwise. It lives in `ui/components/AccountLabels.kt`.
  - Movement-form routing (§3.4). When the selected account is a card with a child and the movement's currency equals the child's currency, save the movement to the child, and back to the parent for any other currency. The chip shows `accountLabel(...)`. Choosing the child in the picker keeps the child.
- **Form layout.** The "Tarjeta de crédito" / "Credit card" switch comes after "Sumar al saldo total". When it's on, it reveals:
  - Limit, in the card currency.
  - Closing day and Due day, as numeric fields.
  - "Remind me N days before the due date" / "Avisarme N días antes del pago". An empty value means no reminder.
  - "Second currency" / "Segunda moneda", which opens the existing currency picker and excludes the card currency. Once it's set, a "Current debt in US$" / "Deuda actual en US$" field appears, using the currency symbol. Removing it is disabled with a hint when `!secondCurrencyRemovable`.
  - Turning the switch off while a second currency exists shows the `RemoveSecondCurrencyFirst` message.
- **Lists:**
  - The accounts list, the Home row and the Movements account filter hide accounts with `parentAccountId != null`.
  - Movements filtered by a card includes the child's rows. AccountDetail does the same through Task 5.
  - Movement and transfer pickers list each child right after its parent, with `accountLabel`.

- [ ] **Step 1: Write failing tests**
  - `AccountFormStateTest.cardDayValidation`: "0", "32" and "a" give `account_error_day`; "31" gives null.
  - `AccountFormStateTest.cardLimitValidation`: "0" gives `account_error_limit`. An empty value gives null, because the limit is optional.
  - `AccountFormStateTest.cardDebtIsNegated`: `isCard` with balance "1980" saves `Money(-198000, GTQ)`.
  - `TransactionFormViewModelTest.usdExpenseOnDualCardGoesToChild` and `gtqExpenseOnDualCardStaysOnParent` (Review Focus 4).
  - `TransactionsListViewModelTest.cardFilterIncludesChildRows`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*AccountFormStateTest' --tests '*TransactionFormViewModelTest' --tests '*TransactionsListViewModelTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the full suite and `./gradlew compileDebugKotlin`.** Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat: credit card fields and second currency in the account form`.

### Task 5: Card detail, Home tile and "Pay"

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/components/CardBlock.kt`
- Modify:
  - `.../ui/screens/accounts/AccountDetailScreen.kt` and `AccountDetailViewModel.kt`
  - `.../ui/screens/home/HomeAccounts.kt`
  - `.../ui/screens/accounts/AccountsScreen.kt`
  - `.../ui/navigation/NavRoutes.kt` and `NavGraph.kt`
  - `.../ui/screens/transfer/TransferFormViewModel.kt`
  - strings in EN and ES
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/navigation/NavRoutesTest.kt`, `.../ui/screens/transfer/TransferFormViewModelTest.kt`, `.../ui/components/CardBlockTextTest.kt`

**Interfaces:**
- Consumes: Task 3's `observeCardSummaries()` and `lastPayerAccountId`, plus PR 2's `TransferForm` route.
- Produces:
  - Route change: `TransferForm` gains an optional `receive` argument, in plain destination-currency major units.
    - The route becomes `"transfer_form?id={id}&from={from}&to={to}&amount={amount}&receive={receive}"` and `createRoute(..., receive: String? = null)`.
    - When source and destination currencies match, `receive` fills the amount.
    - Otherwise the VM sets the amount to `receive` ÷ the resolved automatic rate, rounded `HALF_UP` to the source currency, so what arrives is at least the debt. The rate stays automatic, and the user can still edit the received amount.
  - `fun payRoute(card: Account, balance: CardBalance, payer: Long?): String`
    - It returns `createRoute(from = payer, to = balance.account.id, receive = toPay major as plain string)`.
    - With no statement, or when to-pay is 0, it leaves out `receive`.
    - `payer` comes from `lastPayerAccountId(listOf(card.id, child.id))`, falling back to the default account.
  - Pure text helpers for tests, in `CardBlock.kt`:
    - `fun statementLine(balance: CardBalance, formatter): String?` returns "Este corte: Q 1,980.00 · pagar antes del 5 dic" / "This statement: Q 1,980.00 · pay by Dec 5". It returns "Corte pagado ✓" / "Statement paid ✓" when to-pay is 0 and the statement exists, and null when there is no statement or it is `noStatementYet`.
    - Use string resources `card_statement_due` ("This statement: %1$s · pay by %2$s" / "Este corte: %1$s · pagar antes del %2$s"), `card_statement_paid`, `card_no_statement_yet` ("No statement yet" / "Aún no hay corte"), `card_used`, `card_available`, `card_credit_balance` ("Credit balance" / "Saldo a favor"), `card_pay_amount` ("Pay %1$s" / "Pagar %1$s") and `card_pay` ("Pay card" / "Pagar tarjeta").
- **UI:**
  - **AccountDetail for a card.** A `CardBlock` replaces the plain balance hero. It shows:
    - used and available, with a progress bar when there's a limit;
    - a credit balance when the balance is positive;
    - a statement line per currency;
    - a Pay button per currency ("Pay Q 1,980.00", or "Pay card" without a statement).
    - "Adjust balance" is offered per currency.
  - **Home row and accounts list.** Card tiles show used plus a thin usage bar when there is a limit.

- [ ] **Step 1: Write failing tests**
  - `NavRoutesTest.transferFormRouteWithReceive`: `createRoute(to = 3, receive = "1980.00")` gives `"transfer_form?to=3&receive=1980.00"`.
  - `TransferFormViewModelTest.receiveSameCurrencyFillsAmount`.
  - `TransferFormViewModelTest.receiveCrossCurrencyCoversDebt`: GTQ → USD child with receive "100.00" at 7.64 GTQ/USD. The amount is 764.00 and the equivalent shows 100.00.
  - `CardBlockTextTest.statementLineDueAndPaid` and `noStatementYetHasNoLine`.
  - `CardBlockTextTest.payRouteOmitsReceiveWhenNothingToPay`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*NavRoutesTest' --tests '*TransferFormViewModelTest' --tests '*CardBlockTextTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Follow `DESIGN.md` and the components in `Expressive.kt`.
- [ ] **Step 4: Run the full suite and `./gradlew compileDebugKotlin`.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: card detail with statement and one-tap payment`.

### Task 6: Due-date reminder

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/model/CardReminder.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/worker/ReminderWorker.kt`, and strings in EN and ES
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/model/CardReminderTest.kt`

**Interfaces:**
- Consumes: Task 3's `CardSummary`, `AppMetaDao.get/put`.
- Produces:
  - `fun cardReminderDue(summary: CardSummary, today: LocalDate, lastNotifiedClosing: LocalDate?): Boolean`. It is true when:
    - `card.dueReminderDaysBefore != null` and the card isn't archived;
    - the primary statement exists and isn't `noStatementYet`;
    - any currency's `toPayMinor > 0`;
    - `0 ≤ days(today → dueDate) ≤ N`;
    - `lastNotifiedClosing != statement.lastClosing`.
  - App-meta key: `"card_reminder_<cardId>"`, with the value `lastClosing.toString()`.
  - Notification text:
    - `card_reminder_text`: "%1$s: pay %2$s by %3$s" / "%1$s: paga %2$s antes del %3$s".
    - A dual-currency card joins both amounts with " + " (for example "Q 1,980.00 + US$ 25.00"), leaving out a currency with 0 to pay.
  - The worker calls this inside its existing 12-hour run, after `checkUpcomingReminders`. It uses the existing `showNotification` with a stable id per card (`(Int.MAX_VALUE - cardId).toInt()`).

- [ ] **Step 1: Write failing tests** in `CardReminderTest` (Review Focus 5). Fixture: due 2026-12-05, N=3, to-pay > 0.
  - `firesWithinWindow`: today 2026-12-02 is true.
  - `notBeforeWindow`: today 2026-12-01 is false.
  - `notAfterDue`: today 2026-12-06 is false.
  - `oncePerStatement`: true, then false with `lastNotifiedClosing` = the same Lc.
  - `notWhenNothingToPay`, `notWhenNoReminderDays` and `notWhenArchived`.
  - `secondCurrencyAloneTriggers`: primary to-pay is 0 and second is > 0, which is true.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CardReminderTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the function and the worker wiring.
- [ ] **Step 4: Run the full suite.** Expected: PASS.
- [ ] **Step 5: Commit** `feat: remind before a card's due date`.

---

**Manual check after Task 6** (emulator; spec §11):
1. Create a dual-currency card "Visa BAC": GTQ, limit 10,000, closing day 15, due day 5, second currency USD, debt Q 1,980 and US$ 25.
2. The card detail shows "This statement: Q 1,980.00 · pay by Dec 5" and "US$ 25.00", with used and available (available includes US$ 25 × rate).
3. Record a US$ 10 expense on the card. It lands in the USD balance and the card appears once in the accounts list.
4. Tap "Pay Q 1,980.00". The transfer form opens with To = Visa BAC and the amount filled. Save it: the statement shows "Statement paid ✓", and Home and Analytics income and expense are unchanged.
5. Pay the USD balance from the GTQ account. The cross-currency transfer covers US$ 25.
6. A plain account with a single currency still looks like before.
