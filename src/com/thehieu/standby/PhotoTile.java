package com.thehieu.standby;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.io.File;

/** Ô ảnh vuông bo góc (cắt giữa), có vòng chọn kiểu Thư viện HyperOS. */
final class PhotoTile extends android.view.View {
    final Ui ui;
    final File file;
    final int thumbSize;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Path clip = new Path();
    final RectF rf = new RectF();
    final Matrix mx = new Matrix();
    float radius;
    boolean selectable, selected;

    PhotoTile(Context c, Ui ui, File file, int thumbSize, float radius) {
        super(c);
        this.ui = ui;
        this.file = file;
        this.thumbSize = thumbSize;
        this.radius = radius;
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        if (MeasureSpec.getMode(ws) == MeasureSpec.UNSPECIFIED) w = MeasureSpec.getSize(hs);
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        clip.reset();
        rf.set(0, 0, w, h);
        clip.addRoundRect(rf, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        c.save();
        c.clipPath(clip);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ui.field);
        c.drawRect(0, 0, w, h, p);
        Bitmap b = Photos.thumb(file, thumbSize, this::invalidate);
        if (b != null) {
            float s = Math.max(w / b.getWidth(), h / b.getHeight());
            mx.setScale(s, s);
            mx.postTranslate((w - b.getWidth() * s) / 2f, (h - b.getHeight() * s) / 2f);
            c.drawBitmap(b, mx, p);
        }
        if (selected) {
            p.setColor(0x40000000);
            c.drawRect(0, 0, w, h, p);
        }
        c.restore();
        if (selected) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ui.px(3));
            p.setColor(ui.accent);
            float i = ui.px(1.5f);
            RectF r = new RectF(i, i, w - i, h - i);
            c.drawRoundRect(r, radius - i, radius - i, p);
        }
        if (selectable) {
            float br = ui.px(11), cx = w - ui.px(16), cy = ui.px(16);
            p.setStyle(Paint.Style.FILL);
            p.setColor(selected ? ui.accent : 0x33000000);
            c.drawCircle(cx, cy, br, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ui.px(1.6f));
            p.setColor(0xFFFFFFFF);
            if (selected) {
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                Path ck = new Path();
                ck.moveTo(cx - br * 0.42f, cy + br * 0.02f);
                ck.lineTo(cx - br * 0.1f, cy + br * 0.34f);
                ck.lineTo(cx + br * 0.45f, cy - br * 0.3f);
                c.drawPath(ck, p);
            } else {
                c.drawCircle(cx, cy, br - ui.px(0.8f), p);
            }
        }
    }
}
