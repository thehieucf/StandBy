package com.thehieu.standby;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Vẽ các mặt StandBy. Dùng chung cho màn StandBy thật và ảnh xem trước trong cài đặt.
 * Mọi kích thước tính theo dp nên vẽ ở "màn ảo" rồi thu nhỏ vẫn đúng tỉ lệ.
 */
final class Faces {
    static final int BIG = 0, ANALOG = 1, INFO = 2, BLOC = 3, FLIP = 4, OUTLINE = 5, WORLD = 6, PHOTO = 7, WORDS = 8;
    static final int BIG_ANALOG = 9, LED = 10, RINGS = 11, ROLL = 12, SUN = 13, TIMELINE = 14;
    static final int COUNT = 15;
    static final int[] ORDER = {BIG, FLIP, OUTLINE, LED, ROLL, ANALOG, BIG_ANALOG, RINGS, BLOC, SUN, TIMELINE, WORDS, WORLD, INFO, PHOTO};
    static final String[] NAMES = {"Số lớn", "Kim & lịch", "Pin & báo thức", "Lịch âm dương", "Lật số",
            "Chữ viền", "Giờ thế giới", "Khung ảnh", "Bằng chữ",
            "Kim lớn", "Đèn LED", "Vòng tròn", "Cuộn số", "Mặt trời & trăng", "Dòng thời gian"};
    /** Vị trí tính giờ mặt trời mọc/lặn (Hà Nội). */
    static final double LAT = 21.03, LON = 105.85;

