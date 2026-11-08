# Material Layout, Top Search and Full-Screen Forms — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix tab navigation out of Settings, move BoxWallet to the M3 pane layout (surfaceContainer background, white panes, top search bar, free rotation, list-detail for Loans and Settings) and turn the movement/loan sheets into full-screen forms.

**Architecture:** Keep the custom navigation shell (`NavigationTab`), driven by a pure `appLayout(width, height)` function fed from `currentWindowAdaptiveInfo()`. Add `material3-adaptive` only for the list-detail screens. New shared components `AppPane` and `AppSearchBar`. Forms become NavHost destinations whose ViewModels read `id` from `SavedStateHandle`.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (BOM 2025.05.01, material3 1.5.0-alpha13, Expressive APIs), Navigation Compose 2.9, Hilt, JUnit4 + mockito-kotlin + kotlinx-coroutines-test + Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-08-material-layout-design.md`

## Global Constraints

- Three PRs, in order, each branched from an up-to-date `main` after the previous one merged:
  1. branch `fix/tab-settings-restore`, title `fix: tabs no longer reopen settings after leaving it` (Tasks 1)
  2. branch `feat/material-layout`, title `feat: material list-detail layout with top search bar` (Tasks 2–9)
  3. branch `feat/full-screen-forms`, title `feat: full-screen forms for movements and loans` (Tasks 10–13)
- Never commit to `main`; never touch `version.properties`; PR via `gh pr create --base main`.
- Breakpoints: compact < 600dp, medium 600–839dp, expanded ≥ 840dp, rail expanded ≥ 1200dp, compact height < 480dp.
- Margins 16dp (compact) / 24dp (medium+), spacer between panes 24dp, fixed pane 360dp, form column max 640dp, search results max 720dp.
- Window background `surfaceContainer`; `AppPane` = `surfaceContainerLowest`, `MaterialTheme.shapes.extraLarge`; forms fill with `surface`.
- No orientation lock anywhere.
- Every user-visible string goes in both `res/values/strings.xml` (English) and `res/values-es/strings.xml` (Spanish).
- Tests live in `app/src/test/java/<same package>/<Class>Test.kt`. Verify with `./gradlew testDebugUnitTest` (and `./gradlew assembleDebug` for UI-only tasks).
- Commit messages: Conventional Commits, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. PR bodies end with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## Review Focus

1. Tapping the tab that owns a secondary destination (e.g. Loans while on `loan_detail` opened from Home, where `loans` is not on the stack) must land on that tab, never no-op. → `TabNavigationTest` cases in Task 1.
2. Phone landscape (~891×411dp) must get rail + two panes and still be usable with ~360dp of height. → `AppLayoutTest` cases in Task 2; manual check in Task 9.
3. Folding/rotating with a loan selected must keep showing that loan; without a selection the list (single pane) or the placeholder (two panes) — never the old auto-select-first behavior. → manual check in Task 6 and Task 9.
4. A form opened for editing must show "discard?" only after a real edit, not right after loading (loading replaces the state). → `FormChangesTest` in Task 11.
5. Saving or deleting from a form must leave the form exactly once (no double pop landing on the wrong screen). → manual check in Task 13.

---

## PR 1 — Tab navigation fix

### Task 1: Pure tab-click resolution and safe `navigateToTab`

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/TabNavigation.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavigationTab.kt` (the `onTabClick` lambda)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavGraph.kt` (`navigateToTab`)
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/navigation/TabNavigationTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface TabAction {
      /** Leave everything above [popTo] (unsaved), then switch to [route] as a tab. popTo == null: already at a tab root. */
      data class Switch(val route: String, val popTo: String?) : TabAction
      data class PopToRoot(val route: String) : TabAction
      data class ScrollToTop(val route: String) : TabAction
  }
  /** [tabsInBackStack]: tab routes currently on the back stack (always contains home). */
  fun resolveTabClick(target: String, currentRoute: String?, tabsInBackStack: Set<String>): TabAction
  fun NavController.performTabAction(action: TabAction, onScrollToTop: (String) -> Unit)
  ```
  Tab routes = `Destination.entries.map { it.route }`; home = `NavRoutes.Home.route`.
  Root tab of a secondary destination = the non-home tab in `tabsInBackStack` if any, else home.

