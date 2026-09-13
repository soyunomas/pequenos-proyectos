package dev.soyunomas.fluxfiles

import java.io.InputStream
import java.io.OutputStream

interface StorageRepository {
    fun rootLocation(root: StorageRootRef): BrowserLocation
    fun listChildren(location: BrowserLocation): List<StorageEntry>
    fun createFolder(parent: BrowserLocation, name: String): StorageEntry
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

/**
 * Backend-neutral content access used by previews and archive operations.
 *
 * The common contract deliberately exposes JVM streams rather than Android Uri/ContentResolver.
 * A remote backend can therefore implement the same API without pretending to be a SAF document.
 */
interface StorageContentAccess {
    fun createFile(parent: BrowserLocation, name: String, mimeType: String): StorageEntry
    fun openInput(entry: StorageEntry): InputStream
    fun openOutput(entry: StorageEntry): OutputStream
}

interface StorageFileSystem : StorageRepository, StorageContentAccess
