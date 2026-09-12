package com.soyunomas.pdflimpio;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
        Diagnostics.i("FRAGMENT", "listener attached=" + (listener != null));
    }

    @Override
    public void onAttach(@NonNull Context context) {
        Diagnostics.init(context);
        Diagnostics.i("FRAGMENT", "onAttach context=" + context.getClass().getName());
        super.onAttach(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Diagnostics.i("FRAGMENT", "onCreate savedInstanceState=" + (savedInstanceState != null));
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        long start = android.os.SystemClock.elapsedRealtime();
        Diagnostics.i("FRAGMENT", "onCreateView BEGIN container=" + (container == null ? "null" : container.getClass().getName()));
        try {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            Diagnostics.i("FRAGMENT", "onCreateView SUCCESS elapsedMs="
                    + (android.os.SystemClock.elapsedRealtime() - start)
                    + " view=" + (view == null ? "null" : view.getClass().getName()));
            return view;
        } catch (RuntimeException error) {
            Diagnostics.e("FRAGMENT", "onCreateView FAILED elapsedMs="
                    + (android.os.SystemClock.elapsedRealtime() - start), error);
            throw error;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Diagnostics.i("FRAGMENT", "onViewCreated root=" + view.getClass().getName()
                + " width=" + view.getWidth() + " height=" + view.getHeight());
    }

    @Override
    public void onStart() {
        super.onStart();
        Diagnostics.i("FRAGMENT", "onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Diagnostics.i("FRAGMENT", "onResume");
    }

    @Override
    public void onPause() {
        Diagnostics.i("FRAGMENT", "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Diagnostics.i("FRAGMENT", "onStop");
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        Diagnostics.i("FRAGMENT", "onDestroyView");
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Diagnostics.i("FRAGMENT", "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onLoadDocumentSuccess(@NonNull PdfDocument document) {
        Diagnostics.i("VIEWER", "onLoadDocumentSuccess documentClass=" + document.getClass().getName());
        super.onLoadDocumentSuccess(document);
        try {
            setToolboxVisible(false);
            Diagnostics.i("VIEWER", "toolbox hidden");
        } catch (RuntimeException error) {
            Diagnostics.e("VIEWER", "setToolboxVisible(false) failed", error);
        }
        if (listener != null) listener.onDocumentLoaded(document);
        else Diagnostics.w("VIEWER", "success callback has no listener");
    }

    @Override
    public void onLoadDocumentError(@NonNull Throwable error) {
        Diagnostics.e("VIEWER", "onLoadDocumentError", error);
        super.onLoadDocumentError(error);
        if (listener != null) listener.onDocumentLoadError(error);
        else Diagnostics.w("VIEWER", "error callback has no listener");
    }
}