- [ ] **Step 1: Write the failing tests** — `TabNavigationTest`, routes from `NavRoutes`:
  - on `home`, tap `home` → `ScrollToTop("home")`
  - on `home`, tap `loans` → `Switch("loans", popTo = null)`
  - on `settings`, stack {home}, tap `transactions_list` → `Switch("transactions_list", popTo = "home")`
  - on `settings`, stack {home}, tap `home` → `PopToRoot("home")` (the reported bug)
  - on `loan_detail/{loanId}`, stack {home}, tap `loans` → `Switch("loans", popTo = "home")` (Review Focus 1)
  - on `loan_detail/{loanId}`, stack {home, loans}, tap `loans` → `PopToRoot("loans")`
  - on `search`, stack {home, analytics}, tap `home` → `Switch("home", popTo = "analytics")`
  - `currentRoute == null` → `Switch(target, popTo = null)`

- [ ] **Step 2: Run, expect FAIL** — `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.ui.navigation.TabNavigationTest` → unresolved `resolveTabClick`.

- [ ] **Step 3: Implement** `resolveTabClick` and `performTabAction` in `TabNavigation.kt`.
  `performTabAction`: `Switch` with `popTo != null` → `popBackStack(popTo, inclusive = false, saveState = false)` first, then `navigateToTab(route)`; `PopToRoot` → `popBackStack(route, inclusive = false)`; `ScrollToTop` → `onScrollToTop(route)`.
  In `NavigationTab`, replace the `when` in `onTabClick` with `resolveTabClick(...)` + `performTabAction(...)`, computing `tabsInBackStack` as `Destination.entries.map { it.route }.filter { runCatching { navController.getBackStackEntry(it) }.isSuccess }.toSet()`; `onScrollToTop = { tabReselect.tryEmit(it) }`.

- [ ] **Step 4: Run, expect PASS** — same command, then the full `./gradlew testDebugUnitTest`.

- [ ] **Step 5: Manual check** on a wide emulator (Pixel Tablet / unfolded foldable): Home → Settings → tap Home shows Home; Settings → tap Loans → tap Home shows Home (not Settings).

- [ ] **Step 6: Commit and open PR 1**
  ```bash
  git add app/src/main/java/com/boxdev/boxwallet/ui/navigation app/src/test/java/com/boxdev/boxwallet/ui/navigation
  git commit -m "fix: tabs no longer reopen settings after leaving it"
  git push -u origin fix/tab-settings-restore
  gh pr create --base main --title "fix: tabs no longer reopen settings after leaving it" --body "..."
  ```

---

## PR 2 — Pane layout and top search bar

Branch `feat/material-layout` from `main` after PR 1 merges.

### Task 2: Adaptive dependency and `appLayout`

**Files:**
- Modify: `gradle/libs.versions.toml` (add `material3Adaptive = "1.3.0"`; libraries `androidx-compose-material3-adaptive`, `-adaptive-layout`, `-adaptive-navigation`, group `androidx.compose.material3.adaptive`)
- Modify: `app/build.gradle.kts` (implementation of the three)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/theme/WindowLayout.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/theme/Theme.kt:92,110` (provide `LocalAppLayout`, add `BoxTheme.layout`)
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/theme/AppLayoutTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class NavKind { Bar, Rail }
  data class AppLayout(
      val widthClass: WidthClass, val nav: NavKind, val railExpanded: Boolean,
      val twoPane: Boolean, val margin: Dp, val compactHeight: Boolean,
  )
  fun appLayout(widthDp: Int, heightDp: Int): AppLayout
  val LocalAppLayout: ProvidableCompositionLocal<AppLayout>
  BoxTheme.layout: AppLayout   // BoxTheme.widthClass keeps working = layout.widthClass
  ```
  `computeWidthClass()` is replaced by `computeAppLayout()` reading `currentWindowAdaptiveInfo()`'s window size in dp (`LocalWindowInfo.current.containerSize` converted with density is acceptable).

