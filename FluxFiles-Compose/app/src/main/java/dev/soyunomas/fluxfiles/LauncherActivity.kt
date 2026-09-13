package dev.soyunomas.fluxfiles

import android.os.Bundle
import androidx.activity.ComponentActivity
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

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluxThemeV4 {
                val vm: BrowserViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                var about by remember { mutableStateOf(false) }

                if (about) {
                    AboutScreenV4 { about = false }
                } else {
                    BrowserScreenV4(
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
                        onResolveConflict = vm::resolveConflict,
                    )
                }
            }
        }
    }
}

@Composable
private fun FluxThemeV4(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF80D5D5))
    } else {
        lightColorScheme(primary = Color(0xFF006C6D))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreenV4(onBack: () -> Unit) {
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
                "Implementación propia en Kotlin y Jetpack Compose. Esta versión incorpora búsqueda local, ordenación, favoritos y vistas de lista/cuadrícula."
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Flux Files usa Storage Access Framework (SAF): solo trabaja con ubicaciones autorizadas por el usuario.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
