package com.soyunomas.pdflimpio;

import androidx.annotation.NonNull;
import androidx.pdf.PdfDocument;
import androidx.pdf.viewer.fragment.PdfViewerFragment;

public class ReaderPdfFragment extends PdfViewerFragment {
    public interface Listener {
        void onDocumentLoaded(PdfDocument document);
        void onDocumentLoadError(Throwable error);
    }

    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onLoadDocumentSuccess(@NonNull PdfDocument document) {
        super.onLoadDocumentSuccess(document);
        setToolboxVisible(false);
        if (listener != null) {
            listener.onDocumentLoaded(document);
        }
    }

    @Override
    public void onLoadDocumentError(@NonNull Throwable error) {
        super.onLoadDocumentError(error);
        if (listener != null) {
            listener.onDocumentLoadError(error);
        }
    }
}
