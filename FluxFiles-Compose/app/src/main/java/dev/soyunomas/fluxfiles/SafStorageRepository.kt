package dev.soyunomas.fluxfiles

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import java.text.Collator
import java.util.Locale
import java.util.UUID

class SafStorageRepository(context: Context) : StorageRepository {
    private val resolver: ContentResolver = context.contentResolver
    private val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }

    override fun rootLocation(root: StorageRootRef): BrowserLocation {
        require(root.backend == SAF_BACKEND) { "Backend no compatible con SAF" }
        val treeUri = Uri.parse(root.opaqueId)
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
        return BrowserLocation(StorageRef(SAF_BACKEND, uri.toString()), queryDisplayName(uri) ?: "Ubicación")
    }

    override fun listChildren(location: BrowserLocation): List<StorageEntry> {
        val parentUri = uri(location.ref)
        val parentId = DocumentsContract.getDocumentId(parentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val result = mutableListOf<StorageEntry>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val mime = cursor.getString(2) ?: "application/octet-stream"
                val childUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, id)
                result += StorageEntry(
                    ref = StorageRef(SAF_BACKEND, childUri.toString()),
                    name = cursor.getString(1) ?: "Sin nombre",
                    kind = if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        StorageEntryKind.DIRECTORY
                    } else {
                        StorageEntryKind.FILE
                    },
                    mimeType = mime,
                    sizeBytes = if (cursor.isNull(3)) null else cursor.getLong(3),
                    modifiedAtMillis = if (cursor.isNull(4)) null else cursor.getLong(4),
                )
            }
        }
        return result.sortedWith { a, b ->
            when {
                a.isDirectory && !b.isDirectory -> -1
                !a.isDirectory && b.isDirectory -> 1
                else -> collator.compare(a.name, b.name)
            }
        }
    }

    override fun createFolder(parent: BrowserLocation, name: String) {
        checkNotNull(
            DocumentsContract.createDocument(
                resolver,
                uri(parent.ref),
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
        ) { "Android no pudo crear la carpeta" }
    }

    override fun rename(entry: StorageEntry, name: String) {
        checkNotNull(DocumentsContract.renameDocument(resolver, uri(entry.ref), name)) {
            "Android no pudo renombrar el elemento"
        }
    }

    override fun delete(entry: StorageEntry) {
        check(DocumentsContract.deleteDocument(resolver, uri(entry.ref))) {
            "Android no pudo eliminar el elemento"
        }
    }

    override fun copyEntry(source: StorageEntry, destination: BrowserLocation, targetName: String) {
        copyEntryRecursive(source, destination, targetName)
    }

    override fun moveEntry(
        source: StorageEntry,
        sourceParent: BrowserLocation,
        destination: BrowserLocation,
        targetName: String,
    ) {
        val sourceUri = uri(source.ref)
        if (targetName == source.name && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val moved = runCatching {
                DocumentsContract.moveDocument(
                    resolver,
                    sourceUri,
                    uri(sourceParent.ref),
                    uri(destination.ref),
                )
            }.getOrNull()
            if (moved != null) return
        }
        copyEntryRecursive(source, destination, targetName)
        check(DocumentsContract.deleteDocument(resolver, sourceUri)) {
            "Se copió “$targetName”, pero no se pudo eliminar el original"
        }
    }

    override fun replaceEntry(
        source: StorageEntry,
        existing: StorageEntry,
        sourceParent: BrowserLocation,
        destination: BrowserLocation,
        move: Boolean,
    ) {
        require(source.ref != existing.ref) { "No se puede reemplazar un elemento consigo mismo" }
        val temporaryName = "Flux temporal ${UUID.randomUUID().toString().take(8)} - ${source.name}"
        val stagedUri = copyEntryRecursive(source, destination, temporaryName)
        if (!runCatching { DocumentsContract.deleteDocument(resolver, uri(existing.ref)) }.getOrDefault(false)) {
            runCatching { DocumentsContract.deleteDocument(resolver, stagedUri) }
            error("No se pudo eliminar el elemento existente; no se modificó el original")
        }
        if (runCatching { DocumentsContract.renameDocument(resolver, stagedUri, source.name) }.getOrNull() == null) {
            error("El contenido nuevo quedó guardado como “$temporaryName”, pero Android no permitió completar el reemplazo")
        }
        if (move) {
            check(DocumentsContract.deleteDocument(resolver, uri(source.ref))) {
                "El destino se actualizó, pero no se pudo eliminar el original"
            }
        }
    }

    override fun persistRootPermission(root: StorageRootRef) {
        require(root.backend == SAF_BACKEND) { "Backend no compatible con SAF" }
        val rootUri = Uri.parse(root.opaqueId)
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(rootUri, both) }
            .getOrElse { resolver.takePersistableUriPermission(rootUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    override fun hasPersistedPermission(root: StorageRootRef): Boolean {
        if (root.backend != SAF_BACKEND) return false
        val rootUri = Uri.parse(root.opaqueId)
        return resolver.persistedUriPermissions.any { it.uri == rootUri && it.isReadPermission }
    }

    private fun copyEntryRecursive(
        source: StorageEntry,
        destination: BrowserLocation,
        targetName: String = source.name,
    ): Uri {
        val created = checkNotNull(
            DocumentsContract.createDocument(
                resolver,
                uri(destination.ref),
                source.mimeType,
                targetName,
            )
        ) { "No se pudo crear “$targetName” en el destino" }
        try {
            if (source.isDirectory) {
                val createdLocation = BrowserLocation(
                    ref = StorageRef(SAF_BACKEND, created.toString()),
                    name = targetName,
                )
                listChildren(BrowserLocation(source.ref, source.name)).forEach { child ->
                    copyEntryRecursive(child, createdLocation)
                }
            } else {
                val input = checkNotNull(resolver.openInputStream(uri(source.ref))) { "No se pudo leer “${source.name}”" }
                val output = checkNotNull(resolver.openOutputStream(created, "w")) { "No se pudo escribir “$targetName”" }
                input.use { sourceStream -> output.use { targetStream -> sourceStream.copyTo(targetStream) } }
            }
            return created
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw t
        }
    }

    private fun uri(ref: StorageRef): Uri {
        require(ref.backend == SAF_BACKEND) { "Backend no compatible con SAF" }
        return Uri.parse(ref.opaqueId)
    }

    private fun queryDisplayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { if (it.moveToFirst()) it.getString(0) else null }

    companion object {
        val SAF_BACKEND = StorageBackendId("saf")
    }
}
