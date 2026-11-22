# Préstamos dentro de las cuentas — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un préstamo (*Le presté* / *Me prestaron*) vive en una cuenta, su desembolso y sus pagos mueven el saldo de esa cuenta, no cuentan como ingreso o gasto, y la pestaña Préstamos desaparece.

**Architecture:** Los préstamos dejan de generar una fila por cuota. Cada préstamo tiene una fila de desembolso y una fila por pago, de tipos nuevos `LOAN_IN` / `LOAN_OUT` (como `TRANSFER_*`: mueven saldo y no cuentan en reportes). El plan de cuotas pasa a ser un cálculo puro (`LoanSchedule`) que reparte lo pagado sobre las cuotas en orden. La UI cuelga de la cuenta (sección en el detalle de cuenta), de Inicio y de Reportes. La lista de préstamos queda como pantalla secundaria.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 Expressive), Room (esquema v1, sin migraciones), Hilt, Robolectric + JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-21-loans-in-accounts-design.md`

## Global Constraints

- Base de datos **siempre en `version = 1`**, sin migraciones (`BoxWalletDatabase.kt:32`). Los cambios se hacen directo en las entidades.
- Respaldo `BackupData.CURRENT_VERSION = 6`. Solo se acepta el formato actual.
- `LOAN_IN` / `LOAN_OUT` **nunca** cuentan en reportes: `countsInReports` sigue siendo solo `INCOME`/`EXPENSE`.
- Préstamos solo en cuentas que **no** son `AccountType.CREDIT_CARD` y no están archivadas (al crear; al editar, la cuenta ya usada se permite aunque se haya archivado después, como en `TransferRepository.requireAccount`).
- Un préstamo usa **una sola cuenta**: el desembolso y todos los pagos van a `loan.accountId`. Su moneda es la de esa cuenta.
- Un pago es `> 0` y `≤` lo que falta. El total editado no puede quedar por debajo de lo pagado.
- Toda escritura de movimientos pasa por `MovementWriteGuard.write` y sus montos por `TransactionAmounts.forSave` / `compute`.
- Textos en inglés (`values/strings.xml`) y español (`values-es/strings.xml`).
- Sin préstamos perdidos, sin condonación, sin intereses aparte, sin notificaciones.
- Rama `feat/loans-in-accounts`. Un commit por tarea, en Conventional Commits. Título del PR: `feat: préstamos dentro de tus cuentas, con cuotas y abonos libres`.
- Verificación por tarea: `./gradlew testDebugUnitTest --tests <Clase>`. Al final, la suite completa y `./gradlew assembleDebug`.

## Review Focus

1. **Pago con fecha anterior al desembolso:** se rechaza con `IllegalArgumentException`, porque un abono anterior al préstamo no tiene sentido. Test en Task 3.
2. **Editar el préstamo a un total menor que lo pagado:** se rechaza y no cambia nada. Test en Task 3.
3. **Cambiar la cuenta a otra moneda:** se rechaza en el repositorio, no solo en la UI. Test en Task 3.
4. **Borrar una cuenta que tiene préstamos:** queda bloqueado por RESTRICT y el mensaje dice por qué. Test en Task 1, y mensaje en Task 8.
5. **Un préstamo liquidado y después un pago borrado:** vuelve a estar activo y "falta" sube. Test en Task 2.

---

### Task 1: Esquema y tipos de movimiento

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/entity/LoanEntity.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/entity/TransactionEntity.kt:95-125`
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/dao/AccountDao.kt:16`
- Test: `app/src/test/java/com/boxdev/boxwallet/data/local/database/SchemaTest.kt`, `app/src/test/java/com/boxdev/boxwallet/data/repository/AccountRepositoryTest.kt`

**Interfaces:**
- Produces: `LoanEntity(id, name, description, totalAmountMinor, currencyCode, accountId: Long, startDate: Long, loanType: String, totalInstallments: Int = 0, frequency: String = "MONTHLY", frequencyInterval: Int = 1, firstDueDate: Long? = null, expectedDate: Long? = null)`. Se eliminan `kind` y `categoryId`. La FK a `accounts` pasa a `onDelete = RESTRICT`.
- Produces: `TransactionType.LOAN_IN`, `TransactionType.LOAN_OUT`, `val TransactionType.isLoan: Boolean`. `isInflow` incluye `LOAN_IN`.

- [ ] **Step 1: Tests que fallan**
  - `SchemaTest`: `loan rows need an account and block deleting it`. Inserta una cuenta y un `LoanEntity` en ella; `accountDao().delete(...)` lanza `SQLiteConstraintException`.
  - `AccountRepositoryTest`: `loan movements move the balance`. En una cuenta con saldo 0, inserta `LOAN_IN` de 10000 y `LOAN_OUT` de 1100 (con `accountAmountMinor`). `assertEquals(8900, balance)`.
  - `TransactionTypeTest` (nuevo, en `data/local/entity/`): `LOAN_IN.isInflow`, `!LOAN_OUT.isInflow`, `!LOAN_IN.countsInReports`, `!LOAN_OUT.countsInReports`, `LOAN_IN.isLoan`.
- [ ] **Step 2:** Correr `./gradlew testDebugUnitTest --tests '*SchemaTest' --tests '*AccountRepositoryTest' --tests '*TransactionTypeTest'`. Esperado: FAIL (no compila).
- [ ] **Step 3:** Hacer los cambios de *Produces*. En `AccountDao.BALANCE`, agregar `'LOAN_IN'` a la lista `IN (...)`. Hacer `grep -rn "categoryId\|\.kind\|kind =" ` sobre los usos de `LoanEntity` y dejar compilando los sitios que todavía usan los campos borrados. Para que compile mientras llegan las tareas siguientes, `Loan.toEntity()` puede quedar provisional (Task 2 lo reescribe).
- [ ] **Step 4:** Correr los mismos tests. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: add loan movement types and tie loans to an account`.

