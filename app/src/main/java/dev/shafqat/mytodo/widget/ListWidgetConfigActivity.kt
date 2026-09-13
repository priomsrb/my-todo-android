package dev.shafqat.mytodo.widget

import androidx.glance.appwidget.GlanceAppWidget
import dev.shafqat.mytodo.R

/** Asks which list a freshly dropped [ListWidget] should show. */
class ListWidgetConfigActivity : WidgetConfigActivity() {
    override val widget: GlanceAppWidget = ListWidget()
    override val titleRes = R.string.widget_pick_list
}
