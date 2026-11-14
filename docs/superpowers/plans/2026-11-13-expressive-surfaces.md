# Expressive Surfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move BoxWallet's body onto `surface` with no visible panes, contain only the supporting pane in two-pane layouts, and align the shape scale with the M3 corner-radius tokens.

**Architecture:** The change is theme-first: `Shape.kt` takes the M3 scale and a new `LocalCardColor` carries the card tone, so screens read tokens instead of hard-coded roles. `PaneRow` becomes pure layout; `AppPane` becomes the one contained pane and provides the raised card tone. Screens are then adjusted one at a time against the spec.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 1.5.0-alpha13 (`Shapes.largeIncreased`, `extraLargeIncreased`, `extraExtraLarge` available).

**Spec:** `docs/superpowers/specs/2026-11-13-expressive-surfaces-design.md`

## Global Constraints

- Branch `feat/expressive-surfaces`; never commit to `main`; never touch `version.properties`.
- Conventional Commits; each commit ends with the Claude co-author line.
- Colors only from scheme roles, `BoxTheme.money` and `BoxTheme.cardColor`; no hex in screens.
- No new shadows or borders. No `surfaceContainerLowest` anywhere in `ui/`.
- Out of scope: palette, typography, navigation, copy, view models, which screens show one or two panes.
- All paths below are under `app/src/main/java/com/boxdev/boxwallet/ui/` unless they start with `app/` or a repo-root file.
- Build check after every task: `./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`.

## Review Focus

1. Dark theme: `surface` is the darkest tone, so cards must still read against it and against `surfaceContainerLow` panes. Checked in Task 6 screenshots.
2. Dynamic color and medium/high contrast: nothing may depend on a hex or on `surfaceContainerLowest` being white. Covered by the Task 6 grep.
3. A lone row in a group keeps the outer radius on all four corners. Pinned by `GroupShapesTest` in Task 1.
4. Two-pane with nothing selected (Loans placeholder, Settings first page): the contained pane must still fill its height. Task 6 screenshot.
5. Font scale 130% on Loans summary tiles: amounts auto-size, not clip. Task 4 uses `AmountText`; checked in Task 6.

---

### Task 1: Theme tokens (shapes and card tone)

**Files:**
- Modify: `theme/Shape.kt`, `theme/Theme.kt`
- Create: `app/src/test/java/com/boxdev/boxwallet/ui/theme/GroupShapesTest.kt`
- Modify (token remap only): every file calling `MaterialTheme.shapes.*` (34 call sites; list with `grep -rnE "shapes\.(extraSmall|small|medium|large|extraLarge)" app/src/main`)

**Interfaces:**
- Produces: `BoxShapes` with extraSmall 4, small 8, medium 12, large 16, largeIncreased 20, extraLarge 28, extraLargeIncreased 32, extraExtraLarge 48 (dp).
- Produces: `GroupShapes.item(index, count)` with outer 28 dp, inner 4 dp (same signature).
- Produces: `val LocalCardColor: ProvidableCompositionLocal<Color?>` (default `null`) and `BoxTheme.cardColor: Color` (`@Composable get`, returns `LocalCardColor.current ?: colorScheme.surfaceContainer`).

- [ ] **Step 1: Write the failing test** `GroupShapesTest`:

```kotlin
@Test fun loneRowIsRoundOnAllCorners() =
    assertEquals(RoundedCornerShape(28.dp), GroupShapes.item(0, 1))
@Test fun firstRowHasTightBottomCorners() =
    assertEquals(RoundedCornerShape(28.dp, 28.dp, 4.dp, 4.dp), GroupShapes.item(0, 3))
@Test fun middleRowIsTight() =
    assertEquals(RoundedCornerShape(4.dp), GroupShapes.item(1, 3))
@Test fun lastRowHasTightTopCorners() =
    assertEquals(RoundedCornerShape(4.dp, 4.dp, 28.dp, 28.dp), GroupShapes.item(2, 3))
```

- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.ui.theme.GroupShapesTest` → FAIL (24/6 values).
- [ ] **Step 3:** Update `BoxShapes` and `GroupShapes` to the values above; add `LocalCardColor` and `BoxTheme.cardColor` in `Theme.kt`.
- [ ] **Step 4:** Remap call sites so radii stay put: old `large` → `extraLarge`; old `extraLarge` → `extraLargeIncreased`; old `medium` → `largeIncreased`; old `small` → `medium`; old `extraSmall` → `small`. Do the renames from largest to smallest so no site is renamed twice. Exception: the income/expense tiles in `AnalyticsScreen.kt` (`Surface(... shape = MaterialTheme.shapes.medium ...)` near line 491) stay `medium` (now 12 dp, optical roundness inside the 28 dp summary card, which itself moves from old `extraLarge` to `extraLarge` = 28).
- [ ] **Step 5:** Test passes; `./gradlew compileDebugKotlin` succeeds.
- [ ] **Step 6:** Commit `refactor: adopt the Material 3 corner scale and a card tone token`.

### Task 2: Panes and shell on `surface`

**Files:**
- Modify: `components/Panes.kt`, `navigation/NavigationTab.kt`, `navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `LocalCardColor`, `BoxShapes.extraLargeIncreased`.
- Produces: `AppPane(modifier, content)` = contained pane: `surfaceContainerLow`, `shapes.extraLargeIncreased`, provides `LocalCardColor = surfaceContainerHigh`.
- Produces: `PaneRow(first, second = null, firstWidth = null, modifier, containFirst: Boolean = false, containSecond: Boolean = false)`. Same margins, 24 dp spacer and widths as today; a pane is wrapped in `AppPane` only when its `contain*` flag is true, otherwise a plain `Box`.

- [ ] **Step 1:** Rewrite `AppPane` and `PaneRow` per the interface; update the four previews (window color `surface`, expanded previews with `containSecond = true`).
- [ ] **Step 2:** `NavigationTab.kt`: the shell `Row` background (line ~193) and the compact `Scaffold` `containerColor` (line ~300) become `colorScheme.surface`; rail and `ShortNavigationBar` keep `surfaceContainer`.
- [ ] **Step 3:** `NavGraph.kt` (line ~225): the standalone destination wrapper drops `AppPane` and the `surfaceContainer` background for a `surface` background with the same padding.
- [ ] **Step 4:** Compile. Commit `feat: put the app body on surface and drop the white panes`.

### Task 3: Shared components read the card tone

**Files:**
- Modify: `components/TransactionItem.kt`, `LoanCard.kt` (card and `LoanRow`), `Expressive.kt` (`Panel` default), `charts/ChartKit.kt`, `charts/RateLineChart.kt`, `screens/settings/SettingsKit.kt`, `screens/loans/LoanDetailScreen.kt` (lines ~200, ~333), `screens/settings/CurrencyDetailScreen.kt` (lines ~86, ~144)

**Interfaces:**
- Consumes: `BoxTheme.cardColor`.

- [ ] **Step 1:** Replace each `colorScheme.surfaceContainer` used as a card, row or group container (and the chart "mix toward the card" color) with `BoxTheme.cardColor`. Leave navigation, search bar, `surfaceContainerHigh` fields and `surfaceContainerHighest` tracks untouched.
- [ ] **Step 2:** Compile. Commit `refactor: cards and rows take their tone from the theme`.

### Task 4: Tab screens

**Files:**
- Modify: `screens/home/HomeScreen.kt`, `screens/transactions/TransactionsListScreen.kt`, `screens/loans/LoansScreen.kt`, `screens/analytics/AnalyticsScreen.kt`, `components/Expressive.kt` (`CollapsibleFilterPanel` padding)
- Modify: `app/src/main/res/values/strings.xml` and `values-es/strings.xml` only if a content description is needed for the new icon buttons (reuse existing previous/next period strings if present).

