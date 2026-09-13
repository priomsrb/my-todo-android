package dev.shafqat.mytodo.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.shafqat.mytodo.ui.search.SearchScreen
import dev.shafqat.mytodo.ui.settings.SettingsScreen
import dev.shafqat.mytodo.ui.todo.TodoListScreen

/** How long a screen takes to fade in or out. Short, so screens settle quickly under a fast tap. */
internal const val ScreenTransitionMillis = 180

object Routes {
    const val LISTS = "lists"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val LIST_DETAIL = "list/{listId}"

    // List ids are filenames, so they must be encoded before going into a route.
    fun listDetail(listId: String) = "list/" + Uri.encode(listId)
}

/**
 * What a home-screen widget asked the app to do on its way in.
 *
 * @param startNewItem set by the list widget's "+": land in the list with an empty item already
 *   open for typing, rather than just showing it.
 */
data class OpenListRequest(
    val listId: String,
    val startNewItem: Boolean = false,
)

/**
 * @param openRequest what a home-screen widget asked for, or null for the usual start.
 * @param onRequestHandled called once that request has been navigated to, so the next tap on the
 *   same widget counts as a new request rather than being swallowed as a repeat.
 */
@Composable
fun MyTodoApp(
    openRequest: OpenListRequest? = null,
    onRequestHandled: () -> Unit = {},
) {
    val navController = rememberNavController()

    // The back stack entry that still owes someone a new row.
    //
    // The *entry*, not the list. Opening a list the app is already showing pops the screen and
    // pushes a new one, and for the length of the fade both are composed — so a request addressed
    // to "the screen showing this list" is seen by two of them, and the one on its way out can get
    // there first. It then adds the row to its own dying ViewModel, taking the focus with it: the
    // surviving screen opens no editor, no keyboard appears, and because nothing is being edited
    // nothing cleans the blank row up either. Naming the entry leaves only one possible reader.
    //
    // Held here rather than in the route because it is an event, not part of the destination's
    // identity: as an argument it would fire again every time the entry was rebuilt.
    var startNewItemIn by remember { mutableStateOf<String?>(null) }

    // A widget opens a list *on top of* the home screen rather than instead of it, so back still
    // goes where it always goes.
    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            navController.navigate(Routes.listDetail(openRequest.listId)) {
                popUpTo(Routes.LISTS)
            }
            // Read after navigating, because the entry being addressed is the one just pushed.
            if (openRequest.startNewItem) {
                startNewItemIn = navController.currentBackStackEntry?.id
            }
            onRequestHandled()
        }
    }

    // Screens cross-fade, so both are briefly translucent and whatever sits behind them shows
    // through. Without a surface in the app's own background colour that is the bare window, which
    // reads as a flash every time a screen changes.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
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
                        onSearchClick = { navController.navigate(Routes.SEARCH) },
                        onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    )
                }
            }
            composable(
                route = Routes.LIST_DETAIL,
                arguments = listOf(navArgument("listId") { type = NavType.StringType }),
            ) { entry ->
                val listId = requireNotNull(entry.arguments?.getString("listId"))
                Screen(entry) {
                    TodoListScreen(
                        listId = listId,
                        onBack = { navController.popBackStack() },
                        startNewItem = startNewItemIn == entry.id,
                        onNewItemStarted = { startNewItemIn = null },
                        // A rename renames the file, and the file name is this route's argument,
                        // so the screen has to be reopened under the id the list now has.
                        onRenamed = { newListId ->
                            navController.navigate(Routes.listDetail(newListId)) {
                                popUpTo(Routes.LIST_DETAIL) { inclusive = true }
                            }
                        },
                    )
                }
            }
            composable(Routes.SEARCH) { entry ->
                Screen(entry) {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenList = { listId -> navController.navigate(Routes.listDetail(listId)) },
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
internal fun Screen(entry: NavBackStackEntry, content: @Composable () -> Unit) {
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
