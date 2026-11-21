# Multicuenta PR 1 — Cuentas personalizables · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el usuario cree, personalice, archive y borre cuentas en cualquier moneda, con saldo inicial y ajuste de saldo, sin que nada de eso cambie ingresos, gastos ni reportes.

**Architecture:** Todo vive en `accounts` y `transactions` (enfoque A del spec). La migración Room v1→v2 agrega de una vez todas las columnas de las tres fases (cuentas, transferencias, tarjetas). Saldo inicial y ajuste son filas `ADJUSTMENT_IN/OUT`; los reportes solo cuentan `TransactionType.countsInReports`. Se corrigen de paso los dos puntos que se rompen con varias cuentas (marcador del cambio de moneda y cuenta huérfana al restaurar).

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 Expressive), Room (KSP, `exportSchema`), Hilt, DataStore, JUnit4 + Robolectric (sdk 34) + `androidx.room:room-testing`.

**Spec:** `docs/superpowers/specs/2026-11-20-multi-account-design.md` (§3.1, §3.5, §5, §6, §9, §10, §11 PR 1)

## Global Constraints

- Rama `feat/multi-account`; nunca commit a `main`. Título del PR: `feat: cuentas personalizables con saldo inicial y ajuste` (Conventional Commit, va al "What's new").
- No editar `version.properties`, no crear tags.
- Dinero en `Long` unidades menores; cálculos `BigDecimal` `HALF_EVEN`.
- No hay fallback destructivo: la versión 2 lleva `Migration` + test.
- Tests junto al paquete del código: `app/src/test/java/<mismo paquete>/<Clase>Test.kt` (PR Checks los encuentra por nombre).
- UI según `DESIGN.md` (modelo de superficies, escala de esquinas, componentes de `ui/components/Expressive.kt`); strings en `values/strings.xml` y `values-es/strings.xml`.
- Ingresos/gastos/categorías/gráficos/Excel cuentan solo `INCOME` y `EXPENSE`.
- Con una sola cuenta la app se ve como hoy (sin chip de cuenta, sin fila de cuentas en Inicio).
- Verificación: `./gradlew testDebugUnitTest` verde antes de cada commit de tarea.

## Review Focus

1. **Respaldo v3 importado con Gson** — los campos nuevos no nulos llegan como `null` (Gson ignora defaults de Kotlin); restaurar debe rellenarlos, no crashear después en la UI. (Task 6)
2. **Ajuste en una lista existente** — `TransactionItem`, búsqueda y Excel asumen "si no es INCOME es EXPENSE"; un ajuste no puede pintarse rojo ni contar como gasto. (Task 3)
3. **Borrar la cuenta por defecto o la última cuenta** — debe rechazarse; borrar una cuenta con solo ajustes debe borrarlos en la misma transacción (RESTRICT). (Task 5)
4. **Saldo total con una cuenta en moneda sin tasa de hoy** — no debe tirar ni mostrar 0: usa la tasa más cercana y marca "≈". (Task 4)
5. **Crash entre el commit del cambio de moneda y DataStore con dos cuentas** — al reiniciar, la moneda principal queda la nueva (antes la heurística de "cuenta por defecto tiene la moneda" fallaba). (Task 7)

---

## File Structure

