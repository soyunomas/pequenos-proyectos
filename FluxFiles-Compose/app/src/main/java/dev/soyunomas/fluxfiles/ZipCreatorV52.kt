package dev.soyunomas.fluxfiles

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ln
import kotlin.math.pow

private const val MAX_ARCHIVE_ENTRIES_V52 = 10_000
private const val MAX_ARCHIVE_SOURCE_BYTES_V52 = 4L * 1024L * 1024L * 1024L

data class ZipCreateScanV52(
    val entries: Int,
    val files: Int,
    val directories: Int,
    val knownBytes: Long,
    val hasUnknownSizes: Boolean,
)

data class ZipCreateProgressV52(
    val completedEntries: Int,
    val totalEntries: Int,
    val processedBytes: Long,
    val currentPath: String,
)

data class ZipCreateResultV52(
    val archiveName: String,
    val entries: Int,
    val sourceBytes: Long,
)

private sealed interface ZipCreateStateV52 {
    data object Scanning : ZipCreateStateV52
    data class Ready(val scan: ZipCreateScanV52) : ZipCreateStateV52
    data class Creating(val scan: ZipCreateScanV52, val progress: ZipCreateProgressV52) : ZipCreateStateV52
    data class Done(val result: ZipCreateResultV52) : ZipCreateStateV52
    data class Failed(val message: String, val scan: ZipCreateScanV52? = null) : ZipCreateStateV52
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipCreatorV52(
    sources: List<StorageEntry>,
    destination: BrowserLocation,
    fileSystem: StorageFileSystem,
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val creator = remember(fileSystem) { StorageZipCreatorV52(fileSystem) }
    val sourceRefs = remember(sources) { sources.map { it.ref } }
    var archiveName by remember(sourceRefs) {
        mutableStateOf(defaultArchiveNameV52(sources))
    }
    var state by remember(sourceRefs) {
        mutableStateOf<ZipCreateStateV52>(ZipCreateStateV52.Scanning)
    }
    var creationJob by remember(sourceRefs) { mutableStateOf<Job?>(null) }

    fun scan() {
        state = ZipCreateStateV52.Scanning
        scope.launch {
            state = runCatching { creator.scan(sources) }
                .fold(
                    onSuccess = { ZipCreateStateV52.Ready(it) },
                    onFailure = {
                        ZipCreateStateV52.Failed(
                            it.message?.takeIf(String::isNotBlank) ?: "No se pudo analizar la selección"
                        )
                    },
                )
        }
    }

    fun create(scan: ZipCreateScanV52) {
        if (creationJob?.isActive == true) return
        creationJob = scope.launch {
            state = ZipCreateStateV52.Creating(
                scan,
                ZipCreateProgressV52(0, scan.entries, 0L, "Preparando…"),
            )
            try {
                val result = creator.create(
                    destination = destination,
                    sources = sources,
                    requestedName = archiveName,
                ) { progress ->
                    state = ZipCreateStateV52.Creating(scan, progress)
                }
                state = ZipCreateStateV52.Done(result)
                onCreated()
            } catch (_: CancellationException) {
                state = ZipCreateStateV52.Ready(scan)
            } catch (t: Throwable) {
                state = ZipCreateStateV52.Failed(
                    t.message?.takeIf(String::isNotBlank) ?: "No se pudo crear el ZIP",
                    scan,
                )
            } finally {
                creationJob = null
            }
        }
    }

    LaunchedEffect(sourceRefs) { scan() }

    BackHandler {
        if (creationJob?.isActive == true) creationJob?.cancel() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Crear ZIP")
                        Text(
                            "${sources.size} ${if (sources.size == 1) "seleccionado" else "seleccionados"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (creationJob?.isActive == true) creationJob?.cancel() else onBack()
                        }
                    ) {
                        Icon(
                            if (creationJob?.isActive == true) Icons.Default.Cancel else Icons.AutoMirrored.Filled.ArrowBack,
                            if (creationJob?.isActive == true) "Cancelar compresión" else "Volver",
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            ZipCreateStateV52.Scanning -> {
                CenterCreateStatusV52(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    title = "Analizando selección…",
                    detail = "Flux Files está calculando los archivos y carpetas que se incluirán.",
                )
            }

            is ZipCreateStateV52.Ready -> {
                ReadyCreateV52(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    destinationName = destination.name,
                    scan = current.scan,
                    archiveName = archiveName,
                    onArchiveNameChange = { archiveName = it },
                    onCreate = { create(current.scan) },
                )
            }

            is ZipCreateStateV52.Creating -> {
                CreatingV52(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    progress = current.progress,
                    onCancel = { creationJob?.cancel() },
                )
            }

            is ZipCreateStateV52.Done -> {
                DoneCreateV52(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    result = current.result,
                    onBack = onBack,
                )
            }

            is ZipCreateStateV52.Failed -> {
                FailedCreateV52(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    message = current.message,
                    canRetryCreation = current.scan != null,
                    onRetry = { current.scan?.let(::create) ?: scan() },
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun ReadyCreateV52(
    modifier: Modifier,
    destinationName: String,
    scan: ZipCreateScanV52,
    archiveName: String,
    onArchiveNameChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val normalized = normalizeArchiveNameV52(archiveName)
    val valid = normalized != null

    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Archive, null)
        Spacer(Modifier.height(16.dp))
        Text("Preparar archivo ZIP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "${scan.files} archivos · ${scan.directories} carpetas · ${formatBytesCreateV52(scan.knownBytes)}${if (scan.hasUnknownSizes) " conocidos" else ""}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = archiveName,
            onValueChange = onArchiveNameChange,
            label = { Text("Nombre del ZIP") },
            singleLine = true,
            isError = archiveName.isNotBlank() && !valid,
            supportingText = {
                Text(
                    if (archiveName.isBlank()) {
                        "Escribe un nombre."
                    } else if (!valid) {
                        "El nombre no puede contener /, \\ ni segmentos especiales."
                    } else {
                        "Se guardará como “$normalized” dentro de “$destinationName”."
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Si ya existe un archivo con ese nombre, Flux Files elegirá automáticamente un nombre libre. El ZIP parcial se elimina si cancelas o se produce un error.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onCreate,
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Archive, null)
            Spacer(Modifier.width(8.dp))
            Text("Crear ZIP")
        }
    }
}

@Composable
private fun CreatingV52(
    modifier: Modifier,
    progress: ZipCreateProgressV52,
    onCancel: () -> Unit,
) {
    val fraction = if (progress.totalEntries > 0) {
        progress.completedEntries.toFloat() / progress.totalEntries.toFloat()
    } else 0f

    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Archive, null)
        Spacer(Modifier.height(16.dp))
        Text("Creando ZIP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
            formatBytesCreateV52(progress.processedBytes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) {
            Icon(Icons.Default.Cancel, null)
            Spacer(Modifier.width(8.dp))
            Text("Cancelar y borrar ZIP parcial")
        }
    }
}

@Composable
private fun DoneCreateV52(
    modifier: Modifier,
    result: ZipCreateResultV52,
    onBack: () -> Unit,
) {
    Column(
        modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, null)
        Spacer(Modifier.height(16.dp))
        Text("ZIP creado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(result.archiveName, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "${result.entries} entradas · ${formatBytesCreateV52(result.sourceBytes)} procesados",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Volver a archivos") }
    }
}

@Composable
private fun FailedCreateV52(
    modifier: Modifier,
    message: String,
    canRetryCreation: Boolean,
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
        Text("No se pudo crear el ZIP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Volver") }
            Button(onClick = onRetry) {
                Text(if (canRetryCreation) "Reintentar" else "Analizar otra vez")
            }
        }
    }
}

