package com.soyunomas.pdflimpio;

import androidx.annotation.NonNull;
import androidx.pdf.PdfDocument;
import androidx.pdf.viewer.fragment.PdfViewerFragment;

public class BasicReaderPdfFragment extends PdfViewerFragment {
    private ReaderEvents listener;

    public void setListener(ReaderEvents listener) {
        this.listener = listener;
    }

    @Override
    public void onLoadDocumentSuccess(@NonNull PdfDocument document) {
        super.onLoadDocumentSuccess(document);
        try {
            setToolboxVisible(false);
        } catch (RuntimeException error) {
            Diagnostics.e("VIEWER", "Could not hide toolbox", error);
        }
        Diagnostics.i("VIEWER", "Basic viewer loaded document class=" + document.getClass().getName());
        if (listener != null) listener.onDocumentLoaded(document);
    }

    @Override
    public void onLoadDocumentError(@NonNull Throwable error) {
        super.onLoadDocumentError(error);
        if (isCancellation(error)) {
            Diagnostics.w("VIEWER", "Ignoring cancellation from detached basic viewer: " + error);
            return;
        }
        Diagnostics.e("VIEWER", "Basic viewer load error", error);
        if (listener != null) listener.onDocumentLoadError(error);
    }

    private boolean isCancellation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.contains("CancellationException") || name.contains("JobCancellationException")) return true;
            current = current.getCause();
        }
        return false;
    }
}
