package dev.soyunomas.fluxfiles

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluxTheme {
                val vm: BrowserViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                var about by remember { mutableStateOf(false) }
                if (about) {
                    AboutScreen { about = false }
                } else {
                    BrowserScreen(
                        state = state,
                        onTreeSelected = vm::selectTree,
                        onDirectoryClick = vm::openDirectory,
                        onNavigateUp = { vm.navigateUp() },
                        onRefresh = vm::refresh,
                        onErrorConsumed = vm::consumeError,
                        onMessageConsumed = vm::consumeMessage,
                        onOpenAbout = { about = true },
                        onCreateFolder = vm::createFolder,
                        onRename = vm::rename,
                        onDelete = vm::delete,
                        onEnterSelection = vm::enterSelection,
                        onStartSelection = vm::startSelection,
                        onToggleSelection = vm::toggleSelection,
                        onSelectAll = vm::selectAll,
                        onClearSelection = vm::clearSelection,
                        onBeginCopy = { vm.beginTransfer(TransferMode.COPY) },
                        onBeginMove = { vm.beginTransfer(TransferMode.MOVE) },
                        onCancelTransfer = vm::cancelTransfer,
                        onPasteHere = vm::pasteHere,
                    )
                }
            }
        }
    }
}

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

data class BrowserLocation(val documentId: String, val name: String)

enum class TransferMode { COPY, MOVE }

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
            if (current.documentId == transfer.sourceParent.documentId) return false
            val pathIds = navigationStack.mapTo(hashSetOf()) { it.documentId }
            return transfer.entries.none { it.isDirectory && it.documentId in pathIds }
        }
}

class StorageRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }

    fun rootLocation(treeUri: Uri): BrowserLocation {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        return BrowserLocation(id, queryDisplayName(documentUri(treeUri, id)) ?: "Ubicación")
    }

    fun listChildren(treeUri: Uri, location: BrowserLocation): List<StorageEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, location.documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val result = mutableListOf<StorageEntry>()
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
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

    fun createFolder(treeUri: Uri, parent: BrowserLocation, name: String) {
        checkNotNull(
            DocumentsContract.createDocument(
                resolver,
                documentUri(treeUri, parent.documentId),
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
        ) { "Android no pudo crear la carpeta" }
    }

    fun rename(entry: StorageEntry, name: String) {
        checkNotNull(DocumentsContract.renameDocument(resolver, entry.uri, name)) {
            "Android no pudo renombrar el elemento"
        }
    }

    fun delete(entry: StorageEntry) {
        check(DocumentsContract.deleteDocument(resolver, entry.uri)) {
            "Android no pudo eliminar el elemento"
        }
    }

    fun ensureNoTopLevelConflicts(
        treeUri: Uri,
        destination: BrowserLocation,
        sources: List<StorageEntry>,
    ) {
        val existing = listChildren(treeUri, destination).mapTo(hashSetOf()) { it.name }
        val conflict = sources.firstOrNull { it.name in existing }
        require(conflict == null) {
            "Ya existe “${conflict?.name}” en la carpeta de destino"
        }
    }

    fun copyEntry(treeUri: Uri, source: StorageEntry, destination: BrowserLocation) {
        copyEntryRecursive(treeUri, source, destination)
    }

    fun moveEntry(
        treeUri: Uri,
        source: StorageEntry,
        sourceParent: BrowserLocation,
        destination: BrowserLocation,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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

        copyEntryRecursive(treeUri, source, destination)
        check(DocumentsContract.deleteDocument(resolver, source.uri)) {
            "Se copió “${source.name}”, pero no se pudo eliminar el original"
        }
    }

    fun persistTreePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
            .getOrElse { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun hasPersistedPermission(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private fun copyEntryRecursive(
        treeUri: Uri,
        source: StorageEntry,
        destination: BrowserLocation,
    ): Uri {
        val created = checkNotNull(
            DocumentsContract.createDocument(
                resolver,
                documentUri(treeUri, destination.documentId),
                source.mimeType,
                source.name,
            )
        ) { "No se pudo crear “${source.name}” en el destino" }

        try {
            if (source.isDirectory) {
                val createdId = DocumentsContract.getDocumentId(created)
                val createdLocation = BrowserLocation(createdId, source.name)
                val children = listChildren(treeUri, BrowserLocation(source.documentId, source.name))
                children.forEach { child -> copyEntryRecursive(treeUri, child, createdLocation) }
            } else {
                val input = checkNotNull(resolver.openInputStream(source.uri)) {
                    "No se pudo leer “${source.name}”"
                }
                val output = checkNotNull(resolver.openOutputStream(created, "w")) {
                    "No se pudo escribir “${source.name}”"
                }
                input.use { sourceStream ->
                    output.use { targetStream -> sourceStream.copyTo(targetStream) }
                }
            }
            return created
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw t
        }
    }

    private fun documentUri(treeUri: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun queryDisplayName(uri: Uri): String? =
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
}

class BrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)
    private val prefs = app.getSharedPreferences("flux_files", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(BrowserUiState())
    val state = _state.asStateFlow()

    init {
        prefs.getString("tree_uri", null)
            ?.let(Uri::parse)
            ?.takeIf(repo::hasPersistedPermission)
            ?.let { selectTree(it, false) }
    }

    fun selectTree(uri: Uri, persist: Boolean = true) = viewModelScope.launch {
        if (_state.value.pendingTransfer != null || _state.value.isMutating) return@launch
        runCatching {
            if (persist) repo.persistTreePermission(uri)
            uri to withContext(Dispatchers.IO) { repo.rootLocation(uri) }
        }.onSuccess { (tree, root) ->
            prefs.edit().putString("tree_uri", tree.toString()).apply()
            _state.update {
                it.copy(
                    treeUri = tree,
                    navigationStack = listOf(root),
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    errorMessage = null,
                )
            }
            refresh()
        }.onFailure { setError(it, "No se pudo abrir esa ubicación") }
    }

    fun openDirectory(entry: StorageEntry) {
        if (!entry.isDirectory || _state.value.isMutating || _state.value.isSelectionMode) return
        _state.update {
            it.copy(navigationStack = it.navigationStack + BrowserLocation(entry.documentId, entry.name))
        }
        refresh()
    }

    fun navigateUp(): Boolean {
        if (_state.value.isMutating || !_state.value.canNavigateUp) return false
        _state.update { it.copy(navigationStack = it.navigationStack.dropLast(1)) }
        refresh()
        return true
    }

    fun refresh() {
        val snapshot = _state.value
        val tree = snapshot.treeUri ?: return
        val location = snapshot.currentLocation ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { withContext(Dispatchers.IO) { repo.listChildren(tree, location) } }
                .onSuccess { list ->
                    _state.update { current ->
                        val validIds = list.mapTo(hashSetOf()) { it.documentId }
                        current.copy(
                            isLoading = false,
                            entries = list,
                            selectedIds = current.selectedIds.intersect(validIds),
                        )
                    }
                }
                .onFailure {
                    _state.update { current -> current.copy(isLoading = false) }
                    setError(it, "No se pudo leer esta carpeta")
                }
        }
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
        _state.update { it.copy(selectedIds = it.entries.mapTo(hashSetOf()) { entry -> entry.documentId }) }
    }

    fun clearSelection() {
        if (_state.value.isMutating) return
        _state.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
    }

    fun beginTransfer(mode: TransferMode) {
        val snapshot = _state.value
        if (!snapshot.isSelectionMode || snapshot.isMutating) return
        val selected = snapshot.selectedEntries
        if (selected.isEmpty()) {
            setError(IllegalArgumentException("Selecciona al menos un elemento"), "Selecciona al menos un elemento")
            return
        }
        val sourceParent = snapshot.currentLocation ?: return
        _state.update {
            it.copy(
                isSelectionMode = false,
                selectedIds = emptySet(),
                pendingTransfer = PendingTransfer(selected, sourceParent, mode),
                message = "Elige la carpeta de destino",
            )
        }
    }

    fun cancelTransfer() {
        if (_state.value.isMutating) return
        _state.update { it.copy(pendingTransfer = null, operationLabel = null) }
    }

    fun pasteHere() {
        val snapshot = _state.value
        val transfer = snapshot.pendingTransfer ?: return
        val tree = snapshot.treeUri ?: return
        val destination = snapshot.currentLocation ?: return
        if (!snapshot.canPasteHere || snapshot.isMutating) {
            setError(
                IllegalArgumentException("Elige una carpeta distinta y que no esté dentro de una carpeta seleccionada"),
                "Ese destino no es válido",
            )
            return
        }

        viewModelScope.launch {
            var completed = 0
            _state.update { it.copy(isMutating = true, errorMessage = null) }
            try {
                withContext(Dispatchers.IO) {
                    repo.ensureNoTopLevelConflicts(tree, destination, transfer.entries)
                }
                transfer.entries.forEachIndexed { index, entry ->
                    val verb = if (transfer.mode == TransferMode.COPY) "Copiando" else "Moviendo"
                    _state.update {
                        it.copy(operationLabel = "$verb ${index + 1} de ${transfer.entries.size}…")
                    }
                    withContext(Dispatchers.IO) {
                        when (transfer.mode) {
                            TransferMode.COPY -> repo.copyEntry(tree, entry, destination)
                            TransferMode.MOVE -> repo.moveEntry(
                                tree,
                                entry,
                                transfer.sourceParent,
                                destination,
                            )
                        }
                    }
                    completed++
                }

                val count = transfer.entries.size
                val action = if (transfer.mode == TransferMode.COPY) "copiados" else "movidos"
                _state.update {
                    it.copy(
                        isMutating = false,
                        operationLabel = null,
                        pendingTransfer = null,
                        message = "$count ${if (count == 1) "elemento" else "elementos"} $action",
                    )
                }
                refresh()
            } catch (t: Throwable) {
                val prefix = if (completed > 0) {
                    "La operación se detuvo después de $completed de ${transfer.entries.size}. "
                } else {
                    ""
                }
                _state.update {
                    it.copy(
                        isMutating = false,
                        operationLabel = null,
                        pendingTransfer = null,
                        errorMessage = prefix + (t.message?.takeIf(String::isNotBlank)
                            ?: "No se pudo completar la operación"),
                    )
                }
                refresh()
            }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun mutate(success: String, block: suspend (BrowserUiState) -> Unit) {
        if (_state.value.isMutating || _state.value.isSelectionMode) return
        val snapshot = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, errorMessage = null) }
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(
    state: BrowserUiState,
    onTreeSelected: (Uri) -> Unit,
    onDirectoryClick: (StorageEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onErrorConsumed: () -> Unit,
    onMessageConsumed: () -> Unit,
    onOpenAbout: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (StorageEntry, String) -> Unit,
    onDelete: (StorageEntry) -> Unit,
    onEnterSelection: () -> Unit,
    onStartSelection: (StorageEntry) -> Unit,
    onToggleSelection: (StorageEntry) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBeginCopy: () -> Unit,
    onBeginMove: () -> Unit,
    onCancelTransfer: () -> Unit,
    onPasteHere: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var appMenu by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<StorageEntry?>(null) }
    var delete by remember { mutableStateOf<StorageEntry?>(null) }
    var itemMenu by remember { mutableStateOf<StorageEntry?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(onTreeSelected)
    }

    BackHandler(
        enabled = state.isSelectionMode || state.pendingTransfer != null || state.canNavigateUp
    ) {
        when {
            state.isSelectionMode -> onClearSelection()
            state.canNavigateUp -> onNavigateUp()
            state.pendingTransfer != null -> onCancelTransfer()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            onErrorConsumed()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (
                state.treeUri != null &&
                !state.isMutating &&
                !state.isSelectionMode
            ) {
                FloatingActionButton(onClick = { create = true }) {
                    Icon(Icons.Default.CreateNewFolder, "Nueva carpeta")
                }
            }
        },
        bottomBar = {
            state.pendingTransfer?.let { transfer ->
                TransferDestinationBar(
                    transfer = transfer,
                    canPasteHere = state.canPasteHere,
                    isMutating = state.isMutating,
                    operationLabel = state.operationLabel,
                    onCancel = onCancelTransfer,
                    onPasteHere = onPasteHere,
                )
            }
        },
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            if (state.selectedIds.isEmpty()) "Seleccionar" else "${state.selectedIds.size} seleccionados"
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection, enabled = !state.isMutating) {
                            Icon(Icons.Default.Close, "Salir de selección")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onBeginCopy,
                            enabled = state.selectedIds.isNotEmpty() && !state.isMutating,
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copiar")
                        }
                        IconButton(
                            onClick = onBeginMove,
                            enabled = state.selectedIds.isNotEmpty() && !state.isMutating,
                        ) {
                            Icon(Icons.Default.DriveFileMove, "Mover")
                        }
                        IconButton(onClick = onSelectAll, enabled = state.entries.isNotEmpty()) {
                            Icon(Icons.Default.SelectAll, "Seleccionar todo")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.currentLocation?.name ?: "Flux Files",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.currentLocation != null) {
                                val subtitle = state.pendingTransfer?.let {
                                    "Destino para ${it.entries.size} ${if (it.entries.size == 1) "elemento" else "elementos"}"
                                } ?: "${state.entries.size} elementos"
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (state.canNavigateUp) {
                            IconButton(onClick = onNavigateUp, enabled = !state.isMutating) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                            }
                        }
                    },
                    actions = {
                        if (state.treeUri != null) {
                            IconButton(onClick = onRefresh, enabled = !state.isMutating) {
                                Icon(Icons.Default.Refresh, "Actualizar")
                            }
                        }
                        if (state.pendingTransfer == null) {
                            IconButton(onClick = { appMenu = true }, enabled = !state.isMutating) {
                                Icon(Icons.Default.MoreVert, "Más opciones")
                            }
                            DropdownMenu(
                                expanded = appMenu,
                                onDismissRequest = { appMenu = false },
                            ) {
                                if (state.entries.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Seleccionar") },
                                        onClick = {
                                            appMenu = false
                                            onEnterSelection()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Checklist, null) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(if (state.treeUri == null) "Elegir ubicación" else "Cambiar ubicación")
                                    },
                                    onClick = {
                                        appMenu = false
                                        picker.launch(state.treeUri)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Storage, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Acerca de") },
                                    onClick = {
                                        appMenu = false
                                        onOpenAbout()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.treeUri == null -> WelcomeState { picker.launch(null) }
                state.isLoading && state.entries.isEmpty() -> CenterMessage("Leyendo carpeta…")
                state.entries.isEmpty() -> EmptyState(onRefresh) { create = true }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.documentId }) { entry ->
                        val selected = entry.documentId in state.selectedIds
                        Box {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        entry.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        entrySupportingText(entry),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingContent = {
                                    if (state.isSelectionMode) {
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = { onToggleSelection(entry) },
                                            enabled = !state.isMutating,
                                        )
                                    } else {
                                        Icon(
                                            if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                            null,
                                            Modifier.size(32.dp),
                                            tint = if (entry.isDirectory) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                },
                                trailingContent = {
                                    if (!state.isSelectionMode && state.pendingTransfer == null) {
                                        IconButton(
                                            onClick = { itemMenu = entry },
                                            enabled = !state.isMutating,
                                        ) {
                                            Icon(Icons.Default.MoreVert, "Opciones")
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        enabled = !state.isMutating,
                                        onClick = {
                                            when {
                                                state.isSelectionMode -> onToggleSelection(entry)
                                                entry.isDirectory -> onDirectoryClick(entry)
                                                else -> openFile(context, entry)
                                            }
                                        },
                                        onLongClick = {
                                            if (state.pendingTransfer == null) onStartSelection(entry)
                                        },
                                    )
                                    .padding(vertical = 4.dp),
                            )

                            if (state.pendingTransfer == null) {
                                DropdownMenu(
                                    expanded = itemMenu?.documentId == entry.documentId,
                                    onDismissRequest = { itemMenu = null },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Renombrar") },
                                        onClick = {
                                            itemMenu = null
                                            rename = entry
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Eliminar") },
                                        onClick = {
                                            itemMenu = null
                                            delete = entry
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    )
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
            }

            if ((state.isLoading && state.entries.isNotEmpty()) || state.isMutating) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }
    }

    if (create) {
        NameDialog(
            title = "Nueva carpeta",
            initial = "",
            confirm = "Crear",
            onDismiss = { create = false },
        ) {
            create = false
            onCreateFolder(it)
        }
    }

    rename?.let { entry ->
        NameDialog(
            title = "Renombrar",
            initial = entry.name,
            confirm = "Guardar",
            onDismiss = { rename = null },
        ) {
            rename = null
            onRename(entry, it)
        }
    }

    delete?.let { entry ->
        AlertDialog(
            onDismissRequest = { delete = null },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text(if (entry.isDirectory) "¿Eliminar carpeta?" else "¿Eliminar archivo?") },
            text = {
                Text(
                    if (entry.isDirectory) {
                        "Se eliminará “${entry.name}” y posiblemente todo su contenido. Esta acción no tiene deshacer en Flux Files."
                    } else {
                        "Se eliminará “${entry.name}”. Esta acción no tiene deshacer en Flux Files."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    delete = null
                    onDelete(entry)
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { delete = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun TransferDestinationBar(
    transfer: PendingTransfer,
    canPasteHere: Boolean,
    isMutating: Boolean,
    operationLabel: String?,
    onCancel: () -> Unit,
    onPasteHere: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                operationLabel ?: if (transfer.mode == TransferMode.COPY) {
                    "Copiar ${transfer.entries.size} ${if (transfer.entries.size == 1) "elemento" else "elementos"}"
                } else {
                    "Mover ${transfer.entries.size} ${if (transfer.entries.size == 1) "elemento" else "elementos"}"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (!canPasteHere && !isMutating) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Elige otra carpeta como destino.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel, enabled = !isMutating) {
                    Text("Cancelar")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onPasteHere,
                    enabled = canPasteHere && !isMutating,
                ) {
                    Icon(
                        if (transfer.mode == TransferMode.COPY) Icons.Default.ContentCopy else Icons.Default.DriveFileMove,
                        null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (transfer.mode == TransferMode.COPY) "Copiar aquí" else "Mover aquí")
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.trim().isNotEmpty(),
            ) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun WelcomeState(onChoose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.FolderOpen,
            null,
            Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Elige dónde quieres trabajar",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Flux Files usa el selector de Android. Tú decides qué carpeta puede leer y modificar.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onChoose) {
            Icon(Icons.Default.Storage, null)
            Spacer(Modifier.width(8.dp))
            Text("Elegir carpeta")
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit, onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Folder,
            null,
            Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("Esta carpeta está vacía", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "No hay archivos o carpetas que mostrar.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh) { Text("Actualizar") }
            Button(onClick = onCreate) {
                Icon(Icons.Default.CreateNewFolder, null)
                Spacer(Modifier.width(8.dp))
                Text("Nueva carpeta")
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acerca de") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text(
                "Flux Files",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Versión ${BuildConfig.VERSION_NAME}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Implementación propia en Kotlin y Jetpack Compose, diseñada alrededor de navegación clara, selección explícita y operaciones de archivos predecibles."
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Esta versión usa Storage Access Framework (SAF): solo trabaja con ubicaciones autorizadas por el usuario.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FluxTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF80D5D5))
    } else {
        lightColorScheme(primary = Color(0xFF006C6D))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private fun entrySupportingText(entry: StorageEntry): String {
    if (entry.isDirectory) return "Carpeta"
    val parts = buildList {
        entry.sizeBytes?.let { add(formatBytes(it)) }
        entry.modifiedAtMillis?.takeIf { it > 0 }?.let {
            add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)))
        }
    }
    return parts.joinToString(" · ").ifEmpty { entry.mimeType.substringAfterLast('/') }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val unit = 1024.0
    val exponent = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceIn(1, 4)
    val suffix = listOf("B", "KB", "MB", "GB", "TB")[exponent]
    val value = bytes / unit.pow(exponent.toDouble())
    return if (value >= 10) {
        "%.0f %s".format(value, suffix)
    } else {
        "%.1f %s".format(value, suffix)
    }
}

private fun openFile(context: Context, entry: StorageEntry) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(entry.uri, entry.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No hay una aplicación para abrir este archivo", Toast.LENGTH_SHORT).show()
    }
}
