# Multimoneda — diseño

- **Fecha:** 2026-11-07
- **Estado:** aprobado en conversación; decisión de §4 (opción B) incorporada
- **Rama:** `feat/multi-currency`
- **Sub-proyecto 1 de 2.** El sub-proyecto 2 (captura automática de compras con Google Pay vía
  `NotificationListenerService`) se diseña después y se apoya en este.

## 1. Objetivo

Que cada movimiento pueda ocurrir en cualquier moneda, guardando su monto original y una conversión
estimada a la moneda principal con la tasa **de su propia fecha**, con tasas que se actualizan solas
desde una fuente libre. Aprovechar el cambio para reconstruir la base de datos sobre una estructura
sólida: dinero en enteros (`Long`), esquema versionado con migraciones reales y preparación para
multicuenta.

**Éxito:**
- Registrar un gasto en otra moneda toma lo mismo que uno normal (la tasa se llena sola).
- Los totales del mes nunca cambian solos: cada movimiento congela su tasa.
- Cambiar la moneda principal convierte todo el historial de forma atómica, sin estados a medias.
- Activar multicuenta en el futuro no requiere reescribir datos.

### Decisiones tomadas

| Tema | Decisión |
|---|---|
| Tasa de un movimiento | Congelada a la fecha del movimiento; editable por el usuario |
| Histórico | Tabla local de tasas diarias; fechas pasadas usan la tasa de ese día; gráfico de variación |
| Fuente de tasas | fawazahmed0/exchange-api (libre, sin clave), base USD |
| Moneda principal | Se elige en Onboarding por país (sugerida, editable); cambiarla **siempre** recalcula todo, con advertencia previa |
| Catálogo | Todas las monedas ISO, orden alfabético, buscador, "Usadas recientemente" arriba (derivado del historial) |
| Préstamos | Cada préstamo tiene su propia moneda |
| Recurrentes | La plantilla guarda moneda; cada ocurrencia usa la tasa de su fecha |
| Dinero | `Long` en unidades menores de su moneda; cálculos en `BigDecimal` `HALF_EVEN` |
| Base de datos | Archivo nuevo, esquema v1 limpio, `exportSchema = true`, sin fallback destructivo |
| Multicuenta | Tabla `accounts` y `accountId` obligatorio desde ya; una cuenta por defecto oculta en la UI |
| Moneda de cuenta | Cada cuenta tiene moneda; saldo de cuenta en su moneda; monto del movimiento independiente |

### Fuera de alcance

- UI de multicuenta, transferencias entre cuentas, límites y cortes de tarjetas de crédito.
- Evolución funcional de préstamos (se iterará aparte).
- Captura de Google Pay (sub-proyecto 2).
- Pruebas instrumentadas de UI.

## 2. Modelo de datos

### 2.1 Base de datos nueva

- Archivo nuevo `boxwallet.db`, `@Database(version = 1, exportSchema = true)`, esquemas en
  `app/schemas/` (configurado con `room { schemaDirectory(...) }` del plugin de Room).
- Se elimina `fallbackToDestructiveMigration`. Toda versión futura lleva `Migration` + prueba.
- Al arrancar, si existe el archivo viejo `boxwallet_database`, se borra
  (`context.deleteDatabase("boxwallet_database")`). Es seguro: la app solo está en internal testing.
  Los testers recuperan datos vía restore de un backup de Drive (§6.3). **Avisar a los testers
  que hagan backup antes de instalar esta versión.**

### 2.2 Dinero

```kotlin
data class Money(val minor: Long, val currency: Currency)
```

- `minor` está en unidades menores según `Currency.defaultFractionDigits` (USD/GTQ: 2, JPY: 0,
  BHD: 3). Monedas con `defaultFractionDigits = -1` (no aplica a ISO de curso legal) se tratan como 2.
- Conversión: `BigDecimal(minor).movePointLeft(fromDigits) × rate`, redondeado a `toDigits` con
  `RoundingMode.HALF_EVEN`, a `Long`. Nunca se usa `Double` para montos.
- Las tasas se guardan como `TEXT` (`BigDecimal.toPlainString()`) vía `TypeConverter`, sin perder
  precisión.

### 2.3 Tablas

**`accounts`**

| Columna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK | |
| `name` | TEXT | "Principal" por defecto (string localizado al crearla) |
| `type` | TEXT | `CASH`, `CHECKING`, `SAVINGS`, `CREDIT_CARD`, `OTHER` |
| `currencyCode` | TEXT | ISO 4217; inmutable si la cuenta tiene movimientos |
| `isArchived` | INTEGER | |
| `sortOrder` | INTEGER | |
| `createdAt` | INTEGER | epoch ms |

Se crea una cuenta por defecto (`CHECKING`, moneda principal) al terminar el Onboarding o al restaurar.
Su id se guarda en DataStore como `defaultAccountId`.

