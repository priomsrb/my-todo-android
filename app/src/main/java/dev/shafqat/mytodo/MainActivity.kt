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
import dev.shafqat.mytodo.ui.theme.MyTodoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * The list a widget asked for, until the NavHost has opened it.
     *
     * Held as state rather than read straight from the intent so that a second tap on the same
     * widget row re-opens the list: the request is cleared once it has been acted on, and setting
     * it again is what makes the next one a new request.
     */
    private var openListId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openListId = intent?.getStringExtra(EXTRA_LIST_ID)

        setContent {
            MyTodoTheme {
                MyTodoApp(
                    openListId = openListId,
                    onListOpened = { openListId = null },
                )
            }
        }
    }

    /** The activity is `singleTop`, so a widget tap while it is already open lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openListId = intent.getStringExtra(EXTRA_LIST_ID)
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
    }
}
