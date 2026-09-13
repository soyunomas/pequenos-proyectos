package dev.soyunomas.fluxfiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ZipSelectionOverlayV52(
    state: BrowserUiState,
    onCreateZip: (List<StorageEntry>) -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        if (
            state.isSelectionMode &&
            state.selectedIds.isNotEmpty() &&
            !state.isMutating &&
            state.pendingTransfer == null
        ) {
            ExtendedFloatingActionButton(
                onClick = { onCreateZip(state.selectedEntries) },
                icon = { Icon(Icons.Default.Archive, null) },
                text = { Text("Crear ZIP") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
}
