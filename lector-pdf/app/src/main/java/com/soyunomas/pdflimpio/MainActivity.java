package com.soyunomas.pdflimpio;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.pdf.PdfDocument;

public class MainActivity extends AppCompatActivity implements ReaderPdfFragment.Listener {
    private static final int OPEN_PDF = 41;
    private static final String PREFS = "reader";
    private static final String LAST_URI = "last_uri";
    private static final String SELECTION_HINT_DISMISSED = "selection_hint_dismissed";
    private static final String VIEWER_TAG = "pdf_viewer";

    private ReaderPdfFragment readerFragment;
    private TextView titleView;
    private TextView emptyView;
    private Button searchButton;
    private LinearLayout selectionHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(17, 21, 27));
        getWindow().setNavigationBarColor(Color.rgb(17, 21, 27));
        setContentView(buildUi());

        readerFragment = (ReaderPdfFragment) getSupportFragmentManager().findFragmentByTag(VIEWER_TAG);
        if (readerFragment == null) {
            readerFragment = new ReaderPdfFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.pdf_viewer_container, readerFragment, VIEWER_TAG)
                    .commitNow();
        }
        readerFragment.setListener(this);

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            openDocument(intent.getData(), false);
        } else {
            String last = getSharedPreferences(PREFS, MODE_PRIVATE).getString(LAST_URI, null);
            if (last != null) {
                openDocument(Uri.parse(last), false);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            openDocument(intent.getData(), false);
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(238, 240, 243));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(8), dp(12), dp(8));
        top.setBackgroundColor(Color.rgb(17, 21, 27));

        titleView = new TextView(this);
        titleView.setText("PDF Limpio");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(18);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleView.setContentDescription("Documento abierto");
        top.addView(titleView, new LinearLayout.LayoutParams(0, dp(48), 1f));

        searchButton = new Button(this);
        searchButton.setText("Buscar");
        searchButton.setAllCaps(false);
        searchButton.setEnabled(false);
        searchButton.setContentDescription("Buscar texto en el PDF");
        searchButton.setOnClickListener(v -> startSearch());
        top.addView(searchButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        Button openButton = new Button(this);
        openButton.setText("Abrir");
        openButton.setAllCaps(false);
        openButton.setContentDescription("Abrir otro PDF");
        openButton.setOnClickListener(v -> choosePdf());
        top.addView(openButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        root.addView(top);

        selectionHint = new LinearLayout(this);
        selectionHint.setGravity(Gravity.CENTER_VERTICAL);
        selectionHint.setPadding(dp(14), dp(8), dp(8), dp(8));
        selectionHint.setBackgroundColor(Color.rgb(255, 244, 205));
        selectionHint.setVisibility(View.GONE);

        TextView hintText = new TextView(this);
        hintText.setText("Para copiar: mantén pulsado sobre el texto, ajusta los tiradores y toca Copiar.");
        hintText.setTextColor(Color.rgb(59, 48, 16));
        hintText.setTextSize(15);
        selectionHint.addView(hintText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button closeHint = new Button(this);
        closeHint.setText("×");
        closeHint.setTextSize(22);
        closeHint.setContentDescription("Cerrar consejo de selección");
        closeHint.setOnClickListener(v -> dismissSelectionHint());
        selectionHint.addView(closeHint, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(selectionHint);

        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(Color.rgb(224, 226, 230));

        FrameLayout viewerContainer = new FrameLayout(this);
        viewerContainer.setId(R.id.pdf_viewer_container);
        content.addView(viewerContainer, new FrameLayout.LayoutParams(-1, -1));

        emptyView = new TextView(this);
        emptyView.setText("Abre un PDF para empezar\n\nMantén pulsado sobre el texto para seleccionarlo y copiarlo.\n\nSin anuncios · sin cuenta · sin Internet");
        emptyView.setTextColor(Color.rgb(66, 72, 80));
        emptyView.setTextSize(17);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), dp(32), dp(32), dp(32));
        emptyView.setBackgroundColor(Color.rgb(238, 240, 243));
        content.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));

        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private void choosePdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, OPEN_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_PDF || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        openDocument(uri, true);
    }

    private void openDocument(Uri uri, boolean remember) {
        if (readerFragment == null || uri == null) {
            return;
        }
        titleView.setText(displayName(uri));
        searchButton.setEnabled(false);
        emptyView.setVisibility(View.GONE);
        selectionHint.setVisibility(View.GONE);
        try {
            readerFragment.setDocumentUri(uri);
            if (remember) {
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit()
                        .putString(LAST_URI, uri.toString())
                        .apply();
            }
        } catch (RuntimeException error) {
            showOpenError(error);
        }
    }

    private void startSearch() {
        if (readerFragment == null || !searchButton.isEnabled()) {
            return;
        }
        readerFragment.setTextSearchActive(true);
    }

    @Override
    public void onDocumentLoaded(PdfDocument document) {
        searchButton.setEnabled(true);
        emptyView.setVisibility(View.GONE);
        showSelectionHintIfNeeded();
    }

    @Override
    public void onDocumentLoadError(Throwable error) {
        showOpenError(error);
    }

    private void showOpenError(Throwable error) {
        searchButton.setEnabled(false);
        selectionHint.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "Archivo no compatible o permiso no disponible.";
        }
        emptyView.setText("No se pudo abrir este PDF.\n\n" + message);
    }

    private void showSelectionHintIfNeeded() {
        boolean dismissed = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(SELECTION_HINT_DISMISSED, false);
        if (!dismissed) {
            selectionHint.setVisibility(View.VISIBLE);
        }
    }

    private void dismissSelectionHint() {
        selectionHint.setVisibility(View.GONE);
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(SELECTION_HINT_DISMISSED, true)
                .apply();
    }

    private String displayName(Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    name = cursor.getString(0);
                }
            } catch (Exception ignored) {
            }
        }
        if (name == null || name.trim().isEmpty()) {
            name = uri.getLastPathSegment();
        }
        return name == null || name.trim().isEmpty() ? "PDF Limpio" : name;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
