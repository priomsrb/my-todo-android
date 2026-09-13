package dev.shafqat.mytodo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.shafqat.mytodo.ui.navigation.MyTodoApp
import dev.shafqat.mytodo.ui.navigation.OpenListRequest
import dev.shafqat.mytodo.ui.theme.MyTodoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * What a widget asked for, until the NavHost has acted on it.
     *
     * Held as state rather than read straight from the intent so that a second tap on the same
     * widget row re-opens the list: the request is cleared once it has been acted on, and setting
     * it again is what makes the next one a new request. It is one nullable object rather than a
     * field per thing asked for, so clearing it cannot leave half a request behind.
     */
    private var openRequest by mutableStateOf<OpenListRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start. After a rotation the intent is still the widget's, and reading it
        // again would ask for a second empty item; the NavHost restores which list was open anyway.
        if (savedInstanceState == null) openRequest = intent?.openListRequest()

        setContent {
            MyTodoTheme {
                MyTodoApp(
                    openRequest = openRequest,
                    onRequestHandled = { openRequest = null },
                )
            }
        }
    }

    /** The activity is `singleTop`, so a widget tap while it is already open lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest = intent.openListRequest()
    }

    private fun Intent.openListRequest(): OpenListRequest? {
        val listId = getStringExtra(EXTRA_LIST_ID) ?: return null
        return OpenListRequest(listId, startNewItem = getBooleanExtra(EXTRA_NEW_ITEM, false))
    }

    /** Files may have been edited elsewhere while the app was away, so re-read them. */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { todoApp.repository.refresh() }
    }

    /** Don't rely on the autosave timer surviving the app going to the background. */
    override fun onStop() {
        super.onStop()
        todoApp.applicationScope.launch { todoApp.repository.flushPendingSaves() }
    }

    companion object {
        /** Set by the home-screen widgets to open one list straight away. */
        const val EXTRA_LIST_ID = "dev.shafqat.mytodo.extra.LIST_ID"

        /** Set by the list widget's "+" to land in that list with an empty item ready to type in. */
        const val EXTRA_NEW_ITEM = "dev.shafqat.mytodo.extra.NEW_ITEM"
    }
}