### Task 2: Modelo de dominio y reparto de cuotas

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/domain/model/Loan.kt` (reescritura)
- Create: `app/src/main/java/com/boxdev/boxwallet/domain/model/LoanSchedule.kt`
- Test: `app/src/test/java/com/boxdev/boxwallet/domain/model/LoanTest.kt` (reescritura), `app/src/test/java/com/boxdev/boxwallet/domain/model/LoanScheduleTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class LoanPlan(val installments: Int, val frequency: RecurrenceFrequency, val interval: Int, val firstDue: LocalDate)
  data class Loan(
      val id: Long = 0, val name: String, val description: String = "",
      val loanType: LoanType,            // INCOME = Le presté, EXPENSE = Me prestaron
      val total: Money,                  // moneda de la cuenta
      val accountId: Long,
      val date: LocalDateTime,           // desembolso
      val plan: LoanPlan? = null,
      val expectedDate: LocalDate? = null,
      val paid: Money = Money(0, total.currency),   // Σ pagos
      val remainingMainMinor: Long = 0,
  ) {
      val remaining: Money; val isSettled: Boolean
      fun schedule(): List<ScheduledInstallment>     // vacío sin plan
      fun nextDue(): ScheduledInstallment?           // primera no pagada
      fun status(today: LocalDate): LoanStatus       // PAID | OVERDUE | ACTIVE
      val disbursementType: TransactionType          // INCOME→LOAN_OUT, EXPENSE→LOAN_IN
      val paymentType: TransactionType               // el opuesto
  }
  enum class InstallmentState { PAID, PARTIAL, PENDING, OVERDUE }
  data class ScheduledInstallment(val number: Int, val due: LocalDate, val amount: Money, val covered: Money, val state: InstallmentState) { val uncovered: Money }
  fun LoanEntity.toDomain(paidMinor: Long, remainingMainMinor: Long): Loan
  fun Loan.toEntity(): LoanEntity
  fun List<Loan>.outstandingTotals(): LoanTotals   // se mantiene, sobre !isSettled
  ```
- `LoanSchedule.kt` contiene `fun allocate(total: Money, plan: LoanPlan, paid: Money, today: LocalDate): List<ScheduledInstallment>`, que es lo que usa `Loan.schedule()`. Cuotas iguales; la última absorbe el residuo (el mismo criterio que el `installmentAmount` actual). Fecha de la cuota *n* = `frequency.advance(firstDue, interval, n-1)`. `OVERDUE` = no cubierta del todo y `due < today`.

- [ ] **Step 1: Tests que fallan** en `LoanScheduleTest`, con total 1100.00 (110000) en 10 cuotas:
  - `exact payment covers one installment`: paid 11000 → #1 PAID, #2 PENDING, `covered` de #2 = 0.
  - `larger payment spills into the next`: paid 15000 → #1 PAID, #2 PARTIAL con covered 4000 y uncovered 7000.
  - `last installment absorbs the remainder`: total 100000 en 3 → montos 33333, 33333, 33334.
  - `overdue only when due before today and not fully covered`.
  - `fully paid plan is all PAID`.
- [ ] **Step 2:** Tests que fallan en `LoanTest` (borrar los tests viejos de `paidInstallments` / `progressFraction`):
  - `remaining is total minus paid` y `settled when nothing remains`.
  - `deleting a payment reopens a settled loan`: `loan.copy(paid = total)` → PAID; `copy(paid = total - 1)` → no settled y remaining = 1 (Review Focus 5).
  - `without plan overdue only after expectedDate with something left`.
  - `disbursement and payment types follow the direction`: INCOME → LOAN_OUT / LOAN_IN; EXPENSE → LOAN_IN / LOAN_OUT.
  - `outstanding totals keep directions apart and skip settled loans`.
- [ ] **Step 3:** Correr `--tests '*LoanTest' --tests '*LoanScheduleTest'`. Esperado: FAIL.
- [ ] **Step 4:** Implementar *Produces*. `status`: `PAID` si settled; si hay plan, `OVERDUE` si alguna cuota está OVERDUE; si no hay plan, `OVERDUE` si `expectedDate < today`; si no, `ACTIVE`.
- [ ] **Step 5:** Correr los tests. Esperado: PASS.
- [ ] **Step 6:** Commit `feat: compute loan installments from free payments`.

### Task 3: Repositorio de préstamos

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/repository/LoanRepository.kt` (reescritura)
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/dao/LoanDao.kt`, `TransactionDao.kt:115-165`
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/repository/TransactionRepository.kt` (borrar `setInstallmentCleared`, `getInstallmentsBetweenDates`, `getInstallmentsByLoanId`)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/LoanRepositoryTest.kt` (reescritura; reutiliza su `setUp` y agrega una cuenta GTQ, una USD y una tarjeta)

**Interfaces:**
- Consumes: Task 1 y Task 2.
- Produces (`LoanRepository`):
  ```kotlin
  fun observeAll(): Flow<List<Loan>>
  fun observeByAccount(accountId: Long): Flow<List<Loan>>
  fun observe(id: Long): Flow<Loan?>
  fun observeMovements(loanId: Long): Flow<List<Transaction>>   // desembolso + pagos, por fecha
  suspend fun create(loan: Loan): Long                          // escribe loan + desembolso
  suspend fun update(loan: Loan)                                // reescribe desembolso; mueve todo si cambia la cuenta
  suspend fun addPayment(loanId: Long, amount: Money, date: LocalDateTime): Long
  suspend fun payInstallment(loanId: Long, number: Int, date: LocalDateTime): Long  // addPayment(uncovered)
  suspend fun updatePayment(paymentId: Long, amount: Money, date: LocalDateTime)
  suspend fun deletePayment(paymentId: Long)
  suspend fun delete(loanId: Long)                              // filas + préstamo, en una transacción
  ```
- `LoanDao`: `paidMinor` = `SUM(accountAmountMinor)` de las filas con `loanId` y `type = CASE loans.loanType WHEN 'INCOME' THEN 'LOAN_IN' ELSE 'LOAN_OUT' END`. `remainingMainMinor` = `(total − paid)` convertido con el `mainRate` de la fila de desembolso; se calcula en Kotlin a partir de `disbursementAmountMinor` (main) y `totalAmountMinor`, proporcional. Queries `getByAccount(accountId)` y `getMovements(loanId)`.
- Descripción de las filas: el nombre del préstamo (las etiquetas visibles las arma la UI, Task 5). `isCleared = true`, `categoryId = null`.

- [ ] **Step 1: Tests que fallan:**
  - `lending takes the money out and payments bring it back`: Le presté 1000.00 desde GTQ (saldo 5000) → saldo 4000; `addPayment` 250 → 4250; `loan.remaining` = 750.
  - `borrowing adds the money and payments take it out`: lo inverso.
  - `loan movements never reach monthly totals`: tras desembolso y pago, `transactionDao` income/expense del mes = 0 / null.
  - `payment over the remaining is refused`: `assertThrows<IllegalArgumentException>`; nada escrito.
  - `payment before the disbursement is refused` (Review Focus 1).
  - `paying an installment pays what it lacks`: plan 10×1100, pago libre 1500, `payInstallment(2)` escribe 700.
  - `editing the total below what was paid is refused` (Review Focus 2).
  - `changing the account moves every movement`: GTQ → otra cuenta GTQ; el desembolso y los pagos quedan con el nuevo `accountId` y los saldos se ajustan.
  - `changing the account to another currency is refused` (Review Focus 3).
  - `cards and archived accounts are refused on create`.
  - `deleting the loan deletes its movements`.
  - `missing rate saves nothing`: cuenta EUR sin tasa a la moneda principal → `MissingRateException` y ninguna fila (ya existe uno parecido; adaptarlo).
- [ ] **Step 2:** Correr `--tests '*LoanRepositoryTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar. Construir las filas con `Transaction.singleCurrency`-style + `amounts.forSave(base, previous, accountCurrency, main, manualAccountRate = null)`, como en `TransferRepository.legs`. Validar dentro de `guard.write { database.withTransaction { … } }`.
- [ ] **Step 4:** Borrar de `TransactionDao` las queries de cuotas (`getInstallmentsByLoanId*`, `getLoanInstallmentsBetween…`, el conteo de pagadas) y sus usos en `TransactionRepository`.
- [ ] **Step 5:** Correr `--tests '*LoanRepositoryTest' --tests '*TransactionRepositoryTest'`. Esperado: PASS.
- [ ] **Step 6:** Commit `feat: record loan disbursements and payments in their account`.

