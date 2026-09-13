package dev.soyunomas.fluxfiles

interface StorageRepository {
    fun rootLocation(root: StorageRootRef): BrowserLocation
    fun listChildren(location: BrowserLocation): List<StorageEntry>
    fun createFolder(parent: BrowserLocation, name: String)
    fun rename(entry: StorageEntry, name: String)
    fun delete(entry: StorageEntry)

    fun copyEntry(
        source: StorageEntry,
        destination: BrowserLocation,
        targetName: String = source.name,
    )

    fun moveEntry(
        source: StorageEntry,
        sourceParent: BrowserLocation,
        destination: BrowserLocation,
        targetName: String = source.name,
    )

    fun replaceEntry(
        source: StorageEntry,
        existing: StorageEntry,
        sourceParent: BrowserLocation,
        destination: BrowserLocation,
        move: Boolean,
    )

    fun persistRootPermission(root: StorageRootRef)
    fun hasPersistedPermission(root: StorageRootRef): Boolean
}
