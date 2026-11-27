package com.thehieu.standby;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static final int REQ_PICK = 7;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    Prefs prefs;
    Ui ui;
    Faces faces;
    Photos photos;
    final Handler h = new Handler(Looper.getMainLooper());

    ScrollView sv;
    LinearLayout content;
    FrameLayout topBar;
    TextView bigTitle;
    View permSection;

    FacePreview hero;
    final FacePreview[] thumbs = new FacePreview[Faces.COUNT];
    final FacePreview[] fxTiles = new FacePreview[Effects.COUNT];
    Ui.Row rDensity, rUnplug;
    TextView heroName, heroSub;
    MiSwitch heroSw;
    int focus;

    LinearLayout notice;
    TextView noticeText;
    LinearLayout photoStrip;
    Ui.Row rAccent, rFont, rPhotos, rMax, rNotif, rFsi, rBatt, rLock, rPopup;
    int tickCount;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        ui = new Ui(this);
        faces = new Faces(this, prefs);
        photos = new Photos(this, this::invalidatePreviews);
        faces.photos = photos;
        readBattery();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ui.bg);
        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);
        root.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));

        topBar = new FrameLayout(this);
        topBar.setBackgroundColor(ui.bg);
        topBar.setAlpha(0f);
        TextView small = ui.text("StandBy", 18f, ui.text, 600);
        topBar.addView(small, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
        root.addView(topBar, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP));
        setContentView(root);

        root.setOnApplyWindowInsetsListener((v, in) -> {
            Insets s = in.getInsets(WindowInsets.Type.systemBars());
            content.setPadding(0, s.top + ui.px(44), 0, s.bottom + ui.px(28));
            topBar.setPadding(0, s.top, 0, ui.px(16));
            topBar.setMinimumHeight(s.top + ui.px(56));
            return WindowInsets.CONSUMED;
        });
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            float t = Math.max(0f, Math.min(1f, (y - ui.px(28)) / (float) ui.px(28)));
            topBar.setAlpha(t);
            bigTitle.setAlpha(1f - t);
        });
        WindowInsetsController ic = getWindow().getInsetsController();
        if (ic != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            ic.setSystemBarsAppearance(ui.dark ? 0 : light, light);
        }

        build();
        int pf = prefs.pageFace();
        focus(pf >= 0 && pf < Faces.COUNT ? pf : Faces.BIG);
        if (prefs.enabled()) StandbyService.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        readBattery();
        photos.reload();
        refresh();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            hero.invalidate();
            if (tickCount++ % 10 == 0) for (FacePreview t : thumbs) if (t != null) t.invalidate();
            h.postDelayed(this, 1000 - System.currentTimeMillis() % 1000 + 10);
        }
    };

    void invalidatePreviews() {
        if (hero != null) hero.invalidate();
        for (FacePreview t : thumbs) if (t != null) t.invalidate();
    }

    void readBattery() {
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return;
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        faces.battery = level < 0 ? -1 : level * 100 / Math.max(1, scale);
        faces.charging = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        faces.full = b.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_FULL;
    }

    // ---------- dựng màn hình ----------

    void build() {
        bigTitle = ui.text("StandBy", 32f, ui.text, 620);
        bigTitle.setPadding(ui.px(28), ui.px(12), ui.px(28), 0);
        content.addView(bigTitle);
        TextView sub = ui.text("Khóa máy và đặt nằm ngang để thành đồng hồ để bàn", 14f, ui.sub, 360);
        sub.setPadding(ui.px(28), ui.px(10), ui.px(28), ui.px(6));
        content.addView(sub);

        // Thông báo thiếu quyền
        notice = new LinearLayout(this);
        notice.setOrientation(LinearLayout.HORIZONTAL);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setBackground(ui.round(ui.dark ? 0xFF2A1F12 : 0xFFFFF4E5, 20));
        notice.setPadding(ui.px(20), ui.px(16), ui.px(14), ui.px(16));
        noticeText = ui.text("", 14f, ui.dark ? 0xFFFFC27A : 0xFFB35C00, 450);
        notice.addView(noticeText, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView go = ui.text("Cấp quyền", 14f, ui.accent, 600);
        go.setPadding(ui.px(12), ui.px(8), ui.px(6), ui.px(8));
        notice.addView(go);
        notice.setOnClickListener(v -> sv.smoothScrollTo(0, permSection.getTop()));
        LinearLayout.LayoutParams nl = ui.lp(MATCH, WRAP);
        nl.leftMargin = nl.rightMargin = ui.px(14);
        nl.topMargin = ui.px(14);
        content.addView(notice, nl);

        buildGallery();

        buildEffects();

        ui.section(content, "Chung");
        LinearLayout g = ui.card(content);
        ui.switchRow(g, prefs, "Bật StandBy", "Tự hiện khi máy khóa và dựng nằm ngang", "enabled", true, () -> {
            if (prefs.enabled()) StandbyService.start(this);
            else stopService(new Intent(this, StandbyService.class));
        });
        ui.switchRow(g, prefs, "Chỉ khi đang sạc", "Tắt: không cắm sạc vẫn vào StandBy khi khóa máy lúc cầm ngang hoặc bật màn khóa rồi xoay ngang", "needCharge", false, () -> {
            StandbyService.poke();
            refresh();
        });
        rUnplug = ui.navRow(g, "Khi không sạc, tự thoát sau", "Không chạm trong khoảng này thì tắt StandBy cho đỡ hao pin; pin dưới 15% cũng tự thoát", "", v -> unplugSheet());
        ui.switchRow(g, prefs, "Đồng hồ 24 giờ", null, "h24", true, this::invalidatePreviews);
        rAccent = ui.navRow(g, "Màu nhấn", null, "", v -> accentSheet());
        rFont = ui.navRow(g, "Kiểu chữ số", "Font đồng hồ có sẵn trong HyperOS", "", v -> fontSheet());

        ui.section(content, "Khung ảnh");
        LinearLayout ph = ui.card(content);
        rPhotos = ui.navRow(ph, "Ảnh đã chọn", "Xem, gỡ bớt hoặc thêm ảnh mới", "", v -> openPhotos(false));
        photoStrip = new LinearLayout(this);
        photoStrip.setPadding(ui.px(20), 0, ui.px(20), ui.px(18));
        ph.addView(photoStrip, ui.lp(MATCH, WRAP));

        ui.section(content, "Chống lưu ảnh màn hình");
        LinearLayout a = ui.card(content);
        ui.switchRow(a, prefs, "Dịch điểm ảnh", "Mỗi phút toàn bộ nội dung trôi nhẹ sang chỗ khác", "shift", true, null);
        ui.switchRow(a, prefs, "Làm mờ khi không chạm", "Sau 5 phút nội dung giảm còn 45% độ sáng", "dim", true, null);
        ui.switchRow(a, prefs, "Tự đổi giao diện", "Chuyển sang giao diện kế tiếp mỗi 20 phút", "cycle", true, null);
        ui.switchRow(a, prefs, "Chế độ ban đêm", "Phòng tối: chữ chuyển đỏ, màn hình giảm sáng tối đa", "night", true, null);
        ui.switchRow(a, prefs, "Tắt hẳn khi phòng tối", "Tối hẳn 10 phút thì màn đen hoàn toàn; chạm hoặc bật đèn để hiện lại", "blackout", false, null);
        rMax = ui.navRow(a, "Tự thoát sau", "Hết giờ thì màn hình tắt như bình thường", "", v -> maxSheet());

        ui.section(content, "Quyền");
        permSection = content.getChildAt(content.getChildCount() - 1);
        LinearLayout p = ui.card(content);
        rNotif = ui.navRow(p, "Thông báo", null, "", v -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1));
        rFsi = ui.navRow(p, "Thông báo toàn màn hình", "Để StandBy tự sáng màn khi máy đang tắt", "",
                v -> open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri())));
        rBatt = ui.navRow(p, "Tiết kiệm pin", "Vào mục Tiết kiệm pin, chọn Không hạn chế, để HyperOS không đóng băng StandBy khi tắt màn", "",
                v -> openMiuiAppInfo());
        rLock = ui.navRow(p, "Hiện trên màn hình khóa", null, "", v -> openMiuiPerms());
        rPopup = ui.navRow(p, "Mở cửa sổ khi chạy nền", null, "", v -> openMiuiPerms());
        ui.navRow(p, "Tự khởi chạy", "Để StandBy chạy lại sau khi khởi động máy", "", v -> openAutostart());

        ui.section(content, "Cách dùng");
        LinearLayout u = ui.card(content);
        String[][] tips = {
                {"Vuốt ngang", "Đổi giao diện"},
                {"Vuốt xuống", "Đổi kiểu chữ số"},
                {"Chạm giữ", "Đổi màu nhấn"},
                {"Chạm 2 lần", "Đổi hiệu ứng nền"},
                {"Vuốt lên", "Mở khóa bằng vân tay hoặc mật khẩu"},
                {"Nút nguồn", "Tắt màn, StandBy không tự bật lại"},
                {"Dựng dọc máy", "Thoát StandBy"}};
        for (String[] t : tips) {
            Ui.Row r = ui.row(u, t[0], null);
            TextView v = ui.text(t[1], 14f, ui.value, 380);
            v.setGravity(Gravity.END);
            r.view.addView(v, new LinearLayout.LayoutParams(0, WRAP, 1.3f));
            r.view.setMinimumHeight(ui.px(54));
            r.view.setBackground(null);
        }

        TextView foot = ui.text("StandBy 2.0", 12.5f, ui.value, 380);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, ui.px(28), 0, 0);
        content.addView(foot, ui.lp(MATCH, WRAP));
    }

    void buildGallery() {
        ui.section(content, "Giao diện");
        LinearLayout c = ui.card(content);
        c.setPadding(ui.px(14), ui.px(14), ui.px(14), ui.px(16));

        hero = new FacePreview(this, ui, faces, Faces.BIG, ui.px(14));
        hero.fx = new Effects(ui.dp);
        hero.setOnClickListener(v -> previewFull());
        c.addView(hero, ui.lp(MATCH, WRAP));

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(ui.px(8), ui.px(16), ui.px(4), ui.px(12));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        heroName = ui.text("", 18f, ui.text, 600);
        heroSub = ui.text("", 13f, ui.sub, 360);
        names.addView(heroName);
        LinearLayout.LayoutParams sl = ui.lp(WRAP, WRAP);
        sl.topMargin = ui.px(6);
        names.addView(heroSub, sl);
        info.addView(names, new LinearLayout.LayoutParams(0, WRAP, 1));
        heroSw = new MiSwitch(this, ui, true);
        heroSw.listener = on -> {
            prefs.setFace(focus, on);
            if (on && focus == Faces.PHOTO && photos.size() == 0) openPhotos(true);
            refreshGallery();
        };
        info.addView(heroSw);
        c.addView(info);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setPadding(0, ui.px(2), 0, ui.px(4));
        for (int i = 0; i < Faces.ORDER.length; i++) {
            final int f = Faces.ORDER[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            FacePreview t = new FacePreview(this, ui, faces, f, ui.px(10));
            thumbs[f] = t;
            item.addView(t, ui.lp(ui.px(132), WRAP));
            TextView name = ui.text(Faces.NAMES[f], 12.5f, ui.sub, 420);
            name.setPadding(0, ui.px(8), 0, 0);
            name.setGravity(Gravity.CENTER);
            item.addView(name, ui.lp(ui.px(132), WRAP));
            item.setOnClickListener(v -> focus(f));
            t.setOnClickListener(v -> focus(f));
            LinearLayout.LayoutParams il = ui.lp(WRAP, WRAP);
            il.rightMargin = i == Faces.ORDER.length - 1 ? 0 : ui.px(10);
            strip.addView(item, il);
        }
        hs.addView(strip);
        c.addView(hs);

        TextView full = ui.pill("Xem toàn màn hình", true, v -> previewFull());
        LinearLayout.LayoutParams fl = ui.lp(MATCH, ui.px(50));
        fl.topMargin = ui.px(16);
        c.addView(full, fl);
    }

    void buildEffects() {
        ui.section(content, "Hiệu ứng nền");
        LinearLayout c = ui.card(content);
        c.setPadding(0, ui.px(16), 0, 0);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setPadding(ui.px(14), 0, ui.px(14), ui.px(4));
        for (int i = 0; i < Effects.COUNT; i++) {
            final int ef = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            FacePreview t = new FacePreview(this, ui, faces, -1, ui.px(10));
            t.fx = new Effects(ui.dp);
            t.fxType = ef;
            fxTiles[ef] = t;
            item.addView(t, ui.lp(ui.px(112), WRAP));
            TextView name = ui.text(Effects.NAMES[ef], 12.5f, ui.sub, 420);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, ui.px(8), 0, 0);
            item.addView(name, ui.lp(ui.px(112), WRAP));
            View.OnClickListener pick = v -> {
                prefs.put("effect", ef);
                refreshEffects();
                hero.invalidate();
            };
            item.setOnClickListener(pick);
            t.setOnClickListener(pick);
            LinearLayout.LayoutParams il = ui.lp(WRAP, WRAP);
            il.rightMargin = i == Effects.COUNT - 1 ? 0 : ui.px(10);
            strip.addView(item, il);
        }
        hs.addView(strip);
        c.addView(hs);
        rDensity = ui.navRow(c, "Mật độ", "Chạm 2 lần trên StandBy để đổi nhanh hiệu ứng", "", v -> densitySheet());
        refreshEffects();
    }

    void refreshEffects() {
        int cur = prefs.effect();
        for (int i = 0; i < Effects.COUNT; i++) {
            if (fxTiles[i] == null) continue;
            fxTiles[i].selected = i == cur;
            fxTiles[i].invalidate();
        }
        if (rDensity != null) {
            rDensity.value.setText(Effects.DENSITY_NAMES[Math.floorMod(prefs.fxDensity(), Effects.DENSITY.length)]);
            rDensity.view.setVisibility(cur == Effects.NONE || cur == Effects.AURORA ? View.GONE : View.VISIBLE);
        }
    }

    void densitySheet() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] d = new Dialog[1];
        for (int i = 0; i < Effects.DENSITY.length; i++) {
            final int idx = i;
            list.addView(ui.option(ui.text(Effects.DENSITY_NAMES[i], 16f, ui.text, 420), prefs.fxDensity() == i, v -> {
                prefs.put("fxDensity", idx);
                d[0].dismiss();
                refreshEffects();
            }));
        }
        d[0] = ui.sheet("Mật độ hiệu ứng", list);
        d[0].show();
    }

    void focus(int f) {
        focus = f;
        hero.face = f;
        refreshGallery();
    }

    void refreshGallery() {
        int[] pages = prefs.pages();
        boolean on = prefs.faceOn(focus);
        heroName.setText(Faces.NAMES[focus]);
        int idx = -1;
        for (int i = 0; i < pages.length; i++) if (pages[i] == focus) idx = i;
        String s;
        if (!on) s = "Đang tắt · bật để thêm vào StandBy";
        else s = "Giao diện " + (idx + 1) + " / " + pages.length;
        if (focus == Faces.PHOTO) s += " · " + photos.size() + " ảnh";
        heroSub.setText(s);
        heroSw.setChecked(on, false);
        for (int f = 0; f < Faces.COUNT; f++) {
            FacePreview t = thumbs[f];
            if (t == null) continue;
            boolean fon = prefs.faceOn(f);
            t.selected = f == focus;
            t.badge = fon ? 1 : 2;
            t.dimmed = !fon;
            t.invalidate();
        }
        hero.invalidate();
    }

    void previewFull() {
        startActivity(new Intent(this, StandbyActivity.class).putExtra("preview", true).putExtra("face", focus));
    }

    // ---------- bảng chọn ----------

    void accentSheet() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(ui.px(16), 0, ui.px(16), 0);
        final Dialog[] d = new Dialog[1];
        LinearLayout row = null;
        for (int i = 0; i < Faces.ACCENTS.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                grid.addView(row, ui.lp(MATCH, WRAP));
            }
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, ui.px(10), 0, ui.px(10));
            cell.setBackground(ui.ripple());
            final boolean sel = prefs.accent() == i;
            View dot = new View(this) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void onDraw(Canvas c) {
                    float r = getWidth() / 2f;
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Faces.ACCENTS[idx]);
                    c.drawCircle(r, r, sel ? r - ui.px(5) : r - ui.px(2), p);
                    if (sel) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(ui.px(2));
                        p.setColor(ui.accent);
                        c.drawCircle(r, r, r - ui.px(1), p);
                    }
                    if (Faces.ACCENTS[idx] == 0xFFF4EFE6 && !ui.dark) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(ui.px(1));
                        p.setColor(0x22000000);
                        c.drawCircle(r, r, sel ? r - ui.px(5) : r - ui.px(2), p);
                    }
                }
            };
            cell.addView(dot, ui.lp(ui.px(44), ui.px(44)));
            TextView n = ui.text(Faces.ACCENT_NAMES[i], 12.5f, sel ? ui.accent : ui.sub, 420);
            n.setPadding(0, ui.px(8), 0, 0);
            cell.addView(n);
            cell.setOnClickListener(v -> {
                prefs.put("accent", idx);
                d[0].dismiss();
                refresh();
            });
            row.addView(cell, new LinearLayout.LayoutParams(0, WRAP, 1));
        }
        d[0] = ui.sheet("Màu nhấn", grid);
        d[0].show();
    }

    void fontSheet() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] d = new Dialog[1];
        for (int i = 0; i < Fonts.CLOCK_FILES.length; i++) {
            final int idx = i;
            LinearLayout label = new LinearLayout(this);
            label.setGravity(Gravity.CENTER_VERTICAL);
            TextView sample = new TextView(this);
            sample.setText("09:41");
            sample.setTextSize(30f);
            sample.setTypeface(Fonts.clock(i));
            sample.setTextColor(prefs.font() == i ? ui.accent : ui.text);
            sample.setIncludeFontPadding(false);
            label.addView(sample, ui.lp(ui.px(130), WRAP));
            label.addView(ui.text(Fonts.CLOCK_NAMES[i], 15f, ui.sub, 420));
            list.addView(ui.option(label, prefs.font() == i, v -> {
                prefs.put("font", idx);
                d[0].dismiss();
                refresh();
            }));
        }
        d[0] = ui.sheet("Kiểu chữ số", list);
        d[0].show();
    }

    void maxSheet() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] d = new Dialog[1];
        for (int i = 0; i < Prefs.MAX_HOURS.length; i++) {
            final int idx = i;
            list.addView(ui.option(ui.text(maxLabel(Prefs.MAX_HOURS[i]), 16f, ui.text, 420), prefs.maxHoursIdx() == i, v -> {
                prefs.put("maxHours", idx);
                d[0].dismiss();
                refresh();
            }));
        }
        d[0] = ui.sheet("Tự thoát sau", list);
        d[0].show();
    }

    static String unplugLabel(int min) { return min == 0 ? "Không tự thoát" : min + " phút"; }

    void unplugSheet() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] d = new Dialog[1];
        for (int i = 0; i < Prefs.UNPLUG_MIN.length; i++) {
            final int idx = i;
            list.addView(ui.option(ui.text(unplugLabel(Prefs.UNPLUG_MIN[i]), 16f, ui.text, 420), prefs.unplugIdx() == i, v -> {
                prefs.put("unplugIdx", idx);
                d[0].dismiss();
                refresh();
            }));
        }
        d[0] = ui.sheet("Khi không sạc, tự thoát sau", list);
        d[0].show();
    }

    static String maxLabel(int hrs) { return hrs == 0 ? "Không bao giờ" : hrs + " giờ"; }

    // ---------- ảnh ----------

    void openPhotos(boolean pick) {
        startActivity(new Intent(this, PhotosActivity.class).putExtra("pick", pick));
    }

    /** Dải ảnh thu nhỏ trong thẻ Khung ảnh: tối đa 5 ảnh + ô thêm ảnh. */
    void fillStrip() {
        photoStrip.removeAllViews();
        java.io.File[] files = Photos.list(this);
        int gap = ui.px(6);
        int slots = 6;
        int tile = (getResources().getDisplayMetrics().widthPixels - ui.px(28) - ui.px(40) - gap * (slots - 1)) / slots;
        int shown = Math.min(files.length, slots - 1);
        for (int i = 0; i < shown; i++) {
            FrameLayout cell = new FrameLayout(this);
            PhotoTile t = new PhotoTile(this, ui, files[i], tile, ui.px(8));
            cell.addView(t, new FrameLayout.LayoutParams(tile, tile));
            int more = files.length - shown;
            if (i == shown - 1 && more > 0) {
                TextView m = ui.text("+" + more, 15f, 0xFFFFFFFF, 600);
                m.setGravity(Gravity.CENTER);
                m.setBackground(ui.round(0x8C000000, 8));
                cell.addView(m, new FrameLayout.LayoutParams(tile, tile));
            }
            cell.setOnClickListener(v -> openPhotos(false));
            t.setOnClickListener(v -> openPhotos(false));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(tile, tile);
            l.rightMargin = gap;
            photoStrip.addView(cell, l);
        }
        View add = new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                float w = getWidth(), h = getHeight(), s = ui.px(8);
                p.setStrokeWidth(ui.px(2));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(ui.accent);
                c.drawLine(w / 2 - s, h / 2, w / 2 + s, h / 2, p);
                c.drawLine(w / 2, h / 2 - s, w / 2, h / 2 + s, p);
            }
        };
        add.setBackground(ui.round(ui.field, 8));
        add.setOnClickListener(v -> openPhotos(true));
        add.setContentDescription("Thêm ảnh");
        photoStrip.addView(add, new LinearLayout.LayoutParams(tile, tile));
    }

    // ---------- quyền ----------

    Uri pkgUri() { return Uri.parse("package:" + getPackageName()); }

    void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri()));
        }
    }

    void openMiuiPerms() {
        Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR");
        i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
        i.putExtra("extra_pkgname", getPackageName());
        open(i);
    }

    /** Trang thông tin ứng dụng của HyperOS (có mục Tiết kiệm pin). */
    void openMiuiAppInfo() {
        Intent i = new Intent();
        i.setClassName("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity");
        i.putExtra("package_name", getPackageName());
        try {
            startActivity(i);
        } catch (Exception e) {
            open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri()));
        }
    }

    /** 1 nếu HyperOS không đóng băng app (có trong MILLET_NO_RESTRICT_APP), 0 nếu chưa, -1 nếu không đọc được. */
    int milletState() {
        try {
            String v = Settings.System.getString(getContentResolver(), "MILLET_NO_RESTRICT_APP");
            if (v == null) return -1;
            for (String t : v.split(",")) if (t.trim().equals(getPackageName())) return 1;
            return 0;
        } catch (Throwable t) {
            return -1;
        }
    }

    void openAutostart() {
        Intent i = new Intent();
        i.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        open(i);
    }

    /** Quyền riêng của HyperOS: 10020 = hiện trên màn khóa, 10021 = mở cửa sổ khi chạy nền. */
    int miuiOp(int op) {
        try {
            AppOpsManager ao = getSystemService(AppOpsManager.class);
            Method m = AppOpsManager.class.getMethod("checkOpNoThrow", int.class, int.class, String.class);
            int mode = (int) m.invoke(ao, op, Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED ? 1 : 0;
        } catch (Throwable t) {
            return -1;
        }
    }

    int status(Ui.Row r, int state) {
        if (state > 0) {
            r.value.setText("Đã cấp");
            r.value.setTextColor(ui.value);
            return 0;
        }
        if (state == 0) {
            r.value.setText("Chưa cấp");
            r.value.setTextColor(ui.red);
            return 1;
        }
        r.value.setText("Kiểm tra");
        r.value.setTextColor(ui.value);
        return 0;
    }

    void refresh() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        PowerManager pm = getSystemService(PowerManager.class);
        int missing = 0;
        missing += status(rNotif, checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ? 1 : 0);
        missing += status(rFsi, nm.canUseFullScreenIntent() ? 1 : 0);
        int mil = milletState();
        int battOk = mil >= 0 ? mil : (pm.isIgnoringBatteryOptimizations(getPackageName()) ? 1 : 0);
        missing += status(rBatt, battOk);
        if (battOk > 0) rBatt.value.setText("Không hạn chế");
        missing += status(rLock, miuiOp(10020));
        missing += status(rPopup, miuiOp(10021));
        notice.setVisibility(missing > 0 ? View.VISIBLE : View.GONE);
        noticeText.setText("Còn " + missing + " quyền chưa cấp, StandBy có thể không tự hiện");

        rAccent.value.setText(Faces.ACCENT_NAMES[Math.floorMod(prefs.accent(), Faces.ACCENTS.length)]);
        rFont.value.setText(Fonts.CLOCK_NAMES[Math.floorMod(prefs.font(), Fonts.CLOCK_FILES.length)]);
        rFont.value.setTypeface(Fonts.sans(380));
        int n = photos.size();
        rPhotos.value.setText(n == 0 ? "Chưa có ảnh" : n + " ảnh");
        fillStrip();
        rMax.value.setText(maxLabel(prefs.maxHours()));
        rUnplug.value.setText(unplugLabel(prefs.unplugMin()));
        rUnplug.view.setVisibility(prefs.needCharge() ? View.GONE : View.VISIBLE);
        refreshGallery();
        refreshEffects();
        invalidatePreviews();
    }
}
