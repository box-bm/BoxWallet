@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.boxdev.boxwallet.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.util.categoryIconVector
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import kotlin.math.absoluteValue

// ---------------------------------------------------------------------------------------------
// Category shape tokens: every category keeps one silhouette and one tone everywhere it appears.
// ---------------------------------------------------------------------------------------------

private val categoryPolygons: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Gem,
    MaterialShapes.Flower,
    MaterialShapes.SoftBurst,
    MaterialShapes.Pentagon,
    MaterialShapes.Puffy,
    MaterialShapes.Arch,
)

private fun Category.tokenSeed(): Int =
    if (id > 0) id.toInt() else (name + icon).hashCode().absoluteValue

fun Category.polygon(): RoundedPolygon = categoryPolygons[tokenSeed() % categoryPolygons.size]

/** Container / content pair for a category, taken from scheme roles so dynamic color still works. */
@Composable
fun Category.tone(): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when ((tokenSeed() / categoryPolygons.size + tokenSeed()) % 4) {
        0 -> c.primaryContainer to c.onPrimaryContainer
        1 -> c.tertiaryContainer to c.onTertiaryContainer
        2 -> c.secondaryContainer to c.onSecondaryContainer
        else -> c.surfaceContainerHighest to c.primary
    }
}

/** A [Shape] that renders a [Morph] at a given progress, scaled to fill its bounds. */
internal class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        morph.toPath(progress = progress, path = path)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        val bounds = path.getBounds()
        path.translate(androidx.compose.ui.geometry.Offset(size.width / 2f - bounds.center.x, size.height / 2f - bounds.center.y))
        return Outline.Generic(path)
    }
}

/**
 * The category badge: icon inside the category's polygon. While [interactionSource] is pressed the
 * silhouette relaxes into a circle (the app's signature press feedback).
 */
@Composable
fun CategoryBadge(
    category: Category?,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.AutoMirrored.Filled.TrendingDown,
    fallbackContainer: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    fallbackContent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 44.dp,
    interactionSource: MutableInteractionSource? = null,
    selected: Boolean = false,
) {
    val polygon = category?.polygon() ?: MaterialShapes.Circle
    val (container, content) = category?.tone() ?: (fallbackContainer to fallbackContent)
    val pressed = interactionSource?.collectIsPressedAsState()?.value ?: false
    val progress by animateFloatAsState(
        targetValue = if (pressed || selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "badgeMorph",
    )
    val morph = remember(polygon) { Morph(polygon, MaterialShapes.Circle) }
    Box(
        modifier = modifier
            .size(size)
            .clip(MorphShape(morph, progress))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category?.let { categoryIconVector(it.icon) } ?: fallbackIcon,
            contentDescription = category?.name,
            tint = content,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

/** A static polygon shape for decorative containers (empty states, onboarding). */
@Composable
fun polygonShape(polygon: RoundedPolygon): Shape = polygon.toShape()

// ---------------------------------------------------------------------------------------------
// Money text
// ---------------------------------------------------------------------------------------------

enum class AmountKind { Income, Expense, Neutral }

@Composable
fun AmountKind.color(): Color = when (this) {
    AmountKind.Income -> BoxTheme.money.income
    AmountKind.Expense -> BoxTheme.money.expense
    AmountKind.Neutral -> MaterialTheme.colorScheme.onSurface
}

/** Formatted amount. Signed amounts carry +/− so color is never the only signal. */
@Composable
fun AmountText(
    amount: Double,
    modifier: Modifier = Modifier,
    kind: AmountKind = AmountKind.Neutral,
    style: TextStyle = BoxTheme.moneyType.medium,
    signed: Boolean = kind != AmountKind.Neutral,
    color: Color = kind.color(),
    textAlign: TextAlign? = null,
    autoSize: Boolean = false,
) {
    val formatter = rememberCurrencyFormatter()
    val sign = when {
        !signed -> ""
        kind == AmountKind.Income -> "+"
        kind == AmountKind.Expense -> "−"
        else -> ""
    }
    Text(
        text = sign + formatter.format(amount),
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        autoSize = if (autoSize) TextAutoSize.StepBased(minFontSize = style.fontSize * 0.55f, maxFontSize = style.fontSize) else null,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------------------------
// Structure
// ---------------------------------------------------------------------------------------------

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Empty state: icon set inside a soft-burst polygon, a title, and an optional next step. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(polygonShape(MaterialShapes.Cookie9Sided))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        action?.invoke()
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

/** Constrains content to a readable measure on wide windows. */
fun Modifier.readableWidth(max: Dp = 720.dp): Modifier = this
    .fillMaxWidth()
    .widthIn(max = max)

// ---------------------------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------------------------

/** Connected single-choice button group (the Expressive replacement for segmented buttons). */
@Composable
fun <T> ConnectedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
    colors: @Composable (T) -> androidx.compose.material3.ToggleButtonColors = {
        ToggleButtonDefaults.toggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    },
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = colors(option),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                if (icon != null) {
                    Icon(icon(option), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Pill search field with a filter toggle that shows how many filters are active. */
@Composable
fun SearchFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    filtersOpen: Boolean,
    onToggleFilters: (Boolean) -> Unit,
    activeFilterCount: Int,
    modifier: Modifier = Modifier,
    showFilterToggle: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
        )
        if (showFilterToggle) BadgedBox(
            badge = {
                if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) }
            }
        ) {
            FilledTonalIconToggleButton(
                checked = filtersOpen,
                onCheckedChange = onToggleFilters,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.action_filter))
            }
        }
    }
}

/** A titled block inside a filter panel. */
@Composable
fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** Fallback icons for movements without a category. */
fun trendIcon(isIncome: Boolean): ImageVector =
    if (isIncome) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown

/** Tonal panel used for grouped content blocks. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, color = color, shape = shape, content = content)
}

/** True when the user has turned animations off (Settings › Accessibility › Remove animations). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
}

/**
 * Segmented-list shapes, except a lone row keeps the group's large outer radius instead of the
 * library's small default, so one-row groups match multi-row ones.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentShapes(index: Int, count: Int): androidx.compose.material3.ListItemShapes =
    if (count <= 1) {
        androidx.compose.material3.ListItemDefaults.shapes(
            shape = MaterialTheme.shapes.large,
            selectedShape = MaterialTheme.shapes.large,
            pressedShape = MaterialTheme.shapes.medium,
        )
    } else {
        androidx.compose.material3.ListItemDefaults.segmentedShapes(index = index, count = count)
    }