| Archivo | Responsabilidad |
|---|---|
| `data/local/entity/TransactionEntity.kt` (mod) | `TransactionType` + helpers; columna `transferId` |
| `data/local/entity/AccountEntity.kt` (mod) | columnas nuevas de §5.1 |
| `data/local/database/Migrations.kt` (nuevo) | `MIGRATION_1_2` |
| `domain/model/Account.kt` (mod) | campos nuevos en dominio |
| `domain/model/AccountBalance.kt` (nuevo) | `AccountWithBalance`, `TotalBalance`, cálculo puro del total |
| `data/local/dao/AccountDao.kt` (mod) | saldos por cuenta, conteo de movimientos no-ajuste |
| `data/repository/AccountRepository.kt` (mod) | CRUD, reglas de archivar/borrar, ajustes, flujos de saldo |
| `data/repository/BackupRepository.kt`, `domain/model/BackupData.kt`, `data/repository/LegacyBackupConverter.kt` (mod) | respaldo v4, normalización v3, huérfanos |
| `data/rates/MainCurrencyChanger.kt`, `data/rates/ApproximateRateFixer.kt` (mod) | marcador en `app_meta` |
| `util/AccountIcons.kt` (nuevo), `ui/theme/AccountColors.kt` (nuevo) | sets cerrados de ícono y color |
| `ui/screens/accounts/*` (nuevo) | `AccountsScreen`, `AccountDetailScreen`, `AccountFormScreen`, VMs, `AdjustBalanceSheet` |
| `ui/navigation/NavRoutes.kt`, `NavGraph.kt`, `MovementsFilterRequest.kt` (mod) | rutas y filtro por cuenta |
| `ui/components/BalancePanel.kt`, `ui/screens/home/*` (mod) | saldo total + fila de cuentas |
| `ui/screens/transaction/TransactionFormViewModel.kt` + screen (mod) | chip de cuenta |

Rutas relativas a `app/src/main/java/com/boxdev/boxwallet/`.

---

### Task 1: Tipos de movimiento para ajustes

**Files:**
- Modify: `data/local/entity/TransactionEntity.kt` (enum `TransactionType`)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/local/entity/TransactionTypeTest.kt`

**Interfaces:**
- Produces: `enum class TransactionType { INCOME, EXPENSE, ADJUSTMENT_IN, ADJUSTMENT_OUT }`; `val TransactionType.isInflow: Boolean` (`INCOME`, `ADJUSTMENT_IN`); `val TransactionType.countsInReports: Boolean` (`INCOME`, `EXPENSE`); `val TransactionType.isAdjustment: Boolean`. PR 2 agregará `TRANSFER_IN/OUT` a estos mismos helpers.

- [ ] **Step 1: Test** `TransactionTypeTest`: `assertEquals(setOf(INCOME, ADJUSTMENT_IN), entries.filter { it.isInflow }.toSet())`; `assertEquals(setOf(INCOME, EXPENSE), entries.filter { it.countsInReports }.toSet())`; `assertEquals(setOf(ADJUSTMENT_IN, ADJUSTMENT_OUT), entries.filter { it.isAdjustment }.toSet())`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests '*TransactionTypeTest'` → FAIL (unresolved).
- [ ] **Step 3:** Agregar los valores y las tres propiedades de extensión junto al enum.
- [ ] **Step 4:** Mismo comando → PASS. Compila todo el módulo (`./gradlew compileDebugKotlin`): arreglar `when` no exhaustivos que aparezcan con la rama que corresponda (los de presentación se tratan en Task 3; aquí solo lo mínimo para compilar, sin cambiar comportamiento de `INCOME/EXPENSE`).
- [ ] **Step 5: Commit** `feat: add adjustment movement types`.

### Task 2: Migración Room v1 → v2 y modelo de cuenta

