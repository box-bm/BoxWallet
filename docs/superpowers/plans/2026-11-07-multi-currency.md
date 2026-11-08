# Multimoneda — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada movimiento puede estar en cualquier moneda, con tasa congelada a su fecha y conversión a
la moneda principal, sobre una base de datos nueva (dinero en `Long`, esquema versionado, lista para
multicuenta).

**Architecture:** Núcleo de dinero puro en `domain/money` (`Money`, conversión `BigDecimal`), tasas en
`data/rates` (cliente HTTP sobre fawazahmed0 + tabla `exchange_rates` + Worker diario) y un
`ExchangeRateResolver` puro que decide `AUTO/APPROXIMATE/MANUAL`. La base nueva `boxwallet.db` v1
reemplaza a la vieja; `amountMinor` (moneda principal) sigue alimentando todos los totales SQL.

**Tech Stack:** Kotlin, Compose/Material3, Room 2.8.4 (KSP), Hilt, DataStore, WorkManager 2.11.1,
Gson, `HttpURLConnection`, JUnit4 + Robolectric + room-testing.

**Spec:** `docs/superpowers/specs/2026-11-07-multi-currency-design.md` (leerlo junto con este plan).

## Global Constraints

- Nunca `Double` para montos guardados o sumados. `Long` en unidades menores; `BigDecimal` con `RoundingMode.HALF_EVEN` para convertir. `Double` solo al dibujar charts.
- Unidades menores = `Currency.defaultFractionDigits`; si es `-1`, usar 2.
- Tasas como `BigDecimal` persistidas en `TEXT` (`toPlainString()`).
- `RateSource` = `AUTO | MANUAL | APPROXIMATE`. `MANUAL` nunca se sobrescribe automáticamente (salvo el cambio de moneda principal, §4 del spec).
- La fecha de la tasa es la **fecha del movimiento**. `transactions.date` guarda hora local como si fuera UTC; la fecha local es `LocalDate.ofEpochDay(Math.floorDiv(date, 86_400_000))`.
- URLs: `https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@{tag}/v1/currencies/usd.json` y respaldo `https://{tag}.currency-api.pages.dev/v1/currencies/usd.json`, `tag` = `latest` o `YYYY-MM-DD`. Histórico desde `2024-03-02`. Timeout 10 s.
- Sin cliente HTTP nuevo (sin OkHttp/Retrofit/Ktor).
- Base nueva: archivo `boxwallet.db`, `version = 1`, `exportSchema = true`, esquemas en `app/schemas`, **sin** `fallbackToDestructiveMigration`. Borrar `boxwallet_database` al arrancar.
- `BackupData.version = 3`; restore acepta 2 y 3.
- Todo texto visible en `values/strings.xml` y `values-es/strings.xml`.
- Pruebas en `app/src/test/java/<mismo paquete>/<Clase>Test.kt`; correr con `./gradlew testDebugUnitTest`.
- Commits Conventional Commits en `feat/multi-currency`; nunca a `main`. Terminar mensajes con `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Monedas de 0 o 3 decimales** (JPY, CRC se mantiene en 2, BHD): un gasto de ¥1,500 convertido a GTQ y a BHD debe redondear con los decimales de destino y mostrarse sin ".00" en JPY. → pruebas en Task 1, Task 2 y Task 7.
2. **Movimiento con fecha previa a 2024-03-02 o futura**: debe guardarse con tasa `APPROXIMATE` (más antigua / más reciente), nunca fallar ni hacer peticiones inútiles. → prueba en Task 6.
3. **Editar un movimiento existente sin tocar moneda ni fecha**: no debe re-resolver la tasa (una `MANUAL` o la congelada se conserva). → prueba en Task 9.
4. **Ocurrencia recurrente generada en una fecha nueva**: toma la tasa de su propia fecha, no copia la de la plantilla, salvo que la plantilla tenga `MANUAL`. → prueba en Task 9.
5. **Proceso muerto tras el commit del recálculo y antes de escribir DataStore**: al arrancar se completa la moneda principal pendiente. → prueba en Task 12.

---

### Task 1: Núcleo de dinero

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/money/Money.kt`
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/money/RateSource.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/money/MoneyTest.kt`

**Interfaces:**
- Produces:
  - `data class Money(val minor: Long, val currency: Currency)` con `companion fun of(major: BigDecimal, currency: Currency): Money`, `fun toMajor(): BigDecimal`, `fun toMajorDouble(): Double` (solo charts), `operator fun plus(other: Money)` (requiere misma moneda), `fun convert(to: Currency, rate: BigDecimal): Money`.
  - `fun Currency.minorDigits(): Int` (−1 → 2).
  - `fun parseMajor(input: String, currency: Currency): Money?` (acepta `,` de miles, `.` decimal; `null` si inválido o ≤ 0; trunca nada: rechaza más decimales que la moneda).
  - `enum class RateSource { AUTO, MANUAL, APPROXIMATE }`
  - `data class ExchangeRate(val value: BigDecimal, val source: RateSource)` con `companion val IDENTITY = ExchangeRate(BigDecimal.ONE, RateSource.AUTO)`.

- [ ] **Step 1: Write the failing tests** (`MoneyTest`)
  - `minor digits` → `USD=2`, `JPY=0`, `BHD=3`.
  - `of rounds half even` → `Money.of(BigDecimal("1.005"), USD).minor == 100`, `Money.of(BigDecimal("1.015"), USD).minor == 102`.
  - `convert uses target digits` → `Money(150000, GTQ).convert(USD, BigDecimal("0.130882")).minor == 19632`; `Money(1500, JPY).convert(GTQ, BigDecimal("0.0482")).minor == 7230`; `Money(1000, USD).convert(JPY, BigDecimal("158.48096064")).minor == 1585`.
  - `plus rejects mixed currencies` → `assertThrows<IllegalArgumentException>`.
  - `parseMajor` → `"1,234.5"` USD → `123450`; `"1.234"` USD → `null`; `"0"` → `null`; `"1500"` JPY → `1500`; `"abc"` → `null`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests com.boxdev.boxwallet.domain.money.MoneyTest` → FAIL (no compila).