@Composable
private fun CenterCreateStatusV52(
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

private class StorageZipCreatorV52(
    private val fileSystem: StorageFileSystem,
) {
    suspend fun scan(sources: List<StorageEntry>): ZipCreateScanV52 = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty()) { "No hay elementos seleccionados" }
        val accumulator = ScanAccumulatorV52()
        val seenPaths = hashSetOf<String>()
        sources.forEach { source ->
            scanEntry(source, safeComponentV52(source.name), accumulator, seenPaths)
        }
        accumulator.toResult()
    }

    suspend fun create(
        destination: BrowserLocation,
        sources: List<StorageEntry>,
        requestedName: String,
        onProgress: suspend (ZipCreateProgressV52) -> Unit,
    ): ZipCreateResultV52 = withContext(Dispatchers.IO) {
        require(sources.isNotEmpty()) { "No hay elementos seleccionados" }
        val normalized = requireNotNull(normalizeArchiveNameV52(requestedName)) { "Nombre de ZIP no válido" }
        val outputName = uniqueArchiveName(destination, normalized)
        val outputEntry = fileSystem.createFile(destination, outputName, "application/zip")

        val scan = scan(sources)
        var completedEntries = 0
        var processedBytes = 0L
        val emittedPaths = hashSetOf<String>()
        var success = false

        try {
            ZipOutputStream(fileSystem.openOutput(outputEntry).buffered()).use { zip ->
                sources.forEach { source ->
                    val topPath = safeComponentV52(source.name)
                    writeEntry(
                        source = source,
                        path = topPath,
                        zip = zip,
                        emittedPaths = emittedPaths,
                        onEntryComplete = { path, bytes ->
                            completedEntries++
                            processedBytes += bytes
                            require(processedBytes <= MAX_ARCHIVE_SOURCE_BYTES_V52) {
                                "La selección supera el límite de ${formatBytesCreateV52(MAX_ARCHIVE_SOURCE_BYTES_V52)}"
                            }
                            withContext(Dispatchers.Main.immediate) {
                                onProgress(
                                    ZipCreateProgressV52(
                                        completedEntries = completedEntries,
                                        totalEntries = scan.entries,
                                        processedBytes = processedBytes,
                                        currentPath = path,
                                    )
                                )
                            }
                        },
                    )
                }
            }
            success = true
            ZipCreateResultV52(outputName, completedEntries, processedBytes)
        } finally {
            if (!success) runCatching { fileSystem.delete(outputEntry) }
        }
    }

    private suspend fun scanEntry(
        source: StorageEntry,
        path: String,
        accumulator: ScanAccumulatorV52,
        seenPaths: MutableSet<String>,
    ) {
        currentCoroutineContext().ensureActive()
        require(seenPaths.add(path)) { "Hay dos elementos con la misma ruta en el ZIP: $path" }
        accumulator.entries++
        require(accumulator.entries <= MAX_ARCHIVE_ENTRIES_V52) {
            "La selección supera el límite de $MAX_ARCHIVE_ENTRIES_V52 entradas"
        }

        if (source.isDirectory) {
            accumulator.directories++
            fileSystem.listChildren(BrowserLocation(source.ref, source.name)).forEach { child ->
                scanEntry(
                    child,
                    "$path/${safeComponentV52(child.name)}",
                    accumulator,
                    seenPaths,
                )
            }
        } else {
            accumulator.files++
            val size = source.sizeBytes
            if (size == null || size < 0L) {
                accumulator.hasUnknownSizes = true
            } else {
                accumulator.knownBytes += size
                require(accumulator.knownBytes <= MAX_ARCHIVE_SOURCE_BYTES_V52) {
                    "La selección supera el límite de ${formatBytesCreateV52(MAX_ARCHIVE_SOURCE_BYTES_V52)}"
                }
            }
        }
    }

    private suspend fun writeEntry(
        source: StorageEntry,
        path: String,
        zip: ZipOutputStream,
        emittedPaths: MutableSet<String>,
        onEntryComplete: suspend (String, Long) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        val zipPath = if (source.isDirectory) "$path/" else path
        require(emittedPaths.add(zipPath)) { "Hay dos elementos con la misma ruta en el ZIP: $zipPath" }

        val zipEntry = ZipEntry(zipPath).apply {
            source.modifiedAtMillis?.takeIf { it > 0L }?.let { time = it }
        }
        zip.putNextEntry(zipEntry)

        var bytes = 0L
        try {
            if (!source.isDirectory) {
                fileSystem.openInput(source).use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = stream.read(buffer)
                        if (read < 0) break
                        zip.write(buffer, 0, read)
                        bytes += read
                        require(bytes <= MAX_ARCHIVE_SOURCE_BYTES_V52) {
                            "Un archivo supera el límite de ${formatBytesCreateV52(MAX_ARCHIVE_SOURCE_BYTES_V52)}"
                        }
                    }
                }
            }
        } finally {
            zip.closeEntry()
        }

        onEntryComplete(path, bytes)

        if (source.isDirectory) {
            fileSystem.listChildren(BrowserLocation(source.ref, source.name)).forEach { child ->
                writeEntry(
                    source = child,
                    path = "$path/${safeComponentV52(child.name)}",
                    zip = zip,
                    emittedPaths = emittedPaths,
                    onEntryComplete = onEntryComplete,
                )
            }
        }
    }

    private fun uniqueArchiveName(destination: BrowserLocation, requested: String): String {
        val names = fileSystem.listChildren(destination).mapTo(hashSetOf()) { it.name }
        if (requested !in names) return requested
        val stem = requested.dropLast(4)
        var index = 1
        while (true) {
            val candidate = "$stem ($index).zip"
            if (candidate !in names) return candidate
            index++
        }
    }
}

