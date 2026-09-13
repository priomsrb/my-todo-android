package dev.shafqat.mytodo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.glance.appwidget.updateAll
import dev.shafqat.mytodo.MainActivity
import dev.shafqat.mytodo.MyTodoApplication
import dev.shafqat.mytodo.data.MarkdownTodoRepository
import dev.shafqat.mytodo.data.StorageState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What a widget needs from the rest of the app: the repository, and a way back into the app.
 *
 * A widget update often *is* what started the process, so nothing here can assume the app has been
 * opened or that the folder setting has been read yet.
 */

/** Glance state key holding the list a [ListWidget] instance was configured to show. */
internal const val WIDGET_LIST_ID_KEY = "widget_list_id"

/** How long a widget waits for storage before giving up and saying so. */
private const val LOAD_TIMEOUT_MILLIS = 5_000L

/**
 * The app's one repository, once it has finished its first load — or null if it never does.
 *
 * The repository is shared with the app rather than rebuilt here on purpose: two readers of the
 * same files would sooner or later disagree about them, and ticking something off in the widget has
 * to be the same edit as ticking it off in the app.
 */
internal suspend fun Context.widgetRepository(): MarkdownTodoRepository? {
    val application = applicationContext as? MyTodoApplication ?: return null
    val repository = application.repository

    val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) {
        repository.storageState.first { it !is StorageState.Loading }
    }
    return if (loaded == null) null else repository
}

/**
 * An intent that opens [listId] in the app, or the home screen when it is null.
 *
 * The list id goes in the data URI as well as an extra: `Intent` equality ignores extras, so two
 * `PendingIntent`s that differed only by extra would be treated as the same one and every row in
 * the launcher widget would open whichever list was tapped first.
 */
internal fun openListIntent(context: Context, listId: String?): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = ("mytodo://list/" + Uri.encode(listId.orEmpty())).toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (listId != null) putExtra(MainActivity.EXTRA_LIST_ID, listId)
    }

/**
 * An intent that opens [listId] in the app with a fresh, empty item waiting at the top of it.
 *
 * The counterpart to the voice tile for the times dictation is the wrong tool — a noisy room, or an
 * item easier typed than said. It goes through the app rather than capturing in place because
 * typing needs a keyboard and a list to see it against, which is a screen, not an overlay.
 *
 * `mytodo://add/` rather than `mytodo://list/` so this and the header's plain "open the list" do
 * not collapse into one `PendingIntent`: `Intent` equality ignores extras, and the widget holds
 * both at once.
 */
internal fun newItemIntent(context: Context, listId: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = ("mytodo://add/" + Uri.encode(listId)).toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(MainActivity.EXTRA_LIST_ID, listId)
        putExtra(MainActivity.EXTRA_NEW_ITEM, true)
    }

/**
 * An intent that opens the voice capture overlay for [listId], with [listName] along for the ride.
 *
 * The name is passed rather than looked up because the overlay's whole job is to get the
 * microphone open fast: waiting for storage before it can label the prompt would put a pause
 * exactly where the user is already talking. The list id is in the data URI for the same reason
 * [openListIntent] puts it there — two voice tiles for different lists must not collapse into one
 * `PendingIntent`.
 */
internal fun voiceCaptureIntent(context: Context, listId: String, listName: String): Intent =
    Intent(context, VoiceCaptureActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = ("mytodo://speak/" + Uri.encode(listId)).toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(VoiceCaptureActivity.EXTRA_LIST_ID, listId)
        putExtra(VoiceCaptureActivity.EXTRA_LIST_NAME, listName)
    }

/** Redraws every widget. Called whenever the lists change, from either side. */
internal suspend fun Context.updateTodoWidgets() {
    ListWidget().updateAll(this)
    LauncherWidget().updateAll(this)
    VoiceWidget().updateAll(this)
}
