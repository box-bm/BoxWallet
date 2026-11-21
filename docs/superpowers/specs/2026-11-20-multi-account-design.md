# Multicuenta — diseño

- **Fecha:** 2026-11-20
- **Estado:** borrador para aprobación
- **Rama:** `feat/multi-account` (un PR por fase, ver §11)
- Se apoya en el diseño de multimoneda (`2026-11-07-multi-currency-design.md`), que dejó la tabla
  `accounts` y el `accountId` obligatorio listos para esto.

## 1. Objetivo y por qué

El usuario usa la app para **controlar sus gastos**, tarjetas de crédito incluidas. Lo que le molesta
de otras apps, y lo que esta debe resolver:

1. **No son gratuitas.** Todo lo de este diseño es gratis; nada queda detrás de un pago.
2. **Son confusas.** Crear una cuenta, transferir o pagar la tarjeta no debe sentirse como contabilidad.
3. **Nunca resuelven bien la moneda.** Cada cuenta tiene su moneda; las transferencias entre monedas
   muestran ambos montos y la tasa; las tarjetas bimoneda (GTQ + USD con un solo límite, comunes en
   Centroamérica) funcionan como en el estado de cuenta del banco.
4. **Las transferencias son complejas.** Origen, destino, monto, listo.

**Éxito:**
- Crear una cuenta toma un formulario corto: nombre, moneda, saldo actual; lo demás es opcional.
- Una transferencia o un pago de tarjeta nunca cambia los gastos ni los ingresos del mes.
- Al abrir una tarjeta se ve de un vistazo: usado, disponible, **cuánto pagar este corte y antes de
  qué fecha**.
- Una tarjeta bimoneda muestra los dos saldos y un solo disponible, y se paga por moneda.
- Quien nunca crea una segunda cuenta ve la app casi igual que hoy.

### Decisiones tomadas (en conversación)

| Tema | Decisión |
|---|---|
| Datos de una cuenta | Nombre, descripción, moneda, ícono, color |
| Tarjeta de crédito | Interruptor "Tarjeta de crédito" en el formulario. Límite; fecha de corte y de pago **opcionales**; sin fechas se comporta como "solo usado/disponible" |
| Cuándo cuenta un gasto con tarjeta | El día de la compra. Pagar la tarjeta es una transferencia y **no** es gasto |
| Bimoneda | Soporte real: una tarjeta, dos saldos (uno por moneda), un solo límite |
| Saldo inicial y ajuste | Incluidos: se registran como ajustes que no cuentan como ingreso/gasto |
| Liquidado en tarjetas | Se ignora: la deuda cuenta todo movimiento con fecha ≤ hoy (§6) |
| Excluir del total | Incluido: interruptor "Sumar al saldo total" por cuenta |
| Enfoque técnico | **A**: todo vive en `accounts` y `transactions` (§4) |
| Orden de entrega | 3 PRs: cuentas → transferencias → tarjeta de crédito (§11) |

### Anti-objetivos (sería un fracaso aunque funcione)

- Que una transferencia, un pago de tarjeta o un ajuste aparezca como gasto o ingreso en Inicio,
  Análisis o el Excel.
- Que el usuario de una sola cuenta tenga que entender "cuentas" para registrar un gasto.
- Que la tarjeta bimoneda se vea como dos tarjetas o calcule mal el disponible.

### Fuera de alcance

- Intereses, pago mínimo, cuotas sin intereses ("visacuotas") y estados de cuenta históricos.
- Transferencias recurrentes o programadas (el pago de tarjeta se hace con un botón; ver §8.5).
- Conciliación movimiento por movimiento (solo ajuste de saldo).
- Cambiar préstamos (el usuario los iterará después); siguen usando la cuenta por defecto.
- Captura de Google Pay (sub-proyecto aparte). Este modelo la permite: una notificación se podrá
  asociar a una cuenta/tarjeta.

## 2. Estado actual (lo que ya existe)

