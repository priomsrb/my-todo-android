package dev.shafqat.mytodo.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.shafqat.mytodo.ui.lists.ListsScreen
import dev.shafqat.mytodo.ui.settings.SettingsScreen
import dev.shafqat.mytodo.ui.todo.TodoListScreen

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

    NavHost(navController = navController, startDestination = Routes.LISTS) {
        composable(Routes.LISTS) {
            ListsScreen(
                onListClick = { listId -> navController.navigate(Routes.listDetail(listId)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.LIST_DETAIL,
            arguments = listOf(navArgument("listId") { type = NavType.StringType }),
        ) { backStackEntry ->
            TodoListScreen(
                listId = requireNotNull(backStackEntry.arguments?.getString("listId")),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
