package com.clemente.clementime;
import android.app.Activity; import android.appwidget.AppWidgetManager; import android.os.Bundle;
public class RevealSettingsActivity extends Activity { @Override protected void onCreate(Bundle b){ super.onCreate(b); int id=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID); if(id!=AppWidgetManager.INVALID_APPWIDGET_ID) ClementimeWidgetProvider.render(this,AppWidgetManager.getInstance(this),id,true); finish(); } }
