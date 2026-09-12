package dev.shafqat.mytodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.ui.theme.noteColors

/**
 * Keep's colour row: one swatch per note tint, with the current one ticked.
 *
 * The first swatch is "no colour", which is not a colour of its own but a return to whatever the
 * list would look like untouched — stored as a null index rather than as palette entry zero, so a
 * later change to the default tint reaches lists that never chose one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = noteColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_color)) },
        text = {
            // Wraps rather than scrolls: a swatch pushed off the edge of a narrow dialog is a
            // colour the user never finds.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Swatch(
                    color = MaterialTheme.colorScheme.background,
                    selected = selectedIndex == null,
                    description = stringResource(R.string.color_default),
                    onClick = { onSelect(null) },
                )
                colors.drop(1).forEachIndexed { offset, color ->
                    val index = offset + 1
                    Swatch(
                        color = color,
                        selected = selectedIndex == index,
                        description = stringResource(R.string.color_n, index),
                        onClick = { onSelect(index) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
