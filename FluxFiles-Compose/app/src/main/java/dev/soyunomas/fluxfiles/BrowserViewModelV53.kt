package dev.soyunomas.fluxfiles

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class BrowserViewModelV53(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)
    private val prefs = app.getSharedPreferences("flux_files", Context.MODE_PRIVATE)
    private val resolver = app.contentResolver

    private val _state = MutableStateFlow(BrowserUiState())
    val state = _state.asStateFlow()

    private val _canWrite = MutableStateFlow(false)
    val canWrite = _canWrite.asStateFlow()

    private var conflictAnswer: CompletableDeferred<ConflictAnswer>? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        prefs.getString("tree_uri", null)
            ?.let(Uri::parse)
            ?.takeIf(repo::hasPersistedPermission)
            ?.let { selectTree(it, false) }
    }

    fun selectTree(uri: Uri, persist: Boolean = true) {
        if (_state.value.pendingTransfer != null || _state.value.isMutating) return

        val generation = ++loadGeneration
        loadJob?.cancel()
        _state.update {
            it.copy(
                treeUri = uri,
                navigationStack = emptyList(),
                entries = emptyList(),
                selectedIds = emptySet(),
                isSelectionMode = false,
                isLoading = true,
                errorMessage = null,
            )
        }

        loadJob = viewModelScope.launch {
            try {
                if (persist) {
                    withContext(Dispatchers.IO) { repo.persistTreePermission(uri) }
                }
                val root = withContext(Dispatchers.IO) { repo.rootLocation(uri) }
                val entries = withContext(Dispatchers.IO) { repo.listChildren(uri, root) }
                if (generation != loadGeneration) return@launch

                prefs.edit().putString("tree_uri", uri.toString()).apply()
                _canWrite.value = hasPersistedWritePermission(uri)
                _state.update {
                    it.copy(
                        treeUri = uri,
                        navigationStack = listOf(root),
                        entries = entries,
                        isLoading = false,
                        selectedIds = emptySet(),
                        isSelectionMode = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (generation == loadGeneration) {
                    _canWrite.value = false
                    _state.update {
                        it.copy(
                            isLoading = false,
                            entries = emptyList(),
                            errorMessage = t.message?.takeIf(String::isNotBlank)
                                ?: "No se pudo abrir esa ubicación",
                        )
                    }
                }
            }
        }
    }

    fun openDirectory(entry: StorageEntry) {
        val snapshot = _state.value
        if (!entry.isDirectory || snapshot.isMutating || snapshot.isSelectionMode) return
        loadLocation(
            stack = snapshot.navigationStack + BrowserLocation(entry.documentId, entry.name),
            clearEntries = true,
        )
    }

    fun navigateUp(): Boolean {
        val snapshot = _state.value
        if (snapshot.isMutating || !snapshot.canNavigateUp) return false
        loadLocation(snapshot.navigationStack.dropLast(1), clearEntries = true)
        return true
    }

    fun refresh() {
        val stack = _state.value.navigationStack
        if (stack.isNotEmpty()) loadLocation(stack, clearEntries = false)
    }

    fun createFolder(name: String) = mutate("Carpeta creada") { snapshot ->
        repo.createFolder(
            snapshot.treeUri ?: error("No hay ubicación abierta"),
            snapshot.currentLocation ?: error("No hay carpeta abierta"),
            validName(name),
        )
    }

    fun rename(entry: StorageEntry, name: String) = mutate("Renombrado correctamente") {
        repo.rename(entry, validName(name))
    }

    fun delete(entry: StorageEntry) = mutate(
        if (entry.isDirectory) "Carpeta eliminada" else "Archivo eliminado"
    ) {
        repo.delete(entry)
    }

    fun enterSelection() {
        if (_state.value.pendingTransfer != null || _state.value.isMutating) return
        _state.update { it.copy(isSelectionMode = true, selectedIds = emptySet()) }
    }

    fun startSelection(entry: StorageEntry) {
        if (_state.value.pendingTransfer != null || _state.value.isMutating) return
        _state.update {
            it.copy(isSelectionMode = true, selectedIds = it.selectedIds + entry.documentId)
        }
    }

    fun toggleSelection(entry: StorageEntry) {
        if (!_state.value.isSelectionMode || _state.value.isMutating) return
        _state.update {
            val next = if (entry.documentId in it.selectedIds) {
                it.selectedIds - entry.documentId
            } else {
                it.selectedIds + entry.documentId
            }
            it.copy(selectedIds = next)
        }
    }

    fun selectAll() {
        if (!_state.value.isSelectionMode || _state.value.isMutating) return
        _state.update {
            it.copy(selectedIds = it.entries.mapTo(hashSetOf()) { entry -> entry.documentId })
        }
    }

    fun clearSelection() {
        if (_state.value.isMutating) return
        _state.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
    }

    fun beginTransfer(mode: TransferMode) {
        if (!requireWriteAccess("copiar o mover elementos")) return
        val snapshot = _state.value
        if (!snapshot.isSelectionMode || snapshot.isMutating) return
        val selected = snapshot.selectedEntries
        if (selected.isEmpty()) {
            setErrorMessage("Selecciona al menos un elemento")
            return
        }
        val sourceParent = snapshot.currentLocation ?: return
        _state.update {
            it.copy(
                isSelectionMode = false,
                selectedIds = emptySet(),
                pendingTransfer = PendingTransfer(selected, sourceParent, mode),
                message = if (
                    mode == TransferMode.COPY &&
                    sourceParent.documentId == it.currentLocation?.documentId
                ) {
                    "Puedes elegir otra carpeta o copiar aquí para crear duplicados"
                } else {
                    "Elige la carpeta de destino"
                },
            )
        }
    }

    fun cancelTransfer() {
        if (_state.value.isMutating && _state.value.transferConflict == null) return
        conflictAnswer?.takeIf { !it.isCompleted }?.complete(
            ConflictAnswer(ConflictResolution.CANCEL, false)
        )
        conflictAnswer = null
        _state.update {
            it.copy(
                pendingTransfer = null,
                transferConflict = null,
                operationLabel = null,
                isMutating = false,
            )
        }
    }

    fun resolveConflict(resolution: ConflictResolution, applyToAll: Boolean) {
        val deferred = conflictAnswer ?: return
        if (!deferred.isCompleted) deferred.complete(ConflictAnswer(resolution, applyToAll))
    }

    fun pasteHere() {
        if (!requireWriteAccess("pegar elementos")) return
        val snapshot = _state.value
        val transfer = snapshot.pendingTransfer ?: return
        val tree = snapshot.treeUri ?: return
        val destination = snapshot.currentLocation ?: return
        if (!snapshot.canPasteHere || snapshot.isMutating) {
            setErrorMessage("Ese destino no es válido")
            return
        }

        invalidateLoads()
        viewModelScope.launch {
            var transferred = 0
            var skipped = 0
            var rememberedResolution: ConflictResolution? = null
            _state.update { it.copy(isMutating = true, isLoading = false, errorMessage = null) }

            try {
                transfer.entries.forEachIndexed { index, entry ->
                    val verb = if (transfer.mode == TransferMode.COPY) "Copiando" else "Moviendo"
                    _state.update {
                        it.copy(operationLabel = "$verb ${index + 1} de ${transfer.entries.size}…")
                    }

                    val existing = withContext(Dispatchers.IO) {
                        findConflict(tree, destination, entry.name)
                    }

                    var resolution: ConflictResolution? = null
                    if (existing != null) {
                        resolution = rememberedResolution
                        if (
                            resolution == null ||
                            (resolution == ConflictResolution.REPLACE && existing.documentId == entry.documentId)
                        ) {
                            val answer = awaitConflict(TransferConflict(entry, existing))
                            if (answer.resolution == ConflictResolution.CANCEL) {
                                throw TransferCancelledV53()
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
                            val unique = withContext(Dispatchers.IO) {
                                uniqueNameCaseAware(tree, destination, entry.name)
                            }
                            withContext(Dispatchers.IO) {
                                if (transfer.mode == TransferMode.COPY) {
                                    repo.copyEntry(tree, entry, destination, unique)
                                } else {
                                    repo.moveEntry(
                                        tree,
                                        entry,
                                        transfer.sourceParent,
                                        destination,
                                        unique,
                                    )
                                }
                            }
                            transferred++
                        }
                        ConflictResolution.REPLACE -> {
                            val target = checkNotNull(existing)
                            require(target.documentId != entry.documentId) {
                                "No se puede reemplazar un elemento consigo mismo"
                            }
                            withContext(Dispatchers.IO) {
                                repo.replaceEntry(
                                    treeUri = tree,
                                    source = entry,
                                    existing = target,
                                    sourceParent = transfer.sourceParent,
                                    destination = destination,
                                    move = transfer.mode == TransferMode.MOVE,
                                )
                            }
                            transferred++
                        }
                        ConflictResolution.CANCEL -> throw TransferCancelledV53()
                        null -> {
                            withContext(Dispatchers.IO) {
                                if (transfer.mode == TransferMode.COPY) {
                                    repo.copyEntry(tree, entry, destination)
                                } else {
                                    repo.moveEntry(
                                        tree,
                                        entry,
                                        transfer.sourceParent,
                                        destination,
                                    )
                                }
                            }
                            transferred++
                        }
                    }
                }

                val action = if (transfer.mode == TransferMode.COPY) "copiados" else "movidos"
                val summary = buildString {
                    append("$transferred ${if (transferred == 1) "elemento" else "elementos"} $action")
                    if (skipped > 0) append(" · $skipped omitidos")
                }
                _state.update {
                    it.copy(
                        isMutating = false,
                        operationLabel = null,
                        pendingTransfer = null,
                        transferConflict = null,
                        message = summary,
                    )
                }
                refresh()
            } catch (_: TransferCancelledV53) {
                _state.update {
                    it.copy(
                        isMutating = false,
                        operationLabel = null,
                        pendingTransfer = null,
                        transferConflict = null,
                        message = if (transferred > 0 || skipped > 0) {
                            "Operación cancelada · $transferred completados · $skipped omitidos"
                        } else {
                            "Operación cancelada"
                        },
                    )
                }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isMutating = false,
                        operationLabel = null,
                        pendingTransfer = null,
                        transferConflict = null,
                        errorMessage = buildString {
                            if (transferred > 0 || skipped > 0) {
                                append("La operación se detuvo: $transferred completados, $skipped omitidos. ")
                            }
                            append(t.message?.takeIf(String::isNotBlank) ?: "No se pudo completar la operación")
                        },
                    )
                }
                refresh()
            } finally {
                conflictAnswer = null
            }
        }
    }

    fun requestWriteAction(action: String): Boolean = requireWriteAccess(action)

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun loadLocation(stack: List<BrowserLocation>, clearEntries: Boolean) {
        val tree = _state.value.treeUri ?: return
        val location = stack.lastOrNull() ?: return
        val generation = ++loadGeneration
        loadJob?.cancel()

        _state.update {
            it.copy(
                navigationStack = stack,
                entries = if (clearEntries) emptyList() else it.entries,
                isLoading = true,
                errorMessage = null,
                selectedIds = emptySet(),
                isSelectionMode = false,
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { repo.listChildren(tree, location) }
                if (generation != loadGeneration) return@launch
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        entries = list,
                        selectedIds = emptySet(),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (generation == loadGeneration) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            entries = emptyList(),
                            errorMessage = t.message?.takeIf(String::isNotBlank)
                                ?: "No se pudo leer esta carpeta",
                        )
                    }
                }
            }
        }
    }

    private suspend fun awaitConflict(conflict: TransferConflict): ConflictAnswer {
        val deferred = CompletableDeferred<ConflictAnswer>()
        conflictAnswer = deferred
        _state.update { it.copy(transferConflict = conflict) }
        return deferred.await().also {
            conflictAnswer = null
            _state.update { state -> state.copy(transferConflict = null) }
        }
    }

    private fun mutate(success: String, block: suspend (BrowserUiState) -> Unit) {
        if (!requireWriteAccess("modificar esta ubicación")) return
        if (_state.value.isMutating || _state.value.isSelectionMode) return
        val snapshot = _state.value
        invalidateLoads()

        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, isLoading = false, errorMessage = null) }
            runCatching { withContext(Dispatchers.IO) { block(snapshot) } }
                .onSuccess {
                    _state.update { it.copy(isMutating = false, message = success) }
                    refresh()
                }
                .onFailure {
                    _state.update { it.copy(isMutating = false) }
                    setError(it, "No se pudo completar la operación")
                }
        }
    }

    private fun findConflict(
        treeUri: Uri,
        destination: BrowserLocation,
        name: String,
    ): StorageEntry? {
        val children = repo.listChildren(treeUri, destination)
        children.firstOrNull { it.name == name }?.let { return it }
        val folded = name.lowercase(Locale.ROOT)
        return children.firstOrNull { it.name.lowercase(Locale.ROOT) == folded }
    }

    private fun uniqueNameCaseAware(
        treeUri: Uri,
        destination: BrowserLocation,
        originalName: String,
    ): String {
        val existing = repo.listChildren(treeUri, destination)
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

    private fun invalidateLoads() {
        loadGeneration++
        loadJob?.cancel()
        loadJob = null
    }

    private fun requireWriteAccess(action: String): Boolean {
        if (_state.value.treeUri == null) return false
        if (_canWrite.value) return true
        setErrorMessage("Ubicación de solo lectura: no se puede $action")
        return false
    }

    private fun hasPersistedWritePermission(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    private fun validName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty()) { "Escribe un nombre" }
        require(name != "." && name != ".." && !name.contains('/')) {
            "Ese nombre no es válido"
        }
        return name
    }

    private fun setError(t: Throwable, fallback: String) {
        _state.update {
            it.copy(errorMessage = t.message?.takeIf(String::isNotBlank) ?: fallback)
        }
    }

    private fun setErrorMessage(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }
}

private class TransferCancelledV53 : Exception()
