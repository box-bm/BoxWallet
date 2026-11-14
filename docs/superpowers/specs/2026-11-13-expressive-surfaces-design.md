# Rediseño de superficies y contención según Material 3 Expressive

Fecha: 2026-11-13 · Estado: diseño aprobado en brainstorming, pendiente de revisión escrita

## Objetivo

Alinear el sistema visual de BoxWallet con las guías oficiales de Material 3 Expressive en lo que
hoy se desvía: roles de superficie, contención, márgenes y escala de formas. Se conservan todas las
funciones, la paleta, la tipografía, la navegación y el comportamiento adaptativo.

Este diseño reemplaza el requisito 5 de `2026-11-08-material-layout-design.md` ("fondo igual al de la
navegación; contenido en paneles blancos"). El resto de ese documento sigue vigente.

## Requisito del usuario

- Rediseño completo siguiendo Material 3 Expressive, revisando en cada pantalla qué va sobre el fondo,
  qué va contenido y si debe existir un fondo blanco. Ejemplo citado: en Inicio, la tarjeta de saldo
  sobre el panel blanco.
- Decisión tomada: **opción A, sin panes visibles**.

## Reglas M3 que se aplican

Fuentes: [Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive),
[Color roles](https://m3.material.io/styles/color/roles),
[Parts of layout](https://m3.material.io/foundations/layout/layout-overview/parts-of-layout),
[Lists](https://m3.material.io/components/lists/guidelines),
[Corner radius scale](https://m3.material.io/styles/shape/corner-radius-scale).

1. El cuerpo usa `surface` y la zona de navegación usa `surface container`, igual en todos los
   breakpoints. Los demás tonos de contenedor crean jerarquía y anidación en pantallas grandes.
2. Todo el contenido vive en un pane, pero el pane puede fundirse con el fondo (agrupación
   implícita). El color explícito se usa en layouts de varios panes para mostrar énfasis.
3. Contener para enfatizar: agrupar contenido relacionado y dar al más importante espacio y la
   superficie más brillante.
4. Listas de borde a borde en compact; ítems rellenos con separación segmentada, sin divisores.
5. Escala de radios: 0, 4, 8, 12, 16, 20, 28, 32, 48 dp y full. Al anidar, radio interior =
   radio exterior − padding. Sin esquinas grandes en contenedores densos.
6. Uno o dos momentos hero por producto.

## Hallazgos de la auditoría que se corrigen

| # | Hallazgo | Dónde |
|---|---|---|
| 1 | Cuerpo en `surfaceContainer` (igual que la navegación) con un pane `surfaceContainerLowest` encima | 4 pestañas, Búsqueda, Ajustes en dos panes |
| 2 | Pane explícito en compact: doble margen (16 + 16 dp) y hasta 4 niveles anidados | Inicio, Reportes |
| 3 | La superficie más brillante es un contenedor casi vacío | Préstamos, Movimientos |
| 4 | Pestañas con pane y pantallas secundarias sin pane | Global |
| 5 | Redondez óptica: saldo de 36 dp dentro de un pane de 36 dp | Inicio |
| 6 | Contenido suelto pegado al borde del pane | Préstamos (resumen), Movimientos (contador) |
| 7 | Tres bloques de relleno fuerte compiten | Reportes |
| 8 | Barra de búsqueda con poco contraste contra el fondo | Pestañas |

## Diseño

### 1. Superficies

- **Ventana:** `surface` en todos los tamaños (shell de `NavigationTab.kt`, `SearchScreen`,
  `SettingsScreen`, overlay de `NavGraph.kt`). `ShortNavigationBar` y `WideNavigationRail` siguen en
  `surfaceContainer`.
- **`PaneRow` / `AppPane`:** `PaneRow` conserva márgenes (16 dp compact, 24 dp medium y expanded),
  espaciador de 24 dp y anchos (360 dp fijo o 50/50), pero sus panes ya no pintan fondo ni forma.
  `AppPane` pasa a ser solo el **pane contenido** y se usa únicamente donde esta especificación lo indica.
- **Pane contenido (solo con dos panes):** `surfaceContainerLow`, esquinas `extraLargeIncreased`
  (32 dp). Se aplica a:
  - Movimientos: pane de filtros (360 dp).
  - Préstamos: pane de detalle, incluido su placeholder.
  - Ajustes: pane de la página.
  - Inicio: ninguno; son dos columnas implícitas.
  - Reportes: ninguno.
- **Tono de las tarjetas:** nuevo `LocalCardColor` (expuesto como `BoxTheme.cardColor`). Vale
  `surfaceContainer` por defecto y `surfaceContainerHigh` dentro de un pane contenido; `AppPane` lo
  provee. Lo leen los valores por defecto de `TransactionItem`, `LoanCard`, `LoanRow`, las filas de
  `SettingsKit`, la superficie de grupo de `Expressive.kt`, las tarjetas de `LoanDetailScreen` y
  `CurrencyDetailScreen`, y los helpers de gráficos que mezclan hacia la tarjeta (`ChartKit`,
  `RateLineChart`).
- **Pila tonal resultante:** `surface` (cuerpo) → `surfaceContainerLow` (pane contenido) →
  `surfaceContainer` (tarjetas, filas, grupos, navegación) → `surfaceContainerHigh` (campos, barra de
  búsqueda, tarjetas dentro de un pane contenido) → `surfaceContainerHighest` (tracks, chips, toggles
  sin seleccionar). Sin sombras ni bordes nuevos.
- **Margen de contenido:** el contenido de cada pane deja de añadir 16 dp horizontales propios; el
  margen lo pone `PaneRow` una sola vez. Dentro de un pane contenido el padding interno es 16 dp.

### 2. Formas

`BoxShapes` adopta los valores de la escala M3 y los componentes se reasignan al token equivalente
a su radio actual:

| Token | Hoy | Nuevo |
|---|---|---|
| `extraSmall` | 8 | 4 |
| `small` | 12 | 8 |
| `medium` | 20 | 12 |
| `large` | 28 | 16 |
| `largeIncreased` | — | 20 |
| `extraLarge` | 36 | 28 |
| `extraLargeIncreased` | — | 32 |
| `extraExtraLarge` | — | 48 |

Reasignación de usos (34 llamadas a `MaterialTheme.shapes.*`):

- Lo que hoy usa `large` (28: tarjetas de préstamo, campos, superficies de formulario, menús) →
  `extraLarge` (28). Sin cambio visual.
- Lo que hoy usa `extraLarge` (36: tarjeta de saldo, campo de monto, campo de búsqueda global,
  tarjetas de detalle) → `extraLargeIncreased` (32).
- Lo que hoy usa `medium` (20: tiles de ingreso/gasto en Reportes, filas de categoría, estado
  presionado) → `largeIncreased` (20), salvo los tiles anidados, que siguen la regla de redondez
  óptica: en la tarjeta de resumen (28 dp, padding 20 dp) los tiles usan `medium` (12 dp).
- `GroupShapes` (24 exterior / 6 interior) pasa a 28 / 4 para coincidir con `extraLarge` y
  `extraSmall`.
- Chips y botones siguen en full.

### 3. Pantallas

- **Inicio:** barra de búsqueda; `BalancePanel` directo sobre `surface` a todo el ancho del contenido
  (único hero); secciones "Movimientos recientes" y "Préstamos activos" como grupos de filas. En
  expanded: dos columnas implícitas (saldo y préstamos | movimientos).
- **Movimientos:** contador y encabezados de fecha sobre `surface`; el fondo del encabezado fijo pasa
  de `surfaceContainerLowest` a `surface`. En expanded: filtros en pane contenido, lista implícita.
  El panel plegable de filtros en compact sigue en `surfaceContainerLow`.
- **Préstamos:** el resumen "Debes / Te deben" pasa a un grupo contenido de dos tiles
  (`BoxTheme.cardColor`, etiqueta en label-large y monto en Money Large, formas de `GroupShapes`
  horizontales). Tarjetas sin cambios. En expanded: lista implícita, detalle en pane contenido.
- **Reportes:** la tarjeta de resumen (`secondaryContainer`) queda como énfasis. El navegador de
  periodo baja de peso: la pill del mes pasa a `surfaceContainerHigh` y las flechas a `IconButton`
  estándar; solo el periodo seleccionado del selector conserva relleno primario. Las tarjetas de
  sección quedan en `surfaceContainer` sobre `surface`.
- **Búsqueda:** fondo `surface`; resultados sin pane, con el mismo tope de 720 dp.
- **Ajustes:** compact sin cambios. Dos panes: lista de secciones implícita, página en pane contenido.
- **Detalle de préstamo, formularios, detalle de moneda, onboarding, bloqueo:** solo la reasignación
  de formas y `BoxTheme.cardColor`.
- **Barra de búsqueda superior:** se mantiene en `surfaceContainerHigh`; gana contraste porque el
  fondo pasa a `surface`.

### 4. Fuera de alcance

Paleta y esquemas de contraste, tipografía, navegación y destinos, textos, lógica de view models,
qué pantallas muestran uno o dos panes, color dinámico.

## Archivos

- `ui/theme/Shape.kt`, `ui/theme/Theme.kt` (nuevo `LocalCardColor`, `BoxTheme.cardColor`).
- `ui/components/Panes.kt`, `Expressive.kt`, `TransactionItem.kt`, `LoanCard.kt`, `BalancePanel.kt`,
  `AppSearchBar.kt`, `FormFields.kt`, `ConversionRow.kt`, `CurrencyPickerSheet.kt`,
  `charts/ChartKit.kt`, `charts/RateLineChart.kt`, `charts/CategoryDonut.kt`.
- `ui/navigation/NavigationTab.kt`, `NavGraph.kt`.
- `ui/screens/home/HomeScreen.kt`, `transactions/TransactionsListScreen.kt`, `loans/LoansScreen.kt`,
  `loans/LoanDetailScreen.kt`, `loans/LoanFormScreen.kt`, `transaction/TransactionFormScreen.kt`,
  `analytics/AnalyticsScreen.kt`, `search/SearchScreen.kt`, `settings/SettingsScreen.kt`,
  `settings/SettingsKit.kt`, `settings/CurrencyDetailScreen.kt`, `settings/CategoriesScreen.kt`,
  `settings/DataManagementScreen.kt`, `onboarding/OnboardingScreen.kt`.
- `DESIGN.md`: front matter (`rounded`, componentes), Overview, Colors → Neutral, Layout → Window and
  panes, Elevation, Shapes, Do's and Don'ts.

## Pruebas y verificación

- No hay lógica nueva que requiera pruebas unitarias; `GroupShapes` y `appLayout` conservan su
  interfaz. `./gradlew testDebugUnitTest` debe seguir en verde.
- Verificación visual en el emulador Pixel Fold, plegado (compact) y desplegado (expanded), en tema
  claro y oscuro: Inicio, Movimientos, Préstamos (con y sin selección), Reportes, Búsqueda, Ajustes,
  formulario de movimiento y detalle de préstamo.
- Criterios: ningún fondo `surfaceContainerLowest`; un solo margen horizontal en compact; ningún
  contenedor anidado con el mismo radio que su padre; el saldo es el único relleno primario en
  Inicio; el contraste entre tarjeta y fondo se mantiene dentro de los panes contenidos.

## Entrega

Rama `feat/expressive-surfaces`, PR contra `main` con título Conventional Commit
(`feat: ...`, descripción escrita para usuarios porque entra en "What's new").
