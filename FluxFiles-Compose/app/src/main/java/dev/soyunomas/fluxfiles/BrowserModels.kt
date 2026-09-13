package dev.soyunomas.fluxfiles

@JvmInline
value class StorageBackendId(val value: String)

data class StorageRootRef(
    val backend: StorageBackendId,
    val opaqueId: String,
)

data class StorageRef(
    val backend: StorageBackendId,
    val opaqueId: String,
)

enum class StorageEntryKind { FILE, DIRECTORY }

data class StorageEntry(
    val ref: StorageRef,
    val name: String,
    val kind: StorageEntryKind,
    val mimeType: String,
    val sizeBytes: Long?,
    val modifiedAtMillis: Long?,
) {
    val isDirectory: Boolean get() = kind == StorageEntryKind.DIRECTORY
}

data class BrowserLocation(
    val ref: StorageRef,
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
    val isSameDocument: Boolean get() = source.ref == existing.ref
}

data class PendingTransfer(
    val entries: List<StorageEntry>,
    val sourceParent: BrowserLocation,
    val mode: TransferMode,
)

data class BrowserUiState(
    val storageRoot: StorageRootRef? = null,
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
    val selectedEntries: List<StorageEntry> get() = entries.filter { it.ref.opaqueId in selectedIds }

    val canPasteHere: Boolean
        get() {
            val transfer = pendingTransfer ?: return false
            val current = currentLocation ?: return false
            if (transfer.mode == TransferMode.MOVE && current.ref == transfer.sourceParent.ref) return false
            val pathRefs = navigationStack.mapTo(hashSetOf()) { it.ref }
            return transfer.entries.none { it.isDirectory && it.ref in pathRefs }
        }
}
