package com.thehieu.standby;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;

import java.util.Random;

/**
 * Màn StandBy: lật trang giữa các mặt đồng hồ, cử chỉ, và lớp chống lưu ảnh
 * (cả nội dung trôi chậm mỗi phút, làm mờ, tắt hẳn).
 */
public class StandbyView extends View {
    public interface Host {
        void onUnlockRequest();
        void onUserActivity();
    }

    static final long SHIFT_MS = 7000;

    final float dp;
    final Prefs prefs;
    final Host host;
    final Faces faces;
    final Effects fx;
    long lastTapT;
    final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF();
    final Random rnd = new Random();
    final int slop, flingV;

    int[] pages;
    int page;
    float dragX;
    ValueAnimator snap;

    float offX, offY, fromX, fromY, toX, toY;
    long shiftT0 = -1;
    float alphaTarget = 1f;
    boolean blackout;

    long chromeUntil;
    String toast;
    long toastUntil;

    public StandbyView(Context c, Prefs prefs, Host host) {
        super(c);
        this.prefs = prefs;
        this.host = host;
        dp = getResources().getDisplayMetrics().density;
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        flingV = vc.getScaledMinimumFlingVelocity() * 6;
        faces = new Faces(c, prefs);
        fx = new Effects(dp);
        pages = prefs.pages();
        for (int f : pages) {
            if (f == Faces.PHOTO) faces.photos = new Photos(c, this::invalidate);
        }
        int want = prefs.pageFace();
        page = 0;
        for (int i = 0; i < pages.length; i++) if (pages[i] == want) page = i;
        setKeepScreenOn(true);
        setBackgroundColor(Color.BLACK);
    }

