package dev.soyunomas.fluxfiles

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.ln
import kotlin.math.pow

private const val MAX_ZIP_ENTRIES_V51 = 10_000
private const val MAX_EXTRACTED_BYTES_V51 = 4L * 1024L * 1024L * 1024L
private const val MAX_VISIBLE_ZIP_ENTRIES_V51 = 1_000

data class ZipScanEntryV51(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
)

data class ZipScanV51(
    val entries: List<ZipScanEntryV51>,
    val totalEntries: Int,
    val knownBytes: Long,
    val unknownSizes: Boolean,
)

data class ZipProgressV51(
    val completedEntries: Int,
    val totalEntries: Int,
    val extractedBytes: Long,
    val currentPath: String,
)

data class ZipExtractionResultV51(
    val folderName: String,
    val entries: Int,
    val bytes: Long,
)

private sealed interface ZipScreenStateV51 {
    data object Scanning : ZipScreenStateV51
    data class Ready(val scan: ZipScanV51) : ZipScreenStateV51
    data class Extracting(val scan: ZipScanV51, val progress: ZipProgressV51) : ZipScreenStateV51
    data class Done(val result: ZipExtractionResultV51) : ZipScreenStateV51
    data class Failed(val message: String, val scan: ZipScanV51? = null) : ZipScreenStateV51
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipInspectorV51(
    entry: StorageEntry,
    treeUri: Uri,
    destination: BrowserLocation,
    onBack: () -> Unit,
    onExtracted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extractor = remember(context) { SafZipExtractorV51(context) }
    var state by remember(entry.documentId) { mutableStateOf<ZipScreenStateV51>(ZipScreenStateV51.Scanning) }
    var extractionJob by remember(entry.documentId) { mutableStateOf<Job?>(null) }

    fun loadScan() {
        state = ZipScreenStateV51.Scanning
        scope.launch {
            state = runCatching { extractor.scan(entry.uri) }
                .fold(
                    onSuccess = { ZipScreenStateV51.Ready(it) },
                    onFailure = {
                        ZipScreenStateV51.Failed(
                            it.message?.takeIf(String::isNotBlank) ?: "No se pudo inspeccionar el ZIP"
                        )
                    },
                )
        }
    }

    fun startExtraction(scan: ZipScanV51) {
        if (extractionJob?.isActive == true) return
        extractionJob = scope.launch {
            state = ZipScreenStateV51.Extracting(
                scan,
                ZipProgressV51(0, scan.totalEntries, 0L, "Preparando…"),
            )
            try {
                val result = extractor.extract(
                    zipUri = entry.uri,
                    treeUri = treeUri,
                    destination = destination,
                    archiveName = entry.name,
                ) { progress ->
                    state = ZipScreenStateV51.Extracting(scan, progress)
                }
                state = ZipScreenStateV51.Done(result)
                onExtracted()
            } catch (_: CancellationException) {
                state = ZipScreenStateV51.Ready(scan)
            } catch (t: Throwable) {
                state = ZipScreenStateV51.Failed(
                    t.message?.takeIf(String::isNotBlank) ?: "No se pudo extraer el ZIP",
                    scan,
                )
            } finally {
                extractionJob = null
            }
        }
    }

    LaunchedEffect(entry.documentId) { loadScan() }

    BackHandler {
        if (extractionJob?.isActive == true) {
            extractionJob?.cancel()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Archivo ZIP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (extractionJob?.isActive == true) extractionJob?.cancel() else onBack()
                        }
                    ) {
                        Icon(
                            if (extractionJob?.isActive == true) Icons.Default.Cancel else Icons.AutoMirrored.Filled.ArrowBack,
                            if (extractionJob?.isActive == true) "Cancelar extracción" else "Volver",
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            ZipScreenStateV51.Scanning -> {
                CenterZipStatusV51(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    title = "Inspeccionando ZIP…",
                    detail = "Flux Files está validando las rutas y calculando el contenido.",
                )
            }

            is ZipScreenStateV51.Ready -> {
                ZipReadyV51(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    archiveName = entry.name,
                    destinationName = destination.name,
                    scan = current.scan,
                    onExtract = { startExtraction(current.scan) },
                )
            }

            is ZipScreenStateV51.Extracting -> {
                ZipExtractingV51(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    progress = current.progress,
                    onCancel = { extractionJob?.cancel() },
                )
            }

            is ZipScreenStateV51.Done -> {
                ZipDoneV51(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    result = current.result,
                    onBack = onBack,
                )
            }

            is ZipScreenStateV51.Failed -> {
                ZipFailedV51(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    message = current.message,
                    canRetryExtraction = current.scan != null,
                    onRetry = {
                        current.scan?.let(::startExtraction) ?: loadScan()
                    },
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun ZipReadyV51(
    modifier: Modifier,
    archiveName: String,
    destinationName: String,
    scan: ZipScanV51,
    onExtract: () -> Unit,
) {
    Column(modifier) {
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Archive, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${scan.totalEntries} ${if (scan.totalEntries == 1) "entrada" else "entradas"}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            buildString {
                                append(formatBytesZipV51(scan.knownBytes))
                                if (scan.unknownSizes) append(" conocidos · algunas entradas no declaran tamaño")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Se extraerá en una carpeta nueva dentro de “$destinationName”. Si ya existe una carpeta llamada como el ZIP, Flux Files elegirá automáticamente un nombre libre.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onExtract, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Unarchive, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Extraer ${archiveBaseNameV51(archiveName)}")
                }
            }
        }

        if (scan.entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("El ZIP no contiene entradas visibles.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(scan.entries, key = { it.path }) { item ->
                    ListItem(
                        headlineContent = {
                            Text(item.path, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                if (item.isDirectory) "Carpeta"
                                else item.sizeBytes?.let(::formatBytesZipV51) ?: "Tamaño no declarado"
                            )
                        },
                        leadingContent = {
                            Icon(if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description, null)
                        },
                    )
                    HorizontalDivider()
                }
                if (scan.totalEntries > scan.entries.size) {
                    item {
                        Text(
                            "Vista limitada a ${scan.entries.size} entradas de ${scan.totalEntries}.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZipExtractingV51(
    modifier: Modifier,
    progress: ZipProgressV51,
    onCancel: () -> Unit,
) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val fraction = if (progress.totalEntries > 0) {
            progress.completedEntries.toFloat() / progress.totalEntries.toFloat()
        } else 0f
        Icon(Icons.Default.Unarchive, null, Modifier.padding(bottom = 16.dp))
        Text("Extrayendo ZIP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "${progress.completedEntries} de ${progress.totalEntries}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(
            progress.currentPath,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            formatBytesZipV51(progress.extractedBytes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) {
            Icon(Icons.Default.Cancel, null)
            Spacer(Modifier.width(8.dp))
            Text("Cancelar y limpiar")
        }
    }
}

@Composable
private fun ZipDoneV51(
    modifier: Modifier,
    result: ZipExtractionResultV51,
    onBack: () -> Unit,
) {
    Column(
        modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, null)
        Spacer(Modifier.height(16.dp))
        Text("Extracción completada", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "${result.entries} entradas · ${formatBytesZipV51(result.bytes)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text("Guardado en “${result.folderName}”.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Volver a archivos") }
    }
}

@Composable
private fun ZipFailedV51(
    modifier: Modifier,
    message: String,
    canRetryExtraction: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.WarningAmber, null)
        Spacer(Modifier.height(16.dp))
        Text("No se pudo completar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Volver") }
            Button(onClick = onRetry) {
                Text(if (canRetryExtraction) "Reintentar extracción" else "Reintentar")
            }
        }
    }
}

@Composable
private fun CenterZipStatusV51(
    modifier: Modifier,
    title: String,
    detail: String,
) {
    Column(
        modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class SafZipExtractorV51(context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    suspend fun scan(zipUri: Uri): ZipScanV51 = withContext(Dispatchers.IO) {
        var count = 0
        var knownBytes = 0L
        var unknownSizes = false
        val visible = mutableListOf<ZipScanEntryV51>()
        val seenPaths = hashSetOf<String>()

        openZip(zipUri).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                count++
                require(count <= MAX_ZIP_ENTRIES_V51) {
                    "El ZIP supera el límite de $MAX_ZIP_ENTRIES_V51 entradas"
                }

                val path = safeZipPathV51(entry.name)
                require(seenPaths.add(path)) { "El ZIP contiene una ruta duplicada: $path" }
                val size = entry.size.takeIf { it >= 0L }
                if (size == null) {
                    unknownSizes = true
                } else {
                    knownBytes += size
                    require(knownBytes <= MAX_EXTRACTED_BYTES_V51) {
                        "El contenido declarado supera el límite de ${formatBytesZipV51(MAX_EXTRACTED_BYTES_V51)}"
                    }
                }

                if (visible.size < MAX_VISIBLE_ZIP_ENTRIES_V51) {
                    visible += ZipScanEntryV51(path, entry.isDirectory, size)
                }
                zip.closeEntry()
            }
        }

        ZipScanV51(visible, count, knownBytes, unknownSizes)
    }

    suspend fun extract(
        zipUri: Uri,
        treeUri: Uri,
        destination: BrowserLocation,
        archiveName: String,
        onProgress: suspend (ZipProgressV51) -> Unit,
    ): ZipExtractionResultV51 = withContext(Dispatchers.IO) {
        val rootName = uniqueFolderName(treeUri, destination, archiveBaseNameV51(archiveName))
        val destinationUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, destination.documentId)
        val rootUri = checkNotNull(
            DocumentsContract.createDocument(
                resolver,
                destinationUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                rootName,
            )
        ) { "No se pudo crear la carpeta de extracción" }

        var completed = 0
        var extractedBytes = 0L
        val directoryUris = mutableMapOf<String, Uri>("" to rootUri)
        val createdPaths = hashSetOf<String>()
        var success = false

        try {
            val totalEntries = countEntries(zipUri)
            openZip(zipUri).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    val safePath = safeZipPathV51(entry.name)
                    require(createdPaths.add(safePath)) { "El ZIP contiene una ruta duplicada: $safePath" }
                    val segments = safePath.split('/')

                    if (entry.isDirectory) {
                        ensureDirectoryPath(directoryUris, segments, rootUri)
                    } else {
                        val parentSegments = segments.dropLast(1)
                        val parentUri = ensureDirectoryPath(directoryUris, parentSegments, rootUri)
                        val fileName = segments.last()
                        val fileUri = checkNotNull(
                            DocumentsContract.createDocument(
                                resolver,
                                parentUri,
                                mimeTypeForNameV51(fileName),
                                fileName,
                            )
                        ) { "No se pudo crear “$safePath”" }

                        try {
                            val output = checkNotNull(resolver.openOutputStream(fileUri, "w")) {
                                "No se pudo escribir “$safePath”"
                            }
                            output.use { stream ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = zip.read(buffer)
                                    if (read < 0) break
                                    stream.write(buffer, 0, read)
                                    extractedBytes += read
                                    require(extractedBytes <= MAX_EXTRACTED_BYTES_V51) {
                                        "La extracción supera el límite de ${formatBytesZipV51(MAX_EXTRACTED_BYTES_V51)}"
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            runCatching { DocumentsContract.deleteDocument(resolver, fileUri) }
                            throw t
                        }
                    }

                    completed++
                    withContext(Dispatchers.Main.immediate) {
                        onProgress(
                            ZipProgressV51(
                                completedEntries = completed,
                                totalEntries = totalEntries,
                                extractedBytes = extractedBytes,
                                currentPath = safePath,
                            )
                        )
                    }
                    zip.closeEntry()
                }
            }
            success = true
            ZipExtractionResultV51(rootName, completed, extractedBytes)
        } finally {
            if (!success) {
                runCatching { DocumentsContract.deleteDocument(resolver, rootUri) }
            }
        }
    }

    private fun countEntries(zipUri: Uri): Int {
        var count = 0
        openZip(zipUri).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                require(count <= MAX_ZIP_ENTRIES_V51) {
                    "El ZIP supera el límite de $MAX_ZIP_ENTRIES_V51 entradas"
                }
                safeZipPathV51(entry.name)
                zip.closeEntry()
            }
        }
        return count
    }

    private fun ensureDirectoryPath(
        known: MutableMap<String, Uri>,
        segments: List<String>,
        rootUri: Uri,
    ): Uri {
        if (segments.isEmpty()) return rootUri
        var currentUri = rootUri
        val built = mutableListOf<String>()
        segments.forEach { segment ->
            built += segment
            val key = built.joinToString("/")
            val existing = known[key]
            if (existing != null) {
                currentUri = existing
            } else {
                currentUri = checkNotNull(
                    DocumentsContract.createDocument(
                        resolver,
                        currentUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        segment,
                    )
                ) { "No se pudo crear la carpeta “$key”" }
                known[key] = currentUri
            }
        }
        return currentUri
    }

    private fun uniqueFolderName(
        treeUri: Uri,
        destination: BrowserLocation,
        base: String,
    ): String {
        val names = childNames(treeUri, destination)
        val cleanBase = base.ifBlank { "ZIP extraído" }
        if (cleanBase !in names) return cleanBase
        var index = 1
        while (true) {
            val candidate = "$cleanBase ($index)"
            if (candidate !in names) return candidate
            index++
        }
    }

    private fun childNames(treeUri: Uri, destination: BrowserLocation): Set<String> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, destination.documentId)
        val result = hashSetOf<String>()
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let(result::add)
            }
        }
        return result
    }

    private fun openZip(uri: Uri): ZipInputStream {
        val input = checkNotNull(resolver.openInputStream(uri)) { "No se pudo leer el ZIP" }
        return ZipInputStream(input.buffered())
    }
}

