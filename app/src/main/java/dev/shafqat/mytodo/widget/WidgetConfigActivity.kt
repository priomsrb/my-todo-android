package dev.shafqat.mytodo.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.model.doneCount
import dev.shafqat.mytodo.model.totalCount
import dev.shafqat.mytodo.ui.components.EmptyState
import dev.shafqat.mytodo.ui.lists.ListsViewModel
import dev.shafqat.mytodo.ui.theme.MyTodoTheme
import dev.shafqat.mytodo.ui.theme.noteColors
import kotlinx.coroutines.launch

/**
 * Asks which list a freshly dropped widget belongs to.
 *
 * Shared by every widget that is configured per list, because the contract with the launcher is the
 * subtle part and is best written down once: the launcher starts this *before* the widget exists on
 * the home screen and reads a cancelled result as "do not place it", which is why the result is set
 * to cancelled up front and only replaced once a list has actually been chosen.
 */
abstract class WidgetConfigActivity : ComponentActivity() {

    /** The widget being configured — redrawn once its list is known. */
    protected abstract val widget: GlanceAppWidget

    /** What the picker is asking for, in this widget's own terms. */
    @get:StringRes
    protected abstract val titleRes: Int

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MyTodoTheme {
                ListPickerScreen(title = stringResource(titleRes), onPick = ::chooseList)
            }
        }
    }

    /** Stores the chosen list in this widget's own state, draws it, and reports success. */
    private fun chooseList(list: TodoList) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)

            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { state ->
                state[stringPreferencesKey(WIDGET_LIST_ID_KEY)] = list.id
            }
            widget.update(this@WidgetConfigActivity, glanceId)

            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPickerScreen(
    title: String,
    onPick: (TodoList) -> Unit,
    viewModel: ListsViewModel = viewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        val tints = noteColors()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            if (lists.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Widgets,
                    title = stringResource(R.string.widget_no_lists_title),
                    subtitle = stringResource(R.string.widget_no_lists_subtitle),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(lists, key = { _, list -> list.id }) { index, list ->
                        ListPickerRow(
                            list = list,
                            tint = tints[(list.prefs.colorIndex ?: index) % tints.size],
                            onClick = { onPick(list) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListPickerRow(list: TodoList, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = list.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${list.items.doneCount()}/${list.items.totalCount()} done",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