### Task 4: Respaldo v6, cambio de moneda y borrado de datos

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/domain/model/BackupData.kt:27` (`CURRENT_VERSION = 6`)
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/repository/BackupRepository.kt:220-260` (sanitizado de préstamos)
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/rates/MainCurrencyChanger.kt` (solo lo que no compile)
- Test: `app/src/test/java/com/boxdev/boxwallet/data/repository/BackupRepositoryTest.kt`, `app/src/test/java/com/boxdev/boxwallet/data/rates/MainCurrencyChangerTest.kt`

- [ ] **Step 1: Tests que fallan:**
  - `backup round-trips a loan with its payments`: crea un préstamo con plan, expectedDate y 2 pagos; exporta y restaura; mismo `Loan` y mismos movimientos.
  - `version 5 backups are refused`.
  - `a loan whose account is missing is dropped with its movements` (sanitizado: ya no hay "cuenta por defecto" para préstamos).
  - En `MainCurrencyChangerTest`: `loan movements are re-converted like any other`.
- [ ] **Step 2:** Correr esos tests. Esperado: FAIL.
- [ ] **Step 3:** Implementar. Quitar el `categoryId` del sanitizado de préstamos.
- [ ] **Step 4:** Correr. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: back up loans with their payments (format 6)`.

### Task 5: Los movimientos de préstamo en las listas

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/dao/TransactionDao.kt:121-135` (quitar `loanId IS NULL` de las listas global, por cuenta, últimas y por fechas)
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/components/LoanLabels.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/components/TransactionItem.kt`, `ui/screens/transactions/TransactionsListScreen.kt`, `ui/screens/accounts/AccountDetailScreen.kt:190-200`, `ui/screens/search/SearchViewModel.kt`, `ui/screens/search/SearchScreen.kt`, `ui/screens/home/HomeScreen.kt` (tap de "últimos")
- Modify: `app/src/main/java/com/boxdev/boxwallet/util/ExcelReportGenerator.kt` (borrar las hojas de préstamos; la hoja de movimientos solo lleva `countsInReports`)
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/components/LoanLabelsTest.kt`, `app/src/test/java/com/boxdev/boxwallet/data/local/dao/TransactionDaoTest.kt`

**Interfaces:**
- Produces: `enum class LoanMovementLabel { LENT_TO, BORROWED_FROM, PAYMENT_FROM, PAYMENT_TO }` y `fun loanMovementLabel(type: TransactionType, loanType: LoanType): LoanMovementLabel`, con strings `"Préstamo a %s"`, `"Préstamo de %s"`, `"Pago de %s"`, `"Pago a %s"` (y en inglés `"Loan to %s"`, `"Loan from %s"`, `"Payment from %s"`, `"Payment to %s"`). Mismo patrón que `TransferLabels`.
- Tocar un movimiento con `loanId` abre `NavRoutes.LoanDetail` (igual que `installmentPlanId` abre su plan).

- [ ] **Step 1: Tests que fallan:**
  - `LoanLabelsTest`: las 4 combinaciones de tipo y dirección.
  - `TransactionDaoTest`: `loan movements are listed by account and globally` y `the type filter Income leaves loan movements out`.
- [ ] **Step 2:** Correr. Esperado: FAIL.
- [ ] **Step 3:** Implementar. El ícono de la fila es `Icons.Outlined.Handshake`, sin categoría. La búsqueda encuentra préstamos por `name`/`description` (ya sin `categoryId`) y sus movimientos por la descripción.
- [ ] **Step 4:** Correr esos tests y `--tests '*ExcelReport*'` si existe. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: list loan movements with their own labels`.

