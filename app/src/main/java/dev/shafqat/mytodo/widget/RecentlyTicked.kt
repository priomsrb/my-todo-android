package dev.shafqat.mytodo.widget

import dev.shafqat.mytodo.model.TodoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The item last ticked off from a widget, held in view for a moment on a list that hides finished
 * items.
 *
 * Without it a tick on the wrong row made that row vanish on the spot, and putting it right meant
 * opening the app to find it. Held back, it stays where it was — struck through — so a second tap
 * unticks it right there. It is let go after [holdMillis], or as soon as something else is ticked:
 * running down a list ticking several things off should not leave a trail of them behind.
 *
 * Only one item is ever held, identified by its [dev.shafqat.mytodo.data.collapse.CollapseKeys] key
 * (which names its file too), since ids do not survive the reload a widget redraw brings. This is
 * view state and lives in memory alone: if the process dies the item simply hides, as it used to.
 *
 * A tick that took a whole subtree with it — a widget showing top-level items only — also leaves
 * the subtree as it was before, so unticking it while it is held puts each child back how it was
 * rather than leaving them all done or clearing them all. Once the hold ends, so does that undo.
 */
class RecentlyTicked(
    private val scope: CoroutineScope,
    private val holdMillis: Long = HOLD_MILLIS,
    /** Called once the held item is let go, so the widgets can redraw without it. */
    private val onReleased: suspend () -> Unit = {},
) {
    private val _key = MutableStateFlow<String?>(null)

    /** The key of the item being held in view, or null. */
    val key: StateFlow<String?> = _key.asStateFlow()

    private var releaseJob: Job? = null

    /** The held item's subtree as it was before its tick, when the tick took the subtree along. */
    private var before: TodoItem? = null

    /**
     * Records a tick from a widget: [done] holds the item, un-ticking lets it go. [before] is the
     * item as it stood before a tick that cascaded to its children, for [before] to hand back.
     */
    @Synchronized
    fun ticked(itemKey: String, done: Boolean, before: TodoItem? = null) {
        if (!done) {
            if (_key.value == itemKey) release()
            return
        }
        releaseJob?.cancel()
        _key.value = itemKey
        this.before = before
        releaseJob = scope.launch {
            delay(holdMillis)
            // Only if nothing has been ticked since; a later tick owns the hold now.
            if (releaseIfHeld(itemKey)) onReleased()
        }
    }

    /** What [itemKey]'s subtree looked like before its tick, if it is still held and had one. */
    @Synchronized
    fun before(itemKey: String): TodoItem? = if (_key.value == itemKey) before else null

    @Synchronized
    private fun releaseIfHeld(itemKey: String): Boolean {
        if (_key.value != itemKey) return false
        _key.value = null
        before = null
        return true
    }

    private fun release() {
        releaseJob?.cancel()
        releaseJob = null
        _key.value = null
        before = null
    }

    companion object {
        const val HOLD_MILLIS = 4_000L
    }
}