private class ScanAccumulatorV52 {
    var entries: Int = 0
    var files: Int = 0
    var directories: Int = 0
    var knownBytes: Long = 0L
    var hasUnknownSizes: Boolean = false

    fun toResult() = ZipCreateScanV52(
        entries = entries,
        files = files,
        directories = directories,
        knownBytes = knownBytes,
        hasUnknownSizes = hasUnknownSizes,
    )
}

private fun safeComponentV52(name: String): String {
    val value = name.trim()
    require(value.isNotBlank()) { "Hay un elemento sin nombre válido" }
    require(value != "." && value != "..") { "Nombre no válido: $name" }
    require('/' !in value && '\\' !in value && '\u0000' !in value) { "Nombre no válido para ZIP: $name" }
    require(value.length <= 255) { "Nombre demasiado largo: $name" }
    return value
}

private fun normalizeArchiveNameV52(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    if ('/' in trimmed || '\\' in trimmed || '\u0000' in trimmed) return null
    if (trimmed == "." || trimmed == "..") return null
    val withExtension = if (trimmed.endsWith(".zip", ignoreCase = true)) trimmed else "$trimmed.zip"
    if (withExtension.length > 255) return null
    return withExtension
}

private fun defaultArchiveNameV52(sources: List<StorageEntry>): String {
    if (sources.size != 1) return "Archivos.zip"
    val name = sources.first().name.trim()
    val base = if (name.endsWith(".zip", ignoreCase = true)) {
        name.dropLast(4).ifBlank { "Archivo" }
    } else {
        name.substringBeforeLast('.', name).ifBlank { "Archivo" }
    }
    return "$base.zip"
}

private fun formatBytesCreateV52(bytes: Long): String {
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