- `accounts`: `id`, `name`, `type` (`CASH|CHECKING|SAVINGS|CREDIT_CARD|OTHER`), `currencyCode`
  (inmutable si hay movimientos), `isArchived`, `sortOrder`, `createdAt`. Una cuenta por defecto
  creada por `AccountRepository.ensureDefaultAccount()`, id en DataStore (`defaultAccountId`), oculta
  en la UI.
- `transactions.accountId` NOT NULL, FK RESTRICT. Cada movimiento guarda tres montos:
  `originalAmountMinor` (moneda del movimiento) → `accountAmountMinor` (`accountRate`) →
  `amountMinor` (`mainRate`, moneda principal). Montos siempre positivos; el signo lo da `type`
  (`INCOME|EXPENSE`).
- Totales: `TransactionDao` suma `amountMinor` filtrando `type` e `isCleared = 1`. Análisis, Inicio
  y Excel filtran en Kotlin con `type == INCOME/EXPENSE`, y varios sitios asumen "si no es INCOME,
  es EXPENSE" (`TransactionItem.isIncome`, `ExcelReportGenerator` col. "Tipo").
- `MainCurrencyChanger` ya tiene la rama "varias cuentas" (las cuentas conservan moneda; solo cambia
  la pierna `mainRate`/`amountMinor`).
- La app ya está publicada (v1.0.0) con esquema Room **v1**: este trabajo trae la **primera
  migración real** (v1 → v2) y su test.
- Pendientes conocidos que se rompen con varias cuentas (se corrigen en el PR 1):
  - El marcador de recuperación del cambio de moneda principal usa DataStore + una heurística que
    asume una sola cuenta; la restauración usa `app_meta`. Unificar en `app_meta`.
  - Al restaurar, un `accountId` huérfano se adivina por la moneda original; inferirlo desde las
    fuentes de tasa `IDENTITY`.

## 3. Comportamiento

### 3.1 Cuentas
- Lista de cuentas con ícono, color, nombre y saldo en su moneda; arriba el **saldo total** en moneda
  principal (solo cuentas con "Sumar al saldo total").
- Formulario: nombre*, moneda* (selector existente; bloqueado si la cuenta tiene movimientos),
  saldo actual (para tarjetas: "Deuda actual"), descripción, ícono, color, interruptor
  "Sumar al saldo total", interruptor "Tarjeta de crédito" (despliega §3.3).
- Cuenta por defecto: la que se preselecciona al registrar un movimiento. Se elige en el detalle de
  la cuenta ("Usar por defecto"). No se puede archivar ni borrar la cuenta por defecto.
- Archivar: la cuenta deja de aparecer en selectores y en la lista principal (sección
  "Archivadas"); su historial queda. Borrar: solo si no tiene movimientos que no sean ajustes; al
  borrarla se borran sus ajustes. El mismo criterio decide si la moneda está bloqueada (un saldo
  inicial no la bloquea). Nunca se puede quedar en cero cuentas. *Decisión del diseñador.*
- Cambiar a/desde tarjeta: se puede en cualquier momento; quitar "Tarjeta de crédito" exige quitar
  antes la segunda moneda. Agregar fechas de corte después no cambia la fecha de ajustes ya hechos
  (para cuadrar se usa "Ajustar saldo"). *Decisión del diseñador.*
- Detalle de cuenta: saldo, (bloque de tarjeta), lista de sus movimientos, acciones "Transferir",
  "Ajustar saldo", "Editar".

### 3.2 Transferencias
- Formulario: Desde (cuenta), Hacia (cuenta), Monto (en la moneda de "Desde"), fecha, nota.
- Si las monedas difieren aparece la fila de conversión que ya existe en el formulario de
  movimientos: `1 USD = 7.71 GTQ · 20 nov ✏️ → llegan Q 771.00`. El monto que llega es editable (lo
  que el banco realmente acreditó) y la tasa resultante se guarda como `MANUAL`.
