package com.thehieu.standby;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Bộ dựng giao diện theo phong cách cài đặt HyperOS. */
final class Ui {
    final Activity a;
    final float dp;
    final boolean dark;
    final int bg, card, text, sub, value, chevron, section, accent, switchOff, press, red, field;

    Ui(Activity a) {
        this.a = a;
        dp = a.getResources().getDisplayMetrics().density;
        dark = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            bg = 0xFF000000; card = 0xFF1A1A1A; text = 0xE6FFFFFF; sub = 0x80FFFFFF; value = 0x66FFFFFF;
            chevron = 0x40FFFFFF; section = 0x73FFFFFF; accent = 0xFF3482FF; switchOff = 0xFF363636;
            press = 0x14FFFFFF; red = 0xFFF2584B; field = 0xFF262626;
        } else {
            bg = 0xFFF7F7F7; card = 0xFFFFFFFF; text = 0xE6000000; sub = 0x80000000; value = 0x66000000;
            chevron = 0x40000000; section = 0x80000000; accent = 0xFF0D84FF; switchOff = 0xFFE5E5E5;
            press = 0x0F000000; red = 0xFFE5392E; field = 0xFFF0F0F0;
        }
    }

    int px(float v) { return Math.round(v * dp); }

    static int lerp(int c1, int c2, float t) {
        int a = Math.round(Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * t);
        int r = Math.round(Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t);
        int g = Math.round(Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t);
        int b = Math.round(Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t);
        return Color.argb(a, r, g, b);
    }

    TextView text(CharSequence s, float sp, int color, int weight) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Fonts.sans(weight));
        t.setIncludeFontPadding(false);
        return t;
    }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radiusDp));
        return g;
    }

    RippleDrawable ripple() {
        return new RippleDrawable(ColorStateList.valueOf(press), null, new ColorDrawable(Color.WHITE));
    }

    LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(card, 20));
        c.setClipToOutline(true);
        LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.leftMargin = l.rightMargin = px(14);
        l.bottomMargin = px(4);
        parent.addView(c, l);
        return c;
    }

    void section(LinearLayout parent, String title) {
        TextView t = text(title, 13.5f, section, 450);
        t.setPadding(px(30), px(22), px(30), px(10));
        parent.addView(t);
    }

    /** Một dòng cài đặt: tiêu đề + mô tả bên trái, phần tử bên phải. */
    static final class Row {
        LinearLayout view;
        TextView title, summary, value;
        MiSwitch sw;
    }

    Row row(LinearLayout card, String title, String summary) {
        Row r = new Row();
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMinimumHeight(px(64));
        v.setPadding(px(22), px(15), px(20), px(15));
        v.setBackground(ripple());
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        r.title = text(title, 16.5f, text, 420);
        texts.addView(r.title);
        r.summary = text(summary == null ? "" : summary, 13f, sub, 340);
        r.summary.setLineSpacing(px(2), 1f);
        LinearLayout.LayoutParams sl = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = px(5);
        texts.addView(r.summary, sl);
        if (summary == null || summary.isEmpty()) r.summary.setVisibility(View.GONE);
        v.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(v, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        r.view = v;
        return r;
    }

    void setSummary(Row r, CharSequence s, int color) {
        r.summary.setText(s);
        r.summary.setTextColor(color);
        r.summary.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
    }

    Row navRow(LinearLayout card, String title, String summary, String value, View.OnClickListener click) {
        Row r = row(card, title, summary);
        r.value = text(value == null ? "" : value, 14f, this.value, 380);
        r.value.setMaxWidth(px(150));
        r.value.setSingleLine(true);
        r.value.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vl = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.leftMargin = px(12);
        r.view.addView(r.value, vl);
        LinearLayout.LayoutParams cl = lp(px(8), px(14));
        cl.leftMargin = px(8);
        r.view.addView(chevron(), cl);
        r.view.setOnClickListener(click);
        return r;
    }

    Row switchRow(LinearLayout card, Prefs prefs, String title, String summary, String key, boolean def, Runnable after) {
        Row r = row(card, title, summary);
        r.sw = new MiSwitch(a, this, prefs.get(key, def));
        r.sw.listener = on -> {
            prefs.put(key, on);
            if (after != null) after.run();
        };
        LinearLayout.LayoutParams l = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.leftMargin = px(16);
        r.view.addView(r.sw, l);
        r.view.setOnClickListener(v -> r.sw.toggle());
        return r;
    }

    View chevron() {
        return new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Path path = new Path();

            @Override
            protected void onDraw(Canvas c) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(px(1.6f));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(chevron);
                float w = getWidth(), h = getHeight(), i = px(1.2f);
                path.reset();
                path.moveTo(i, i);
                path.lineTo(w - i, h / 2f);
                path.lineTo(i, h - i);
                c.drawPath(path, p);
            }
        };
    }

    TextView pill(String label, boolean primary, View.OnClickListener click) {
        TextView b = text(label, 16f, primary ? 0xFFFFFFFF : accent, 500);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = round(primary ? accent : field, 16);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x33FFFFFF : press), g, null));
        b.setOnClickListener(click);
        return b;
    }

    /** Bảng chọn trượt từ dưới lên kiểu HyperOS. */
    Dialog sheet(String title, View content) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(dark ? 0xFF1F1F1F : 0xFFFFFFFF, 32));
        box.setClipToOutline(true);
        box.setPadding(0, px(26), 0, px(18));
        TextView t = text(title, 18f, text, 600);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(24), 0, px(24), px(16));
        box.addView(t);
        ScrollView sv = new ScrollView(a);
        sv.addView(content);
        sv.setVerticalScrollBarEnabled(false);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView cancel = pill("Hủy", false, v -> d.dismiss());
        cancel.setTextColor(text);
        LinearLayout.LayoutParams cl = lp(ViewGroup.LayoutParams.MATCH_PARENT, px(50));
        cl.leftMargin = cl.rightMargin = px(24);
        cl.topMargin = px(14);
        box.addView(cancel, cl);
        FrameLayout wrap = new FrameLayout(a);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bl.leftMargin = bl.rightMargin = px(12);
        bl.bottomMargin = px(14);
        wrap.addView(box, bl);
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.3f);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        return d;
    }

    /** Một dòng trong bảng chọn, có dấu tích xanh khi đang chọn. */
    View option(View label, boolean selected, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(px(58));
        r.setPadding(px(28), px(8), px(28), px(8));
        r.setBackground(ripple());
        r.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View check = new View(a) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                if (!selected) return;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(px(2.2f));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(accent);
                float w = getWidth(), h = getHeight();
                Path ck = new Path();
                ck.moveTo(w * 0.08f, h * 0.52f);
                ck.lineTo(w * 0.38f, h * 0.82f);
                ck.lineTo(w * 0.92f, h * 0.18f);
                c.drawPath(ck, p);
            }
        };
        r.addView(check, lp(px(18), px(14)));
        r.setOnClickListener(click);
        return r;
    }
}
