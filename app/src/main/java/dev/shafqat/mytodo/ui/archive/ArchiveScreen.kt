package dev.shafqat.mytodo.ui.archive

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoItem
import dev.shafqat.mytodo.model.displayNameFor
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.ui.components.EmptyState
import dev.shafqat.mytodo.ui.theme.noteColors
import dev.shafqat.mytodo.ui.todo.IndentPerLevel

/**
 * What a list has had archived: read-only, with Restore and Delete forever on each top-level item.
 *
 * Only top-level items carry buttons, because an archived subtree comes and goes as one — the same
 * rule a move follows in the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    listId: String,
    onBack: () -> Unit,
    viewModel: ArchiveViewModel = viewModel(
        key = "archive-$listId",
        factory = archiveViewModelFactory(listId),
    ),
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    // What is awaiting confirmation to be deleted for good: one item, or null for all of them.
    var confirmDelete by remember { mutableStateOf<TodoItem?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val palette = noteColors()
    val tint = list?.prefs?.colorIndex
        ?.let { palette[it.coerceIn(palette.indices)] }
        ?: MaterialTheme.colorScheme.background

    // Restoring the last item empties the archive, and there is nothing left here to look at.
    var hadItems by remember { mutableStateOf(false) }
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty()) hadItems = true else if (hadItems) onBack()
    }

    Scaffold(
        containerColor = tint,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            R.string.archive_title,
                            list?.name ?: displayNameFor(listId),
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.restore_all)) },
                            leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                viewModel.restoreAll()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_all_forever)) },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                confirmDeleteAll = true
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tint),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tint)
                .padding(innerPadding),
        ) {
            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Inventory2,
                    title = stringResource(R.string.archive_empty_title),
                    subtitle = stringResource(R.string.archive_empty_subtitle),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                // Each row remembers the top-level item it belongs to: that is what the buttons act on.
                val rows = remember(items) {
                    items.flatMap { top -> listOf(top).flattenVisible().map { top to it } }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rows, key = { it.second.item.id }) { (top, row) ->
                        ArchivedRow(
                            item = row.item,
                            depth = row.depth,
                            isTop = row.item === top,
                            onRestore = { viewModel.restore(top.id) },
                            onDelete = { confirmDelete = top },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { item ->
        DeleteForeverDialog(
            message = stringResource(R.string.delete_forever_message),
            onConfirm = {
                viewModel.delete(item.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
    if (confirmDeleteAll) {
        DeleteForeverDialog(
            message = stringResource(R.string.delete_all_forever_message),
            onConfirm = {
                viewModel.deleteAll()
                confirmDeleteAll = false
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

@Composable
private fun ArchivedRow(
    item: TodoItem,
    depth: Int,
    isTop: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp + IndentPerLevel * depth, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.CheckBox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            textDecoration = TextDecoration.LineThrough,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        if (isTop) {
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.restore))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.delete_forever))
            }
        }
    }
}

@Composable
private fun DeleteForeverDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_forever_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_forever)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun archiveViewModelFactory(listId: String) = viewModelFactory {
    initializer {
        val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
        ArchiveViewModel(application, listId)
    }
}