- [ ] **Step 3:** Implementar los tipos de Interfaces. `convert`: `toMajor().multiply(rate).setScale(to.minorDigits(), HALF_EVEN).unscaledValue().toLong()`.
- [ ] **Step 4:** Mismo comando → PASS.
- [ ] **Step 5:** Commit `feat: add Money core with minor units and half-even conversion`.

---

### Task 2: Catálogo de monedas, formato y preferencias

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/money/CurrencyCatalog.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/util/CurrencyFormatter.kt` (reescritura)
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/datastore/UserPreferences.kt` (quitar `CentralAmericanCurrency`, `currency`, `currencySymbol`, `setCurrency`)
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/money/CurrencyCatalogTest.kt`, `app/src/test/java/com/boxdev/boxwallet/util/CurrencyFormatterTest.kt`

**Interfaces:**
- Consumes: `Money`, `minorDigits()` (Task 1).
- Produces:
  - `object CurrencyCatalog { fun all(locale: Locale): List<Currency>; fun symbol(c: Currency, locale: Locale): String; fun displayName(c: Currency, locale: Locale): String; fun forCountry(countryCode: String): Currency?; fun countries(locale: Locale): List<Country>; fun isSupported(code: String): Boolean }`, `data class Country(val code: String, val name: String, val currency: Currency)`.
    - `all`: `Currency.getAvailableCurrencies()` excluyendo códigos `X**` (XAU, XDR, XXX…), ordenado por `displayName` con `Collator` del locale.
    - Símbolos preferidos fijos: `USD "$"`, `GTQ "Q"`, `HNL "L"`, `NIO "C$"`, `CRC "₡"`, `PAB "B/."`, `BZD "BZ$"`, `SVC "₡"`, `MXN "MX$"`, `EUR "€"`; el resto `getSymbol(locale)`.
    - `countries`: `Locale.getISOCountries()` con moneda resoluble, ordenado por nombre localizado.
  - `class CurrencyFormatter(val main: Currency = USD, private val locale: Locale = Locale.getDefault())` con `fun format(money: Money): String`, `fun formatMain(minor: Long): String`, `fun formatCompact(money: Money): String`. Separadores siempre `Locale.US`; decimales según la moneda; negativos con `−` antes del símbolo (igual que hoy). `LocalCurrencyFormatter` se mantiene.
  - `UserPreferences`: `val mainCurrency: Flow<Currency>` (clave `main_currency`, default `USD`), `suspend fun setMainCurrency(code: String)`, `val defaultAccountId: Flow<Long?>`, `suspend fun setDefaultAccountId(id: Long)`, `val pendingMainCurrency: Flow<String?>`, `suspend fun setPendingMainCurrency(code: String?)`, `val lastRateSyncAt: Flow<Long?>`, `suspend fun setLastRateSyncAt(millis: Long)`.

- [ ] **Step 1: Tests**
  - `CurrencyCatalogTest`: `forCountry("GT") == GTQ`, `forCountry("SV") == USD`, `forCountry("PA")` es `PAB` o `USD` (aceptar el que devuelva el JDK, documentado), `all(Locale.US)` no contiene `XAU` y está ordenado, `symbol(GTQ, Locale("es")) == "Q"`.
  - `CurrencyFormatterTest`: `format(Money(183035, GTQ)) == "Q1,830.35"`, `format(Money(-1200, USD)) == "−$12.00"`, `format(Money(1500, JPY))` termina en `"1,500"` sin decimales, `format(Money(1234, BHD))` termina en `"1.234"`, `formatCompact(Money(120000_00, GTQ)) == "Q120k"`.
- [ ] **Step 2:** correr ambas clases → FAIL.
- [ ] **Step 3:** Implementar. Los usos de `currencySymbol`/`selectedCurrency` en `MainActivity.kt` y `GeneralSettingsViewModel.kt` pasan a `mainCurrency`; el selector de moneda de `GeneralSettingsScreen.kt` se elimina (lo reemplaza Task 11) dejando la app compilando.
- [ ] **Step 4:** `./gradlew testDebugUnitTest` → PASS (todo).
- [ ] **Step 5:** Commit `feat: add currency catalog and money-aware formatter`.

---

### Task 3: Base de datos nueva y barrido a `Long`

La tarea más grande: cambia el esquema y todos sus consumidores en un solo paso para que la app
compile y funcione en una sola moneda al terminar. Sin tasas todavía: toda tasa es `IDENTITY`.

**Files:**
- Modify: `app/build.gradle.kts` (testImplementation `org.robolectric:robolectric:4.14.1`, `androidx.room:room-testing:2.8.4`, `androidx.test:core:1.6.1`; `testOptions.unitTests.isIncludeAndroidResources = true`; `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`), `gradle/libs.versions.toml`
- Create: `data/local/entity/AccountEntity.kt`, `data/local/entity/ExchangeRateEntity.kt`, `data/local/dao/AccountDao.kt`, `data/local/dao/ExchangeRateDao.kt`, `domain/model/Account.kt`, `data/repository/AccountRepository.kt`
- Modify: `data/local/entity/TransactionEntity.kt`, `data/local/entity/LoanEntity.kt`, `data/local/database/BoxWalletDatabase.kt`, `data/local/database/Converters.kt`, `data/local/dao/TransactionDao.kt`, `data/local/dao/LoanDao.kt`, `di/DatabaseModule.kt`, `domain/model/Transaction.kt`, `domain/model/Loan.kt`, `domain/model/FinancialSummary.kt`, `domain/model/TransactionTotals.kt`, `data/repository/TransactionRepository.kt`, `data/repository/LoanRepository.kt`, `data/repository/DataManagementRepository.kt`, y los consumidores de montos: `ui/screens/analytics/{AnalyticsViewModel,AnalyticsScreen,ReportMath}.kt`, `ui/components/{BalancePanel,LoanCard,TransactionItem,Expressive,TransactionSheet,LoanSheet}.kt`, `ui/components/charts/*.kt`, `ui/screens/{transaction/TransactionFormViewModel,loans/LoanFormViewModel,loans/LoanDetailScreen,loans/LoansViewModel,search/SearchViewModel,transactions/*}.kt`, `worker/ReminderWorker.kt`, `analytics/AnalyticsHelper.kt`, `util/ExcelReportGenerator.kt` (solo compilar: montos vía `toMajor()`; columnas nuevas en Task 14). `BackupRepository` compila con entidades nuevas (formato en Task 13). `ui/screens/onboarding/OnboardingViewModel.kt` llama `AccountRepository.ensureDefaultAccount()` al completar.
- Create: `app/schemas/com.boxdev.boxwallet.data.local.database.BoxWalletDatabase/1.json` (generado)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/local/dao/TransactionDaoTest.kt`; actualizar `FinancialSummaryTest`, `LoanTest`, `TransactionFormStateTest`, `LoanFormStateTest`, `ReportMathTest`, `ExcelReportGeneratorTest`.

**Interfaces:**
- Consumes: `Money`, `ExchangeRate`, `RateSource` (Task 1); `UserPreferences.mainCurrency/defaultAccountId` (Task 2).
- Produces:
  - Entidades exactamente como §2.3 del spec (nombres de columna literales). `AccountEntity(id, name, type: String, currencyCode, isArchived, sortOrder, createdAt)`; `ExchangeRateEntity(date: Long /*epochDay*/, currencyCode, unitsPerUsd: BigDecimal)` PK compuesta; `LoanEntity` con `totalAmountMinor: Long`, `currencyCode: String`, `accountId: Long?`. FKs e índices según spec.
  - `Converters`: `BigDecimal <-> String`, `RateSource <-> String`.
  - `@Database(entities=[Account, Transaction, Category, Loan, ExchangeRate], version = 1, exportSchema = true)`, `DATABASE_NAME = "boxwallet.db"`, `LEGACY_DATABASE_NAME = "boxwallet_database"`. `DatabaseModule` llama `context.deleteDatabase(LEGACY_DATABASE_NAME)` antes de construir y ya no usa fallback destructivo.
  - `TransactionDao`: los `SUM` pasan a `SUM(amountMinor)` devolviendo `Flow<Long?>`; nuevo `@Query fun recentCurrencies(limit: Int): Flow<List<CurrencyUsage>>` (`SELECT currencyCode, COUNT(*) AS uses, MAX(date) AS lastUsed FROM transactions GROUP BY currencyCode ORDER BY uses DESC, lastUsed DESC LIMIT :limit`), `suspend fun countAll(): Int`, `suspend fun getWithApproximateRates(): List<TransactionEntity>`, `suspend fun distinctEpochDaysWhereCurrencyNot(code: String): List<Long>`.
  - `ExchangeRateDao`: `upsertAll(List<ExchangeRateEntity>)`, `rateOn(day: Long, code: String): ExchangeRateEntity?`, `nearestBefore(day, code)`, `nearestAfter(day, code)`, `series(code: String, fromDay: Long): Flow<List<ExchangeRateEntity>>`, `hasDay(day: Long): Boolean`.
  - `data class CurrencyUsage(val currencyCode: String, val uses: Int, val lastUsed: Long)`.
  - Dominio: `Transaction(id, accountId, type, date, description, categoryId, loanId, installmentNumber, original: Money, accountAmount: Money, accountRate: ExchangeRate, amount: Money /*principal*/, mainRate: ExchangeRate, isCleared, hasReminder, reminderSecondsBefore, isRecurrent, recurrenceFrequency, recurrenceInterval, nextOccurrenceDate, parentRecurrenceId)`. `toDomain(accountCurrency: Currency, main: Currency)` / `toEntity()`.
  - `Loan.totalAmount: Money`; `installmentAmount`, `remainingAmount`, `settledAmount` como `Money` (cuotas: división entera, la última absorbe el residuo — `installmentAmount(number: Int): Money`). `LoanTotals(toPay: Long, toCollect: Long)` en moneda principal, sumando cuotas por su `amount` real.
  - `FinancialSummary` y `total()/net()` en `Long` (minor de la principal).
  - `AccountRepository`: `suspend fun ensureDefaultAccount(): Account` (crea "Principal"/`R.string.account_default_name`, `CHECKING`, moneda principal; guarda id en DataStore; idempotente), `suspend fun defaultAccount(): Account`, `suspend fun count(): Int`.

- [ ] **Step 1: Test DAO** (`TransactionDaoTest`, Robolectric + `Room.inMemoryDatabaseBuilder`)
  - `sums use amountMinor` → dos gastos limpios de 1050 y 250 → `getTotalExpense().first() == 1300L`.
  - `recentCurrencies orders by uses then recency` → GTQ×3, USD×1 (más reciente), EUR×1 (más viejo) → `[GTQ, USD, EUR]`.
  - `account delete is restricted while transactions exist` → borrar cuenta con movimientos lanza `SQLiteConstraintException`.
- [ ] **Step 2:** correr `TransactionDaoTest` → FAIL.
- [ ] **Step 3:** Implementar entidades, DAOs, DB, módulo y barrido. Los charts convierten con `toMajorDouble()` en el borde. `ReminderWorker`/`TransactionRepository` formatean el monto del recordatorio con `CurrencyFormatter.format(original)`.
- [ ] **Step 4:** Actualizar las pruebas existentes a `Long`/`Money` manteniendo sus casos; `./gradlew testDebugUnitTest` → PASS. Verificar que exista `app/schemas/.../1.json`.
- [ ] **Step 5:** `./gradlew assembleDebug` → BUILD SUCCESSFUL. Instalar y comprobar a mano: onboarding, crear gasto, ver Home y Reports.
- [ ] **Step 6:** Commit `feat: rebuild database with minor-unit money, accounts and rate history` (incluye `app/schemas`). Sin `!`: la versión aún no está en producción y un breaking change subiría la primera versión pública a 2.0.0.

---

### Task 4: Cliente de la API de tasas

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/data/rates/ExchangeRateApi.kt`, `.../data/rates/HttpTransport.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/data/rates/ExchangeRateApiTest.kt`

