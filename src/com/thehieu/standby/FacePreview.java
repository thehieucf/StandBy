package com.thehieu.standby;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.WindowManager;

/** Ảnh thu nhỏ trực tiếp của một mặt StandBy: vẽ ở kích thước màn thật rồi thu nhỏ. */
final class FacePreview extends android.view.View {
    final Faces faces;
    final Ui ui;
    final float vw, vh;
    final Path clip = new Path();
    final RectF rf = new RectF();
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    int face;
    float radius;
    boolean selected;
    int badge; // 0 không, 1 đang bật, 2 đang tắt
    boolean dimmed;
    /** Hiệu ứng nền: null = không vẽ; fxType < 0 = theo cài đặt. */
    Effects fx;
    int fxType = -1;

    FacePreview(Context c, Ui ui, Faces faces, int face, float radiusPx) {
        super(c);
        this.ui = ui;
        this.faces = faces;
        this.face = face;
        this.radius = radiusPx;
        Rect b = c.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        vw = Math.max(b.width(), b.height());
        vh = Math.min(b.width(), b.height());
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        setMeasuredDimension(w, Math.round(w * vh / vw));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        clip.reset();
        rf.set(0, 0, w, h);
        clip.addRoundRect(rf, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.save();
        c.clipPath(clip);
        c.drawColor(Color.BLACK);
        float s = w / vw;
        c.scale(s, s);
        boolean fxAnim = false;
        if (fx != null) {
            Prefs pr = faces.prefs;
            int type = fxType >= 0 ? fxType : pr.effect();
            int accent = Faces.ACCENTS[Math.floorMod(pr.accent(), Faces.ACCENTS.length)];
            fxAnim = fx.draw(c, type, vw, vh, 0.95f, false, accent, Effects.DENSITY[Math.floorMod(pr.fxDensity(), Effects.DENSITY.length)]);
        }
        faces.animating = false;
        faces.draw(c, face, vw, vh);
        c.restore();
        if (fx != null && face < 0 && fxType == Effects.NONE) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ui.px(1.5f));
            p.setColor(0x55FFFFFF);
            float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) * 0.18f;
            c.drawCircle(cx, cy, r, p);
            c.drawLine(cx - r * 0.7f, cy + r * 0.7f, cx + r * 0.7f, cy - r * 0.7f, p);
        }
        if (dimmed) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x99000000);
            c.drawRoundRect(rf, radius, radius, p);
        }
        if (selected) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ui.px(2.5f));
            p.setColor(ui.accent);
            float i = ui.px(1.25f);
            RectF r = new RectF(i, i, w - i, h - i);
            c.drawRoundRect(r, radius - i, radius - i, p);
        }
        if (badge != 0) {
            float br = ui.px(9), cx = w - ui.px(15), cy = ui.px(15);
            p.setStyle(Paint.Style.FILL);
            p.setColor(badge == 1 ? ui.accent : 0x66000000);
            c.drawCircle(cx, cy, br, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ui.px(1.5f));
            p.setColor(0xFFFFFFFF);
            if (badge == 1) {
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                Path ck = new Path();
                ck.moveTo(cx - br * 0.42f, cy + br * 0.02f);
                ck.lineTo(cx - br * 0.1f, cy + br * 0.34f);
                ck.lineTo(cx + br * 0.45f, cy - br * 0.3f);
                c.drawPath(ck, p);
            } else {
                c.drawCircle(cx, cy, br - ui.px(0.75f), p);
            }
        }
        if (faces.animating || fxAnim) postInvalidateOnAnimation();
    }
}
