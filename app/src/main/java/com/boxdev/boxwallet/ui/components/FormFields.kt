@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.components

import androidx.compose.material3.SelectableDates
import androidx.compose.material3.DatePickerDefaults
import java.time.LocalDate
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.util.DateFormats
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.GroupShapes
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

// ---------------------------------------------------------------------------------------------
// Sheet chrome
// ---------------------------------------------------------------------------------------------

/** Sheet title row. */
@Composable
fun SheetHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------------------------
// Amount
// ---------------------------------------------------------------------------------------------

/**
 * The amount is the point of the form, so it is typed at display size in the numerals face,
 * tinted by direction. The currency symbol sits in front at a smaller size.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    symbol: String,
    kind: AmountKind,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.transaction_amount_label),
    isError: Boolean = false,
    autoFocus: Boolean = false,
    currencyCode: String? = null,
    onCurrencyClick: (() -> Unit)? = null,
    currencyEnabled: Boolean = true,
    /** Spoken instead of the default chip description, e.g. why the currency is locked. */
    currencyDisabledDescription: String? = null,
    /** Shown in the header just before the currency button, e.g. the movement's account. */
    accountChip: (@Composable () -> Unit)? = null,
) {
    val color by animateColorAsState(
        if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else kind.color(),
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "amountColor",
    )
    val style = BoxTheme.moneyType.hero.copy(color = color, textAlign = TextAlign.Center)
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        border = if (isError) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val labelText: @Composable () -> Unit = {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (currencyCode != null && onCurrencyClick != null) {
                // The currency is a quiet menu button in the field's header, so the amount stands alone below.
                val chipDescription = if (!currencyEnabled && currencyDisabledDescription != null) {
                    currencyDisabledDescription
                } else {
                    stringResource(R.string.currency_chip_description, currencyCode)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).padding(start = 4.dp)) { labelText() }
                    if (accountChip != null) {
                        accountChip()
                        Spacer(Modifier.width(8.dp))
                    }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onCurrencyClick,
                        enabled = currencyEnabled,
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp),
                        modifier = Modifier.semantics { contentDescription = chipDescription },
                    ) {
                        Text(currencyCode)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                }
            } else {
                labelText()
            }
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val symbolStyle = BoxTheme.moneyType.large.copy(color = color)
                val gapPx = with(density) { 4.dp.roundToPx() }
                // Room for the caret so the last digit is never clipped.
                val caretPx = with(density) { 6.dp.roundToPx() }
                val symbolPx = measurer.measure(symbol, symbolStyle).size.width
                val availablePx = (constraints.maxWidth - symbolPx - gapPx - caretPx).coerceAtLeast(0)
                val text = value.ifEmpty { "0.00" }
                // Shrink the numerals until the whole amount fits on one line.
                var fitted = style
                var textPx = measurer.measure(text, fitted, softWrap = false, maxLines = 1).size.width
                while (textPx > availablePx && fitted.fontSize.value > 20f) {
                    fitted = fitted.copy(fontSize = fitted.fontSize * 0.92f, lineHeight = fitted.lineHeight * 0.92f)
                    textPx = measurer.measure(text, fitted, softWrap = false, maxLines = 1).size.width
                }
                val fieldWidth = with(density) { (textPx + caretPx).toDp() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(symbol, style = symbolStyle)
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = fitted,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(fieldWidth).focusRequester(focus),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.isEmpty()) Text("0.00", style = fitted, maxLines = 1, softWrap = false)
                                inner()
                            }
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Category picker
// ---------------------------------------------------------------------------------------------

/** Horizontal row of category shape tokens; the chosen one morphs to a circle and is ringed. */
@Composable
fun CategoryPicker(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = categories.indexOfFirst { it.id == selectedCategoryId }
        if (i > 2) listState.scrollToItem(i - 1)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.transaction_category_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(end = 8.dp)) {
            item(key = "none") {
                CategoryToken(
                    category = null,
                    name = stringResource(R.string.transaction_category_none),
                    selected = selectedCategoryId == null,
                    onClick = { onCategorySelected(null) },
                )
            }
            items(categories, key = { it.id }) { category ->
                CategoryToken(
                    category = category,
                    name = category.name,
                    selected = selectedCategoryId == category.id,
                    onClick = { onCategorySelected(category.id) },
                )
            }
        }
    }
}

@Composable
private fun CategoryToken(category: Category?, name: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "ring",
    )
    Column(
        modifier = Modifier
            .width(76.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton, interactionSource = interaction, indication = null)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color.Transparent, border = BorderStroke(2.dp, ring)) {
            CategoryBadge(
                category = category,
                fallbackIcon = Icons.Outlined.Close,
                size = 52.dp,
                interactionSource = interaction,
                selected = selected,
                modifier = Modifier.padding(4.dp),
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Grouped option rows
// ---------------------------------------------------------------------------------------------

/** A titled column of connected rows; give each row [groupShape] for its position. */
@Composable
fun OptionGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        content()
    }
}

