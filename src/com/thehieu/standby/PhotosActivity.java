package com.thehieu.standby;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Xem các ảnh đang dùng cho Khung ảnh, chạm để chọn rồi gỡ, hoặc thêm ảnh mới. */
public class PhotosActivity extends Activity {
    static final int REQ_PICK = 8;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int COLS = 3;

    Prefs prefs;
    Ui ui;
    ScrollView sv;
    LinearLayout content, grid, bottom;
    FrameLayout topBar;
    View topBg;
    TextView bigTitle, smallTitle, subtitle, selectAll, empty;
    File[] files = new File[0];
    final Set<String> selected = new HashSet<>();
    boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        ui = new Ui(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ui.bg);
        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);
        root.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));

        bigTitle = ui.text("Ảnh đã chọn", 30f, ui.text, 620);
        bigTitle.setPadding(ui.px(28), ui.px(12), ui.px(28), 0);
        content.addView(bigTitle);
        subtitle = ui.text("", 14f, ui.sub, 360);
        subtitle.setPadding(ui.px(28), ui.px(10), ui.px(28), ui.px(18));
        content.addView(subtitle);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(ui.px(14), 0, ui.px(14), 0);
        content.addView(grid, ui.lp(MATCH, WRAP));
        empty = ui.text("Chưa có ảnh nào.\nẢnh bạn thêm sẽ lần lượt hiện trên StandBy, mỗi ảnh 40 giây.", 15f, ui.sub, 380);
        empty.setGravity(Gravity.CENTER);
        empty.setLineSpacing(ui.px(4), 1f);
        empty.setPadding(ui.px(40), ui.px(90), ui.px(40), 0);
        content.addView(empty, ui.lp(MATCH, WRAP));

        // Thanh trên: nút quay lại, tiêu đề nhỏ khi cuộn, chọn tất cả
        topBar = new FrameLayout(this);
        topBg = new View(this);
        topBg.setBackgroundColor(ui.bg);
        topBg.setAlpha(0f);
        topBar.addView(topBg, new FrameLayout.LayoutParams(MATCH, MATCH));
        View back = backArrow();
        back.setOnClickListener(v -> finish());
        topBar.addView(back, new FrameLayout.LayoutParams(ui.px(48), ui.px(48), Gravity.START | Gravity.BOTTOM));
        smallTitle = ui.text("Ảnh đã chọn", 18f, ui.text, 600);
        smallTitle.setAlpha(0f);
        FrameLayout.LayoutParams stl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        stl.bottomMargin = ui.px(13);
        topBar.addView(smallTitle, stl);
        selectAll = ui.text("Chọn tất cả", 15f, ui.accent, 500);
        selectAll.setPadding(ui.px(16), ui.px(14), ui.px(20), ui.px(14));
        selectAll.setOnClickListener(v -> {
            if (selected.size() == files.length) selected.clear();
            else for (File f : files) selected.add(f.getName());
            updateSelection();
        });
        topBar.addView(selectAll, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END | Gravity.BOTTOM));
        root.addView(topBar, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP));

        // Thanh dưới
        bottom = new LinearLayout(this);
        bottom.setBackgroundColor(ui.bg);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bottom, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));
        setContentView(root);

        root.setOnApplyWindowInsetsListener((v, in) -> {
            Insets s = in.getInsets(WindowInsets.Type.systemBars());
            topBar.setPadding(ui.px(4), s.top, 0, 0);
            ViewGroup.LayoutParams tl = topBar.getLayoutParams();
            tl.height = s.top + ui.px(56);
            topBar.setLayoutParams(tl);
            content.setPadding(0, s.top + ui.px(44), 0, s.bottom + ui.px(110));
            bottom.setPadding(ui.px(24), ui.px(12), ui.px(24), s.bottom + ui.px(14));
            return WindowInsets.CONSUMED;
        });
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            float t = Math.max(0f, Math.min(1f, (y - ui.px(24)) / (float) ui.px(28)));
            topBg.setAlpha(t);
            smallTitle.setAlpha(t);
            bigTitle.setAlpha(1f - t);
        });
        WindowInsetsController ic = getWindow().getInsetsController();
        if (ic != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            ic.setSystemBarsAppearance(ui.dark ? 0 : light, light);
        }
        reload();
        if (getIntent().getBooleanExtra("pick", false) && b == null) pick();
    }

    View backArrow() {
        return new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Path path = new Path();

            @Override
            protected void onDraw(Canvas c) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(ui.px(2.2f));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(ui.text);
                float cx = getWidth() / 2f, cy = getHeight() / 2f, s = ui.px(7);
                path.reset();
                path.moveTo(cx + s * 0.45f, cy - s);
                path.lineTo(cx - s * 0.55f, cy);
                path.lineTo(cx + s * 0.45f, cy + s);
                c.drawPath(path, p);
            }
        };
    }

    void reload() {
        files = Photos.list(this);
        Set<String> names = new HashSet<>();
        for (File f : files) names.add(f.getName());
        selected.retainAll(names);
        buildGrid();
        updateSelection();
    }

    void buildGrid() {
        grid.removeAllViews();
        int gap = ui.px(4);
        int avail = getResources().getDisplayMetrics().widthPixels - ui.px(28);
        int tile = (avail - gap * (COLS - 1)) / COLS;
        LinearLayout row = null;
        for (int i = 0; i < files.length; i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rl = ui.lp(MATCH, WRAP);
                rl.bottomMargin = gap;
                grid.addView(row, rl);
            }
            final File f = files[i];
            PhotoTile t = new PhotoTile(this, ui, f, tile, ui.px(10));
            t.selectable = true;
            t.selected = selected.contains(f.getName());
            t.setOnClickListener(v -> {
                if (!selected.remove(f.getName())) selected.add(f.getName());
                t.selected = selected.contains(f.getName());
                t.invalidate();
                updateSelection();
            });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(tile, tile);
            if (i % COLS != COLS - 1) tl.rightMargin = gap;
            row.addView(t, tl);
        }
        empty.setVisibility(files.length == 0 ? View.VISIBLE : View.GONE);
    }

    void updateSelection() {
        int n = files.length, s = selected.size();
        subtitle.setText(n == 0 ? "Khung ảnh đang trống"
                : s > 0 ? "Đã chọn " + s + " / " + n + " ảnh"
                : n + " ảnh · chạm vào ảnh để chọn ảnh muốn gỡ");
        selectAll.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        selectAll.setText(n > 0 && s == n ? "Bỏ chọn" : "Chọn tất cả");
        for (int i = 0; i < grid.getChildCount(); i++) {
            ViewGroup r = (ViewGroup) grid.getChildAt(i);
            for (int j = 0; j < r.getChildCount(); j++) {
                PhotoTile t = (PhotoTile) r.getChildAt(j);
                boolean sel = selected.contains(t.file.getName());
                if (t.selected != sel) {
                    t.selected = sel;
                    t.invalidate();
                }
            }
        }
        bottom.removeAllViews();
        if (s == 0) {
            TextView add = ui.pill(busy ? "Đang thêm ảnh…" : "Thêm ảnh", true, v -> pick());
            add.setEnabled(!busy);
            bottom.addView(add, new LinearLayout.LayoutParams(0, ui.px(50), 1));
        } else {
            TextView clear = ui.pill("Bỏ chọn", false, v -> {
                selected.clear();
                updateSelection();
            });
            clear.setTextColor(ui.text);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ui.px(50), 1);
            cl.rightMargin = ui.px(12);
            bottom.addView(clear, cl);
            TextView del = ui.pill("Gỡ " + s + " ảnh", true, v -> removeSelected());
            del.setBackground(ui.round(ui.red, 16));
            bottom.addView(del, new LinearLayout.LayoutParams(0, ui.px(50), 1));
        }
    }

    void removeSelected() {
        int n = 0;
        for (File f : files) {
            if (selected.contains(f.getName()) && f.delete()) n++;
        }
        selected.clear();
        if (Photos.count(this) == 0) prefs.setFace(Faces.PHOTO, false);
        Toast.makeText(this, "Đã gỡ " + n + " ảnh khỏi khung ảnh", Toast.LENGTH_SHORT).show();
        reload();
    }

    void pick() {
        try {
            startActivityForResult(Photos.pickIntent(), REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "Máy không có trình chọn ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK) return;
        List<android.net.Uri> uris = Photos.uris(data);
        if (uris.isEmpty()) return;
        busy = true;
        updateSelection();
        Photos.importAll(this, uris, n -> {
            busy = false;
            prefs.setFace(Faces.PHOTO, true);
            Toast.makeText(this, "Đã thêm " + n + " ảnh", Toast.LENGTH_SHORT).show();
            reload();
            sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
        });
    }
}
