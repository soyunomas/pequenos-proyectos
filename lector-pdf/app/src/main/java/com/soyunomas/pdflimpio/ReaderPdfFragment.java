package com.soyunomas.pdflimpio;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.pdf.ExperimentalPdfApi;
import androidx.pdf.PdfDocument;
import androidx.pdf.PdfWriteHandle;
import androidx.pdf.ink.EditablePdfViewerFragment;

@OptIn(markerClass = ExperimentalPdfApi.class)
public class ReaderPdfFragment extends EditablePdfViewerFragment {
    private ReaderEvents listener;

    public void setListener(ReaderEvents listener) {
        this.listener = listener;
    }

    public void beginEditMode() {
        Diagnostics.i("EDIT", "Entering edit mode");
        setEditModeEnabled(true);
    }

    public void discardEditMode() {
        Diagnostics.i("EDIT", "Leaving edit mode without saving drafts=" + hasUnsavedChanges());
        setEditModeEnabled(false);
    }

    public boolean hasDraftChanges() {
        return hasUnsavedChanges();
    }

    public boolean isApplyingChanges() {
        return isApplyEditsInProgress();
    }

    public void applyChanges() {
        Diagnostics.i("EDIT", "applyDraftEdits requested drafts=" + hasUnsavedChanges());
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
            Diagnostics.e("VIEWER", "Could not hide editable toolbox", error);
        }
        Diagnostics.i("VIEWER", "Editable viewer loaded document class=" + document.getClass().getName());
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
        Diagnostics.i("EDIT", "Editable viewer entered edit mode");
        if (listener != null) listener.onEditModeChanged(true);
    }

    @Override
    public void onExitEditMode() {
        super.onExitEditMode();
        Diagnostics.i("EDIT", "Editable viewer exited edit mode");
        if (listener != null) listener.onEditModeChanged(false);
    }

    @Override
    public void onApplyEditsSuccess(@NonNull PdfWriteHandle handle) {
        super.onApplyEditsSuccess(handle);
        Diagnostics.i("EDIT", "Draft edits applied; write handle ready");
        if (listener != null) listener.onEditsReady(handle);
        else {
            try { handle.close(); } catch (Exception ignored) { }
        }
    }

    @Override
    public void onApplyEditsFailed(@NonNull Throwable error) {
        super.onApplyEditsFailed(error);
        Diagnostics.e("EDIT", "Applying draft edits failed", error);
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
