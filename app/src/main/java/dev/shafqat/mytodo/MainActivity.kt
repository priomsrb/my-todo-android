package dev.shafqat.mytodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.shafqat.mytodo.ui.navigation.MyTodoApp
import dev.shafqat.mytodo.ui.theme.MyTodoTheme

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
}
