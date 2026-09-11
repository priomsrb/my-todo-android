package dev.shafqat.mytodo.ui.todo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.totalCount

/** Indent applied per nesting level. Depth is unbounded, so this only scales the padding. */
val IndentPerLevel = 24.dp

/** Width reserved for the chevron, so items without children still line up with their siblings. */
private val ChevronSize = 28.dp

/**
 * One TODO row: drag handle, expand chevron, checkbox, text, delete.
 *
 * Ticked items render grayed out and struck through. A collapsed parent shows how many descendants
 * are hidden underneath it. The drag handle is inert until Phase 3.
 */
@Composable
fun TodoRow(
    item: TodoItem,
    depth: Int,
    onToggleDone: (Boolean) -> Unit,
    onToggleCollapsed: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasChildren = item.children.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = IndentPerLevel * depth, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )

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

        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.done) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )

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