- [ ] **Step 1: Failing tests** — `AppLayoutTest`:
  - 411×891 → `Compact, Bar, railExpanded=false, twoPane=false, margin=16.dp, compactHeight=false`
  - 780×360 (small phone landscape) → `Medium, Rail, twoPane=false, margin=24.dp, compactHeight=true`
  - 891×411 (phone landscape) → `Expanded, Rail, railExpanded=false, twoPane=true, compactHeight=true`
  - 673×841 (foldable portrait) → `Medium, Rail, twoPane=false`
  - 1280×800 → `Expanded, Rail, railExpanded=true, twoPane=true`
  - boundaries: 599→Compact, 600→Medium, 839→Medium, 840→Expanded, 1199→not expanded rail, 1200→expanded rail, height 479→compactHeight, 480→not
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** `appLayout` and wire `LocalAppLayout` in `BoxWalletTheme`.
- [ ] **Step 4: Run, expect PASS**, plus `./gradlew assembleDebug`.
- [ ] **Step 5: Commit** `feat: derive app layout from the M3 window breakpoints`.

### Task 3: `AppPane`, `PaneRow` and `AppSearchBar`

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/components/Panes.kt`
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/components/AppSearchBar.kt`
- Modify: `res/values/strings.xml`, `res/values-es/strings.xml`

**Interfaces:**
- Produces:
  ```kotlin
  @Composable fun AppPane(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)
  /** Lays out one or two panes with BoxTheme.layout.margin around them and a 24dp spacer; second == null → single pane. */
  @Composable fun PaneRow(first: @Composable () -> Unit, second: (@Composable () -> Unit)? = null,
                          firstWidth: Dp? = null /* null = flexible 50/50, 360.dp = fixed */, modifier: Modifier = Modifier)
  @Composable fun AppSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null)
  ```
  `AppSearchBar`: M3 `SearchBar` collapsed input (`SearchBarDefaults.InputField`, read-only, opens on click), 56dp pill, `surfaceContainerHigh`, leading search icon, placeholder `R.string.search_in_app`, trailing slot. Top padding = status bar inset; horizontal padding = `BoxTheme.layout.margin`. Delete `SearchAction` from `Expressive.kt` once Task 8 removes its last use.
  Strings: `search_in_app` = "Search BoxWallet" / "Buscar en BoxWallet".
- [ ] **Step 1: Implement**; add `@Preview`s (light/dark, compact/expanded) for both files.
- [ ] **Step 2: Verify** `./gradlew assembleDebug` succeeds and previews render.
- [ ] **Step 3: Commit** `feat: add pane and top search bar components`.

### Task 4: Shell — background, secondary destinations, rail/bar from `AppLayout`

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavigationTab.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavGraph.kt` (drop `onNavigateToSettings` from every tab except Home)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/TabNavigation.kt` (add `isSecondary`)
- Test: `TabNavigationTest` (extend)

**Interfaces:**
- Produces: `fun isSecondary(route: String?): Boolean` — true for any route not in `Destination` routes (settings and children, search, `loan_detail/{loanId}`, later the form routes).
- [ ] **Step 1: Failing tests** — `isSecondary("home") == false`, `isSecondary("loans") == false`, `isSecondary("settings") == true`, `isSecondary("loan_detail/{loanId}") == true`, `isSecondary("search") == true`, `isSecondary(null) == false`.
- [ ] **Step 2: Run, expect FAIL.** **Step 3: Implement**, then in `NavigationTab`:
  - choose rail vs bar from `BoxTheme.layout.nav` and `railExpanded` (remove the `screenWidthDp >= 1200` check);
  - rail hidden (animated, fade) when `isSecondary(currentRoute)`; bottom bar keeps hiding on non-tab routes as today;
  - root `Scaffold`/`Row` `containerColor = surfaceContainer`; rail `containerColor` stays `surfaceContainer`; `ShortNavigationBar` `containerColor = surfaceContainer`.
- [ ] **Step 4: Run tests, PASS; `./gradlew assembleDebug`.**
- [ ] **Step 5: Commit** `feat: hide navigation on secondary destinations and paint the shell background`.

