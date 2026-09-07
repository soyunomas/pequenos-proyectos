package com.soyunomas.pdflimpio;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.io.IOException;

final class PdfDocumentController implements Closeable {
    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;

    synchronized void open(ContentResolver resolver, Uri uri) throws IOException {
        close();
        descriptor = resolver.openFileDescriptor(uri, "r");
        if (descriptor == null) throw new IOException("No se pudo abrir el archivo");
        try {
            renderer = new PdfRenderer(descriptor);
            if (renderer.getPageCount() == 0) throw new IOException("El PDF no contiene páginas");
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    synchronized boolean isOpen() {
        return renderer != null;
    }

    synchronized int pageCount() {
        return renderer == null ? 0 : renderer.getPageCount();
    }

    synchronized Bitmap renderPage(int index, int viewportWidth, int viewportHeight) throws IOException {
        if (renderer == null) throw new IOException("No hay ningún PDF abierto");
        if (index < 0 || index >= renderer.getPageCount()) throw new IOException("Página fuera de rango");
        PdfRenderer.Page page = renderer.openPage(index);
        try {
            float scale = Math.min((float) viewportWidth / page.getWidth(), (float) viewportHeight / page.getHeight());
            scale = Math.max(scale, 1f);
            int width = Math.max(1, Math.round(page.getWidth() * scale));
            int height = Math.max(1, Math.round(page.getHeight() * scale));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } finally {
            page.close();
        }
    }

    @Override
    public synchronized void close() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (descriptor != null) {
            try { descriptor.close(); } catch (IOException ignored) { }
            descriptor = null;
        }
    }
}
