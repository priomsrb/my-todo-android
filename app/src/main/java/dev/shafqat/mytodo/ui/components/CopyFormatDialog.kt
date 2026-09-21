package dev.shafqat.mytodo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.CopyFormat

/**
 * Which of the two formats "Copy list" writes.
 *
 * Each option carries a line of its own syntax rather than just a name: "checkboxes" and "plain
 * bullets" only mean something once you have seen what lands in the message you paste into.
 */
@Composable
fun CopyFormatDialog(
    selected: CopyFormat,
    onSelect: (CopyFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.copy_format)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormatOption(
                    title = stringResource(R.string.copy_format_checkboxes),
                    subtitle = stringResource(R.string.copy_format_checkboxes_summary),
                    selected = selected == CopyFormat.Checkboxes,
                    onSelect = { onSelect(CopyFormat.Checkboxes) },
                )
                FormatOption(
                    title = stringResource(R.string.copy_format_bullets),
                    subtitle = stringResource(R.string.copy_format_bullets_summary),
                    selected = selected == CopyFormat.Bullets,
                    onSelect = { onSelect(CopyFormat.Bullets) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}

@Composable
private fun FormatOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is the target, and it is the row rather than the button that carries
            // the selection for accessibility — a radio button alone is a small thing to hit.
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