**Interfaces:**
- Consumes: `PaneRow(..., containFirst, containSecond)`, `AppPane`, `BoxTheme.cardColor`, `GroupShapes`.

- [ ] **Step 1: Home.** Remove the 16 dp horizontal `contentPadding` from the three `LazyColumn`s (the margin now comes from `PaneRow`); keep vertical padding and the 96 dp FAB spacer. No contained panes.
- [ ] **Step 2: Movements.** Two-pane call: `containFirst = true` (filters, 360 dp) with 16 dp inner padding; list pane loses its horizontal padding. Sticky header `Surface` color (line ~285) → `colorScheme.surface`. `CollapsibleFilterPanel` loses its horizontal 16 dp padding.
- [ ] **Step 3: Loans.** The list pane becomes a plain `Box`; the detail pane (and its `PanePlaceholder`) stays in `AppPane`. Replace the floating "You owe / Owed to you" texts with a `Row` of two equal-weight tiles, 4 dp gap, each a `Surface(color = BoxTheme.cardColor)` with 16 dp padding, label in `labelLarge` / `onSurfaceVariant` and amount via `AmountText` in `BoxTheme.moneyType.large` with its money color. Shapes: start tile `RoundedCornerShape(topStart = 28, bottomStart = 28, topEnd = 4, bottomEnd = 4)`, end tile mirrored. Remove the list's own horizontal padding.
- [ ] **Step 4: Reports.** `PaneRow` uncontained; remove inner horizontal padding. Period navigator: the month pill container → `surfaceContainerHigh` with `onSurface` content; previous/next become standard `IconButton`s (no filled container), keeping their enabled logic and content descriptions. Section cards → `BoxTheme.cardColor`.
- [ ] **Step 5:** Compile. Commit `feat: redraw the tabs on the surface background`.

### Task 5: Secondary screens and DESIGN.md

**Files:**
- Modify: `screens/search/SearchScreen.kt`, `screens/settings/SettingsScreen.kt`, `DESIGN.md`

- [ ] **Step 1: Search.** `Scaffold` `containerColor` → `surface`; replace the `AppPane` (line ~142) with a `Box` keeping `fillMaxWidth().widthIn(max = 720.dp).fillMaxHeight()`.
- [ ] **Step 2: Settings.** `containerColor` → `surface`; the sections list pane (line ~187) becomes a plain `Box`; the page pane (line ~215) stays `AppPane`.
- [ ] **Step 3: DESIGN.md.** Update: front matter `rounded` (extra-small 4, small 8, medium 12, large 16, large-increased 20, extra-large 28, extra-large-increased 32, group-outer 28, group-inner 4) and component radii (balance-panel, amount-field, search-field → extra-large-increased; loan-card → extra-large); Key Characteristics bullet on panes; Colors → Neutral (surface is the body everywhere; no Paper White role; card tone rule); Layout → "Window and panes"; Elevation ("The Tonal Stack Rule" with the five-step stack from the spec); Shapes; the Do "put every screen's content on an AppPane" becomes "content sits on surface; contain only the supporting pane in two-pane layouts".
- [ ] **Step 4:** Compile. Commit `feat: search and settings follow the surface model` and `docs: describe the surface model and corner scale in DESIGN.md`.

### Task 6: Verification

- [ ] **Step 1:** `grep -rn "surfaceContainerLowest" app/src/main/java` → no matches.
- [ ] **Step 2:** `./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`.
- [ ] **Step 3:** `./gradlew installDebug`; on the Pixel Fold emulator capture, folded and unfolded, light and dark (`adb shell cmd uimode night yes|no`): Home, Movements, Loans (with and without a selected loan when unfolded), Reports, Search, Settings, the movement form and a loan detail. Check each against the spec's criteria and Review Focus 1, 4 and 5 (`adb shell settings put system font_scale 1.3` for the Loans tiles, then restore `1.0`).
- [ ] **Step 4:** Fix anything found, re-capture the affected screens, commit fixes as `fix:`.
