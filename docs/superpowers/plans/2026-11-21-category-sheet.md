# Category sheet, color and default category — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Categories get their own color, a bottom-sheet form with a live preview, a compact "+" FAB, and the
default category moves from General Settings into the Categories screen (and is applied again to new movements).

**Architecture:** `CategoryEntity`/`Category` gain a `color` that reuses the 12 `AccountColor` keys; `DEFAULT`
keeps today's seed tone. `CategoriesViewModel` takes over the `defaultCategoryId` preference, General Settings
disappears, and `TransactionFormViewModel` pre-selects the default category on a new movement. The UI is a
new `CategoryFormSheet` (ModalBottomSheet) plus a reworked `CategoriesScreen`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 Expressive, Room (schema v1, no migrations), Hilt,
DataStore, Robolectric + JUnit4 + mockito-kotlin.

**Spec:** `docs/superpowers/specs/2026-11-21-category-sheet-design.md`

**Branch:** `feat/category-sheet`, stacked on `feat/loans-in-accounts` (PR box-bm/BoxWallet#38). Worktree
`/Users/bmanzo/Developer/BoxWallet-categories`. The PR targets `main` once #38 merges.

## Global Constraints

- Room database stays at **version 1**, no migration; regenerate `app/schemas/.../1.json` with the build and commit it.
- Backup format: `BackupData.CURRENT_VERSION = 7` (PR 1 made it 6). Only the current format is accepted.
- Category color keys are exactly the `AccountColor` keys (`DEFAULT`, `RED` … `PINK`); unknown or missing keys read as `DEFAULT`.
- `DEFAULT` keeps `Category.tone()`'s seed-based scheme pair; the badge polygon always comes from the seed.
- The `defaultCategoryId` DataStore preference (key `default_category_id`) is unchanged; only its UI moves.
- Material 3 Expressive per `DESIGN.md` and `ui/components/Expressive.kt`; every user string in `values/strings.xml` **and** `values-es/strings.xml`.
- Unit tests beside the code's package (`app/src/test/java/<same package>/<Class>Test.kt`); `./gradlew testDebugUnitTest` green at the end of every task.
- Conventional Commits; never commit `local.properties` or `app/google-services.json`.

## Review Focus

1. **Default category that no longer exists** (deleted, cleared via Data management, or a restore with other ids): the Categories card shows "None" and a new movement starts without a category — never a dangling id. → Task 2 tests.
2. **Editing an existing movement** must keep its own category (or none); only *new* movements get the default, and the pre-fill must not mark the new form as having unsaved changes. → Task 2 tests.
3. **Whitespace-only name** cannot be saved; names are trimmed. → Task 2 test.
4. **Backup file whose category has no `color`** (Gson leaves it null) restores as `DEFAULT`, not a crash. → Task 1 test.
5. **Keyboard open in the sheet**: the save button stays visible above the IME, and the sheet scrolls on small/short windows (folded and landscape). → Task 3, checked on device by the controller.

---

### Task 1: Category color in data, domain, backup and badge

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/entity/CategoryEntity.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/domain/model/Category.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/domain/model/BackupData.kt` (version 7)
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/repository/BackupRepository.kt` (`sanitized()`)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/components/Expressive.kt` (`Category.tone()`)
- Modify: `app/schemas/com.boxdev.boxwallet.data.local.database.BoxWalletDatabase/1.json` (regenerated)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/CategoryRepositoryTest.kt` (new), `BackupRepositoryTest.kt`

**Interfaces:**
- Produces: `CategoryEntity.color: String = "DEFAULT"`; `Category.color: AccountColor = AccountColor.DEFAULT`
  (mapped with `AccountColor.fromKey(color)` / `color.key`); `BackupData.CURRENT_VERSION = 7`.

- [ ] **Step 1: Write failing tests**
  - `CategoryRepositoryTest` (Robolectric, in-memory `BoxWalletDatabase` like `LoanRepositoryTest`):
    - `a category keeps its color` — insert `Category(name = "Super", icon = "ShoppingCart", color = AccountColor.ORANGE)`, read `getAllCategories().first()` → color `ORANGE`.
    - `a category without a color reads as DEFAULT` — insert `CategoryEntity(name = "X")` through the DAO → domain color `DEFAULT`.
    - `an unknown color key reads as DEFAULT` — entity with `color = "MAUVE"` → `DEFAULT`.
  - `BackupRepositoryTest`:
    - extend the round-trip so one category has `color = "TEAL"` and assert it comes back.
    - `a category without color in the file restores as DEFAULT` — build a `BackupData` whose category entity has `color` forced to null (Gson-style, e.g. via `copy` + reflection or by parsing a JSON string with the field missing, whichever the existing tests use for files) → restored entity color `"DEFAULT"`.
    - rename/adjust the "v6 shape" and "version 5 refused" tests: write version is 7, versions 5 **and 6** are refused.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*CategoryRepositoryTest' --tests '*BackupRepositoryTest'` → FAIL (no `color`).
- [ ] **Step 3: Implement**
  - Add the field to entity and domain with the mapping above.
  - `BackupData.CURRENT_VERSION = 7`; KDoc mentions category color.
  - `sanitized()`: categories with a null/blank `color` get `"DEFAULT"` (Gson bypasses the Kotlin default).
  - `Category.tone()`: when `color != AccountColor.DEFAULT` return `color.container() to color.onContainer()`; otherwise today's seed branch unchanged.
  - Build once (`./gradlew assembleDebug`) so Room re-exports `1.json`; commit it.
- [ ] **Step 4: Run** the two test classes → PASS, then the full suite → PASS.
- [ ] **Step 5: Commit** `feat: give categories their own color`

### Task 2: Default category moves to Categories and applies to new movements

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/settings/CategoriesViewModel.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/transaction/TransactionFormViewModel.kt` (new-movement branch of `loadTransaction`)
- Delete: `ui/screens/settings/GeneralSettingsScreen.kt`, `ui/screens/settings/GeneralSettingsViewModel.kt`
- Modify: `ui/screens/settings/SettingsSection.kt` (drop `GENERAL`), `ui/screens/settings/SettingsScreen.kt` (drop the row and pane branch; Preferences group goes from 5 to 4 rows), `ui/navigation/NavRoutes.kt` + `NavGraph.kt` (drop `GeneralSettings`), `values*/strings.xml` (drop unused `general_settings_*` / `settings_general_*` strings — grep first)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/settings/CategoriesScreen.kt` — only the minimal glue so it compiles against the new VM API (Task 3 rebuilds it)
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/settings/CategoriesViewModelTest.kt` (new), `SettingsSectionTest.kt`, `ui/screens/transaction/TransactionFormViewModelTest.kt`

**Interfaces:**
- Consumes: `Category.color` (Task 1); `UserPreferences.defaultCategoryId: Flow<Long?>`, `setDefaultCategory(Long?)`.
- Produces (Task 3 relies on these):
  ```kotlin
  data class CategoriesUiState(
      val categories: List<Category> = emptyList(),
      /** The default category, or null when none is set or it no longer exists. */
      val defaultCategory: Category? = null,
      val isLoading: Boolean = true,
      @StringRes val error: Int? = null,
  )
  // CategoriesViewModel(categoryRepository, userPreferences, analyticsHelper)
  fun save(id: Long?, name: String, description: String, icon: String, color: AccountColor) // id null = create
  fun delete(id: Long)                 // clears the preference when id is the default
  fun setDefaultCategory(id: Long?)
  fun clearError()
  ```

- [ ] **Step 1: Write failing tests**
  - `CategoriesViewModelTest` (mocked `CategoryRepository`, `AnalyticsHelper`; real `UserPreferences` on the Robolectric context as `AccountFormViewModelTest` does, or a mock with a `MutableStateFlow` — implementer's choice):
    - `the default category resolves from the list` — prefs id 2, categories [1, 2] → `defaultCategory.id == 2`.
    - `a default id missing from the list shows none` — prefs id 9 → `defaultCategory == null`.
    - `save without id creates a trimmed category with its color` — `save(null, "  Super ", "", "ShoppingCart", AccountColor.ORANGE)` → `insertCategory(Category(name = "Super", icon = "ShoppingCart", color = ORANGE))`.
    - `save with id updates it` → `updateCategory(...)` with the same id.
    - `a blank name is not saved` — `save(null, "   ", …)` → no insert.
    - `deleting the default clears it` — prefs id 2, `delete(2)` → `setDefaultCategory(null)` / pref reads null.
    - `deleting another category keeps the default`.
    - `a failing save shows a localized error` — insert throws → `error == R.string.categories_save_failed`.
  - `TransactionFormViewModelTest`:
    - `a new movement starts with the default category` — `userPreferences.defaultCategoryId` emits 7, categories contain id 7 → `formState.categoryId == 7` and the form has no unsaved changes.
    - `a default category that no longer exists is ignored` — emits 7, categories empty → `categoryId == null`.
    - `editing keeps the movement's own category` — open id 3 (category 1) with default 7 → `categoryId == 1`.
    - In `setUp` stub `userPreferences.defaultCategoryId` with `flowOf(null)` so existing tests keep passing.
  - `SettingsSectionTest`: remove the `GENERAL` assertion.
- [ ] **Step 2: Run** the three test classes → FAIL.
- [ ] **Step 3: Implement**
  - `CategoriesViewModel`: combine categories + `defaultCategoryId` into the state above; analytics events stay `category_created` / `category_updated` / `category_deleted`, plus `default_category_changed` on `setDefaultCategory`. Catch `Exception` (rethrow `CancellationException`) → `categories_save_failed` / `categories_delete_failed`.
  - `TransactionFormViewModel` new-movement branch: read `userPreferences.defaultCategoryId.first()` and the current category list once; set `categoryId` only when the id exists, **before** `initial.value = _formState.value`.
  - Remove General Settings as listed; strings `categories_save_failed`, `categories_delete_failed` en/es ("Couldn't save the category" / "No se pudo guardar la categoría", "Couldn't delete the category" / "No se pudo borrar la categoría").
  - `CategoriesScreen`: minimal call-site glue only (`save`/`delete`, `error` as a resource).
- [ ] **Step 4: Run** the three test classes → PASS; full suite → PASS.
- [ ] **Step 5: Commit** `feat: pick the default category from Categories and apply it to new movements`

### Task 3: Categories screen and category sheet (Material 3 Expressive)

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/screens/settings/CategoryFormSheet.kt`
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/components/ChoiceToken.kt` (move `ChoiceToken` + `LabeledBlock` out of `AccountFormScreen.kt`, make them `internal`, reuse in both forms)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/settings/CategoriesScreen.kt`, `ui/screens/accounts/AccountFormScreen.kt` (import the moved helpers), `values*/strings.xml`, `DESIGN.md`
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/settings/CategoryFormStateTest.kt` (new)

**Interfaces:**
- Consumes: Task 2's `CategoriesUiState` and VM functions; `CategoryBadge`, `AccountColor.container()/onContainer()`, `categoryIconOptions`, `ConfirmDeleteDialog`, `SettingsScaffold`, `SettingsGroup`, `segmentShapes`, `settingsRowColors`.
- Produces:
  ```kotlin
  /** Editable copy of a category in the sheet; [preview] feeds the live badge. */
  data class CategoryFormState(
      val id: Long? = null, val name: String = "", val description: String = "",
      val icon: String = "Category", val color: AccountColor = AccountColor.DEFAULT,
  ) {
      val canSave: Boolean            // name.isNotBlank()
      fun preview(): Category         // Category(id = id ?: 0, name, description, icon, color)
      companion object { fun from(category: Category?): CategoryFormState }
  }
  @Composable fun CategoryFormSheet(category: Category?, onDismiss: () -> Unit,
      onSave: (CategoryFormState) -> Unit, onDelete: (() -> Unit)?)
  ```

- [ ] **Step 1: Write failing test** `CategoryFormStateTest`: `from(null)` is blank with `DEFAULT`/`"Category"`; `from(category)` copies every field including color; `canSave` false for `""` and `"  "`, true for `"Super"`; `preview()` of a new form has id 0 and of an edit keeps the real id (so an edited category keeps its silhouette).
- [ ] **Step 2: Run** `--tests '*CategoryFormStateTest'` → FAIL.
- [ ] **Step 3: Implement**
  - **Screen** (`CategoriesScreen`):
    - Top: tonal card (shape and container from `DESIGN.md`'s surface model) titled "Default category" / "Categoría principal": large `CategoryBadge` (or the `Category` fallback icon), the name or "None"/"Ninguna", one line "Selected automatically on every new movement" / "Se elige sola en cada movimiento nuevo", and a tonal "Change" / "Cambiar" button that opens a picker `ModalBottomSheet`: "None" row plus every category as `SegmentedListItem` with its badge, a check on the current one; tapping selects and closes.
    - Below: today's segmented list without the trailing delete button; tapping a row opens the sheet in edit mode.
    - FAB: standard `FloatingActionButton` with the `Add` icon, `contentDescription = "New category" / "Nueva categoría"`; no extended text, no hide-on-scroll.
    - Empty state unchanged.
  - **Sheet** (`CategoryFormSheet`, `ModalBottomSheet` with `skipPartiallyExpanded = true`):
    - Header row: title "New category" / "Nueva categoría" or "Edit category" / "Editar categoría"; in edit mode a delete `IconButton` that opens the existing `ConfirmDeleteDialog`.
    - Preview: centered `CategoryBadge(category = form.preview(), size = 72.dp)` with the name (or the "Name" placeholder in `onSurfaceVariant`) below; the badge morphs on each change using `MaterialTheme.motionScheme` (builder's choice of effect, within `Expressive.kt`).
    - Fields: name (required, single line), description (optional), both `shapes.extraLarge` like the account form.
    - "Color": `FlowRow` of `ChoiceToken` for the 12 `AccountColor.entries`, check icon on the selected one, `contentDescription` via the existing `account_color_option` pattern (add `category_color_option` if wording differs). `DEFAULT` shows the seed tone of the preview, so "Default" visibly means "automatic".
    - "Icon": `FlowRow` grid of `categoryIconOptions` as `ChoiceToken`s; selected uses the chosen color's container / onContainer (DEFAULT → the preview's tone).
    - Primary full-width `Button` "Create" / "Crear" or "Save" / "Guardar", `enabled = form.canSave`, pinned at the bottom above the IME (`imePadding()` + the content in a `verticalScroll` column).
    - Dismissing the sheet discards edits, as the old dialog did.
  - Remove the now unused old dialog code and strings (`categories_add` etc. — grep before removing).
  - `DESIGN.md`: Categories section describes the default card, the compact FAB and the sheet; remove General Settings from the settings list.
- [ ] **Step 4: Run** `--tests '*CategoryFormStateTest'` → PASS; full suite → PASS; `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat: category sheet with color and icon, and a default category card`

---

## Rulings made while planning

- **General Settings is removed**, not left empty — its only row was the default category; an empty screen in the Settings list is worse than no screen. Cost if wrong: restore one route and screen.
- **The default category is applied again to new movements** — the 2026-11-06 refactor (`23a3bd2`) dropped the only consumer, and the spec's card promises "it's picked automatically". Cost if wrong: one pre-fill to remove.
- **Stacked on `feat/loans-in-accounts`**, backup version 7 — avoids a version clash; retarget to `main` after #38 merges. Cost if wrong: a rebase.
- **`CategoryDonut` keeps its own palette** — chart colors need to be distinct from each other, which category tones don't guarantee. Cost if wrong: one color lookup.
- **No hide-on-scroll FAB** — Settings has no such pattern (spec: "if the pattern already exists").
