package dev.soyunomas.fluxfiles

/**
 * Resolves a backend-neutral reference to the concrete filesystem implementation that owns it.
 *
 * The registry intentionally contains no Android types. Adding SFTP/SMB/FTP/WebDAV later only
 * requires registering another StorageFileSystem instead of teaching browser/ZIP UI about it.
 */
class StorageBackendRegistry(
    backends: Collection<StorageFileSystem>,
    val defaultBackendId: StorageBackendId,
) {
    private val fileSystems: Map<StorageBackendId, StorageFileSystem> =
        backends.associateBy { it.backendId }

    init {
        require(fileSystems.isNotEmpty()) { "Debe existir al menos un backend de almacenamiento" }
        require(defaultBackendId in fileSystems) { "El backend predeterminado no está registrado" }
        require(fileSystems.size == backends.size) { "Hay identificadores de backend duplicados" }
    }

    fun supports(backendId: StorageBackendId): Boolean = backendId in fileSystems

    fun requireFileSystem(backendId: StorageBackendId): StorageFileSystem =
        fileSystems[backendId] ?: error("Backend no disponible: ${backendId.value}")

    fun fileSystemFor(root: StorageRootRef): StorageFileSystem = requireFileSystem(root.backend)
    fun fileSystemFor(ref: StorageRef): StorageFileSystem = requireFileSystem(ref.backend)
    fun fileSystemFor(location: BrowserLocation): StorageFileSystem = fileSystemFor(location.ref)
    fun fileSystemFor(entry: StorageEntry): StorageFileSystem = fileSystemFor(entry.ref)
}
