package com.soyunomas.pdflimpio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int OPEN_PDF = 41;
    private static final String PREFS = "reader";
    private static final String LAST_URI = "last_uri";

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private final PdfDocumentController document = new PdfDocumentController();
    private int pageIndex;
    private int renderGeneration;

    private ZoomableImageView pageView;
    private TextView titleView;
    private TextView pageIndicator;
    private TextView emptyText;
    private ProgressBar progress;
    private ImageButton previousButton;
    private ImageButton nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(17, 21, 27));
        getWindow().setNavigationBarColor(Color.rgb(17, 21, 27));
        setContentView(buildUi());

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            openDocument(intent.getData(), false);
        } else {
            String last = getSharedPreferences(PREFS, MODE_PRIVATE).getString(LAST_URI, null);
            if (last != null) openDocument(Uri.parse(last), false);
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
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        top.addView(titleView, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button openButton = new Button(this);
        openButton.setText("Abrir PDF");
        openButton.setAllCaps(false);
        openButton.setOnClickListener(v -> choosePdf());
        top.addView(openButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        root.addView(top);

        FrameLayout content = new FrameLayout(this);
        pageView = new ZoomableImageView(this);
        pageView.setBackgroundColor(Color.rgb(218, 221, 226));
        pageView.setOnPageSwipeListener(direction -> showPage(pageIndex + (direction < 0 ? 1 : -1)));
        content.addView(pageView, new FrameLayout.LayoutParams(-1, -1));

        emptyText = new TextView(this);
        emptyText.setText("Abre un PDF para empezar\n\nSin anuncios · sin cuenta · sin Internet");
        emptyText.setTextColor(Color.rgb(66, 72, 80));
        emptyText.setTextSize(18);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(32), dp(32), dp(32), dp(32));
        content.addView(emptyText, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        content.addView(progress, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(8), dp(6), dp(8), dp(6));
        bottom.setBackgroundColor(Color.rgb(17, 21, 27));

        previousButton = new GlyphImageButton(this, "‹", "Página anterior", v -> showPage(pageIndex - 1));
        nextButton = new GlyphImageButton(this, "›", "Página siguiente", v -> showPage(pageIndex + 1));
        pageIndicator = new TextView(this);
        pageIndicator.setText("—");
        pageIndicator.setTextColor(Color.WHITE);
        pageIndicator.setTextSize(17);
        pageIndicator.setGravity(Gravity.CENTER);
        pageIndicator.setPadding(dp(20), 0, dp(20), 0);
        pageIndicator.setOnClickListener(v -> showJumpDialog());

        bottom.addView(previousButton, new LinearLayout.LayoutParams(dp(64), dp(52)));
        bottom.addView(pageIndicator, new LinearLayout.LayoutParams(0, dp(52), 1f));
        bottom.addView(nextButton, new LinearLayout.LayoutParams(dp(64), dp(52)));
        root.addView(bottom);
        updateNavigation();
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
        if (requestCode != OPEN_PDF || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        openDocument(uri, true);
    }

    private void openDocument(Uri uri, boolean remember) {
        renderGeneration++;
        progress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        titleView.setText(displayName(uri));
        try {
            document.open(getContentResolver(), uri);
            pageIndex = 0;
            if (remember) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(LAST_URI, uri.toString()).apply();
            }
            showPage(0);
        } catch (Exception error) {
            progress.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText("No se pudo abrir este PDF.\n\n" + safeMessage(error));
            document.close();
            updateNavigation();
        }
    }

    private void showPage(int index) {
        int count = document.pageCount();
        if (index < 0 || index >= count) return;
        pageIndex = index;
        updateNavigation();
        progress.setVisibility(View.VISIBLE);
        pageView.setVisibility(View.INVISIBLE);
        int generation = ++renderGeneration;
        int width = Math.max(pageView.getWidth(), getResources().getDisplayMetrics().widthPixels);
        int height = Math.max(pageView.getHeight(), getResources().getDisplayMetrics().heightPixels - dp(140));

        renderExecutor.execute(() -> {
            try {
                Bitmap bitmap = document.renderPage(index, width, height);
                runOnUiThread(() -> {
                    if (generation != renderGeneration || isFinishing()) {
                        bitmap.recycle();
                        return;
                    }
                    pageView.setImageBitmapAndReset(bitmap);
                    pageView.setVisibility(View.VISIBLE);
                    emptyText.setVisibility(View.GONE);
                    progress.setVisibility(View.GONE);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (generation == renderGeneration) {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(this, "No se pudo renderizar la página", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showJumpDialog() {
        if (!document.isOpen()) return;
        int max = document.pageCount();
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(pageIndex + 1));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Ir a página")
                .setMessage("Introduce un número entre 1 y " + max)
                .setView(input)
                .setPositiveButton("Ir", (dialog, which) -> {
                    try {
                        int target = Integer.parseInt(input.getText().toString()) - 1;
                        if (target >= 0 && target < max) showPage(target);
                        else Toast.makeText(this, "Página fuera de rango", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException error) {
                        Toast.makeText(this, "Número de página no válido", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void updateNavigation() {
        int count = document.pageCount();
        boolean hasPdf = count > 0;
        pageIndicator.setText(hasPdf ? (pageIndex + 1) + " / " + count : "—");
        previousButton.setEnabled(hasPdf && pageIndex > 0);
        nextButton.setEnabled(hasPdf && pageIndex < count - 1);
        previousButton.setAlpha(previousButton.isEnabled() ? 1f : 0.35f);
        nextButton.setAlpha(nextButton.isEnabled() ? 1f : 0.35f);
    }

    private String displayName(Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            } catch (Exception ignored) { }
        }
        if (name == null || name.trim().isEmpty()) name = uri.getLastPathSegment();
        return name == null || name.trim().isEmpty() ? "PDF Limpio" : name;
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Archivo no compatible o permiso no disponible." : message;
    }

    @Override
    protected void onDestroy() {
        renderGeneration++;
        document.close();
        renderExecutor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