**`transactions`**

| Columna | Tipo | Notas |
|---|---|---|
| `id` | INTEGER PK | |
| `accountId` | INTEGER NOT NULL | FK → `accounts` (RESTRICT), índice |
| `type` | TEXT | `INCOME`, `EXPENSE` |
| `date` | INTEGER | igual que hoy; índice |
| `description` | TEXT | |
| `categoryId` | INTEGER NULL | FK → `categories` (SET NULL), índice |
| `loanId` | INTEGER NULL | FK → `loans` (CASCADE, igual que el comportamiento actual de borrar préstamo), índice |
| `installmentNumber` | INTEGER NULL | |
| `originalAmountMinor` | INTEGER | monto en la moneda del movimiento, siempre positivo |
| `currencyCode` | TEXT | moneda del movimiento; índice |
| `accountAmountMinor` | INTEGER | en la moneda de la cuenta |
| `accountRate` | TEXT | original → cuenta (`1` si son iguales) |
| `accountRateSource` | TEXT | `AUTO`, `MANUAL`, `APPROXIMATE` |
| `amountMinor` | INTEGER | en la moneda principal; alimenta todos los totales |
| `mainRate` | TEXT | cuenta → principal (`1` si son iguales) |
| `mainRateSource` | TEXT | `AUTO`, `MANUAL`, `APPROXIMATE` |
| `isCleared`, `hasReminder`, `reminderDate`, `reminderSecondsBefore` | | igual que hoy |
| `isRecurrent`, `recurrenceFrequency`, `recurrenceInterval`, `nextOccurrenceDate`, `parentRecurrenceId` | | igual que hoy; `parentRecurrenceId` FK → `transactions` (SET NULL) |

Invariantes:
- `accountAmountMinor = convert(originalAmountMinor, currencyCode → account.currencyCode, accountRate)`.
- `amountMinor = convert(accountAmountMinor, account.currencyCode → main, mainRate)`.
- Si `currencyCode == account.currencyCode`, `accountRate = 1` y la fuente es `AUTO`.
- Si `account.currencyCode == main`, `mainRate = 1` y la fuente es `AUTO`.
- Las tasas se aplican al cruzar dos tasas contra USD (`rate(A→B) = unitsPerUsd(B) / unitsPerUsd(A)`)
  salvo que el usuario la edite (`MANUAL`).

**`loans`**: campos actuales con montos en `Long` (`totalAmountMinor`, etc.), más `currencyCode` y
`accountId` (NULL, FK → `accounts` SET NULL). La moneda del préstamo es inmutable si tiene pagos.
Cada pago de cuota es un `transaction` con `currencyCode` = moneda del préstamo.

**`categories`**: sin cambios de forma (se copia la definición actual a la nueva base).

**`exchange_rates`**

| Columna | Tipo | Notas |
|---|---|---|
| `date` | INTEGER | epoch day (UTC) de la publicación |
| `currencyCode` | TEXT | ISO 4217 en mayúsculas |
| `unitsPerUsd` | TEXT | BigDecimal |
| PK | `(date, currencyCode)` | |

Se guardan solo códigos ISO 4217 válidos (`Currency.getAvailableCurrencies()`); se descartan cripto
y metales. ~160 filas/día ≈ 58k/año.

### 2.4 Moneda principal

- DataStore guarda `mainCurrencyCode` (reemplaza `currency`) y `defaultAccountId`.
- `CentralAmericanCurrency` desaparece. Nombre y símbolo salen de `java.util.Currency`
  (`getDisplayName(locale)`, `getSymbol(locale)`), con una tabla pequeña de símbolos preferidos
  para la región cuando el `Locale` devuelve el código (p. ej. `Q`, `L`, `C$`, `₡`, `B/.`, `BZ$`).
- País → moneda: `Currency.getInstance(Locale("", countryCode))` sobre `Locale.getISOCountries()`.

## 3. Tasas de cambio

### 3.1 Fuente (verificado 2026-11-07)

- Principal: `https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@{latest|YYYY-MM-DD}/v1/currencies/usd.json`
- Respaldo: `https://{latest|YYYY-MM-DD}.currency-api.pages.dev/v1/currencies/usd.json`
- Respuesta: `{"date":"YYYY-MM-DD","usd":{"gtq":7.64,...}}` con claves en minúscula, ~340 entradas.
- Histórico disponible desde **2024-03-02** (jsDelivr) / ~2024-03-06 (Pages). Antes: 404.

### 3.2 Componentes

- **`ExchangeRateApi`** (`data/remote`): interfaz `suspend fun fetch(date: LocalDate?): RateSnapshot`.
  Implementación con `HttpURLConnection` + Gson (ya en dependencias; no se agrega cliente HTTP).
  Intenta la URL principal y, ante error o 404, la de respaldo. Timeouts de 10 s.
  Valida: JSON bien formado, tasas > 0, código ISO válido; descarta entradas inválidas.