    static final int[] ACCENTS = {0xFFF4EFE6, 0xFFFFB547, 0xFF8FE3B8, 0xFF8CBFFF, 0xFFFF8E9E, 0xFFC9A8FF, 0xFFFF7A45, 0xFFE8E27A};
    static final String[] ACCENT_NAMES = {"Trắng ngà", "Hổ phách", "Bạc hà", "Xanh trời", "Hồng", "Oải hương", "Cam đất", "Vàng chanh"};
    static final String[] WD = {"", "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
    static final String[] WD_SHORT = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
    static final String[] WORLD_ZONES = {null, "Asia/Tokyo", "Europe/Paris", "America/New_York"};
    static final String[] WORLD_NAMES = {null, "Tokyo", "Paris", "New York"};
    static final long PHOTO_MS = 40_000, PHOTO_FADE = 1_800, FLIP_MS = 640;

    final float dp;
    final Prefs prefs;
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final Rect rect = new Rect();
    final RectF rf = new RectF();
    final Matrix mx = new Matrix();
    final Calendar cal = Calendar.getInstance();
    final Typeface s300, s400, s500, s600, s700;
    Photos photos;

    float alpha = 1f;
    boolean night;
    int battery = -1;
    boolean charging, full;
    long chargeLeft = -1;
    String alarm;
    long alarmAt = -1;
    /** Đặt true trong lúc vẽ nếu mặt cần khung hình tiếp theo (lật số, ảnh trôi). */
    boolean animating;

    final String[] flipVal = new String[4];
    final String[] flipPrev = new String[4];
    final long[] flipT0 = new long[4];

    int lunarKey = -1;
    int[] lunar;
    int jd;

    Faces(Context c, Prefs prefs) {
        this.prefs = prefs;
        dp = c.getResources().getDisplayMetrics().density;
        s300 = Fonts.sans(300);
        s400 = Fonts.sans(400);
        s500 = Fonts.sans(500);
        s600 = Fonts.sans(600);
        s700 = Fonts.sans(700);
    }

    // ---------- màu ----------

    int withA(int c, float a) {
        float k = a * alpha * (night ? 0.75f : 1f);
        int A = Math.round(255 * Math.max(0f, Math.min(1f, k)));
        return (c & 0x00FFFFFF) | (A << 24);
    }

    int fg(float a) { return withA(night ? 0xFFC8231B : 0xFFF2EFEA, a); }

    int ac(float a) {
        int base = night ? 0xFFFF3626 : ACCENTS[Math.floorMod(prefs.accent(), ACCENTS.length)];
        return withA(base, a);
    }

    // ---------- chữ ----------

    static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    String hh(Calendar k) {
        int H = k.get(Calendar.HOUR_OF_DAY);
        if (prefs.h24()) return two(H);
        H %= 12;
        return String.valueOf(H == 0 ? 12 : H);
    }

    String mm(Calendar k) { return two(k.get(Calendar.MINUTE)); }

    String time(Calendar k) { return hh(k) + ":" + mm(k); }

    String dateLine(boolean withYear) {
        String s = WD[cal.get(Calendar.DAY_OF_WEEK)] + ", " + cal.get(Calendar.DAY_OF_MONTH)
                + " tháng " + (cal.get(Calendar.MONTH) + 1);
        return withYear ? s + " năm " + cal.get(Calendar.YEAR) : s;
    }

    int[] lunarToday() {
        int d = cal.get(Calendar.DAY_OF_MONTH), m = cal.get(Calendar.MONTH) + 1, y = cal.get(Calendar.YEAR);
        int key = y * 10000 + m * 100 + d;
        if (key != lunarKey) {
            lunar = Lunar.fromSolar(d, m, y);
            jd = Lunar.jdFromDate(d, m, y);
            lunarKey = key;
        }
        return lunar;
    }

    Typeface clockFace() { return Fonts.clock(prefs.font()); }

    float digitHeight(Paint p) {
        p.getTextBounds("0123456789", 0, 10, rect);
        return rect.height();
    }

    float fit(Paint p, String s, float maxW, float maxH) {
        p.setTextSize(100);
        float w = p.measureText(s);
        float h = digitHeight(p);
        return Math.min(maxW * 100 / Math.max(1, w), maxH * 100 / Math.max(1, h));
    }

    float fitText(Paint p, String s, float maxW, float maxSize) {
        p.setTextSize(100);
        return Math.min(maxSize, maxW * 100 / Math.max(1, p.measureText(s)));
    }

    float text(Canvas c, String s, float x, float y, float size, Typeface tf, int color, Paint.Align al) {
        tp.setTypeface(tf);
        tp.setFontFeatureSettings(null);
        tp.setStyle(Paint.Style.FILL);
        tp.setTextSize(size);
        tp.setTextAlign(al);
        tp.setColor(color);
        c.drawText(s, x, y, tp);
        return tp.measureText(s);
    }

    static String ampm(Calendar k) { return k.get(Calendar.AM_PM) == Calendar.AM ? "SA" : "CH"; }

    // ---------- vào ----------

    void draw(Canvas c, int face, float w, float h) {
        if (face < 0) return;
        cal.setTimeInMillis(System.currentTimeMillis());
        switch (face) {
            case ANALOG: drawAnalogCalendar(c, w, h); break;
            case INFO: drawInfo(c, w, h); break;
            case BLOC: drawBloc(c, w, h); break;
            case FLIP: drawFlip(c, w, h); break;
            case OUTLINE: drawOutline(c, w, h); break;
            case WORLD: drawWorld(c, w, h); break;
            case PHOTO: drawPhoto(c, w, h); break;
            case WORDS: drawWords(c, w, h); break;
            case BIG_ANALOG: drawBigAnalog(c, w, h); break;
            case LED: drawLed(c, w, h); break;
            case RINGS: drawRings(c, w, h); break;
            case ROLL: drawRoll(c, w, h); break;
            case SUN: drawSun(c, w, h); break;
            case TIMELINE: drawTimeline(c, w, h); break;
            default: drawBig(c, w, h); break;
        }
    }

    float margin() { return 30 * dp; }

    // ---------- 1. Số lớn ----------

    void drawBig(Canvas c, float w, float h) {
        float m = margin();
        tp.setTypeface(clockFace());
        tp.setStyle(Paint.Style.FILL);
        tp.setFontFeatureSettings("tnum");
        tp.setTextAlign(Paint.Align.LEFT);
        float size = fit(tp, "00:00", w - 2 * m - 24 * dp, h * 0.64f);
        tp.setTextSize(size);
        float dh = digitHeight(tp);
        String H = hh(cal), M = mm(cal);
        float wH = tp.measureText(H), wC = tp.measureText(":"), wM = tp.measureText(M);
        float x = (w - (wH + wC + wM)) / 2f;
        float base = h / 2f + dh / 2f + 16 * dp;
        tp.setColor(ac(1));
        c.drawText(H, x, base, tp);
        tp.setColor(ac(0.45f));
        c.drawText(":", x + wH, base, tp);
        tp.setColor(ac(1));
        c.drawText(M, x + wH + wC, base, tp);

        float top = m + 20 * dp;
        text(c, dateLine(false), m, top, 21 * dp, s500, fg(0.72f), Paint.Align.LEFT);
        if (!prefs.h24()) text(c, ampm(cal), w - m, top, 21 * dp, s500, fg(0.72f), Paint.Align.RIGHT);
        else if (battery >= 0) drawBatteryBadge(c, w - m, top - 7 * dp);
    }

    void drawBatteryBadge(Canvas c, float right, float cy) {
        float bh = 15 * dp, bw = 30 * dp;
        float l = right - bw - 3 * dp;
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeWidth(1.6f * dp);
        sp.setColor(fg(0.55f));
        rf.set(l, cy - bh / 2, l + bw, cy + bh / 2);
        c.drawRoundRect(rf, 4 * dp, 4 * dp, sp);
        sp.setStyle(Paint.Style.FILL);
        rf.set(l + bw + 1.2f * dp, cy - bh * 0.22f, l + bw + 3 * dp, cy + bh * 0.22f);
        c.drawRoundRect(rf, 1.5f * dp, 1.5f * dp, sp);
        float inset = 3 * dp;
        float lv = Math.max(0, Math.min(100, battery)) / 100f;
        sp.setColor(battery <= 20 && !charging ? withA(0xFFFF5A4E, 0.9f) : ac(0.9f));
        rf.set(l + inset, cy - bh / 2 + inset, l + inset + (bw - 2 * inset) * lv, cy + bh / 2 - inset);
        c.drawRoundRect(rf, 2 * dp, 2 * dp, sp);
        text(c, battery + "%", l - 8 * dp, cy + 7 * dp, 19 * dp, s500, fg(0.72f), Paint.Align.RIGHT);
    }

    // ---------- 2. Kim & lịch ----------

    void analog(Canvas c, float cx, float cy, float R, Calendar k, boolean numerals, boolean fine, boolean seconds, float dimHands) {
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeCap(Paint.Cap.ROUND);
        int n = fine ? 60 : 12;
        for (int i = 0; i < n; i++) {
            boolean hour = !fine || i % 5 == 0;
            double a = Math.toRadians(i * 360.0 / n);
            float r2 = hour ? R * 0.9f : R * 0.965f;
            sp.setStrokeWidth(hour ? R * 0.022f : R * 0.009f);
            sp.setColor(fg(hour ? 0.85f * dimHands : 0.32f * dimHands));
            float s = (float) Math.sin(a), co = (float) -Math.cos(a);
            c.drawLine(cx + s * R, cy + co * R, cx + s * r2, cy + co * r2, sp);
        }
        if (numerals) {
            tp.setTypeface(s500);
            tp.setFontFeatureSettings(null);
            tp.setStyle(Paint.Style.FILL);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(R * 0.15f);
            tp.setColor(fg(0.8f));
            float nh = digitHeight(tp);
            for (int i = 1; i <= 12; i++) {
                double a = Math.toRadians(i * 30);
                float r = R * 0.74f;
                c.drawText(String.valueOf(i), cx + (float) Math.sin(a) * r, cy - (float) Math.cos(a) * r + nh / 2, tp);
            }
        }
        float sec = k.get(Calendar.SECOND);
        float min = k.get(Calendar.MINUTE) + sec / 60f;
        float hr = k.get(Calendar.HOUR) + min / 60f;
        hand(c, cx, cy, hr * 30, R * 0.5f, R * 0.08f, R * 0.06f, fg(dimHands));
        hand(c, cx, cy, min * 6, R * 0.8f, R * 0.08f, R * 0.04f, fg(dimHands));
        if (seconds) hand(c, cx, cy, sec * 6, R * 0.88f, R * 0.18f, R * 0.014f, ac(1));
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(ac(1));
        c.drawCircle(cx, cy, R * 0.035f, sp);
        sp.setColor(Color.BLACK);
        c.drawCircle(cx, cy, R * 0.014f, sp);
    }

    void hand(Canvas c, float cx, float cy, float deg, float len, float tail, float width, int color) {
        double a = Math.toRadians(deg);
        float s = (float) Math.sin(a), co = (float) -Math.cos(a);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeWidth(width);
        sp.setColor(color);
        c.drawLine(cx - s * tail, cy - co * tail, cx + s * len, cy + co * len, sp);
    }

    void drawAnalogCalendar(Canvas c, float w, float h) {
        float m = margin();
        float side = Math.min(h - 2 * m, w * 0.42f);
        float R = side / 2f;
        float cx = m + R + w * 0.025f, cy = h / 2f;
        analog(c, cx, cy, R, cal, true, true, true, 1f);

        float left = cx + R + w * 0.07f, right = w - m;
        float cw = (right - left) / 7f;
        float rowH = (h - 2 * m) / 8.7f;
        float top = m;
        int month = cal.get(Calendar.MONTH), year = cal.get(Calendar.YEAR), today = cal.get(Calendar.DAY_OF_MONTH);
        float tx = left + cw * 0.18f;
        String title = "Tháng " + (month + 1);
        float tw = text(c, title, tx, top + rowH * 0.85f, rowH * 0.66f, s700, ac(1), Paint.Align.LEFT);
        text(c, String.valueOf(year), tx + tw + 10 * dp, top + rowH * 0.85f, rowH * 0.66f, s400, fg(0.45f), Paint.Align.LEFT);
        int[] l = lunarToday();
        text(c, "Âm lịch " + l[0] + "/" + l[1] + (l[3] == 1 ? " nhuận" : "") + " · năm " + Lunar.yearName(l[2]),
                tx, top + rowH * 1.55f, rowH * 0.36f, s400, fg(0.55f), Paint.Align.LEFT);

        float hy = top + rowH * 2.45f;
        for (int i = 0; i < 7; i++) {
            text(c, WD_SHORT[i], left + cw * (i + 0.5f), hy, rowH * 0.34f, s500,
                    fg(i == 6 ? 0.6f : 0.42f), Paint.Align.CENTER);
        }
        Calendar k = (Calendar) cal.clone();
        k.set(Calendar.DAY_OF_MONTH, 1);
        int lead = (k.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = k.getActualMaximum(Calendar.DAY_OF_MONTH);
        float gridTop = top + rowH * 2.7f;
        tp.setTypeface(s500);
        tp.setTextSize(rowH * 0.44f);
        float th = digitHeight(tp);
        for (int d = 1; d <= days; d++) {
            int idx = lead + d - 1, col = idx % 7, row = idx / 7;
            float x = left + cw * (col + 0.5f);
            float y = gridTop + rowH * (row + 0.5f);
            int color;
            if (d == today) {
                sp.setStyle(Paint.Style.FILL);
                sp.setColor(ac(1));
                c.drawCircle(x, y, Math.min(cw, rowH) * 0.44f, sp);
                color = withA(0xFF000000, 1f / Math.max(0.01f, alpha));
            } else {
                color = fg(col == 6 ? 0.75f : 0.62f);
            }
            text(c, String.valueOf(d), x, y + th / 2, rowH * 0.44f, d == today ? s700 : s500, color, Paint.Align.CENTER);
        }
    }

    // ---------- 3. Pin & báo thức ----------

    void drawInfo(Canvas c, float w, float h) {
        float m = margin();
        float x0 = m + w * 0.02f;
        Typeface f = clockFace();
        tp.setTypeface(f);
        tp.setStyle(Paint.Style.FILL);
        tp.setFontFeatureSettings("tnum");
        float size = fit(tp, "00:00", w * 0.44f, h * 0.34f);
        tp.setTextSize(size);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setColor(ac(1));
        float base = h * 0.46f;
        String t = time(cal);
        c.drawText(t, x0, base, tp);
        if (!prefs.h24()) {
            float tw = tp.measureText(t);
            text(c, ampm(cal), x0 + tw + 8 * dp, base, 22 * dp, s500, ac(0.8f), Paint.Align.LEFT);
        }
        float y = base + 44 * dp;
        text(c, dateLine(true), x0, y, 22 * dp, s500, fg(0.85f), Paint.Align.LEFT);
        int[] l = lunarToday();
        y += 32 * dp;
        text(c, "Âm lịch " + l[0] + " tháng " + Lunar.monthName(l[1]) + (l[3] == 1 ? " nhuận" : "") + ", năm " + Lunar.yearName(l[2]),
                x0, y, 18 * dp, s400, fg(0.55f), Paint.Align.LEFT);
        if (alarm != null) {
            y += 30 * dp;
            text(c, alarm, x0, y, 18 * dp, s500, ac(0.85f), Paint.Align.LEFT);
        }

        float R = Math.min(h * 0.27f, w * 0.12f);
        float cx = w - m - R - w * 0.06f, cy = h * 0.45f;
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeWidth(R * 0.12f);
        sp.setColor(fg(0.1f));
        c.drawCircle(cx, cy, R, sp);
        if (battery >= 0) {
            sp.setColor(battery <= 20 && !charging ? withA(0xFFFF5A4E, 1f) : ac(1));
            rf.set(cx - R, cy - R, cx + R, cy + R);
            c.drawArc(rf, -90, 360f * Math.max(1, battery) / 100f, false, sp);
            tp.setTypeface(f);
            tp.setFontFeatureSettings("tnum");
            tp.setTextSize(R * 0.62f);
            float bh = digitHeight(tp);
            String pct = String.valueOf(battery);
            float pw = tp.measureText(pct);
            tp.setTypeface(s500);
            tp.setTextSize(R * 0.26f);
            float ps = tp.measureText("%");
            float sx = cx - (pw + 3 * dp + ps) / 2f;
            tp.setTypeface(f);
            tp.setTextSize(R * 0.62f);
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setColor(fg(0.95f));
            c.drawText(pct, sx, cy + bh / 2, tp);
            text(c, "%", sx + pw + 3 * dp, cy + bh / 2, R * 0.26f, s500, fg(0.6f), Paint.Align.LEFT);
        }
        String st;
        if (full || battery >= 100) st = "Đã sạc đầy";
        else if (charging && chargeLeft > 0) st = "Đầy sau " + duration(chargeLeft);
        else if (charging) st = "Đang sạc";
        else st = "Không sạc";
        text(c, st, cx, cy + R + 40 * dp, 18 * dp, s500, fg(0.65f), Paint.Align.CENTER);
    }

    static String duration(long ms) {
        long min = Math.max(1, Math.round(ms / 60000.0));
        if (min < 60) return min + " phút";
        long hh = min / 60, mm = min % 60;
        return mm == 0 ? hh + " giờ" : hh + " giờ " + mm + " phút";
    }

    // ---------- 4. Lịch âm dương (kiểu lịch bloc) ----------

    void drawBloc(Canvas c, float w, float h) {
        float m = margin();
        int[] l = lunarToday();
        float mid = w * 0.47f;

        // Dương lịch bên trái
        float lc = m + (mid - m) / 2f;
        text(c, "Tháng " + (cal.get(Calendar.MONTH) + 1) + " năm " + cal.get(Calendar.YEAR),
                lc, m + 24 * dp, 22 * dp, s600, ac(0.95f), Paint.Align.CENTER);
        tp.setTypeface(clockFace());
        tp.setFontFeatureSettings("tnum");
        float ds = fit(tp, "00", (mid - m) * 0.8f, h * 0.5f);
        tp.setTextSize(ds);
        float dh = digitHeight(tp);
        float dBase = h * 0.5f + dh / 2f + 4 * dp;
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(ac(1));
        c.drawText(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), lc, dBase, tp);
        text(c, WD[cal.get(Calendar.DAY_OF_WEEK)], lc, h - m - 8 * dp, 30 * dp, s600, fg(0.92f), Paint.Align.CENTER);

        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeWidth(1.2f * dp);
        sp.setColor(fg(0.16f));
        c.drawLine(mid, m + 10 * dp, mid, h - m - 10 * dp, sp);

        // Âm lịch bên phải
        float x = mid + w * 0.05f;
        float y = m + 24 * dp;
        text(c, "Âm lịch", x, y, 18 * dp, s500, fg(0.5f), Paint.Align.LEFT);
        text(c, time(cal) + (prefs.h24() ? "" : " " + ampm(cal)), w - m, y, 22 * dp, s500, fg(0.8f), Paint.Align.RIGHT);
        tp.setTypeface(clockFace());
        float ls = fit(tp, "00", w * 0.16f, h * 0.27f);
        tp.setTextSize(ls);
        float lh = digitHeight(tp);
        float lBase = y + 22 * dp + lh;
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setColor(fg(0.95f));
        String ld = String.valueOf(l[0]);
        c.drawText(ld, x - ls * 0.03f, lBase, tp);
        float lw = tp.measureText(ld);
        float tx = x + lw + 18 * dp;
        text(c, "tháng " + Lunar.monthName(l[1]) + (l[3] == 1 ? " nhuận" : ""), tx, lBase - lh * 0.52f, 24 * dp, s600, fg(0.9f), Paint.Align.LEFT);
        text(c, "năm " + Lunar.yearName(l[2]), tx, lBase - lh * 0.04f, 20 * dp, s400, fg(0.6f), Paint.Align.LEFT);

        float maxW = w - m - x;
        String cc = "Ngày " + Lunar.dayCanChi(jd) + " · tháng " + Lunar.monthCanChi(l[1], l[2]);
        text(c, cc, x, lBase + 42 * dp, fitText(tp, cc, maxW, 20 * dp), s500, fg(0.8f), Paint.Align.LEFT);
        String gio = Lunar.gioHoangDao(jd);
        float gy = h - m - 8 * dp;
        text(c, gio, x, gy, fitText(tp, gio, maxW, 19 * dp), s400, fg(0.7f), Paint.Align.LEFT);
        text(c, "Giờ hoàng đạo", x, gy - 30 * dp, 16 * dp, s500, ac(0.9f), Paint.Align.LEFT);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeWidth(1.2f * dp);
        sp.setColor(fg(0.16f));
        c.drawLine(x, gy - 58 * dp, w - m, gy - 58 * dp, sp);
    }

    // ---------- 5. Lật số ----------

    void drawFlip(Canvas c, float w, float h) {
        float m = margin();
        float g1 = 10 * dp, g2 = 44 * dp;
        float tw = (w - 2 * m - 2 * g1 - g2 - 16 * dp) / 4f;
        float th = Math.min(h - 2 * m - 56 * dp, tw * 1.42f);
        tw = Math.min(tw, th / 1.25f);
        float total = 4 * tw + 2 * g1 + g2;
        float x = (w - total) / 2f, y = (h - th) / 2f + 18 * dp;
        String H = prefs.h24() ? hh(cal) : (hh(cal).length() == 1 ? " " + hh(cal) : hh(cal));
        String M = mm(cal);
        String[] digits = {H.substring(0, 1), H.substring(1, 2), M.substring(0, 1), M.substring(1, 2)};
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < 4; i++) {
            if (flipVal[i] == null) {
                flipVal[i] = digits[i];
                flipPrev[i] = digits[i];
                flipT0[i] = 0;
            } else if (!flipVal[i].equals(digits[i])) {
                flipPrev[i] = flipVal[i];
                flipVal[i] = digits[i];
                flipT0[i] = now + i * 70L;
            }
            float tx = x + i * tw + (i >= 1 ? g1 : 0) + (i >= 2 ? g2 : 0) + (i >= 3 ? g1 : 0);
            float t = flipT0[i] == 0 ? 1f : Math.max(0f, Math.min(1f, (now - flipT0[i]) / (float) FLIP_MS));
            if (t < 1f) animating = true;
            drawTile(c, tx, y, tw, th, flipVal[i], flipPrev[i], t);
        }
        // dấu hai chấm
        float cxc = x + 2 * tw + g1 + g2 / 2f;
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(ac(0.85f));
        c.drawCircle(cxc, y + th * 0.36f, 5 * dp, sp);
        c.drawCircle(cxc, y + th * 0.64f, 5 * dp, sp);

        text(c, dateLine(false), x, y - 20 * dp, 20 * dp, s500, fg(0.7f), Paint.Align.LEFT);
        if (!prefs.h24()) text(c, ampm(cal), x + total, y - 20 * dp, 20 * dp, s500, fg(0.7f), Paint.Align.RIGHT);
        else if (battery >= 0) drawBatteryBadge(c, x + total, y - 27 * dp);
    }

