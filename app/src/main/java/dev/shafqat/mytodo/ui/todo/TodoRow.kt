package dev.shafqat.mytodo.ui.todo

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.totalCount
import kotlinx.coroutines.flow.first

/** Indent applied per nesting level. Depth is unbounded, so this only scales the padding. */
val IndentPerLevel = 24.dp

/** Width reserved for the chevron, so items without children still line up with their siblings. */
private val ChevronSize = 28.dp

/**
 * Padding above and below an item's text — and the same on its editor, so the swap between the
 * two moves nothing. It is also the gap between the top of the text slot and the text itself,
 * which matters when turning a touch into a caret position.
 */
private val TextVerticalPadding = 12.dp

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
    /** Enter with the caret anywhere past the start: finish this item and start the next one. */
    val onSplit: () -> Unit = {},
    /** Enter with the caret on the first character: start an item on the row above this one. */
    val onSplitAbove: () -> Unit = {},
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
 * Tapping the text turns it into a field in place, with the caret on the character that was
 * tapped. That editor is where fast entry lives: Enter starts the next item — or, with the caret
 * still before the first character, one above this one — and Tab and Shift-Tab re-nest it.
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
    /**
     * Reports the character offset the text was tapped at, or null when nothing was tapped.
     */
    onStartEdit: (caret: Int?) -> Unit = {},
    /** Where to put the caret when the editor opens; null means the end of the text. */
    initialCaret: Int? = null,
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
                initialCaret = initialCaret,
                modifier = Modifier.weight(1f),
            )
        } else {
            // How the text is laid out, and where the finger last went down on it, are what turn a
            // tap into a caret position. Both are forgotten when the edit begins and this branch
            // leaves the composition, so a later edit started some other way cannot inherit a
            // stale touch.
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            var touch by remember { mutableStateOf<Offset?>(null) }
            val textTop = with(LocalDensity.current) { TextVerticalPadding.toPx() }

            Text(
                text = item.text.ifBlank { stringResource(R.string.empty_item) },
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    item.text.isBlank() -> MaterialTheme.colorScheme.outline
                    item.done -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .weight(1f)
                    // Noting the touch and handling the tap are kept apart on purpose: this reads
                    // the press on the initial pass and consumes nothing, so the clickable below
                    // still owns the gesture — and with it the ripple and the accessibility click.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            touch = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            ).position
                        }
                    }
                    .clickable { onStartEdit(caretOffsetAt(layout, touch, textTop)) }
                    .padding(vertical = TextVerticalPadding),
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
 * The character of the row's text that a touch landed on, or null if that cannot be worked out —
 * a click with no touch behind it, from the keyboard or a screen reader.
 *
 * [touch] is in the coordinates of the whole text slot, whose top edge sits [textTop] pixels above
 * the text itself. Positions past the end of a line, which is most of the slot for a short item,
 * resolve to the end of that line, so tapping the empty space beside an item puts the caret after
 * its last character.
 */
private fun caretOffsetAt(layout: TextLayoutResult?, touch: Offset?, textTop: Float): Int? {
    if (layout == null || touch == null) return null
    return layout.getOffsetForPosition(touch - Offset(0f, textTop))
}

/**
 * The text field a row shows while it is being edited.
 *
 * Its own state is the source of truth for what is on screen; every keystroke is also reported
 * upward, where the repository's debounce turns a burst of typing into one write. Keys are handled
 * on the *preview* pass so Tab moves the item rather than the focus, and Enter starts another item
 * rather than inserting a newline — the row after this one, or the row above it when the caret has
 * nothing of this item in front of it.
 *
 * A separate composable so that its state — including where the caret sits — is created fresh when
 * an edit begins and thrown away when it ends.
 */
@Composable
private fun ItemEditor(
    item: TodoItem,
    callbacks: RowEditCallbacks,
    initialCaret: Int? = null,
    modifier: Modifier = Modifier,
) {
    // The caret starts on the character that was tapped, so a word in the middle of a long item can
    // be fixed without walking back to it. Edits that began without a tap — a new item from Enter,
    // a widget opening the app on one — have no position to honour and start at the end. A blank
    // item draws a placeholder, which is longer than the empty text the caret has to sit in.
    var value by remember {
        val caret = initialCaret?.coerceIn(0, item.text.length) ?: item.text.length
        mutableStateOf(TextFieldValue(item.text, TextRange(caret)))
    }
    val focusRequester = remember { FocusRequester() }
    val current by rememberUpdatedState(callbacks)
    // A field reports "not focused" once before it is given focus; without this that first report
    // would end the edit before it began — and end it by deleting a brand new, still-empty item.
    var hasBeenFocused by remember { mutableStateOf(false) }

    // Focus is asked for only once the window actually has it. A widget tap opens the app and this
    // editor in the same breath, and a focus request made while the window is still coming forward
    // is half-honoured: Compose gives the field the caret, but the keyboard that should come with
    // it never appears — and nothing asks again once the window settles. Waiting is a no-op for
    // every edit started from inside the app, where the window is focused already.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(Unit) {
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        focusRequester.requestFocus()
    }

    // Where the new item goes is read off the caret: nothing of this item is before it, so the row
    // the user is asking for is the one above rather than the one below. A selection is not a
    // caret at the start even when it begins there — it is a range the next keystroke replaces.
    fun onEnter() {
        val selection = value.selection
        if (selection.collapsed && selection.start == 0) current.onSplitAbove() else current.onSplit()
    }

    BasicTextField(
        value = value,
        onValueChange = { newValue ->
            // An item is one line of markdown, so a newline that arrives anyway — pasted in, or
            // typed on an IME that offers a return key now that the field wraps — becomes a space.
            // Swapping rather than dropping keeps the length, and with it the caret, where the
            // field thinks it is.
            val flattened = newValue.withoutNewlines()
            value = flattened
            current.onTextChange(flattened.text)
        },
        // The row's own style, not a merge onto whatever the surface provides: the two have to
        // lay out identically, and a style the field inherits brings its own line metrics with it.
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        // The field wraps exactly as the text it replaces does, so opening a long item for editing
        // leaves every line where it was. A single-line field would show that same item as one long
        // strip that scrolls sideways, and the row — and everything below it — would jump.
        singleLine = false,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onEnter() }),
        modifier = modifier
            .testTag(ItemEditorTag)
            .padding(vertical = TextVerticalPadding)
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
                        onEnter()
                        true
                    }
                    else -> false
                }
            },
    )
}

/** The same value with any newline in its text turned into a space. */
private fun TextFieldValue.withoutNewlines(): TextFieldValue =
    if ('\n' in text) copy(text = text.replace('\n', ' ')) else this
