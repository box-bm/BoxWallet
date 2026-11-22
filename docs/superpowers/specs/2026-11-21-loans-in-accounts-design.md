# Préstamos dentro de las cuentas — diseño

Fecha: 2026-11-21 · PR 1 de 2 (el PR 2 es categorías, con su propio diseño).

Reemplaza la §6 de `2026-11-21-visacuotas-and-personal-loans-design.md` (préstamos entre personas), que
nunca se construyó. Las Visacuotas no cambian.

## 1. Intención

Ahora que hay varias cuentas, un préstamo debe vivir en una cuenta y mover su saldo de verdad. Si me
prestaron, el dinero entra a mi cuenta y cada pago lo va restando. Si yo presté, el dinero sale y cada
pago que recibo vuelve a entrar. El usuario debe saber en todo momento que tiene préstamos activos y
cómo afectan sus saldos, sin que inflen sus ingresos y gastos: ese dinero no es suyo (si lo recibió) o
sigue siendo suyo (si lo prestó).

Un préstamo es solo eso: **Le presté** o **Me prestaron**. Nada que ver con las compras a cuotas de
tarjeta.

## 2. Decisiones del usuario

- Un solo tipo de préstamo, con dos direcciones (*Le presté* / *Me prestaron*).
- **Una sola cuenta por préstamo**, elegida al crearlo. El desembolso y todos los pagos pasan por ella.
- **Plan de cuotas opcional** (fechas fijas, como hoy) **y abonos libres**: a veces depositan más de lo
  pactado para una fecha. Un préstamo puede tener las dos cosas.
- **Saldos y reportes (enfoque B):** los movimientos del préstamo mueven el saldo de la cuenta al
  momento y se ven en las listas, pero **no** entran en los totales de ingresos y gastos. Reportes
  muestra los préstamos en un bloque aparte.
- Se quita la pestaña **Préstamos**. La barra queda en **Inicio, Movimientos y Reportes**. Los
  préstamos se ven en las cuentas.
- Préstamos perdidos (no pagados) y condonaciones: **fuera de esta versión**.
- Dos PRs: este (préstamos) y otro para categorías.

## 3. Objetivos, no-objetivos, anti-objetivos

**Objetivos**
- Crear un préstamo en una cuenta (no tarjeta, no archivada); su desembolso mueve el saldo ese día.
- Registrar pagos por cualquier monto, con o sin plan de cuotas; cada pago mueve el saldo.
- Ver en el detalle de cada cuenta sus préstamos y cuánto falta.
- Ver en Inicio y en Reportes cuánto debo y cuánto me deben, aparte de ingresos y gastos.

**No-objetivos**
- Préstamos perdidos, condonación, intereses separados del capital.
- Pagar desde otra cuenta distinta a la del préstamo.
- Préstamos en tarjetas de crédito.
- Recordatorios o notificaciones de cuotas.
- Migrar préstamos existentes (la app no está en producción; ver §7).

**Anti-objetivos** (fallaría aunque funcione)
- Que un préstamo aparezca como ingreso o gasto en Inicio, Reportes, categorías o el Excel.
- Que el saldo de la cuenta no cambie al registrar el desembolso o un pago.
- Que un préstamo activo quede escondido: debe verse en Inicio y en su cuenta sin buscarlo.

## 4. Enfoques considerados

Con el ejemplo "me prestan Q10,000 y devuelvo Q11,000 en 10 cuotas":

| | Qué cuenta en ingresos/gastos | Trade-off |
|---|---|---|
| A. Todo cuenta | Desembolso = ingreso, pagos = gasto (categoría "Préstamos") | Simple, pero el mes del préstamo infla los ingresos y el balance del mes deja de ser real. |
| **B. Solo saldo + bloque aparte** ✅ | Nada; Reportes tiene su bloque de Préstamos | Totales confiables y deuda visible. El interés no aparece como gasto. |
| C. Solo el interés cuenta | La diferencia entre recibido y pagado | Lo más correcto contablemente, pero un campo más y la partición capital/interés con abonos libres complica la lógica. |

