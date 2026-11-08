# Layout Material list-detail, búsqueda superior y formularios a pantalla completa

Fecha: 2026-11-08 · Estado: aprobado en brainstorming

## Objetivo

Alinear el shell de BoxWallet con las guías de layout de Material 3 / Expressive
([list-detail](https://m3.material.io/foundations/layout/canonical-examples/list-detail),
[parts of layout](https://m3.material.io/foundations/layout/understanding-layout/parts-of-layout),
breakpoints compact y expanded), corregir el bug de navegación desde Ajustes y sacar los
formularios de los bottom sheets.

## Requisitos del usuario

1. Desde Ajustes, tocar una tab debe cambiar de pantalla.
2. Barra de búsqueda arriba en lugar del botón; quitar títulos de Movimientos, Préstamos y Reportes; ajustar "BoxWallet".
3. Ajustes solo accesible desde Inicio, junto a la barra de búsqueda.
4. Horizontal correcto: el teléfono rota y usa layout de paneles (decisión: no bloquear orientación).
5. Fondo igual al de la navegación; contenido en paneles blancos; la búsqueda sobre el fondo, no sobre el panel.
6. Formularios de movimiento y préstamo como pantallas independientes, sin modal.

## Reglas M3 que se aplican

- Todo el contenido vive en un panel. Compact: 1 panel, márgenes 16dp. Medium: 1 panel recomendado.
  Expanded+: 2 paneles, márgenes y espaciador de 24dp, panel fijo de 360dp + flexible, o dos flexibles 50/50.
- Botón atrás solo en layouts de un panel; estado seleccionado solo en layouts de dos paneles; sin
  selección, el panel de detalle muestra un placeholder.
- El rail puede ocultarse en destinos secundarios si hay botón atrás.
- Filtros y orden van dentro del panel.

## Enfoque elegido: híbrido

- Se mantiene el shell propio de navegación (`ShortNavigationBar` + FAB menu en compact;
  `WideNavigationRail` con FAB en el header en ancho; rail expandido ≥ 1200dp).
- Se agrega `androidx.compose.material3.adaptive` (`adaptive`, `adaptive-layout`, `adaptive-navigation`)
  para los list-detail (`NavigableListDetailPaneScaffold`) y para el tamaño de ventana
  (`currentWindowAdaptiveInfo()`), que reemplaza el cálculo con `screenWidthDp` de `WindowLayout.kt`.
- Componente nuevo `AppPane`: `surfaceContainerLowest` (blanco en claro), esquinas `extraLarge`,
  márgenes 16/24dp y espaciador de 24dp entre paneles.

## 1. Shell, navegación y orientación

- Sin bloqueo de orientación. El layout depende solo del tamaño de ventana (breakpoints 600 / 840 / 1200).

| Ventana | Navegación | Paneles | Márgenes |
|---|---|---|---|
| Compact | `ShortNavigationBar` + FAB menu | 1 | 16dp |
| Medium (incluye teléfono horizontal típico) | rail colapsado, FAB en header | 1 | 24dp |
| Expanded+ | rail (expandido ≥ 1200dp) | 2 donde aplique | 24dp + 24dp |

- Altura compact (< 480dp, teléfono horizontal): rail sin etiquetas, formularios con scroll, nada que dependa de altura grande.
- Fondo de toda la ventana: `surfaceContainer` (igual que barra y rail).
- Destinos secundarios: Ajustes y subpantallas, búsqueda, `loan_detail/{id}` independiente y los
  formularios. En ancho ocultan el rail y muestran atrás; en compact ocultan la barra inferior (como hoy).
- Bug de tabs: `navigateToTab` hacía `popUpTo(start){ saveState }` + `restoreState`, lo que guardaba
  Ajustes como estado de Inicio y lo restauraba al volver. La decisión de qué hacer al tocar una tab
  se extrae a una función pura (`SwitchTab`, `PopToRoot`, `ScrollToTop`, descartando antes los destinos
  secundarios) y nunca se guarda ni restaura una pila con destinos que no son tabs.
- FAB: mismo comportamiento contextual; visible solo en Inicio, Movimientos y Préstamos.

## 2. Barra de búsqueda y cabeceras

- `AppSearchBar`: `SearchBar` M3 colapsado sobre el fondo, input píldora de 56dp en `surfaceContainerHigh`,
  placeholder "Buscar en BoxWallet", icono de búsqueda al inicio y una acción al final según la pantalla:
  - Inicio: Ajustes (único punto de entrada a Ajustes).
  - Movimientos y Préstamos: Tune con badge de filtros activos (solo cuando los filtros no están abiertos como panel).
  - Reportes: sin acción.
- Al tocarla se abre la pantalla de búsqueda existente, con transición desde la barra.
- En ancho la barra ocupa el ancho del primer panel.
- Se eliminan los `LargeFlexibleTopAppBar` / `MediumFlexibleTopAppBar` de las cuatro tabs.
  "BoxWallet" solo vive en el placeholder.
- El subtítulo "N registros" de Movimientos pasa a una línea al inicio de la lista, dentro del panel.
- La barra no se colapsa con el scroll. Al volver a tocar una tab, la lista vuelve arriba;
  `TopAppBarScrollBehavior.expand()` se elimina.

## 3. Formularios a pantalla completa

- `TransactionSheet` y `LoanSheet` (`ModalBottomSheet`) se reemplazan por los destinos
  `transaction_form?id={id}` → `TransactionFormScreen` y `loan_form?id={id}` → `LoanFormScreen`.
  Sin `id` es nuevo; con `id` es editar.
- `TransactionFormViewModel` y `LoanFormViewModel` leen el `id` del `SavedStateHandle` y quedan
  ligados a la entrada de navegación.
- Patrón de diálogo a pantalla completa de M3: `TopAppBar` con ✕ al inicio, título y **Guardar** al
  final; al editar, eliminar con el diálogo de confirmación actual. Cuerpo con los mismos campos,
  scroll e `imePadding`. Fondo `surface` en toda la pantalla, sin `AppPane`.
- Si hay cambios sin guardar, atrás o ✕ preguntan "¿Descartar cambios?".
- Transición: entra desde abajo con fade y sale igual.
- En ancho: destino secundario sin rail, columna centrada de máximo 640dp.
- Todos los puntos de entrada (FAB, header del rail, listas, Inicio, Reportes, búsqueda, detalle de préstamo)
  navegan a la ruta. Se eliminan `showTransactionSheet`, `showLoanSheet` y `selectedTransactionId` de `NavigationTab`.

## 4. Layout por pantalla

| Pantalla | Compact | Medium | Expanded+ |
|---|---|---|---|
| Inicio | 1 panel: balance → recientes → préstamos | 1 panel | 2 paneles 50/50: balance y préstamos \| recientes |
| Movimientos | 1 panel, filtros plegables (Tune) | 1 panel con Tune | filtros fijos 360dp \| lista flexible |
| Préstamos | lista → detalle en un panel con atrás | igual | `NavigableListDetailPaneScaffold`: lista 360dp \| detalle, placeholder sin selección, tarjeta seleccionada |
| Reportes | 1 panel | 1 panel | 1 panel con las dos columnas internas actuales |
| Ajustes | lista → subpantalla con atrás | igual | list-detail: categorías 360dp \| detalle (General, Monedas, Categorías, Tema, Respaldo, Datos) |
| Búsqueda | pantalla completa | igual | igual, resultados hasta 720dp |

- Un préstamo abierto desde Inicio, Búsqueda o Reportes usa `loan_detail/{id}` como destino secundario;
  atrás vuelve al origen.
- Detalle de moneda y cambio de moneda principal siguen siendo destinos completos encima de Ajustes.
- Plegar o rotar: el scaffold conserva la selección (detalle en un panel si había selección, lista si no)
  y el scroll.
- Las filas de las listas siguen en `surfaceContainer` sobre el panel blanco. Los paneles laterales que
  hoy son `surfaceContainerLow` pasan a ser `AppPane`.

## 5. Pruebas y documentación

- Unit tests:
  - `TabNavigationTest`: decisión pura de navegación de tabs, incluido el caso de Ajustes.
  - `AppLayoutTest`: tamaño de ventana → navegación, paneles y márgenes, incluida la altura compact.
  - Tests de `TransactionFormViewModel` y `LoanFormViewModel` con `id` desde `SavedStateHandle` (nuevo y editar).
  - `FormDirtyTest`: regla de cambios sin guardar.
- Verificación manual en emulador de teléfono (vertical y horizontal) y de foldable o tablet, con capturas.
- Actualizar la sección Layout y los componentes de `DESIGN.md` (paneles, fondo, búsqueda, formularios;
  quitar sheets y top bars flexibles), y `README.md` si aplica.

## Entrega en 3 PRs

1. `fix: tabs no longer reopen settings after leaving it`: solo el bug de navegación y su test.
2. `feat: material list-detail layout with top search bar`: secciones 1, 2 y 4 más la documentación.
3. `feat: full-screen forms for movements and loans`: sección 3.

## Fuera de alcance

- Cambiar campos o validaciones de los formularios.
- Rediseñar el contenido de Reportes o de la búsqueda.
- `NavigationSuiteScaffold` (se mantiene el shell propio).