**Interfaces:**
- Consumes: `CurrencyCatalog.isSupported` (Task 2).
- Produces:
  - `fun interface HttpTransport { fun get(url: String): HttpResult }`, `sealed interface HttpResult { data class Ok(val body: String); data class Status(val code: Int); data class Failure(val error: IOException) }`. `UrlConnectionTransport` (timeouts 10 s) como implementación real.
  - `data class RateSnapshot(val date: LocalDate, val unitsPerUsd: Map<String, BigDecimal>)` (códigos en mayúscula).
  - `class ExchangeRateApi(transport: HttpTransport, onInvalid: (String) -> Unit = {})` con `suspend fun fetch(date: LocalDate? = null): RateSnapshot?` (corre en `Dispatchers.IO`; `null` si ambas URLs fallan o la fecha es < 2024-03-02). Hilt provee `onInvalid = { analytics.logEvent("rate_payload_invalid", mapOf("reason" to it)) }`.

- [ ] **Step 1: Tests** con transporte falso que registra URLs:
  - `latest uses jsdelivr` → URL pedida exacta `https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json`; snapshot `date == 2026-11-07`, `unitsPerUsd["GTQ"] == BigDecimal("7.64040354")`.
  - `falls back to pages on 404 and on IOException` → segunda URL `https://2025-01-15.currency-api.pages.dev/v1/currencies/usd.json` para `fetch(LocalDate.of(2025,1,15))`.
  - `filters non ISO and non positive` → payload con `"btc"`, `"gtq": 0`, `"eur": -1`, `"usd": 1` → solo `USD`.
  - `malformed json returns null and reports` → `onInvalid` llamado una vez.
  - `date before history returns null without requests`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar (Gson `JsonParser`, claves → `uppercase(Locale.ROOT)`). **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat: add exchange rate API client with CDN fallback`.

---

### Task 5: Repositorio de tasas y resolver

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/money/ExchangeRateResolver.kt`, `app/src/main/java/com/boxdev/boxwallet/data/rates/ExchangeRateRepository.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/money/ExchangeRateResolverTest.kt`, `app/src/test/java/com/boxdev/boxwallet/data/rates/ExchangeRateRepositoryTest.kt`

