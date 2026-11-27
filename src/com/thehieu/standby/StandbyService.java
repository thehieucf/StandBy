package com.thehieu.standby;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Chạy nền: khi đang sạc + máy khóa/tắt màn hình + máy dựng nằm ngang ổn định
 * → bật StandbyActivity (qua full-screen intent, giống app báo thức).
 */
public class StandbyService extends Service implements SensorEventListener {
    static final String CH_BG = "bg", CH_FSI = "fsi";
    static final int NOTIF_BG = 1, NOTIF_FSI = 2;
    static final int ST_LAND = 1, ST_PORT = 2, ST_OTHER = 3;

    static volatile boolean sVisible;
    static volatile long sHiddenAt;
    static StandbyService sInstance;

    Handler h;
    Prefs prefs;
    PowerManager pm;
    KeyguardManager km;
    NotificationManager nm;
    SensorManager sm;
    Sensor acc;
    PowerManager.WakeLock wl;

    boolean charging, suppressed, monitoring, haveG;
    float gx, gy, gz;
    int rawState = -1, stableState = -1, failCount;
    long rawSince, lastLaunch, holdUntil;
    /** Khi không sạc: chỉ theo dõi xoay máy trong một khoảng ngắn sau khi tắt màn để đỡ tốn pin. */
    long offWindowUntil;
    static final long OFF_WINDOW = 30_000;

    static void start(Context c) {
        c.startForegroundService(new Intent(c, StandbyService.class));
    }

    /** Không tự bật lại cho tới khi tắt màn hình / dựng dọc / rút sạc. */
    static void suppress() {
        StandbyService s = sInstance;
        if (s != null) s.suppressed = true;
    }

    static void onShown() {
        StandbyService s = sInstance;
        if (s != null) {
            s.failCount = 0;
            s.nm.cancel(NOTIF_FSI);
            s.h.post(s::evaluate);
        }
    }

    static void poke() {
        StandbyService s = sInstance;
        if (s != null) s.h.post(s::evaluate);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        h = new Handler(Looper.getMainLooper());
        prefs = new Prefs(this);
        pm = getSystemService(PowerManager.class);
        km = getSystemService(KeyguardManager.class);
        nm = getSystemService(NotificationManager.class);
        sm = getSystemService(SensorManager.class);
        acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "standby:orientation");
        wl.setReferenceCounted(false);

        NotificationChannel bg = new NotificationChannel(CH_BG, "Chạy nền", NotificationManager.IMPORTANCE_MIN);
        bg.setShowBadge(false);
        NotificationChannel fsi = new NotificationChannel(CH_FSI, "Mở StandBy", NotificationManager.IMPORTANCE_HIGH);
        fsi.setSound(null, null);
        fsi.enableVibration(false);
        fsi.setShowBadge(false);
        fsi.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(bg);
        nm.createNotificationChannel(fsi);

        Notification n = new Notification.Builder(this, CH_BG)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("StandBy đang chờ")
                .setContentText("Khóa máy và đặt nằm ngang để hiện đồng hồ")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .build();
        startForeground(NOTIF_BG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(rx, f, Context.RECEIVER_EXPORTED);

        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        charging = b != null && b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        evaluate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.enabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        evaluate();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(rx); } catch (Exception ignored) { }
        stopMonitor();
        h.removeCallbacksAndMessages(null);
        if (sInstance == this) sInstance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            long now = SystemClock.uptimeMillis();
            switch (a) {
                case Intent.ACTION_POWER_CONNECTED:
                    charging = true;
                    suppressed = false;
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    charging = false;
                    suppressed = false;
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    // Bấm nút nguồn lúc StandBy đang hiện = muốn tắt màn → đừng bật lại.
                    suppressed = sVisible || now - sHiddenAt < 1500;
                    holdUntil = 0;
                    offWindowUntil = now + OFF_WINDOW;
                    h.postDelayed(StandbyService.this::evaluate, OFF_WINDOW + 500);
                    break;
                case Intent.ACTION_SCREEN_ON:
                    suppressed = false;
                    // Chờ một nhịp để mở khóa vân tay từ màn tắt không bị StandBy chen vào.
                    holdUntil = now + 900;
                    h.postDelayed(StandbyService.this::check, 1000);
                    break;
                case Intent.ACTION_USER_PRESENT:
                    suppressed = false;
                    break;
            }
            evaluate();
        }
    };

    void evaluate() {
        boolean screenOff = !pm.isInteractive();
        boolean lockedOn = !screenOff && km.isKeyguardLocked();
        boolean powerOk = charging || !prefs.needCharge();
        // Đang sạc: theo dõi suốt lúc tắt màn. Không sạc: chỉ 30 giây đầu sau khi tắt màn.
        boolean offOk = screenOff && (charging || SystemClock.uptimeMillis() < offWindowUntil);
        boolean want = prefs.enabled() && powerOk && !sVisible && (lockedOn || offOk) && acc != null;
        if (want && !monitoring) startMonitor();
        else if (!want && monitoring) stopMonitor();
        else if (want && charging && wl != null && !wl.isHeld() && screenOff) wl.acquire();
        if (monitoring) check();
    }

    void startMonitor() {
        haveG = false;
        rawState = stableState = -1;
        sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL, h);
        if (charging) wl.acquire();
        else wl.acquire(OFF_WINDOW + 2_000);
        monitoring = true;
    }

    void stopMonitor() {
        if (sm != null) sm.unregisterListener(this);
        if (wl != null && wl.isHeld()) wl.release();
        monitoring = false;
    }

    static int classify(float x, float y, float z) {
        float ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        // Ngang: trọng lực dọc theo cạnh dài của máy, máy dựng đứng hoặc hơi ngả (không nằm phẳng).
        if (ax > 6.0f && ay < 4.5f && az < 7.5f) return ST_LAND;
        if (ay > 6.5f && ax < 4.5f) return ST_PORT;
        return ST_OTHER;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float k = haveG ? 0.3f : 1f;
        gx += k * (e.values[0] - gx);
        gy += k * (e.values[1] - gy);
        gz += k * (e.values[2] - gz);
        haveG = true;
        long now = SystemClock.uptimeMillis();
        int st = classify(gx, gy, gz);
        if (st != rawState) {
            rawState = st;
            rawSince = now;
        } else if (now - rawSince > 700 && stableState != st) {
            stableState = st;
            if (st != ST_LAND) suppressed = false;
        }
        check();
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) { }

    void check() {
        if (!monitoring || stableState != ST_LAND || suppressed || sVisible) return;
        if (prefs.needCharge() && !charging) return;
        long now = SystemClock.uptimeMillis();
        if (now < holdUntil) return;
        if (pm.isInteractive() && !km.isKeyguardLocked()) {
            evaluate();
            return;
        }
        long cool = failCount == 0 ? 2500 : failCount < 3 ? 6000 : 60000;
        if (now - lastLaunch < cool) return;
        lastLaunch = now;
        failCount++;
        launch();
    }

    void launch() {
        Intent i = new Intent(this, StandbyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent pi = PendingIntent.getActivity(this, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH_FSI)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("StandBy")
                .setContentText("Đang mở đồng hồ chờ")
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setTimeoutAfter(8000)
                .build();
        nm.notify(NOTIF_FSI, n);
        if (Settings.canDrawOverlays(this)) {
            try { startActivity(i); } catch (Exception ignored) { }
        }
    }
}
