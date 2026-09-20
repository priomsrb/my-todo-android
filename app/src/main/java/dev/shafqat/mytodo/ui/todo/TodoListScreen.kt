package dev.shafqat.mytodo.ui.todo

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.ListPrefs
import dev.shafqat.mytodo.model.hasCompleted
import dev.shafqat.mytodo.ui.components.AddFromTextDialog
import dev.shafqat.mytodo.ui.components.ColorPickerDialog
import dev.shafqat.mytodo.ui.components.EmptyState
import dev.shafqat.mytodo.ui.components.TextInputDialog
import dev.shafqat.mytodo.ui.theme.noteColors

/** One list of TODOs, rendered as a flat lazy column of recursively-indented rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    listId: String,
    onBack: () -> Unit,
    onRenamed: (String) -> Unit,
    startNewItem: Boolean = false,
    onNewItemStarted: () -> Unit = {},
    viewModel: TodoListViewModel = viewModel(
        key = "todo-list-$listId",
        factory = todoListViewModelFactory(listId),
    ),
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val focusItemId by viewModel.focusItemId.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val pendingAddUndo by viewModel.pendingAddUndo.collectAsStateWithLifecycle()
    val addFromTextAtTop by viewModel.addFromTextAtTop.collectAsStateWithLifecycle()
    val renamedListId by viewModel.renamedListId.collectAsStateWithLifecycle()
    val swipeToDeleteEnabled by viewModel.swipeToDeleteEnabled.collectAsStateWithLifecycle()

    // Which row the list has open for typing, mirrored up here only so the "Add item" button can
    // step out of the toolbar's way. The list remains the one that decides.
    var editingItemId by remember { mutableStateOf<String?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddFromTextDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs = list?.prefs ?: ListPrefs.Default
    val palette = noteColors()
    // A list with no colour of its own keeps the plain app background rather than palette entry
    // zero, so "no colour" still means "looks untouched" if the palette ever changes.
    val targetTint = prefs.colorIndex
        ?.let { palette[it.coerceIn(palette.indices)] }
        ?: MaterialTheme.colorScheme.background
    val tint by animateColorAsState(targetTint, label = "list-tint")

    // Renaming a list renames its file, and the file name is the id this screen was opened with,
    // so the screen has to follow the list to its new route.
    LaunchedEffect(renamedListId) { renamedListId?.let(onRenamed) }

    // The widget's "+" asked for a row to type in. Reported back straight away rather than when the
    // item appears: the request is answered by having started it, and leaving it outstanding would
    // add a second empty row the next time this screen composed.
    LaunchedEffect(startNewItem) {
        if (startNewItem) {
            viewModel.addItemAtTop()
            onNewItemStarted()
        }
    }

    val deletedMessage = stringResource(R.string.item_deleted)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(pendingUndo) {
        val deleted = pendingUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        } else if (viewModel.pendingUndo.value === deleted) {
            viewModel.dismissUndo()
        }
    }

    // Resolved while composing, because a plural string is a composable read and the effect below
    // is not; null whenever there is nothing to announce.
    val addedMessage = pendingAddUndo?.let {
        pluralStringResource(R.plurals.items_added, it.rowCount, it.rowCount)
    }
    LaunchedEffect(pendingAddUndo) {
        val added = pendingAddUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = addedMessage ?: return@LaunchedEffect,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoAdd()
        } else if (viewModel.pendingAddUndo.value === added) {
            viewModel.dismissAddUndo()
        }
    }

    Scaffold(
        containerColor = tint,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    ListMenu(
                        expanded = menuExpanded,
                        hideCompleted = prefs.hideCompleted,
                        hasCompleted = list?.items?.hasCompleted() == true,
                        onDismiss = { menuExpanded = false },
                        onRename = { showRenameDialog = true },
                        onAddFromText = { showAddFromTextDialog = true },
                        onPickColor = { showColorPicker = true },
                        onToggleHideCompleted = { viewModel.setHideCompleted(!prefs.hideCompleted) },
                        onMoveCompletedToBottom = viewModel::moveCompletedToBottom,
                        onSetAllCollapsed = viewModel::setAllCollapsed,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tint),
            )
        },
        floatingActionButton = {
            // Gone while an item is being edited: it would sit on top of the edit toolbar, and
            // Enter already starts the next item, which is what it would have been reached for.
            AnimatedVisibility(
                visible = editingItemId == null,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::addItem,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_item)) },
                )
            }
        },
    ) { innerPadding ->
        val allItems = list?.items.orEmpty()
        val visibleItems = list?.visibleItems.orEmpty()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tint)
                .padding(innerPadding)
                // The padding above is the system bars'; saying so is what lets the edit toolbar
                // inside ask for the keyboard inset without counting the navigation bar twice.
                .consumeWindowInsets(innerPadding),
        ) {
            when {
                allItems.isEmpty() -> EmptyState(
                    icon = Icons.Default.Checklist,
                    title = stringResource(R.string.list_empty_title),
                    subtitle = stringResource(R.string.list_empty_subtitle),
                    modifier = Modifier.align(Alignment.Center),
                )

                visibleItems.isEmpty() -> EmptyState(
                    icon = Icons.Default.CheckCircle,
                    title = stringResource(R.string.all_done_title),
                    subtitle = stringResource(R.string.all_done_subtitle),
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> TodoItemList(
                    items = visibleItems,
                    // Row indices are the drag's coordinate system, and a filtered list does not
                    // have the same ones. Hiding finished items therefore parks reordering until
                    // the whole list is on screen again.
                    dragEnabled = !prefs.hideCompleted,
                    swipeToDeleteEnabled = swipeToDeleteEnabled,
                    focusItemId = focusItemId,
                    onToggleDone = viewModel::setDone,
                    onToggleCollapsed = viewModel::setCollapsed,
                    onDelete = viewModel::deleteItem,
                    onMove = viewModel::moveItem,
                    editActions = ItemEditActions(
                        onTextChange = viewModel::setText,
                        onSplit = viewModel::addItemAfter,
                        onIndent = viewModel::indent,
                        onOutdent = viewModel::outdent,
                        onEditFinished = viewModel::finishEditing,
                    ),
                    onEditingChanged = { editingItemId = it },
                )
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            selectedIndex = prefs.colorIndex,
            onSelect = viewModel::setColor,
            onDismiss = { showColorPicker = false },
        )
    }

    if (showAddFromTextDialog) {
        AddFromTextDialog(
            initialAtTop = addFromTextAtTop,
            onConfirm = { text, atTop ->
                viewModel.addFromText(text, atTop)
                showAddFromTextDialog = false
            },
            onDismiss = { showAddFromTextDialog = false },
        )
    }

    if (showRenameDialog) {
        TextInputDialog(
            title = stringResource(R.string.rename_list),
            confirmLabel = stringResource(R.string.rename),
            initialValue = list?.name.orEmpty(),
            onConfirm = { name ->
                viewModel.rename(name)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }
}

/** The list screen's overflow menu, split out to keep the top bar readable. */
@Composable
private fun ListMenu(
    expanded: Boolean,
    hideCompleted: Boolean,
    hasCompleted: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onAddFromText: () -> Unit,
    onPickColor: () -> Unit,
    onToggleHideCompleted: () -> Unit,
    onMoveCompletedToBottom: () -> Unit,
    onSetAllCollapsed: (Boolean) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuRow(stringResource(R.string.rename_list), Icons.Default.DriveFileRenameOutline) {
            onDismiss()
            onRename()
        }
        MenuRow(stringResource(R.string.add_from_text), Icons.Default.PlaylistAdd) {
            onDismiss()
            onAddFromText()
        }
        MenuRow(stringResource(R.string.list_color), Icons.Default.Palette) {
            onDismiss()
            onPickColor()
        }
        if (hasCompleted) {
            MenuRow(
                text = stringResource(
                    if (hideCompleted) R.string.show_completed else R.string.hide_completed,
                ),
                icon = if (hideCompleted) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            ) {
                onDismiss()
                onToggleHideCompleted()
            }
            MenuRow(
                stringResource(R.string.move_completed_to_bottom),
                Icons.Default.VerticalAlignBottom,
            ) {
                onDismiss()
                onMoveCompletedToBottom()
            }
        }
        MenuRow(stringResource(R.string.collapse_all), Icons.Default.UnfoldLess) {
            onDismiss()
            onSetAllCollapsed(true)
        }
        MenuRow(stringResource(R.string.expand_all), Icons.Default.UnfoldMore) {
            onDismiss()
            onSetAllCollapsed(false)
        }
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** Supplies the list id to the ViewModel, which otherwise has no way to know which list it shows. */
private fun todoListViewModelFactory(listId: String) = viewModelFactory {
    initializer {
        val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
        TodoListViewModel(application, listId)
    }
}