**Interfaces:**
- Consumes: `ExchangeRateApi`, `RateSnapshot` (Task 4); `ExchangeRateDao` (Task 3); `ExchangeRate`, `RateSource` (Task 1).
- Produces:
  - `interface RateLookup { suspend fun exact(day: LocalDate, code: String): BigDecimal?; suspend fun nearest(day: LocalDate, code: String): BigDecimal?; suspend fun download(day: LocalDate): Boolean }` (`download` = traer y guardar; `false` sin red o sin histórico).
  - `class ExchangeRateResolver(lookup: RateLookup)` con `suspend fun resolve(day: LocalDate, from: Currency, to: Currency): ExchangeRate?` — `IDENTITY` si `from == to`; tasa cruzada `unitsPerUsd(to) / unitsPerUsd(from)` con `MathContext.DECIMAL64`; reglas §3.3 (exact → download+exact → nearest `APPROXIMATE` → `null` = pedir manual).
  - `class ExchangeRateRepository @Inject constructor(dao, api, prefs) : RateLookup` con además `suspend fun syncLatest(): Boolean` (guarda y actualiza `lastRateSyncAt`), `fun series(code: String, main: Currency, from: LocalDate): Flow<List<Pair<LocalDate, BigDecimal>>>` (tasa 1 code = x main), `suspend fun latest(code: String, main: Currency): Pair<BigDecimal, BigDecimal?>?` (actual y del día anterior, para la variación).

