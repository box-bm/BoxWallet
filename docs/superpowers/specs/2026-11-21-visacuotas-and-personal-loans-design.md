# Visacuotas y préstamos entre personas — diseño

Continúa el diseño de multicuenta (`2026-11-20-multi-account-design.md`, §11 "PR 4"). Los PR 1–3
(cuentas, transferencias, tarjetas) ya existen; este documento parte de ellos.

## 1. Intención

El usuario usa BoxWallet para **controlar sus gastos**. Hay dos cosas que hoy no puede registrar
bien:

1. **Visacuotas (extrafinanciamiento).** Compra algo con la tarjeta en N cuotas, y en cada fecha de
   corte el banco le cobra una cuota. La compra es de la **tarjeta**. Se paga con el pago normal de
   la tarjeta, que sale de una cuenta monetaria (el "Pagar" del PR 3).
2. **Préstamos entre personas.** Presta dinero a un amigo o familiar, o se lo prestan a él, y quiere
   saber cuánto falta. Los pagos llegan cuando llegan y por el monto que sea.

Para el usuario son cosas distintas, aunque las dos se parezcan a un préstamo. Por eso van separadas
en la app: las Visacuotas viven en la tarjeta y los préstamos en la pestaña Préstamos.

## 2. Decisiones del usuario

| Tema | Decisión |
|---|---|
| Cuándo cuenta como gasto una Visacuota | **Cada cuota en su corte**. No se cuenta la compra entera el día que se compra. |
| Deuda y disponible de la tarjeta | Reflejan el **total pendiente** desde el día de la compra. |
| Préstamo a o de una persona | **Solo cambia el saldo** de la cuenta, igual que una transferencia. No es gasto ni ingreso en Inicio, Análisis ni reportes. |
| Pagos de un préstamo entre personas | **Abonos libres**: cualquier monto, cuando sea, a la cuenta que elija. |
| Comisión / interés | Fuera de alcance por ahora. Cuando se agregue, solo la comisión contará como ingreso. |
| Dónde vive cada cosa | Visacuotas: formulario de gasto, detalle de la tarjeta y Movimientos. Préstamos: pestaña Préstamos. Las pestañas no cambian. |

## 3. Objetivos, no-objetivos, anti-objetivos

**Objetivos**
- Crear una compra a cuotas desde el formulario de gasto cuando la cuenta es una tarjeta.
- Hacer que el "A pagar" de cada corte (§8 del diseño de multicuenta) incluya las cuotas de ese
  corte sin pasos extra.
- Mostrar, en el detalle de la tarjeta, las compras a cuotas y lo que falta de cada una.
- Préstamos "Le presté" / "Me prestaron" con desembolso desde o hacia una cuenta, abonos libres y lo
  que falta.
- Poder elegir la cuenta de los préstamos de cuotas fijas que ya existen.

**No-objetivos**
- Interés o comisión, tanto en Visacuotas como en préstamos.
- Cancelar anticipadamente una Visacuota (pagar todas las cuotas que faltan en un corte).
- Convertir los préstamos que ya existen al modelo de abonos.
- Préstamos entre personas pagados con tarjeta.

**Anti-objetivos.** Cualquiera de estos sería un fracaso aunque el código funcione:
- Que prestar Q1,000 aparezca como Q1,000 de gasto en Inicio o Análisis.
- Que una Visacuota de Q3,000 cargue los Q3,000 de gasto en el mes de compra.
- Que el "A pagar" de un corte incluya cuotas de cortes futuros, o que el disponible de la tarjeta
  ignore las cuotas pendientes.
- Que los movimientos de un préstamo entre personas cambien el saldo de una cuenta sin aparecer en su
  lista de movimientos (el saldo saltaría sin explicación).
- Que las Visacuotas aparezcan en la pestaña Préstamos.

## 4. Enfoques considerados

1. **Todo dentro de Préstamos, con un tipo por caso.** Una sola tabla y una sola pantalla. Se
   descartó porque las Visacuotas son de la tarjeta y se pagan con ella, y el usuario no las piensa
   como un préstamo de su lista.
2. **Cada caso donde vive (elegido).** Las Visacuotas son un plan de cuotas de la tarjeta, con su
   tabla, su entrada en el formulario de gasto y su sección en el detalle de la tarjeta. Los
   préstamos entre personas quedan en Préstamos. Cuesta algo más de código, pero coincide con cómo el
   usuario piensa y paga cada cosa.
3. **Préstamo entre personas como una cuenta.** Prestar sería transferir a una cuenta "Juan".
   Reutiliza todas las transferencias, pero llena Cuentas con personas, y un préstamo no tiene la
   forma de una cuenta. Se descartó.

