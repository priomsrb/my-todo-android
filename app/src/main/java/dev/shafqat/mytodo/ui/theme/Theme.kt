package dev.shafqat.mytodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Exposed rather than private so the home-screen widgets can be built from the same palette:
 * a widget that did not match the app would read as a different product.
 */
val KeepLightColors = lightColorScheme(
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
    // The snackbar inverts the theme; without these it would come up in Material's default purple.
    inverseSurface = KeepOnSurface,
    inverseOnSurface = KeepSurface,
    inversePrimary = KeepYellow,
)

val KeepDarkColors = darkColorScheme(
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
    inverseSurface = KeepOnSurfaceDark,
    inverseOnSurface = KeepOnSurface,
    inversePrimary = KeepAmberDeep,
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
        colorScheme = if (darkTheme) KeepDarkColors else KeepLightColors,
        typography = MyTodoTypography,
        content = content,
    )
}