- [ ] **Step 1: Tests del resolver** con un `RateLookup` falso en memoria:
  - `same currency is identity`.
  - `cross rate via usd` → GTQ=7.64, EUR=0.8976 en el día → `resolve(GTQ→EUR).value` == `0.8976/7.64` a DECIMAL64, `AUTO`.
  - `downloads missing day once` → `exact` vacío, `download` lo llena → `AUTO`, contador de `download == 1`.
  - `offline uses nearest as approximate`.
  - `no rate at all returns null`.
  - `date before 2024-03-02 never downloads and is approximate` (Review Focus 2).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar resolver.
- [ ] **Step 4: Test del repositorio** (Robolectric, DB en memoria, API con transporte falso): `syncLatest stores snapshot and sync time`; `nearest prefers previous day then next`.
- [ ] **Step 5:** Implementar repositorio; ambas clases PASS.
- [ ] **Step 6:** Commit `feat: resolve exchange rates by transaction date with approximate fallback`.

---

### Task 6: Worker diario y corrección de tasas aproximadas

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/worker/ExchangeRateSyncWorker.kt`, `app/src/main/java/com/boxdev/boxwallet/data/rates/ApproximateRateFixer.kt`
- Modify: `BoxWalletApplication.kt` (encolar periódico único `exchange_rate_sync`, 24 h, `NetworkType.CONNECTED`, backoff exponencial; y un `OneTime` al iniciar si `lastRateSyncAt` > 24 h)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/rates/ApproximateRateFixerTest.kt`

**Interfaces:**
- Consumes: `ExchangeRateRepository`, `ExchangeRateResolver` (Task 5); `TransactionDao.getWithApproximateRates`, `AccountDao`, `UserPreferences.mainCurrency`.
- Produces: `class ApproximateRateFixer @Inject constructor(...) { suspend fun fix(): Int }` — por cada movimiento con `accountRateSource` o `mainRateSource == APPROXIMATE`, re-resuelve esa tasa; si queda `AUTO`, recalcula montos y guarda; nunca toca `MANUAL`; devuelve cuántos corrigió. El Worker = `syncLatest()` + `fix()`; `Result.retry()` si `syncLatest` falla.

