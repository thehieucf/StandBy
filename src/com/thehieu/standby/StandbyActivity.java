package com.thehieu.standby;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.Calendar;

public class StandbyActivity extends Activity implements StandbyView.Host, SensorEventListener {
    static final long SHIFT_EVERY = 60_000;
    static final long DIM_AFTER = 5 * 60_000;
    static final long CYCLE_EVERY = 20 * 60_000;
    static final long BLACKOUT_AFTER = 10 * 60_000;

    final Handler h = new Handler(Looper.getMainLooper());
    Prefs prefs;
    StandbyView view;
    boolean preview;
    SensorManager sm;
    Sensor acc, light;
    float gx, gy, gz;
    boolean haveG;
    long portraitSince = -1;
    float lux = -1;
    boolean nightNow;
    long darkSince = -1;
    long lastTouch, startedAt, lastShift, lastCycle, lastInfo;
    boolean plugged = true;
    int level = 100;
    long unpluggedSince = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        preview = getIntent().getBooleanExtra("preview", false);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setAttributes(lp);
        getWindow().setDecorFitsSystemWindows(false);

        view = new StandbyView(this, prefs, this);
        int face = getIntent().getIntExtra("face", -1);
        if (face >= 0) view.focusFace(face);
        setContentView(view);
        getSystemService(NotificationManager.class).cancel(StandbyService.NOTIF_FSI);

        sm = getSystemService(SensorManager.class);
        acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        light = sm.getDefaultSensor(Sensor.TYPE_LIGHT);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(rx, f, Context.RECEIVER_EXPORTED);