- Una transferencia nunca cuenta como ingreso ni gasto y no lleva categoría.
- Editar o borrar una transferencia afecta a sus dos lados a la vez.

### 3.3 Tarjeta de crédito
- Campos: límite (en la moneda de la tarjeta), día de corte (1–31), día de pago (1–31),
  "Avisarme N días antes del pago" (por defecto 3), y "Segunda moneda" (bimoneda, §3.4).
- Saldo de tarjeta: lo usado es deuda. **Usado** = deuda actual; **Disponible** = límite − usado
  (solo si hay límite). Si se paga de más se muestra "Saldo a favor".
- Con corte y pago configurados: **"Este corte: Q X · pagar antes del D"** y botón
  **"Pagar Q X"**. Sin fechas: solo usado/disponible y botón "Pagar tarjeta" (monto vacío).
- Una compra con tarjeta es un gasto normal cuya cuenta es la tarjeta. Una devolución es un ingreso
  en la tarjeta.

### 3.4 Bimoneda
- Una tarjeta con "Segunda moneda" (p. ej. USD) tiene dos saldos y **un solo límite** en la moneda
  principal de la tarjeta.
- Disponible = límite − usado(moneda 1) − usado(moneda 2) × tasa de hoy (moneda 2 → moneda 1).
- El corte y el pago son los mismos para las dos monedas; "Este corte" muestra dos montos y hay un
  botón de pago por moneda.
- Al registrar un gasto con la tarjeta, la moneda del gasto decide el saldo: gasto en USD → saldo en
  USD; en cualquier otra moneda → saldo de la moneda 1 (convertido como hoy). El usuario puede
  cambiar el saldo destino en el selector de cuenta ("Visa BAC · US$").
- Con segunda moneda, el formulario muestra un segundo campo "Deuda actual en US$"; su ajuste usa la
  misma fecha que el de la moneda 1 (§3.5). El detalle ofrece "Ajustar saldo" por moneda.
  *Decisión del diseñador.*
- La segunda moneda se puede agregar después; se puede quitar solo si su saldo no tiene movimientos.

### 3.5 Saldo inicial y ajuste
- El "saldo actual" del formulario crea un **ajuste** con fecha de hoy (para tarjetas con corte
  configurado: con fecha del último corte, para que el primer "Este corte" sea esa deuda —
  *decisión del diseñador*).
- "Ajustar saldo": el usuario escribe el saldo real; la app crea un ajuste por la diferencia.
- Los ajustes aparecen en la lista de la cuenta ("Ajuste de saldo"), nunca en ingresos/gastos.

## 4. Enfoques considerados

| | Descripción | A favor | En contra |
|---|---|---|---|
| **A (elegido)** | Transferencia = dos filas en `transactions` enlazadas por `transferId`; ajustes como tipo propio; bimoneda = cuenta padre + subcuenta hija (`parentAccountId`) | Lista, búsqueda, respaldo, Excel y cambio de moneda funcionan casi sin cambios; reutiliza las tres piernas de montos/tasas | Hay que auditar cada lugar que asume `INCOME`/`EXPENSE` |
| B | Tablas `transfers` y `credit_cards` aparte | Modelo "limpio" en papel | Cada saldo, lista, búsqueda, respaldo y Excel combinaría 2–3 tablas; más código y más errores |

Se eligió **A** porque la base multimoneda ya resolvió montos y tasas por fila y por cuenta.

## 5. Modelo de datos (migración Room v1 → v2)

### 5.1 `accounts` (columnas nuevas)

