package dev.shafqat.mytodo.widget

import androidx.glance.appwidget.GlanceAppWidget
import dev.shafqat.mytodo.R

/** Asks which list a freshly dropped [VoiceWidget] should add to. */
class VoiceWidgetConfigActivity : WidgetConfigActivity() {
    override val widget: GlanceAppWidget = VoiceWidget()
    override val titleRes = R.string.voice_widget_pick_list
}