**Elegido: B** (usuario). La mayoría son préstamos entre personas; C puede venir después si hace falta.

Sobre el modelo de pagos, se consideraron "solo cuotas", "solo abonos" y "los dos". El usuario eligió
los dos, y se resuelven con un único mecanismo (§5.3): los pagos son siempre abonos y el plan es solo
una expectativa.

## 5. Diseño

### 5.1 Modelo

Se reutiliza la tabla `loans`, ajustada en el esquema v1 (sin migración):

- `name`: persona o entidad ("Juan", "Banco Industrial"). `description` opcional.
- `loanType`: `INCOME` = *Le presté* (me deben), `EXPENSE` = *Me prestaron* (debo). Se reutilizan
  las etiquetas de `LoanType`.
- `totalAmountMinor` + `currencyCode`: lo que se debe devolver, **en la moneda de la cuenta**. Como
  todo pasa por una sola cuenta, el préstamo siempre tiene la moneda de su cuenta.
- `disbursedAmountMinor`: lo que se recibió (*Me prestaron*) o entregó (*Le presté*) al inicio, en la
  misma moneda; es el monto del movimiento de desembolso. Puede ser menor, igual o mayor que el total
  (> 0, sin otra regla). En el formulario es opcional ("Recibí" / "Entregué"): vacío = igual al total.
  El total sigue mandando en lo pagado, lo que falta, las cuotas y si está saldado.
- `accountId`: **obligatorio** (deja de ser nullable). Solo cuentas que no son tarjeta y no están
  archivadas. Al borrar una cuenta con préstamos, sigue el RESTRICT de movimientos: se bloquea con un
  mensaje que dice por qué.
- `startDate`: fecha del desembolso.
- Plan (opcional): `totalInstallments` (0 = sin plan), `frequency`, `frequencyInterval` y una columna
  nueva `firstDueDate` (la primera cuota no tiene por qué caer el día del desembolso).
- `expectedDate` (nullable): para préstamos **sin plan**, la fecha en que se espera el pago total.
- Se eliminan `kind` y `categoryId` (los préstamos ya no usan categoría).

**Movimientos:** dos tipos nuevos en `TransactionType`, `LOAN_IN` y `LOAN_OUT`, siempre con `loanId`:

| | Desembolso | Pago |
|---|---|---|
| Le presté | `LOAN_OUT` | `LOAN_IN` |
| Me prestaron | `LOAN_IN` | `LOAN_OUT` |

- Siempre en la cuenta del préstamo, con `isCleared = true`, y pasan por `MovementWriteGuard` (sus
  tres montos se calculan como cualquier movimiento, para convertir a la moneda principal).
- `isInflow` incluye `LOAN_IN`. El `BALANCE` de `AccountDao` suma `LOAN_IN`; `LOAN_OUT` ya resta por
  la rama `ELSE`.
- `countsInReports` es falso para los dos: quedan fuera de Inicio (totales del mes), Reportes
  (ingresos, gastos, categorías, gráficas), el Excel y presupuestos.
- Ya no se generan movimientos por cuota: las cuotas no son filas (§5.3).

### 5.2 Saldo del préstamo

- **Pagado** = Σ pagos. **Falta** = total − pagado.
- Un pago no puede pasar de lo que falta. El formulario lo limita.
- Cuando falta llega a 0, el préstamo pasa a **Liquidado**.

### 5.3 Plan de cuotas y abonos libres

- Con plan, la cuota *n* vale una parte igual del total (la última absorbe el residuo, como hoy en
  `Loan.installmentAmount`) y vence en `firstDueDate` avanzada *n − 1* veces por la frecuencia.