| Columna | Tipo | Notas |
|---|---|---|
| `description` | TEXT NOT NULL DEFAULT '' | |
| `icon` | TEXT NOT NULL DEFAULT 'AccountBalance' | Nombre de un set cerrado de íconos (como `CategoryIcons`) |
| `color` | TEXT NOT NULL DEFAULT 'DEFAULT' | Clave de una paleta cerrada con variante clara/oscura, no un ARGB libre (respeta `DESIGN.md` y el tema dinámico) |
| `includeInTotal` | INTEGER NOT NULL DEFAULT 1 | |
| `parentAccountId` | INTEGER NULL | FK → `accounts` (RESTRICT), índice. Solo la subcuenta de segunda moneda de una tarjeta |
| `creditLimitMinor` | INTEGER NULL | Solo tarjetas (padre); en la moneda del padre |
| `statementClosingDay` | INTEGER NULL | 1–31 |
| `paymentDueDay` | INTEGER NULL | 1–31 |
| `dueReminderDaysBefore` | INTEGER NULL | NULL = sin aviso |

- `type`: se conserva la columna. El formulario no muestra selector de tipo, solo el interruptor;
  cuentas nuevas son `CHECKING` o `CREDIT_CARD`. Los otros valores siguen siendo válidos.
- Subcuenta bimoneda: `type = CREDIT_CARD`, `parentAccountId = tarjeta`, moneda ≠ la del padre,
  campos de tarjeta NULL; nombre, ícono, color y `includeInTotal` se heredan del padre al mostrar.
  Máximo una hija por tarjeta; las hijas no tienen hijas. No aparece como cuenta suelta en la lista.
- `isArchived` del padre aplica a la hija.

### 5.2 `transactions`

| Cambio | Notas |
|---|---|
| `type` admite `TRANSFER_OUT`, `TRANSFER_IN`, `ADJUSTMENT_IN`, `ADJUSTMENT_OUT` | TEXT, sin cambio de columna |
| `transferId` TEXT NULL, índice | UUID compartido por las dos filas de una transferencia |

`TransactionType` gana helpers: `isInflow` (`INCOME`, `TRANSFER_IN`, `ADJUSTMENT_IN`) y
`countsInReports` (solo `INCOME`, `EXPENSE`).

**Filas de una transferencia** de A (moneda a) a B (moneda b):
- `TRANSFER_OUT` en A: `original = enviado (a)`, `accountAmount = enviado`, `accountRate = 1`,
  `amountMinor` = enviado → principal (como cualquier movimiento).
- `TRANSFER_IN` en B: `original = enviado (a)`, `accountAmount = recibido (b)`,
  `accountRate = recibido / enviado` (`AUTO` si viene de la tasa del día, `MANUAL` si el usuario
  editó el monto recibido), `amountMinor` = recibido → principal.
- Ambas: misma fecha, `transferId`, `categoryId = NULL`, `isCleared = 1`, sin recordatorio ni
  recurrencia.

**Ajuste:** una fila `ADJUSTMENT_IN/OUT` en la cuenta, moneda de la cuenta, tasas identidad hacia la
cuenta, `isCleared = 1`.

### 5.3 Migración y respaldo
- `MIGRATION_1_2`: `ALTER TABLE ... ADD COLUMN` para todo lo anterior + índices; sin tocar filas.
  Test con `MigrationTestHelper` sobre el esquema v1 exportado.
- `BackupData.version` 3 → 4 con los campos nuevos. Un respaldo v3 se importa con los valores por
  defecto. Validar al importar: las dos filas de cada `transferId`, padres existentes.

## 6. Reglas de saldo y totales

- **Saldo de una cuenta** (en su moneda, exacto) = Σ `accountAmountMinor` de entradas − Σ de
  salidas, con `isCleared = 1`.
- **Tarjetas (padre e hija): se ignora `isCleared`.** Cuenta todo movimiento con fecha ≤ hoy,
  liquidado o no (la deuda existe desde la compra). Deuda = −saldo. Lo mismo aplica a los cálculos de
  §8. *Decidido por el usuario.* Al convertir una cuenta en tarjeta (o viceversa) su saldo se
  recalcula con la regla que le toque; no hay backfill de `isCleared`.
