package dev.shafqat.mytodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = KeepYellowDark,
    onPrimary = Color.White,
    primaryContainer = KeepYellow,
    onPrimaryContainer = KeepOnSurface,
    surface = KeepSurface,
    onSurface = KeepOnSurface,
    surfaceVariant = KeepBackground,
    onSurfaceVariant = KeepOnSurfaceVariant,
    background = KeepBackground,
    onBackground = KeepOnSurface,
    outline = KeepOutline,
    outlineVariant = KeepOutline,
)

private val DarkColors = darkColorScheme(
    primary = KeepYellow,
    onPrimary = KeepOnSurface,
    primaryContainer = KeepYellowDark,
    onPrimaryContainer = KeepOnSurface,
    surface = KeepSurfaceDark,
    onSurface = KeepOnSurfaceDark,
    surfaceVariant = KeepBackgroundDark,
    onSurfaceVariant = KeepOnSurfaceVariantDark,
    background = KeepBackgroundDark,
    onBackground = KeepOnSurfaceDark,
    outline = KeepOutlineDark,
    outlineVariant = KeepOutlineDark,
)

/** Note card tints for the current theme, indexed by list position. */
@Composable
fun noteColors(): List<Color> = if (isSystemInDarkTheme()) NoteColorsDark else NoteColors

@Composable
fun MyTodoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic color is deliberately off: the Keep-like yellow identity is the point.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MyTodoTypography,
        content = content,
    )
}