- Las cuotas **no se guardan**: se calculan. Lo pagado las va **cubriendo en orden**. Una cuota está
  pagada, parcial o pendiente según cuánto de lo pagado le alcanza.
  - Ejemplo: cuotas de Q1,100 y un depósito de Q1,500 → cuota 1 pagada, cuota 2 con Q400 cubiertos.
- **"Marcar pagada"** en una cuota es un atajo: crea un pago por lo que le falta a esa cuota, con
  fecha de hoy (editable).
- **"Registrar abono"** crea un pago libre. Se prellena con lo que le falta a la próxima cuota (con
  plan) o con todo lo que falta (sin plan).
- **Atrasado:** con plan, si lo que vencía hasta hoy es mayor que lo pagado. Sin plan, si
  `expectedDate` ya pasó y falta algo.
- Etiquetas de los movimientos: "Préstamo de Banco" / "Préstamo a Juan" para el desembolso, "Pago a
  Banco" / "Pago de Juan" para los pagos. El texto exacto queda al constructor.

### 5.4 Editar y eliminar

- Editar el préstamo: nombre, descripción, monto, fecha, plan y fecha esperada, siempre que el total
  no quede por debajo de lo pagado. Cambiar el monto o la fecha actualiza el desembolso.
- Cambiar la cuenta mueve el desembolso y todos los pagos a la nueva cuenta. Solo se ofrecen cuentas
  de la misma moneda (el préstamo no cambia de moneda).
- Los pagos se editan o eliminan desde el detalle del préstamo.
- Eliminar el préstamo borra su desembolso y sus pagos, previa confirmación que lo dice.
- Los movimientos de préstamo no se editan desde el formulario de movimientos: tocarlos abre el
  detalle del préstamo (igual que hoy con los planes de cuotas).

### 5.5 Pantallas

**Navegación**
- Se quita el destino **Préstamos** de la barra y del riel. Quedan Inicio, Movimientos y Reportes.
- El detalle del préstamo pasa a ser una pantalla secundaria (sin barra), como el detalle de cuenta.
- El FAB conserva "Préstamo" en su menú. Desaparece el caso del FAB contextual de la pestaña
  Préstamos y el estado `loanDetailInSinglePane`.

```
Detalle de cuenta "Banco Industrial"
┌──────────────────────────────┐
│ Saldo  Q14,500               │
│ [Editar] [Transferir]        │
│                              │
│ Préstamos            [+ Nuevo]│
│  ↓ Me prestó Banco           │
│    Faltan Q9,900 · próx 15 dic│
│  ↑ Le presté a Juan          │
│    Faltan Q750 · esperado 30 nov│
│  Ver liquidados (2)          │
│                              │
│ Movimientos                  │
│  Préstamo de Banco  +10,000  │
│  Pago a Banco       −1,100   │
└──────────────────────────────┘
```

**Detalle de cuenta** (cuentas que no son tarjeta)
- Sección **Préstamos** con los préstamos activos de esa cuenta: dirección, persona, lo que falta y
  la próxima fecha (cuota o esperada), marcados si están atrasados. "Ver liquidados" despliega los
  liquidados. Tocar uno abre su detalle.
- Acción **"Nuevo préstamo"**, que abre el formulario con esa cuenta elegida.
- Sin préstamos, la sección no ocupa espacio (o solo la acción, a criterio del constructor).

**Formulario del préstamo** (hoja o pantalla, siguiendo el formulario actual)
- Le presté / Me prestaron, persona, monto, cuenta, fecha del desembolso, descripción opcional.
- Interruptor **"Pagos en cuotas"**: muestra número de cuotas, frecuencia y fecha de la primera cuota.
  Apagado, muestra **"Fecha esperada"** opcional.

**Detalle del préstamo**
- Arriba, lo que falta en grande, "de Q11,000", la cuenta y el estado (activo, atrasado, liquidado).
- Con plan, la lista de cuotas con fecha, monto y estado (pagada, parcial con lo cubierto, pendiente,
  atrasada), y "Marcar pagada" en la próxima.
