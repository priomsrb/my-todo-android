package dev.shafqat.mytodo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoList

/**
 * A tile that is only a button: press it, speak, and what you said lands on one list.
 *
 * Everything about it is arranged around the time between the press and the microphone opening.
 * The tile draws no list contents, so it never has to be current to be correct; the tap goes
 * straight to [VoiceCaptureActivity] carrying the list's name, so nothing has to be read off disk
 * before the prompt can be shown. Which list it feeds is chosen in [VoiceWidgetConfigActivity] when
 * the widget is dropped, so several tiles can sit side by side feeding different lists.
 */
class VoiceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.widgetRepository()
        // A list renamed outside the app is a renamed *file*, and the file name is the list id — so
        // the tile would be pointing at nothing. Re-reading here is what notices.
        repository?.refresh()

        provideContent {
            GlanceTheme(colors = WidgetColors) {
                val context = LocalContext.current
                if (repository == null) {
                    WidgetMessage(context.getString(R.string.widget_unavailable))
                } else {
                    val listId = currentState(ListIdKey)
                    val lists by repository.lists.collectAsState()
                    // The position is what tints a list that never chose a colour, exactly as it
                    // does in the app's grid.
                    val position = lists.indexOfFirst { it.id == listId }
                    val list = lists.getOrNull(position)

                    if (list == null) {
                        WidgetMessage(context.getString(R.string.widget_list_missing))
                    } else {
                        VoiceWidgetTile(list, position)
                    }
                }
            }
        }
    }

    companion object {
        val ListIdKey = stringPreferencesKey(WIDGET_LIST_ID_KEY)
    }
}

/** The whole tile is the target: a mic on the list's own colour, named underneath when it fits. */
@Composable
private fun VoiceWidgetTile(list: TodoList, position: Int) {
    val context = LocalContext.current
    val height = LocalSize.current.height

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(listTint(list, position))
            .cornerRadius(WidgetCornerRadius)
            .clickable(actionStartActivity(voiceCaptureIntent(context, list.id, list.name)))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_mic),
            // Read aloud in place of the tile itself, since the label below may not be drawn.
            contentDescription = context.getString(R.string.voice_widget_action, list.name),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.size(MicSize),
        )

        // On a single cell the mic alone has to carry the tile; crowding a name in beside it would
        // leave both too small to read.
        if (height >= LabelMinHeight) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = list.name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = WidgetCaptionSize,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

private val MicSize = 28.dp

/** Below this the tile is one cell tall, and only the mic fits. */
private val LabelMinHeight = 90.dp

/** Receives the system's update broadcasts for [VoiceWidget]. */
class VoiceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoiceWidget()
}
