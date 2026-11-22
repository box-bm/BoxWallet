@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.boxdev.boxwallet.ui.screens.settings

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.ui.components.CategoryBadge
import com.boxdev.boxwallet.ui.components.ChoiceToken
import com.boxdev.boxwallet.ui.components.ConfirmDeleteDialog
import com.boxdev.boxwallet.ui.components.LabeledBlock
import com.boxdev.boxwallet.ui.components.tone
import com.boxdev.boxwallet.ui.theme.BoxWalletTheme
import com.boxdev.boxwallet.ui.theme.container
import com.boxdev.boxwallet.ui.theme.onContainer
import com.boxdev.boxwallet.util.categoryIconOptions
import com.boxdev.boxwallet.util.categoryIconVector
import kotlinx.coroutines.launch

/** Editable copy of a category in the sheet; [preview] feeds the live badge. */
data class CategoryFormState(
    val id: Long? = null,
    val name: String = "",
    val description: String = "",
    val icon: String = "Category",
    val color: AccountColor = AccountColor.DEFAULT,
) {
    val canSave: Boolean get() = name.isNotBlank()

    /** The category as it would be saved; an edit keeps its real id, so it keeps its silhouette. */
    fun preview(): Category = Category(id = id ?: 0, name = name, description = description, icon = icon, color = color)

    /**
     * A new category has no id yet, so its seeded silhouette would follow the typed name; it previews
     * as a steady circle instead. An edit keeps its real silhouette.
     */
    val previewIsCircle: Boolean get() = id == null

    /** A new category with the automatic color has no tone until it is saved, so it previews neutral. */
    val previewIsNeutral: Boolean get() = previewIsCircle && color == AccountColor.DEFAULT

    companion object {
        fun from(category: Category?): CategoryFormState =
            category?.let { CategoryFormState(it.id, it.name, it.description, it.icon, it.color) } ?: CategoryFormState()

        internal val Saver = listSaver<CategoryFormState, Any>(
            save = { listOf(it.id ?: -1L, it.name, it.description, it.icon, it.color.key) },
            restore = {
                CategoryFormState(
                    id = (it[0] as Long).takeIf { id -> id >= 0 },
                    name = it[1] as String,
                    description = it[2] as String,
                    icon = it[3] as String,
                    color = AccountColor.fromKey(it[4] as String),
                )
            },
        )
    }
}

/**
 * Creates ([category] null) or edits a category in a bottom sheet: live badge preview, name,
 * description, color and icon, and a full-width Create / Save pinned above the keyboard.
 * Dismissing discards the edits. A non-null [onDelete] adds delete (with confirmation) to the header.
 */