**Files:**
- Modify: `data/local/entity/AccountEntity.kt`, `data/local/entity/TransactionEntity.kt`, `data/local/database/BoxWalletDatabase.kt` (`version = 2`), `di/DatabaseModule.kt` (`.addMigrations(MIGRATION_1_2)`), `domain/model/Account.kt`, `app/build.gradle.kts`
- Create: `data/local/database/Migrations.kt`, `app/schemas/com.boxdev.boxwallet.data.local.database.BoxWalletDatabase/2.json` (lo genera KSP)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/local/database/MigrationTest.kt`

**Interfaces:**
- Produces (`AccountEntity`, todos con default para que el código existente compile):
  `description: String = ""`, `icon: String = "AccountBalance"`, `color: String = "DEFAULT"`, `includeInTotal: Boolean = true`, `parentAccountId: Long? = null` (`Index("parentAccountId")`, **sin FK de SQLite**: agregar una FK obligaría a recrear `accounts`, que tiene hijos con RESTRICT; la integridad la valida `AccountRepository` en PR 3), `creditLimitMinor: Long? = null`, `statementClosingDay: Int? = null`, `paymentDueDay: Int? = null`, `dueReminderDaysBefore: Int? = null`.
- Produces (`TransactionEntity`): `transferId: String? = null` con `Index("transferId")`.
- Produces (`Account` dominio): mismos campos; `color: AccountColor` (Task 8 define el enum; aquí `String` en entidad y `AccountColor.fromKey(key)` en el mapper — crear en esta tarea `enum class AccountColor(val key: String)` en `domain/model/Account.kt` con al menos `DEFAULT` y `fromKey` que devuelve `DEFAULT` para claves desconocidas; Task 8 agrega los demás valores y sus colores).
- Produces: `val MIGRATION_1_2: Migration` en `Migrations.kt`.

- [ ] **Step 1:** En `app/build.gradle.kts`, `android { sourceSets { getByName("test").assets.srcDir("$projectDir/schemas") } }` para que `MigrationTestHelper` lea los JSON bajo Robolectric.
- [ ] **Step 2: Test** `MigrationTest` (`@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])`, `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BoxWalletDatabase::class.java)`):
  - `migrate1To2_keepsRowsAndFillsDefaults`: crea v1 con una cuenta (`GTQ`) y un movimiento por SQL; `runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)`; asserts: la cuenta tiene `description = ''`, `icon = 'AccountBalance'`, `color = 'DEFAULT'`, `includeInTotal = 1`, `parentAccountId IS NULL`, `creditLimitMinor IS NULL`; el movimiento conserva `amountMinor` y tiene `transferId IS NULL`.
  - `migrate1To2_keepsTransactionForeignKeys`: tras migrar, borrar la cuenta con un movimiento por SQL falla con `SQLiteConstraintException` (RESTRICT intacto).
- [ ] **Step 3:** `./gradlew testDebugUnitTest --tests '*MigrationTest'` → FAIL.
- [ ] **Step 4:** Implementar columnas, `version = 2`, `MIGRATION_1_2`: solo `ALTER TABLE … ADD COLUMN` (los `NOT NULL` con el mismo `DEFAULT` que declara la entidad vía `@ColumnInfo(defaultValue = …)`, para que `2.json` y la migración coincidan) y `CREATE INDEX index_accounts_parentAccountId` / `index_transactions_transferId`.
- [ ] **Step 5:** Mapper `toDomain/toEntity` de `Account` con los campos nuevos.
- [ ] **Step 6:** Test → PASS; suite completa verde. Commitear `2.json`.
- [ ] **Step 7: Commit** `feat: migrate database to v2 for accounts, transfers and cards`.

### Task 3: Ajustes fuera de reportes y bien pintados

**Files:**
- Modify: `ui/components/TransactionItem.kt`, `util/ExcelReportGenerator.kt`, `ui/screens/analytics/AnalyticsViewModel.kt`, `ui/screens/analytics/ReportMath.kt`, `ui/screens/search/SearchViewModel.kt`, `ui/screens/transactions/TransactionsListViewModel.kt` (si filtra por tipo), strings
- Test: `util/ExcelReportGeneratorTest.kt`, `ui/screens/analytics/ReportMathTest.kt`, `data/local/dao/TransactionDaoTest.kt`

**Interfaces:**
- Consumes: `countsInReports`, `isInflow`, `isAdjustment` (Task 1).

- [ ] **Step 1:** `grep -rn "TransactionType\.\|\.type ==\|isIncome" app/src/main/java` y listar cada sitio en el mensaje del commit. Regla: lo que suma o agrupa ingresos/gastos filtra `countsInReports`; lo que pinta signo usa `isInflow`.
- [ ] **Step 2: Tests**
  - `TransactionDaoTest.adjustmentsDoNotChangeIncomeOrExpenseTotals`: insertar `INCOME 1000`, `EXPENSE 300`, `ADJUSTMENT_IN 5000`, `ADJUSTMENT_OUT 200` (todos cleared); `getTotalIncome().first() == 1000L`, `getTotalExpense().first() == 300L`.
  - `ExcelReportGeneratorTest.adjustmentsExcludedFromTotalsAndLabelled`: un ajuste aparece en la hoja de movimientos con tipo `"Adjustment"` y no altera las celdas de total de ingresos/gastos ni el resumen por categoría.
  - `ReportMathTest` (o el test del helper de totales que use Análisis): ajustes en la lista del período no cambian `periodIncome`/`periodExpense` ni los datos mensuales.
- [ ] **Step 3:** Correr → FAIL los de Excel/ReportMath.
- [ ] **Step 4:** Implementar. `TransactionItem`: ajuste con ícono `Icons.Outlined.Tune`, color neutro (`onSurfaceVariant`), título "Ajuste de saldo"/"Balance adjustment", signo según `isInflow`. Al tocar un ajuste en una lista se abre un diálogo "Borrar ajuste" (no el formulario de movimiento, que solo conoce ingreso/gasto).
- [ ] **Step 5:** Tests PASS; suite verde.
- [ ] **Step 6: Commit** `feat: keep balance adjustments out of income, expenses and reports`.

### Task 4: Saldos por cuenta y saldo total

**Files:**
- Create: `domain/model/AccountBalance.kt`
- Modify: `data/local/dao/AccountDao.kt`, `data/repository/AccountRepository.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/model/AccountBalanceTest.kt`, `app/src/test/java/com/boxdev/boxwallet/data/repository/AccountRepositoryTest.kt` (nuevo)

**Interfaces:**
- Produces (DAO): `data class AccountBalanceRow(val accountId: Long, val balanceMinor: Long)`; `@Query fun observeBalances(): Flow<List<AccountBalanceRow>>` = `SELECT accountId, SUM(CASE WHEN type IN ('INCOME','ADJUSTMENT_IN') THEN accountAmountMinor ELSE -accountAmountMinor END) AS balanceMinor FROM transactions WHERE isCleared = 1 GROUP BY accountId`. (PR 2/3 ampliarán la lista de entradas y la regla de tarjetas.)
- Produces (dominio): `data class AccountWithBalance(val account: Account, val balance: Money)`; `data class TotalBalance(val amount: Money, val approximate: Boolean)`; `suspend fun totalBalance(accounts: List<AccountWithBalance>, main: Currency, rateToMain: suspend (Currency) -> ExchangeRate?): TotalBalance` — excluye solo las cuentas con `includeInTotal == false` (las archivadas suman: su dinero existe); cada cuenta se convierte a `main` con `rateToMain`. `approximate = true` si alguna cuenta no está en `main` o alguna tasa vino `APPROXIMATE`; tasa nula → esa cuenta se omite y `approximate = true`.
- Produces (repo): `fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>>` (cuentas sin movimientos con saldo 0, orden `sortOrder, id`); `fun observeTotalBalance(): Flow<TotalBalance>` usando `ExchangeRateResolver` con `LocalDate.now()` y `RateLookup.nearest` como respaldo (no descarga).

- [ ] **Step 1: Tests** `AccountBalanceTest`:
  - `sumsOnlyIncludedAccounts`: GTQ 100.00 (incluida) + GTQ 50.00 (`includeInTotal=false`) → `TotalBalance(Q100.00, approximate=false)`.
  - `convertsForeignAccountsWithTodayRate`: GTQ 100.00 + USD 10.00 con tasa 7.75 → Q177.50, `approximate = true`.
  - `missingRateSkipsAccountAndFlagsApproximate`: USD sin tasa → solo lo de GTQ, `approximate = true`.
- [ ] **Step 2:** `AccountRepositoryTest.balanceCountsClearedInflowsMinusOutflows` (Room in-memory): INCOME 1000 cleared, EXPENSE 300 cleared, EXPENSE 50 **no** cleared, ADJUSTMENT_IN 200 → saldo 900.
- [ ] **Step 3:** Correr → FAIL. **Step 4:** Implementar. **Step 5:** PASS.
- [ ] **Step 6: Commit** `feat: compute account balances and total balance`.

### Task 5: Crear, editar, archivar, borrar y ajustar cuentas

**Files:**
- Modify: `data/repository/AccountRepository.kt`, `data/local/dao/AccountDao.kt`, `data/local/dao/TransactionDao.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/AccountRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1, 4; `MovementWriteGuard` (bloquea escrituras durante el cambio de moneda; usar igual que `TransactionRepository`).
- Produces:
  - `suspend fun create(account: Account, currentBalance: Money?): Long` — inserta y, si `currentBalance` ≠ 0, un ajuste fechado hoy (`ADJUSTMENT_IN` si positivo, `ADJUSTMENT_OUT` si negativo, monto absoluto, tasas identidad hacia la cuenta, `amountMinor` a principal con la tasa del día vía `TransactionAmounts`, `isCleared = true`), todo en una `withTransaction`.
  - `suspend fun update(account: Account)` — lanza `IllegalStateException` si cambia `currency` y `hasNonAdjustmentMovements`.
  - `suspend fun hasNonAdjustmentMovements(accountId: Long): Boolean` (DAO: `COUNT(*) WHERE accountId = :id AND type NOT IN ('ADJUSTMENT_IN','ADJUSTMENT_OUT')`; incluye cuotas de préstamo).
  - `suspend fun adjustBalance(accountId: Long, realBalance: Money)` — crea un ajuste por `real − actual`; no hace nada si es 0.
  - `suspend fun archive(accountId: Long, archived: Boolean)`; `suspend fun delete(accountId: Long)`; `suspend fun setDefault(accountId: Long)`.
  - `sealed class AccountRuleError : Exception` con `IsDefault`, `LastAccount`, `HasMovements`.
  - `fun observeAll(): Flow<List<Account>>`; `fun observeDefaultAccountId(): Flow<Long?>`.