- **Saldo total** (Inicio y lista de cuentas) = Σ saldos de cuentas con `includeInTotal`, cada uno
  convertido a la moneda principal **con la tasa de hoy**; se marca "≈" si alguna cuenta está en
  otra moneda. *Decisión del diseñador:* el saldo es una foto de hoy; los ingresos/gastos siguen
  usando `amountMinor` congelado a su fecha. Con una sola cuenta en moneda principal ambos coinciden
  con lo actual.
- **Ingresos, gastos, categorías, gráficos, Excel:** solo `countsInReports`. Revisar todas las
  consultas y filtros: DAO (`SUM` ya filtra por tipo), `AnalyticsViewModel`, `ReportMath`,
  `HomeViewModel`, `ExcelReportGenerator`, `SearchViewModel`, y cualquier `if (INCOME) … else …`
  (`TransactionItem`). Un test de "transferencias y ajustes no cambian los totales" por cada uno.
- **Listas:** sin filtro de cuenta, la lista global y la búsqueda muestran **una sola fila por
  transferencia** (la `TRANSFER_OUT`, presentada como "Banco → Visa BAC"). Con filtro de cuenta (en
  Movimientos, búsqueda o el detalle de la cuenta) se muestra la fila de esa cuenta.
  Color neutro (ni verde ni rojo), ícono de transferencia.

## 7. Transferencias — reglas

- Origen ≠ destino. Se permite entre cualquier par de cuentas no archivadas, incluida la subcuenta
  bimoneda.
- Se escriben las dos filas en una sola `@Transaction` de Room; editar/borrar reescribe o borra
  ambas por `transferId`.
- Tasa por defecto: `ExchangeRateResolver` con la fecha de la transferencia (moneda a → b); el
  monto recibido se redondea `HALF_EVEN` a las unidades menores de b.
- `MovementWriteGuard` (cambio de moneda en curso) también bloquea transferencias y ajustes.
- `MainCurrencyChanger` recalcula `mainRate`/`amountMinor` de estas filas como de cualquier otra
  (rama "varias cuentas"); `accountRate` de `TRANSFER_IN` no se toca.

## 8. Tarjeta de crédito — cálculos

Sea `C` = día de corte, `D` = día de pago; un día mayor que el largo del mes se ajusta al último día.

1. **Último corte** `Lc`: la fecha más reciente con día `C` que sea ≤ hoy (fin del día).
2. **Saldo del corte** `S` = deuda al final de `Lc` (movimientos con fecha ≤ `Lc`).
3. **Pagos y créditos desde el corte** `P` = Σ entradas (`TRANSFER_IN`, `INCOME`, `ADJUSTMENT_IN`)
   con fecha > `Lc`.
4. **Este corte (a pagar)** = máx(0, `S` − `P`). Cero → "Corte pagado ✓".
5. **Fecha de pago** = primera fecha con día `D` posterior a `Lc` (si `D ≤ C`, cae en el mes
   siguiente al del corte).
6. Si `S` = 0 y no hay movimientos antes de `Lc` → "Aún no hay corte" y solo usado/disponible.
7. Bimoneda: pasos 2–4 por cada saldo, con las mismas fechas.

### 8.5 Pagar y avisar
- "Pagar Q X" abre el formulario de transferencia con Hacia = tarjeta (o la subcuenta USD), monto =
  a pagar, Desde = la última cuenta usada para pagar esa tarjeta (si no, la cuenta por defecto).
  Desde una cuenta en GTQ hacia el saldo USD funciona como transferencia entre monedas.
