package com.soyunomas.pdflimpio;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompatPdfActivity extends AppCompatActivity {
    public static final String EXTRA_URI = "uri";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private ImageView imageView;
    private TextView pageIndicator;
    private TextView messageView;
    private ProgressBar progress;
    private Button previousButton;
    private Button nextButton;
    private int pageIndex;
    private int renderGeneration;
    private Bitmap bitmap;
    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Diagnostics.init(this);
        uri = getIntent().getParcelableExtra(EXTRA_URI);
        Diagnostics.i("COMPAT", "onCreate uri=" + uri);
        setContentView(buildUi());
        if (uri == null) {
            showFatal("No se ha recibido ningún PDF.", null);
            return;
        }
        openRenderer();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(31, 34, 39));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(8));
        top.setBackgroundColor(Color.rgb(17, 21, 27));

        Button back = button("Volver", "Volver al lector normal");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        TextView title = new TextView(this);
        title.setText("Modo compatible");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(12), 0, dp(8), 0);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(top);

        TextView notice = new TextView(this);
        notice.setText("Modo compatible: prioriza abrir el documento. La selección y copia de texto no están disponibles aquí.");
        notice.setTextColor(Color.rgb(66, 55, 15));
        notice.setBackgroundColor(Color.rgb(255, 244, 205));
        notice.setTextSize(14);
        notice.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(notice);

        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(Color.rgb(210, 213, 218));

        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(Color.rgb(210, 213, 218));
        imageView.setContentDescription("Página del PDF");
        content.addView(imageView, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
        content.addView(progress, progressParams);

        messageView = new TextView(this);
        messageView.setTextColor(Color.rgb(45, 49, 55));
        messageView.setTextSize(17);
        messageView.setGravity(Gravity.CENTER);
        messageView.setPadding(dp(28), dp(28), dp(28), dp(28));
        messageView.setVisibility(View.GONE);
        content.addView(messageView, new FrameLayout.LayoutParams(-1, -1));

        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));
        nav.setBackgroundColor(Color.rgb(17, 21, 27));

        previousButton = button("‹", "Página anterior");
        previousButton.setTextSize(26);
        previousButton.setOnClickListener(v -> showPage(pageIndex - 1));
        nav.addView(previousButton, new LinearLayout.LayoutParams(dp(72), dp(52)));

        pageIndicator = new TextView(this);
        pageIndicator.setText("—");
        pageIndicator.setTextColor(Color.WHITE);
        pageIndicator.setTextSize(16);
        pageIndicator.setGravity(Gravity.CENTER);
        nav.addView(pageIndicator, new LinearLayout.LayoutParams(0, dp(52), 1f));

        nextButton = button("›", "Página siguiente");
        nextButton.setTextSize(26);
        nextButton.setOnClickListener(v -> showPage(pageIndex + 1));
        nav.addView(nextButton, new LinearLayout.LayoutParams(dp(72), dp(52)));
        root.addView(nav);
        updateNavigation();
        return root;
    }

    private void openRenderer() {
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                descriptor = getContentResolver().openFileDescriptor(uri, "r");
                if (descriptor == null) throw new IOException("openFileDescriptor devolvió null");
                renderer = new PdfRenderer(descriptor);
                int count = renderer.getPageCount();
                Diagnostics.i("COMPAT", "PdfRenderer opened pages=" + count + " statSize=" + descriptor.getStatSize());
                if (count <= 0) throw new IOException("El PDF no contiene páginas");
                runOnUiThread(() -> showPage(0));
            } catch (Throwable error) {
                Diagnostics.e("COMPAT", "PdfRenderer open failed uri=" + uri, error);
                runOnUiThread(() -> showFatal("Tampoco se pudo abrir este PDF en modo compatible.", error));
            }
        });
    }

    private void showPage(int index) {
        if (renderer == null || index < 0 || index >= renderer.getPageCount()) return;
        pageIndex = index;
        updateNavigation();
        progress.setVisibility(View.VISIBLE);
        messageView.setVisibility(View.GONE);
        final int generation = ++renderGeneration;
        final int targetWidth = Math.max(imageView.getWidth(), getResources().getDisplayMetrics().widthPixels);
        final int targetHeight = Math.max(imageView.getHeight(), getResources().getDisplayMetrics().heightPixels - dp(160));
        Diagnostics.i("COMPAT", "render BEGIN page=" + (index + 1) + "/" + renderer.getPageCount()
                + " target=" + targetWidth + "x" + targetHeight + " generation=" + generation);

        executor.execute(() -> {
            try (PdfRenderer.Page page = renderer.openPage(index)) {
                float scale = Math.min((float) targetWidth / page.getWidth(), (float) targetHeight / page.getHeight());
                scale = Math.max(scale, 1f);
                int width = Math.max(1, Math.round(page.getWidth() * scale));
                int height = Math.max(1, Math.round(page.getHeight() * scale));
                Bitmap rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                rendered.eraseColor(Color.WHITE);
                page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                Diagnostics.i("COMPAT", "render SUCCESS page=" + (index + 1) + " bitmap=" + width + "x" + height);
                runOnUiThread(() -> {
                    if (generation != renderGeneration || isFinishing()) {
                        rendered.recycle();
                        return;
                    }
                    Bitmap old = bitmap;
                    bitmap = rendered;
                    imageView.setImageBitmap(rendered);
                    progress.setVisibility(View.GONE);
                    if (old != null && old != rendered && !old.isRecycled()) old.recycle();
                });
            } catch (Throwable error) {
                Diagnostics.e("COMPAT", "render ERROR page=" + (index + 1), error);
                runOnUiThread(() -> showFatal("No se pudo renderizar la página " + (index + 1) + ".", error));
            }
        });
    }

    private void updateNavigation() {
        int count = renderer == null ? 0 : renderer.getPageCount();
        pageIndicator.setText(count > 0 ? (pageIndex + 1) + " / " + count : "—");
        previousButton.setEnabled(count > 0 && pageIndex > 0);
        nextButton.setEnabled(count > 0 && pageIndex < count - 1);
        previousButton.setAlpha(previousButton.isEnabled() ? 1f : 0.35f);
        nextButton.setAlpha(nextButton.isEnabled() ? 1f : 0.35f);
    }

    private void showFatal(String message, Throwable error) {
        progress.setVisibility(View.GONE);
        messageView.setVisibility(View.VISIBLE);
        String detail = error == null || error.getMessage() == null ? "" : "\n\n" + error.getMessage();
        messageView.setText(message + detail + "\n\nEl detalle también ha quedado guardado en Log.");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private Button button(String text, String description) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setContentDescription(description);
        return button;
    }

    @Override
    protected void onDestroy() {
        renderGeneration++;
        executor.shutdownNow();
        imageView.setImageDrawable(null);
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        try {
            if (renderer != null) renderer.close();
        } catch (Exception ignored) {
        }
        try {
            if (descriptor != null) descriptor.close();
        } catch (Exception ignored) {
        }
        Diagnostics.i("COMPAT", "onDestroy");
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
