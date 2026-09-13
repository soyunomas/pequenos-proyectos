package dev.soyunomas.fluxfiles

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Android composition root for storage implementations and platform-only actions. */
object AndroidStorageServices {
    @Volatile
    private var sharedRegistry: StorageBackendRegistry? = null

    fun registry(context: Context): StorageBackendRegistry {
        sharedRegistry?.let { return it }
        return synchronized(this) {
            sharedRegistry ?: StorageBackendRegistry(
                backends = listOf(SafStorageRepository(context.applicationContext)),
                defaultBackendId = SafStorageRepository.SAF_BACKEND,
            ).also { sharedRegistry = it }
        }
    }

    /** Converts the Android SAF picker result at the platform boundary only. */
    fun safRoot(uri: Uri): StorageRootRef =
        StorageRootRef(SafStorageRepository.SAF_BACKEND, uri.toString())

    fun externalFileOpener(context: Context): ExternalFileOpener = AndroidExternalFileOpener(
        context = context,
        adapters = listOf(SafExternalOpenAdapter),
    )
}

fun interface ExternalFileOpener {
    fun open(entry: StorageEntry)
}

private interface AndroidExternalOpenAdapter {
    val backendId: StorageBackendId
    fun uriFor(entry: StorageEntry): Uri?
}

private object SafExternalOpenAdapter : AndroidExternalOpenAdapter {
    override val backendId: StorageBackendId = SafStorageRepository.SAF_BACKEND

    override fun uriFor(entry: StorageEntry): Uri? {
        if (entry.ref.backend != backendId || entry.isDirectory) return null
        return Uri.parse(entry.ref.opaqueId)
    }
}

private class AndroidExternalFileOpener(
    context: Context,
    adapters: Collection<AndroidExternalOpenAdapter>,
) : ExternalFileOpener {
    private val context = context
    private val adaptersByBackend = adapters.associateBy { it.backendId }

    override fun open(entry: StorageEntry) {
        val uri = adaptersByBackend[entry.ref.backend]?.uriFor(entry)
        if (uri == null) {
            Toast.makeText(
                context,
                "Este backend no ofrece apertura externa directa",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, entry.mimeType.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No hay una aplicación compatible para abrir este archivo",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
