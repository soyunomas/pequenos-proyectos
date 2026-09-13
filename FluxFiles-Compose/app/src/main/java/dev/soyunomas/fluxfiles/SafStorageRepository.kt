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

    override fun rootLocation(treeUri: Uri): BrowserLocation {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        return BrowserLocation(id, queryDisplayName(documentUri(treeUri, id)) ?: "Ubicación")
    }

    override fun listChildren(treeUri: Uri, location: BrowserLocation): List<StorageEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, location.documentId)
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
                result += StorageEntry(
                    documentId = id,
                    uri = documentUri(treeUri, id),
                    name = cursor.getString(1) ?: "Sin nombre",
                    mimeType = cursor.getString(2) ?: "application/octet-stream",
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

    override fun createFolder(treeUri: Uri, parent: BrowserLocation, name: String) {
        checkNotNull(
            DocumentsContract.createDocument(
                resolver,
                documentUri(treeUri, parent.documentId),
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
        ) { "Android no pudo crear la carpeta" }
    }

    override fun rename(entry: StorageEntry, name: String) {
        checkNotNull(DocumentsContract.renameDocument(resolver, entry.uri, name)) {
            "Android no pudo renombrar el elemento"
        }
    }

    override fun delete(entry: StorageEntry) {
        check(DocumentsContract.deleteDocument(resolver, entry.uri)) {
            "Android no pudo eliminar el elemento"
        }
    }

    override fun copyEntry(treeUri: Uri, source: StorageEntry, destination: BrowserLocation, targetName: String) {
        copyEntryRecursive(treeUri, source, destination, targetName)
    }

    override fun moveEntry(
        treeUri: Uri,
        source: StorageEntry,
        sourceParent: BrowserLocation,
        destination: BrowserLocation,
        targetName: String,
    ) {
        if (targetName == source.name && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val moved = runCatching {
                DocumentsContract.moveDocument(
                    resolver,
                    source.uri,
                    documentUri(treeUri, sourceParent.documentId),
                    documentUri(treeUri, destination.documentId),
                )
            }.getOrNull()
            if (moved != null) return
        }
        copyEntryRecursive(treeUri, source, destination, targetName)
        check(DocumentsContract.deleteDocument(resolver, source.uri)) {
            "Se copió “$targetName”, pero no se pudo eliminar el original"
        }
    }

    override fun replaceEntry(
        treeUri: Uri,
        source: StorageEntry,
        existing: StorageEntry,
        sourceParent: BrowserLocation,
        destination: BrowserLocation,
        move: Boolean,
    ) {
        require(source.documentId != existing.documentId) { "No se puede reemplazar un elemento consigo mismo" }
        val temporaryName = "Flux temporal ${UUID.randomUUID().toString().take(8)} - ${source.name}"
        val stagedUri = copyEntryRecursive(treeUri, source, destination, temporaryName)
        if (!runCatching { DocumentsContract.deleteDocument(resolver, existing.uri) }.getOrDefault(false)) {
            runCatching { DocumentsContract.deleteDocument(resolver, stagedUri) }
            error("No se pudo eliminar el elemento existente; no se modificó el original")
        }
        if (runCatching { DocumentsContract.renameDocument(resolver, stagedUri, source.name) }.getOrNull() == null) {
            error("El contenido nuevo quedó guardado como “$temporaryName”, pero Android no permitió completar el reemplazo")
        }
        if (move) {
            check(DocumentsContract.deleteDocument(resolver, source.uri)) {
                "El destino se actualizó, pero no se pudo eliminar el original"
            }
        }
    }

    override fun persistTreePermission(uri: Uri) {
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, both) }
            .getOrElse { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    override fun hasPersistedPermission(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private fun copyEntryRecursive(
        treeUri: Uri,
        source: StorageEntry,
        destination: BrowserLocation,
        targetName: String = source.name,
    ): Uri {
        val created = checkNotNull(
            DocumentsContract.createDocument(
                resolver,
                documentUri(treeUri, destination.documentId),
                source.mimeType,
                targetName,
            )
        ) { "No se pudo crear “$targetName” en el destino" }
        try {
            if (source.isDirectory) {
                val createdLocation = BrowserLocation(DocumentsContract.getDocumentId(created), targetName)
                listChildren(treeUri, BrowserLocation(source.documentId, source.name)).forEach { child ->
                    copyEntryRecursive(treeUri, child, createdLocation)
                }
            } else {
                val input = checkNotNull(resolver.openInputStream(source.uri)) { "No se pudo leer “${source.name}”" }
                val output = checkNotNull(resolver.openOutputStream(created, "w")) { "No se pudo escribir “$targetName”" }
                input.use { sourceStream -> output.use { targetStream -> sourceStream.copyTo(targetStream) } }
            }
            return created
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw t
        }
    }

    private fun documentUri(treeUri: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun queryDisplayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { if (it.moveToFirst()) it.getString(0) else null }
}
