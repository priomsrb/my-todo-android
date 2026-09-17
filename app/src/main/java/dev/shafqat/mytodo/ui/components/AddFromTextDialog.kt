package dev.shafqat.mytodo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.itemsFromText

/**
 * Pasting a whole list in at once.
 *
 * The field is multi-line and the text is parsed as it is typed, so the count under it says how
 * many items the paste will actually become before anything is added — which is the only way to
 * see, without adding first, that a stray indent has nested half the list under its first line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFromTextDialog(
    initialAtTop: Boolean,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    // Held here rather than read back from the setting, so the toggle answers the finger at once
    // instead of on the next emission from storage. Confirming is what makes the choice the
    // remembered one — a paste that is cancelled should not change where the next one lands.
    var atTop by remember { mutableStateOf(initialAtTop) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Parsing is cheap and pure, and the dialog holds a paste rather than a novel.
    val itemCount = remember(text) { itemsFromText(text).rowCount() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_from_text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.add_from_text_hint)) },
                    supportingText = {
                        Text(
                            if (itemCount == 0) {
                                stringResource(R.string.add_from_text_formats)
                            } else {
                                pluralStringResource(R.plurals.add_from_text_count, itemCount, itemCount)
                            },
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .focusRequester(focusRequester),
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !atTop,
                        onClick = { atTop = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.add_at_end)) }
                    SegmentedButton(
                        selected = atTop,
                        onClick = { atTop = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.add_at_top)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text, atTop) },
                enabled = itemCount > 0,
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Every item the paste names, nested ones included — what the count under the field promises. */
private fun List<TodoItem>.rowCount(): Int =
    sumOf { 1 + it.children.rowCount() }
