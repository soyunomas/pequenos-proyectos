package dev.soyunomas.fluxfiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                val vm: BrowserViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                var about by remember { mutableStateOf(false) }
                var inspector by remember { mutableStateOf<InspectorRequestV5?>(null) }

                when {
                    inspector != null -> {
                        val request = inspector!!
                        val tree = state.treeUri
                        val destination = state.currentLocation
                        if (
                            request.tab == InspectorTabV5.PREVIEW &&
                            previewKindV5(request.entry) == PreviewKindV5.ZIP &&
                            tree != null &&
                            destination != null
                        ) {
                            ZipInspectorV51(
                                entry = request.entry,
                                treeUri = tree,
                                destination = destination,
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
                        BrowserScreenV5(
                            state = state,
                            onTreeSelected = vm::selectTree,
                            onDirectoryClick = vm::openDirectory,
                            onNavigateUp = { vm.navigateUp() },
                            onRefresh = vm::refresh,
                            onErrorConsumed = vm::consumeError,
                            onMessageConsumed = vm::consumeMessage,
                            onOpenAbout = { about = true },
                            onInspect = { entry, tab -> inspector = InspectorRequestV5(entry, tab) },
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
                }
            }
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
                "Implementación propia en Kotlin y Jetpack Compose. Esta versión incorpora extracción segura de ZIP, además de detalles y vistas previas internas de archivos."
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Flux Files usa Storage Access Framework (SAF): solo trabaja con ubicaciones autorizadas por el usuario.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