/** Corner for a row at [index] of [count] inside an [OptionGroup]. */
@Composable
private fun GroupRowSurface(
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = shape, modifier = modifier) {
        Box(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { content() }
    }
}

@Composable
private fun RowBody(icon: ImageVector?, label: String, supporting: String?, trailing: @Composable (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** Row with a switch; the whole row toggles. */
@Composable
fun SwitchRow(
    shape: androidx.compose.ui.graphics.Shape,
    icon: ImageVector?,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
) {
    GroupRowSurface(
        shape,
        Modifier.fillMaxWidth().toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
    ) { RowBody(icon, label, supporting) { Switch(checked = checked, onCheckedChange = null) } }
}

/** Row showing a value; tapping it runs [onClick]. */
@Composable
fun ValueRow(
    shape: androidx.compose.ui.graphics.Shape,
    icon: ImageVector?,
    label: String,
    value: String,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null,
) {
    GroupRowSurface(shape, Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        RowBody(icon, label, null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (trailingIcon != null) Icon(trailingIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Row whose value is chosen from a menu anchored to the row. */
@Composable
fun <T> MenuRow(
    shape: androidx.compose.ui.graphics.Shape,
    icon: ImageVector?,
    label: String,
    value: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ValueRow(shape, icon, label, value, onClick = { open = true }, trailingIcon = Icons.Outlined.UnfoldMore)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.extraLarge) {
            options.forEach { option ->
                val text = optionLabel(option)
                DropdownMenuItem(
                    text = { Text(text, color = if (text == value) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                    onClick = { onSelect(option); open = false },
                )
            }
        }
    }
}

/** Shape for row [index] of [count]. */
fun groupShape(index: Int, count: Int) = GroupShapes.item(index, count)

// ---------------------------------------------------------------------------------------------
// Date & time
// ---------------------------------------------------------------------------------------------

/** Holds the open/closed state of the date and time pickers for one [LocalDateTime]. */
class DateTimePickerState internal constructor() {
    var showDate by mutableStateOf(false)
    var showTime by mutableStateOf(false)
}

@Composable
fun rememberDateTimePickerState() = remember { DateTimePickerState() }

@Composable
fun DateRow(shape: androidx.compose.ui.graphics.Shape, label: String, dateTime: LocalDateTime, state: DateTimePickerState) =
    ValueRow(shape, Icons.Outlined.CalendarMonth, label, dateTime.format(DateFormats.mediumDate), onClick = { state.showDate = true })

@Composable
fun TimeRow(shape: androidx.compose.ui.graphics.Shape, dateTime: LocalDateTime, state: DateTimePickerState) =
    ValueRow(shape, Icons.Outlined.Schedule, stringResource(R.string.transaction_time_label), dateTime.format(DateFormats.shortTime), onClick = { state.showTime = true })

/** Material date pickers work in UTC midnight millis; these convert to and from a [LocalDate]. */
fun LocalDate.toPickerMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

fun Long.pickerMillisToLocalDate(): LocalDate = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toLocalDate()

/** Dates from [min] (when given) up to [max], inclusive. */
@OptIn(ExperimentalMaterial3Api::class)
fun selectableDates(max: LocalDate, min: LocalDate? = null): SelectableDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = utcTimeMillis.pickerMillisToLocalDate()
        return !date.isAfter(max) && (min == null || !date.isBefore(min))
    }
}

/** OK/Cancel date picker dialog; OK reports the picked day and then dismisses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDateDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    selectable: SelectableDates = DatePickerDefaults.AllDates,
) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis(), selectableDates = selectable)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(it.pickerMillisToLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = picker) }
}

/** The picker dialogs for a [DateTimePickerState]; place once per form. */
@Composable
fun DateTimePickerDialogs(
    state: DateTimePickerState,
    dateTime: LocalDateTime,
    onDateChange: (LocalDateTime) -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
) {
    if (state.showDate) {
        LocalDateDialog(
            initial = dateTime.toLocalDate(),
            onPick = { onDateChange(dateTime.with(it)) },
            onDismiss = { state.showDate = false },
        )
    }
    if (state.showTime) {
        val picker = rememberTimePickerState(initialHour = dateTime.hour, initialMinute = dateTime.minute)
        AlertDialog(
            onDismissRequest = { state.showTime = false },
            title = { Text(stringResource(R.string.transaction_select_time)) },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(picker.hour, picker.minute)
                    state.showTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { state.showTime = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