### Task 5: Home and Movements on panes with the search bar

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/transactions/TransactionsListScreen.kt`
- Modify: `res/values*/strings.xml` if a count line string is needed (reuse `R.plurals.records_count`).

**Interfaces:** Consumes `AppSearchBar`, `PaneRow`, `AppPane`, `BoxTheme.layout`.
- [ ] **Step 1: Home** — replace the `MediumFlexibleTopAppBar` with `AppSearchBar(trailing = settings IconButton)`; `Scaffold(containerColor = Color.Transparent)`; wide → `PaneRow(first = balance+loans, second = recent)` (50/50); compact → single `AppPane` with the existing column. Remove `scrollBehavior`/`nestedScroll`; `TabReselectEffect` only scrolls lists.
- [ ] **Step 2: Movements** — `AppSearchBar(trailing = FilterToggleAction)` only when `!twoPane`; `twoPane` → `PaneRow(first = filters, firstWidth = 360.dp, second = list)`; compact/medium → one `AppPane` with `CollapsibleFilterPanel` on top; the `records_count` plural becomes a `labelLarge`/`onSurfaceVariant` line as the list's first item. Remove the settings action and the `LargeFlexibleTopAppBar`.
- [ ] **Step 3: Verify** `./gradlew testDebugUnitTest assembleDebug`; run on phone portrait and tablet, screenshot both screens.
- [ ] **Step 4: Commit** `feat: home and movements on panes with the top search bar`.

### Task 6: Loans list-detail with `NavigableListDetailPaneScaffold`

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/loans/LoansScreen.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/loans/LoanDetailScreen.kt` (wrap standalone use in `AppPane`; keep `onNavigateBack: (() -> Unit)?`)

**Interfaces:**
- Consumes: `AppSearchBar`, `AppPane`.
- Behavior: `rememberListDetailPaneScaffoldNavigator<Long>()`; list pane width 360dp (`ListDetailPaneScaffoldDefaults`/`PaneExpansionState` anchored at 360dp); tapping a loan → `navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, loanId)` inside the Loans tab (single pane shows detail with back; two panes show selection); detail with no selection → existing `EmptyState(TouchApp, loans_select_hint)`; **remove** the auto-select-first `LaunchedEffect`. `LoanDetailScreen(onNavigateBack = null)` in two-pane, `{ navigator.navigateBack() }` in single pane. Search bar sits above the list pane only; Tune action as in Movements.
  `onNavigateToLoanDetail` from Home/Search/Reports keeps using the `loan_detail/{loanId}` route.
- [ ] **Step 1: Implement.**
- [ ] **Step 2: Verify** build; manual: phone (list→detail→back), tablet (placeholder, select, selection highlight), foldable emulator fold/unfold with and without a selection (Review Focus 3).
- [ ] **Step 3: Commit** `feat: loans list-detail with the M3 adaptive scaffold`.

### Task 7: Reports and Search on panes

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/AnalyticsScreen.kt` (lines ~147–215: top bar)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/components/Expressive.kt` (delete `SearchAction`)
- [ ] **Step 1: Reports** — `AppSearchBar()` with no trailing action; content in one `AppPane` keeping its internal two-column layout; drop settings param from `AnalyticsScreen` and its `NavGraph` call.
- [ ] **Step 2: Search** — background `surfaceContainer`, search field stays on the background, results in an `AppPane` capped at `widthIn(max = 720.dp)` and centered. In `NavGraph`, the `search` route uses the tab fade-through transition (not shared-axis) so it reads as the bar expanding in place.
- [ ] **Step 3: Verify** `./gradlew assembleDebug`; grep shows no `SearchAction` usages.
- [ ] **Step 4: Commit** `feat: reports and search on panes`.