    void drawTile(Canvas c, float x, float y, float tw, float th, String cur, String prev, float t) {
        float mid = y + th / 2f;
        if (t >= 1f) {
            half(c, x, y, tw, th, cur, true, 1f, 0f);
            half(c, x, y, tw, th, cur, false, 1f, 0f);
        } else {
            half(c, x, y, tw, th, cur, true, 1f, 0f);
            half(c, x, y, tw, th, prev, false, 1f, 0f);
            float e = t < 0.5f ? t / 0.5f : (t - 0.5f) / 0.5f;
            e = e * e * (3 - 2 * e);
            if (t < 0.5f) half(c, x, y, tw, th, prev, true, 1f - e, e * 0.6f);
            else half(c, x, y, tw, th, cur, false, e, (1f - e) * 0.6f);
        }
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(Color.BLACK);
        c.drawRect(x, mid - 1.5f * dp, x + tw, mid + 1.5f * dp, sp);
    }

    void half(Canvas c, float x, float y, float tw, float th, String d, boolean top, float scaleY, float shade) {
        float mid = y + th / 2f;
        c.save();
        if (top) c.clipRect(x, y, x + tw, mid);
        else c.clipRect(x, mid, x + tw, y + th);
        c.scale(1f, Math.max(0.001f, scaleY), x + tw / 2f, mid);
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(withA(night ? 0xFF240806 : 0xFF19191B, 1f));
        rf.set(x, y, x + tw, y + th);
        c.drawRoundRect(rf, tw * 0.1f, tw * 0.1f, sp);
        tp.setTypeface(clockFace());
        tp.setFontFeatureSettings("tnum");
        tp.setStyle(Paint.Style.FILL);
        tp.setTextAlign(Paint.Align.CENTER);
        float size = fit(tp, "0", tw * 0.78f, th * 0.6f);
        tp.setTextSize(size);
        float dh = digitHeight(tp);
        tp.setColor(fg(0.95f));
        c.drawText(d, x + tw / 2f, mid + dh / 2f, tp);
        if (shade > 0) {
            sp.setColor(Color.argb(Math.round(255 * shade), 0, 0, 0));
            c.drawRect(x, y, x + tw, y + th, sp);
        }
        c.restore();
    }

