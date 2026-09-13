package dev.soyunomas.fluxfiles

import android.app.Application
import android.content.Context
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

class BrowserViewModelV53(app: Application) : AndroidViewModel(app) {
    private val backends = AndroidStorageServices.registry(app)
    private val prefs = app.getSharedPreferences("flux_files", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(BrowserUiState())
    val state = _state.asStateFlow()

    private val _canWrite = MutableStateFlow(false)
    val canWrite = _canWrite.asStateFlow()

    private var conflictAnswer: CompletableDeferred<ConflictAnswer>? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        restoreRoot()
            ?.takeIf { root ->
                backends.supports(root.backend) &&
                    runCatching { backends.fileSystemFor(root).hasRootAccess(root) }.getOrDefault(false)
            }
            ?.let { selectRoot(it, persist = false) }
    }

    fun selectRoot(root: StorageRootRef, persist: Boolean = true) {
        if (_state.value.pendingTransfer != null || _state.value.isMutating) return

        val generation = ++loadGeneration
        loadJob?.cancel()
        _state.update {
            it.copy(
                storageRoot = root,
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
                val fileSystem = backends.fileSystemFor(root)
                if (persist) withContext(Dispatchers.IO) { fileSystem.persistRootAccess(root) }
                val rootLocation = withContext(Dispatchers.IO) { fileSystem.rootLocation(root) }
                val entries = withContext(Dispatchers.IO) { fileSystem.listChildren(rootLocation) }
                if (generation != loadGeneration) return@launch

                saveRoot(root)
                _canWrite.value = withContext(Dispatchers.IO) { fileSystem.canWrite(root) }
                _state.update {
                    it.copy(
                        storageRoot = root,
                        navigationStack = listOf(rootLocation),
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
            stack = snapshot.navigationStack + BrowserLocation(entry.ref, entry.name),
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

    fun createFolder(name: String) = mutate("Carpeta creada") { fileSystem, snapshot ->
        fileSystem.createFolder(snapshot.currentLocation ?: error("No hay carpeta abierta"), validName(name))
    }

    fun rename(entry: StorageEntry, name: String) = mutate("Renombrado correctamente") { fileSystem, _ ->
        require(entry.ref.backend == fileSystem.backendId) { "El elemento pertenece a otro backend" }
        fileSystem.rename(entry, validName(name))
    }

    fun delete(entry: StorageEntry) = mutate(
        if (entry.isDirectory) "Carpeta eliminada" else "Archivo eliminado"
    ) { fileSystem, _ ->
        require(entry.ref.backend == fileSystem.backendId) { "El elemento pertenece a otro backend" }
        fileSystem.delete(entry)
    }

    fun enterSelection() {
        if (_state.value.pendingTransfer != null || _state.value.isMutating) return
        _state.update { it.copy(isSelectionMode = true, selectedIds = emptySet()) }
    }

    fun startSelection(entry: StorageEntry) {
        if (_state.value.pendingTransfer != null || _state.value.isMutating) return
        _state.update {
            it.copy(isSelectionMode = true, selectedIds = it.selectedIds + entry.ref.opaqueId)
        }
    }

    fun toggleSelection(entry: StorageEntry) {
        if (!_state.value.isSelectionMode || _state.value.isMutating) return
        _state.update {
            val id = entry.ref.opaqueId
            val next = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = next)
        }
    }

    fun selectAll() {
        if (!_state.value.isSelectionMode || _state.value.isMutating) return
        _state.update {
            it.copy(selectedIds = it.entries.mapTo(hashSetOf()) { entry -> entry.ref.opaqueId })
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
                message = if (mode == TransferMode.COPY && sourceParent.ref == it.currentLocation?.ref) {
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
        val destination = snapshot.currentLocation ?: return
        if (!snapshot.canPasteHere || snapshot.isMutating) {
            setErrorMessage("Ese destino no es válido")
            return
        }

        invalidateLoads()
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, isLoading = false, errorMessage = null) }

            try {
                require(transfer.entries.all { it.ref.backend == destination.ref.backend }) {
                    "Las transferencias entre backends todavía no están habilitadas"
                }
                val transferEngine = TransferOperationEngine(backends.fileSystemFor(destination))
                when (
                    val result = transferEngine.execute(
                        transfer = transfer,
                        destination = destination,
                        onProgress = { progress ->
                            _state.update { it.copy(operationLabel = progress.label) }
                        },
                        onConflict = ::awaitConflict,
                    )
                ) {
                    is TransferExecutionResult.Completed -> {
                        val action = if (transfer.mode == TransferMode.COPY) "copiados" else "movidos"
                        val summary = buildString {
                            append("${result.transferred} ${if (result.transferred == 1) "elemento" else "elementos"} $action")
                            if (result.skipped > 0) append(" · ${result.skipped} omitidos")
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
                    }

                    is TransferExecutionResult.Cancelled -> {
                        _state.update {
                            it.copy(
                                isMutating = false,
                                operationLabel = null,
                                pendingTransfer = null,
                                transferConflict = null,
                                message = if (result.transferred > 0 || result.skipped > 0) {
                                    "Operación cancelada · ${result.transferred} completados · ${result.skipped} omitidos"
                                } else {
                                    "Operación cancelada"
                                },
                            )
                        }
                    }
                }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: TransferExecutionException) {
                _state.update {
                    it.copy(
                        isMutating = false,
                        operationLabel = null,
                        pendingTransfer = null,
                        transferConflict = null,
                        errorMessage = buildString {
                            if (failure.transferred > 0 || failure.skipped > 0) {
                                append("La operación se detuvo: ${failure.transferred} completados, ${failure.skipped} omitidos. ")
                            }
                            append(
                                failure.cause?.message?.takeIf(String::isNotBlank)
                                    ?: failure.message?.takeIf(String::isNotBlank)
                                    ?: "No se pudo completar la operación"
                            )
                        },
                    )
                }
                refresh()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isMutating = false,
                        operationLabel = null,
                        pendingTransfer = null,
                        transferConflict = null,
                        errorMessage = t.message?.takeIf(String::isNotBlank)
                            ?: "No se pudo completar la operación",
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
                val fileSystem = backends.fileSystemFor(location)
                val list = withContext(Dispatchers.IO) { fileSystem.listChildren(location) }
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

    private fun mutate(
        success: String,
        block: suspend (StorageFileSystem, BrowserUiState) -> Unit,
    ) {
        if (!requireWriteAccess("modificar esta ubicación")) return
        if (_state.value.isMutating || _state.value.isSelectionMode) return
        val snapshot = _state.value
        val location = snapshot.currentLocation ?: return
        invalidateLoads()

        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, isLoading = false, errorMessage = null) }
            val fileSystem = runCatching { backends.fileSystemFor(location) }
                .getOrElse {
                    _state.update { it.copy(isMutating = false) }
                    setError(it, "Backend no disponible")
                    return@launch
                }
            runCatching { withContext(Dispatchers.IO) { block(fileSystem, snapshot) } }
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

    private fun invalidateLoads() {
        loadGeneration++
        loadJob?.cancel()
        loadJob = null
    }

    private fun requireWriteAccess(action: String): Boolean {
        if (_state.value.storageRoot == null) return false
        if (_canWrite.value) return true
        setErrorMessage("Ubicación de solo lectura: no se puede $action")
        return false
    }

    private fun validName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty()) { "Escribe un nombre" }
        require(name != "." && name != ".." && !name.contains('/')) { "Ese nombre no es válido" }
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

    private fun restoreRoot(): StorageRootRef? {
        val backend = prefs.getString(PREF_ROOT_BACKEND, null)
        val opaqueId = prefs.getString(PREF_ROOT_ID, null)
        if (!backend.isNullOrBlank() && !opaqueId.isNullOrBlank()) {
            return StorageRootRef(StorageBackendId(backend), opaqueId)
        }

        // One-time migration from the pre-0.5.10 SAF-only preference.
        return prefs.getString(LEGACY_TREE_URI, null)
            ?.takeIf(String::isNotBlank)
            ?.let { StorageRootRef(backends.defaultBackendId, it) }
    }

    private fun saveRoot(root: StorageRootRef) {
        prefs.edit()
            .putString(PREF_ROOT_BACKEND, root.backend.value)
            .putString(PREF_ROOT_ID, root.opaqueId)
            .remove(LEGACY_TREE_URI)
            .apply()
    }

    companion object {
        private const val PREF_ROOT_BACKEND = "storage_root_backend"
        private const val PREF_ROOT_ID = "storage_root_id"
        private const val LEGACY_TREE_URI = "tree_uri"
    }
}
