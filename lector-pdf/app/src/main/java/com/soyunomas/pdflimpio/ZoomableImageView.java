package com.soyunomas.pdflimpio;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

final class ZoomableImageView extends ImageView {
    interface OnPageSwipeListener { void onSwipe(int direction); }

    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final PointF last = new PointF();
    private float minScale = 1f;
    private float maxScale = 5f;
    private boolean dragging;
    private OnPageSwipeListener swipeListener;
    private Bitmap currentBitmap;

    ZoomableImageView(Activity context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        setImageMatrix(matrix);
        setAdjustViewBounds(false);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float current = getScale();
                float factor = detector.getScaleFactor();
                float target = current * factor;
                if (target < minScale) factor = minScale / current;
                if (target > maxScale) factor = maxScale / current;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                constrain();
                setImageMatrix(matrix);
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent event) {
                float current = getScale();
                if (current > minScale * 1.2f) {
                    fitToView();
                } else {
                    matrix.postScale(2f, 2f, event.getX(), event.getY());
                    constrain();
                    setImageMatrix(matrix);
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent first, MotionEvent second, float velocityX, float velocityY) {
                if (getScale() <= minScale * 1.05f
                        && Math.abs(velocityX) > Math.abs(velocityY)
                        && Math.abs(velocityX) > 700f) {
                    if (swipeListener != null) swipeListener.onSwipe(velocityX < 0 ? -1 : 1);
                    return true;
                }
                return false;
            }
        });
    }

    void setOnPageSwipeListener(OnPageSwipeListener listener) {
        swipeListener = listener;
    }

    void setImageBitmapAndReset(Bitmap bitmap) {
        if (currentBitmap != null && currentBitmap != bitmap && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
        currentBitmap = bitmap;
        super.setImageBitmap(bitmap);
        post(this::fitToView);
    }

    private void fitToView() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
        float drawableWidth = getDrawable().getIntrinsicWidth();
        float drawableHeight = getDrawable().getIntrinsicHeight();
        if (drawableWidth <= 0 || drawableHeight <= 0) return;
        float scale = Math.min((float) getWidth() / drawableWidth, (float) getHeight() / drawableHeight);
        minScale = scale;
        maxScale = scale * 5f;
        float dx = (getWidth() - drawableWidth * scale) / 2f;
        float dy = (getHeight() - drawableHeight * scale) / 2f;
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    private float getScale() {
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    private void constrain() {
        if (getDrawable() == null) return;
        RectF rect = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        matrix.mapRect(rect);
        float dx = 0f;
        float dy = 0f;
        if (rect.width() <= getWidth()) dx = getWidth() / 2f - rect.centerX();
        else if (rect.left > 0) dx = -rect.left;
        else if (rect.right < getWidth()) dx = getWidth() - rect.right;
        if (rect.height() <= getHeight()) dy = getHeight() / 2f - rect.centerY();
        else if (rect.top > 0) dy = -rect.top;
        else if (rect.bottom < getHeight()) dy = getHeight() - rect.bottom;
        matrix.postTranslate(dx, dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                last.set(event.getX(), event.getY());
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleDetector.isInProgress() && getScale() > minScale * 1.05f) {
                    float dx = event.getX() - last.x;
                    float dy = event.getY() - last.y;
                    matrix.postTranslate(dx, dy);
                    constrain();
                    setImageMatrix(matrix);
                    last.set(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (getScale() < minScale) fitToView();
                break;
            default:
                break;
        }
        return true;
    }
}