### Task 6: Navegación sin la pestaña Préstamos

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavGraph.kt:75-100,160-330` (quitar `Destination.LOANS`; `NavRoutes.Loans` pasa a ser ruta secundaria normal; `LoanDetail` ya no hace de pestaña)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavigationTab.kt` (quitar `loanDetailInSinglePane`, el caso `"loan_detail" -> LOANS` y `NavRoutes.Loans` de `showFab`/`primaryAction`)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/navigation/NavRoutes.kt` (`LoanForm`: `loan_form?id={id}&accountId={accountId}`)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/loans/LoansScreen.kt` (pantalla secundaria con atrás, agrupada en **Me deben** / **Debo**, liquidados al final; quitar el panel doble propio y los filtros de categoría)
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/navigation/TabNavigationTest.kt`, `NavRoutesTest.kt`

- [ ] **Step 1: Tests que fallan:**
  - `NavRoutesTest`: `loan form route carries the account` → `LoanForm.createRoute(accountId = 3)` = `"loan_form?accountId=3"`; con id y cuenta, ambos.
  - `TabNavigationTest`: `there are three destinations` (`Destination.entries.map { it.route } == [home, transactions_list, analytics]`), y adaptar los casos que usaban Loans.
- [ ] **Step 2:** Correr `--tests '*NavRoutesTest' --tests '*TabNavigationTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar. `isSecondary(route)` debe cubrir `loans` y `loan_detail`. "Ver todos" de Inicio y "Ver préstamos" de Reportes navegan a `NavRoutes.Loans`.
- [ ] **Step 4:** Correr. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: drop the Loans tab and open loans as their own screens`.

### Task 7: Formulario del préstamo

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/loans/LoanFormViewModel.kt`, `LoanFormScreen.kt`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/transaction/FormChanges.kt:26-40`
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/loans/LoanFormStateTest.kt`, `LoanFormViewModelTest.kt` (reescrituras)

