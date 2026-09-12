package dev.shafqat.mytodo.ui.theme

import androidx.compose.ui.graphics.Color

// Keep-inspired palette: warm yellow accent on a near-white surface.
val KeepYellow = Color(0xFFFBBC04)
val KeepYellowDark = Color(0xFFE0A800)
val KeepSurface = Color(0xFFFFFFFF)
val KeepBackground = Color(0xFFFAFAFA)
val KeepOutline = Color(0xFFDADCE0)
val KeepOnSurface = Color(0xFF202124)
val KeepOnSurfaceVariant = Color(0xFF5F6368)

val KeepSurfaceDark = Color(0xFF202124)
val KeepBackgroundDark = Color(0xFF17181A)
val KeepOutlineDark = Color(0xFF3C4043)
val KeepOnSurfaceDark = Color(0xFFE8EAED)
val KeepOnSurfaceVariantDark = Color(0xFF9AA0A6)

/**
 * Accent for surfaces that invert the theme — the snackbar, chiefly.
 *
 * Material's `inversePrimary` defaults to its own purple, and the Keep yellow is unreadable on the
 * pale inverted surface a dark theme produces, so the dark theme gets this deeper amber instead.
 */
val KeepAmberDeep = Color(0xFF7A5900)

/** Card tints for lists, mirroring Keep's colored notes. Indexed by list position. */
val NoteColors = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFFFF8E1),
    Color(0xFFE8F0FE),
    Color(0xFFE6F4EA),
    Color(0xFFFCE8E6),
    Color(0xFFF3E8FD),
)

val NoteColorsDark = listOf(
    Color(0xFF202124),
    Color(0xFF3D3419),
    Color(0xFF1E2A3A),
    Color(0xFF1E2E23),
    Color(0xFF3A2220),
    Color(0xFF2C2438),
)
