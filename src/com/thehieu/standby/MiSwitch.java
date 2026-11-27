package com.thehieu.standby;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Công tắc kiểu HyperOS: rãnh bo tròn, núm trắng trượt mềm. */
final class MiSwitch extends View {
    interface OnChange { void onChange(boolean on); }

    final Ui ui;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF();
    boolean checked;
    float pos;
    ValueAnimator anim;
    OnChange listener;

    MiSwitch(Context c, Ui ui, boolean on) {
        super(c);
        this.ui = ui;
        checked = on;
        pos = on ? 1f : 0f;
        setOnClickListener(v -> toggle());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void toggle() { setChecked(!checked, true); }

    void setChecked(boolean on, boolean fromUser) {
        if (on == checked) return;
        checked = on;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(pos, on ? 1f : 0f);
        anim.setDuration(260);
        anim.setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.25f, 1f));
        anim.addUpdateListener(a -> {
            pos = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
        if (fromUser) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.onChange(on);
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(ui.px(50), ui.px(30));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Ui.lerp(ui.switchOff, ui.accent, pos));
        rf.set(0, 0, w, h);
        c.drawRoundRect(rf, h / 2, h / 2, p);
        float r = h / 2f - ui.px(3);
        float cx = h / 2f + (w - h) * pos;
        p.setColor(0x1A000000);
        c.drawCircle(cx, h / 2f + ui.px(0.6f), r + ui.px(0.6f), p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, h / 2f, r, p);
    }
}