## 5. Visacuotas

### 5.1 Modelo

Tabla nueva `installment_plans` (llamada en código `InstallmentPlan`):

| Columna | Notas |
|---|---|
| `id` | PK |
| `accountId` | La tarjeta, o su subcuenta de segunda moneda. FK a `accounts`, RESTRICT. |
| `description` | Lo que se compró ("Laptop"). |
| `categoryId` | Opcional, FK SET_NULL. |
| `totalMinor` | Total en la moneda de la cuenta (`accountAmount` de la compra). |
| `installments` | N ≥ 2. |
| `purchaseDate` | Fecha de la compra (misma convención de fechas que `transactions.date`). |
| `currencyCode`, `originalMinor`, rate | La moneda y el monto originales de la compra, para mostrar "€500 en 3 cuotas". |

Cada cuota es una fila `EXPENSE` en `transactions` con la columna nueva `installmentPlanId` (FK a
`installment_plans`, CASCADE) e `installmentNumber`. Las cuotas se generan todas al crear el plan.
**No usan `loanId`**, así las consultas de préstamos no las ven.

- **Montos:** el total se divide en partes iguales y la última cuota absorbe el residuo (la misma
  regla que `Loan.installmentAmount`). Si la compra es en otra moneda, se convierte **una sola vez**,
  con la tasa del día de compra, como hace el banco. Cada cuota copia esa `accountRate`. `mainRate`
  se resuelve como en cualquier gasto de esa fecha.
- **Fechas:** la cuota 1 cae en el primer día de corte **≥ fecha de compra**. Así, una compra hecha
  el día del corte entra en ese corte, igual que en §8.2 ("movimientos con fecha ≤ Lc"). La cuota k
  cae en el k-ésimo corte desde ahí. El día de corte que no existe en un mes se ajusta al último día
  del mes (§8). La hora de la cuota es la de la compra, para que el orden en las listas sea estable.
- **Requisito:** la tarjeta debe tener día de corte. Si no lo tiene, el interruptor "A cuotas" no
  aparece.
- **Tasa principal de cuotas futuras** *(decisión del diseñador)*: una cuota con fecha futura no tiene
  tasa real aún. Se guarda la más cercana disponible como `APPROXIMATE`, y el arreglo existente de
  tasas aproximadas (`getWithApproximateRates`) la reemplaza cuando llega la tasa de su día. Solo
  afecta a cuentas en otra moneda que la principal.
- **Si la tarjeta cambia después** *(decisión del diseñador)*: mientras la tarjeta tenga planes con
  cuotas por cobrar, el formulario de cuenta no deja quitar "Tarjeta de crédito", quitar el día de
  corte ni quitar la segunda moneda que tenga planes. El mensaje explica por qué.
- **Cambio del día de corte** (decisión del usuario): las cuotas con fecha ≥ fin de hoy de todos los
  planes de esa tarjeta (y su subcuenta) se mueven a las nuevas fechas de corte. La primera cuota
  pendiente cae en el primer corte nuevo ≥ mañana y las siguientes, en los cortes que siguen. Las ya
  cobradas no se tocan, y se recalcula su tasa principal aproximada. Se hace en la misma transacción
  que guarda la cuenta. Test: el corte pasa del 15 al 25 con 2 de 3 cuotas pendientes.


### 5.2 Cómo encaja en los saldos

Las cuotas son gastos normales de una tarjeta. Por la regla de tarjetas (`COUNTED`: cuenta todo lo
fechado antes de mañana, confirmado o no), cada cuota entra sola en la deuda el día de su corte. No
hay que marcarlas como pagadas, y Inicio/Análisis la cuentan ese mes, como ya hacen con los gastos de
tarjeta.

- **A pagar del corte (§8):** sin cambios en la fórmula. La cuota fechada en `Lc` ya está en `S`.
- **Disponible:** pasa de `límite − usado` a `límite − usado − cuotas pendientes`, donde cuotas
  pendientes = Σ cuotas de esa tarjeta con fecha ≥ fin de hoy. Con tarjeta bimoneda, las cuotas
  pendientes de la subcuenta se convierten como ya se convierte su usado.
- **Deuda total:** en el detalle se muestra como "Usado Q X · Cuotas por cobrar Q Y". La barra de uso
  incluye las dos partes.
- **Aviso de pago (§8.5):** sin cambios. El "a pagar" ya incluye la cuota.

### 5.3 Pantallas

- **Formulario de gasto:** si la cuenta elegida es una tarjeta con día de corte, y el movimiento es
  un gasto nuevo y no recurrente, aparece el interruptor **"A cuotas"** con el número de cuotas
  (2–60) y el texto "Q1,000 por corte · primera el 15 dic". Recurrente y A cuotas se excluyen
  entre sí. Al guardar se crea el plan con sus N cuotas en lugar de un gasto.
