package com.thehieu.standby;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;

import java.util.Random;

/** Hiệu ứng nền chuyển động, vẽ phía sau mặt đồng hồ. Mọi thứ luôn di chuyển nên không gây lưu ảnh. */
final class Effects {
    static final int NONE = 0, SNOW = 1, RAIN = 2, FIREFLY = 3, STARS = 4, BLOSSOM = 5, BUBBLES = 6, AURORA = 7, FIREWORKS = 8;
    static final int COUNT = 9;
    static final String[] NAMES = {"Không", "Tuyết rơi", "Mưa", "Đom đóm", "Trời sao", "Hoa mai", "Bong bóng", "Cực quang", "Pháo hoa"};
    static final float[] DENSITY = {0.55f, 1f, 1.7f};
    static final String[] DENSITY_NAMES = {"Ít", "Vừa", "Nhiều"};
    static final int MAX = 520;
    static final int[] FIRE_COLORS = {0xFFFF6B6B, 0xFFFFD166, 0xFF6BCBFF, 0xFFB388FF, 0xFF7CFFB2, 0xFFFF9F5A, 0xFFFFFFFF};
    static final int[] AURORA_COLORS = {0xFF3DFFB0, 0xFF2FD3FF, 0xFFA77BFF};

    final float dp;
    final Random r = new Random();
    final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();

    int type = -1;
    float W, H, dens;
    long last;
    float t;