- Aviso: `ReminderWorker` (cada 12 h) revisa las tarjetas con `dueReminderDaysBefore`; si faltan ≤ N
  días para la fecha de pago y "a pagar" > 0, notifica una vez por corte ("Visa BAC: paga Q 1,980
  antes del 5 ene"). La última fecha avisada por tarjeta se guarda en `app_meta`.

## 9. Interfaz

Sigue `DESIGN.md` (modelo de superficies, escala de esquinas, componentes de `Expressive.kt`).

- **Inicio:** el panel de saldo muestra el saldo total y una fila horizontal de cuentas (ícono,
  nombre, saldo; tarjetas con barra de uso). Con una sola cuenta la fila no aparece. Tocar el saldo o
  "Ver cuentas" → lista de cuentas.
- **Rutas nuevas:** `Accounts`, `AccountDetail/{id}`, `AccountForm?id=`, `TransferForm?from=&to=&amount=&id=`.
  En pantallas anchas, lista y detalle lado a lado como Movimientos (`Panes.kt`).
- **Botón "+":** agrega "Transferencia" junto a ingreso/gasto.
- **Formulario de movimiento:** chip de cuenta junto al chip de moneda; por defecto la cuenta por
  defecto; al cambiar de cuenta, la moneda se va a la de la cuenta si el usuario no la había tocado.
  Oculto si solo hay una cuenta.
- **Movimientos y búsqueda:** filtro por cuenta (vía `MovementsFilterRequest`).
- **Ajustes:** entrada "Cuentas" que abre la misma lista.
- Ícono y color: cuadrícula de íconos (set cerrado, como categorías) y ~12 colores de la paleta.

## 10. Otros módulos

- **Excel:** columna "Cuenta"; "Tipo" con Transferencia/Ajuste; hoja de resumen por cuenta (saldo
  final en su moneda). Totales solo `countsInReports`. Arreglar de paso la columna "Tasa" para que
  use `accountRate × mainRate` (pendiente conocido).
- **Recurrentes:** la plantilla ya guarda `accountId`; cada ocurrencia hereda la cuenta. Si la
  cuenta está archivada, la ocurrencia va a la cuenta por defecto.
- **Onboarding:** sin cambios (crea la cuenta por defecto como hoy).
- **Préstamos:** sin cambios: las cuotas se escriben en `loan.accountId ?: cuenta por defecto`
  vigente al escribir. Si esa cuenta está archivada, van a la cuenta por defecto. Archivar una
  cuenta con cuotas está permitido (el historial queda).

## 11. Entrega y pruebas

**PR 1 — `feat: cuentas personalizables con saldo inicial y ajuste`**
Migración v1→v2 completa (todas las columnas, también las de tarjeta, para no migrar tres veces),
respaldo v4, tipos `ADJUSTMENT_*`, `countsInReports`/auditoría de totales, lista/detalle/formulario
de cuentas, Inicio, chip de cuenta en el formulario, filtro por cuenta, los dos arreglos de §2.
Tests: migración, saldo de cuenta, saldo total con varias monedas, ajustes excluidos de totales,
respaldo v3→v4, cambio de moneda principal con varias cuentas.

**PR 2 — `feat: transferencias entre cuentas y monedas`**
Tipos `TRANSFER_*`, `transferId`, formulario, edición/borrado atómico, una fila por transferencia en
listas, Excel. Tests: montos y tasas de ambas filas, `MANUAL` al editar lo recibido, totales sin
cambios, borrado de ambas filas, cambio de moneda principal.

**PR 3 — `feat: tarjetas de crédito con corte, pago y bimoneda`**
Interruptor y campos, cálculos de §8 (lógica pura en `domain`, sin Android), subcuenta bimoneda,
"Pagar", aviso. Tests: corte con días 29–31 y febrero, `D ≤ C`, sobrepago, sin corte aún, bimoneda
(disponible con tasa, a pagar por moneda), aviso una vez por corte.

Verificación manual por PR: crear cuenta USD y tarjeta bimoneda, transferir GTQ → USD, gastar en USD
con la tarjeta, pagarla desde GTQ, revisar que Inicio y Análisis no cambian con transferencias.

## 12. Queda a criterio del implementador

- Set exacto de íconos y colores (dentro de `DESIGN.md`).
- Si el cálculo de saldos va en SQL o en Kotlin sobre los flujos existentes.
- Textos finales (escribirlos como "What's new" amigable: `feat` describe lo que gana el usuario).
