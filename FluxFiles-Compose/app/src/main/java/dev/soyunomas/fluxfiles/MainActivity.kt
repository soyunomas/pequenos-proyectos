package dev.soyunomas.fluxfiles

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import java.util.*
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
                if (about) AboutScreen { about = false } else BrowserScreen(
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
                )
            }
        }
    }
}

data class StorageEntry(val documentId: String, val uri: Uri, val name: String, val mimeType: String, val sizeBytes: Long?, val modifiedAtMillis: Long?) {
    val isDirectory get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}
data class BrowserLocation(val documentId: String, val name: String)
data class BrowserUiState(
    val treeUri: Uri? = null,
    val navigationStack: List<BrowserLocation> = emptyList(),
    val entries: List<StorageEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
) {
    val currentLocation get() = navigationStack.lastOrNull()
    val canNavigateUp get() = navigationStack.size > 1
}

class StorageRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }

    fun rootLocation(treeUri: Uri): BrowserLocation {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
        return BrowserLocation(id, queryDisplayName(uri) ?: "Ubicación")
    }

    fun listChildren(treeUri: Uri, location: BrowserLocation): List<StorageEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, location.documentId)
        val p = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val result = mutableListOf<StorageEntry>()
        resolver.query(uri, p, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                result += StorageEntry(
                    id,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    c.getString(1) ?: "Sin nombre",
                    c.getString(2) ?: "application/octet-stream",
                    if (c.isNull(3)) null else c.getLong(3),
                    if (c.isNull(4)) null else c.getLong(4),
                )
            }
        }
        return result.sortedWith { a, b -> when { a.isDirectory && !b.isDirectory -> -1; !a.isDirectory && b.isDirectory -> 1; else -> collator.compare(a.name, b.name) } }
    }

    fun createFolder(treeUri: Uri, parent: BrowserLocation, name: String) {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parent.documentId)
        checkNotNull(DocumentsContract.createDocument(resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name)) { "Android no pudo crear la carpeta" }
    }
    fun rename(entry: StorageEntry, name: String) { checkNotNull(DocumentsContract.renameDocument(resolver, entry.uri, name)) { "Android no pudo renombrar el elemento" } }
    fun delete(entry: StorageEntry) { check(DocumentsContract.deleteDocument(resolver, entry.uri)) { "Android no pudo eliminar el elemento" } }

    fun persistTreePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, flags) }.getOrElse { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    fun hasPersistedPermission(uri: Uri) = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    private fun queryDisplayName(uri: Uri) = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
}

class BrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StorageRepository(app)
    private val prefs = app.getSharedPreferences("flux_files", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(BrowserUiState())
    val state = _state.asStateFlow()

    init { prefs.getString("tree_uri", null)?.let(Uri::parse)?.takeIf(repo::hasPersistedPermission)?.let { selectTree(it, false) } }

    fun selectTree(uri: Uri, persist: Boolean = true) = viewModelScope.launch {
        runCatching {
            if (persist) repo.persistTreePermission(uri)
            uri to withContext(Dispatchers.IO) { repo.rootLocation(uri) }
        }.onSuccess { (tree, root) ->
            prefs.edit().putString("tree_uri", tree.toString()).apply()
            _state.update { it.copy(treeUri = tree, navigationStack = listOf(root), errorMessage = null) }
            refresh()
        }.onFailure { setError(it, "No se pudo abrir esa ubicación") }
    }

    fun openDirectory(e: StorageEntry) { if (e.isDirectory) { _state.update { it.copy(navigationStack = it.navigationStack + BrowserLocation(e.documentId, e.name)) }; refresh() } }
    fun navigateUp(): Boolean { if (!_state.value.canNavigateUp) return false; _state.update { it.copy(navigationStack = it.navigationStack.dropLast(1)) }; refresh(); return true }

    fun refresh() {
        val s = _state.value; val tree = s.treeUri ?: return; val loc = s.currentLocation ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { withContext(Dispatchers.IO) { repo.listChildren(tree, loc) } }
                .onSuccess { list -> _state.update { it.copy(isLoading = false, entries = list) } }
                .onFailure { _state.update { it.copy(isLoading = false) }; setError(it, "No se pudo leer esta carpeta") }
        }
    }

    fun createFolder(name: String) = mutate("Carpeta creada") { s -> repo.createFolder(s.treeUri ?: error("No hay ubicación abierta"), s.currentLocation ?: error("No hay carpeta abierta"), validName(name)) }
    fun rename(e: StorageEntry, name: String) = mutate("Renombrado correctamente") { repo.rename(e, validName(name)) }
    fun delete(e: StorageEntry) = mutate(if (e.isDirectory) "Carpeta eliminada" else "Archivo eliminado") { repo.delete(e) }
    fun consumeError() = _state.update { it.copy(errorMessage = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun mutate(success: String, block: suspend (BrowserUiState) -> Unit) {
        if (_state.value.isMutating) return
        val snapshot = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, errorMessage = null) }
            runCatching { withContext(Dispatchers.IO) { block(snapshot) } }
                .onSuccess { _state.update { it.copy(isMutating = false, message = success) }; refresh() }
                .onFailure { _state.update { it.copy(isMutating = false) }; setError(it, "No se pudo completar la operación") }
        }
    }
    private fun validName(raw: String): String { val n = raw.trim(); require(n.isNotEmpty()) { "Escribe un nombre" }; require(n != "." && n != ".." && !n.contains('/')) { "Ese nombre no es válido" }; return n }
    private fun setError(t: Throwable, fallback: String) = _state.update { it.copy(errorMessage = t.message?.takeIf(String::isNotBlank) ?: fallback) }
}