### Task 8: Settings list-detail

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/settings/SettingsKit.kt` (`SettingsScaffold(onNavigateBack: (() -> Unit)?)`; null → no navigation icon)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/screens/settings/SettingsSection.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavGraph.kt` (settings block)
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/settings/SettingsSectionTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class SettingsSection(val route: String) { GENERAL, CURRENCIES, CATEGORIES, THEME, BACKUP, DATA }
  // routes = NavRoutes.GeneralSettings, Currencies, Categories, ThemeCustomization, Backup, DataManagement
  fun SettingsSection.Companion.fromRoute(route: String?): SettingsSection?
  ```
- Behavior: `twoPane` → `NavigableListDetailPaneScaffold` with `SettingsSection` as content key; list pane 360dp; detail pane renders the section's screen with `onNavigateBack = null`; no selection → `EmptyState` with new string `settings_select_hint` = "Choose a section" / "Elige una sección". Single pane → current route-based navigation unchanged. Currency detail / main currency change still navigate as full routes.
- [ ] **Step 1: Failing test** — `fromRoute` round-trips every section and returns null for `"settings"` and `null`.
- [ ] **Step 2: Run FAIL → Step 3: Implement → Step 4: Run PASS.**
- [ ] **Step 5: Verify** manual on tablet: Settings hides the rail, has back, list | detail; on phone unchanged.
- [ ] **Step 6: Commit** `feat: settings list-detail on wide windows`.

### Task 9: Docs, full verification and PR 2

**Files:**
- Modify: `DESIGN.md` (sections Layout, Global search, Elevation/tonal stack: background `surfaceContainer`, `AppPane`, search bar, secondary destinations, rotation)
- Modify: `README.md` only if it describes layout/navigation.
- [ ] **Step 1: Update docs.**
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Manual matrix** — phone portrait, phone landscape (Review Focus 2: rail, two panes, nothing clipped, sheets still usable), foldable folded/unfolded, tablet landscape ≥1200dp (expanded rail). Light and dark. Save screenshots for the PR.
- [ ] **Step 4: Commit, push, open PR** with title `feat: material list-detail layout with top search bar`.

---

## PR 3 — Full-screen forms

Branch `feat/full-screen-forms` from `main` after PR 2 merges.

### Task 10: Form routes and ViewModels reading `id` from `SavedStateHandle`

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavRoutes.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/transaction/TransactionFormViewModel.kt:126-150`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/loans/LoanFormViewModel.kt:72-95`
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/navigation/NavRoutesTest.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/loans/LoanFormViewModelTest.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/transaction/TransactionFormViewModelTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data object TransactionForm : NavRoutes("transaction_form?id={id}") { fun createRoute(id: Long? = null): String }
  data object LoanForm : NavRoutes("loan_form?id={id}") { fun createRoute(id: Long? = null): String }
  // nav argument "id": LongType, defaultValue = -1L
  ```
  Both ViewModels take `savedStateHandle: SavedStateHandle` as the first constructor parameter and call their existing `loadX(savedStateHandle.get<Long>("id"))` from `init`. `loadLoan`/`loadTransaction` become `private`.
- [ ] **Step 1: Failing tests**
  - `NavRoutesTest`: `TransactionForm.createRoute() == "transaction_form"`, `createRoute(5) == "transaction_form?id=5"`; same for `LoanForm`.
  - `LoanFormViewModelTest` (mockito mocks, `Dispatchers.setMain(StandardTestDispatcher())`): `SavedStateHandle(mapOf("id" to 7L))` → after `advanceUntilIdle()`, `verify(loanRepository).getLoanById(7L)` and `formState.isEditing == true`; empty handle → `accountRepository.defaultAccount()` called, `isEditing == false`, `isLoading == false`.
  - `TransactionFormViewModelTest`: same two cases with `transactionRepository` and `"id" to 3L`.
- [ ] **Step 2: Run, FAIL.** **Step 3: Implement.** **Step 4: Run, PASS** (full suite; existing `*FormStateTest` untouched).
- [ ] **Step 5: Commit** `refactor: form view models load their id from navigation`.

### Task 11: Unsaved-changes rule