- **`ExchangeRateRepository`**: guarda snapshots en `exchange_rates`, expone
  `rateOn(date, code)`, `nearestBefore(date, code)`, `nearestAfter(date, code)`, series para el
  gráfico y `lastSyncAt`.
- **`ExchangeRateResolver`** (`domain`, puro): dada fecha, moneda origen y destino, devuelve
  `ResolvedRate(rate, source)` según §3.3. Sin dependencias Android.
- **`ExchangeRateSyncWorker`**: periódico cada 24 h con `NetworkType.CONNECTED` y backoff exponencial.
  Descarga `latest`, y re-resuelve los movimientos con alguna tasa `APPROXIMATE` cuyas fechas ya
  tengan dato (nunca toca `MANUAL`). También se encola al abrir la app si `lastSyncAt` > 24 h.

### 3.3 Resolución de la tasa de un movimiento

La fecha que manda es siempre la **fecha del movimiento**, no la del día en que se registra o
edita: un gasto del martes anotado el viernes usa la tasa del martes.


1. Hay tasa para esa fecha en `exchange_rates` → `AUTO`.
2. No hay, y hay red → descargar esa fecha (fechas ≥ 2024-03-02), guardar → `AUTO`.
3. No hay red o no hay dato → la más cercana anterior (o posterior si no hay anterior) → `APPROXIMATE`.
   El Worker la corrige después si aparece el dato exacto. Fechas < 2024-03-02 quedan `APPROXIMATE`
   con la tasa más antigua disponible.
4. No hay ninguna tasa local (instalación nueva sin red) → el formulario exige tasa manual → `MANUAL`.

`MANUAL` nunca se sobrescribe automáticamente.

### 3.4 Privacidad

Las peticiones son siempre el mismo archivo público con base USD: no llevan moneda del usuario,
montos ni identificadores. Se explica en Settings > Monedas y se declara en la ficha de datos de Play.

## 4. Cambio de moneda principal

Siempre recalcula todo; no hay estado intermedio.

1. **Advertencia:** "Se convertirán N movimientos y M préstamos de USD a GTQ usando la tasa de cada
   fecha. Se creará un respaldo local antes de empezar." Confirmar / Cancelar.
2. **Fase de red** (`MainCurrencyChangeWorker`, expedited, con progreso): calcula las fechas distintas
   de los movimientos cuya cuenta no esté en la nueva moneda y descarga las que falten (una petición
   por fecha, todas las monedas). Si falla la red: reintentar o cancelar; ningún dato cambió.
3. **Respaldo local** con `BackupRepository` (archivo en almacenamiento interno, se conserva el último).
4. **Fase de datos:** una sola `@Transaction` de Room. La regla depende de cuántas cuentas haya:
   - **Una sola cuenta (caso actual, sin UI de multicuenta):** la cuenta por defecto cambia de moneda
     junto con la principal, para que ambas sigan coincidiendo. Por cada movimiento:
     `accountRate` se re-resuelve (moneda original → nueva moneda; `1` si coinciden),
     `accountAmountMinor` se recalcula desde `originalAmountMinor`, y `mainRate = 1` / `AUTO`,
     `amountMinor = accountAmountMinor`. Las tasas `MANUAL` estaban definidas contra la moneda
     anterior y ya no aplican: se re-resuelven como `AUTO`/`APPROXIMATE` y el resumen informa
     cuántas se reemplazaron. `originalAmountMinor` y `currencyCode` nunca se tocan.
   - **Varias cuentas (futuro):** las cuentas conservan su moneda y sus saldos. Solo se recalculan
     `mainRate`, `mainRateSource` y `amountMinor` (cuenta → nueva principal); las `mainRate` `MANUAL`
     se re-resuelven como arriba y las `accountRate` no se tocan.
   - Los préstamos conservan su moneda: sus pagos son movimientos y siguen la misma regla.
5. **Commit**, y solo después se escribe `mainCurrencyCode` en DataStore. Si el proceso muere entre
   el commit y la escritura, al arrancar se detecta la marca `pendingMainCurrency` (escrita en el
   paso 1) y se completa la escritura.
6. **Resumen:** movimientos convertidos, tasas aproximadas, tasas manuales reemplazadas.

La fase de red (paso 2) descarga las fechas de todos los movimientos cuya moneda original difiera de
la nueva moneda.

## 5. UI

### 5.1 Formulario de movimiento (`TransactionSheet`)
- Chip de moneda junto al monto (por defecto, moneda de la cuenta).
- Selector (bottom sheet): buscador, "Usadas recientemente" (por frecuencia y recencia, de
  `transactions.currencyCode`), luego todas en orden alfabético por nombre localizado
  (código, nombre, símbolo).