**Interfaces:**
- Consumes: `LoanRepository.create/update/delete` (Task 3); ruta con `accountId` (Task 6).
- Produces: `LoanFormState(name, description, totalAmount: String, loanType, accountId: Long?, accounts: List<Account>, date, hasPlan: Boolean, installments: String, frequency, frequencyInterval: String, firstDueDate: LocalDate, expectedDate: LocalDate?, paid: Money?, …)` con `currency` derivada de la cuenta elegida y `isValid`.

- [ ] **Step 1: Tests que fallan:**
  - `LoanFormStateTest`: `valid without plan needs name, amount and account`; `with plan needs installments > 0`; `total below what was paid is invalid` (al editar, con `paid`).
  - `LoanFormViewModelTest`: `account from the route is preselected`; `cards and archived accounts are not offered`; `when editing, only accounts in the loan currency are offered`; `new loan defaults to the default account unless it is a card, then the first usable one`.
- [ ] **Step 2:** Correr. Esperado: FAIL.
- [ ] **Step 3:** Implementar. Selector de cuenta con el chip/hoja que ya usa el formulario de movimientos. Interruptor "Pagos en cuotas" (cuotas, frecuencia, fecha de la primera cuota, por defecto un período después del desembolso); apagado, "Fecha esperada" opcional. Se quitan la categoría y el selector de moneda.
- [ ] **Step 4:** Correr. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: loan form with account, optional installments and expected date`.

### Task 8: Detalle del préstamo y registro de abonos

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/loans/LoanDetailViewModel.kt`, `LoanDetailScreen.kt`
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/screens/loans/LoanPaymentSheet.kt` (hoja: monto + fecha; sirve para crear y editar)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/components/LoanCard.kt`, `LoanChips.kt` (quitar categoría y conteo de cuotas pagadas; mostrar "Faltan X de Y" y la próxima fecha)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/accounts/AccountsViewModel.kt` o donde se maneje el error de borrar cuenta: el mensaje menciona préstamos (Review Focus 4)
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/loans/LoanDetailViewModelTest.kt`, `LoanPaymentStateTest.kt`

