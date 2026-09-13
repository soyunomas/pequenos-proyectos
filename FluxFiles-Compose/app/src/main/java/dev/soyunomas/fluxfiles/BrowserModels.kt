package dev.soyunomas.fluxfiles

import android.net.Uri
import android.provider.DocumentsContract

data class StorageEntry(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val modifiedAtMillis: Long?,
) {
    val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

data class BrowserLocation(
    val documentId: String,
    val name: String,
)

enum class TransferMode { COPY, MOVE }

enum class ConflictResolution { KEEP_BOTH, SKIP, REPLACE, CANCEL }

data class ConflictAnswer(
    val resolution: ConflictResolution,
    val applyToAll: Boolean,
)

data class TransferConflict(
    val source: StorageEntry,
    val existing: StorageEntry,
) {
    val isSameDocument: Boolean get() = source.documentId == existing.documentId
}

data class PendingTransfer(
    val entries: List<StorageEntry>,
    val sourceParent: BrowserLocation,
    val mode: TransferMode,
)

data class BrowserUiState(
    val treeUri: Uri? = null,
    val navigationStack: List<BrowserLocation> = emptyList(),
    val entries: List<StorageEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val pendingTransfer: PendingTransfer? = null,
    val transferConflict: TransferConflict? = null,
    val operationLabel: String? = null,
    val errorMessage: String? = null,
    val message: String? = null,
) {
    val currentLocation: BrowserLocation? get() = navigationStack.lastOrNull()
    val canNavigateUp: Boolean get() = navigationStack.size > 1
    val selectedEntries: List<StorageEntry> get() = entries.filter { it.documentId in selectedIds }

    val canPasteHere: Boolean
        get() {
            val transfer = pendingTransfer ?: return false
            val current = currentLocation ?: return false
            if (transfer.mode == TransferMode.MOVE && current.documentId == transfer.sourceParent.documentId) {
                return false
            }
            val pathIds = navigationStack.mapTo(hashSetOf()) { it.documentId }
            return transfer.entries.none { it.isDirectory && it.documentId in pathIds }
        }
}
