package dev.soyunomas.fluxfiles

import android.app.Application
import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.StateFlow
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
                    AboutScreen(onBack = { about = false })
                } else {
                    BrowserScreen(
                        state = state,
                        onTreeSelected = vm::selectTree,
                        onDirectoryClick = vm::openDirectory,
                        onNavigateUp = { vm.navigateUp() },
                        onRefresh = vm::refresh,
                        onErrorConsumed = vm::consumeError,
                        onOpenAbout = { about = true },
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

data class BrowserUiState(
    val treeUri: Uri? = null,
    val navigationStack: List<BrowserLocation> = emptyList(),
    val entries: List<StorageEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val currentLocation: BrowserLocation? get() = navigationStack.lastOrNull()
    val canNavigateUp: Boolean get() = navigationStack.size > 1
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
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val entries = mutableListOf<StorageEntry>()
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idI = cursor.getColumnIndexOrThrow(projection[0])
            val nameI = cursor.getColumnIndexOrThrow(projection[1])
            val mimeI = cursor.getColumnIndexOrThrow(projection[2])
            val sizeI = cursor.getColumnIndexOrThrow(projection[3])
            val modifiedI = cursor.getColumnIndexOrThrow(projection[4])
            while (cursor.moveToNext()) {
                val id = cursor.getString(idI)
                entries += StorageEntry(
                    documentId = id,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    name = cursor.getString(nameI) ?: "Sin nombre",
                    mimeType = cursor.getString(mimeI) ?: "application/octet-stream",
                    sizeBytes = if (cursor.isNull(sizeI)) null else cursor.getLong(sizeI),
                    modifiedAtMillis = if (cursor.isNull(modifiedI)) null else cursor.getLong(modifiedI),
                )
            }
        }
        return entries.sortedWith { a, b ->
            when {
                a.isDirectory && !b.isDirectory -> -1
                !a.isDirectory && b.isDirectory -> 1
                else -> collator.compare(a.name, b.name)
            }
        }
    }

    fun persistTreePermission(uri: Uri) {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(uri, read or write)
        } catch (_: SecurityException) {
            resolver.takePersistableUriPermission(uri, read)
        }
    }

    fun hasPersistedPermission(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private fun queryDisplayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StorageRepository(application)
    private val prefs = application.getSharedPreferences("flux_files", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    init {
        prefs.getString("tree_uri", null)?.let(Uri::parse)?.takeIf(repository::hasPersistedPermission)?.let {
            selectTree(it, persist = false)
        }
    }

    fun selectTree(uri: Uri, persist: Boolean = true) {
        viewModelScope.launch {
            runCatching {
                if (persist) repository.persistTreePermission(uri)
                val root = withContext(Dispatchers.IO) { repository.rootLocation(uri) }
                uri to root
            }.onSuccess { (tree, root) ->
                prefs.edit().putString("tree_uri", tree.toString()).apply()
                _state.update { it.copy(treeUri = tree, navigationStack = listOf(root), errorMessage = null) }
                refresh()
            }.onFailure { setError(it, "No se pudo abrir esa ubicación") }
        }
    }

    fun openDirectory(entry: StorageEntry) {
        if (!entry.isDirectory) return
        _state.update { it.copy(navigationStack = it.navigationStack + BrowserLocation(entry.documentId, entry.name)) }
        refresh()
    }

    fun navigateUp(): Boolean {
        if (_state.value.navigationStack.size <= 1) return false
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
            runCatching { withContext(Dispatchers.IO) { repository.listChildren(tree, location) } }
                .onSuccess { list -> _state.update { it.copy(isLoading = false, entries = list) } }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, entries = emptyList()) }
                    setError(error, "No se pudo leer esta carpeta")
                }
        }
    }

    fun consumeError() = _state.update { it.copy(errorMessage = null) }

    private fun setError(t: Throwable, fallback: String) {
        _state.update { it.copy(errorMessage = t.message?.takeIf(String::isNotBlank) ?: fallback) }
    }
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
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(onTreeSelected) }

    BackHandler(enabled = state.canNavigateUp) { onNavigateUp() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); onErrorConsumed() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.currentLocation?.name ?: "Flux Files", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.currentLocation?.let {
                            Text("${state.entries.size} elementos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    if (state.canNavigateUp) IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (state.treeUri != null) IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.treeUri == null) "Elegir ubicación" else "Cambiar ubicación") },
                            leadingIcon = { Icon(Icons.Default.Storage, null) },
                            onClick = { menu = false; picker.launch(state.treeUri) },
                        )
                        DropdownMenuItem(
                            text = { Text("Acerca de") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { menu = false; onOpenAbout() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.treeUri == null -> WelcomeState { picker.launch(null) }
                state.isLoading && state.entries.isEmpty() -> CenterMessage("Leyendo carpeta…", Icons.Default.FolderOpen)
                state.entries.isEmpty() -> EmptyState(onRefresh)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.documentId }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(entrySupportingText(entry), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                Icon(
                                    if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (entry.isDirectory) onDirectoryClick(entry) else openFile(context, entry)
                            }.padding(vertical = 4.dp),
                        )
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
            }
            if (state.isLoading && state.entries.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun WelcomeState(onChoose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.FolderOpen, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Elige dónde quieres trabajar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Flux Files usa el selector de Android. Tú decides qué carpeta puede leer; no necesita acceso completo al almacenamiento.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onChoose) { Icon(Icons.Default.Storage, null); Spacer(Modifier.size(8.dp)); Text("Elegir carpeta") }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Folder, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Esta carpeta está vacía", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("No hay archivos o carpetas que mostrar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRefresh) { Text("Actualizar") }
    }
}

@Composable
private fun CenterMessage(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp)); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Acerca de") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text("Flux Files", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Versión ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Text("Nueva implementación en Kotlin y Jetpack Compose, diseñada desde cero con prioridad en claridad, control del usuario y navegación predecible.")
            Spacer(Modifier.height(16.dp))
            Text("Esta versión inicial usa Storage Access Framework (SAF): solo conserva acceso a ubicaciones autorizadas por el usuario.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FluxTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFF80D5D5)) else lightColorScheme(primary = Color(0xFF006C6D))
    MaterialTheme(colorScheme = colors, content = content)
}

private fun entrySupportingText(entry: StorageEntry): String {
    if (entry.isDirectory) return "Carpeta"
    val parts = buildList {
        entry.sizeBytes?.let { add(formatBytes(it)) }
        entry.modifiedAtMillis?.takeIf { it > 0 }?.let { add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) }
    }
    return parts.joinToString(" · ").ifEmpty { entry.mimeType.substringAfterLast('/') }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val unit = 1024.0
    val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceIn(1, 4)
    val prefix = listOf("B", "KB", "MB", "GB", "TB")[exp]
    val value = bytes / unit.pow(exp.toDouble())
    return if (value >= 10) "%.0f %s".format(value, prefix) else "%.1f %s".format(value, prefix)
}

private fun openFile(context: Context, entry: StorageEntry) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(entry.uri, entry.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No app registered for this file type. A later UX iteration will surface this in-app.
    }
}
