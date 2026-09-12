package com.soyunomas.pdflimpio;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.pdf.ExperimentalPdfApi;
import androidx.pdf.PdfDocument;
import androidx.pdf.PdfWriteHandle;
import androidx.pdf.ink.EditablePdfViewerFragment;
import androidx.pdf.view.PdfView;

@OptIn(markerClass = ExperimentalPdfApi.class)
public class ReaderPdfFragment extends EditablePdfViewerFragment {
    private ReaderEvents listener;

    public void setListener(ReaderEvents listener) {
        this.listener = listener;
    }

    @Override
    public void onPdfViewCreated(@NonNull PdfView pdfView) {
        super.onPdfViewCreated(pdfView);
        try {
            // Form filling is a separate interaction path from annotation mode.
            // Keep it enabled during normal reading so tapping a PDF form widget
            // opens the appropriate native control instead of drawing ink.
            pdfView.setFormFillingEnabled(true);
            setToolboxVisible(false);
            Diagnostics.i("FORM", "Inline form filling enabled; annotation toolbox hidden");
        } catch (RuntimeException error) {
            Diagnostics.e("FORM", "Could not enable inline form filling", error);
        }
    }

    public void discardEditMode() {
        Diagnostics.i("FORM", "Leaving form edit session without saving drafts=" + hasUnsavedChanges());
        setEditModeEnabled(false);
    }

    public boolean hasDraftChanges() {
        return hasUnsavedChanges();
    }

    public boolean isApplyingChanges() {
        return isApplyEditsInProgress();
    }

    public void applyChanges() {
        Diagnostics.i("FORM", "applyDraftEdits requested drafts=" + hasUnsavedChanges());
        applyDraftEdits();
    }

    public void finishEditModeAfterSave() {
        setEditModeEnabled(false);
    }

    @Override
    public void onLoadDocumentSuccess(@NonNull PdfDocument document) {
        super.onLoadDocumentSuccess(document);
        try {
            setToolboxVisible(false);
        } catch (RuntimeException error) {
            Diagnostics.e("VIEWER", "Could not keep annotation toolbox hidden", error);
        }
        Diagnostics.i("VIEWER", "Editable viewer loaded document class=" + document.getClass().getName()
                + " formType=" + document.getFormType());
        if (listener != null) listener.onDocumentLoaded(document);
    }

    @Override
    public void onLoadDocumentError(@NonNull Throwable error) {
        super.onLoadDocumentError(error);
        if (isCancellation(error)) {
            Diagnostics.w("VIEWER", "Ignoring cancellation from detached editable viewer: " + error);
            return;
        }
        Diagnostics.e("VIEWER", "Editable viewer load error", error);
        if (listener != null) listener.onDocumentLoadError(error);
    }

    @Override
    public void onEnterEditMode() {
        super.onEnterEditMode();
        Diagnostics.i("FORM", "Form interaction entered edit session");
        if (listener != null) listener.onEditModeChanged(true);
    }

    @Override
    public void onExitEditMode() {
        super.onExitEditMode();
        Diagnostics.i("FORM", "Form edit session exited");
        if (listener != null) listener.onEditModeChanged(false);
    }

    @Override
    public void onApplyEditsSuccess(@NonNull PdfWriteHandle handle) {
        super.onApplyEditsSuccess(handle);
        Diagnostics.i("FORM", "Form edits applied; write handle ready");
        if (listener != null) listener.onEditsReady(handle);
        else {
            try { handle.close(); } catch (Exception ignored) { }
        }
    }

    @Override
    public void onApplyEditsFailed(@NonNull Throwable error) {
        super.onApplyEditsFailed(error);
        Diagnostics.e("FORM", "Applying form edits failed", error);
        if (listener != null) listener.onApplyEditsFailed(error);
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
