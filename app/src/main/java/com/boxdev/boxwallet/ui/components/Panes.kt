@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import com.boxdev.boxwallet.ui.theme.LocalAppLayout
import com.boxdev.boxwallet.ui.theme.LocalCardColor
import com.boxdev.boxwallet.ui.theme.appLayout

/**
 * A contained pane: the supporting pane of a two-pane layout (detail, filters), one step above the
 * surface body. Cards inside it step up too, through [LocalCardColor], so they keep their contrast.
 */
@Composable
fun AppPane(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        CompositionLocalProvider(LocalCardColor provides MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(content = content)
        }
    }
}

/**
 * Lays out one or two panes with [BoxTheme.layout] margin at start and end and a 24dp gap.
 * Panes blend into the surface body unless [containFirst] / [containSecond] puts one in an [AppPane];
 * contain only the supporting pane of a two-pane layout.
 * A fixed [firstWidth] is meant for two-pane (expanded) layouts only.
 * `second == null` gives a single pane. [firstWidth] null splits 50/50, otherwise the first pane is fixed.
 */
@Composable
fun PaneRow(
    first: @Composable () -> Unit,
    second: (@Composable () -> Unit)? = null,
    firstWidth: Dp? = null,
    modifier: Modifier = Modifier,
    containFirst: Boolean = false,
    containSecond: Boolean = false,
) {
    val margin = BoxTheme.layout.margin
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = margin, end = margin),
    ) {
        val firstModifier = if (second != null && firstWidth != null) {
            Modifier.width(firstWidth).fillMaxHeight()
        } else {
            Modifier.weight(1f).fillMaxHeight()
        }
        Pane(firstModifier, containFirst, first)
        if (second != null) {
            Spacer(Modifier.width(24.dp))
            Pane(Modifier.weight(1f).fillMaxHeight(), containSecond, second)
        }
    }
}

@Composable
private fun Pane(modifier: Modifier, contained: Boolean, content: @Composable () -> Unit) {
    // Only a contained pane stops short of the window's bottom edge; content on the body scrolls to it.
    if (contained) AppPane(modifier.padding(bottom = BoxTheme.layout.margin)) { content() } else Box(modifier) { content() }
}

/**
 * What an empty detail pane shows before anything is selected. It scrolls, so the hint stays
 * reachable on short windows (phone landscape), and starts lower when there is room.
 */
@Composable
fun PanePlaceholder(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        EmptyState(
            icon = icon,
            title = title,
            modifier = Modifier.padding(top = if (BoxTheme.layout.compactHeight) 0.dp else 96.dp),
        )
    }
}

@Composable
private fun PanesPreviewContent(width: Int, firstWidth: Dp?, two: Boolean) {
    BoxWalletTheme {
        CompositionLocalProvider(LocalAppLayout provides appLayout(width, 800)) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                PaneRow(
                    first = { Text("First pane", Modifier.padding(16.dp)) },
                    second = if (two) ({ Text("Second pane", Modifier.padding(16.dp)) }) else null,
                    firstWidth = firstWidth,
                    containSecond = two,
                )
            }
        }
    }
}

@Preview(name = "Compact light", widthDp = 411, heightDp = 600)
@Composable
private fun PaneRowCompactPreview() = PanesPreviewContent(411, null, two = false)

@Preview(name = "Compact dark", widthDp = 411, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaneRowCompactDarkPreview() = PanesPreviewContent(411, null, two = false)

@Preview(name = "Expanded light", widthDp = 900, heightDp = 600)
@Composable
private fun PaneRowExpandedPreview() = PanesPreviewContent(900, 360.dp, two = true)

@Preview(name = "Expanded dark", widthDp = 900, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaneRowExpandedDarkPreview() = PanesPreviewContent(900, null, two = true)