- **Detalle de la tarjeta:** debajo del estado de cuenta va la sección **"Compras a cuotas"**, con
  los planes que aún tienen cuotas futuras. Cada fila muestra la descripción, "2/3", el monto por
  corte y lo que falta. El total de lo que falta aparece en el encabezado. Los planes completos se
  ocultan, con un enlace "Ver terminadas" si los hay.
- **Detalle del plan:** muestra las cuotas con su fecha de corte, cobradas o por cobrar. Desde ahí se
  puede editar la descripción y la categoría, que se propagan a todas las cuotas, o **eliminar** el
  plan. Eliminar borra todas sus cuotas, también las cobradas, y pide confirmación diciendo cuánto
  cambia la deuda. No se pueden cambiar el monto, las cuotas ni la fecha; para eso se elimina y se
  vuelve a crear.
- **Movimientos / detalle de cuenta / búsqueda:** cada cuota aparece en su fecha como
  "Laptop · cuota 2/3". Las cuotas con fecha futura no se listan. Tocar una cuota abre el detalle del
  plan, no el formulario de movimiento.
- **Pestaña Préstamos:** no muestra Visacuotas.

## 6. Préstamos entre personas

### 6.1 Modelo

Se reutiliza la tabla `loans` con una columna nueva `kind`: `SCHEDULED` (los préstamos de cuotas fijas
que ya existen, y es el valor por defecto en la migración) o `PERSONAL`. En `PERSONAL`:

- `name` es la persona ("Juan"), y `description` es opcional.
- `loanType` dice la dirección: `INCOME` = "Le presté" (me deben), `EXPENSE` = "Me prestaron" (debo).
  Se reutilizan las etiquetas de `LoanType`.
- `totalAmountMinor` + `currencyCode` = el monto prestado, en la moneda de la cuenta del desembolso.
  *(Decisión del diseñador)*: esa moneda queda fija una vez hay abonos, como ya indica
  `LoanEntity.currencyCode`. A partir de entonces, editar la cuenta del desembolso solo ofrece cuentas
  de la misma moneda.
- `accountId` = la cuenta del desembolso. `totalInstallments`, `frequency` e `startDate` no se usan,
  salvo `startDate` como fecha del préstamo.
- Fecha esperada opcional (`expectedDate`, columna nueva, nullable) para mostrar "esperado el …" y
  marcarlo vencido. Por ahora no genera recordatorio.

**Movimientos:** dos tipos nuevos en `TransactionType`, `LOAN_OUT` y `LOAN_IN`, siempre con `loanId`:

| Caso | Desembolso | Abono |
|---|---|---|
| Le presté | `LOAN_OUT` desde mi cuenta | `LOAN_IN` a la cuenta que elija |
| Me prestaron | `LOAN_IN` a mi cuenta | `LOAN_OUT` desde la cuenta que elija |

- Se guardan con `isCleared = true`, tienen sus tres montos como cualquier movimiento, y pasan por
  `MovementWriteGuard`.
- `isInflow` incluye `LOAN_IN`. `countsInReports` es falso para los dos, así que quedan fuera de
  Inicio, Análisis, Excel y categorías. El `BALANCE` de `AccountDao` suma `LOAN_IN`, y `LOAN_OUT` ya
  resta porque cae en la rama `ELSE`.
- Las cuentas permitidas son las que no son tarjeta y no están archivadas.
- **Lo que falta** = total − Σ abonos, en la moneda del préstamo. El abono se registra en la moneda
  del préstamo (`original`). Si la cuenta es de otra moneda, se convierte como cualquier movimiento.
- **Sobrepago:** un abono no puede pasar de lo que falta. El formulario lo limita y prellena con lo
  que falta. Si lo que falta llega a 0, el préstamo pasa a "Liquidado".
- El desembolso se edita desde el préstamo (monto, cuenta, fecha), siempre que lo que falta no quede
  negativo. Los abonos se editan o eliminan desde el detalle del préstamo. Eliminar el préstamo borra
  todos sus movimientos, previa confirmación.

### 6.2 Pantallas

- **Pestaña Préstamos:** dos grupos, **Me deben** y **Debo**, cada uno con su total en la moneda
  principal. Cada fila muestra la persona, "faltan Q750 de Q1,000" y "esperado el …" si hay fecha.
  Los préstamos de cuotas fijas van en el grupo de su dirección, con su fila actual. Los liquidados
  van al final o detrás de "Ver liquidados".
