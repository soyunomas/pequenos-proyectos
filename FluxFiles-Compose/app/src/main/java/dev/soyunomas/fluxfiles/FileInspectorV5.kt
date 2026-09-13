package dev.soyunomas.fluxfiles

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.ln
import kotlin.math.pow

enum class InspectorTabV5 { PREVIEW, DETAILS }

enum class PreviewKindV5 { IMAGE, TEXT, ZIP, UNSUPPORTED }

data class ZipEntryInfoV5(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
)

data class ZipSummaryV5(
    val entries: List<ZipEntryInfoV5>,
    val truncated: Boolean,
)

fun previewKindV5(entry: StorageEntry): PreviewKindV5 {
    if (entry.isDirectory) return PreviewKindV5.UNSUPPORTED
    val mime = entry.mimeType.lowercase(Locale.ROOT)
    val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        mime.startsWith("image/") -> PreviewKindV5.IMAGE
        mime == "application/zip" || ext == "zip" -> PreviewKindV5.ZIP
        mime.startsWith("text/") || ext in setOf(
            "txt", "md", "json", "xml", "csv", "log", "yaml", "yml", "toml",
            "ini", "properties", "kt", "kts", "java", "py", "js", "ts", "html",
            "htm", "css", "scss", "sh", "bat", "gradle", "sql", "c", "cpp", "h",
        ) -> PreviewKindV5.TEXT
        else -> PreviewKindV5.UNSUPPORTED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileInspectorV5(
    entry: StorageEntry,
    initialTab: InspectorTabV5,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val previewKind = remember(entry) { previewKindV5(entry) }
    val previewAvailable = previewKind != PreviewKindV5.UNSUPPORTED
    var tab by remember(entry.documentId, initialTab) {
        mutableStateOf(if (initialTab == InspectorTabV5.PREVIEW && !previewAvailable) InspectorTabV5.DETAILS else initialTab)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (entry.isDirectory) "Carpeta" else readableTypeV5(entry),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    },
                    actions = {
                        if (!entry.isDirectory) {
                            IconButton(onClick = { openExternalV5(context, entry) }) {
                                Icon(Icons.Default.OpenInNew, "Abrir con otra aplicación")
                            }
                        }
                    },
                )
                if (previewAvailable) {
                    TabRow(selectedTabIndex = if (tab == InspectorTabV5.PREVIEW) 0 else 1) {
                        Tab(
                            selected = tab == InspectorTabV5.PREVIEW,
                            onClick = { tab = InspectorTabV5.PREVIEW },
                            text = { Text("Vista previa") },
                        )
                        Tab(
                            selected = tab == InspectorTabV5.DETAILS,
                            onClick = { tab = InspectorTabV5.DETAILS },
                            text = { Text("Detalles") },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (tab == InspectorTabV5.DETAILS || !previewAvailable) {
                DetailsPaneV5(entry)
            } else {
                when (previewKind) {
                    PreviewKindV5.IMAGE -> ImagePreviewV5(entry)
                    PreviewKindV5.TEXT -> TextPreviewV5(entry)
                    PreviewKindV5.ZIP -> ZipPreviewV5(entry)
                    PreviewKindV5.UNSUPPORTED -> UnsupportedPreviewV5(entry)
                }
            }
        }
    }
}

@Composable
private fun DetailsPaneV5(entry: StorageEntry) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                Icon(
                    if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    null,
                    Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(entry.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            DetailRowV5("Tipo", if (entry.isDirectory) "Carpeta" else readableTypeV5(entry))
            HorizontalDivider()
        }
        if (!entry.isDirectory) {
            item {
                DetailRowV5("Tamaño", entry.sizeBytes?.let(::formatBytesV5) ?: "No disponible")
                HorizontalDivider()
            }
        }
        item {
            DetailRowV5(
                "Modificado",
                entry.modifiedAtMillis?.takeIf { it > 0 }?.let {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                } ?: "No disponible",
            )
            HorizontalDivider()
        }
        item {
            DetailRowV5("Proveedor", entry.uri.authority ?: "Android")
            HorizontalDivider()
        }
        item {
            DetailRowV5("Identificador", entry.documentId)
        }
        if (!entry.isDirectory) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = { openExternalV5(context, entry) }) {
                        Icon(Icons.Default.OpenInNew, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Abrir con…")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRowV5(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ImagePreviewV5(entry: StorageEntry) {
    val context = LocalContext.current
    var bitmap by remember(entry.uri) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(entry.uri) { mutableStateOf(true) }
    var error by remember(entry.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(entry.uri) {
        loading = true
        error = null
        bitmap = runCatching {
            withContext(Dispatchers.IO) { decodeSampledBitmapV5(context, entry.uri, 2048) }
        }.onFailure { error = it.message ?: "No se pudo leer la imagen" }.getOrNull()
        loading = false
    }

    when {
        loading -> InspectorLoadingV5("Preparando imagen…")
        bitmap != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = entry.name,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentScale = ContentScale.Fit,
            )
        }
        else -> InspectorErrorV5(error ?: "No se pudo mostrar la imagen")
    }
}

@Composable
private fun TextPreviewV5(entry: StorageEntry) {
    val context = LocalContext.current
    var text by remember(entry.uri) { mutableStateOf<String?>(null) }
    var truncated by remember(entry.uri) { mutableStateOf(false) }
    var loading by remember(entry.uri) { mutableStateOf(true) }
    var error by remember(entry.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(entry.uri) {
        loading = true
        val result = runCatching {
            withContext(Dispatchers.IO) { readTextPreviewV5(context, entry.uri, 256 * 1024) }
        }
        result.onSuccess {
            text = it.first
            truncated = it.second
        }.onFailure {
            error = it.message ?: "No se pudo leer el texto"
        }
        loading = false
    }

    when {
        loading -> InspectorLoadingV5("Leyendo texto…")
        text != null -> Column(Modifier.fillMaxSize()) {
            if (truncated) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "Vista previa limitada a los primeros 256 KiB.",
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = text.orEmpty(),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        else -> InspectorErrorV5(error ?: "No se pudo mostrar el texto")
    }
}

@Composable
private fun ZipPreviewV5(entry: StorageEntry) {
    val context = LocalContext.current
    var summary by remember(entry.uri) { mutableStateOf<ZipSummaryV5?>(null) }
    var loading by remember(entry.uri) { mutableStateOf(true) }
    var error by remember(entry.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(entry.uri) {
        loading = true
        val result = runCatching {
            withContext(Dispatchers.IO) { listZipV5(context, entry.uri, 1000) }
        }
        result.onSuccess { summary = it }.onFailure {
            error = it.message ?: "No se pudo leer el ZIP"
        }
        loading = false
    }

    when {
        loading -> InspectorLoadingV5("Leyendo archivo ZIP…")
        summary != null -> Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    if (summary!!.truncated) {
                        "Mostrando los primeros ${summary!!.entries.size} elementos. La extracción llegará en una fase posterior."
                    } else {
                        "${summary!!.entries.size} elementos · inspección en modo lectura"
                    },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(summary!!.entries, key = { it.name }) { zipEntry ->
                    ListItem(
                        headlineContent = {
                            Text(zipEntry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            if (!zipEntry.isDirectory && zipEntry.size >= 0) {
                                Text(formatBytesV5(zipEntry.size))
                            }
                        },
                        leadingContent = {
                            Icon(if (zipEntry.isDirectory) Icons.Default.Folder else Icons.Default.Description, null)
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 72.dp))
                }
            }
        }
        else -> InspectorErrorV5(error ?: "No se pudo inspeccionar el ZIP")
    }
}

@Composable
private fun UnsupportedPreviewV5(entry: StorageEntry) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Info, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Sin vista previa interna", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Flux Files todavía no puede mostrar este formato internamente.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = { openExternalV5(context, entry) }) {
            Icon(Icons.Default.OpenInNew, null)
            Spacer(Modifier.size(8.dp))
            Text("Abrir con…")
        }
    }
}