**Files:**
- Modify: both form ViewModels (keep `initial` snapshot set at the end of loading; expose `val hasUnsavedChanges: StateFlow<Boolean>`)
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/screens/transaction/FormChanges.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/transaction/FormChangesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  fun TransactionFormState.differsFrom(initial: TransactionFormState): Boolean
  fun LoanFormState.differsFrom(initial: LoanFormState): Boolean
  ```
  Compares only user-editable fields. Transaction: `amount, currency, manualRate, equivalent, description, categoryId, date, type, isCleared, hasReminder, reminderDuration, isRecurrent, recurrenceFrequency, recurrenceInterval`. Loan: `name, description, totalAmount, currency, installments, startDate, categoryId, loanType, frequency, frequencyInterval`. Ignores `isLoading, isSaved, error, rate, needsManualRate, accountCurrency, missingRateCurrency, paidInstallments, wasCleared, isEditing, id`.
- [ ] **Step 1: Failing tests** — identical states → false; changing `amount` (resp. `name`) → true; changing only `isLoading`/`rate`/`error` → false; edit then revert to initial value → false.
- [ ] **Step 2: FAIL → Step 3: Implement → Step 4: PASS.** Snapshot `initial` after load finishes (Review Focus 4).
- [ ] **Step 5: Commit** `feat: detect unsaved changes in movement and loan forms`.

### Task 12: Full-screen form screens

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/components/FullScreenForm.kt`
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/screens/transaction/TransactionFormScreen.kt` (body moved from `components/TransactionSheet.kt`)
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/screens/loans/LoanFormScreen.kt` (body moved from `components/LoanSheet.kt`)
- Delete: `components/TransactionSheet.kt`, `components/LoanSheet.kt` (move any still-shared helpers like `SheetHeader` callers' pieces into `FormFields.kt` or the new screens)
- Modify: `res/values*/strings.xml`

**Interfaces:**
- Produces:
  ```kotlin
  @Composable fun FullScreenForm(
      title: String, onClose: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean,
      onDelete: (() -> Unit)?, hasUnsavedChanges: Boolean, content: @Composable ColumnScope.() -> Unit,
  )
  @Composable fun TransactionFormScreen(onDone: () -> Unit, viewModel: TransactionFormViewModel = hiltViewModel())
  @Composable fun LoanFormScreen(onDone: () -> Unit, viewModel: LoanFormViewModel = hiltViewModel())
  ```
  `FullScreenForm`: `Scaffold(containerColor = surface)`; `TopAppBar` with ✕ (`Icons.Filled.Close`), title, delete icon button (if `onDelete`), filled `Button` "Save" (`R.string` existing save label if present, else new `action_save`); content in a `verticalScroll` column, `imePadding`, `widthIn(max = 640.dp)` centered, horizontal padding `BoxTheme.layout.margin`. `BackHandler` + ✕: if `hasUnsavedChanges` show `AlertDialog` (`form_discard_title` "Discard changes?" / "¿Descartar cambios?", confirm `form_discard` "Discard" / "Descartar", dismiss `form_keep_editing` "Keep editing" / "Seguir editando"), else `onClose()`.
  Screens call `onDone()` once when `formState.isSaved` turns true (same `LaunchedEffect` as the sheets) and after a confirmed delete. The field content and delete confirmation dialog are moved unchanged.
- [ ] **Step 1: Implement** with `@Preview`s.
- [ ] **Step 2: Verify** `./gradlew assembleDebug`.
- [ ] **Step 3: Commit** `feat: full-screen movement and loan forms`.

### Task 13: Wire routes, remove sheets, docs and PR 3

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavGraph.kt` (two `composable`s with `navArgument("id") { type = NavType.LongType; defaultValue = -1L }`; transitions `slideInVertically { it / 8 } + fadeIn` / `slideOutVertically { it / 8 } + fadeOut`)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavigationTab.kt` (remove `showTransactionSheet`, `showLoanSheet`, `selectedTransactionId` and the sheet calls; FAB/rail menu and `onEditTransaction` navigate)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/loans/LoanDetailScreen.kt:178` (edit → `LoanForm.createRoute(id)` via new `onEditLoan: (Long) -> Unit`)
- Modify: `DESIGN.md` (forms section: full-screen pattern instead of sheets)
- [ ] **Step 1: Implement**; `onDone = { navController.popBackStack() }`. Form routes are secondary (rail hidden) via `isSecondary`; no extra code needed.
- [ ] **Step 2: Verify** `./gradlew testDebugUnitTest assembleDebug`; manual: new movement from FAB on each tab, edit from Home/Movements/Search/Reports, new loan, edit loan from Loan detail, delete both kinds, discard dialog only after edits, save returns to the origin exactly once (Review Focus 5); phone landscape and tablet.
- [ ] **Step 3: Commit, push, open PR** `feat: full-screen forms for movements and loans` with screenshots.
