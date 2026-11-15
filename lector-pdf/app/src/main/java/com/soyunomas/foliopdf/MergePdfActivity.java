package com.soyunomas.foliopdf;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MergePdfActivity extends AppCompatActivity {
    private final ArrayList<Uri> files=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private MaterialButton add, save;
    private ProgressBar progress;
    private boolean busy, closed;
    private static final int PICK=81, SAVE=82;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null) {
            ArrayList<String> restored=state.getStringArrayList("files");
            if(restored!=null) for(String s:restored)files.add(Uri.parse(s));
        }
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setFitsSystemWindows(true);
        root.setBackgroundColor(getColor(R.color.app_surface));
        MaterialToolbar bar=new MaterialToolbar(this);bar.setTitle("Unir documentos");bar.setBackgroundColor(getColor(R.color.app_bar));bar.setTitleTextColor(Color.WHITE);
        bar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);bar.setNavigationIconTint(Color.WHITE);bar.setNavigationOnClickListener(v->onBackPressed());
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView hint=new TextView(this);hint.setText("Añade PDFs y elige el orden del nuevo documento.");hint.setPadding(dp(16),dp(12),dp(16),dp(12));root.addView(hint);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(12),0,dp(12),0);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bottom=new LinearLayout(this);bottom.setPadding(dp(8),dp(8),dp(8),dp(8));
        add=button("Añadir PDF");add.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("application/pdf");pick.addCategory(Intent.CATEGORY_OPENABLE);pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(pick,PICK);});
        save=button("Unir");save.setOnClickListener(v->{Intent out=new Intent(Intent.ACTION_CREATE_DOCUMENT);out.setType("application/pdf");out.addCategory(Intent.CATEGORY_OPENABLE);out.putExtra(Intent.EXTRA_TITLE,"documentos-unidos.pdf");startActivityForResult(out,SAVE);});
        bottom.addView(add);bottom.addView(save,new LinearLayout.LayoutParams(0,-2,1));root.addView(bottom);setContentView(root);refresh();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private MaterialButton button(String s){MaterialButton b=new MaterialButton(this);b.setText(s);b.setAllCaps(false);return b;}
    private void refresh(){
        add.setEnabled(!busy);save.setEnabled(!busy&&files.size()>1);save.setText("Unir ("+files.size()+")");progress.setVisibility(busy?View.VISIBLE:View.INVISIBLE);list.removeAllViews();
        for(int i=0;i<files.size();i++){final int pos=i;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=new TextView(this);name.setText((i+1)+". "+PdfTools.name(this,files.get(i)));row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            for(String action:new String[]{"↑","↓","×"}){Button b=new Button(this);b.setText(action);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);b.setContentDescription(action.equals("↑")?"Subir":action.equals("↓")?"Bajar":"Quitar");b.setEnabled(!busy&&(!action.equals("↑")||i>0)&&(!action.equals("↓")||i<files.size()-1));b.setOnClickListener(v->{if(action.equals("×"))files.remove(pos);else Collections.swap(files,pos,pos+(action.equals("↑")?-1:1));refresh();});row.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));}
            list.addView(row);}
    }
    private void append(Uri uri,int flags){if(uri==null)return;try{getContentResolver().takePersistableUriPermission(uri,flags&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}files.add(uri);}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);if(result!=RESULT_OK||data==null||busy)return;
        if(code==PICK){ClipData clip=data.getClipData();if(clip!=null){for(int i=0;i<clip.getItemCount();i++)append(clip.getItemAt(i).getUri(),data.getFlags());}else append(data.getData(),data.getFlags());refresh();}
        if(code==SAVE&&data.getData()!=null&&files.size()>1){Uri dest=data.getData();if(files.contains(dest)){Toast.makeText(this,"Elige un archivo distinto",Toast.LENGTH_LONG).show();return;}ArrayList<Uri> input=new ArrayList<>(files);busy=true;refresh();worker.execute(()->{File f=null;try{f=PdfTools.merge(this,input);PdfTools.publish(this,f,dest);runOnUiThread(()->{if(!closed){busy=false;Toast.makeText(this,"PDF guardado",Toast.LENGTH_LONG).show();finish();}});}catch(Exception e){Diagnostics.e("MERGE","Merge failed",e);runOnUiThread(()->{if(!closed){busy=false;refresh();new MaterialAlertDialogBuilder(this).setTitle("No se pudo unir").setMessage(e.getMessage()).setPositiveButton("Aceptar",null).show();}});}finally{if(f!=null)f.delete();}});}
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);ArrayList<String> uris=new ArrayList<>();for(Uri uri:files)uris.add(uri.toString());state.putStringArrayList("files",uris);}
    @Override public void onBackPressed(){if(!busy)super.onBackPressed();}
    @Override protected void onDestroy(){closed=true;worker.shutdown();super.onDestroy();}
}