    // ---------- 6. Chữ viền ----------

    void drawOutline(Canvas c, float w, float h) {
        float m = margin();
        Typeface f = Fonts.clock(Fonts.OUTLINE_FONT);
        tp.setTypeface(f);
        tp.setFontFeatureSettings("tnum");
        tp.setTextAlign(Paint.Align.LEFT);
        String H = hh(cal), M = mm(cal);
        float gap = 44 * dp;
        float size = fit(tp, "0000", w - 2 * m - gap - 40 * dp, h - 2 * m - 30 * dp);
        tp.setTextSize(size);
        float dh = digitHeight(tp);
        float wH = tp.measureText(H), wM = tp.measureText(M);
        float x = (w - wH - gap - wM) / 2f;
        float base = h / 2f + dh / 2f;
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(2.4f * dp);
        tp.setStrokeJoin(Paint.Join.ROUND);
        tp.setColor(fg(0.9f));
        c.drawText(H, x, base, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(ac(1));
        c.drawText(M, x + wH + gap, base, tp);
        float by = base + 30 * dp;
        if (by > h - 12 * dp) by = h - 12 * dp;
        text(c, WD[cal.get(Calendar.DAY_OF_WEEK)] + " " + cal.get(Calendar.DAY_OF_MONTH) + "/" + (cal.get(Calendar.MONTH) + 1)
                + (prefs.h24() ? "" : " · " + ampm(cal)), x + wH + gap + wM, by, 18 * dp, s500, fg(0.6f), Paint.Align.RIGHT);
    }

    // ---------- 7. Giờ thế giới ----------

    void drawWorld(Canvas c, float w, float h) {
        float m = margin();
        float colW = (w - 2 * m) / 4f;
        float R = Math.min(colW * 0.36f, h * 0.24f);
        float cy = m + 20 * dp + R;
        TimeZone local = TimeZone.getDefault();
        long now = System.currentTimeMillis();
        int localDay = cal.get(Calendar.DAY_OF_YEAR);
        int localOff = local.getOffset(now);
        for (int i = 0; i < 4; i++) {
            TimeZone tz = WORLD_ZONES[i] == null ? local : TimeZone.getTimeZone(WORLD_ZONES[i]);
            Calendar k = Calendar.getInstance(tz);
            k.setTimeInMillis(now);
            float cx = m + colW * (i + 0.5f);
            int hr = k.get(Calendar.HOUR_OF_DAY);
            boolean dark = hr < 6 || hr >= 18;
            analog(c, cx, cy, R, k, false, false, i == 0, dark ? 0.55f : 1f);
            String name = WORLD_NAMES[i] != null ? WORLD_NAMES[i] : localName(local);
            float y = cy + R + 40 * dp;
            text(c, name, cx, y, 21 * dp, s600, fg(0.92f), Paint.Align.CENTER);
            y += 28 * dp;
            String rel;
            int diffMin = (tz.getOffset(now) - localOff) / 60000;
            if (i == 0) rel = "Tại đây";
            else {
                String hrs = diffMin % 60 == 0 ? String.valueOf(Math.abs(diffMin / 60)) : String.format(java.util.Locale.US, "%.1f", Math.abs(diffMin / 60f));
                rel = (diffMin == 0 ? "Cùng giờ" : (diffMin > 0 ? "+" : "−") + hrs + " giờ");
                int day = k.get(Calendar.DAY_OF_YEAR);
                if (k.get(Calendar.YEAR) != cal.get(Calendar.YEAR)) day += (k.get(Calendar.YEAR) > cal.get(Calendar.YEAR) ? 400 : -400);
                if (day > localDay) rel += " · Ngày mai";
                else if (day < localDay) rel += " · Hôm qua";
                else rel += " · Hôm nay";
            }
            text(c, rel, cx, y, 16 * dp, s400, fg(0.5f), Paint.Align.CENTER);
            y += 30 * dp;
            text(c, time(k) + (prefs.h24() ? "" : " " + ampm(k)), cx, y, 19 * dp, s500, ac(dark ? 0.6f : 0.95f), Paint.Align.CENTER);
        }
    }

    static String localName(TimeZone tz) {
        String id = tz.getID();
        if (id.contains("Ho_Chi_Minh") || id.contains("Saigon") || id.contains("Bangkok") || id.contains("Hanoi")) return "Việt Nam";
        int s = id.lastIndexOf('/');
        return (s >= 0 ? id.substring(s + 1) : id).replace('_', ' ');
    }

    // ---------- 8. Khung ảnh ----------

    LinearGradient shade;
    float shadeH = -1;

    void drawPhoto(Canvas c, float w, float h) {
        float m = margin();
        if (photos == null || photos.size() == 0) {
            text(c, "Chưa có ảnh", w / 2f, h * 0.42f, 30 * dp, s600, fg(0.85f), Paint.Align.CENTER);
            text(c, "Mở ứng dụng StandBy › Khung ảnh để chọn ảnh", w / 2f, h * 0.42f + 40 * dp, 18 * dp, s400, fg(0.5f), Paint.Align.CENTER);
            text(c, time(cal), w / 2f, h - m, 24 * dp, s500, ac(0.9f), Paint.Align.CENTER);
            return;
        }
        long wall = System.currentTimeMillis();
        long slot = wall / PHOTO_MS;
        float p = (wall % PHOTO_MS) / (float) PHOTO_MS;
        int n = photos.size();
        int i = (int) (slot % n);
        Bitmap cur = photos.get(i);
        Bitmap next = photos.get(i + 1);
        float fade = (wall % PHOTO_MS) > PHOTO_MS - PHOTO_FADE ? ((wall % PHOTO_MS) - (PHOTO_MS - PHOTO_FADE)) / (float) PHOTO_FADE : 0f;
        if (cur != null) kenBurns(c, cur, w, h, p, i, 1f);
        if (fade > 0 && next != null) kenBurns(c, next, w, h, 0f, i + 1, fade);
        animating = true;

        if (shade == null || shadeH != h) {
            shade = new LinearGradient(0, h * 0.4f, 0, h, 0x00000000, 0xB0000000, Shader.TileMode.CLAMP);
            shadeH = h;
        }
        sp.setShader(shade);
        sp.setStyle(Paint.Style.FILL);
        c.drawRect(0, h * 0.4f, w, h, sp);
        sp.setShader(null);

        tp.setTypeface(clockFace());
        tp.setFontFeatureSettings("tnum");
        tp.setStyle(Paint.Style.FILL);
        float size = fit(tp, "00:00", w * 0.34f, h * 0.22f);
        tp.setTextSize(size);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setColor(night ? fg(1) : withA(0xFFFFFFFF, 0.96f));
        c.drawText(time(cal), m + 6 * dp, h - m, tp);
        float dh = digitHeight(tp);
        text(c, dateLine(false) + (prefs.h24() ? "" : " · " + ampm(cal)), m + 8 * dp, h - m - dh - 18 * dp, 20 * dp, s500,
                night ? fg(0.8f) : withA(0xFFFFFFFF, 0.85f), Paint.Align.LEFT);
    }

    final java.util.WeakHashMap<Bitmap, Bitmap> blurCache = new java.util.WeakHashMap<>();
    final android.graphics.Path frame = new android.graphics.Path();

    /** Bản thu rất nhỏ, phóng to lại bằng lọc song tuyến = nền mờ rẻ. */
    Bitmap blurOf(Bitmap b) {
        Bitmap s = blurCache.get(b);
        if (s == null) {
            int tw = 24, th = Math.max(1, Math.round(24f * b.getHeight() / b.getWidth()));
            Bitmap tiny = Bitmap.createScaledBitmap(b, tw, th, true);
            s = Bitmap.createScaledBitmap(tiny, tw * 4, th * 4, true);
            blurCache.put(b, s);
        }
        return s;
    }

    void setPhotoFilter() {
        if (night) {
            bp.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                    0.13f, 0.45f, 0.04f, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 1, 0})));
        } else {
            bp.setColorFilter(null);
        }
    }

    void cover(Canvas c, Bitmap b, float w, float h, float p, int idx, float a, float zoom) {
        float bw = b.getWidth(), bh = b.getHeight();
        float base = Math.max(w / bw, h / bh);
        float s = base * (1.04f + zoom * p);
        float ow = bw * s - w, oh = bh * s - h;
        float f = (idx % 2 == 0) ? p : 1f - p;
        boolean horiz = (idx / 2) % 2 == 0;
        mx.setScale(s, s);
        mx.postTranslate(horiz ? -ow * f : -ow * 0.5f, horiz ? -oh * 0.5f : -oh * f);
        bp.setAlpha(Math.round(255 * a * alpha));
        c.drawBitmap(b, mx, bp);
    }

    void kenBurns(Canvas c, Bitmap b, float w, float h, float p, int idx, float a) {
        setPhotoFilter();
        float ratio = b.getWidth() / (float) b.getHeight();
        if (ratio >= 1.2f) {
            cover(c, b, w, h, p, idx, a, 0.08f);
            return;
        }
        // Ảnh dọc / vuông: nền là chính ảnh làm mờ, ảnh đầy đủ đặt bên phải.
        cover(c, blurOf(b), w, h, p, idx, a, 0.04f);
        sp.setShader(null);
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(Color.argb(Math.round(110 * a), 0, 0, 0));
        c.drawRect(0, 0, w, h, sp);
        float pad = 26 * dp;
        float s = (h - 2 * pad) / b.getHeight() * (1f + 0.025f * p);
        float fw = b.getWidth() * s, fh = b.getHeight() * s;
        float cx = Math.min(w * 0.68f, w - pad - fw / 2f), cy = h / 2f;
        float l = cx - fw / 2f, t = cy - fh / 2f;
        frame.reset();
        rf.set(l, t, l + fw, t + fh);
        frame.addRoundRect(rf, 18 * dp, 18 * dp, android.graphics.Path.Direction.CW);
        c.save();
        c.clipPath(frame);
        mx.setScale(s, s);
        mx.postTranslate(l, t);
        bp.setAlpha(Math.round(255 * a * alpha));
        c.drawBitmap(b, mx, bp);
        c.restore();
    }

    // ---------- 9. Bằng chữ ----------

    static final String[] U = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín", "mười"};

    static String words(int n) {
        if (n <= 10) return U[n];
        if (n < 20) return "mười " + (n == 15 ? "lăm" : U[n - 10]);
        int t = n / 10, u = n % 10;
        String s = U[t] + " mươi";
        if (u == 0) return s;
        if (u == 1) return s + " mốt";
        if (u == 4) return s + " tư";
        if (u == 5) return s + " lăm";
        return s + " " + U[u];
    }

    static String cap(String s) { return s.substring(0, 1).toUpperCase() + s.substring(1); }

    static String period(int h24) {
        if (h24 < 4) return "đêm";
        if (h24 < 11) return "sáng";
        if (h24 < 13) return "trưa";
        if (h24 < 18) return "chiều";
        if (h24 < 22) return "tối";
        return "đêm";
    }

    void drawWords(Canvas c, float w, float h) {
        float m = margin();
        int H24 = cal.get(Calendar.HOUR_OF_DAY), M = cal.get(Calendar.MINUTE);
        int h12 = H24 % 12 == 0 ? 12 : H24 % 12;
        String l1 = cap(words(h12)) + " giờ";
        String l2 = M == 0 ? "đúng" : words(M) + " phút";
        String l3 = "Buổi " + period(H24) + " · " + WD[cal.get(Calendar.DAY_OF_WEEK)] + ", " + cal.get(Calendar.DAY_OF_MONTH)
                + "/" + (cal.get(Calendar.MONTH) + 1);
        float maxW = w - 2 * m - 40 * dp;
        Typeface serif = Fonts.serif();
        tp.setTypeface(serif);
        float s1 = fitText(tp, l1, maxW, h * 0.3f);
        tp.setTypeface(s300);
        float s2 = fitText(tp, l2, maxW, h * 0.17f);
        float x = m + 20 * dp;
        float y1 = h * 0.47f;
        text(c, l1, x, y1, s1, serif, ac(1), Paint.Align.LEFT);
        float y2 = y1 + s2 * 1.15f;
        text(c, l2, x, y2, s2, s300, fg(0.92f), Paint.Align.LEFT);
        text(c, l3, x, Math.min(h - m, y2 + 52 * dp), 19 * dp, s500, fg(0.5f), Paint.Align.LEFT);
    }

    // ---------- 10. Kim lớn ----------

    void drawBigAnalog(Canvas c, float w, float h) {
        float m = margin();
        float R = h / 2f - m * 0.8f;
        float cx = w / 2f, cy = h / 2f;
        sp.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 60; i++) {
            double an = Math.toRadians(i * 6);
            boolean hour = i % 5 == 0, quarter = i % 15 == 0;
            float rr = hour ? R * 0.9f : R * 0.95f;
            float px = cx + (float) Math.sin(an) * rr, py = cy - (float) Math.cos(an) * rr;
            if (quarter) {
                sp.setColor(ac(1));
                c.drawCircle(px, py, R * 0.034f, sp);
            } else if (hour) {
                sp.setColor(fg(0.85f));
                c.drawCircle(px, py, R * 0.022f, sp);
            } else {
                sp.setColor(fg(0.28f));
                c.drawCircle(px, py, R * 0.008f, sp);
            }
        }
        float sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f;
        float min = cal.get(Calendar.MINUTE) + sec / 60f;
        float hr = cal.get(Calendar.HOUR) + min / 60f;
        hand(c, cx, cy, hr * 30, R * 0.52f, R * 0.07f, R * 0.06f, fg(1));
        hand(c, cx, cy, min * 6, R * 0.8f, R * 0.07f, R * 0.038f, fg(1));
        hand(c, cx, cy, sec * 6, R * 0.88f, R * 0.2f, R * 0.012f, ac(1));
        animating = true;
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(ac(1));
        c.drawCircle(cx, cy, R * 0.032f, sp);
        sp.setColor(Color.BLACK);
        c.drawCircle(cx, cy, R * 0.013f, sp);

        float lx = m + 8 * dp;
        text(c, WD[cal.get(Calendar.DAY_OF_WEEK)], lx, h * 0.34f, 24 * dp, s600, fg(0.85f), Paint.Align.LEFT);
        tp.setTypeface(clockFace());
        float ds = fit(tp, "00", (cx - R - lx - 16 * dp), h * 0.22f);
        tp.setTextSize(ds);
        text(c, String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), lx - ds * 0.03f, h * 0.34f + 16 * dp + digitHeight(tp), ds, clockFace(), ac(1), Paint.Align.LEFT);
        text(c, "tháng " + (cal.get(Calendar.MONTH) + 1), lx, h * 0.34f + 50 * dp + digitHeight(tp), 19 * dp, s500, fg(0.6f), Paint.Align.LEFT);

        float rx = w - m - 8 * dp;
        int[] l = lunarToday();
        text(c, "Âm lịch", rx, h * 0.34f, 18 * dp, s500, fg(0.5f), Paint.Align.RIGHT);
        tp.setTypeface(clockFace());
        float ls = fit(tp, "00/00", (rx - cx - R - 16 * dp), h * 0.15f);
        tp.setTextSize(ls);
        float lh = digitHeight(tp);
        text(c, l[0] + "/" + l[1], rx, h * 0.34f + 16 * dp + lh, ls, clockFace(), fg(0.9f), Paint.Align.RIGHT);
        text(c, Lunar.yearName(l[2]), rx, h * 0.34f + 50 * dp + lh, 19 * dp, s500, fg(0.6f), Paint.Align.RIGHT);
        if (battery >= 0) text(c, "Pin " + battery + "%", rx, h - m, 17 * dp, s500, fg(0.5f), Paint.Align.RIGHT);
        if (!prefs.h24()) text(c, ampm(cal), lx, h - m, 17 * dp, s500, fg(0.5f), Paint.Align.LEFT);
    }

    // ---------- 11. Đèn LED ----------

    static final String[][] GLYPH = {
            {"01110", "10001", "10011", "10101", "11001", "10001", "01110"},
            {"00100", "01100", "00100", "00100", "00100", "00100", "01110"},
            {"01110", "10001", "00001", "00010", "00100", "01000", "11111"},
            {"11111", "00010", "00100", "00010", "00001", "10001", "01110"},
            {"00010", "00110", "01010", "10010", "11111", "00010", "00010"},
            {"11111", "10000", "11110", "00001", "00001", "10001", "01110"},
            {"00110", "01000", "10000", "11110", "10001", "10001", "01110"},
            {"11111", "00001", "00010", "00100", "01000", "01000", "01000"},
            {"01110", "10001", "10001", "01110", "10001", "10001", "01110"},
            {"01110", "10001", "10001", "01111", "00001", "00010", "01100"}};

    void drawLed(Canvas c, float w, float h) {
        float m = margin();
        int cols = 25;
        float pitch = Math.min((w - 2 * m - 20 * dp) / cols, (h * 0.52f) / 7);
        float rad = pitch * 0.36f;
        float gw = cols * pitch;
        float x0 = (w - gw) / 2f + pitch / 2f;
        float y0 = h * 0.42f - 3.5f * pitch + pitch / 2f;
        String H = hh(cal), M = mm(cal);
        if (H.length() == 1) H = " " + H;
        String digits = H + M;
        int[] startCol = {0, 6, 14, 20};
        int sec = cal.get(Calendar.SECOND);
        sp.setStyle(Paint.Style.FILL);
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < 7; row++) {
                boolean on = false;
                for (int d = 0; d < 4; d++) {
                    int cc = col - startCol[d];
                    if (cc >= 0 && cc < 5) {
                        char ch = digits.charAt(d);
                        on = ch != ' ' && GLYPH[ch - '0'][row].charAt(cc) == '1';
                    }
                }
                float px = x0 + col * pitch, py = y0 + row * pitch;
                if (col == 12 && (row == 2 || row == 4)) {
                    float k = sec % 2 == 0 ? 1f : 0.35f;
                    sp.setColor(ac(0.18f * k));
                    c.drawCircle(px, py, rad * 1.9f, sp);
                    sp.setColor(ac(k));
                    c.drawCircle(px, py, rad, sp);
                } else if (on) {
                    sp.setColor(ac(0.16f));
                    c.drawCircle(px, py, rad * 1.9f, sp);
                    sp.setColor(ac(1));
                    c.drawCircle(px, py, rad, sp);
                } else {
                    sp.setColor(fg(0.07f));
                    c.drawCircle(px, py, rad * 0.85f, sp);
                }
            }
        }
        float by = y0 + 7 * pitch + pitch * 0.55f;
        float p2 = gw / 60f;
        for (int i = 0; i < 60; i++) {
            sp.setColor(i <= sec ? ac(i == sec ? 1f : 0.55f) : fg(0.08f));
            c.drawCircle(x0 - pitch / 2f + p2 * (i + 0.5f), by, p2 * 0.28f, sp);
        }
        String bottom = dateLine(false) + (prefs.h24() ? "" : " · " + ampm(cal)) + (battery >= 0 ? " · Pin " + battery + "%" : "");
        text(c, bottom, w / 2f, Math.min(h - m + 6 * dp, by + 52 * dp), 20 * dp, s500, fg(0.6f), Paint.Align.CENTER);
    }

    // ---------- 12. Vòng tròn ----------

    void drawRings(Canvas c, float w, float h) {
        float m = margin();
        float R = h / 2f - m;
        float cx = m + R + w * 0.03f, cy = h / 2f;
        float sw = R * 0.13f, gap = sw * 1.32f;
        long ms = System.currentTimeMillis();
        float sec = cal.get(Calendar.SECOND) + (ms % 1000) / 1000f;
        float min = cal.get(Calendar.MINUTE) + sec / 60f;
        float hr = cal.get(Calendar.HOUR) + min / 60f;
        int base = night ? 0xFFFF3626 : ACCENTS[Math.floorMod(prefs.accent(), ACCENTS.length)];
        int[] colors = {base, Effects.mix(base, 0xFFFFFFFF, 0.45f), 0xFFF2EFEA};
        float[] frac = {hr / 12f, min / 60f, sec / 60f};
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setStrokeWidth(sw);
        for (int i = 0; i < 3; i++) {
            float rr = R - sw / 2f - gap * i;
            rf.set(cx - rr, cy - rr, cx + rr, cy + rr);
            sp.setColor(withA(colors[i], 0.12f));
            c.drawCircle(cx, cy, rr, sp);
            sp.setColor(withA(colors[i], i == 2 ? 0.8f : 1f));
            c.drawArc(rf, -90, Math.max(0.5f, 360f * frac[i]), false, sp);
        }
        animating = true;
        text(c, String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), cx, cy + 4 * dp, R * 0.3f, s600, fg(0.9f), Paint.Align.CENTER);
        String wd = WD_SHORT[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7];
        text(c, wd, cx, cy + R * 0.25f, R * 0.13f, s500, fg(0.5f), Paint.Align.CENTER);

        float x = cx + R + w * 0.08f;
        tp.setTypeface(clockFace());
        tp.setFontFeatureSettings("tnum");
        float size = fit(tp, "00:00", w - x - m, h * 0.3f);
        tp.setTextSize(size);
        float th = digitHeight(tp);
        float y = h * 0.46f;
        text(c, time(cal), x, y, size, clockFace(), ac(1), Paint.Align.LEFT);
        if (!prefs.h24()) {
            text(c, ampm(cal), w - m, y - th - 8 * dp, 20 * dp, s500, ac(0.8f), Paint.Align.RIGHT);
        }
        y += 42 * dp;
        text(c, dateLine(true), x, y, 21 * dp, s500, fg(0.85f), Paint.Align.LEFT);
        int dayMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        y += 32 * dp;
        text(c, "Đã qua " + Math.round(dayMin * 100f / 1440f) + "% của ngày hôm nay", x, y, 18 * dp, s400, fg(0.55f), Paint.Align.LEFT);
        y += 40 * dp;
        String[] names = {"Giờ", "Phút", "Giây"};
        float lx = x;
        sp.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) {
            sp.setColor(withA(colors[i], 1f));
            c.drawCircle(lx + 5 * dp, y - 5 * dp, 5 * dp, sp);
            float tw = text(c, names[i], lx + 16 * dp, y, 16 * dp, s400, fg(0.6f), Paint.Align.LEFT);
            lx += tw + 40 * dp;
        }
    }

    // ---------- 13. Cuộn số ----------

    final float[] rollPos = {-1, -1, -1, -1};
    long rollLast;

    void drawRoll(Canvas c, float w, float h) {
        float m = margin();
        int H24 = cal.get(Calendar.HOUR_OF_DAY);
        int Hd = prefs.h24() ? H24 : (H24 % 12 == 0 ? 12 : H24 % 12);
        int M = cal.get(Calendar.MINUTE);
        int[] target = {Hd / 10, Hd % 10, M / 10, M % 10};
        int[] max = {prefs.h24() ? 2 : 1, 9, 5, 9};
        long now = SystemClock.uptimeMillis();
        float dt = rollLast == 0 ? 0f : Math.min(0.1f, (now - rollLast) / 1000f);
        rollLast = now;
        float pitch = h * 0.2f;
        float colW = pitch * 0.95f, g1 = pitch * 0.12f, gMid = pitch * 0.7f;
        float total = 4 * colW + 2 * g1 + gMid;
        float x0 = (w - total) / 2f;
        float cy = h / 2f + 14 * dp;
        tp.setTypeface(clockFace());
        tp.setFontFeatureSettings("tnum");
        tp.setStyle(Paint.Style.FILL);
        tp.setTextAlign(Paint.Align.CENTER);
        float baseSize = fit(tp, "0", colW, pitch * 0.62f);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeWidth(1.2f * dp);
        sp.setColor(fg(0.14f));
        c.drawLine(x0 - 10 * dp, cy - pitch * 0.62f, x0 + total + 10 * dp, cy - pitch * 0.62f, sp);
        c.drawLine(x0 - 10 * dp, cy + pitch * 0.62f, x0 + total + 10 * dp, cy + pitch * 0.62f, sp);
        for (int i = 0; i < 4; i++) {
            if (rollPos[i] < 0) rollPos[i] = target[i];
            float diff = target[i] - rollPos[i];
            if (Math.abs(diff) < 0.002f) rollPos[i] = target[i];
            else {
                rollPos[i] += diff * (1f - (float) Math.exp(-dt * 6));
                animating = true;
            }
            float colX = x0 + i * colW + (i >= 1 ? g1 : 0) + (i >= 2 ? gMid : 0) + (i >= 3 ? g1 : 0) + colW / 2f;
            for (int d = 0; d <= max[i]; d++) {
                float dist = d - rollPos[i];
                float ad = Math.abs(dist);
                if (ad > 2.6f) continue;
                float al = Math.max(0f, 1f - ad * 0.42f);
                float k = 1f + 0.22f * Math.max(0f, 1f - ad);
                tp.setTextSize(baseSize * k);
                float dh = digitHeight(tp);
                tp.setColor(ad < 0.5f ? ac(al) : fg(al * 0.45f));
                c.drawText(String.valueOf(d), colX, cy + dist * pitch + dh / 2f, tp);
            }
        }
        float cx = x0 + 2 * colW + g1 + gMid / 2f;
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(ac(0.8f));
        c.drawCircle(cx, cy - pitch * 0.2f, 5 * dp, sp);
        c.drawCircle(cx, cy + pitch * 0.2f, 5 * dp, sp);
        text(c, dateLine(false), m, m + 20 * dp, 20 * dp, s500, fg(0.7f), Paint.Align.LEFT);
        if (!prefs.h24()) text(c, ampm(cal), w - m, m + 20 * dp, 20 * dp, s500, fg(0.7f), Paint.Align.RIGHT);
        else if (battery >= 0) drawBatteryBadge(c, w - m, m + 13 * dp);
    }

    // ---------- 14. Mặt trời & trăng ----------

    /** Giờ mọc/lặn (phút trong ngày, giờ địa phương) theo công thức NOAA rút gọn. */
    static double[] sunTimes(Calendar k) {
        int N = k.get(Calendar.DAY_OF_YEAR);
        double g = 2 * Math.PI / 365 * (N - 1);
        double eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
                + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double lat = Math.toRadians(LAT);
        double cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(lat) * Math.cos(decl)) - Math.tan(lat) * Math.tan(decl);
        double ha = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosHa))));
        double off = k.getTimeZone().getOffset(k.getTimeInMillis()) / 60000.0;
        return new double[]{720 - 4 * (LON + ha) - eq + off, 720 - 4 * (LON - ha) - eq + off};
    }

    static String hm(double minutes) {
        int t = (int) Math.round(minutes);
        t = Math.floorMod(t, 1440);
        return (t / 60) + ":" + two(t % 60);
    }

    static String moonName(int d) {
        if (d <= 1) return "non";
        if (d <= 6) return "lưỡi liềm";
        if (d <= 8) return "thượng huyền";
        if (d <= 13) return "gần tròn";
        if (d <= 16) return "tròn";
        if (d <= 21) return "khuyết dần";
        if (d <= 23) return "hạ huyền";
        return "lưỡi liềm cuối tháng";
    }

    final Path moonPath = new Path();

    void drawMoon(Canvas c, float cx, float cy, float r, int lunarDay) {
        float age = lunarDay - 0.5f;
        double th = 2 * Math.PI * age / 29.53;
        boolean waxing = age < 14.765f;
        float cos = (float) Math.cos(th);
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(fg(0.12f));
        c.drawCircle(cx, cy, r, sp);
        moonPath.reset();
        moonPath.addArc(cx - r, cy - r, cx + r, cy + r, -90, 180);
        float rxT = Math.abs(cos) * r;
        RectF e = new RectF(cx - rxT, cy - r, cx + rxT, cy + r);
        moonPath.arcTo(e, 90, cos > 0 ? -180 : 180);
        moonPath.close();
        c.save();
        if (!waxing) c.scale(-1, 1, cx, cy);
        sp.setColor(night ? fg(0.9f) : withA(0xFFF3EBD3, 0.95f));
        c.drawPath(moonPath, sp);
        c.restore();
    }

    final android.graphics.DashPathEffect dash = new android.graphics.DashPathEffect(new float[]{6, 9}, 0);

    void drawSun(Canvas c, float w, float h) {
        float m = margin();
        int[] l = lunarToday();
        double[] st = sunTimes(cal);
        double now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60.0;
        boolean day = now >= st[0] && now <= st[1];

        tp.setTypeface(clockFace());
        tp.setFontFeatureSettings("tnum");
        float size = fit(tp, "00:00", w * 0.28f, h * 0.2f);
        tp.setTextSize(size);
        float th = digitHeight(tp);
        text(c, time(cal), m + 4 * dp, m + th, size, clockFace(), ac(1), Paint.Align.LEFT);
        text(c, dateLine(false) + (prefs.h24() ? "" : " · " + ampm(cal)), m + 6 * dp, m + th + 34 * dp, 19 * dp, s500, fg(0.7f), Paint.Align.LEFT);
        text(c, "Hà Nội", w - m, m + 20 * dp, 18 * dp, s500, fg(0.45f), Paint.Align.RIGHT);

        float hy = h * 0.74f, ax0 = w * 0.3f, ax1 = w - m - w * 0.04f;
        float acx = (ax0 + ax1) / 2f, arx = (ax1 - ax0) / 2f, ary = h * 0.5f;
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeWidth(1.3f * dp);
        sp.setColor(fg(0.22f));
        c.drawLine(m, hy, w - m, hy, sp);
        rf.set(acx - arx, hy - ary, acx + arx, hy + ary);
        sp.setPathEffect(dash);
        sp.setColor(fg(0.28f));
        c.drawArc(rf, 180, 180, false, sp);
        sp.setPathEffect(null);

        double f;
        if (day) f = (now - st[0]) / (st[1] - st[0]);
        else {
            double nightLen = 1440 - (st[1] - st[0]);
            double el = now > st[1] ? now - st[1] : now + 1440 - st[1];
            f = el / nightLen;
        }
        f = Math.max(0, Math.min(1, f));
        sp.setStrokeWidth(2.2f * dp);
        sp.setStrokeCap(Paint.Cap.ROUND);
        sp.setColor(day ? ac(0.75f) : fg(0.45f));
        c.drawArc(rf, 180, (float) (180 * f), false, sp);
        double ang = Math.toRadians(180 + 180 * f);
        float px = acx + arx * (float) Math.cos(ang), py = hy + ary * (float) Math.sin(ang);
        if (day) {
            sp.setStyle(Paint.Style.FILL);
            sp.setColor(ac(0.1f));
            c.drawCircle(px, py, 34 * dp, sp);
            sp.setColor(ac(0.22f));
            c.drawCircle(px, py, 21 * dp, sp);
            sp.setColor(ac(1));
            c.drawCircle(px, py, 12 * dp, sp);
            sp.setStyle(Paint.Style.STROKE);
            sp.setStrokeWidth(2 * dp);
            float rot = (cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f) * 3f;
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(rot + i * 45);
                float s1 = 17 * dp, s2 = 25 * dp;
                c.drawLine(px + (float) Math.cos(a) * s1, py + (float) Math.sin(a) * s1,
                        px + (float) Math.cos(a) * s2, py + (float) Math.sin(a) * s2, sp);
            }
            animating = true;
        } else {
            sp.setStyle(Paint.Style.FILL);
            sp.setColor(fg(0.06f));
            c.drawCircle(px, py, 30 * dp, sp);
            drawMoon(c, px, py, 15 * dp, l[0]);
        }

        float y1 = hy + 30 * dp, y2 = hy + 58 * dp;
        text(c, "Mặt trời mọc", ax0, y1, 15 * dp, s400, fg(0.5f), Paint.Align.CENTER);
        text(c, hm(st[0]), ax0, y2, 21 * dp, s600, fg(0.85f), Paint.Align.CENTER);
        text(c, "Mặt trời lặn", ax1, y1, 15 * dp, s400, fg(0.5f), Paint.Align.CENTER);
        text(c, hm(st[1]), ax1, y2, 21 * dp, s600, fg(0.85f), Paint.Align.CENTER);
        float mx0 = m + 6 * dp, my = m + th + 84 * dp;
        drawMoon(c, mx0 + 14 * dp, my - 6 * dp, 14 * dp, l[0]);
        text(c, "Trăng " + moonName(l[0]), mx0 + 38 * dp, my - 12 * dp, 17 * dp, s500, fg(0.8f), Paint.Align.LEFT);
        text(c, "Ngày " + l[0] + " âm lịch", mx0 + 38 * dp, my + 14 * dp, 15 * dp, s400, fg(0.5f), Paint.Align.LEFT);
        int len = (int) Math.round(st[1] - st[0]);
        text(c, "Ngày dài " + (len / 60) + " giờ " + (len % 60) + " phút", acx, y2, 15 * dp, s400, fg(0.45f), Paint.Align.CENTER);
    }

    // ---------- 15. Dòng thời gian ----------

    void drawTimeline(Canvas c, float w, float h) {
        float m = margin();
        tp.setTypeface(clockFace());
        tp.setFontFeatureSettings("tnum");
        float size = fit(tp, "00:00", w * 0.42f, h * 0.34f);
        tp.setTextSize(size);
        float th = digitHeight(tp);
        float base = m + th + 12 * dp;
        text(c, time(cal), m + 4 * dp, base, size, clockFace(), ac(1), Paint.Align.LEFT);
        int[] l = lunarToday();
        float rx = w - m;
        text(c, dateLine(true), rx, m + 34 * dp, 21 * dp, s500, fg(0.85f), Paint.Align.RIGHT);
        text(c, "Âm lịch " + l[0] + "/" + l[1] + " · năm " + Lunar.yearName(l[2]), rx, m + 66 * dp, 17 * dp, s400, fg(0.55f), Paint.Align.RIGHT);
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int left = 1440 - nowMin;
        text(c, "Còn " + (left / 60) + " giờ " + (left % 60) + " phút đến nửa đêm", rx, m + 98 * dp, 17 * dp, s500, ac(0.85f), Paint.Align.RIGHT);
        if (!prefs.h24()) text(c, ampm(cal), rx, base, 20 * dp, s500, ac(0.8f), Paint.Align.RIGHT);

        float x0 = m + 12 * dp, x1 = w - m - 12 * dp, y = h * 0.74f;
        float span = x1 - x0;
        double[] st = sunTimes(cal);
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(fg(0.06f));
        rf.set(x0, y - 22 * dp, x0 + span * (float) (st[0] / 1440), y);
        c.drawRect(rf, sp);
        rf.set(x0 + span * (float) (st[1] / 1440), y - 22 * dp, x1, y);
        c.drawRect(rf, sp);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeCap(Paint.Cap.ROUND);
        for (int hr = 0; hr <= 24; hr++) {
            float x = x0 + span * hr / 24f;
            boolean major = hr % 6 == 0;
            sp.setStrokeWidth((major ? 2f : 1.2f) * dp);
            sp.setColor(fg(major ? 0.55f : 0.25f));
            c.drawLine(x, y, x, y + (major ? 16 : 9) * dp, sp);
            if (hr % 3 == 0) {
                text(c, String.valueOf(hr), x, y + 38 * dp, (major ? 15 : 13) * dp, major ? s500 : s400, fg(major ? 0.6f : 0.35f), Paint.Align.CENTER);
            }
        }
        sp.setStrokeWidth(2 * dp);
        sp.setColor(fg(0.2f));
        c.drawLine(x0, y, x1, y, sp);
        float xn = x0 + span * (nowMin + cal.get(Calendar.SECOND) / 60f) / 1440f;
        sp.setStrokeWidth(3.2f * dp);
        sp.setColor(ac(1));
        c.drawLine(x0, y, xn, y, sp);
        if (alarmAt > 0) {
            Calendar k = Calendar.getInstance();
            k.setTimeInMillis(alarmAt);
            float xa = x0 + span * (k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE)) / 1440f;
            sp.setStyle(Paint.Style.FILL);
            sp.setColor(ac(0.9f));
            moonPath.reset();
            moonPath.moveTo(xa, y - 6 * dp);
            moonPath.lineTo(xa - 7 * dp, y - 18 * dp);
            moonPath.lineTo(xa + 7 * dp, y - 18 * dp);
            moonPath.close();
            c.drawPath(moonPath, sp);
            sp.setStrokeWidth(1.2f * dp);
            sp.setStyle(Paint.Style.STROKE);
            c.drawLine(xa, y - 18 * dp, xa, y - 46 * dp, sp);
            text(c, "Báo thức " + time(k), xa, y - 54 * dp, 14 * dp, s500, ac(0.9f), Paint.Align.CENTER);
        }
        text(c, "Mọc " + hm(st[0]), x0 + span * (float) (st[0] / 1440) + 6 * dp, y - 30 * dp, 13 * dp, s400, fg(0.45f), Paint.Align.LEFT);
        text(c, "Lặn " + hm(st[1]), x0 + span * (float) (st[1] / 1440) - 6 * dp, y - 30 * dp, 13 * dp, s400, fg(0.45f), Paint.Align.RIGHT);
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(ac(0.22f));
        c.drawCircle(xn, y, 13 * dp, sp);
        sp.setColor(ac(1));
        c.drawCircle(xn, y, 7 * dp, sp);
    }
}