- [ ] **Step 1: Tests** (Robolectric + DB en memoria):
  - `upgrades approximate to auto when exact day appears` → montos recalculados, devuelve 1.
  - `leaves manual untouched` → movimiento con `accountRate MANUAL` y `mainRate APPROXIMATE`: solo cambia `mainRate`.
  - `keeps approximate when still no exact rate`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar fixer, Worker (`@HiltWorker`) y programación. **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat: sync exchange rates daily and correct approximate rates`.

---

### Task 7: Cálculo de montos al guardar (form, recurrencias, cuotas)

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/money/TransactionAmounts.kt`
- Modify: `ui/screens/transaction/TransactionFormViewModel.kt`, `data/repository/TransactionRepository.kt` (`createNextRecurrence`), `ui/screens/loans/LoanFormViewModel.kt`, `data/repository/LoanRepository.kt` (cuotas en moneda del préstamo)
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/money/TransactionAmountsTest.kt`; ampliar `TransactionFormStateTest`

**Interfaces:**
- Consumes: `ExchangeRateResolver` (Task 5), `Money` (Task 1), `Transaction` (Task 3).
- Produces:
  - `data class AmountInput(val original: Money, val day: LocalDate, val accountCurrency: Currency, val main: Currency, val manualAccountRate: BigDecimal? = null)`.
  - `class TransactionAmounts(resolver: ExchangeRateResolver) { suspend fun compute(input: AmountInput): AmountResult }`, `sealed interface AmountResult { data class Ready(val accountAmount: Money, val accountRate: ExchangeRate, val amount: Money, val mainRate: ExchangeRate); data object NeedsManualRate }`.
  - `fun Transaction.needsRateRefresh(previous: Transaction): Boolean` — `true` solo si cambió `original.currency`, el día o el monto con tasa no `MANUAL`.
  - Form state añade `currency: Currency`, `manualRate: String?`, `equivalent: String?`, `rate: ExchangeRate?`, `needsManualRate: Boolean`; `TransactionFormViewModel` expone `updateCurrency(Currency)`, `updateManualRate(String)`, `updateEquivalent(String)`, `useAutomaticRate()`.

- [ ] **Step 1: Tests** (`TransactionAmountsTest`, resolver falso):
  - `same currencies give identity rates`.
  - `foreign purchase converts twice` → €20.00 en cuenta USD (1.0852) con principal GTQ (7.64) → `accountAmount = 2170` USD, `amount = 16579` GTQ.
  - `manual account rate marks MANUAL and skips resolver`.
  - `jpy to bhd rounds to three decimals` (Review Focus 1).
  - `needsRateRefresh false when only description changed` (Review Focus 3).
  - `recurrence occurrence resolves its own date unless template rate is manual` (Review Focus 4) — probar con `TransactionRepository.createNextRecurrence` contra resolver falso.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar y cablear: form, recurrencias (re-resolver por la fecha nueva; copiar si `MANUAL`), cuotas de préstamo (`original` en moneda del préstamo, resolver por fecha de pago al marcarla pagada). **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat: compute frozen conversions when saving movements`.

---

### Task 8: Selector de moneda y fila de conversión en el formulario

**Files:**
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/components/CurrencyPickerSheet.kt`, `app/src/main/java/com/boxdev/boxwallet/ui/components/ConversionRow.kt`, `app/src/main/java/com/boxdev/boxwallet/domain/money/RecentCurrencies.kt`
- Modify: `ui/components/TransactionSheet.kt`, `ui/components/FormFields.kt` (campo de monto con chip), `values/strings.xml`, `values-es/strings.xml`
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/money/RecentCurrenciesTest.kt`

**Interfaces:**
- Consumes: `CurrencyCatalog` (Task 2), `TransactionDao.recentCurrencies` (Task 3), acciones del VM (Task 7).
- Produces: `fun pickerSections(all: List<Currency>, recent: List<CurrencyUsage>, query: String, locale: Locale): PickerSections` con `data class PickerSections(val recent: List<Currency>, val all: List<Currency>)` — búsqueda por código, nombre o símbolo, sin acentos ni mayúsculas; `recent` máx. 5 y excluido de `all` solo cuando `query` está vacío.
  `@Composable fun CurrencyPickerSheet(selected: Currency, sections: PickerSections, onQuery: (String) -> Unit, onPick: (Currency) -> Unit, onDismiss: () -> Unit)`; `@Composable fun ConversionRow(rate: ExchangeRate?, from: Currency, to: Currency, day: LocalDate, equivalent: String, onRateChange, onEquivalentChange, onUseAutomatic)`.

- [ ] **Step 1: Tests** `RecentCurrenciesTest`: orden recibido se respeta; `query "quet"` encuentra GTQ; `query "₡"` encuentra CRC y SVC; con query, `recent` vacío.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar función y composables (UI según §5.1 del spec; etiqueta "Tasa aproximada"; si `needsManualRate`, la fila exige tasa antes de guardar). **Step 4:** PASS.
- [ ] **Step 5:** Verificación manual: gasto en EUR, editar tasa, editar equivalente, "Usar tasa automática", cambiar fecha. Commit `feat: pick a currency and edit the rate when logging a movement`.

---

### Task 9: Mostrar monto original y equivalente

**Files:**
- Modify: `ui/components/TransactionItem.kt`, `ui/components/LoanCard.kt`, `ui/screens/loans/LoanDetailScreen.kt`, `ui/components/LoanSheet.kt` (selector de moneda, bloqueado si hay pagos), `ui/screens/loans/LoanFormViewModel.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/util/CurrencyFormatterTest.kt` (ampliar)

**Interfaces:**
- Consumes: `CurrencyFormatter` (Task 2), `Transaction`/`Loan` (Task 3).
- Produces: `fun CurrencyFormatter.withEquivalent(original: Money, inMain: Money): Pair<String, String?>` — segundo valor `"≈ Q168.20"` o `null` si `original.currency == main`.

