package dev.shafqat.mytodo.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/** Puts [text] on the clipboard, labelled [label] for whatever shows the user what they copied. */
fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * Whether the system already says a copy happened.
 *
 * Android 13 shows its own clipboard confirmation over the bottom of the screen — exactly where a
 * snackbar goes. Saying it a second time would stack one on top of the other.
 */
val ClipboardConfirmsItself: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
