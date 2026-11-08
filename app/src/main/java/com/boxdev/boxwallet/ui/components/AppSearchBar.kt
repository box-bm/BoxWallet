package com.boxdev.boxwallet.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import com.boxdev.boxwallet.ui.theme.LocalAppLayout
import com.boxdev.boxwallet.ui.theme.appLayout

/** How wide the search bar runs: the whole row, or only over the first of two panes below it. */
sealed interface SearchBarSpan {
    /** Single pane: the bar spans the content width. */
    data object Full : SearchBarSpan

    /** Two 50/50 panes: the bar spans the first one. */
    data object HalfPane : SearchBarSpan

    /** A fixed first pane: the bar is exactly [width] wide, like the pane under it. */
    data class Fixed(val width: Dp) : SearchBarSpan
}

/**
 * Search entry point that sits on the window background above the panes. It holds no query text:
 * the search icon and placeholder form one button that opens search; [trailing] is a separate
 * control beside it, focusable on its own.
 */
@Composable
fun AppSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    span: SearchBarSpan = SearchBarSpan.Full,
    trailing: @Composable (() -> Unit)? = null,
) {
    val placeholder = stringResource(R.string.search_in_app)
    val margin = BoxTheme.layout.margin
    Row(
        modifier = modifier
            // Same horizontal insets as the panes below, so their edges line up.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = margin, end = margin, top = 8.dp, bottom = 8.dp)
            .fillMaxWidth(),
    ) {
        val barModifier = when (span) {
            SearchBarSpan.Full, SearchBarSpan.HalfPane -> Modifier.weight(1f)
            is SearchBarSpan.Fixed -> Modifier.width(span.width)
        }
        SearchBarPill(onClick, placeholder, trailing, barModifier.height(56.dp))
        if (span == SearchBarSpan.HalfPane) {
            Spacer(Modifier.width(24.dp))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SearchBarPill(
    onClick: () -> Unit,
    placeholder: String,
    trailing: @Composable (() -> Unit)?,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    placeholder,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                Box(Modifier.padding(end = 4.dp)) { trailing() }
            }
        }
    }
}

@Composable
private fun SearchBarPreviewContent(width: Int) {
    BoxWalletTheme {
        CompositionLocalProvider(LocalAppLayout provides appLayout(width, 800)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                AppSearchBar(onClick = {}, span = if (width >= 840) SearchBarSpan.HalfPane else SearchBarSpan.Full)
            }
        }
    }
}

@Preview(name = "Compact light", widthDp = 411)
@Composable
private fun AppSearchBarCompactPreview() = SearchBarPreviewContent(411)

@Preview(name = "Compact dark", widthDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppSearchBarCompactDarkPreview() = SearchBarPreviewContent(411)

@Preview(name = "Expanded light", widthDp = 900)
@Composable
private fun AppSearchBarExpandedPreview() = SearchBarPreviewContent(900)

@Preview(name = "Expanded dark", widthDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppSearchBarExpandedDarkPreview() = SearchBarPreviewContent(900)
