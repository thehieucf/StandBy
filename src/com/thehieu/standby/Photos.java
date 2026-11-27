package com.thehieu.standby;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ảnh của mặt "Khung ảnh": chép vào bộ nhớ riêng của app, đọc dần ở luồng nền. */
final class Photos {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    static final int MAX_EDGE = 2800;

    final Context ctx;
    final Runnable onLoaded;
    final Handler main = new Handler(Looper.getMainLooper());
    File[] files = new File[0];
    final Bitmap[] cache = new Bitmap[2];
    final int[] cacheIdx = {-1, -1};
    final boolean[] loading = new boolean[2];

    Photos(Context c, Runnable onLoaded) {
        ctx = c.getApplicationContext();
        this.onLoaded = onLoaded;
        reload();
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "photos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File[] list(Context c) {
        File[] f = dir(c).listFiles((d, n) -> n.endsWith(".jpg"));
        if (f == null) return new File[0];
        Arrays.sort(f);
        return f;
    }

    static int count(Context c) { return list(c).length; }

    static void clear(Context c) {
        for (File f : list(c)) f.delete();
    }

    /** Thu nhỏ còn cạnh dài tối đa MAX_EDGE rồi lưu JPEG. Chạy ở luồng nền. */
    static boolean importUri(Context c, Uri uri, String name) {
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(c.getContentResolver(), uri);
            Bitmap b = ImageDecoder.decodeBitmap(src, (dec, info, s) -> {
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                float k = Math.min(1f, MAX_EDGE / (float) Math.max(w, h));
                dec.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
                dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            File out = new File(dir(c), name);
            try (FileOutputStream os = new FileOutputStream(out)) {
                b.compress(Bitmap.CompressFormat.JPEG, 90, os);
            }
            b.recycle();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- ảnh thu nhỏ cho màn quản lý ----------

    static final android.util.LruCache<String, Bitmap> THUMBS = new android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    static final java.util.Set<String> PENDING = new java.util.HashSet<>();
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Ảnh thu nhỏ trong bộ nhớ đệm; chưa có thì đọc ở nền rồi gọi after. */
    static Bitmap thumb(File f, int size, Runnable after) {
        String key = f.getAbsolutePath() + "@" + size;
        Bitmap b = THUMBS.get(key);
        if (b != null || PENDING.contains(key)) return b;
        PENDING.add(key);
        IO.execute(() -> {
            Bitmap r = null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                int sample = 1;
                while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= size) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                r = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            } catch (Throwable ignored) { }
            final Bitmap res = r;
            MAIN.post(() -> {
                PENDING.remove(key);
                if (res != null) THUMBS.put(key, res);
                if (after != null) after.run();
            });
        });
        return null;
    }

    /** Chép các ảnh vừa chọn vào app; xong thì gọi done(số ảnh thêm được) trên luồng chính. */
    static void importAll(Context c, java.util.List<Uri> uris, java.util.function.IntConsumer done) {
        final Context app = c.getApplicationContext();
        final long stamp = System.currentTimeMillis();
        IO.execute(() -> {
            int ok = 0;
            for (int i = 0; i < uris.size(); i++) {
                if (importUri(app, uris.get(i), "p" + stamp + "_" + (100 + i) + ".jpg")) ok++;
            }
            final int n = ok;
            MAIN.post(() -> done.accept(n));
        });
    }

    static java.util.List<Uri> uris(android.content.Intent data) {
        java.util.List<Uri> list = new java.util.ArrayList<>();
        if (data == null) return list;
        android.content.ClipData cd = data.getClipData();
        if (cd != null) for (int i = 0; i < cd.getItemCount(); i++) list.add(cd.getItemAt(i).getUri());
        else if (data.getData() != null) list.add(data.getData());
        return list;
    }

    static android.content.Intent pickIntent() {
        android.content.Intent i = new android.content.Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
        i.setType("image/*");
        i.putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX, Math.min(30, android.provider.MediaStore.getPickImagesMaxLimit()));
        return i;
    }

    void reload() {
        files = list(ctx);
        cacheIdx[0] = cacheIdx[1] = -1;
        cache[0] = cache[1] = null;
    }

    int size() { return files.length; }

    /** Trả ảnh thứ i nếu đã đọc xong; nếu chưa thì bắt đầu đọc và trả null. */
    Bitmap get(int i) {
        if (files.length == 0) return null;
        i = Math.floorMod(i, files.length);
        int slot = i % 2;
        if (cacheIdx[slot] == i && cache[slot] != null) return cache[slot];
        if (loading[slot]) return null;
        loading[slot] = true;
        final int idx = i;
        final File f = files[i];
        IO.execute(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            Bitmap b = null;
            try { b = BitmapFactory.decodeFile(f.getAbsolutePath(), o); } catch (Throwable ignored) { }
            final Bitmap res = b;
            main.post(() -> {
                cache[slot] = res;
                cacheIdx[slot] = idx;
                loading[slot] = false;
                if (onLoaded != null) onLoaded.run();
            });
        });
        return null;
    }
}
