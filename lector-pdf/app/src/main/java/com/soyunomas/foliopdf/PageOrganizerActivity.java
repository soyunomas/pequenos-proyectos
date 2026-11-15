package com.soyunomas.foliopdf;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PageOrganizerActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Integer> order = new ArrayList<>();
    private final Set<Integer> selected = new HashSet<>();
    private final Map<Integer,Integer> rotations = new HashMap<>();
    private final LruCache<Integer,Bitmap> thumbs = new LruCache<Integer,Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(Integer key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private final Set<Integer> queued = new HashSet<>();
    private Uri source;
    private File local;
    private PdfRenderer renderer;
    private GridView grid;
    private TextView summary;
    private MaterialButton extract, more, all;
    private ProgressBar progress;
    private PageAdapter adapter;
    private int count;
    private boolean busy = true;
    private volatile boolean closed;
    private String pending;
    private static final int SAVE = 71, FOLDER = 72;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        source = getIntent().getData();
        if (source == null) { finish(); return; }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setFitsSystemWindows(true);
        root.setBackgroundColor(getColor(R.color.app_surface));
        MaterialToolbar bar = new MaterialToolbar(this);
        bar.setBackgroundColor(getColor(R.color.app_bar)); bar.setTitleTextColor(Color.WHITE);
        bar.setTitle("Organizar páginas");
        bar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        bar.setNavigationIconTint(Color.WHITE); bar.setNavigationOnClickListener(v -> onBackPressed());
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),0,dp(8),0);
        summary = new TextView(this); row.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
        all = button("Todas"); all.setOnClickListener(v -> { if (selected.size() == order.size()) selected.clear(); else selected.addAll(order); refresh(); });
        row.addView(all); root.addView(row);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        grid = new GridView(this); grid.setNumColumns(GridView.AUTO_FIT); grid.setColumnWidth(dp(104));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); grid.setHorizontalSpacing(dp(8)); grid.setVerticalSpacing(dp(12));
        grid.setPadding(dp(12), dp(12), dp(12), dp(12)); grid.setClipToPadding(false);
        adapter = new PageAdapter(); grid.setAdapter(adapter);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = new TextView(this); hint.setText("Toca para seleccionar · Mantén pulsado y arrastra para ordenar");
        hint.setPadding(dp(12),dp(4),dp(12),dp(4)); hint.setTextSize(12); root.addView(hint);
        LinearLayout bottom = new LinearLayout(this); bottom.setPadding(dp(8),dp(4),dp(8),dp(8));
        more = button("Opciones"); more.setOnClickListener(v -> options());
        extract = button("Extraer"); extract.setOnClickListener(v -> choose("extract"));
        bottom.addView(more); bottom.addView(extract, new LinearLayout.LayoutParams(0,-2,1)); root.addView(bottom);
        setContentView(root); refresh();
        worker.execute(() -> {
            try {
                local = PdfTools.local(this, source);
                try (com.tom_roush.pdfbox.pdmodel.PDDocument doc = PdfTools.load(this, local)) { PdfTools.checkEditable(doc); }
                ParcelFileDescriptor fd = ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY);
                try { renderer = new PdfRenderer(fd); } catch (Exception e) { fd.close(); throw e; }
                int n = renderer.getPageCount();
                runOnUiThread(() -> {
                    if (closed) return;
                    count = n;
                    for (int i=0;i<n;i++) order.add(i);
                    if (state != null) {
                        ArrayList<Integer> restored = state.getIntegerArrayList("order");
                        ArrayList<Integer> marks = state.getIntegerArrayList("selected");
                        int[] turns = state.getIntArray("rotations");
                        if (restored != null && !restored.isEmpty()) { order.clear(); order.addAll(restored); }
                        if (marks != null) selected.addAll(marks);
                        if (turns != null) for (int i=0;i<turns.length;i++) if (turns[i]!=0) rotations.put(i,turns[i]);
                        pending = state.getString("pending");
                    }
                    busy = false; refresh();
                });
            } catch (Exception e) { runOnUiThread(() -> failure(e, true)); }
        });
    }

    private MaterialButton button(String s) { MaterialButton b = new MaterialButton(this); b.setText(s); b.setAllCaps(false); return b; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void refresh() {
        summary.setText(selected.size()+" seleccionadas · "+order.size()+" páginas");
        all.setEnabled(!busy && !order.isEmpty()); all.setText(selected.size()==order.size() && !order.isEmpty() ? "Ninguna" : "Todas");
        extract.setText("Extraer ("+selected.size()+")"); extract.setEnabled(!busy && !selected.isEmpty());
        more.setEnabled(!busy && !order.isEmpty()); grid.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        adapter.notifyDataSetChanged();
    }
    private List<Integer> chosen() { ArrayList<Integer> list = new ArrayList<>(); for (int i:order) if (selected.contains(i)) list.add(i); return list; }
    private void options() {
        String[] actions = {"Seleccionar intervalo", "Girar selección 90°", "Eliminar selección", "Guardar todas en este orden", "Separar selección en archivos"};
        new MaterialAlertDialogBuilder(this).setTitle("Páginas").setItems(actions,(d,n)->{
            if (n==0) { range(); return; }
            if (n==3) { choose("all"); return; }
            if (selected.isEmpty()) { Toast.makeText(this,"Selecciona páginas",Toast.LENGTH_SHORT).show(); return; }
            if (n==1) { for (int i:selected) rotations.put(i,(rotations.getOrDefault(i,0)+90)%360); refresh(); }
            if (n==2) {
                if (selected.size()==order.size()) { Toast.makeText(this,"Debe quedar una página",Toast.LENGTH_SHORT).show(); return; }
                order.removeAll(selected); selected.clear(); refresh();
            }
            if (n==4) choose("split");
        }).show();
    }
    private void range() {
        EditText input = new EditText(this); input.setSingleLine(); input.setHint("Ej. 1-4, 7");
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Seleccionar por posición").setView(input)
            .setNegativeButton("Cancelar",null).setPositiveButton("Seleccionar",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(w -> {
            try {
                Set<Integer> next = new HashSet<>();
                for (String part:input.getText().toString().split(",")) {
                    String[] limits = part.trim().split("-", -1);
                    if (limits.length>2) throw new IllegalArgumentException();
                    int a=Integer.parseInt(limits[0].trim()), b=limits.length==2?Integer.parseInt(limits[1].trim()):a;
                    if (a<1 || b<a || b>order.size()) throw new IllegalArgumentException();
                    for (int i=a;i<=b;i++) next.add(order.get(i-1));
                }
                selected.clear(); selected.addAll(next); refresh(); dialog.dismiss();
            } catch (Exception e) { input.setError("Intervalo no válido"); }
        })); dialog.show();
    }
    private void choose(String mode) {
        if (busy) return;
        pending = mode;
        Intent intent = new Intent("split".equals(mode) ? Intent.ACTION_OPEN_DOCUMENT_TREE : Intent.ACTION_CREATE_DOCUMENT);
        if (!"split".equals(mode)) {
            intent.setType("application/pdf"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE,PdfTools.stem(PdfTools.name(this,source))+("all".equals(mode)?"-organizado.pdf":"-paginas.pdf"));
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent,"split".equals(mode)?FOLDER:SAVE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if ((request!=SAVE && request!=FOLDER)||result!=RESULT_OK||data==null||data.getData()==null) return;
        if (busy || pending==null) { Toast.makeText(this,"Vuelve a elegir el destino cuando termine la carga",Toast.LENGTH_LONG).show(); return; }
        Uri dest=data.getData(); if (source.equals(dest)) { Toast.makeText(this,"Elige una copia distinta",Toast.LENGTH_LONG).show(); return; }
        List<Integer> pages = "all".equals(pending) ? new ArrayList<>(order) : chosen();
        Map<Integer,Integer> turns = new HashMap<>(rotations);
        String mode=pending; pending=null; busy=true; refresh();
        worker.execute(()->{
            int saved = 0;
            try {
                if ("split".equals(mode)) {
                    Uri directory=DocumentsContract.buildDocumentUriUsingTree(dest, DocumentsContract.getTreeDocumentId(dest));
                    String stem=PdfTools.stem(PdfTools.name(this,source));
                    for (int page:pages) {
                        File f=PdfTools.pages(this,local,Collections.singletonList(page),turns);
                        try {
                            Uri target=DocumentsContract.createDocument(getContentResolver(),directory,"application/pdf",stem+"-pagina-"+(page+1)+".pdf");
                            if (target==null || target.equals(source)) throw new IOException("No se pudo crear una copia nueva");
                            try { PdfTools.publish(this,f,target); } catch (Exception e) { try { DocumentsContract.deleteDocument(getContentResolver(),target); } catch (Exception ignored) {} throw e; }
                            saved++;
                        } finally { f.delete(); }
                    }
                } else {
                    File f=PdfTools.pages(this,local,pages,turns);
                    try { PdfTools.publish(this,f,dest); } finally { f.delete(); }
                    saved=1;
                }
                final int total=saved;
                runOnUiThread(()->{ if (!closed) {busy=false;refresh();Toast.makeText(this,total==1?"Copia guardada":total+" archivos guardados",Toast.LENGTH_LONG).show();finish();} });
            } catch (Exception e) {
                final int total=saved;
                runOnUiThread(()->failure(new IOException((total>0?total+" archivos ya guardados. ":"")+e.getMessage(),e),false));
            }
        });
    }
    private void failure(Exception e, boolean fatal) {
        if (closed) return;
        Diagnostics.e("PAGES","Page operation failed",e);
        busy=false;refresh();
        new MaterialAlertDialogBuilder(this).setTitle("No se pudo completar").setMessage(e.getMessage()==null?"Error al procesar PDF":e.getMessage())
            .setPositiveButton("Aceptar",(d,w)->{if(fatal)finish();}).setCancelable(!fatal).show();
    }
    private class PageAdapter extends BaseAdapter {
        @Override public int getCount(){return order.size();}
        @Override public Object getItem(int p){return order.get(p);}
        @Override public long getItemId(int p){return order.get(p);}
        @Override public View getView(int position,View recycled,android.view.ViewGroup parent) {
            int id=order.get(position);
            LinearLayout card=new LinearLayout(PageOrganizerActivity.this); card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER); card.setPadding(dp(4),dp(4),dp(4),dp(4));
            android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
            bg.setColor(getColor(R.color.app_surface_container)); bg.setCornerRadius(dp(6));
            bg.setStroke(dp(selected.contains(id)?2:1),getColor(selected.contains(id)?R.color.app_primary:R.color.app_outline));card.setBackground(bg);
            ImageView image=new ImageView(PageOrganizerActivity.this); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(image,new LinearLayout.LayoutParams(-1,dp(112)));
            image.setRotation(rotations.getOrDefault(id,0)); Bitmap bitmap=thumbs.get(id);
            if (bitmap!=null)image.setImageBitmap(bitmap); else requestThumb(id,image);
            TextView number=new TextView(PageOrganizerActivity.this);number.setGravity(Gravity.CENTER);
            number.setText((selected.contains(id)?"✓  ":"")+(id+1));card.addView(number);card.setContentDescription("Página "+(id+1)+(selected.contains(id)?", seleccionada":""));
            card.setOnClickListener(v->{if(!busy){if(!selected.add(id))selected.remove(id);refresh();}});
            card.setOnLongClickListener(v->{if(busy)return false;return v.startDragAndDrop(ClipData.newPlainText("page",String.valueOf(id)),new View.DragShadowBuilder(v),id,0);});
            card.setOnDragListener((v,event)->{
                if (!(event.getLocalState() instanceof Integer))return false;
                if(event.getAction()==DragEvent.ACTION_DROP && !busy) {
                    int from=order.indexOf((Integer)event.getLocalState()), to=order.indexOf(id);
                    if(from>=0 && to>=0 && from!=to){Integer moving=order.remove(from);order.add(to,moving);refresh();}
                }
                return true;
            });
            return card;
        }
    }
    private void requestThumb(int id,ImageView image) {
        if (closed || renderer==null || !queued.add(id)) return;
        worker.execute(()->{
            Bitmap b=null;
            try (PdfRenderer.Page page=renderer.openPage(id)) {
                float factor=220f/Math.max(page.getWidth(),page.getHeight());
                b=Bitmap.createBitmap(Math.max(1,Math.round(page.getWidth()*factor)),Math.max(1,Math.round(page.getHeight()*factor)),Bitmap.Config.ARGB_8888);
                b.eraseColor(Color.WHITE);page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            } catch(Exception e){Diagnostics.e("PAGES","Thumbnail failed",e);}
            Bitmap ready=b;
            runOnUiThread(()->{queued.remove(id);if(closed)return;if(ready!=null){thumbs.put(id,ready);adapter.notifyDataSetChanged();}});
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);state.putIntegerArrayList("order",new ArrayList<>(order));
        state.putIntegerArrayList("selected",new ArrayList<>(selected));int[] turns=new int[count];
        for(int i=0;i<count;i++)turns[i]=rotations.getOrDefault(i,0);state.putIntArray("rotations",turns);state.putString("pending",pending);
    }
    @Override public void onBackPressed(){if(busy){Toast.makeText(this,"Operación en curso",Toast.LENGTH_SHORT).show();return;}super.onBackPressed();}
    @Override protected void onDestroy(){closed=true;worker.execute(()->{if(renderer!=null)renderer.close();if(local!=null)local.delete();});worker.shutdown();super.onDestroy();}
}
