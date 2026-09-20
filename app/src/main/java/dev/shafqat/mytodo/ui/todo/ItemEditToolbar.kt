package dev.shafqat.mytodo.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shafqat.mytodo.R

/** Test tag on the toolbar shown while an item is being edited. */
const val EditToolbarTag = "edit-toolbar"

/** Test tags on its buttons, so a test can press one without depending on its icon. */
const val OutdentButtonTag = "toolbar-outdent"
const val IndentButtonTag = "toolbar-indent"

/** Height of the bar itself, not counting the keyboard or navigation bar it sits above. */
private val ToolbarHeight = 48.dp

/** The icon drawn in the middle of each button; the button's own tap target is Material's. */
private val IconSize = 22.dp

/**
 * The bar of actions on the item currently being edited, pinned above the keyboard.
 *
 * It holds what the keyboard cannot offer: Tab and Shift-Tab re-nest an item on a hardware
 * keyboard, and this is the same two moves for a thumb. More buttons are expected here, so the
 * row is laid out from the start rather than fitted around exactly two.
 *
 * A button whose move is impossible — outdenting a top-level item, indenting one with no sibling
 * above it — is drawn greyed out rather than removed, so the buttons never shift under the thumb
 * as the edit moves from row to row.
 *
 * Pressing a button must leave the edit running: the editor treats a lost focus as the end of the
 * edit, and an edit that ends drops the keyboard and throws away an item still blank. Plain
 * `IconButton`s were measured against that on a device, keyboard attached and all, and they keep
 * it — so nothing here is hand-rolled to avoid focus. What did end edits a second after a press
 * was a reload minting new ids; that is fixed where it belongs, in the repository.
 */
@Composable
fun ItemEditToolbar(
    canIndent: Boolean,
    canOutdent: Boolean,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag(EditToolbarTag)
            .fillMaxWidth()
            // Ahead of the inset padding the caller applies, so the bar's own colour carries on
            // down behind the navigation bar instead of leaving a stripe of the list showing.
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ToolbarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left to right in the direction the moves go, so the arrows read as what they do.
            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.FormatIndentDecrease,
                description = stringResource(R.string.outdent_item),
                tag = OutdentButtonTag,
                enabled = canOutdent,
                onClick = onOutdent,
            )
            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.FormatIndentIncrease,
                description = stringResource(R.string.indent_item),
                tag = IndentButtonTag,
                enabled = canIndent,
                onClick = onIndent,
            )
        }
    }
}

/** One icon-only button on the bar. */
@Composable
private fun ToolbarButton(
    icon: ImageVector,
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.testTag(tag),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(IconSize))
    }
}
