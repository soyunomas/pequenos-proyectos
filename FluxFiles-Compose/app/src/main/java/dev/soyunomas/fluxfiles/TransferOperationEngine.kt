package dev.soyunomas.fluxfiles

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class TransferProgress(
    val current: Int,
    val total: Int,
    val mode: TransferMode,
) {
    val label: String
        get() = "${if (mode == TransferMode.COPY) "Copiando" else "Moviendo"} $current de $total…"
}

sealed interface TransferExecutionResult {
    val transferred: Int
    val skipped: Int

    data class Completed(
        override val transferred: Int,
        override val skipped: Int,
    ) : TransferExecutionResult

    data class Cancelled(
        override val transferred: Int,
        override val skipped: Int,
    ) : TransferExecutionResult
}

class TransferExecutionException(
    val transferred: Int,
    val skipped: Int,
    cause: Throwable,
) : Exception(cause.message, cause)

/**
 * Ejecuta una transferencia sin depender de Compose ni de un ViewModel.
 *
 * La UI conserva la responsabilidad de preguntar al usuario cómo resolver un conflicto;
 * el motor conserva la política de copia/movimiento, nombres alternativos y "aplicar a todos".
 * Esta separación permite sustituir el backend SAF en una fase posterior sin duplicar la
 * lógica de operaciones en cada pantalla.
 */
class TransferOperationEngine(
    private val repository: StorageRepository,
) {
    suspend fun execute(
        treeUri: Uri,
        transfer: PendingTransfer,
        destination: BrowserLocation,
        onProgress: suspend (TransferProgress) -> Unit,
        onConflict: suspend (TransferConflict) -> ConflictAnswer,
    ): TransferExecutionResult {
        var transferred = 0
        var skipped = 0
        var rememberedResolution: ConflictResolution? = null

        try {
            for ((index, entry) in transfer.entries.withIndex()) {
                onProgress(
                    TransferProgress(
                        current = index + 1,
                        total = transfer.entries.size,
                        mode = transfer.mode,
                    )
                )

                val existing = withContext(Dispatchers.IO) {
                    findConflict(treeUri, destination, entry.name)
                }

                var resolution: ConflictResolution? = null
                if (existing != null) {
                    resolution = rememberedResolution
                    if (
                        resolution == null ||
                        (resolution == ConflictResolution.REPLACE && existing.documentId == entry.documentId)
                    ) {
                        val answer = onConflict(TransferConflict(entry, existing))
                        if (answer.resolution == ConflictResolution.CANCEL) {
                            return TransferExecutionResult.Cancelled(transferred, skipped)
                        }
                        resolution = answer.resolution
                        if (
                            answer.applyToAll &&
                            !(answer.resolution == ConflictResolution.REPLACE && existing.documentId == entry.documentId)
                        ) {
                            rememberedResolution = answer.resolution
                        }
                    }
                }

                when (resolution) {
                    ConflictResolution.SKIP -> skipped++

                    ConflictResolution.KEEP_BOTH -> {
                        val uniqueName = withContext(Dispatchers.IO) {
                            uniqueNameCaseAware(treeUri, destination, entry.name)
                        }
                        withContext(Dispatchers.IO) {
                            transferEntry(
                                treeUri = treeUri,
                                transfer = transfer,
                                destination = destination,
                                entry = entry,
                                targetName = uniqueName,
                            )
                        }
                        transferred++
                    }

                    ConflictResolution.REPLACE -> {
                        val target = checkNotNull(existing)
                        require(target.documentId != entry.documentId) {
                            "No se puede reemplazar un elemento consigo mismo"
                        }
                        withContext(Dispatchers.IO) {
                            repository.replaceEntry(
                                treeUri = treeUri,
                                source = entry,
                                existing = target,
                                sourceParent = transfer.sourceParent,
                                destination = destination,
                                move = transfer.mode == TransferMode.MOVE,
                            )
                        }
                        transferred++
                    }

                    ConflictResolution.CANCEL -> {
                        return TransferExecutionResult.Cancelled(transferred, skipped)
                    }

                    null -> {
                        withContext(Dispatchers.IO) {
                            transferEntry(
                                treeUri = treeUri,
                                transfer = transfer,
                                destination = destination,
                                entry = entry,
                                targetName = entry.name,
                            )
                        }
                        transferred++
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: TransferExecutionException) {
            throw failure
        } catch (failure: Throwable) {
            throw TransferExecutionException(transferred, skipped, failure)
        }

        return TransferExecutionResult.Completed(transferred, skipped)
    }

    private fun transferEntry(
        treeUri: Uri,
        transfer: PendingTransfer,
        destination: BrowserLocation,
        entry: StorageEntry,
        targetName: String,
    ) {
        if (transfer.mode == TransferMode.COPY) {
            repository.copyEntry(treeUri, entry, destination, targetName)
        } else {
            repository.moveEntry(
                treeUri = treeUri,
                source = entry,
                sourceParent = transfer.sourceParent,
                destination = destination,
                targetName = targetName,
            )
        }
    }

    private fun findConflict(
        treeUri: Uri,
        destination: BrowserLocation,
        name: String,
    ): StorageEntry? {
        val children = repository.listChildren(treeUri, destination)
        children.firstOrNull { it.name == name }?.let { return it }
        val folded = name.lowercase(Locale.ROOT)
        return children.firstOrNull { it.name.lowercase(Locale.ROOT) == folded }
    }

    private fun uniqueNameCaseAware(
        treeUri: Uri,
        destination: BrowserLocation,
        originalName: String,
    ): String {
        val existing = repository.listChildren(treeUri, destination)
            .mapTo(hashSetOf()) { it.name.lowercase(Locale.ROOT) }
        if (originalName.lowercase(Locale.ROOT) !in existing) return originalName

        val lastDot = originalName.lastIndexOf('.')
        val hasExtension = lastDot > 0 && lastDot < originalName.lastIndex
        val stem = if (hasExtension) originalName.substring(0, lastDot) else originalName
        val extension = if (hasExtension) originalName.substring(lastDot) else ""
        var number = 1
        while (true) {
            val candidate = "$stem ($number)$extension"
            if (candidate.lowercase(Locale.ROOT) !in existing) return candidate
            number++
        }
    }
}
