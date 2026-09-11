package dev.shafqat.mytodo.ui.todo

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.CreationExtras
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.flattenVisible
import dev.shafqat.mytodo.model.moveSubtree
import dev.shafqat.mytodo.model.updateItem
import dev.shafqat.mytodo.ui.components.TextInputDialog

/** One list of TODOs, rendered as a flat lazy column of recursively-indented rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    listId: String,
    onBack: () -> Unit,
    viewModel: TodoListViewModel = viewModel(
        key = "todo-list-$listId",
        factory = todoListViewModelFactory(listId),
    ),
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val dragState = rememberTodoDragState(listState)
    var showAddItemDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(list?.name ?: stringResource(R.string.untitled_list)) },
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
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.collapse_all)) },
                            leadingIcon = { Icon(Icons.Default.UnfoldLess, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                viewModel.setAllCollapsed(true)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.expand_all)) },
                            leadingIcon = { Icon(Icons.Default.UnfoldMore, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                viewModel.setAllCollapsed(false)
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddItemDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_item)) },
            )
        },
    ) { innerPadding ->
        val items = list?.items.orEmpty()
        val draggedId = dragState.draggedItemId
        val previewItems = if (draggedId == null) {
            items
        } else {
            // Collapsing the dragged item keeps its descendants out of the way while it travels,
            // and moveSubtree shows it exactly where it would land, indentation included.
            items.updateItem(draggedId) { it.copy(collapsed = true) }
                .moveSubtree(draggedId, dragState.targetIndex, dragState.targetDepth)
        }
        val rows = previewItems.flattenVisible()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            if (rows.isEmpty()) {
                Text(
                    text = "Nothing here yet — tap “Add item”.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.item.id }) { index, row ->
                        // pointerInput is keyed on the item id alone, so that reordering the list
                        // never cancels an in-flight drag. That also means its gesture block is not
                        // recreated when the row moves, so it must not capture the index and depth
                        // directly — it would keep whichever values the row had when it was first
                        // composed, and picking the row up later would fling it back there.
                        val currentIndex by rememberUpdatedState(index)
                        val currentDepth by rememberUpdatedState(row.depth)

                        TodoRow(
                            item = row.item,
                            depth = row.depth,
                            isDragging = row.item.id == draggedId,
                            dragHandleModifier = Modifier.pointerInput(row.item.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragState.onDragStart(row.item.id, currentIndex, currentDepth)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragState.onDrag(amount.x, amount.y)
                                    },
                                    onDragEnd = {
                                        dragState.onDragEnd(viewModel::moveItem)
                                    },
                                    onDragCancel = { dragState.onDragCancel() },
                                )
                            },
                            onToggleDone = { done -> viewModel.setDone(row.item.id, done) },
                            onToggleCollapsed = { collapsed ->
                                viewModel.setCollapsed(row.item.id, collapsed)
                            },
                            onDelete = { viewModel.deleteItem(row.item.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAddItemDialog) {
        TextInputDialog(
            title = stringResource(R.string.new_item),
            confirmLabel = "Add",
            onConfirm = { text ->
                viewModel.addItem(text)
                showAddItemDialog = false
            },
            onDismiss = { showAddItemDialog = false },
        )
    }
}

/** Supplies the list id to the ViewModel, which otherwise has no way to know which list it shows. */
private fun todoListViewModelFactory(listId: String) = viewModelFactory {
    initializer {
        val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
        TodoListViewModel(application, listId)
    }
}
