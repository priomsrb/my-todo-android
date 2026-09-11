package dev.shafqat.mytodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.shafqat.mytodo.ui.navigation.MyTodoApp
import dev.shafqat.mytodo.ui.theme.MyTodoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MyTodoTheme {
                MyTodoApp()
            }
        }
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
}
