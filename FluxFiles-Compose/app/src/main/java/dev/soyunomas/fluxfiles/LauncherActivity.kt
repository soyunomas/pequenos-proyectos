package dev.soyunomas.fluxfiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

data class InspectorRequestV5(
    val entry: StorageEntry,
    val tab: InspectorTabV5,
)

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluxThemeV5 {
                val vm: BrowserViewModelV53 = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val canWrite by vm.canWrite.collectAsStateWithLifecycle()
                val fileSystem: StorageFileSystem = remember { SafStorageRepository(applicationContext) }
                val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    uri?.let { vm.selectTree(it) }
                }
                var about by remember { mutableStateOf(false) }
                var inspector by remember { mutableStateOf<InspectorRequestV5?>(null) }
                var zipSources by remember { mutableStateOf<List<StorageEntry>?>(null) }

                val chooseLocation = { rootPicker.launch(null) }

                when {
                    zipSources != null && state.currentLocation != null && canWrite -> {
                        ZipCreatorV52(
                            sources = zipSources!!,
                            destination = state.currentLocation!!,
                            fileSystem = fileSystem,
                            onBack = { zipSources = null },
                            onCreated = {
                                vm.clearSelection()
                                vm.refresh()
                            },
                        )
                    }

                    inspector != null -> {
                        val request = inspector!!
                        val destination = state.currentLocation
                        if (
                            request.tab == InspectorTabV5.PREVIEW &&
                            previewKindV5(request.entry) == PreviewKindV5.ZIP &&
                            destination != null &&
                            canWrite
                        ) {
                            ZipInspectorV51(
                                entry = request.entry,
                                destination = destination,
                                fileSystem = fileSystem,
                                onBack = { inspector = null },
                                onExtracted = vm::refresh,
                            )
                        } else {
                            FileInspectorV5(
                                entry = request.entry,
                                initialTab = request.tab,
                                onBack = { inspector = null },
                            )
                        }
                    }

                    about -> {
                        AboutScreenV5 { about = false }
                    }

                    else -> {
                        Box(Modifier.fillMaxSize()) {
                            if (canWrite) {
                                ZipSelectionOverlayV52(
                                    state = state,
                                    onCreateZip = { selected ->
                                        if (selected.isNotEmpty() && vm.requestWriteAction("crear un ZIP")) {
                                            zipSources = selected
                                        }
                                    },
                                ) {
                                    BrowserContentV53(
                                        state = state,
                                        vm = vm,
                                        onChooseLocation = chooseLocation,
                                        onOpenAbout = { about = true },
                                        onInspect = { entry, tab ->
                                            inspector = InspectorRequestV5(entry, tab)
                                        },
                                    )
                                }
                            } else {
                                BrowserContentV53(
                                    state = state,
                                    vm = vm,
                                    onChooseLocation = chooseLocation,
                                    onOpenAbout = { about = true },
                                    onInspect = { entry, tab ->
                                        inspector = InspectorRequestV5(entry, tab)
                                    },
                                )
                                if (state.storageRoot != null) {
                                    ReadOnlyBadgeV53(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 88.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserContentV53(
    state: BrowserUiState,
    vm: BrowserViewModelV53,
    onChooseLocation: () -> Unit,
    onOpenAbout: () -> Unit,
    onInspect: (StorageEntry, InspectorTabV5) -> Unit,
) {
    BrowserScreenV5(
        state = state,
        onChooseLocation = onChooseLocation,
        onDirectoryClick = vm::openDirectory,
        onNavigateUp = { vm.navigateUp() },
        onRefresh = vm::refresh,
        onErrorConsumed = vm::consumeError,
        onMessageConsumed = vm::consumeMessage,
        onOpenAbout = onOpenAbout,
        onInspect = onInspect,
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
        onResolveConflict = vm::resolveConflict,
    )
}

@Composable
private fun ReadOnlyBadgeV53(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 3.dp,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Solo lectura", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FluxThemeV5(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF80D5D5))
    } else {
        lightColorScheme(primary = Color(0xFF006C6D))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreenV5(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
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
                "Implementación propia en Kotlin y Jetpack Compose. El navegador, las vistas previas y las operaciones ZIP trabajan sobre referencias y operaciones de almacenamiento propias de Flux Files."
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "El backend disponible en esta versión sigue siendo Storage Access Framework (SAF). El selector de Android queda aislado en la actividad para permitir añadir otros proveedores sin acoplar la pantalla del navegador.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
