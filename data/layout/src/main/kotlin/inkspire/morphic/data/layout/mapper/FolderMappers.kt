package inkspire.morphic.data.layout.mapper

import inkspire.morphic.core.database.entity.FolderEntity
import inkspire.morphic.core.database.entity.FolderItemEntity
import inkspire.morphic.core.model.Folder

/**
 * Assembles [Folder] domain models from the two folder tables: each folder's [Folder.apps] is its `folder_item`
 * rows in `sortOrder`. A join across the definition + membership rows, kept out of the repository so the flow
 * there stays a one-liner.
 */
internal fun foldersOf(folders: List<FolderEntity>, items: List<FolderItemEntity>): List<Folder> {
    val appsByFolder = items.sortedBy { it.sortOrder }.groupBy { it.folderId }
    return folders.map { folder ->
        Folder(
            id = folder.id,
            label = folder.label,
            apps = appsByFolder[folder.id].orEmpty().map { it.component },
        )
    }
}