- [ ] **Step 1: Tests** (Room in-memory + `UserPreferences` falsa o DataStore de test como en tests existentes):
  - `createWithBalanceAddsAdjustmentInAccountCurrency`: crear USD con US$25.00 → un `ADJUSTMENT_IN` de 2500, `currencyCode = "USD"`, saldo 2500.
  - `createWithNegativeBalanceAddsAdjustmentOut`.
  - `adjustBalanceCreatesDifference`: saldo 900, real 1000 → `ADJUSTMENT_IN 100`; real 900 → sin fila nueva.
  - `currencyLockedOnlyByNonAdjustmentMovements`: con solo ajuste se puede cambiar moneda; con un gasto, `update` lanza.
  - `deleteRemovesAdjustmentsAtomically`; `deleteWithMovementsFails` (`HasMovements`); `cannotDeleteOrArchiveDefault` (`IsDefault`); `cannotDeleteLastAccount` (`LastAccount`).
  - `setDefaultPersistsId`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS.
- [ ] **Step 5: Commit** `feat: create, edit, archive, delete and adjust accounts`.

### Task 6: Respaldo v4 y cuenta huérfana

**Files:**
- Modify: `domain/model/BackupData.kt` (`CURRENT_VERSION = 4`, `defaultAccountId: Long? = null`), `data/repository/BackupRepository.kt` (`snapshot`, `restore`, `sanitized`), `data/repository/LegacyBackupConverter.kt` (normalización tras Gson)
- Test: `data/repository/BackupRepositoryTest.kt`, `data/repository/LegacyBackupConverterTest.kt`

