package dev.shafqat.mytodo.model

/**
 * Per-list settings that the markdown file has no way to express, and so are stored locally.
 *
 * Same reasoning as collapse state: the `.md` file is the user's, and the app never writes view
 * preferences into it. Unlike collapse these are keyed by file name rather than by item path,
 * because they belong to the list as a whole.
 *
 * [colorIndex] is an index into the app's note palette, or null for "no colour chosen", which
 * leaves the list showing the default tint for its position in the grid.
 */
data class ListPrefs(
    val colorIndex: Int? = null,
    val hideCompleted: Boolean = false,
) {
    val isDefault: Boolean get() = this == Default

    companion object {
        val Default = ListPrefs()
    }
}
