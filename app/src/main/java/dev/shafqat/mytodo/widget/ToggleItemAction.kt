package dev.shafqat.mytodo.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import dev.shafqat.mytodo.data.collapse.CollapseKeys
import dev.shafqat.mytodo.model.findItem
import dev.shafqat.mytodo.todoApp

/**
 * Ticking an item off from the widget, written straight through to the markdown file.
 *
 * The row hands over a [CollapseKeys] key rather than an item id, because the ids the widget was
 * drawn with may already have been replaced by a reload. Resolving the key here, against the tree
 * as it is now, means a tap either hits the item the user actually pointed at or does nothing.
 *
 * A row drawn with its subtree folded away (the widget showing top-level items only) stands for
 * the whole subtree, so ticking it ticks everything beneath it too. Unticking it while the tick is
 * still held by [RecentlyTicked] is an undo, and puts every child back as it was; after that it
 * unticks the row alone, leaving the children as they are.
 */
class ToggleItemAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val listId = parameters[ListIdParameter] ?: return
        val itemKey = parameters[ItemKeyParameter] ?: return
        // Glance reports the box's new state; the value drawn into the row is the fallback for
        // hosts that do not.
        val done = parameters[ToggleableStateKey] ?: parameters[TargetDoneParameter] ?: return

        val repository = context.widgetRepository() ?: return
        val list = repository.lists.value.firstOrNull { it.id == listId } ?: return
        val itemId = CollapseKeys.idOf(list.items, list.fileName, itemKey) ?: return
        val cascade = parameters[CascadeParameter] ?: false
        val recentlyTicked = context.todoApp.recentlyTicked

        if (!cascade) {
            // Before the write, so the redraw it causes already keeps a just-ticked item in view.
            recentlyTicked.ticked(itemKey, done)
            repository.setItemDone(listId, itemId, done)
        } else if (done) {
            val before = list.items.findItem(itemId) ?: return
            recentlyTicked.ticked(itemKey, done = true, before = before)
            repository.setSubtreeDone(listId, itemId, done = true)
        } else {
            // Read before the untick lets the hold, and the snapshot with it, go.
            val before = recentlyTicked.before(itemKey)
            recentlyTicked.ticked(itemKey, done = false)
            if (before != null) {
                repository.restoreSubtreeDone(listId, itemId, before)
            } else {
                repository.setItemDone(listId, itemId, done = false)
            }
        }
        // Edits normally settle on a debounce, but nothing keeps this process alive once the tap is
        // handled, so the write has to happen now.
        repository.flushPendingSaves()

        context.updateTodoWidgets()
    }

    companion object {
        val ListIdParameter = ActionParameters.Key<String>("listId")
        val ItemKeyParameter = ActionParameters.Key<String>("itemKey")
        val TargetDoneParameter = ActionParameters.Key<Boolean>("targetDone")
        val CascadeParameter = ActionParameters.Key<Boolean>("cascade")

        fun parameters(
            listId: String,
            itemKey: String,
            targetDone: Boolean,
            cascade: Boolean = false,
        ) = actionParametersOf(
            ListIdParameter to listId,
            ItemKeyParameter to itemKey,
            TargetDoneParameter to targetDone,
            CascadeParameter to cascade,
        )
    }
}
