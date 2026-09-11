package dev.shafqat.mytodo.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.shafqat.mytodo.ui.lists.ListsScreen
import dev.shafqat.mytodo.ui.settings.SettingsScreen
import dev.shafqat.mytodo.ui.todo.TodoListScreen

/** How long a screen takes to fade in or out. Short, so screens settle quickly under a fast tap. */
private const val ScreenTransitionMillis = 180

object Routes {
    const val LISTS = "lists"
    const val SETTINGS = "settings"
    const val LIST_DETAIL = "list/{listId}"

    // List ids are filenames, so they must be encoded before going into a route.
    fun listDetail(listId: String) = "list/" + Uri.encode(listId)
}

@Composable
fun MyTodoApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LISTS,
        enterTransition = { fadeIn(tween(ScreenTransitionMillis)) },
        exitTransition = { fadeOut(tween(ScreenTransitionMillis)) },
        popEnterTransition = { fadeIn(tween(ScreenTransitionMillis)) },
        popExitTransition = { fadeOut(tween(ScreenTransitionMillis)) },
    ) {
        composable(Routes.LISTS) { entry ->
            Screen(entry) {
                ListsScreen(
                    onListClick = { listId -> navController.navigate(Routes.listDetail(listId)) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                )
            }
        }
        composable(
            route = Routes.LIST_DETAIL,
            arguments = listOf(navArgument("listId") { type = NavType.StringType }),
        ) { entry ->
            Screen(entry) {
                TodoListScreen(
                    listId = requireNotNull(entry.arguments?.getString("listId")),
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Routes.SETTINGS) { entry ->
            Screen(entry) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Hosts one destination, ignoring touches until it has settled.
 *
 * While a screen fades in or out, both destinations are composed and on screen, and the one on its
 * way out still accepts touches. Tapping a list right after backing out of one would land on the
 * old screen instead — ticking whatever item happened to sit under the finger, and not opening the
 * list that was tapped.
 */
@Composable
private fun Screen(entry: NavBackStackEntry, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .blockInputUntilSettled(entry),
    ) {
        content()
    }
}

/**
 * Swallows pointer events while [entry] is mid-transition.
 *
 * Navigation resumes an entry only once its animation has finished, so "not resumed" is exactly
 * "still animating". Events are consumed on the initial pass, before any child gets to see them.
 */
@Composable
private fun Modifier.blockInputUntilSettled(entry: NavBackStackEntry): Modifier {
    val lifecycleState by entry.lifecycle.currentStateAsState()

    if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) return this

    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
}