**Interfaces:**
- Produces: `BackupData.defaultAccountId`; `restore` escribe en `META_DEFAULT_ACCOUNT_ID` el `defaultAccountId` del respaldo si existe entre las cuentas, si no la primera.

- [ ] **Step 1: Tests**
  - `LegacyBackupConverterTest.v3AccountsGetDefaultsForNewFields`: JSON v3 sin `description/icon/color/includeInTotal` → `AccountEntity` con `""`, `"AccountBalance"`, `"DEFAULT"`, `true` (no `null`).
  - `BackupRepositoryTest.roundTripKeepsAccountCustomizationAndDefault`: dos cuentas con ícono/color/descripción y la segunda por defecto → restore → mismos campos y `defaultAccountId` = la segunda.
  - `BackupRepositoryTest.orphanMovementGoesToAccountOfItsAccountCurrency`: cuentas GTQ(1, por defecto) y USD(2). La fila no guarda la moneda de su cuenta; regla: si `accountRate == 1` y `originalAmountMinor == accountAmountMinor`, la cuenta original estaba en `currencyCode` → primera cuenta en esa moneda; si no, la cuenta por defecto del respaldo. Asserts: movimiento USD, tasa 1, `accountId = 99` → cuenta 2; movimiento EUR, tasa 7.71, `accountId = 99` → cuenta 1.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar (la normalización recorre cuentas y movimientos y reemplaza `null` por los defaults; Gson los deja `null` aunque el tipo Kotlin sea no nulo). **Step 4:** PASS.
