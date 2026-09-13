package dev.soyunomas.fluxfiles

import android.content.Context
import android.net.Uri

interface StorageRepository {
    fun rootLocation(treeUri: Uri): BrowserLocation
    fun listChildren(treeUri: Uri, location: BrowserLocation): List<StorageEntry>
    fun createFolder(treeUri: Uri, parent: BrowserLocation, name: String)
    fun rename(entry: StorageEntry, name: String)
    fun delete(entry: StorageEntry)

    fun copyEntry(
        treeUri: Uri,
        source: StorageEntry,
        destination: BrowserLocation,
        targetName: String = source.name,
    )

    fun moveEntry(
        treeUri: Uri,
        source: StorageEntry,
        sourceParent: BrowserLocation,
        destination: BrowserLocation,
        targetName: String = source.name,
    )

    fun replaceEntry(
        treeUri: Uri,
        source: StorageEntry,
        existing: StorageEntry,
        sourceParent: BrowserLocation,
        destination: BrowserLocation,
        move: Boolean,
    )

    fun persistTreePermission(uri: Uri)
    fun hasPersistedPermission(uri: Uri): Boolean

    companion object {
        operator fun invoke(context: Context): StorageRepository = SafStorageRepository(context)
    }
}