**Interfaces:**
- Consumes: `LoanRepository.observe/observeMovements/addPayment/payInstallment/updatePayment/deletePayment` (Task 3); `Loan.schedule()` (Task 2).
- Produces: `LoanPaymentState(amount: String, date: LocalDateTime, max: Money, editingId: Long?)` con `isValid` (`0 < amount ≤ max`); `LoanDetailViewModel.paymentPrefill(): Money` (lo que le falta a la próxima cuota con plan; todo lo que falta sin plan).

- [ ] **Step 1: Tests que fallan:**
  - `LoanPaymentStateTest`: `amount above max is invalid`; `zero is invalid`; `editing allows up to remaining plus the payment's own amount`.
  - `LoanDetailViewModelTest`: `prefill is the next installment's uncovered part`; `prefill without plan is everything left`; `mark paid pays the installment`.
- [ ] **Step 2:** Correr. Esperado: FAIL.
- [ ] **Step 3:** Implementar la pantalla según §5.5 del spec: hero con lo que falta, "de Y", cuenta y estado; lista de cuotas con estado y "Marcar pagada" en la próxima; lista de pagos (tocar = editar en la hoja, con eliminar); botón "Registrar abono"; menú con editar y eliminar. Seguir `DESIGN.md` / `Expressive.kt`.
- [ ] **Step 4:** Correr. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: loan detail with installments, payments and free top-ups`.

### Task 9: Préstamos en el detalle de cuenta

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/accounts/AccountDetailViewModel.kt`, `AccountDetailScreen.kt:233-300`
- Create: `app/src/main/java/com/boxdev/boxwallet/ui/components/AccountLoansSection.kt` (siguiendo `CardInstallmentsSection`)
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/accounts/AccountDetailViewModelTest.kt` (crear si no existe)

**Interfaces:**
- Consumes: `LoanRepository.observeByAccount` (Task 3); `NavRoutes.LoanForm.createRoute(accountId = …)` (Task 6).
- Produces: `AccountDetailUiState.activeLoans: List<Loan>`, `settledLoans: List<Loan>`, `canHaveLoans: Boolean` (`false` para tarjetas).

- [ ] **Step 1: Tests que fallan:** `active loans come first and settled apart`; `cards have no loans section`.
- [ ] **Step 2:** Correr. Esperado: FAIL.
- [ ] **Step 3:** Implementar: sección "Préstamos" con una fila por préstamo activo (dirección, persona, "Faltan X", próxima fecha, marca si está atrasado), "Ver liquidados (n)" desplegable y la acción "Nuevo préstamo". Sin préstamos, solo la acción.
- [ ] **Step 4:** Correr. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: show an account's loans in its detail`.

