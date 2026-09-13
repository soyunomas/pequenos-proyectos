package dev.soyunomas.fluxfiles

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Android-only compatibility helpers for the 0.5.x SAF UI and preview/archive code.
 * Domain models keep only opaque backend references; this file is the boundary that
 * interprets SAF references as Android Uris while those features are migrated.
 */
val BrowserUiState.treeUri: Uri?
    get() = storageRoot
        ?.takeIf { it.backend == SafStorageRepository.SAF_BACKEND }
        ?.let { Uri.parse(it.opaqueId) }

val StorageEntry.uri: Uri
    get() {
        require(ref.backend == SafStorageRepository.SAF_BACKEND) { "El elemento no pertenece al backend SAF" }
        return Uri.parse(ref.opaqueId)
    }

val StorageEntry.documentId: String
    get() = DocumentsContract.getDocumentId(uri)

val BrowserLocation.documentId: String
    get() {
        require(ref.backend == SafStorageRepository.SAF_BACKEND) { "La ubicación no pertenece al backend SAF" }
        return DocumentsContract.getDocumentId(Uri.parse(ref.opaqueId))
    }

/**
 * Temporary source-compatibility factory for legacy SAF-only helpers that still build
 * StorageEntry values from a document Uri. It deliberately lives outside BrowserModels
 * so Android types do not leak back into the domain model.
 */
@Suppress("FunctionName", "UNUSED_PARAMETER")
fun StorageEntry(
    documentId: String,
    uri: Uri,
    name: String,
    mimeType: String,
    sizeBytes: Long?,
    modifiedAtMillis: Long?,
): StorageEntry = StorageEntry(
    ref = StorageRef(SafStorageRepository.SAF_BACKEND, uri.toString()),
    name = name,
    kind = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
        StorageEntryKind.DIRECTORY
    } else {
        StorageEntryKind.FILE
    },
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    modifiedAtMillis = modifiedAtMillis,
)