@Composable
fun CategoryFormSheet(
    category: Category?,
    onDismiss: () -> Unit,
    onSave: (CategoryFormState) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var form by rememberSaveable(category?.id, stateSaver = CategoryFormState.Saver) {
        mutableStateOf(CategoryFormState.from(category))
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Hide on the sheet's own motion, then let the caller act and drop it.
    // A second tap while the sheet hides must not save or delete twice.
    var closing by remember { mutableStateOf(false) }
    val closeThen = { action: () -> Unit ->
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { action(); onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CategoryFormContent(
            form = form,
            onChange = { form = it },
            onSave = { if (!closing) { onSave(form); closeThen {} } },
            onDelete = onDelete?.let { { confirmDelete = true } },
            modifier = Modifier.imePadding(),
        )
    }

    if (confirmDelete && onDelete != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.categories_delete_title),
            message = stringResource(R.string.categories_delete_message, category?.name.orEmpty()),
            // Delete once the sheet is gone, so it never redraws without its category.
            onConfirm = {
                confirmDelete = false
                if (!closing) closeThen(onDelete)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The sheet's body, stateless so it can be previewed outside a [ModalBottomSheet]. */
@Composable
private fun CategoryFormContent(
    form: CategoryFormState,
    onChange: (CategoryFormState) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val (toneContainer, toneContent) = form.previewTone()
    Column(modifier) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (form.id == null) R.string.categories_new else R.string.categories_edit_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }

            BadgePreview(form)

            OutlinedTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = it)) },
                label = { Text(stringResource(R.string.categories_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { onChange(form.copy(description = it)) },
                label = { Text(stringResource(R.string.description_optional)) },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            LabeledBlock(stringResource(R.string.categories_color_label)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // DEFAULT shows the tone the category gets on its own (neutral while new), so it reads as "automatic".
                    val (autoContainer, autoContent) = form.copy(color = AccountColor.DEFAULT).previewTone()
                    AccountColor.entries.forEachIndexed { index, color ->
                        val selected = color == form.color
                        val isAuto = color == AccountColor.DEFAULT
                        ChoiceToken(
                            selected = selected,
                            container = if (isAuto) autoContainer else color.container(),
                            description = if (isAuto) stringResource(R.string.categories_color_automatic)
                            else stringResource(R.string.account_color_option, index + 1),
                            onClick = { onChange(form.copy(color = color)) },
                        ) {
                            if (selected) {
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = if (isAuto) autoContent else color.onContainer())
                            }
                        }
                    }
                }
            }

            LabeledBlock(stringResource(R.string.categories_icon_label)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoryIconOptions.forEach { (key, icon) ->
                        val selected = key == form.icon
                        ChoiceToken(
                            selected = selected,
                            container = if (selected) toneContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            description = key,
                            onClick = { onChange(form.copy(icon = key)) },
                        ) {
                            Icon(icon, contentDescription = null, tint = if (selected) toneContent else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Button(
            onClick = onSave,
            enabled = form.canSave,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
                .heightIn(min = ButtonDefaults.MediumContainerHeight),
        ) {
            Text(
                stringResource(if (form.id == null) R.string.categories_create else R.string.action_save),
                style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
            )
        }
    }
}

/**
 * Container / content pair the preview shows: neutral for a new category on the automatic color,
 * the chosen color's pair for a new one with a color, and the category's own tone when editing.
 */
@Composable
private fun CategoryFormState.previewTone(): Pair<Color, Color> = when {
    previewIsNeutral -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    previewIsCircle -> color.container() to color.onContainer()
    else -> preview().tone()
}

/**
 * The large live badge with the name under it. A new icon or color springs in (fast spatial
 * scale, default effects fade) so each choice is felt; typing the name never moves it.
 */
@Composable
private fun BadgePreview(form: CategoryFormState) {
    val motion = MaterialTheme.motionScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedContent(
            targetState = form,
            // Never the name: a new form is a steady circle, and an edit's silhouette follows its id.
            contentKey = { it.icon to it.color },
            transitionSpec = {
                (scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f) + fadeIn(motion.defaultEffectsSpec())) togetherWith
                    (scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f) + fadeOut(motion.fastEffectsSpec()))
            },
            contentAlignment = Alignment.Center,
            label = "categoryPreview",
        ) { state ->
            if (state.previewIsCircle) {
                val (container, content) = state.previewTone()
                CategoryBadge(
                    category = null,
                    fallbackIcon = categoryIconVector(state.icon),
                    fallbackContainer = container,
                    fallbackContent = content,
                    size = 72.dp,
                )
            } else {
                CategoryBadge(category = state.preview(), size = 72.dp)
            }
        }
        val preview = form.preview()
        val named = preview.name.isNotBlank()
        Text(
            if (named) preview.name else stringResource(R.string.categories_name_label),
            style = MaterialTheme.typography.titleLarge,
            color = if (named) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryFormPreviewContent(form: CategoryFormState) {
    BoxWalletTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(Modifier.padding(top = 16.dp)) {
                CategoryFormContent(form = form, onChange = {}, onSave = {}, onDelete = if (form.id != null) ({}) else null)
            }
        }
    }
}

@Preview(name = "New", widthDp = 411, heightDp = 1000)
@Composable
private fun CategoryFormNewPreview() = CategoryFormPreviewContent(CategoryFormState())

@Preview(name = "Edit, dark", widthDp = 411, heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryFormEditPreview() = CategoryFormPreviewContent(
    CategoryFormState(id = 3, name = "Groceries", description = "Weekly shop", icon = "ShoppingCart", color = AccountColor.TEAL),
)