@Composable
private fun InspectorLoadingV5(label: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InspectorErrorV5(message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.WarningAmber, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("No se pudo generar la vista previa", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun decodeSampledBitmapV5(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: error("Android no permitió leer la imagen")
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Formato de imagen no reconocido" }

    var sample = 1
    var width = bounds.outWidth
    var height = bounds.outHeight
    while (width / 2 >= maxSide || height / 2 >= maxSide) {
        sample *= 2
        width /= 2
        height /= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: error("No se pudo decodificar la imagen")
}

private fun readTextPreviewV5(context: Context, uri: Uri, limit: Int): Pair<String, Boolean> {
    val input = context.contentResolver.openInputStream(uri) ?: error("Android no permitió leer el archivo")
    input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        var truncated = false
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            val remaining = limit - total
            if (remaining <= 0) {
                truncated = true
                break
            }
            val toWrite = minOf(read, remaining)
            output.write(buffer, 0, toWrite)
            total += toWrite
            if (toWrite < read || total >= limit) {
                truncated = stream.read() != -1 || toWrite < read
                break
            }
        }
        val bytes = output.toByteArray()
        require(bytes.none { it == 0.toByte() }) { "El archivo parece binario" }
        return bytes.toString(Charsets.UTF_8) to truncated
    }
}

private fun listZipV5(context: Context, uri: Uri, maxEntries: Int): ZipSummaryV5 {
    val input = context.contentResolver.openInputStream(uri) ?: error("Android no permitió leer el ZIP")
    val entries = mutableListOf<ZipEntryInfoV5>()
    var truncated = false
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val next = zip.nextEntry ?: break
            if (entries.size >= maxEntries) {
                truncated = true
                break
            }
            entries += ZipEntryInfoV5(
                name = next.name,
                isDirectory = next.isDirectory,
                size = next.size,
            )
            zip.closeEntry()
        }
    }
    return ZipSummaryV5(entries, truncated)
}

private fun readableTypeV5(entry: StorageEntry): String {
    val mime = entry.mimeType
    return when {
        mime.startsWith("image/") -> "Imagen · $mime"
        mime.startsWith("text/") -> "Texto · $mime"
        mime == "application/zip" || entry.name.endsWith(".zip", ignoreCase = true) -> "Archivo ZIP"
        else -> mime.ifBlank { "Archivo" }
    }
}

private fun formatBytesV5(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val unit = 1024.0
    val exponent = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceIn(1, 4)
    val suffix = listOf("B", "KiB", "MiB", "GiB", "TiB")[exponent]
    val value = bytes / unit.pow(exponent.toDouble())
    return if (value >= 10) "%.0f %s".format(Locale.getDefault(), value, suffix)
    else "%.1f %s".format(Locale.getDefault(), value, suffix)
}

fun openExternalV5(context: Context, entry: StorageEntry) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(entry.uri, entry.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No hay una aplicación compatible para abrir este archivo", Toast.LENGTH_SHORT).show()
    }
}