- [ ] **Step 5: Commit** `fix: back up account customization and restore orphan movements to the right account`.

### Task 7: Marcador del cambio de moneda en `app_meta`

**Files:**
- Modify: `data/rates/MainCurrencyChanger.kt` (`apply`, `completePending`), `data/rates/ApproximateRateFixer.kt`, `data/datastore/UserPreferences.kt` (quitar `pendingMainCurrency` y su clave; al leer, borrar la clave vieja si existe)
- Test: `data/rates/MainCurrencyChangerTest.kt`, `data/rates/ApproximateRateFixerTest.kt`

**Interfaces:**
- Produces: `const val META_PENDING_MAIN_CURRENCY = "pending_main_currency"` en `MainCurrencyChanger` companion. `apply` lo escribe **dentro** de `database.withTransaction { recalculate(...); appMetaDao.put(...) }`, luego `prefs.setMainCurrency`, luego borra el marcador. `completePending` = si el marcador existe → `setMainCurrency(valor)` y borrarlo. Sin heurísticas de cuenta.
- `ApproximateRateFixer` omite su trabajo si `appMetaDao.get(META_PENDING_MAIN_CURRENCY) != null`.

- [ ] **Step 1: Tests**
  - `completePendingFinishesCommittedChangeWithSeveralAccounts`: dos cuentas (GTQ por defecto, USD); simular commit con marcador `EUR` y DataStore aún en GTQ → `completePending()` → `mainCurrency == EUR`, marcador borrado.
  - `rolledBackChangeLeavesNoMarker`: forzar `MissingRateException` en `apply` → sin marcador, moneda sin cambio.
  - `ApproximateRateFixerTest.skipsWhilePendingMarkerExists`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS (incluidos los tests existentes de ambos archivos, adaptados).
- [ ] **Step 5: Commit** `fix: record the pending main currency change with the data it changes`.

### Task 8: Íconos y colores de cuenta

