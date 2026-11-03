package com.soyunomas.foliopdf;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Copies an unmodified PDF without rendering or re-encoding its pages. */
final class PdfCopyBridge {
    interface Callback {
        void onComplete(Throwable error);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private PdfCopyBridge() { }

    static void copy(@NonNull ContentResolver resolver,
                     @NonNull Uri source,
                     @NonNull Uri destination,
                     @NonNull Callback callback) {
        EXECUTOR.execute(() -> {
            Throwable failure = null;
            try {
                if (source.equals(destination)) {
                    throw new IOException("El origen y el destino son el mismo archivo");
                }
                try (InputStream input = resolver.openInputStream(source)) {
                    if (input == null) throw new IOException("No se pudo leer el PDF de origen");
                    try (OutputStream output = resolver.openOutputStream(destination, "wt")) {
                        if (output == null) throw new IOException("No se pudo abrir el archivo de destino");
                        byte[] buffer = new byte[64 * 1024];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }
                        output.flush();
                    }
                }
            } catch (Throwable error) {
                failure = error;
            }
            callback.onComplete(failure);
        });
    }
}
