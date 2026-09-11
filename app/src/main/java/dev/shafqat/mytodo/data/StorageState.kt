package dev.shafqat.mytodo.data

/** Where the app's files currently live, and whether it can actually reach them. */
sealed interface StorageState {

    /** Files are readable and writable at [label]. */
    data class Ready(val label: String, val listCount: Int) : StorageState

    /** A folder is configured but the app can no longer access it — usually a revoked permission. */
    data class PermissionLost(val label: String) : StorageState

    /** Reading or writing failed for some other reason; [message] is safe to show to the user. */
    data class Error(val label: String, val message: String) : StorageState

    /** No store has been attached yet (app start). */
    data object Loading : StorageState
}
