package dev.shafqat.mytodo.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import dev.shafqat.mytodo.model.TodoList
import dev.shafqat.mytodo.ui.theme.KeepDarkColors
import dev.shafqat.mytodo.ui.theme.KeepLightColors
import dev.shafqat.mytodo.ui.theme.NoteColors
import dev.shafqat.mytodo.ui.theme.NoteColorsDark

/**
 * The widgets' palette, built from the app's own colour schemes.
 *
 * Deliberately the same values rather than a second set: a widget that did not match the app it
 * came from would read as a different product sitting on the home screen.
 */
val WidgetColors = ColorProviders(light = KeepLightColors, dark = KeepDarkColors)

/** Corner radius for a widget's own background. Honoured from API 31; ignored politely below it. */
internal val WidgetCornerRadius = 16.dp

/** Padding inside a widget's frame. */
internal val WidgetPadding = 12.dp

internal val WidgetTitleSize = 15.sp
internal val WidgetBodySize = 14.sp
internal val WidgetCaptionSize = 12.sp

/** Indent per nesting level. Tighter than the app's, because a widget has less room to spend. */
internal val WidgetIndentPerLevel = 12.dp

/**
 * A list's colour: the one it was given, or the tint of its position — the same fallback the app's
 * grid uses, so a list looks the same wherever it is drawn.
 */
internal fun listTint(list: TodoList, position: Int): GlanceColorProvider {
    val index = (list.prefs.colorIndex ?: position).mod(NoteColors.size)
    return ColorProvider(day = NoteColors[index], night = NoteColorsDark[index])
}

/** What a widget shows instead of a list: nothing configured, nothing to show, nothing readable. */
@Composable
internal fun WidgetMessage(text: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(WidgetCornerRadius)
            .padding(WidgetPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = WidgetBodySize,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
