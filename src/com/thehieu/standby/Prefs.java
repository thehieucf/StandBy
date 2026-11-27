package com.thehieu.standby;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final int[] MAX_HOURS = {0, 1, 2, 4, 8};
    /** Khi không sạc, tự thoát sau bấy nhiêu phút không chạm (0 = không). */
    static final int[] UNPLUG_MIN = {5, 10, 30, 0};
    static final String DEFAULT_FACES = "0,1,2,3,4,5,6,8,9,10,11,12,13,14";

    final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("standby", Context.MODE_PRIVATE);
        // Bản 3: bật sẵn các giao diện mới cho người đã cài bản cũ.
        if (sp.getInt("facesVer", 1) < 3) {
            if (sp.contains("faces")) {
                String f = sp.getString("faces", DEFAULT_FACES);
                StringBuilder b = new StringBuilder(f);
                for (int i = 9; i <= 14; i++) b.append(b.length() > 0 ? "," : "").append(i);
                sp.edit().putString("faces", b.toString()).apply();
            }
            sp.edit().putInt("facesVer", 3).apply();
        }
    }

    boolean needCharge() { return sp.getBoolean("needCharge", false); }
    int unplugIdx()     { return sp.getInt("unplugIdx", 1); }
    int unplugMin()     { return UNPLUG_MIN[Math.max(0, Math.min(UNPLUG_MIN.length - 1, unplugIdx()))]; }
    int effect()        { return sp.getInt("effect", 0); }
    int fxDensity()     { return sp.getInt("fxDensity", 1); }

    boolean enabled()   { return sp.getBoolean("enabled", true); }
    boolean h24()       { return sp.getBoolean("h24", true); }
    boolean shift()     { return sp.getBoolean("shift", true); }
    boolean dim()       { return sp.getBoolean("dim", true); }
    boolean cycle()     { return sp.getBoolean("cycle", true); }
    boolean night()     { return sp.getBoolean("night", true); }
    boolean blackout()  { return sp.getBoolean("blackout", false); }
    int accent()        { return sp.getInt("accent", 1); }
    int font()          { return sp.getInt("font", 0); }
    int pageFace()      { return sp.getInt("pageFace", 0); }
    int maxHoursIdx()   { return sp.getInt("maxHours", 0); }
    int maxHours()      { return MAX_HOURS[Math.max(0, Math.min(MAX_HOURS.length - 1, maxHoursIdx()))]; }

    /** Mặt đồng hồ đang bật, dạng "0,1,3". */
    boolean faceOn(int id) {
        for (String t : sp.getString("faces", DEFAULT_FACES).split(",")) {
            if (t.equals(String.valueOf(id))) return true;
        }
        return false;
    }

    void setFace(int id, boolean on) {
        StringBuilder b = new StringBuilder();
        for (int f = 0; f < Faces.COUNT; f++) {
            boolean v = f == id ? on : faceOn(f);
            if (v) b.append(b.length() > 0 ? "," : "").append(f);
        }
        sp.edit().putString("faces", b.toString()).apply();
    }

    /** Danh sách trang theo thứ tự hiển thị; luôn có ít nhất một trang. */
    int[] pages() {
        int n = 0;
        int[] tmp = new int[Faces.ORDER.length];
        for (int f : Faces.ORDER) if (faceOn(f)) tmp[n++] = f;
        if (n == 0) return new int[]{Faces.BIG};
        int[] r = new int[n];
        System.arraycopy(tmp, 0, r, 0, n);
        return r;
    }

    boolean get(String k, boolean def) { return sp.getBoolean(k, def); }
    void put(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    void put(String k, int v)     { sp.edit().putInt(k, v).apply(); }
}
