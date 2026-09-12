package com.soyunomas.pdflimpio;

import androidx.pdf.PdfDocument;
import androidx.pdf.PdfWriteHandle;

interface ReaderEvents {
    void onDocumentLoaded(PdfDocument document);
    void onDocumentLoadError(Throwable error);
    void onEditModeChanged(boolean enabled);
    void onEditsReady(PdfWriteHandle handle);
    void onApplyEditsFailed(Throwable error);
}
