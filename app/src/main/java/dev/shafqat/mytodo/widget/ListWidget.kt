package dev.shafqat.mytodo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.doneCount
import dev.shafqat.mytodo.model.totalCount

/**
 * One list on the home screen: its items, scrollable, ticked off in place.
 *
 * Which list it shows is chosen in [ListWidgetConfigActivity] when the widget is dropped and kept
 * in that widget's own Glance state, so several can sit side by side showing different lists.
 */
class ListWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.widgetRepository()
        // A widget update is often what woke the process, and a widget is the one surface likely to
        // be looking at a file edited somewhere else, so it reads from disk before drawing.
        // refresh() leaves files with an unsaved edit alone, so this cannot undo recent typing.
        repository?.refresh()

        provideContent {
            GlanceTheme(colors = WidgetColors) {
                val context = LocalContext.current
                if (repository == null) {
                    WidgetMessage(context.getString(R.string.widget_unavailable))
                } else {
                    val listId = currentState(ListIdKey)
                    val lists by repository.lists.collectAsState()
                    val list = lists.firstOrNull { it.id == listId }

                    if (list == null) {
                        WidgetMessage(context.getString(R.string.widget_list_missing))
                    } else {
                        ListWidgetContent(list)
                    }
                }
            }
        }
    }

    companion object {
        val ListIdKey = stringPreferencesKey(WIDGET_LIST_ID_KEY)
    }
}

@Composable
private fun ListWidgetContent(list: TodoList) {
    val context = LocalContext.current
    val rows = list.widgetRows()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(WidgetCornerRadius)
            .padding(WidgetPadding),
    ) {
        ListWidgetHeader(list)

        if (rows.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_list_empty),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = WidgetBodySize,
                ),
                modifier = GlanceModifier.padding(top = WidgetPadding),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(rows, itemId = { it.key.hashCode().toLong() }) { row ->
                    WidgetItemRow(listId = list.id, row = row)
                }
            }
        }
    }
}

/** The list's name and how much of it is done. Tapping it opens that list in the app. */
@Composable
private fun ListWidgetHeader(list: TodoList) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clickable(actionStartActivity(openListIntent(context, list.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = list.name,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = WidgetTitleSize,
                fontWeight = FontWeight.Medium,
            ),
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

/** One item: a real checkbox, indented to show where it sits in the tree. */
@Composable
private fun WidgetItemRow(listId: String, row: WidgetRow) {
    val context = LocalContext.current

    CheckBox(
        checked = row.done,
        onCheckedChange = actionRunCallback<ToggleItemAction>(
            ToggleItemAction.parameters(
                listId = listId,
                itemKey = row.key,
                targetDone = !row.done,
            ),
        ),
        text = row.text.ifBlank { context.getString(R.string.empty_item) },
        style = TextStyle(
            color = if (row.done) {
                GlanceTheme.colors.onSurfaceVariant
            } else {
                GlanceTheme.colors.onSurface
            },
            fontSize = WidgetBodySize,
            textDecoration = if (row.done) TextDecoration.LineThrough else TextDecoration.None,
        ),
        colors = CheckboxDefaults.colors(
            checkedColor = GlanceTheme.colors.onSurfaceVariant,
            uncheckedColor = GlanceTheme.colors.outline,
        ),
        maxLines = 2,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = WidgetIndentPerLevel * row.depth),
    )
}

/** Receives the system's update broadcasts for [ListWidget]. */
class ListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ListWidget()
}