private fun safeZipPathV51(rawName: String): String {
    require(rawName.isNotBlank()) { "El ZIP contiene una ruta vacía" }
    require('\u0000' !in rawName) { "El ZIP contiene un nombre no válido" }
    val normalized = rawName.replace('\\', '/').removeSuffix("/")
    require(normalized.isNotBlank()) { "El ZIP contiene una ruta vacía" }
    require(!normalized.startsWith('/')) { "El ZIP contiene una ruta absoluta no segura: $rawName" }
    require(!Regex("^[A-Za-z]:").containsMatchIn(normalized)) {
        "El ZIP contiene una ruta absoluta no segura: $rawName"
    }
    val segments = normalized.split('/')
    require(segments.none { it.isBlank() || it == "." || it == ".." }) {
        "El ZIP contiene una ruta no segura: $rawName"
    }
    require(segments.all { it.length <= 255 }) { "El ZIP contiene un nombre demasiado largo" }
    return segments.joinToString("/")
}

private fun archiveBaseNameV51(name: String): String {
    val trimmed = name.trim()
    return when {
        trimmed.endsWith(".zip", ignoreCase = true) -> trimmed.dropLast(4).ifBlank { "ZIP extraído" }
        else -> trimmed.ifBlank { "ZIP extraído" }
    }
}

private fun mimeTypeForNameV51(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}

private fun formatBytesZipV51(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val unit = 1024.0
    val exponent = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceIn(1, 4)
    val suffix = listOf("B", "KiB", "MiB", "GiB", "TiB")[exponent]
    val value = bytes / unit.pow(exponent.toDouble())
    return if (value >= 10) {
        "%.0f %s".format(Locale.getDefault(), value, suffix)
    } else {
        "%.1f %s".format(Locale.getDefault(), value, suffix)
    }
}
