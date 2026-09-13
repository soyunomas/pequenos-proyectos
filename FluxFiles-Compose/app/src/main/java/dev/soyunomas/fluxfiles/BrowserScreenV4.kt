package dev.soyunomas.fluxfiles

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

enum class BrowserViewMode { LIST, GRID }
enum class BrowserSortMode { NAME, MODIFIED, SIZE }

data class FavoriteFolder(val documentId: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreenV4(
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
    onResolveConflict: (ConflictResolution, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("flux_files_ux", Context.MODE_PRIVATE) }
    val snackbar = remember { SnackbarHostState() }

    var appMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<StorageEntry?>(null) }
    var delete by remember { mutableStateOf<StorageEntry?>(null) }
    var itemMenu by remember { mutableStateOf<StorageEntry?>(null) }
    var showFavorites by remember { mutableStateOf(false) }

    var viewMode by remember {
        mutableStateOf(
            runCatching { BrowserViewMode.valueOf(prefs.getString("view_mode", "LIST") ?: "LIST") }
                .getOrDefault(BrowserViewMode.LIST)
        )
    }
    var sortMode by remember {
        mutableStateOf(
            runCatching { BrowserSortMode.valueOf(prefs.getString("sort_mode", "NAME") ?: "NAME") }
                .getOrDefault(BrowserSortMode.NAME)
        )
    }
    var descending by remember { mutableStateOf(prefs.getBoolean("sort_desc", false)) }

    val treeKey = remember(state.treeUri) { "favorites_${state.treeUri?.toString()?.hashCode() ?: 0}" }
    var favorites by remember(treeKey) {
        mutableStateOf(loadFavorites(prefs.getStringSet(treeKey, emptySet()).orEmpty()))
    }

    val current = state.currentLocation
    val currentIsFavorite = current?.let { location ->
        favorites.any { it.documentId == location.documentId }
    } == true

    val visibleEntries = remember(state.entries, query, sortMode, descending) {
        state.entries
            .asSequence()
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(entryComparator(sortMode, descending))
            .toList()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(onTreeSelected)
    }

    BackHandler(
        enabled = state.transferConflict == null &&
            (searchVisible || state.isSelectionMode || state.pendingTransfer != null || state.canNavigateUp)
    ) {
        when {
            searchVisible -> {
                searchVisible = false
                query = ""
            }
            state.isSelectionMode -> onClearSelection()
            state.canNavigateUp -> onNavigateUp()
            state.pendingTransfer != null -> onCancelTransfer()
        }
    }

    LaunchedEffect(state.currentLocation?.documentId) {
        query = ""
        searchVisible = false
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

    fun saveFavorites(next: List<FavoriteFolder>) {
        favorites = next.distinctBy { it.documentId }
        prefs.edit().putStringSet(treeKey, favorites.mapTo(linkedSetOf()) { encodeFavorite(it) }).apply()
    }

    fun toggleCurrentFavorite() {
        val location = current ?: return
        if (currentIsFavorite) {
            saveFavorites(favorites.filterNot { it.documentId == location.documentId })
        } else {
            saveFavorites(favorites + FavoriteFolder(location.documentId, location.name))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.treeUri != null && !state.isMutating && !state.isSelectionMode && state.pendingTransfer == null) {
                FloatingActionButton(onClick = { create = true }) {
                    Icon(Icons.Default.CreateNewFolder, "Nueva carpeta")
                }
            }
        },
        bottomBar = {
            state.pendingTransfer?.let { transfer ->
                V4TransferBar(
                    transfer = transfer,
                    canPasteHere = state.canPasteHere,
                    isMutating = state.isMutating,
                    waitingForConflict = state.transferConflict != null,
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
                            if (state.selectedIds.isEmpty()) "Seleccionar"
                            else "${state.selectedIds.size} seleccionados"
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
                        ) { Icon(Icons.Default.ContentCopy, "Copiar") }
                        IconButton(
                            onClick = onBeginMove,
                            enabled = state.selectedIds.isNotEmpty() && !state.isMutating,
                        ) { Icon(Icons.Default.DriveFileMove, "Mover") }
                        IconButton(
                            onClick = onSelectAll,
                            enabled = state.entries.isNotEmpty() && query.isBlank(),
                        ) { Icon(Icons.Default.SelectAll, "Seleccionar todo") }
                    },
                )
            } else {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    current?.name ?: "Flux Files",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (current != null) {
                                    val subtitle = when {
                                        state.pendingTransfer != null -> {
                                            val n = state.pendingTransfer.entries.size
                                            "Destino para $n ${if (n == 1) "elemento" else "elementos"}"
                                        }
                                        query.isNotBlank() -> "${visibleEntries.size} de ${state.entries.size} resultados"
                                        else -> "${state.entries.size} elementos"
                                    }
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
                            if (state.treeUri != null && state.pendingTransfer == null) {
                                IconButton(
                                    onClick = { searchVisible = !searchVisible; if (!searchVisible) query = "" },
                                    enabled = !state.isMutating,
                                ) { Icon(if (searchVisible) Icons.Default.Close else Icons.Default.Search, "Buscar") }
                                IconButton(
                                    onClick = ::toggleCurrentFavorite,
                                    enabled = current != null && !state.isMutating,
                                ) {
                                    Icon(
                                        if (currentIsFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        if (currentIsFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewMode = if (viewMode == BrowserViewMode.LIST) BrowserViewMode.GRID else BrowserViewMode.LIST
                                        prefs.edit().putString("view_mode", viewMode.name).apply()
                                    },
                                    enabled = !state.isMutating,
                                ) {
                                    Icon(
                                        if (viewMode == BrowserViewMode.LIST) Icons.Default.GridView else Icons.Default.List,
                                        "Cambiar vista",
                                    )
                                }
                                Box {
                                    IconButton(onClick = { sortMenu = true }, enabled = !state.isMutating) {
                                        Icon(Icons.Default.Sort, "Ordenar")
                                    }
                                    DropdownMenu(sortMenu, { sortMenu = false }) {
                                        BrowserSortMode.entries.forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(sortLabel(mode)) },
                                                leadingIcon = { RadioButton(selected = sortMode == mode, onClick = null) },
                                                onClick = {
                                                    sortMode = mode
                                                    prefs.edit().putString("sort_mode", mode.name).apply()
                                                    sortMenu = false
                                                },
                                            )
                                        }
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(if (descending) "Orden ascendente" else "Orden descendente") },
                                            onClick = {
                                                descending = !descending
                                                prefs.edit().putBoolean("sort_desc", descending).apply()
                                                sortMenu = false
                                            },
                                        )
                                    }
                                }
                            }

                            if (state.treeUri != null) {
                                IconButton(onClick = onRefresh, enabled = !state.isMutating) {
                                    Icon(Icons.Default.Refresh, "Actualizar")
                                }
                            }

                            if (state.pendingTransfer == null) {
                                IconButton(onClick = { appMenu = true }, enabled = !state.isMutating) {
                                    Icon(Icons.Default.MoreVert, "Más opciones")
                                }
                                DropdownMenu(appMenu, { appMenu = false }) {
                                    if (state.entries.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Seleccionar") },
                                            leadingIcon = { Icon(Icons.Default.Checklist, null) },
                                            onClick = { appMenu = false; onEnterSelection() },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Favoritos${if (favorites.isNotEmpty()) " (${favorites.size})" else ""}") },
                                        leadingIcon = { Icon(Icons.Default.Star, null) },
                                        onClick = { appMenu = false; showFavorites = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (state.treeUri == null) "Elegir ubicación" else "Cambiar ubicación") },
                                        leadingIcon = { Icon(Icons.Default.Storage, null) },
                                        onClick = { appMenu = false; picker.launch(state.treeUri) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Acerca de") },
                                        leadingIcon = { Icon(Icons.Default.Info, null) },
                                        onClick = { appMenu = false; onOpenAbout() },
                                    )
                                }
                            }
                        },
                    )

                    if (searchVisible && state.treeUri != null && state.pendingTransfer == null) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            singleLine = true,
                            placeholder = { Text("Buscar en esta carpeta") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Default.Cancel, "Limpiar búsqueda")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.treeUri == null -> V4Welcome { picker.launch(null) }
                state.isLoading && state.entries.isEmpty() -> V4Loading("Leyendo carpeta…")
                state.entries.isEmpty() -> V4Empty(onRefresh) { create = true }
                visibleEntries.isEmpty() -> V4NoResults(query)
                viewMode == BrowserViewMode.LIST -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(visibleEntries, key = { it.documentId }) { entry ->
                            val selected = entry.documentId in state.selectedIds
                            Box {
                                ListItem(
                                    headlineContent = {
                                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    supportingContent = {
                                        Text(v4SupportingText(entry), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        if (!state.isSelectionMode && state.pendingTransfer == null) {
                                            IconButton(onClick = { itemMenu = entry }, enabled = !state.isMutating) {
                                                Icon(Icons.Default.MoreVert, "Opciones")
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            enabled = !state.isMutating,
                                            onClick = {
                                                when {
                                                    state.isSelectionMode -> onToggleSelection(entry)
                                                    entry.isDirectory -> onDirectoryClick(entry)
                                                    else -> v4OpenFile(context, entry)
                                                }
                                            },
                                            onLongClick = {
                                                if (state.pendingTransfer == null) onStartSelection(entry)
                                            },
                                        )
                                        .padding(vertical = 4.dp),
                                )
                                V4ItemMenu(
                                    entry = entry,
                                    expanded = itemMenu?.documentId == entry.documentId && state.pendingTransfer == null,
                                    onDismiss = { itemMenu = null },
                                    onRename = { itemMenu = null; rename = entry },
                                    onDelete = { itemMenu = null; delete = entry },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                            HorizontalDivider(Modifier.padding(start = 72.dp))
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visibleEntries, key = { it.documentId }) { entry ->
                            val selected = entry.documentId in state.selectedIds
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        enabled = !state.isMutating,
                                        onClick = {
                                            when {
                                                state.isSelectionMode -> onToggleSelection(entry)
                                                entry.isDirectory -> onDirectoryClick(entry)
                                                else -> v4OpenFile(context, entry)
                                            }
                                        },
                                        onLongClick = {
                                            if (state.pendingTransfer == null) onStartSelection(entry)
                                        },
                                    ),
                            ) {
                                Box(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        if (state.isSelectionMode) {
                                            Checkbox(
                                                checked = selected,
                                                onCheckedChange = { onToggleSelection(entry) },
                                                modifier = Modifier.align(Alignment.Start),
                                            )
                                        }
                                        Icon(
                                            if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                            null,
                                            Modifier.size(48.dp),
                                            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            entry.name,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            v4SupportingText(entry),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (!state.isSelectionMode && state.pendingTransfer == null) {
                                        Box(Modifier.align(Alignment.TopEnd)) {
                                            IconButton(onClick = { itemMenu = entry }) {
                                                Icon(Icons.Default.MoreVert, "Opciones")
                                            }
                                            V4ItemMenu(
                                                entry = entry,
                                                expanded = itemMenu?.documentId == entry.documentId,
                                                onDismiss = { itemMenu = null },
                                                onRename = { itemMenu = null; rename = entry },
                                                onDelete = { itemMenu = null; delete = entry },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if ((state.isLoading && state.entries.isNotEmpty()) || state.isMutating) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (create) {
        V4NameDialog("Nueva carpeta", "", "Crear", { create = false }) {
            create = false
            onCreateFolder(it)
        }
    }
    rename?.let { entry ->
        V4NameDialog("Renombrar", entry.name, "Guardar", { rename = null }) {
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
                    if (entry.isDirectory) "Se eliminará “${entry.name}” y posiblemente todo su contenido. Esta acción no tiene deshacer."
                    else "Se eliminará “${entry.name}”. Esta acción no tiene deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = { delete = null; onDelete(entry) }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancelar") } },
        )
    }

    if (showFavorites) {
        V4FavoritesDialog(
            favorites = favorites,
            currentDocumentId = current?.documentId,
            onDismiss = { showFavorites = false },
            onOpen = { favorite ->
                val tree = state.treeUri
                if (tree != null && favorite.documentId != current?.documentId) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, favorite.documentId)
                    showFavorites = false
                    onDirectoryClick(
                        StorageEntry(
                            documentId = favorite.documentId,
                            uri = uri,
                            name = favorite.name,
                            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                            sizeBytes = null,
                            modifiedAtMillis = null,
                        )
                    )
                } else {
                    showFavorites = false
                }
            },
            onRemove = { favorite ->
                saveFavorites(favorites.filterNot { it.documentId == favorite.documentId })
            },
        )
    }

    state.transferConflict?.let { conflict ->
        V4ConflictDialog(
            conflict = conflict,
            mode = state.pendingTransfer?.mode ?: TransferMode.COPY,
            onResolve = onResolveConflict,
        )
    }
}

@Composable
private fun V4ItemMenu(
    entry: StorageEntry,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
        DropdownMenuItem(
            text = { Text("Renombrar") },
            leadingIcon = { Icon(Icons.Default.Edit, null) },
            onClick = onRename,
        )
        DropdownMenuItem(
            text = { Text("Eliminar") },
            leadingIcon = { Icon(Icons.Default.Delete, null) },
            onClick = onDelete,
        )
    }
}

@Composable
private fun V4FavoritesDialog(
    favorites: List<FavoriteFolder>,
    currentDocumentId: String?,
    onDismiss: () -> Unit,
    onOpen: (FavoriteFolder) -> Unit,
    onRemove: (FavoriteFolder) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Star, null) },
        title = { Text("Carpetas favoritas") },
        text = {
            if (favorites.isEmpty()) {
                Text("No tienes favoritas todavía. Usa la estrella de la barra superior para guardar la carpeta actual.")
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                    items(favorites, key = { it.documentId }) { favorite ->
                        ListItem(
                            headlineContent = { Text(favorite.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                if (favorite.documentId == currentDocumentId) {
                                    Text("Carpeta actual")
                                }
                            },
                            leadingContent = { Icon(Icons.Default.Folder, null) },
                            trailingContent = {
                                IconButton(onClick = { onRemove(favorite) }) {
                                    Icon(Icons.Default.Star, "Quitar de favoritos")
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { onOpen(favorite) },
                                onLongClick = { onRemove(favorite) },
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun V4ConflictDialog(
    conflict: TransferConflict,
    mode: TransferMode,
    onResolve: (ConflictResolution, Boolean) -> Unit,
) {
    var applyToAll by remember(conflict.source.documentId, conflict.existing.documentId) { mutableStateOf(false) }
    val verb = if (mode == TransferMode.COPY) "copiar" else "mover"
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.WarningAmber, null) },
        title = { Text("Ya existe “${conflict.source.name}”") },
        text = {
            Column {
                Text(
                    if (conflict.isSameDocument) {
                        "Estás intentando $verb el elemento en la misma carpeta. Puedes conservar ambos para crear una copia con otro nombre u omitirlo."
                    } else if (conflict.existing.isDirectory) {
                        "En el destino ya hay una carpeta con ese nombre. Reemplazar eliminará esa carpeta y su contenido después de preparar una copia temporal."
                    } else {
                        "En el destino ya hay un archivo con ese nombre. Elige qué debe hacer Flux Files."
                    }
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Aplicar a los siguientes conflictos", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { onResolve(ConflictResolution.KEEP_BOTH, applyToAll) }) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Conservar ambos")
                }
                Row {
                    TextButton(onClick = { onResolve(ConflictResolution.SKIP, applyToAll) }) { Text("Omitir") }
                    if (!conflict.isSameDocument) {
                        TextButton(onClick = { onResolve(ConflictResolution.REPLACE, applyToAll) }) {
                            Text("Reemplazar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                TextButton(onClick = { onResolve(ConflictResolution.CANCEL, false) }) {
                    Text("Cancelar operación")
                }
            }
        },
    )
}

@Composable
private fun V4TransferBar(
    transfer: PendingTransfer,
    canPasteHere: Boolean,
    isMutating: Boolean,
    waitingForConflict: Boolean,
    operationLabel: String?,
    onCancel: () -> Unit,
    onPasteHere: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
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
                Text("Elige otra carpeta como destino.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (waitingForConflict) {
                Text("Esperando tu decisión sobre un conflicto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel, enabled = !isMutating || waitingForConflict) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onPasteHere, enabled = canPasteHere && !isMutating) {
                    Icon(if (transfer.mode == TransferMode.COPY) Icons.Default.ContentCopy else Icons.Default.DriveFileMove, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (transfer.mode == TransferMode.COPY) "Copiar aquí" else "Mover aquí")
                }
            }
        }
    }
}

@Composable
private fun V4NameDialog(
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
            TextButton(onClick = { onConfirm(value) }, enabled = value.trim().isNotEmpty()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun V4Welcome(onChoose: () -> Unit) {
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
private fun V4Empty(onRefresh: () -> Unit, onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Folder, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Esta carpeta está vacía", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("No hay archivos o carpetas que mostrar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun V4NoResults(query: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Search, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Sin resultados", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("No hay elementos que coincidan con “$query”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun V4Loading(text: String) {
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

private fun entryComparator(mode: BrowserSortMode, descending: Boolean): Comparator<StorageEntry> =
    Comparator { a, b ->
        if (a.isDirectory != b.isDirectory) {
            if (a.isDirectory) -1 else 1
        } else {
            val result = when (mode) {
                BrowserSortMode.NAME -> a.name.lowercase(Locale.getDefault()).compareTo(b.name.lowercase(Locale.getDefault()))
                BrowserSortMode.MODIFIED -> (a.modifiedAtMillis ?: 0L).compareTo(b.modifiedAtMillis ?: 0L)
                BrowserSortMode.SIZE -> (a.sizeBytes ?: 0L).compareTo(b.sizeBytes ?: 0L)
            }
            if (descending) -result else result
        }
    }

private fun sortLabel(mode: BrowserSortMode): String = when (mode) {
    BrowserSortMode.NAME -> "Nombre"
    BrowserSortMode.MODIFIED -> "Fecha de modificación"
    BrowserSortMode.SIZE -> "Tamaño"
}

private fun encodeFavorite(favorite: FavoriteFolder): String =
    Uri.encode(favorite.documentId) + "|" + Uri.encode(favorite.name)

private fun loadFavorites(raw: Set<String>): List<FavoriteFolder> =
    raw.mapNotNull { item ->
        val split = item.indexOf('|')
        if (split <= 0) null
        else FavoriteFolder(Uri.decode(item.substring(0, split)), Uri.decode(item.substring(split + 1)))
    }.sortedBy { it.name.lowercase(Locale.getDefault()) }

private fun v4SupportingText(entry: StorageEntry): String {
    if (entry.isDirectory) return "Carpeta"
    val parts = buildList {
        entry.sizeBytes?.let { add(v4FormatBytes(it)) }
        entry.modifiedAtMillis?.takeIf { it > 0 }?.let {
            add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)))
        }
    }
    return parts.joinToString(" · ").ifEmpty { entry.mimeType.substringAfterLast('/') }
}

private fun v4FormatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val unit = 1024.0
    val exponent = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceIn(1, 4)
    val suffix = listOf("B", "KB", "MB", "GB", "TB")[exponent]
    val value = bytes / unit.pow(exponent.toDouble())
    return if (value >= 10) "%.0f %s".format(value, suffix) else "%.1f %s".format(value, suffix)
}

private fun v4OpenFile(context: Context, entry: StorageEntry) {
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
