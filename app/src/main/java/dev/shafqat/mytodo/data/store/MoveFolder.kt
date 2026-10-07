package dev.shafqat.mytodo.data.store

/** Why a folder could not be moved. The message is shown to the user as is. */
class MoveFolderException(message: String) : Exception(message)

/**
 * Moves every list and archive from [from] into [to], and returns how many files went.
 *
 * Nothing in the source is deleted until everything has been copied and read back, so a failure
 * part-way leaves the original folder whole (and at worst some copies in the destination). A
 * destination that already holds a file of the same name is refused up front rather than
 * overwritten: the user's lists are not ours to merge or replace.
 */
suspend fun moveFolder(from: TodoFileStore, to: TodoFileStore): Int {
    val names = from.listFileNames()
    val existing = to.listFileNames().map { it.lowercase() }.toSet()
    val clashes = names.filter { it.lowercase() in existing }
    if (clashes.isNotEmpty()) {
        throw MoveFolderException(
            "That folder already has ${clashes.joinToString(", ")}. Choose an empty folder.",
        )
    }

    val contents = names.associateWith { from.read(it) }
    for ((name, content) in contents) {
        val created = to.create(name)
        to.write(created, content)
        if (to.read(created) != content) {
            throw MoveFolderException("Could not copy $name to the new folder.")
        }
    }
    names.forEach { from.delete(it) }
    return names.size
}