@OptIn(ExperimentalMaterial3Api::class)
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
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var appMenu by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<StorageEntry?>(null) }
    var delete by remember { mutableStateOf<StorageEntry?>(null) }
    var itemMenu by remember { mutableStateOf<StorageEntry?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(onTreeSelected) }

    BackHandler(enabled = state.canNavigateUp) { onNavigateUp() }
    LaunchedEffect(state.errorMessage) { state.errorMessage?.let { snackbar.showSnackbar(it); onErrorConsumed() } }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); onMessageConsumed() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { if (state.treeUri != null && !state.isMutating) FloatingActionButton(onClick = { create = true }) { Icon(Icons.Default.CreateNewFolder, "Nueva carpeta") } },
        topBar = { TopAppBar(
            title = { Column { Text(state.currentLocation?.name ?: "Flux Files", maxLines = 1, overflow = TextOverflow.Ellipsis); if (state.currentLocation != null) Text("${state.entries.size} elementos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            navigationIcon = { if (state.canNavigateUp) IconButton(onClick = onNavigateUp, enabled = !state.isMutating) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            actions = {
                if (state.treeUri != null) IconButton(onClick = onRefresh, enabled = !state.isMutating) { Icon(Icons.Default.Refresh, "Actualizar") }
                IconButton(onClick = { appMenu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                DropdownMenu(appMenu, { appMenu = false }) {
                    DropdownMenuItem({ Text(if (state.treeUri == null) "Elegir ubicación" else "Cambiar ubicación") }, onClick = { appMenu = false; picker.launch(state.treeUri) }, leadingIcon = { Icon(Icons.Default.Storage, null) })
                    DropdownMenuItem({ Text("Acerca de") }, onClick = { appMenu = false; onOpenAbout() }, leadingIcon = { Icon(Icons.Default.Info, null) })
                }
            }
        ) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.treeUri == null -> WelcomeState { picker.launch(null) }
                state.isLoading && state.entries.isEmpty() -> CenterMessage("Leyendo carpeta…")
                state.entries.isEmpty() -> EmptyState(onRefresh) { create = true }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.documentId }) { e ->
                        Box {
                            ListItem(
                                headlineContent = { Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(entrySupportingText(e), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(if (e.isDirectory) Icons.Default.Folder else Icons.Default.Description, null, Modifier.size(32.dp), tint = if (e.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailingContent = { IconButton(onClick = { itemMenu = e }, enabled = !state.isMutating) { Icon(Icons.Default.MoreVert, "Opciones") } },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !state.isMutating) { if (e.isDirectory) onDirectoryClick(e) else openFile(context, e) }.padding(vertical = 4.dp),
                            )
                            DropdownMenu(itemMenu?.documentId == e.documentId, { itemMenu = null }, Modifier.align(Alignment.TopEnd)) {
                                DropdownMenuItem({ Text("Renombrar") }, onClick = { itemMenu = null; rename = e }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                                DropdownMenuItem({ Text("Eliminar") }, onClick = { itemMenu = null; delete = e }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
            }
            if ((state.isLoading && state.entries.isNotEmpty()) || state.isMutating) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (create) NameDialog("Nueva carpeta", "", "Crear", { create = false }) { create = false; onCreateFolder(it) }
    rename?.let { e -> NameDialog("Renombrar", e.name, "Guardar", { rename = null }) { rename = null; onRename(e, it) } }
    delete?.let { e -> AlertDialog(
        onDismissRequest = { delete = null }, icon = { Icon(Icons.Default.Delete, null) },
        title = { Text(if (e.isDirectory) "¿Eliminar carpeta?" else "¿Eliminar archivo?") },
        text = { Text(if (e.isDirectory) "Se eliminará “${e.name}” y posiblemente todo su contenido. Esta acción no tiene deshacer en Flux Files." else "Se eliminará “${e.name}”. Esta acción no tiene deshacer en Flux Files.") },
        confirmButton = { TextButton(onClick = { delete = null; onDelete(e) }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancelar") } }
    ) }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.trim().isNotEmpty()) { Text(confirm) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun WelcomeState(onChoose: () -> Unit) = Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Icon(Icons.Default.FolderOpen, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(24.dp))
    Text("Elige dónde quieres trabajar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(12.dp))
    Text("Flux Files usa el selector de Android. Tú decides qué carpeta puede leer y modificar.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(28.dp))
    Button(onClick = onChoose) { Icon(Icons.Default.Storage, null); Spacer(Modifier.width(8.dp)); Text("Elegir carpeta") }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit, onCreate: () -> Unit) = Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Icon(Icons.Default.Folder, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); Text("Esta carpeta está vacía", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Text("Puedes crear aquí tu primera carpeta.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(20.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = onCreate) { Text("Nueva carpeta") }; TextButton(onClick = onRefresh) { Text("Actualizar") } }
}

@Composable
private fun CenterMessage(text: String) = Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.FolderOpen, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp)); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) = Scaffold(topBar = { TopAppBar(title = { Text("Acerca de") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) }) { p -> Column(Modifier.fillMaxSize().padding(p).padding(24.dp)) { Text("Flux Files", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Versión ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)); Text("Implementación independiente en Kotlin y Jetpack Compose, diseñada con prioridad en claridad, control del usuario y navegación predecible."); Spacer(Modifier.height(16.dp)); Text("Esta versión usa Storage Access Framework (SAF): solo conserva acceso a ubicaciones autorizadas por el usuario.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun FluxTheme(content: @Composable () -> Unit) { val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF80D5D5)) else lightColorScheme(primary = Color(0xFF006C6D)); MaterialTheme(colorScheme = colors, content = content) }

private fun entrySupportingText(e: StorageEntry): String { if (e.isDirectory) return "Carpeta"; val p = buildList { e.sizeBytes?.let { add(formatBytes(it)) }; e.modifiedAtMillis?.takeIf { it > 0 }?.let { add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) } }; return p.joinToString(" · ").ifEmpty { e.mimeType.substringAfterLast('/') } }
private fun formatBytes(bytes: Long): String { if (bytes < 1024) return "$bytes B"; val unit = 1024.0; val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceIn(1, 4); val prefix = listOf("B", "KB", "MB", "GB", "TB")[exp]; val value = bytes / unit.pow(exp.toDouble()); return if (value >= 10) "%.0f %s".format(value, prefix) else "%.1f %s".format(value, prefix) }
private fun openFile(context: Context, e: StorageEntry) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(e.uri, e.mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) } }
