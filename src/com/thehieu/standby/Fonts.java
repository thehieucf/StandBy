package com.thehieu.standby;

import android.graphics.Typeface;
import android.util.SparseArray;

import java.io.File;

/** Font có sẵn trên HyperOS (/product/fonts). Không có thì lùi về font hệ thống. */
final class Fonts {
    static final String DIR = "/product/fonts/";
    static final String[] CLOCK_FILES = {
            "MiClockThin.otf", "MiClock.otf", "MitypeClock.otf", "BebasNeue-Mono.otf",
            "NeumaticCompressed.otf", "ChamberiDisplay-Semibold.ttf", "CommutersSansMono.otf",
            "GulfsDisplay-ExtraExpandedItalic.otf", "C800.ttf"};
    static final String[] CLOCK_NAMES = {
            "Mảnh", "Mi Clock", "Bo tròn", "Cao hẹp", "Nén đậm", "Cổ điển", "Rộng", "Nghiêng", "Khối"};
    static final int[] CLOCK_FALLBACK = {200, 300, 400, 500, 900, 600, 400, 800, 900};
    static final int OUTLINE_FONT = 4;

    private static final Typeface[] clock = new Typeface[CLOCK_FILES.length];
    private static final SparseArray<Typeface> sans = new SparseArray<>();
    private static Typeface serif;

    private Fonts() {}

    static Typeface clock(int i) {
        i = Math.floorMod(i, CLOCK_FILES.length);
        if (clock[i] == null) {
            Typeface t = null;
            File f = new File(DIR + CLOCK_FILES[i]);
            if (f.canRead()) {
                try { t = Typeface.createFromFile(f); } catch (Exception ignored) { }
            }
            if (t == null) t = Typeface.create(Typeface.SANS_SERIF, CLOCK_FALLBACK[i], false);
            clock[i] = t;
        }
        return clock[i];
    }

    /** MiSans Latin VF (có đủ dấu tiếng Việt), độ đậm tùy ý. */
    static Typeface sans(int weight) {
        Typeface t = sans.get(weight);
        if (t == null) {
            File f = new File(DIR + "MiSansLatinVF.ttf");
            if (f.canRead()) {
                try {
                    t = new Typeface.Builder(f).setFontVariationSettings("'wght' " + weight).build();
                } catch (Exception ignored) { }
            }
            if (t == null) t = Typeface.create(Typeface.SANS_SERIF, weight, false);
            sans.put(weight, t);
        }
        return t;
    }

    /** Chữ có chân có dấu tiếng Việt cho mặt "Bằng chữ". */
    static Typeface serif() {
        if (serif == null) {
            File f = new File(DIR + "ChamberiDisplay-Semibold.ttf");
            try { serif = f.canRead() ? Typeface.createFromFile(f) : null; } catch (Exception ignored) { }
            if (serif == null) serif = Typeface.create(Typeface.SERIF, 600, false);
        }
        return serif;
    }
}
