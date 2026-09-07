package com.soyunomas.pdflimpio;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageButton;

final class GlyphImageButton extends ImageButton {
    private final String glyph;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    GlyphImageButton(Activity activity, String glyph, String description, View.OnClickListener listener) {
        super(activity);
        this.glyph = glyph;
        setContentDescription(description);
        setOnClickListener(listener);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(42, 49, 59));
        background.setCornerRadius(Math.round(10 * activity.getResources().getDisplayMetrics().density));
        setBackground(background);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setTextSize(getHeight() * 0.68f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(glyph, getWidth() / 2f, y, paint);
    }
}