- Los pagos registrados, con fecha y monto, editables.
- Botón **"Registrar abono"** (monto prellenado y fecha). Menú con editar y eliminar.

**Inicio**
- Los totales del mes no incluyen préstamos.
- Se mantiene la tarjeta **Préstamos activos** con "Debo" y "Me deben" en la moneda principal y una
  fila por préstamo. Tocar una fila abre el detalle.
- *(Decisión del diseñador)* "Ver todos" abre una lista de préstamos como pantalla secundaria
  (reutilizando lo que hoy es `LoansScreen`, agrupada en **Me deben** y **Debo**). No es una pestaña.

**Reportes**
- Ingresos, gastos, categorías y gráficas excluyen préstamos.
- Bloque nuevo **Préstamos** para el período: recibido y pagado (préstamos que me dieron), prestado y
  cobrado (préstamos que di), y el saldo actual **Debo / Me deben**. Reemplaza el bloque actual de
  "cuotas que vencen en el período", que puede seguir listando las cuotas pendientes del período.

**Movimientos y búsqueda**
- Los movimientos de préstamo se listan con un ícono de préstamo y su etiqueta, y no entran en los
  totales de la lista. El filtro Ingresos/Gastos no los incluye; "Todos" sí.
- La búsqueda los encuentra por el nombre de la persona.

## 6. Moneda

- El préstamo tiene la moneda de su cuenta, así que el monto, los pagos y lo que falta están en esa
  moneda, sin conversión dentro del préstamo.
- Inicio y Reportes suman en la moneda principal usando el monto convertido de cada movimiento (o el
  tipo de cambio actual para "lo que falta", siguiendo cómo lo hace hoy `remainingMainMinor`).
- Cambio de moneda principal: los movimientos de préstamo se recalculan como los demás.

## 7. Datos y respaldo

- **Base de datos siempre en versión 1**, sin migraciones. Se ajusta `loans` (§5.1) y
  `TransactionType`. Una instalación con el esquema anterior se borra al abrir la app.
- **Respaldo:** sube a la versión 6 (`BackupData.CURRENT_VERSION`). Solo se acepta el formato actual;
  los archivos de versión 5 se rechazan con el mensaje de siempre.
- Las cuotas fijas generadas como movimientos por el modelo anterior desaparecen con el cambio de
  esquema.

## 8. Entrega

Un PR: **`feat: préstamos dentro de tus cuentas, con cuotas y abonos libres`**, desde
`feat/loans-in-accounts`.

**Tests clave**
- El desembolso y cada pago cambian el saldo de la cuenta en el sentido correcto, en las dos
  direcciones.
- Los totales del mes, de Reportes, de categorías y del Excel no cambian con préstamos.
- Asignación de pagos a cuotas: pago exacto, pago mayor (cubre parte de la siguiente), pago parcial,
  residuo en la última cuota.
- Tope de lo que falta y paso a Liquidado.
- Atrasado con plan y sin plan.
- Cambiar la cuenta mueve todos los movimientos; editar el monto por debajo de lo pagado se rechaza.
- Eliminar el préstamo borra sus movimientos.
- Bloque de Préstamos de Reportes por período.
- Respaldo v6 de ida y vuelta, y rechazo de v5.
- Navegación: no queda el destino Préstamos; la lógica de pestañas (`resolveTabClick`) sigue pasando.

## 9. Lo que queda a criterio del constructor

- Nombres de clases, DAOs y rutas, siguiendo los patrones de transferencias y Visacuotas.
- Cómo distinguir el desembolso de un pago (se deduce de la dirección y el tipo, o con un campo).
- Si el formulario y el registro de abono son pantalla u hoja inferior.
- Disposición visual dentro de `DESIGN.md` y `Expressive.kt`, en las dos densidades (teléfono y
  plegable abierto).
- Textos exactos en inglés y español, como el resto de `strings.xml`.
- Eventos de analítica, siguiendo los existentes de préstamos.
