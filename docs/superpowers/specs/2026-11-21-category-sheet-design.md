# Categorías: principal, formulario en hoja y botón de agregar — diseño

Fecha: 2026-11-21 · PR 2 de 2 (el PR 1 es `2026-11-21-loans-in-accounts-design.md`, en
`feat/loans-in-accounts`). Cambio pequeño.

## Intención

La pantalla de Categorías se siente genérica: el formulario es un diálogo, el FAB extendido es pesado
y la categoría principal vive lejos, en Ajustes generales. Se quiere una pantalla de categorías completa
y formal, con icono **y color** propios, siguiendo **Material 3 Expressive** (`DESIGN.md`,
`Expressive.kt`).

## Decisiones del usuario

- La **categoría principal** sale de Ajustes generales y pasa a la pantalla de Categorías (dentro de
  Ajustes).
- **Pantalla, opción A:** tarjeta destacada "Categoría principal" arriba de la lista, con "Cambiar", y
  un **FAB compacto solo con "+"** (en lugar de `MediumExtendedFloatingActionButton` con texto).
- **Formulario, opción A:** hoja inferior (`ModalBottomSheet`) con todo: vista previa grande de la
  insignia arriba (cambia en vivo), nombre, descripción opcional, fila de colores y cuadrícula de
  iconos; la hoja se expande al deslizar. Botón Guardar / Crear.
- Seguir los lineamientos de Material 3 Expressive.

## Objetivos / no-objetivos / anti-objetivos

- **Objetivos:** color por categoría; formulario en hoja; principal en Categorías; FAB rediseñado.
- **No-objetivos:** reordenar categorías, subcategorías, búsqueda de iconos, iconos nuevos.
- **Anti-objetivos:** que el color elegido rompa el contraste en claro u oscuro, o que las categorías
  existentes pierdan el color que hoy les da el tema.

## Enfoques considerados

- Pantalla: **A** tarjeta arriba + FAB compacto ✅; B grupo de ajuste + fila "Nueva categoría" (sin
  FAB; la fila queda al fondo con muchas categorías); C etiqueta "Principal" en la fila + botón en la
  barra (más compacto, la principal se nota menos).
- Formulario: **A** todo en una hoja con vista previa ✅; B pestañas Icono/Color con búsqueda (más
  compacto, un nivel más de navegación); C formulario corto + selector de iconos aparte (un paso más
  por icono).

## Diseño

**Datos**
- `CategoryEntity` gana `color: String = "DEFAULT"`, con las claves de `AccountColor` (12 tonos, ya
  validados a 4.5:1 en claro y oscuro por `AccountColorsTest`). Base de datos sigue en v1, sin migración.
- `DEFAULT` conserva el tono automático de hoy (`Category.tone()` por semilla), así nada cambia para
  quien no elija color. Un color elegido usa su par contenedor / contenido de `AccountColors`.
- La forma (polígono) de la insignia sigue saliendo de la semilla, como hoy.
- El respaldo incluye `color` y sube su versión de formato una más que la del PR 1 (si se mergea antes
  que el PR 1, sube a 6 y el PR 1 pasa a 7). Solo se acepta el formato actual.
- La preferencia `defaultCategoryId` (DataStore) no cambia; solo se mueve su interfaz.

**Pantalla de Categorías**
- Arriba, tarjeta tonal "Categoría principal" con la insignia, el nombre (o "Ninguna") y "Cambiar",
  que abre un selector (hoja con la lista de categorías y "Ninguna"). Explica en una línea que se elige
  sola en cada movimiento nuevo.
- Debajo, la lista segmentada (`SegmentedListItem`) como hoy, sin el botón de borrar en la fila; toca
  una fila para editar.
- FAB compacto (`FloatingActionButton` estándar con "+", descripción accesible "Nueva categoría"), que
  se oculta al desplazar hacia abajo si el patrón ya existe en la app.
- Ajustes generales pierde la fila de categoría principal y su diálogo.

**Hoja del formulario**
- Título "Nueva categoría" / "Editar categoría"; en editar, acción de borrar (con la confirmación
  actual) en la cabecera.
- Vista previa: `CategoryBadge` grande con el color e icono elegidos y el nombre debajo.
- Campos: nombre (obligatorio), descripción (opcional).
- Color: fila de 12 muestras (`DEFAULT` + 11), la elegida con marca de selección, como en el
  formulario de cuenta.
- Icono: cuadrícula de `categoryIconOptions`, la elegida en el color de la categoría.
- Botón principal ancho al fondo, siempre visible por encima del teclado.
- Borrar la categoría principal la deja en "Ninguna".

## Tests clave

- Guardar y leer el color; `DEFAULT` conserva el tono automático.
- Respaldo de ida y vuelta con color.
- Cambiar y quitar la principal desde Categorías; borrar la principal la deja en Ninguna.

## A criterio del constructor

- Medidas, movimiento y formas exactas dentro de `DESIGN.md` y `Expressive.kt` (p. ej. el morph de la
  insignia al seleccionar, `motionScheme` para la vista previa).
- Si la gráfica de categorías (`CategoryDonut`) usa el color elegido o sigue con su paleta.
- Textos exactos en inglés y español.
