package com.soyunomas.pdflimpio;

import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
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

import java.util.List;

public class MainActivity extends AppCompatActivity implements ReaderPdfFragment.Listener {
    private static final int OPEN_PDF = 41;
    private static final String PREFS = "reader";
    private static final String LAST_URI = "last_uri";
    private static final String SELECTION_HINT_DISMISSED = "selection_hint_dismissed";
    private static final String VIEWER_TAG = "pdf_viewer";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ReaderPdfFragment readerFragment;
    private TextView titleView;
    private TextView emptyView;
    private Button searchButton;
    private LinearLayout selectionHint;
    private int openGeneration;
    private long openStartedAt;
    private Uri activeUri;
    private boolean documentLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Diagnostics.init(this);
        Diagnostics.i("ACTIVITY", "onCreate savedInstanceState=" + (savedInstanceState != null)
                + " intent=" + describeIntent(getIntent()));
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(17, 21, 27));
        getWindow().setNavigationBarColor(Color.rgb(17, 21, 27));
        setContentView(buildUi());

        readerFragment = (ReaderPdfFragment) getSupportFragmentManager().findFragmentByTag(VIEWER_TAG);
        Diagnostics.i("FRAGMENT", "existing fragment=" + (readerFragment != null));
        if (readerFragment == null) {
            readerFragment = new ReaderPdfFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.pdf_viewer_container, readerFragment, VIEWER_TAG)
                    .commitNow();
            Diagnostics.i("FRAGMENT", "new ReaderPdfFragment committed synchronously");
        }
        readerFragment.setListener(this);

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Diagnostics.i("OPEN", "Launching from ACTION_VIEW");
            openDocument(intent.getData(), false);
        } else {
            String last = getSharedPreferences(PREFS, MODE_PRIVATE).getString(LAST_URI, null);
            Diagnostics.i("OPEN", "No ACTION_VIEW. rememberedUri=" + (last != null));
            if (last != null) openDocument(Uri.parse(last), false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Diagnostics.i("ACTIVITY", "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Diagnostics.i("ACTIVITY", "onResume");
    }

    @Override
    protected void onPause() {
        Diagnostics.i("ACTIVITY", "onPause loaded=" + documentLoaded + " activeUri=" + safeUri(activeUri));
        super.onPause();
    }

    @Override
    protected void onStop() {
        Diagnostics.i("ACTIVITY", "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Diagnostics.i("ACTIVITY", "onDestroy finishing=" + isFinishing() + " changingConfig=" + isChangingConfigurations());
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Diagnostics.i("ACTIVITY", "onNewIntent " + describeIntent(intent));
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
        top.setPadding(dp(8), dp(8), dp(8), dp(8));
        top.setBackgroundColor(Color.rgb(17, 21, 27));

        titleView = new TextView(this);
        titleView.setText("PDF Limpio");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(17);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleView.setContentDescription("Documento abierto");
        top.addView(titleView, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button logButton = compactButton("Log", "Abrir registro de diagnóstico");
        logButton.setOnClickListener(v -> {
            Diagnostics.i("UI", "Log button pressed");
            startActivity(new Intent(this, DiagnosticsActivity.class));
        });
        top.addView(logButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        searchButton = compactButton("Buscar", "Buscar texto en el PDF");
        searchButton.setEnabled(false);
        searchButton.setOnClickListener(v -> startSearch());
        top.addView(searchButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        Button openButton = compactButton("Abrir", "Abrir otro PDF");
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

        Button closeHint = compactButton("×", "Cerrar consejo de selección");
        closeHint.setTextSize(22);
        closeHint.setOnClickListener(v -> dismissSelectionHint());
        selectionHint.addView(closeHint, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(selectionHint);

        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(Color.rgb(224, 226, 230));

        FrameLayout viewerContainer = new FrameLayout(this);
        viewerContainer.setId(R.id.pdf_viewer_container);
        content.addView(viewerContainer, new FrameLayout.LayoutParams(-1, -1));

        emptyView = new TextView(this);
        emptyView.setText("Abre un PDF para empezar\n\nMantén pulsado sobre el texto para seleccionarlo y copiarlo.\n\nSi un documento no termina de abrirse, toca Log y luego Copiar todo.");
        emptyView.setTextColor(Color.rgb(66, 72, 80));
        emptyView.setTextSize(17);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), dp(32), dp(32), dp(32));
        emptyView.setBackgroundColor(Color.rgb(238, 240, 243));
        content.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));

        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private Button compactButton(String text, String description) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setContentDescription(description);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private void choosePdf() {
        Diagnostics.i("UI", "Open button pressed");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Diagnostics.i("PICKER", "startActivityForResult type=application/pdf flags=0x" + Integer.toHexString(intent.getFlags()));
        startActivityForResult(intent, OPEN_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Diagnostics.i("PICKER", "result requestCode=" + requestCode + " resultCode=" + resultCode
                + " data=" + describeIntent(data));
        if (requestCode != OPEN_PDF || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Diagnostics.i("PERMISSION", "Persistable read permission granted uri=" + safeUri(uri));
        } catch (SecurityException error) {
            Diagnostics.e("PERMISSION", "Could not persist URI permission uri=" + safeUri(uri), error);
        }
        openDocument(uri, true);
    }

    private void openDocument(Uri uri, boolean remember) {
        if (readerFragment == null || uri == null) {
            Diagnostics.w("OPEN", "openDocument ignored fragmentNull=" + (readerFragment == null) + " uriNull=" + (uri == null));
            return;
        }
        activeUri = uri;
        documentLoaded = false;
        openStartedAt = SystemClock.elapsedRealtime();
        final int generation = ++openGeneration;

        String name = displayName(uri);
        titleView.setText(name);
        searchButton.setEnabled(false);
        emptyView.setVisibility(View.GONE);
        selectionHint.setVisibility(View.GONE);

        Diagnostics.i("OPEN", "BEGIN generation=" + generation + " remember=" + remember
                + " name=" + name + " uri=" + safeUri(uri));
        logUriDiagnostics(uri);
        Diagnostics.memory("before-setDocumentUri generation=" + generation);

        try {
            readerFragment.setDocumentUri(uri);
            Diagnostics.i("OPEN", "setDocumentUri returned generation=" + generation
                    + " elapsedMs=" + elapsed());
            if (remember) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(LAST_URI, uri.toString()).apply();
                Diagnostics.i("OPEN", "URI saved as last document");
            }
            scheduleOpenWatchdog(generation, 10_000, "10s");
            scheduleOpenWatchdog(generation, 30_000, "30s");
            scheduleOpenWatchdog(generation, 60_000, "60s");
        } catch (RuntimeException error) {
            Diagnostics.e("OPEN", "setDocumentUri threw generation=" + generation + " elapsedMs=" + elapsed(), error);
            showOpenError(error);
        }
    }

    private void scheduleOpenWatchdog(int generation, long delayMs, String label) {
        handler.postDelayed(() -> {
            if (generation == openGeneration && !documentLoaded) {
                Diagnostics.w("WATCHDOG", "Document still not loaded after " + label
                        + " generation=" + generation + " uri=" + safeUri(activeUri));
                Diagnostics.memory("watchdog-" + label + " generation=" + generation);
            }
        }, delayMs);
    }

    private void startSearch() {
        if (readerFragment == null || !searchButton.isEnabled()) return;
        Diagnostics.i("UI", "Search activated");
        try {
            readerFragment.setTextSearchActive(true);
        } catch (RuntimeException error) {
            Diagnostics.e("SEARCH", "setTextSearchActive failed", error);
            Toast.makeText(this, "No se pudo abrir la búsqueda", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDocumentLoaded(PdfDocument document) {
        documentLoaded = true;
        Diagnostics.i("OPEN", "SUCCESS elapsedMs=" + elapsed()
                + " documentClass=" + (document == null ? "null" : document.getClass().getName())
                + " uri=" + safeUri(activeUri));
        Diagnostics.memory("after-document-loaded");
        searchButton.setEnabled(true);
        emptyView.setVisibility(View.GONE);
        showSelectionHintIfNeeded();
    }

    @Override
    public void onDocumentLoadError(Throwable error) {
        documentLoaded = false;
        Diagnostics.e("OPEN", "ERROR elapsedMs=" + elapsed() + " uri=" + safeUri(activeUri), error);
        Diagnostics.memory("after-document-error");
        showOpenError(error);
    }

    private void showOpenError(Throwable error) {
        searchButton.setEnabled(false);
        selectionHint.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty()) message = "Archivo no compatible o permiso no disponible.";
        emptyView.setText("No se pudo abrir este PDF.\n\n" + message + "\n\nToca Log para copiar el diagnóstico.");
    }

    private void showSelectionHintIfNeeded() {
        boolean dismissed = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(SELECTION_HINT_DISMISSED, false);
        if (!dismissed) selectionHint.setVisibility(View.VISIBLE);
    }

    private void dismissSelectionHint() {
        selectionHint.setVisibility(View.GONE);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(SELECTION_HINT_DISMISSED, true).apply();
        Diagnostics.i("UI", "Selection hint dismissed");
    }

    private void logUriDiagnostics(Uri uri) {
        try {
            String type = getContentResolver().getType(uri);
            Diagnostics.i("URI", "scheme=" + uri.getScheme() + " authority=" + uri.getAuthority()
                    + " mime=" + type + " encodedPath=" + uri.getEncodedPath());
        } catch (Exception error) {
            Diagnostics.e("URI", "getType failed", error);
        }

        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor == null) {
                Diagnostics.w("URI", "metadata query returned null cursor");
            } else {
                Diagnostics.i("URI", "metadata cursor rows=" + cursor.getCount() + " columns=" + cursor.getColumnCount());
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    String name = nameIndex >= 0 && !cursor.isNull(nameIndex) ? cursor.getString(nameIndex) : "<unknown>";
                    String size = sizeIndex >= 0 && !cursor.isNull(sizeIndex) ? String.valueOf(cursor.getLong(sizeIndex)) : "<unknown>";
                    Diagnostics.i("URI", "displayName=" + name + " reportedSizeBytes=" + size);
                }
            }
        } catch (Exception error) {
            Diagnostics.e("URI", "metadata query failed", error);
        }

        try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) {
                Diagnostics.w("URI", "openFileDescriptor returned null");
            } else {
                Diagnostics.i("URI", "descriptor valid=true statSizeBytes=" + descriptor.getStatSize()
                        + " fd=" + descriptor.getFd());
            }
        } catch (Exception error) {
            Diagnostics.e("URI", "openFileDescriptor preflight failed", error);
        }

        try {
            List<UriPermission> permissions = getContentResolver().getPersistedUriPermissions();
            boolean exact = false;
            for (UriPermission permission : permissions) {
                if (uri.equals(permission.getUri())) {
                    exact = true;
                    Diagnostics.i("PERMISSION", "matching persisted permission read=" + permission.isReadPermission()
                            + " write=" + permission.isWritePermission()
                            + " persistedTime=" + permission.getPersistedTime());
                }
            }
            Diagnostics.i("PERMISSION", "persistedPermissionCount=" + permissions.size() + " exactMatch=" + exact);
        } catch (Exception error) {
            Diagnostics.e("PERMISSION", "persisted permission inspection failed", error);
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            } catch (Exception error) {
                Diagnostics.e("URI", "displayName query failed", error);
            }
        }
        if (name == null || name.trim().isEmpty()) name = uri.getLastPathSegment();
        return name == null || name.trim().isEmpty() ? "PDF Limpio" : name;
    }

    private long elapsed() {
        return openStartedAt == 0 ? -1 : SystemClock.elapsedRealtime() - openStartedAt;
    }

    private String describeIntent(Intent intent) {
        if (intent == null) return "null";
        return "action=" + intent.getAction()
                + " data=" + safeUri(intent.getData())
                + " type=" + intent.getType()
                + " flags=0x" + Integer.toHexString(intent.getFlags())
                + " categories=" + intent.getCategories();
    }

    private String safeUri(Uri uri) {
        return uri == null ? "null" : uri.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