    /** Mở thẳng một mặt (xem thử từ cài đặt); mặt đang tắt thì tạm thêm vào. */
    void focusFace(int face) {
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] == face) {
                page = i;
                return;
            }
        }
        int[] np = new int[pages.length + 1];
        np[0] = face;
        System.arraycopy(pages, 0, np, 1, pages.length);
        pages = np;
        page = 0;
        if (face == Faces.PHOTO && faces.photos == null) faces.photos = new Photos(getContext(), this::invalidate);
    }

    // ---------- trạng thái từ Activity ----------

    void setBattery(int level, boolean charging, boolean full) {
        faces.battery = level;
        faces.charging = charging;
        faces.full = full;
    }

    void setInfo(String alarm, long alarmAt, long chargeLeft) {
        faces.alarm = alarm;
        faces.alarmAt = alarmAt;
        faces.chargeLeft = chargeLeft;
    }

    boolean night() { return faces.night; }

    void setNight(boolean n) {
        if (faces.night != n) {
            faces.night = n;
            invalidate();
        }
    }

    void setDim(boolean d) {
        float t = d ? 0.45f : 1f;
        if (alphaTarget != t) {
            alphaTarget = t;
            invalidate();
        }
    }

    void setBlackout(boolean b) {
        if (blackout != b) {
            blackout = b;
            invalidate();
        }
    }

    void shiftPixels() {
        if (!prefs.shift()) {
            toX = toY = 0;
        } else {
            float amp = 16 * dp;
            float nx, ny;
            int tries = 0;
            do {
                double a = rnd.nextDouble() * Math.PI * 2;
                double r = amp * (0.35 + 0.65 * Math.sqrt(rnd.nextDouble()));
                nx = (float) (r * Math.cos(a));
                ny = (float) (r * Math.sin(a) * 0.75);
            } while (Math.hypot(nx - toX, ny - toY) < amp * 0.5 && ++tries < 12);
            toX = nx;
            toY = ny;
        }
        fromX = offX;
        fromY = offY;
        shiftT0 = SystemClock.uptimeMillis();
        invalidate();
    }

    void nextPage() {
        if (pages.length > 1) animateTo(1, 900);
    }

    void showChrome(String msg) {
        long now = SystemClock.uptimeMillis();
        chromeUntil = now + 3200;
        if (msg != null) {
            toast = msg;
            toastUntil = now + 2200;
        }
        invalidate();
    }

    // ---------- vẽ ----------

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        boolean anim = false;
        if (shiftT0 >= 0) {
            float t = (now - shiftT0) / (float) SHIFT_MS;
            if (t >= 1) {
                t = 1;
                shiftT0 = -1;
            } else {
                anim = true;
            }
            float e = (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
            offX = fromX + (toX - fromX) * e;
            offY = fromY + (toY - fromY) * e;
        }
        if (Math.abs(faces.alpha - alphaTarget) > 0.004f) {
            faces.alpha += (alphaTarget - faces.alpha) * 0.06f;
            anim = true;
        } else {
            faces.alpha = alphaTarget;
        }

        c.drawColor(Color.BLACK);
        if (blackout) return;

        int w = getWidth(), h = getHeight();
        int accent = Faces.ACCENTS[Math.floorMod(prefs.accent(), Faces.ACCENTS.length)];
        if (fx.draw(c, prefs.effect(), w, h, faces.alpha * 0.9f, faces.night, accent,
                Effects.DENSITY[Math.floorMod(prefs.fxDensity(), Effects.DENSITY.length)])) anim = true;
        faces.animating = false;
        c.save();
        c.translate(offX, offY);
        drawPageAt(c, page, dragX, w, h);
        if (pages.length > 1) {
            if (dragX < 0) drawPageAt(c, Math.floorMod(page + 1, pages.length), dragX + w, w, h);
            else if (dragX > 0) drawPageAt(c, Math.floorMod(page - 1, pages.length), dragX - w, w, h);
        }
        c.restore();
        if (faces.animating) anim = true;
        if (drawChrome(c, now)) anim = true;
        if (anim) postInvalidateOnAnimation();
    }

    void drawPageAt(Canvas c, int p, float x, int w, int h) {
        c.save();
        c.translate(x, 0);
        if (x != 0) c.clipRect(0, 0, w, h);
        faces.draw(c, pages[p], w, h);
        c.restore();
    }

    /** Gợi ý + chấm trang trong một viên nền tối, chỉ hiện vài giây sau khi chạm. */
    boolean drawChrome(Canvas c, long now) {
        long until = Math.max(chromeUntil, toast != null ? toastUntil : 0);
        if (now >= until) return false;
        int w = getWidth(), h = getHeight();
        float a = Math.min(1f, (until - now) / 450f);
        String msg = toast != null && now < toastUntil ? toast : "Vuốt lên để mở khóa";
        tp.setTypeface(faces.s500);
        tp.setTextSize(15 * dp);
        tp.setTextAlign(Paint.Align.CENTER);
        float tw = tp.measureText(msg);
        float dotsW = pages.length > 1 ? (pages.length - 1) * 12 * dp : 0;
        float pw = Math.max(tw, dotsW) + 36 * dp;
        float ph = pages.length > 1 ? 54 * dp : 38 * dp;
        float cy = h - 18 * dp - ph / 2f;
        rf.set(w / 2f - pw / 2f, cy - ph / 2f, w / 2f + pw / 2f, cy + ph / 2f);
        sp.setStyle(Paint.Style.FILL);
        sp.setColor(Color.argb(Math.round(225 * a), 28, 28, 30));
        c.drawRoundRect(rf, ph / 2f, ph / 2f, sp);
        tp.setColor(faces.fg(0.85f * a));
        float ty = pages.length > 1 ? cy - 2 * dp : cy + 5 * dp;
        c.drawText(msg, w / 2f, ty, tp);
        if (pages.length > 1) {
            float x0 = w / 2f - dotsW / 2f, y = cy + 15 * dp;
            for (int i = 0; i < pages.length; i++) {
                sp.setColor(faces.fg(a * (i == page ? 0.9f : 0.3f)));
                c.drawCircle(x0 + i * 12 * dp, y, 3 * dp, sp);
            }
        }
        return true;
    }

    // ---------- cử chỉ ----------

    float downX, downY;
    long downT;
    int mode; // 0 chưa rõ, 1 ngang, 2 dọc, 3 bỏ qua
    VelocityTracker vt;

    final Runnable longPress = this::onLongPress;

    void onLongPress() {
        if (mode != 0) return;
        mode = 3;
        int a = Math.floorMod(prefs.accent() + 1, Faces.ACCENTS.length);
        prefs.put("accent", a);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        showChrome("Màu: " + Faces.ACCENT_NAMES[a]);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                boolean asleep = blackout || faces.alpha < 0.9f;
                host.onUserActivity();
                if (snap != null && snap.isRunning()) snap.end();
                downX = e.getX();
                downY = e.getY();
                downT = SystemClock.uptimeMillis();
                mode = asleep ? 3 : 0;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                if (mode == 0) postDelayed(longPress, 550);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(e);
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (mode == 0) {
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                        mode = 1;
                        removeCallbacks(longPress);
                    } else if (Math.abs(dy) > slop) {
                        mode = 2;
                        removeCallbacks(longPress);
                    }
                }
                if (mode == 1) {
                    dragX = pages.length > 1 ? dx : dx * 0.15f;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(longPress);
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                float vx = 0, vy = 0;
                if (vt != null) {
                    vt.addMovement(e);
                    vt.computeCurrentVelocity(1000);
                    vx = vt.getXVelocity();
                    vy = vt.getYVelocity();
                    vt.recycle();
                    vt = null;
                }
                float dy = e.getY() - downY;
                if (mode == 1) {
                    int dir = 0;
                    if (pages.length > 1 && up && (dragX < -getWidth() * 0.22f || vx < -flingV)) dir = 1;
                    else if (pages.length > 1 && up && (dragX > getWidth() * 0.22f || vx > flingV)) dir = -1;
                    animateTo(dir, 300);
                } else if (mode == 2 && up) {
                    if (dy < -getHeight() * 0.15f || vy < -flingV) host.onUnlockRequest();
                    else if (dy > getHeight() * 0.15f || vy > flingV) {
                        int f = Math.floorMod(prefs.font() + 1, Fonts.CLOCK_FILES.length);
                        prefs.put("font", f);
                        showChrome("Kiểu số: " + Fonts.CLOCK_NAMES[f]);
                    }
                } else if (mode == 0 && up && SystemClock.uptimeMillis() - downT < 450) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTapT < 350) {
                        lastTapT = 0;
                        int ef = Math.floorMod(prefs.effect() + 1, Effects.COUNT);
                        prefs.put("effect", ef);
                        showChrome("Hiệu ứng: " + Effects.NAMES[ef]);
                    } else {
                        lastTapT = now;
                        showChrome(null);
                    }
                }
                mode = 3;
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    void animateTo(int dir, long dur) {
        if (snap != null && snap.isRunning()) snap.end();
        final int w = getWidth();
        float target = dir == 1 ? -w : dir == -1 ? w : 0;
        snap = ValueAnimator.ofFloat(dragX, target);
        snap.setDuration(dur);
        snap.setInterpolator(dur > 500 ? new PathInterpolator(0.45f, 0f, 0.2f, 1f) : new DecelerateInterpolator(1.6f));
        snap.addUpdateListener(a -> {
            dragX = (float) a.getAnimatedValue();
            invalidate();
        });
        snap.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (dir != 0) {
                    page = Math.floorMod(page + dir, pages.length);
                    prefs.put("pageFace", pages[page]);
                }
                dragX = 0;
                invalidate();
            }
        });
        snap.start();
        if (dir != 0) chromeUntil = Math.max(chromeUntil, SystemClock.uptimeMillis() + 1800);
    }
}
