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
