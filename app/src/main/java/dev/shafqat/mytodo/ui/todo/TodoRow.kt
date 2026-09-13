package dev.shafqat.mytodo.ui.todo

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.totalCount

/** Indent applied per nesting level. Depth is unbounded, so this only scales the padding. */
val IndentPerLevel = 24.dp

/** Width reserved for the chevron, so items without children still line up with their siblings. */
private val ChevronSize = 28.dp

/** Test tag on the text field a row shows while it is being edited. */
const val ItemEditorTag = "item-editor"

/**
 * What a row being edited can do beyond changing its text.
 *
 * Bundled into one object because every one of these is wired the same way — row to list to
 * ViewModel — and a row that is not being edited needs none of them.
 */
data class RowEditCallbacks(
    val onTextChange: (String) -> Unit = {},
    /** Enter: finish this item and start the next one. */
    val onSplit: () -> Unit = {},
    /** Tab. */
    val onIndent: () -> Unit = {},
    /** Shift-Tab. */
    val onOutdent: () -> Unit = {},
    /** The field lost focus or the editor was dismissed. */
    val onDone: () -> Unit = {},
)

/**
 * One TODO row: drag handle, expand chevron, checkbox, text, delete.
 *
 * Ticked items render grayed out and struck through. A collapsed parent shows how many descendants
 * are hidden underneath it. The drag handle starts a reorder the moment the finger moves on it, no
 * hold needed; while a row is being dragged it lifts with a shadow and its indent animates to the
 * depth it would land at. [isDragging] stays on for the moment after the drop while the row slides
 * back into its slot, so the lift fades out with the movement rather than before it.
 *
 * The handle is only drawn when reordering is actually available.
 *
 * Tapping the text turns it into a field in place. That editor is where fast entry lives: Enter
 * starts the next item, Tab and Shift-Tab re-nest this one.
 */
@Composable
fun TodoRow(
    item: TodoItem,
    depth: Int,
    onToggleDone: (Boolean) -> Unit,
    onToggleCollapsed: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    isEditing: Boolean = false,
    onStartEdit: () -> Unit = {},
    editCallbacks: RowEditCallbacks = RowEditCallbacks(),
    showDragHandle: Boolean = true,
    dragHandleModifier: Modifier = Modifier,
) {
    val hasChildren = item.children.isNotEmpty()
    // The indent animates so an indent/outdent during a drag reads as movement, not a jump.
    val indent by animateDpAsState(targetValue = IndentPerLevel * depth, label = "indent")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (isDragging) 6.dp else 0.dp, RoundedCornerShape(8.dp))
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .padding(start = indent, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDragHandle) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = stringResource(R.string.drag_handle),
                tint = if (isDragging) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = dragHandleModifier.size(20.dp),
            )
        } else {
            // Reordering is off, so the handle would be a control that does nothing. The space it
            // held stays, to keep the rows lined up with how they look the rest of the time.
            Spacer(Modifier.width(20.dp))
        }

        if (hasChildren) {
            // Pointing down when expanded, right when collapsed.
            val rotation by animateFloatAsState(
                targetValue = if (item.collapsed) -90f else 0f,
                label = "chevron",
            )
            IconButton(
                onClick = { onToggleCollapsed(!item.collapsed) },
                modifier = Modifier.size(ChevronSize),
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (item.collapsed) R.string.expand else R.string.collapse,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                )
            }
        } else {
            Spacer(Modifier.width(ChevronSize))
        }

        Checkbox(
            checked = item.done,
            onCheckedChange = onToggleDone,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        if (isEditing) {
            ItemEditor(
                item = item,
                callbacks = editCallbacks,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = item.text.ifBlank { stringResource(R.string.empty_item) },
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    item.text.isBlank() -> MaterialTheme.colorScheme.outline
                    item.done -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onStartEdit)
                    .padding(vertical = 12.dp),
            )
        }

        if (item.collapsed && hasChildren) {
            Text(
                text = item.children.totalCount().toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The text field a row shows while it is being edited.
 *
 * Its own state is the source of truth for what is on screen; every keystroke is also reported
 * upward, where the repository's debounce turns a burst of typing into one write. Keys are handled
 * on the *preview* pass so Tab moves the item rather than the focus, and Enter starts the next item
 * rather than inserting a newline.
 *
 * A separate composable so that its state — including where the caret sits — is created fresh when
 * an edit begins and thrown away when it ends.
 */
@Composable
private fun ItemEditor(
    item: TodoItem,
    callbacks: RowEditCallbacks,
    modifier: Modifier = Modifier,
) {
    // The caret starts at the end of the existing text, as it does when editing a note title.
    var value by remember {
        mutableStateOf(TextFieldValue(item.text, TextRange(item.text.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val current by rememberUpdatedState(callbacks)
    // A field reports "not focused" once before it is given focus; without this that first report
    // would end the edit before it began — and end it by deleting a brand new, still-empty item.
    var hasBeenFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = value,
        onValueChange = { newValue ->
            value = newValue
            current.onTextChange(newValue.text)
        },
        textStyle = LocalTextStyle.current.merge(
            MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { current.onSplit() }),
        modifier = modifier
            .testTag(ItemEditorTag)
            .padding(vertical = 12.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) hasBeenFocused = true else if (hasBeenFocused) current.onDone()
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.Tab && event.isShiftPressed -> {
                        current.onOutdent()
                        true
                    }
                    event.key == Key.Tab -> {
                        current.onIndent()
                        true
                    }
                    event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                        current.onSplit()
                        true
                    }
                    else -> false
                }
            },
    )
}
