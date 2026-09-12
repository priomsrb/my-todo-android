package dev.shafqat.mytodo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.doneCount
import dev.shafqat.mytodo.model.totalCount
import dev.shafqat.mytodo.ui.theme.NoteColors
import dev.shafqat.mytodo.ui.theme.NoteColorsDark

/**
 * Every list, in miniature: the app's home grid as a home-screen shortcut board.
 *
 * Needs no configuration — it shows whatever lists exist, so creating, renaming and deleting them
 * in the app is enough to keep it current. Tapping one opens that list directly.
 */
class LauncherWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.widgetRepository()
        repository?.refresh()

        provideContent {
            GlanceTheme(colors = WidgetColors) {
                val context = LocalContext.current
                if (repository == null) {
                    WidgetMessage(context.getString(R.string.widget_unavailable))
                } else {
                    val lists by repository.lists.collectAsState()
                    if (lists.isEmpty()) {
                        WidgetMessage(context.getString(R.string.widget_no_lists))
                    } else {
                        LauncherWidgetContent(lists)
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherWidgetContent(lists: List<TodoList>) {
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(WidgetCornerRadius)
            .padding(WidgetPadding),
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = WidgetTitleSize,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .clickable(actionStartActivity(openListIntent(context, listId = null))),
        )

        // Paired with their positions up front: a list with no colour of its own is tinted by where
        // it sits, and Glance's items() hands the row its value, not its index.
        val positioned = lists.withIndex().toList()

        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            // Ids from the file name, so rows keep their identity as lists come and go.
            items(positioned, itemId = { it.value.id.hashCode().toLong() }) { (position, list) ->
                LauncherWidgetRow(list, position)
            }
        }
    }
}

/** One list: its colour, its name, and how much of it is done. */
@Composable
private fun LauncherWidgetRow(list: TodoList, position: Int) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(actionStartActivity(openListIntent(context, list.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same dot the app's grid paints across a whole card, shrunk to what a row can spare.
        Spacer(
            modifier = GlanceModifier
                .size(10.dp)
                .cornerRadius(5.dp)
                .background(listTint(list, position)),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = list.name,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = WidgetBodySize),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = "${list.items.doneCount()}/${list.items.totalCount()}",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = WidgetCaptionSize,
            ),
        )
    }
}

/**
 * A list's dot colour: the one it was given, or the tint of its position — the same fallback the
 * app's grid uses, so a list looks the same in both places.
 */
private fun listTint(list: TodoList, position: Int): GlanceColorProvider {
    val index = (list.prefs.colorIndex ?: position).mod(NoteColors.size)
    return ColorProvider(day = NoteColors[index], night = NoteColorsDark[index])
}

/** Receives the system's update broadcasts for [LauncherWidget]. */
class LauncherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LauncherWidget()
}
