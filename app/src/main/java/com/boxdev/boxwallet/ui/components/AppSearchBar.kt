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
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import com.boxdev.boxwallet.ui.theme.LocalAppLayout
import com.boxdev.boxwallet.ui.theme.appLayout

/**
 * Search entry point that sits on the window background above the panes. It holds no query text:
 * the whole bar is one button that opens search; [trailing] stays separately focusable.
 */
@Composable
fun AppSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val placeholder = stringResource(R.string.search_in_app)
    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = BoxTheme.layout.margin, end = BoxTheme.layout.margin, top = 8.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(56.dp),
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
                AppSearchBar(onClick = {})
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