### Task 10: Inicio y Reportes

**Files:**
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/ReportMath.kt:43-80` (reemplazar `summarizeInstallments`)
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/analytics/AnalyticsViewModel.kt:100-225`, `AnalyticsScreen.kt:300-320,600-790`
- Modify: `app/src/main/java/com/boxdev/boxwallet/ui/screens/home/HomeViewModel.kt:85-100`, `HomeScreen.kt:165-200`
- Modify: `app/src/main/java/com/boxdev/boxwallet/data/local/dao/TransactionDao.kt` (query de movimientos de préstamo entre fechas)
- Test: `app/src/test/java/com/boxdev/boxwallet/ui/screens/analytics/ReportMathTest.kt`

**Interfaces:**
- Produces: `data class LoanFlows(val received: Long, val paid: Long, val lent: Long, val collected: Long, val owe: Long, val owed: Long)` en minor de la moneda principal y `fun loanFlows(movements: List<Transaction>, loans: Map<Long, Loan>): LoanFlows`. `received`/`paid` son de préstamos EXPENSE (desembolso / pagos) y `lent`/`collected` de INCOME. `owe`/`owed` vienen de `outstandingTotals()` y no dependen del período.

- [ ] **Step 1: Tests que fallan:**
  - `loan flows split by direction and role`: un préstamo recibido de 10000 con un pago de 1100, y uno prestado de 1000 con un cobro de 250 → `received = 10000`, `paid = 1100`, `lent = 1000`, `collected = 250`.
  - `loan movements stay out of income and expense totals`: los totales del período que ya calcula `ReportMath` no cambian al agregar esos movimientos.
- [ ] **Step 2:** Correr `--tests '*ReportMathTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar el bloque **Préstamos** en Reportes (cuatro cifras del período, más Debo / Me deben y "Ver préstamos"). Se elimina `LoanInstallmentsDue` / `InstallmentsSummary`. En Inicio, la tarjeta "Préstamos activos" usa los nuevos campos, y "Ver todos" lleva a `NavRoutes.Loans`.
- [ ] **Step 4:** Correr. Esperado: PASS.
- [ ] **Step 5:** Commit `feat: loans block in reports and active loans on home`.

### Task 11: Textos, limpieza y verificación final

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml` (quitar los strings de cuotas y categorías de préstamos que ya no se usan; `nav_loans` se queda solo si la lista lo usa como título)
- Modify: `README.md` / `PRODUCT.md` si describen la pestaña Préstamos

- [ ] **Step 1:** Correr `./gradlew lintDebug`. Esperado: sin `UnusedResources` nuevos ni strings sin traducir.
- [ ] **Step 2:** Correr `./gradlew testDebugUnitTest`. Esperado: BUILD SUCCESSFUL.
- [ ] **Step 3:** Correr `./gradlew assembleDebug`. Esperado: BUILD SUCCESSFUL.
- [ ] **Step 4:** Probar en el emulador, en teléfono y en plegable abierto: crear *Me prestaron* 10,000 con 10 cuotas → el saldo sube; abonar 1,500 → la cuota 1 queda pagada y la 2 parcial, y el saldo baja; Reportes no cambia ingresos ni gastos y el bloque Préstamos lo muestra; la barra tiene 3 destinos.
- [ ] **Step 5:** Commit `chore: tidy loan strings and docs`. Abrir el PR con `gh pr create --base main` y el título de Global Constraints.