- [ ] **Step 1: Test** `withEquivalent` → `(€20.00, "≈ Q165.79")`; misma moneda → segundo `null`; JPY sin decimales.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar y aplicar en las vistas (§5.2 y §5.6). **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat: show original amount with its estimated equivalent`.

---

### Task 10: Onboarding por país

**Files:**
- Modify: `ui/screens/onboarding/OnboardingScreen.kt`, `ui/screens/onboarding/OnboardingViewModel.kt`, strings
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/onboarding/OnboardingCountryTest.kt`

**Interfaces:**
- Consumes: `CurrencyCatalog.countries/forCountry` (Task 2), `UserPreferences.setMainCurrency`, `AccountRepository.ensureDefaultAccount` (Task 3), `CurrencyPickerSheet` (Task 8).
- Produces: `fun initialCountry(deviceRegion: String?, countries: List<Country>): Country` (región del teléfono; si no existe, `US`). VM: `selectCountry(code)`, `overrideCurrency(Currency)`; `completeOnboarding` guarda la moneda **antes** de `ensureDefaultAccount()`.

- [ ] **Step 1: Test** `initialCountry("GT")` → Guatemala/GTQ; `initialCountry(null)` → US; región inexistente → US.
- [ ] **Step 2:** FAIL. **Step 3:** Paso "¿Dónde vives?" según §5.4. **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat: choose country and main currency during onboarding`.

---

### Task 11: Settings > Monedas y gráfico de tasas

**Files:**
- Create: `ui/screens/settings/CurrenciesScreen.kt`, `ui/screens/settings/CurrenciesViewModel.kt`, `ui/screens/settings/CurrencyDetailScreen.kt`, `ui/components/charts/RateLineChart.kt`
- Modify: `ui/navigation/NavRoutes.kt`, `ui/navigation/NavGraph.kt`, `ui/screens/settings/SettingsScreen.kt` (entrada "Monedas"), strings
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/settings/RateStatsTest.kt`

**Interfaces:**
- Consumes: `ExchangeRateRepository.series/latest/syncLatest` (Task 5), `RecentCurrencies` (Task 8), `UserPreferences.lastRateSyncAt`.
- Produces: `data class RateStats(val current: BigDecimal, val min: BigDecimal, val max: BigDecimal, val dailyChangePercent: BigDecimal?)`, `fun rateStats(series: List<Pair<LocalDate, BigDecimal>>): RateStats?`; `enum class RateRange(val days: Long?) { M1(30), M3(90), Y1(365), ALL(null) }`. La entrada "Moneda principal" navega al flujo de Task 12 (en esta tarea muestra el valor y queda deshabilitada).

- [ ] **Step 1: Test** `rateStats`: serie vacía → `null`; `[7.60, 7.70, 7.64]` → min 7.60, max 7.70, current 7.64, cambio ≈ −0.78% (2 decimales HALF_EVEN).
- [ ] **Step 2:** FAIL. **Step 3:** Pantallas según §5.5 (incluye nota de privacidad y "Actualizar ahora"). **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat: add currencies settings with rate history chart`.

---

### Task 12: Cambio de moneda principal

**Files:**
- Create: `data/rates/MainCurrencyChanger.kt`, `worker/MainCurrencyChangeWorker.kt`, `ui/screens/settings/MainCurrencyChangeScreen.kt`
- Modify: `data/repository/BackupRepository.kt` (`suspend fun writeLocalSnapshot(): File` → `filesDir/backups/pre-currency-change.json`, reemplaza el anterior), `BoxWalletApplication.kt` (completar pendiente al arrancar), `CurrenciesScreen.kt` (habilitar entrada), strings
- Test: `app/src/test/java/com/boxdev/boxwallet/data/rates/MainCurrencyChangerTest.kt`

**Interfaces:**
- Consumes: `ExchangeRateResolver`, `ExchangeRateRepository.download` (Task 5), `AccountDao`, `TransactionDao.distinctEpochDaysWhereCurrencyNot`, `UserPreferences.pendingMainCurrency/setMainCurrency`, `BackupRepository.writeLocalSnapshot`.
- Produces:
  - `data class ChangePreview(val transactions: Int, val loans: Int, val from: Currency, val to: Currency)`; `data class ChangeSummary(val converted: Int, val approximate: Int, val manualReplaced: Int)`.
  - `class MainCurrencyChanger { suspend fun preview(to: Currency): ChangePreview; suspend fun prefetch(to: Currency, onProgress: (done: Int, total: Int) -> Unit): Boolean; suspend fun apply(to: Currency): ChangeSummary; suspend fun completePending() }`.
    - `apply`: `setPendingMainCurrency(to)` → `writeLocalSnapshot()` → `database.withTransaction { … reglas §4 paso 4 (una cuenta / varias) … }` → `setMainCurrency(to)` → `setPendingMainCurrency(null)`.
    - `completePending`: si hay pendiente y la cuenta por defecto ya está en esa moneda (el commit ocurrió), escribe `setMainCurrency`; si no, limpia la marca.
  - Worker expedited con `setProgress` (`phase`, `done`, `total`); la pantalla observa `WorkInfo`.

- [ ] **Step 1: Tests** (Robolectric + DB en memoria, resolver falso):
  - `single account follows main currency` → cuenta USD con gastos en USD y EUR, cambio a GTQ: cuenta queda GTQ, `accountAmount == amount` en cada fila, `original` intacto.
  - `manual rates are replaced and counted`.
  - `multiple accounts keep their balances` → dos cuentas (USD, GTQ): `SUM(accountAmountMinor)` por cuenta sin cambios, solo `amountMinor` cambia.
  - `failure mid transaction rolls back` → resolver que lanza en la fila 3 → ningún cambio, `mainCurrency` sigue USD.
  - `completePending finishes after commit` y `completePending clears when commit never happened` (Review Focus 5).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar changer, worker, pantalla (advertencia con conteos, progreso de dos fases sin cancelar durante la transacción, resumen). **Step 4:** PASS.
- [ ] **Step 5:** Verificación manual: sembrar ~1,000 movimientos (script de debug o test instrumentado ad hoc, no se commitea) y cambiar USD → GTQ; repetir en modo avión (debe detenerse en fase 1 sin cambios).
- [ ] **Step 6:** Commit `feat: change main currency by recalculating all movements atomically`.

---

### Task 13: Backup v3 y restore de v2

**Files:**
- Modify: `domain/model/BackupData.kt`, `data/repository/BackupRepository.kt`, `ui/screens/settings/BackupViewModel.kt`, `ui/screens/onboarding/OnboardingViewModel.kt` (restore)
- Create: `data/repository/LegacyBackupConverter.kt`, `app/src/test/resources/backup-v2.json` (fixture con gastos, ingresos, recurrente, préstamo con cuotas, categoría)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/LegacyBackupConverterTest.kt`