**Files:**
- Create: `util/AccountIcons.kt`, `ui/theme/AccountColors.kt`
- Modify: `domain/model/Account.kt` (`AccountColor` completo)
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/theme/AccountColorsTest.kt`

**Interfaces:**
- Produces: `val accountIconOptions: List<Pair<String, ImageVector>>` (16–20 íconos de `Icons.Default`: `AccountBalance`, `AccountBalanceWallet`, `CreditCard`, `Savings`, `Payments`, `Wallet`, `LocalAtm`, `Euro`, `AttachMoney`, `CurrencyExchange`, `Work`, `Home`, `Flight`, `School`, `Store`, `Smartphone`, …) y `fun accountIcon(key: String): ImageVector` (default `AccountBalance`).
- Produces: `enum class AccountColor(val key: String)` con `DEFAULT` + 11 claves (`RED, ORANGE, AMBER, LIME, GREEN, TEAL, CYAN, BLUE, INDIGO, PURPLE, PINK`); `@Composable fun AccountColor.container(): Color` y `.onContainer(): Color` en `AccountColors.kt` con variantes claro/oscuro; `DEFAULT` usa `MaterialTheme.colorScheme.secondaryContainer/onSecondaryContainer`.

- [ ] **Step 1: Test** `AccountColorsTest`: `AccountColor.entries.map { it.key }` sin duplicados; `fromKey("nope") == DEFAULT`; para cada color, contraste `onContainer` vs `container` ≥ 4.5 en claro y oscuro (función pura de luminancia relativa; los pares se exponen como `internal val lightPairs/darkPairs: Map<AccountColor, Pair<Long, Long>>` para poder probarlos sin Compose).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS.
- [ ] **Step 5: Commit** `feat: add account icons and colors`.

### Task 9: Pantallas de cuentas

**Files:**
- Create: `ui/screens/accounts/AccountsScreen.kt`, `AccountsViewModel.kt`, `AccountDetailScreen.kt`, `AccountDetailViewModel.kt`, `AccountFormScreen.kt`, `AccountFormViewModel.kt`, `AccountFormState.kt`, `AdjustBalanceSheet.kt`
- Modify: `ui/navigation/NavRoutes.kt`, `ui/navigation/NavGraph.kt`, `ui/screens/settings/SettingsScreen.kt` (entrada "Cuentas"), `ui/navigation/MovementsFilterRequest.kt`, strings
- Test: `ui/screens/accounts/AccountFormStateTest.kt`, `ui/navigation/NavRoutesTest.kt`

**Interfaces:**
- Consumes: Task 4, 5, 8; `CurrencyPickerSheet`, `FullScreenForm`, `FormFields`, `Panes.kt`.
- Produces: `NavRoutes.Accounts("accounts")`, `NavRoutes.AccountDetail("account_detail/{accountId}")` con `createRoute(id: Long)`, `NavRoutes.AccountForm("account_form?id={id}")` con `createRoute(id: Long? = null)`.
- Produces: `data class AccountFormState(name, description, currency, currentBalance: String, icon: String, color: AccountColor, includeInTotal: Boolean, currencyLocked: Boolean, isEditing: Boolean)` con `val canSave: Boolean` y `val nameError: Int?`.
- Produces: `MovementsFilterRequest.Filter` gana `accountId: Long? = null` (y `categoryId` pasa a `Long?`); `fun requestAccount(accountId: Long)`. `TransactionsListViewModel` aplica el filtro.

Comportamiento (spec §3.1): lista con saldo total arriba, cuentas activas, sección "Archivadas" plegada; detalle con saldo, acciones "Ajustar saldo", "Editar", "Usar por defecto", "Archivar/Desarchivar", "Borrar" (solo si las reglas de Task 5 lo permiten; los errores `AccountRuleError` se muestran como snackbar con texto específico), y la lista de sus movimientos (reutiliza `TransactionItem`). Formulario: nombre*, moneda* (bloqueada con texto de ayuda si `currencyLocked`), "Saldo actual" (solo al crear), descripción, ícono, color, "Sumar al saldo total". En pantallas anchas lista + detalle lado a lado como Movimientos.

- [ ] **Step 1: Tests** `AccountFormStateTest`: nombre vacío o solo espacios → `canSave = false` con error; nombre > 40 caracteres → error; saldo `"12.345"` en GTQ inválido (más decimales que la moneda), `"-50"` válido, `""` válido (= sin saldo). `NavRoutesTest`: `AccountDetail.createRoute(7) == "account_detail/7"`, `AccountForm.createRoute() == "account_form?id=-1"` (seguir la convención existente de `TransactionForm`).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar estado, VMs y pantallas. **Step 4:** PASS.
- [ ] **Step 5:** Verificación visual: `./gradlew installDebug`, abrir Ajustes → Cuentas en el emulador, crear una cuenta USD con saldo, ajustarla, archivarla; captura en claro y oscuro.
- [ ] **Step 6: Commit** `feat: add accounts list, detail and form`.

### Task 10: Inicio y formulario de movimiento con cuentas

**Files:**
- Modify: `ui/screens/home/HomeViewModel.kt`, `ui/screens/home/HomeScreen.kt`, `ui/components/BalancePanel.kt`, `ui/screens/transaction/TransactionFormViewModel.kt`, `ui/screens/transaction/TransactionFormScreen.kt`
- Test: `ui/screens/transaction/TransactionFormViewModelTest.kt`, `ui/screens/home/HomeAccountsTest.kt` (nuevo, lógica pura)

**Interfaces:**
- Consumes: `observeAccountsWithBalance`, `observeTotalBalance`, `observeDefaultAccountId` (Task 4, 5).
- Produces: `HomeUiState` gana `totalBalance: TotalBalance` y `accounts: List<AccountWithBalance>` (no archivadas); `fun showAccountsRow(accounts: List<AccountWithBalance>): Boolean = accounts.size > 1`. `BalancePanel` recibe `totalBalance` y muestra ese monto (con "≈" si `approximate`) en lugar de `summary.balance`; el flujo del mes sigue igual.
- Produces: `TransactionFormState` gana `accountId: Long?` y `accounts: List<Account>` (no archivadas) y `currencyTouched: Boolean`; `TransactionFormViewModel.setAccount(id: Long)`.

- [ ] **Step 1: Tests**
  - `HomeAccountsTest`: una cuenta → `showAccountsRow == false`; dos → `true`.
  - `TransactionFormViewModelTest.newMovementStartsOnDefaultAccount`; `setAccountMovesCurrencyWhenUntouched` (default GTQ → elegir cuenta USD → `currency == USD`, `accountCurrency == USD`); `setAccountKeepsTouchedCurrency` (usuario eligió EUR → cambia cuenta → `currency == EUR`, `accountCurrency` = la nueva); `saveUsesSelectedAccount`; `editingKeepsMovementAccount`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. El chip de cuenta (ícono + nombre) va junto al chip de moneda y se oculta si hay una sola cuenta; tocar la fila de una cuenta en Inicio abre su detalle; "Ver cuentas" abre `Accounts`. **Step 4:** PASS.
- [ ] **Step 5:** Verificación visual en emulador: con una cuenta Inicio y formulario se ven como antes; con dos, aparece la fila y el chip.
- [ ] **Step 6: Commit** `feat: show accounts on home and choose the account of a movement`.

### Task 11: Cierre del PR

**Files:** `README.md` (si describe datos/funciones), `DESIGN.md` (si se agregó un patrón nuevo, p. ej. paleta de cuentas)

- [ ] **Step 1:** `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 2:** Prueba manual (spec §11): instalar sobre una build v1.0.0 con datos (migración real), crear cuenta USD con saldo, gasto en EUR desde ella, ajuste, cambio de moneda principal con dos cuentas, respaldo → borrar datos → restaurar; Inicio y Análisis no cambian por ajustes.
- [ ] **Step 3:** Actualizar docs si aplica; commit `docs: describe accounts`.
- [ ] **Step 4:** `git push -u origin feat/multi-account` y `gh pr create --base main --title "feat: cuentas personalizables con saldo inicial y ajuste"` (cuerpo: resumen, capturas, cómo probar la migración). Esperar **PR Checks**.

---

## Fases siguientes (planes aparte)

- **PR 2 — Transferencias** (`docs/superpowers/plans/…-multi-account-pr2-transfers.md`): se escribe tras mergear este PR, sobre el código resultante. Usa `transferId` ya migrado, agrega `TRANSFER_IN/OUT` a los helpers de Task 1 y a `observeBalances`.
- **PR 3 — Tarjetas de crédito y bimoneda**: usa las columnas de tarjeta ya migradas; cambia la regla de saldo de tarjetas a "ignorar `isCleared`, fecha ≤ hoy" (spec §6) y agrega el cálculo de corte (§8).
