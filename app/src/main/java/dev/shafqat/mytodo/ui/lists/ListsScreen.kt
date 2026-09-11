package dev.shafqat.mytodo.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.doneCount
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.totalCount
import dev.shafqat.mytodo.ui.components.TextInputDialog
import dev.shafqat.mytodo.ui.theme.noteColors

/** Home screen: a Keep-style grid of note cards, one per TODO list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onListClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ListsViewModel = viewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    var showNewListDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lists_title)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewListDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_list)) },
            )
        },
    ) { innerPadding ->
        val tints = noteColors()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(lists, key = { it.id }) { list ->
                val tint = tints[lists.indexOfFirst { it.id == list.id }.coerceAtLeast(0) % tints.size]
                ListCard(
                    list = list,
                    tint = tint,
                    onClick = { onListClick(list.id) },
                )
            }
        }
    }

    if (showNewListDialog) {
        TextInputDialog(
            title = stringResource(R.string.new_list),
            confirmLabel = stringResource(R.string.new_list),
            onConfirm = { name ->
                viewModel.createList(name)
                showNewListDialog = false
            },
            onDismiss = { showNewListDialog = false },
        )
    }
}

@Composable
private fun ListCard(
    list: TodoList,
    tint: Color,
    onClick: () -> Unit,
) {
    // Only the first few rows preview on the card, as Keep does with long notes.
    val previewRows = list.items.flattenVisible().take(MAX_PREVIEW_ROWS)
    val hiddenCount = list.items.totalCount() - previewRows.size

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            text = list.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        previewRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (row.depth * 12).dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (row.item.done) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = row.item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.item.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (row.item.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (list.items.isEmpty()) {
            Text(
                text = "Empty list",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val done = list.items.doneCount()
            val total = list.items.totalCount()
            val footer = if (hiddenCount > 0) "+$hiddenCount more · $done/$total done" else "$done/$total done"
            Spacer(Modifier.height(4.dp))
            Text(
                text = footer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_PREVIEW_ROWS = 5