    // hạt dùng chung
    final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX];
    final float[] s = new float[MAX], p = new float[MAX], a = new float[MAX], rot = new float[MAX], life = new float[MAX];
    final int[] col = new int[MAX];
    int n;

    // gợn nước của mưa
    final float[] rx = new float[48], ry = new float[48], rt = new float[48];
    int rn;

    // pháo hoa
    final float[] kx = new float[4], ky = new float[4], kvy = new float[4], ktarget = new float[4];
    final int[] kcol = new int[4];
    final boolean[] kalive = new boolean[4];
    float nextEvent;

    // sao băng
    float shX, shY, shVx, shVy, shLife = -1;

    LinearGradient[] auroraShaders;

    // màu hiện hành
    float alpha;
    boolean night;
    int accent;

    Effects(float dp) { this.dp = dp; }

    float rnd(float lo, float hi) { return lo + r.nextFloat() * (hi - lo); }

    int tint(int rgb, float k) {
        float aa = k * alpha * (night ? 0.55f : 1f);
        int A = Math.round(255 * Math.max(0f, Math.min(1f, aa)));
        int base = night ? 0xE0281C : (rgb & 0x00FFFFFF);
        return (A << 24) | base;
    }

    /** Vẽ một khung hình; trả true nếu cần khung tiếp theo. */
    boolean draw(Canvas c, int type, float w, float h, float alpha, boolean night, int accent, float density) {
        if (type <= NONE || type >= COUNT) {
            this.type = type;
            return false;
        }
        this.alpha = alpha;
        this.night = night;
        this.accent = accent;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        t += dt;
        if (type != this.type || w != W || h != H || density != dens) init(type, w, h, density);
        switch (type) {
            case SNOW: snow(c, dt); break;
            case RAIN: rain(c, dt); break;
            case FIREFLY: fireflies(c, dt); break;
            case STARS: stars(c, dt); break;
            case BLOSSOM: blossom(c, dt); break;
            case BUBBLES: bubbles(c, dt); break;
            case AURORA: aurora(c); break;
            case FIREWORKS: fireworks(c, dt); break;
        }
        return true;
    }

    void init(int type, float w, float h, float d) {
        this.type = type;
        W = w;
        H = h;
        dens = d;
        n = 0;
        rn = 0;
        shLife = -1;
        for (int i = 0; i < kalive.length; i++) kalive[i] = false;
        nextEvent = 0.6f;
        switch (type) {
            case SNOW:
                n = Math.min(MAX, Math.round(120 * d));
                for (int i = 0; i < n; i++) seedSnow(i, true);
                break;
            case RAIN:
                n = Math.min(MAX, Math.round(85 * d));
                for (int i = 0; i < n; i++) seedRain(i, true);
                break;
            case FIREFLY:
                n = Math.min(MAX, Math.round(24 * d));
                for (int i = 0; i < n; i++) {
                    x[i] = rnd(0, W);
                    y[i] = rnd(0, H);
                    vx[i] = rnd(-12, 12) * dp;
                    vy[i] = rnd(-12, 12) * dp;
                    s[i] = rnd(1.6f, 3f) * dp;
                    p[i] = rnd(0, 100);
                    a[i] = rnd(0.5f, 1.4f);
                }
                break;
            case STARS:
                n = Math.min(MAX, Math.round(160 * d));
                for (int i = 0; i < n; i++) {
                    x[i] = rnd(0, W);
                    y[i] = rnd(0, H);
                    float k = r.nextFloat();
                    s[i] = (0.5f + k * k * 1.3f) * dp;
                    p[i] = rnd(0, 6.3f);
                    a[i] = rnd(0.5f, 2.6f);
                    life[i] = rnd(0.35f, 1f);
                    float cpick = r.nextFloat();
                    col[i] = cpick < 0.08f ? 0xFFAFC8FF : cpick < 0.14f ? 0xFFFFE2B0 : 0xFFFFFFFF;
                }
                nextEvent = rnd(3, 8);
                break;
            case BLOSSOM:
                n = Math.min(MAX, Math.round(26 * d));
                for (int i = 0; i < n; i++) seedPetal(i, true);
                break;
            case BUBBLES:
                n = Math.min(MAX, Math.round(18 * d));
                for (int i = 0; i < n; i++) seedBubble(i, true);
                break;
            case AURORA:
                auroraShaders = new LinearGradient[AURORA_COLORS.length];
                break;
            case FIREWORKS:
                n = 0;
                nextEvent = 0.3f;
                break;
        }
    }

    // ---------- tuyết ----------

    void seedSnow(int i, boolean anywhere) {
        float k = r.nextFloat();
        s[i] = 1f + k * k * 3.4f;
        x[i] = rnd(0, W);
        y[i] = anywhere ? rnd(-10 * dp, H) : rnd(-40 * dp, -6 * dp);
        vy[i] = (12 + s[i] * 11) * dp;
        p[i] = rnd(0, 6.3f);
        a[i] = rnd(5, 18) * dp;
        rot[i] = rnd(0, 360);
        vx[i] = rnd(-30, 30);
        life[i] = s[i] > 3.2f && r.nextFloat() < 0.5f ? 1 : 0;
    }

    void snow(Canvas c, float dt) {
        float wind = (float) Math.sin(t * 0.07) * 10 * dp;
        pt.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            y[i] += vy[i] * dt;
            x[i] += wind * dt * (s[i] / 3f);
            if (y[i] > H + 12 * dp) seedSnow(i, false);
            if (x[i] < -20 * dp) x[i] += W + 40 * dp;
            if (x[i] > W + 20 * dp) x[i] -= W + 40 * dp;
            float xx = x[i] + (float) Math.sin(t * 0.8f + p[i]) * a[i];
            float al = 0.28f + 0.6f * (s[i] / 4.4f);
            if (life[i] == 1) {
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(1.1f * dp);
                pt.setStrokeCap(Paint.Cap.ROUND);
                pt.setColor(tint(0xFFFFFFFF, al));
                float R = s[i] * 2.1f * dp;
                c.save();
                c.rotate(rot[i] + t * vx[i], xx, y[i]);
                for (int k = 0; k < 3; k++) {
                    c.drawLine(xx - R, y[i], xx + R, y[i], pt);
                    c.drawLine(xx + R * 0.55f, y[i], xx + R * 0.8f, y[i] - R * 0.25f, pt);
                    c.drawLine(xx - R * 0.55f, y[i], xx - R * 0.8f, y[i] + R * 0.25f, pt);
                    c.rotate(60, xx, y[i]);
                }
                c.restore();
                pt.setStyle(Paint.Style.FILL);
            } else {
                pt.setColor(tint(0xFFFFFFFF, al));
                c.drawCircle(xx, y[i], s[i] * dp, pt);
            }
        }
    }

    // ---------- mưa ----------

    void seedRain(int i, boolean anywhere) {
        s[i] = rnd(14, 34) * dp;
        vy[i] = rnd(650, 950) * dp;
        vx[i] = -0.12f * vy[i];
        x[i] = rnd(0, W * 1.15f);
        y[i] = anywhere ? rnd(-H * 0.3f, H) : rnd(-H * 0.35f, -s[i]);
        a[i] = rnd(0.12f, 0.32f);
    }

    void rain(Canvas c, float dt) {
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeCap(Paint.Cap.ROUND);
        pt.setStrokeWidth(1.1f * dp);
        for (int i = 0; i < n; i++) {
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            if (y[i] > H) {
                if (rn < rx.length && r.nextFloat() < 0.5f) {
                    rx[rn] = x[i];
                    ry[rn] = H - rnd(2, 40) * dp;
                    rt[rn] = 0;
                    rn++;
                }
                seedRain(i, false);
            }
            float k = s[i] / vy[i];
            pt.setColor(tint(0xFFCFE3FF, a[i]));
            c.drawLine(x[i], y[i], x[i] - vx[i] * k, y[i] - s[i], pt);
        }
        pt.setStrokeWidth(0.9f * dp);
        for (int i = 0; i < rn; i++) {
            rt[i] += dt;
            float q = rt[i] / 0.55f;
            if (q >= 1) {
                rn--;
                rx[i] = rx[rn];
                ry[i] = ry[rn];
                rt[i] = rt[rn];
                i--;
                continue;
            }
            float R = (2 + 16 * q) * dp;
            pt.setColor(tint(0xFFCFE3FF, 0.3f * (1 - q)));
            c.drawOval(rx[i] - R, ry[i] - R * 0.28f, rx[i] + R, ry[i] + R * 0.28f, pt);
        }
    }

    // ---------- đom đóm ----------

    void fireflies(Canvas c, float dt) {
        pt.setStyle(Paint.Style.FILL);
        float maxV = 26 * dp;
        for (int i = 0; i < n; i++) {
            vx[i] += rnd(-1, 1) * 40 * dp * dt;
            vy[i] += rnd(-1, 1) * 40 * dp * dt;
            float sp = (float) Math.hypot(vx[i], vy[i]);
            if (sp > maxV) {
                vx[i] *= maxV / sp;
                vy[i] *= maxV / sp;
            }
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            if (x[i] < -20 * dp) x[i] = W + 10 * dp;
            if (x[i] > W + 20 * dp) x[i] = -10 * dp;
            if (y[i] < -20 * dp) y[i] = H + 10 * dp;
            if (y[i] > H + 20 * dp) y[i] = -10 * dp;
            float g = (float) Math.sin(t * a[i] * 1.6f + p[i]);
            g = g < 0 ? 0 : g * g;
            if (g < 0.01f) continue;
            pt.setColor(tint(0xFFE4FF8A, 0.07f * g));
            c.drawCircle(x[i], y[i], s[i] * 6, pt);
            pt.setColor(tint(0xFFE4FF8A, 0.16f * g));
            c.drawCircle(x[i], y[i], s[i] * 2.6f, pt);
            pt.setColor(tint(0xFFF6FFC8, 0.95f * g));
            c.drawCircle(x[i], y[i], s[i], pt);
        }
    }

    // ---------- trời sao ----------

    void stars(Canvas c, float dt) {
        pt.setStyle(Paint.Style.FILL);
        float drift = 3 * dp / 60f; // ~3dp mỗi phút, sao trôi chậm
        for (int i = 0; i < n; i++) {
            x[i] -= drift * dt;
            if (x[i] < -2 * dp) x[i] += W + 4 * dp;
            float tw = 0.55f + 0.45f * (float) Math.sin(t * a[i] + p[i]);
            pt.setColor(tint(col[i], life[i] * tw));
            c.drawCircle(x[i], y[i], s[i], pt);
        }
        nextEvent -= dt;
        if (shLife < 0 && nextEvent <= 0) {
            shX = rnd(W * 0.3f, W * 1.05f);
            shY = rnd(-10 * dp, H * 0.35f);
            float ang = (float) Math.toRadians(rnd(150, 165));
            float v = rnd(700, 1000) * dp;
            shVx = (float) Math.cos(ang) * v;
            shVy = (float) Math.sin(ang) * v;
            shLife = 0;
            nextEvent = rnd(5, 14);
        }
        if (shLife >= 0) {
            shLife += dt;
            shX += shVx * dt;
            shY += shVy * dt;
            float q = shLife / 0.9f;
            if (q >= 1) {
                shLife = -1;
            } else {
                float fade = q < 0.2f ? q / 0.2f : 1f - (q - 0.2f) / 0.8f;
                float len = 130 * dp;
                float sp = (float) Math.hypot(shVx, shVy);
                float tx = shX - shVx / sp * len, ty = shY - shVy / sp * len;
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(1.6f * dp);
                pt.setStrokeCap(Paint.Cap.ROUND);
                pt.setShader(new LinearGradient(shX, shY, tx, ty, tint(0xFFFFFFFF, fade), tint(0xFFFFFFFF, 0), Shader.TileMode.CLAMP));
                c.drawLine(shX, shY, tx, ty, pt);
                pt.setShader(null);
                pt.setStyle(Paint.Style.FILL);
            }
        }
    }

    // ---------- hoa mai ----------

    void seedPetal(int i, boolean anywhere) {
        s[i] = rnd(5, 9) * dp;
        x[i] = rnd(-W * 0.1f, W);
        y[i] = anywhere ? rnd(-20 * dp, H) : rnd(-60 * dp, -12 * dp);
        vy[i] = rnd(22, 48) * dp;
        vx[i] = rnd(8, 26) * dp;
        rot[i] = rnd(0, 360);
        a[i] = rnd(-70, 70);
        p[i] = rnd(0, 6.3f);
        life[i] = r.nextFloat() < 0.14f ? 1 : 0;
        col[i] = r.nextFloat() < 0.75f ? 0xFFFFCB3D : 0xFFFFB020;
    }

    void blossom(Canvas c, float dt) {
        pt.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            y[i] += vy[i] * dt;
            x[i] += (vx[i] + (float) Math.sin(t * 0.9f + p[i]) * 18 * dp) * dt;
            rot[i] += a[i] * dt;
            if (y[i] > H + 20 * dp || x[i] > W + 30 * dp) seedPetal(i, false);
            float flip = (float) Math.cos(t * 1.7f + p[i]);
            c.save();
            c.translate(x[i], y[i]);
            c.rotate(rot[i]);
            if (life[i] == 1) {
                c.scale(0.6f + 0.4f * Math.abs(flip), 1f);
                for (int k = 0; k < 5; k++) {
                    pt.setColor(tint(col[i], 0.85f));
                    c.drawOval(-s[i] * 0.42f, -s[i] * 1.25f, s[i] * 0.42f, -s[i] * 0.15f, pt);
                    c.rotate(72);
                }
                pt.setColor(tint(0xFFE0701C, 0.95f));
                c.drawCircle(0, 0, s[i] * 0.28f, pt);
            } else {
                c.scale(Math.max(0.15f, Math.abs(flip)), 1f);
                pt.setColor(tint(col[i], 0.8f));
                c.drawOval(-s[i] * 0.55f, -s[i], s[i] * 0.55f, s[i], pt);
                pt.setColor(tint(0xFFE0901C, 0.35f));
                c.drawOval(-s[i] * 0.12f, -s[i] * 0.7f, s[i] * 0.12f, s[i] * 0.2f, pt);
            }
            c.restore();
        }
    }

    // ---------- bong bóng ----------

    void seedBubble(int i, boolean anywhere) {
        s[i] = rnd(6, 30) * dp;
        x[i] = rnd(0, W);
        y[i] = anywhere ? rnd(0, H + s[i]) : H + s[i] + rnd(0, 60) * dp;
        vy[i] = -rnd(10, 26) * dp;
        p[i] = rnd(0, 6.3f);
        a[i] = rnd(6, 18) * dp;
        float pick = r.nextFloat();
        col[i] = pick < 0.4f ? accent : pick < 0.6f ? 0xFF8CD3FF : pick < 0.8f ? 0xFFFFB3D1 : 0xFFB6F5D8;
    }

    void bubbles(Canvas c, float dt) {
        for (int i = 0; i < n; i++) {
            y[i] += vy[i] * dt;
            if (y[i] < -s[i] - 10 * dp) seedBubble(i, false);
            float xx = x[i] + (float) Math.sin(t * 0.6f + p[i]) * a[i];
            float R = s[i];
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(tint(col[i], 0.06f));
            c.drawCircle(xx, y[i], R, pt);
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(1.1f * dp);
            pt.setColor(tint(col[i], 0.28f));
            c.drawCircle(xx, y[i], R, pt);
            pt.setStrokeWidth(1.6f * dp);
            pt.setStrokeCap(Paint.Cap.ROUND);
            pt.setColor(tint(0xFFFFFFFF, 0.3f));
            c.drawArc(xx - R * 0.7f, y[i] - R * 0.7f, xx + R * 0.7f, y[i] + R * 0.7f, 200, 50, false, pt);
        }
    }

    // ---------- cực quang ----------

    void aurora(Canvas c) {
        pt.setStyle(Paint.Style.FILL);
        int bands = AURORA_COLORS.length;
        for (int b = 0; b < bands; b++) {
            float base = H * (0.16f + b * 0.09f);
            float A = H * 0.07f;
            float bottom = base + H * 0.5f;
            int color = AURORA_COLORS[b];
            int top = tint(color, 0.2f - b * 0.03f);
            pt.setShader(new LinearGradient(0, base - A, 0, bottom, top, tint(color, 0), Shader.TileMode.CLAMP));
            path.reset();
            int steps = 36;
            for (int k = 0; k <= steps; k++) {
                float xx = W * k / steps;
                float yy = base + A * (float) Math.sin(xx / W * 5.2f + t * (0.16f + b * 0.05f) + b * 1.7f)
                        + A * 0.45f * (float) Math.sin(xx / W * 11f - t * (0.23f + b * 0.04f));
                if (k == 0) path.moveTo(xx, yy);
                else path.lineTo(xx, yy);
            }
            path.lineTo(W, bottom);
            path.lineTo(0, bottom);
            path.close();
            c.drawPath(path, pt);
        }
        pt.setShader(null);
    }

    // ---------- pháo hoa ----------

    void fireworks(Canvas c, float dt) {
        nextEvent -= dt;
        if (nextEvent <= 0) {
            for (int k = 0; k < kalive.length; k++) {
                if (!kalive[k]) {
                    kalive[k] = true;
                    kx[k] = rnd(W * 0.12f, W * 0.88f);
                    ky[k] = H + 4 * dp;
                    ktarget[k] = rnd(H * 0.12f, H * 0.45f);
                    kvy[k] = -(ky[k] - ktarget[k]) / 1.05f;
                    kcol[k] = r.nextFloat() < 0.3f ? accent : FIRE_COLORS[r.nextInt(FIRE_COLORS.length)];
                    break;
                }
            }
            nextEvent = rnd(1.0f, 3.2f) / dens;
        }
        pt.setStyle(Paint.Style.FILL);
        for (int k = 0; k < kalive.length; k++) {
            if (!kalive[k]) continue;
            ky[k] += kvy[k] * dt;
            pt.setColor(tint(0xFFFFE7C2, 0.8f));
            c.drawCircle(kx[k], ky[k], 1.6f * dp, pt);
            pt.setColor(tint(0xFFFFE7C2, 0.25f));
            c.drawRect(kx[k] - 0.7f * dp, ky[k], kx[k] + 0.7f * dp, ky[k] + 26 * dp, pt);
            if (ky[k] <= ktarget[k]) {
                kalive[k] = false;
                int cnt = Math.round(rnd(40, 60) * Math.min(1.3f, dens));
                for (int j = 0; j < cnt && n < MAX; j++) {
                    double ang = Math.PI * 2 * j / cnt + rnd(-0.05f, 0.05f);
                    float sp = rnd(70, 165) * dp;
                    x[n] = kx[k];
                    y[n] = ky[k];
                    vx[n] = (float) Math.cos(ang) * sp;
                    vy[n] = (float) Math.sin(ang) * sp;
                    life[n] = rnd(1.3f, 2.0f);
                    a[n] = life[n];
                    col[n] = r.nextFloat() < 0.15f ? 0xFFFFFFFF : kcol[k];
                    n++;
                }
            }
        }
        float drag = (float) Math.pow(0.35, dt);
        for (int i = 0; i < n; i++) {
            life[i] -= dt;
            if (life[i] <= 0) {
                n--;
                x[i] = x[n]; y[i] = y[n]; vx[i] = vx[n]; vy[i] = vy[n];
                life[i] = life[n]; a[i] = a[n]; col[i] = col[n];
                i--;
                continue;
            }
            vx[i] *= drag;
            vy[i] = vy[i] * drag + 55 * dp * dt;
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            float q = life[i] / a[i];
            float al = (float) Math.pow(q, 1.3);
            if (q < 0.35f && r.nextFloat() < 0.25f) al *= 0.3f;
            pt.setColor(tint(col[i], al * 0.22f));
            c.drawCircle(x[i], y[i], 4.2f * dp, pt);
            pt.setColor(tint(col[i], al));
            c.drawCircle(x[i], y[i], 1.5f * dp, pt);
        }
    }

    static int mix(int c1, int c2, float t) {
        return Color.rgb(
                Math.round(Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t),
                Math.round(Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t),
                Math.round(Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t));
    }
}