- Si la moneda difiere de la de la cuenta: fila `1 EUR = 1.0852 USD · 7 nov ✏️ → ≈ $21.70`.
  Editar la tasa o el equivalente recalcula el otro y marca `MANUAL`; "Usar tasa automática" la
  restaura. Etiqueta "Tasa aproximada" para `APPROXIMATE`.
- Cambiar la fecha re-resuelve la tasa automática.

### 5.2 Listas (`TransactionItem` en Home, Movements, Search)
Monto en moneda original; "≈ Q168.20" secundario solo si difiere de la principal.

### 5.3 Totales (`BalancePanel`, Reports, charts)
Siempre en moneda principal, sin cambios visuales. `CurrencyFormatter` pasa a formatear `Money`
(símbolo y decimales por moneda); `LocalCurrencyFormatter` provee la moneda principal.

### 5.4 Onboarding
Paso "¿Dónde vives?": países con buscador, preselecciona la región del teléfono; debajo
"Tu moneda: Quetzal (GTQ) · Cambiar" (mismo selector). Define moneda principal y crea la cuenta
por defecto.

### 5.5 Settings > Monedas (reemplaza el selector de General)
- Moneda principal → flujo de §4 con pantalla de progreso de dos fases (no cancelable durante la
  transacción) y resumen.
- "Actualizadas hoy 6:00" + "Actualizar ahora".
- Lista de monedas usadas recientemente con tasa actual vs. principal y variación diaria.
- Detalle de moneda: gráfico de línea (1M/3M/1A/Todo) con `ChartKit`, mínimo/máximo/actual;
  buscador para cualquier moneda.
- Nota de privacidad.

### 5.6 Préstamos
Selector de moneda en `LoanSheet` (bloqueado si hay pagos); detalle con saldo en moneda del
préstamo y "≈" en principal.

### 5.7 Excel
Columnas nuevas: Monto original, Moneda, Tasa, Monto en moneda principal.

### 5.8 Textos
Todo en `values` y `values-es`.

## 6. Errores y casos límite

| Situación | Comportamiento |
|---|---|
| Sin red al registrar | Tasa más cercana, `APPROXIMATE`; el Worker corrige |
| Ambas URLs fallan | Backoff; Settings muestra antigüedad de la última actualización |
| Datos inválidos de la API | Se descarta la entrada; evento de analytics sin datos del usuario |
| Fecha anterior al histórico | Tasa más antigua, `APPROXIMATE` permanente |
| Recálculo: falla red (fase 2) | Nada cambió; reintentar o cancelar |
| Recálculo: falla/muere en fase 4 | Rollback completo; moneda principal intacta |
| Moneda que la API deja de publicar (p. ej. SVC) | Se conserva el código; última tasa conocida como `APPROXIMATE` |

### 6.3 Backups

- `BackupData.version` pasa de `2` a `3` con la forma nueva (cuentas, montos en `Long`, monedas,
  tasas). `exchange_rates` no se respalda (se re-descarga).
- Restore de **v2** (`Double`, sin moneda): moneda = principal del dispositivo, montos
  `BigDecimal(amount).setScale(digits, HALF_EVEN)` a unidades menores, tasas `1`/`AUTO`,
  todo a la cuenta por defecto.
- Restore de v3 con otra moneda principal: se pregunta cuál conservar; si se conserva la del
  dispositivo, se corre §4 tras restaurar.

## 7. Pruebas

Junto al código (`app/src/test/java/<paquete>/<Clase>Test.kt`). Dependencias de prueba nuevas:
Robolectric y `androidx.room:room-testing`.

- `MoneyTest`: unidades menores (0/2/3 decimales), conversión, `HALF_EVEN`, formato.
- `ExchangeRateResolverTest`: reglas §3.3, tasa cruzada vía USD, `MANUAL` intocable.
- `ExchangeRateApiTest`: respaldo a segunda URL, parseo válido/inválido, filtro ISO (con un
  transporte HTTP falso inyectado).
- `MainCurrencyChangeTest` (Room en memoria + Robolectric): recálculo completo, rollback ante fallo,
  con una cuenta (la cuenta cambia de moneda y coincide con la principal) y con varias
  (saldos de cuenta intactos, `accountRate` `MANUAL` intactas).
- `BackupMigrationTest`: fixture JSON v2 real → esquema nuevo.
- `RecentCurrenciesTest`: orden por frecuencia y recencia.
- Esquema exportado `app/schemas/.../1.json` como base de `MigrationTestHelper` futuro.

Verificación manual antes del PR: onboarding, gasto en EUR desde cuenta GTQ, cambio de moneda
principal con ~1,000 movimientos sembrados, modo avión, restore de backup v2 de Drive.