- **Botón +:** abre el formulario de préstamo con un selector arriba, **Entre personas** (por
  defecto) o **Cuotas fijas** (el formulario actual).
  - Entre personas: Le presté / Me prestaron, persona, monto, cuenta, fecha y fecha esperada
    opcional.
  - Cuotas fijas: gana el chip de cuenta, que guarda `accountId` (hoy el formulario no lo expone).
- **Detalle del préstamo entre personas:** lo que falta, en grande, y una lista de movimientos (el
  desembolso y los abonos) con su cuenta. Tiene el botón **"Registrar abono"**, que pide monto
  (prellenado con lo que falta), cuenta (por defecto la del desembolso) y fecha.
- **Movimientos / detalle de cuenta / búsqueda:** el desembolso y los abonos se listan, a diferencia
  de las cuotas fijas de hoy, con textos como "Préstamo a Juan", "Abono de Juan", "Préstamo de Ana" o
  "Abono a Ana". Tocarlos abre el detalle del préstamo.
- **Inicio, "Préstamos activos":** incluye los préstamos entre personas con lo que falta. La fila de
  un préstamo entre personas no muestra "próxima cuota".

### 6.3 Préstamos de cuotas fijas que ya existen

Siguen como hoy: cuotas `EXPENSE`/`INCOME` que cuentan al marcarlas pagadas. La única novedad es
poder elegir su cuenta, solo entre cuentas que no son tarjeta, para que el choque con la regla de
tarjetas no aparezca. Cambiar la cuenta mueve las cuotas no pagadas a la nueva cuenta, recalculando
su conversión; las pagadas se quedan donde están.

*(Decisión del diseñador)*: los préstamos que hoy ya caen en una tarjeta (cuenta por defecto o
`accountId` de tarjeta) **no se migran**. La migración solo agrega columnas. El chip muestra esa
tarjeta, y el usuario puede cambiarla por una cuenta que no sea tarjeta, pero una vez cambiada no
puede volver a elegir una tarjeta. Un préstamo nuevo sin cuenta elegida usa la cuenta por defecto,
salvo que sea tarjeta; en ese caso usa la primera cuenta activa que no sea tarjeta.

## 7. Datos, migración y respaldo

- **Room v2 → v3:**
  - tabla `installment_plans`;
  - en `transactions`, la columna `installmentPlanId` con su índice;
  - en `loans`, las columnas `kind` (default `'SCHEDULED'`) y `expectedDate`.
  - Los préstamos existentes quedan como `SCHEDULED`. Hay test de migración.
- **Respaldo v4 → v5:** incluye `installment_plans` y las columnas nuevas. Un respaldo v4 se
  restaura con `kind = SCHEDULED`.
- **Cambio de moneda principal:** las filas nuevas se recalculan como las demás. `installment_plans`
  guarda montos en moneda de la cuenta y no cambia.
- **Eliminar una cuenta:** sigue bloqueado si tiene movimientos (RESTRICT). Eso ya cubre los planes
  y los préstamos. El mensaje de error debe decir por qué.

## 8. Entrega

Son dos partes independientes. Mi propuesta es enviarlas en **dos PRs**, en este orden:

1. **`feat: compras a cuotas (Visacuotas) en tarjetas de crédito`**: migración v3 completa, con las
   columnas de préstamos también para no migrar dos veces, respaldo v5 y todo §5.
2. **`feat: préstamos entre personas con abonos libres`**: §6, incluido el chip de cuenta de las
   cuotas fijas.

Las dos van después de que se mergee el PR 3 (box-bm/BoxWallet#36).

**Tests clave**
- **Fechas de cuotas:** compra el día del corte, compra el día después del corte, corte el día 31 en
  febrero, que la última cuota absorbe el residuo, cambio del día de corte con cuotas pendientes, y el
  bloqueo al quitar la tarjeta o la segunda moneda.
- **§8 de la tarjeta:** el "a pagar" incluye solo la cuota del corte; el disponible resta las cuotas
  pendientes; tarjeta bimoneda.
- **Totales del mes:** una cuota cuenta en su mes y no en el de compra.
- **Préstamos entre personas:** el saldo cambia y los totales no cambian; abono en otra moneda; tope
  de sobrepago; "Liquidado".
- **Datos:** migración v2→v3 y respaldo v4→v5.

## 9. Lo que queda a criterio del constructor

- Los nombres exactos de clases, DAOs y rutas de navegación, siguiendo los patrones del PR 3.
- La disposición visual de las secciones nuevas, dentro de `DESIGN.md` y `Expressive.kt`.
- Si el detalle del plan de cuotas es una pantalla o una hoja inferior.
- El texto exacto de las etiquetas, en inglés y español, como el resto de `strings.xml`.