        long now = SystemClock.uptimeMillis();
        lastTouch = startedAt = lastShift = lastCycle = now;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        getSystemService(NotificationManager.class).cancel(StandbyService.NOTIF_FSI);
    }

    @Override
    protected void onResume() {
        super.onResume();
        StandbyService.sVisible = true;
        StandbyService.onShown();
        hideBars();
        if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL);
        if (light != null) sm.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        lastInfo = 0;
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        StandbyService.sVisible = false;
        StandbyService.sHiddenAt = SystemClock.uptimeMillis();
        sm.unregisterListener(this);
        h.removeCallbacks(tick);
        StandbyService.poke();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(rx); } catch (Exception ignored) { }
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    void hideBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        c.hide(WindowInsets.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    public void onBackPressed() {
        onUnlockRequest();
    }

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            long idle = now - lastTouch;

            view.setDim(prefs.dim() && idle > DIM_AFTER);
            if (now - lastShift >= SHIFT_EVERY) {
                lastShift = now;
                view.shiftPixels();
            }
            if (prefs.cycle() && now - lastCycle >= CYCLE_EVERY && idle > 60_000) {
                lastCycle = now;
                view.nextPage();
            }
            boolean black = prefs.blackout() && darkSince > 0 && now - darkSince > BLACKOUT_AFTER && idle > BLACKOUT_AFTER;
            if (black != view.blackout) {
                view.setBlackout(black);
                applyBrightness();
            }
            if (!preview && !plugged) {
                // Không sạc: tự thoát để đỡ hao pin.
                int um = prefs.unplugMin();
                long since = Math.max(unpluggedSince, lastTouch);
                if (level <= 15 || (um > 0 && now - since > um * 60_000L)) {
                    StandbyService.suppress();
                    finish();
                    return;
                }
            }
            int maxH = prefs.maxHours();
            if (!preview && maxH > 0 && now - startedAt > maxH * 3_600_000L) {
                StandbyService.suppress();
                finish();
                return;
            }
            if (now - lastInfo > 60_000) {
                lastInfo = now;
                refreshInfo();
            }
            view.invalidate();
            long wall = System.currentTimeMillis();
            h.postDelayed(this, 1000 - (wall % 1000) + 8);
        }
    };

    void refreshInfo() {
        String alarm = null;
        long alarmAt = -1;
        AlarmManager am = getSystemService(AlarmManager.class);
        AlarmManager.AlarmClockInfo ai = am != null ? am.getNextAlarmClock() : null;
        if (ai != null && ai.getTriggerTime() - System.currentTimeMillis() < 24 * 3_600_000L) {
            Calendar c = Calendar.getInstance();
            int todayY = c.get(Calendar.DAY_OF_YEAR);
            c.setTimeInMillis(ai.getTriggerTime());
            int H = c.get(Calendar.HOUR_OF_DAY);
            String t;
            if (prefs.h24()) t = Faces.two(H) + ":" + Faces.two(c.get(Calendar.MINUTE));
            else t = (H % 12 == 0 ? 12 : H % 12) + ":" + Faces.two(c.get(Calendar.MINUTE)) + (H < 12 ? " SA" : " CH");
            alarmAt = ai.getTriggerTime();
            alarm = "Báo thức " + t + (c.get(Calendar.DAY_OF_YEAR) != todayY ? " ngày mai" : "");
        }
        long left = -1;
        BatteryManager bm = getSystemService(BatteryManager.class);
        if (bm != null) left = bm.computeChargeTimeRemaining();
        view.setInfo(alarm, alarmAt, left);
    }

    void applyBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float want;
        if (view.blackout) want = 0.004f;
        else if (view.night()) want = 0.02f;
        else want = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        if (lp.screenBrightness != want) {
            lp.screenBrightness = want;
            getWindow().setAttributes(lp);
        }
    }

    // ---------- Host ----------

    @Override
    public void onUserActivity() {
        lastTouch = SystemClock.uptimeMillis();
        if (view.blackout) {
            view.setBlackout(false);
            if (darkSince > 0) darkSince = lastTouch;
            applyBrightness();
        }
        view.setDim(false);
    }

    @Override
    public void onUnlockRequest() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (preview || km == null || !km.isKeyguardLocked()) {
            finish();
            return;
        }
        StandbyService.suppress();
        km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override
            public void onDismissSucceeded() { finish(); }
        });
    }

    // ---------- cảm biến ----------

    @Override
    public void onSensorChanged(SensorEvent e) {
        long now = SystemClock.uptimeMillis();
        if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float k = haveG ? 0.3f : 1f;
            gx += k * (e.values[0] - gx);
            gy += k * (e.values[1] - gy);
            gz += k * (e.values[2] - gz);
            haveG = true;
            if (preview) return;
            if (StandbyService.classify(gx, gy, gz) == StandbyService.ST_PORT) {
                if (portraitSince < 0) portraitSince = now;
                else if (now - portraitSince > 1000) finish();
            } else {
                portraitSince = -1;
            }
        } else if (e.sensor.getType() == Sensor.TYPE_LIGHT) {
            float v = e.values[0];
            lux = lux < 0 ? v : lux + (v - lux) * 0.25f;
            boolean n = nightNow ? lux < 8f : lux < 3f;
            nightNow = n;
            view.setNight(prefs.night() && n);
            if (lux < 1.5f) {
                if (darkSince < 0) darkSince = now;
            } else {
                darkSince = -1;
                if (view.blackout) view.setBlackout(false);
            }
            applyBrightness();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) { }

    final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            switch (a) {
                case Intent.ACTION_BATTERY_CHANGED: {
                    int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                    boolean pl = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                    int pct = level < 0 ? 100 : level * 100 / Math.max(1, scale);
                    if (plugged && !pl) unpluggedSince = SystemClock.uptimeMillis();
                    if (!pl && unpluggedSince < 0) unpluggedSince = SystemClock.uptimeMillis();
                    plugged = pl;
                    StandbyActivity.this.level = pct;
                    view.setBattery(level < 0 ? -1 : pct, pl, status == BatteryManager.BATTERY_STATUS_FULL);
                    break;
                }
                case Intent.ACTION_POWER_DISCONNECTED:
                    if (!preview && prefs.needCharge()) finish();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    finish();
                    break;
                case Intent.ACTION_USER_PRESENT:
                    if (!preview) finish();
                    break;
            }
        }
    };
}