**Interfaces:**
- Consumes: entidades (Task 3), `MainCurrencyChanger` (Task 12).
- Produces: `BackupData(version = 3, createdAt, mainCurrencyCode, accounts, transactions, categories, loans)`; `object LegacyBackupConverter { fun fromV2(json: JsonObject, main: Currency, accountId: Long): BackupData }`; `BackupRepository.restoreBackup` despacha por `version` y, si `mainCurrencyCode` difiere del dispositivo, devuelve `RestoreResult.CurrencyMismatch(backup, device)` para que la UI pregunte (conservar la del backup = `setMainCurrency`; conservar la del dispositivo = restaurar y luego `MainCurrencyChanger.apply`).

- [ ] **Step 1: Tests**: fixture v2 → montos `12.5` → `1250`, `0.1+0.2`-style `0.30000000000000004` → `30`; todas las filas con `accountId` dado, `currencyCode = main`, tasas `IDENTITY`; préstamo con `totalAmountMinor` y `currencyCode`; versión desconocida → excepción clara.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS.
- [ ] **Step 5:** Verificación manual: restaurar un backup real de Drive creado con la build 18.
- [ ] **Step 6:** Commit `feat: back up multi-currency data and restore legacy backups`.

---

### Task 14: Excel con monedas

**Files:**
- Modify: `util/ExcelReportGenerator.kt`, strings
- Test: `app/src/test/java/com/boxdev/boxwallet/util/ExcelReportGeneratorTest.kt` (ampliar)

**Interfaces:**
- Consumes: `Transaction` (Task 3), `CurrencyFormatter` (Task 2).
- Produces: columnas en este orden tras las actuales: "Monto original", "Moneda", "Tasa" (efectiva original → principal = `amount / original`), "Monto en moneda principal" (títulos localizados). Montos como números con los decimales de su moneda; tasa con hasta 6 decimales.

- [ ] **Step 1: Test** fila de €20.00 → celdas `20.00`, `EUR`, `8.289500`, `165.79`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS.
- [ ] **Step 5:** Commit `feat: include currency columns in the Excel report`.

---

### Task 15: Cierre

**Files:**
- Modify: `README.md` (sección de funcionalidades/privacidad), `PRODUCT.md` (Capabilities: multimoneda, tasas públicas diarias), `fastlane/metadata/android/*/` si aplica la nota de privacidad.

- [ ] **Step 1:** `./gradlew testDebugUnitTest` → todo PASS; `./gradlew assembleRelease` (o `bundleRelease` local sin firmar) → R8 no rompe Gson/Room (añadir reglas `-keep` si falla el parseo en release).
- [ ] **Step 2:** Verificación manual completa del spec §7 en dispositivo y en Pixel Fold (desplegado/plegado).
- [ ] **Step 3:** Commit `docs: document multi-currency support`.
- [ ] **Step 4:** `git push -u origin feat/multi-currency` y `gh pr create --base main --title "feat: multi-currency movements with daily exchange rates"` (cuerpo: resumen, aviso a testers de hacer backup antes de instalar, terminar con `🤖 Generated with [Claude Code](https://claude.com/claude-code)`). Esperar **PR Checks** verde.
