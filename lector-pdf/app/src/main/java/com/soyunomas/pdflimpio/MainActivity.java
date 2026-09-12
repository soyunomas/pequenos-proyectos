package com.soyunomas.pdflimpio;

import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.ext.SdkExtensions;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.pdf.PdfDocument;
import androidx.pdf.PdfWriteHandle;
import androidx.pdf.viewer.fragment.PdfViewerFragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class MainActivity extends AppCompatActivity implements ReaderEvents {
    private static final int OPEN_PDF = 41;
    private static final int SAVE_PDF = 42;
    private static final String PREFS = "reader";
    private static final String LAST_URI = "last_uri";
    private static final String COPY_HINT_SHOWN = "copy_hint_shown_v2";
    private static final String FORM_HINT_SHOWN = "form_hint_shown_v1";
    private static final String VIEWER_TAG = "pdf_viewer";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private MaterialToolbar toolbar;
    private View root;
    private View emptyState;
    private View editBar;
    private TextView editSubtitle;
    private MaterialButton saveButton;

    private PdfViewerFragment viewerFragment;
    private ReaderPdfFragment editableFragment;
    private Fragment currentFragment;
    private Uri activeUri;
    private Uri pendingSaveUri;
    private String activeDisplayName = "PDF Limpio";
    private boolean documentLoaded;
    private boolean formSessionActive;
    private boolean currentDocumentHasForm;
    private boolean saving;
    private int openGeneration;
    private long openStartedAt;
    private Runnable afterSaveAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Diagnostics.init(this);
        Diagnostics.i("ACTIVITY", "onCreate v1.2.2 saved=" + (savedInstanceState != null));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupToolbar();
        setupEditBar();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            openDocumentNow(intent.getData(), false);
        } else {
            String last = getSharedPreferences(PREFS, MODE_PRIVATE).getString(LAST_URI, null);
            if (last != null) openDocumentNow(Uri.parse(last), false);
            else showEmptyState();
        }
    }

    private void bindViews() {
        root = findViewById(R.id.reader_root);
        toolbar = findViewById(R.id.toolbar);
        emptyState = findViewById(R.id.empty_state);
        editBar = findViewById(R.id.edit_bar);
        editSubtitle = findViewById(R.id.edit_subtitle);
        saveButton = findViewById(R.id.action_save_edits);
        findViewById(R.id.empty_open_button).setOnClickListener(v -> requestOpenPicker());
    }

    private void setupToolbar() {
        toolbar.setTitle(R.string.app_name);
        toolbar.setSubtitle(R.string.no_document);
        toolbar.setOnMenuItemClickListener(this::onToolbarItem);
        updateMenuState();
    }

    private void setupEditBar() {
        findViewById(R.id.action_cancel_edits).setOnClickListener(v -> requestCancelFormChanges());
        saveButton.setOnClickListener(v -> startSaveFlow(null));
    }

    private boolean onToolbarItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_open) {
            requestOpenPicker();
            return true;
        }
        if (id == R.id.action_search) {
            startSearch();
            return true;
        }
        if (id == R.id.action_compat) {
            openCompatMode();
            return true;
        }
        if (id == R.id.action_log) {
            startActivity(new Intent(this, DiagnosticsActivity.class));
            return true;
        }
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Diagnostics.i("ACTIVITY", "onNewIntent action=" + intent.getAction() + " uri=" + intent.getData());
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            requestReplaceWith(intent.getData(), false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_PDF) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri uri = data.getData();
            persistReadPermission(uri, data.getFlags());
            requestReplaceWith(uri, true);
            return;
        }
        if (requestCode == SAVE_PDF) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                Diagnostics.i("FORM", "Save destination cancelled");
                pendingSaveUri = null;
                afterSaveAction = null;
                setSaving(false);
                return;
            }
            pendingSaveUri = data.getData();
            persistReadWritePermission(pendingSaveUri, data.getFlags());
            applyEditsForSave();
        }
    }

    private void requestOpenPicker() {
        if (saving) return;
        Runnable openPicker = this::choosePdf;
        if (hasUnsavedChanges()) showUnsavedChangesDialog(openPicker);
        else openPicker.run();
    }

    private void requestReplaceWith(Uri uri, boolean remember) {
        Runnable replace = () -> openDocumentNow(uri, remember);
        if (hasUnsavedChanges()) showUnsavedChangesDialog(replace);
        else replace.run();
    }

    private void choosePdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Diagnostics.i("PICKER", "Opening document picker");
        startActivityForResult(intent, OPEN_PDF);
    }

    private void openDocumentNow(Uri uri, boolean remember) {
        if (uri == null || saving) return;

        final int generation = ++openGeneration;
        documentLoaded = false;
        formSessionActive = false;
        currentDocumentHasForm = false;
        activeUri = uri;
        activeDisplayName = displayName(uri);
        openStartedAt = SystemClock.elapsedRealtime();

        toolbar.setTitle(activeDisplayName);
        toolbar.setSubtitle(R.string.loading_document);
        emptyState.setVisibility(View.GONE);
        editBar.setVisibility(View.GONE);
        updateMenuState();

        Diagnostics.i("OPEN", "BEGIN generation=" + generation + " freshFragment=true uri=" + uri
                + " name=" + activeDisplayName);
        logUriDiagnostics(uri);
        Diagnostics.memory("before-fresh-viewer generation=" + generation);

        detachOldViewer();
        Fragment next;
        if (supportsEditablePdf()) {
            ReaderPdfFragment editable = new ReaderPdfFragment();
            editable.setListener(this);
            editableFragment = editable;
            viewerFragment = editable;
            next = editable;
            Diagnostics.i("VIEWER", "Creating fresh form-capable viewer generation=" + generation);
        } else {
            BasicReaderPdfFragment basic = new BasicReaderPdfFragment();
            basic.setListener(this);
            editableFragment = null;
            viewerFragment = basic;
            next = basic;
            Diagnostics.i("VIEWER", "Creating fresh basic viewer generation=" + generation);
        }
        currentFragment = next;

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.pdf_viewer_container, next, VIEWER_TAG + "_" + generation)
                .commitNow();

        try {
            viewerFragment.setDocumentUri(uri);
            Diagnostics.i("OPEN", "setDocumentUri fresh viewer returned elapsedMs=" + elapsed());
            if (remember) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(LAST_URI, uri.toString()).apply();
            }
            scheduleWatchdog(generation, 10_000, true);
            scheduleWatchdog(generation, 30_000, false);
            scheduleWatchdog(generation, 60_000, false);
        } catch (RuntimeException error) {
            Diagnostics.e("OPEN", "setDocumentUri threw on fresh viewer", error);
            onDocumentLoadError(error);
        }
    }

    private void detachOldViewer() {
        if (currentFragment instanceof ReaderPdfFragment) {
            ((ReaderPdfFragment) currentFragment).setListener(null);
        } else if (currentFragment instanceof BasicReaderPdfFragment) {
            ((BasicReaderPdfFragment) currentFragment).setListener(null);
        }
        currentFragment = null;
        viewerFragment = null;
        editableFragment = null;
    }

    private void scheduleWatchdog(int generation, long delayMs, boolean offerFallback) {
        handler.postDelayed(() -> {
            if (generation != openGeneration || documentLoaded) return;
            Diagnostics.w("WATCHDOG", "Fresh viewer still loading after " + delayMs + "ms generation=" + generation);
            Diagnostics.memory("watchdog generation=" + generation + " delayMs=" + delayMs);
            if (offerFallback && activeUri != null) {
                Snackbar.make(root, "Este PDF está tardando más de lo normal", Snackbar.LENGTH_LONG)
                        .setAction("Modo compatible", v -> openCompatMode())
                        .show();
            }
        }, delayMs);
    }

    @Override
    public void onDocumentLoaded(PdfDocument document) {
        documentLoaded = true;
        String formType = String.valueOf(document.getFormType());
        currentDocumentHasForm = !"NONE".equalsIgnoreCase(formType)
                && !"0".equals(formType)
                && !"null".equalsIgnoreCase(formType);
        toolbar.setSubtitle(currentDocumentHasForm ? R.string.reader_ready_editable : R.string.reader_ready);
        Diagnostics.i("OPEN", "SUCCESS fresh viewer elapsedMs=" + elapsed()
                + " class=" + document.getClass().getName() + " formType=" + formType);
        Diagnostics.memory("after-document-loaded");
        updateMenuState();
        maybeShowCopyHint();
        if (currentDocumentHasForm) maybeShowFormHint();
    }

    @Override
    public void onDocumentLoadError(Throwable error) {
        documentLoaded = false;
        toolbar.setSubtitle(R.string.document_error_short);
        Diagnostics.e("OPEN", "ERROR fresh viewer elapsedMs=" + elapsed() + " uri=" + activeUri, error);
        updateMenuState();
        Snackbar.make(root, "No se pudo procesar el PDF con el visor avanzado", Snackbar.LENGTH_INDEFINITE)
                .setAction("Compatible", v -> openCompatMode())
                .show();
    }

    private void startSearch() {
        if (!documentLoaded || viewerFragment == null || formSessionActive) return;
        try {
            viewerFragment.setTextSearchActive(true);
            Diagnostics.i("UI", "Search activated");
        } catch (RuntimeException error) {
            Diagnostics.e("SEARCH", "Could not activate search", error);
            Snackbar.make(root, "No se pudo abrir la búsqueda", Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEditModeChanged(boolean enabled) {
        formSessionActive = enabled;
        editBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (enabled) {
            editSubtitle.setText(R.string.edit_mode_help);
            toolbar.setSubtitle(R.string.edit_mode_active);
            Diagnostics.i("FORM", "Form session active; save controls visible");
        } else if (documentLoaded) {
            toolbar.setSubtitle(currentDocumentHasForm ? R.string.reader_ready_editable : R.string.reader_ready);
            Diagnostics.i("FORM", "Form session ended");
        }
        updateMenuState();
    }

    private void requestCancelFormChanges() {
        if (editableFragment == null || !formSessionActive || saving) return;
        if (!editableFragment.hasDraftChanges()) {
            editableFragment.discardEditMode();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("¿Descartar los cambios?")
                .setMessage("Los campos rellenados que no hayas guardado se perderán.")
                .setNegativeButton("Seguir rellenando", null)
                .setPositiveButton("Descartar", (dialog, which) -> editableFragment.discardEditMode())
                .show();
    }

    private void showUnsavedChangesDialog(Runnable continueAction) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Hay cambios sin guardar")
                .setMessage("Guarda una copia del formulario antes de abrir otro documento o descarta los cambios.")
                .setNeutralButton("Seguir rellenando", null)
                .setNegativeButton("Descartar", (dialog, which) -> {
                    if (editableFragment != null) editableFragment.discardEditMode();
                    continueAction.run();
                })
                .setPositiveButton("Guardar copia", (dialog, which) -> startSaveFlow(continueAction))
                .show();
    }

    private boolean hasUnsavedChanges() {
        return editableFragment != null && formSessionActive && editableFragment.hasDraftChanges();
    }

    private void startSaveFlow(Runnable actionAfterSave) {
        if (editableFragment == null || !formSessionActive || saving) return;
        if (!editableFragment.hasDraftChanges()) {
            Snackbar.make(root, "No hay cambios que guardar", Snackbar.LENGTH_SHORT).show();
            if (actionAfterSave != null) actionAfterSave.run();
            return;
        }
        afterSaveAction = actionAfterSave;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, suggestedSaveName());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Diagnostics.i("FORM", "Requesting save destination title=" + suggestedSaveName());
        startActivityForResult(intent, SAVE_PDF);
    }

    private void applyEditsForSave() {
        if (editableFragment == null || pendingSaveUri == null) return;
        setSaving(true);
        try {
            editableFragment.applyChanges();
        } catch (RuntimeException error) {
            setSaving(false);
            pendingSaveUri = null;
            afterSaveAction = null;
            Diagnostics.e("FORM", "applyDraftEdits threw", error);
            Snackbar.make(root, "No se pudieron preparar los cambios", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onEditsReady(@NonNull PdfWriteHandle handle) {
        final Uri destination = pendingSaveUri;
        if (destination == null) {
            try { handle.close(); } catch (Exception ignored) { }
            setSaving(false);
            return;
        }
        Diagnostics.i("FORM", "Writing filled PDF destination=" + destination);
        PdfWriteBridge.write(getContentResolver(), destination, handle,
                error -> runOnUiThread(() -> finishSaving(destination, error)));
    }

    private void finishSaving(Uri destination, Throwable failure) {
        setSaving(false);
        pendingSaveUri = null;
        if (failure != null) {
            Diagnostics.e("FORM", "Writing filled PDF failed", failure);
            afterSaveAction = null;
            Snackbar.make(root, "No se pudo guardar la copia", Snackbar.LENGTH_LONG).show();
            return;
        }
        Diagnostics.i("FORM", "Filled PDF saved destination=" + destination);
        if (editableFragment != null) editableFragment.finishEditModeAfterSave();
        Runnable next = afterSaveAction;
        afterSaveAction = null;
        if (next != null) {
            Snackbar.make(root, "Copia guardada", Snackbar.LENGTH_SHORT).show();
            next.run();
        } else {
            openDocumentNow(destination, true);
            Snackbar.make(root, "Copia guardada", Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onApplyEditsFailed(Throwable error) {
        setSaving(false);
        pendingSaveUri = null;
        afterSaveAction = null;
        Diagnostics.e("FORM", "Applying form changes failed", error);
        Snackbar.make(root, "No se pudieron aplicar los cambios del formulario", Snackbar.LENGTH_LONG).show();
    }

    private void setSaving(boolean value) {
        saving = value;
        saveButton.setEnabled(!value);
        saveButton.setText(value ? R.string.saving : R.string.save_copy);
        updateMenuState();
    }

    private void openCompatMode() {
        if (activeUri == null) return;
        Intent intent = new Intent(this, CompatPdfActivity.class);
        intent.putExtra(CompatPdfActivity.EXTRA_URI, activeUri);
        startActivity(intent);
    }

    private void updateMenuState() {
        if (toolbar == null) return;
        Menu menu = toolbar.getMenu();
        MenuItem search = menu.findItem(R.id.action_search);
        MenuItem open = menu.findItem(R.id.action_open);
        MenuItem compat = menu.findItem(R.id.action_compat);
        if (search != null) search.setEnabled(documentLoaded && !formSessionActive && !saving);
        if (open != null) open.setEnabled(!saving);
        if (compat != null) compat.setVisible(activeUri != null);
    }

    private boolean supportsEditablePdf() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        try {
            return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 18;
        } catch (Throwable error) {
            Diagnostics.e("FORM", "Could not inspect SDK extension", error);
            return false;
        }
    }

    private void maybeShowCopyHint() {
        boolean shown = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(COPY_HINT_SHOWN, false);
        if (shown) return;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(COPY_HINT_SHOWN, true).apply();
        Snackbar.make(root, "Mantén pulsado sobre el texto para seleccionarlo y copiarlo", Snackbar.LENGTH_LONG).show();
    }

    private void maybeShowFormHint() {
        boolean shown = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(FORM_HINT_SHOWN, false);
        if (shown) return;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(FORM_HINT_SHOWN, true).apply();
        Snackbar.make(root, "Formulario rellenable: toca directamente sus campos", Snackbar.LENGTH_LONG).show();
    }

    private void showEmptyState() {
        emptyState.setVisibility(View.VISIBLE);
        toolbar.setTitle(R.string.app_name);
        toolbar.setSubtitle(R.string.no_document);
        documentLoaded = false;
        formSessionActive = false;
        currentDocumentHasForm = false;
        updateMenuState();
    }

    private String suggestedSaveName() {
        String name = activeDisplayName == null ? "documento.pdf" : activeDisplayName;
        if (name.toLowerCase().endsWith(".pdf")) name = name.substring(0, name.length() - 4);
        return name + " - rellenado.pdf";
    }

    private void persistReadPermission(Uri uri, int flags) {
        try {
            int takeFlags = flags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (takeFlags != 0) getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception error) {
            Diagnostics.e("PERMISSION", "Could not persist read permission uri=" + uri, error);
        }
    }

    private void persistReadWritePermission(Uri uri, int flags) {
        try {
            int takeFlags = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (takeFlags != 0) getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception error) {
            Diagnostics.e("PERMISSION", "Could not persist destination permission uri=" + uri, error);
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (Exception error) {
            Diagnostics.e("URI", "displayName query failed", error);
        }
        if (name == null || name.trim().isEmpty()) name = uri.getLastPathSegment();
        return name == null || name.trim().isEmpty() ? "PDF Limpio" : name;
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
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                String name = nameIndex >= 0 && !cursor.isNull(nameIndex) ? cursor.getString(nameIndex) : "<unknown>";
                String size = sizeIndex >= 0 && !cursor.isNull(sizeIndex) ? String.valueOf(cursor.getLong(sizeIndex)) : "<unknown>";
                Diagnostics.i("URI", "displayName=" + name + " reportedSizeBytes=" + size);
            }
        } catch (Exception error) {
            Diagnostics.e("URI", "metadata query failed", error);
        }
        try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor != null) Diagnostics.i("URI", "descriptor statSizeBytes=" + descriptor.getStatSize());
        } catch (Exception error) {
            Diagnostics.e("URI", "descriptor preflight failed", error);
        }
        try {
            List<UriPermission> permissions = getContentResolver().getPersistedUriPermissions();
            boolean exact = false;
            for (UriPermission permission : permissions) {
                if (uri.equals(permission.getUri())) {
                    exact = true;
                    Diagnostics.i("PERMISSION", "match read=" + permission.isReadPermission()
                            + " write=" + permission.isWritePermission());
                }
            }
            Diagnostics.i("PERMISSION", "persistedCount=" + permissions.size() + " exactMatch=" + exact);
        } catch (Exception error) {
            Diagnostics.e("PERMISSION", "permission inspection failed", error);
        }
    }

    private long elapsed() {
        return openStartedAt == 0 ? -1 : SystemClock.elapsedRealtime() - openStartedAt;
    }

    @Override
    public void onBackPressed() {
        if (saving) return;
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog(super::onBackPressed);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        detachOldViewer();
        handler.removeCallbacksAndMessages(null);
        Diagnostics.i("ACTIVITY", "onDestroy v1.2.2");
        super.onDestroy();
    }
}
