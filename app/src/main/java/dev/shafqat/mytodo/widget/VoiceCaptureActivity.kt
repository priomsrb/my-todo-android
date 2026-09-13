package dev.shafqat.mytodo.widget

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.splitDictation
import dev.shafqat.mytodo.todoApp
import dev.shafqat.mytodo.ui.theme.MyTodoTheme
import kotlinx.coroutines.launch

/**
 * Press, speak, done: the whole of what [VoiceWidget] does.
 *
 * There is no screen here in the usual sense. The activity is transparent and animation-free, so
 * what the user sees is the system's dictation sheet opening over their home screen and, a moment
 * later, a line confirming what was added — with an undo, because a recogniser that mishears should
 * cost one tap to correct rather than a trip into the app.
 *
 * The work is ordered so nothing waits on anything it does not have to: the microphone is asked for
 * before storage is, and the list's name arrives in the intent so the prompt can be labelled
 * immediately. Reading the folder only starts once there is a transcript to file.
 */
class VoiceCaptureActivity : ComponentActivity() {

    private val listId: String? get() = intent?.getStringExtra(EXTRA_LIST_ID)
    private val listName: String get() = intent?.getStringExtra(EXTRA_LIST_NAME).orEmpty()

    /** What to say once it is over, or null while the user is still speaking. */
    private var outcome by mutableStateOf<CaptureOutcome?>(null)

    private lateinit var dictation: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dictation = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            ::onDictated,
        )

        setContent {
            MyTodoTheme {
                CaptureOverlay(
                    outcome = outcome,
                    onUndo = ::undo,
                    onDismiss = ::finish,
                )
            }
        }

        // Only on a fresh start: after a rotation the dictation sheet is already up, and asking
        // again would stack a second one on top of it.
        if (savedInstanceState == null) startDictation()
    }

    private fun startDictation() {
        if (listId == null) {
            finish()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt, listName))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        // Asked for rather than looked up: package visibility hides most recognisers from a
        // resolveActivity() check on modern Android, so the honest test is whether it launches.
        try {
            dictation.launch(intent)
        } catch (_: ActivityNotFoundException) {
            outcome = CaptureOutcome.Failed(R.string.voice_no_recognizer)
        }
    }

    private fun onDictated(result: ActivityResult) {
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()

        // Backing out of the dictation sheet, or saying nothing, means "never mind" — leaving
        // without a word is friendlier than reporting the non-event.
        if (result.resultCode != RESULT_OK || spoken.isBlank()) {
            finish()
            return
        }

        // On the application's scope rather than this activity's: pressing home the moment you
        // stop speaking is a normal thing to do, and a capture dropped because its window went away
        // is the one failure this feature cannot afford. The window is only where the confirmation
        // is shown; the write does not belong to it.
        todoApp.applicationScope.launch { add(spoken) }
    }

    private suspend fun add(spoken: String) {
        // The application context, because this one may be gone before the write lands.
        val context = applicationContext
        val listId = listId
        val texts = splitDictation(spoken)
        if (listId == null || texts.isEmpty()) {
            dismiss()
            return
        }

        val repository = context.widgetRepository()
        if (repository == null) {
            outcome = CaptureOutcome.Failed(R.string.widget_unavailable)
            return
        }
        val list = repository.lists.value.firstOrNull { it.id == listId }
        if (list == null) {
            outcome = CaptureOutcome.Failed(R.string.widget_list_missing)
            return
        }

        val added = repository.addDictated(listId, texts)
        context.updateTodoWidgets()

        outcome = CaptureOutcome.Added(listName = list.name, texts = texts, itemIds = added)
    }

    private fun undo(added: CaptureOutcome.Added) {
        val context = applicationContext
        val listId = listId

        // Dismissed straight away — waiting for the file to be rewritten before the confirmation
        // goes would make undo feel slower than the add it is undoing.
        finish()

        todoApp.applicationScope.launch {
            if (listId == null) return@launch
            val repository = context.widgetRepository() ?: return@launch
            repository.removeDictated(listId, added.itemIds)
            context.updateTodoWidgets()
        }
    }

    /** Closing up from whichever thread the write happened to finish on. */
    private fun dismiss() = runOnUiThread { finish() }

    companion object {
        const val EXTRA_LIST_ID = "dev.shafqat.mytodo.extra.VOICE_LIST_ID"

        /** Carried so the dictation prompt can name the list without waiting for storage. */
        const val EXTRA_LIST_NAME = "dev.shafqat.mytodo.extra.VOICE_LIST_NAME"
    }
}

/** How a capture ended, once there is something to say about it. */
private sealed interface CaptureOutcome {

    data class Added(
        val listName: String,
        val texts: List<String>,
        val itemIds: List<String>,
    ) : CaptureOutcome

    data class Failed(@param:StringRes val messageRes: Int) : CaptureOutcome
}

/**
 * The confirmation, and nothing else.
 *
 * Deliberately empty until there is an outcome: for the seconds the user is speaking, this activity
 * is an invisible window over their home screen and should look like one. A tap anywhere dismisses
 * it, so the window never sits there swallowing presses meant for the launcher.
 */
@Composable
private fun CaptureOverlay(
    outcome: CaptureOutcome?,
    onUndo: (CaptureOutcome.Added) -> Unit,
    onDismiss: () -> Unit,
) {
    if (outcome == null) return

    val host = remember { SnackbarHostState() }
    val message = when (outcome) {
        is CaptureOutcome.Failed -> stringResource(outcome.messageRes)
        is CaptureOutcome.Added -> if (outcome.texts.size == 1) {
            stringResource(R.string.voice_added_one, outcome.texts.single())
        } else {
            stringResource(R.string.voice_added_many, outcome.texts.size, outcome.listName)
        }
    }
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(outcome) {
        val result = host.showSnackbar(
            message = message,
            actionLabel = if (outcome is CaptureOutcome.Added) undoLabel else null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed && outcome is CaptureOutcome.Added) {
            onUndo(outcome)
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        SnackbarHost(
            hostState = host,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp),
        )
    }
}
