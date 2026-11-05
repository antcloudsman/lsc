package com.luopan.fengshui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * F-18 盘面目录（LC 实现；原版是 CompassListActivity + 服务端目录）。
 *
 * 口径（docs/features/F-18-盘面目录.md §6 / 总监指示）：
 * - 内置盘面 = drawable-nodpi 里实际存在的 5 幅（dial_full_0..4；dial0.png 与 dial_full_0 同图，dial1..4_min 是缩略图）；
 *   全部可选，无 VIP、无「上传到服务器」。
 * - **无云端盘面库**：原版「内置 14 盘面」是线上盘面库，LC 不做服务器；因此口径是
 *   「内置 5 幅全开放 + 本机导入」，台账不得写成「14 个盘面已齐」。
 * - 默认盘面保持 dial_index=0（线稿盘），不改默认值。
 * - 导入：系统文件选择器取图 → 复制到私有目录 files/compass_dials/ → 生成长边 ≤2048 缩略预览 →
 *   列表可选；选中后写 dial_custom_path，MainActivity 的 CompassView 直接换图并持久化。
 * - 删除：只删本机导入项（内置不可删），带确认；删除当前选中项时回退 dial_index=0。
 * - flag enabled_f18 关闭时回退旧四组列表（当前行为）。
 * - id 前缀：lc_compasslist_*（内置 item / 导入 custom / 删除 del / 导入按钮 import）。
 */
public class CompassListActivity extends Activity {

    private static final int REQ_PICK_IMAGE = 4301;
    private static final int MAX_THUMB = 2048;
    /** C-01：单文件与累计上限（内部存储，防大文件写满）。 */
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;

    private static final int[] THUMBS = {
            R.drawable.dial0, R.drawable.dial1_min, R.drawable.dial2_min,
            R.drawable.dial3_min, R.drawable.dial4_min
    };

    private static final int[] ITEM_IDS = {
            R.id.lc_compasslist_item_0, R.id.lc_compasslist_item_1,
            R.id.lc_compasslist_item_2, R.id.lc_compasslist_item_3,
            R.id.lc_compasslist_item_4
    };

    private static final int[] CUSTOM_IDS = {
            R.id.lc_compasslist_custom_0, R.id.lc_compasslist_custom_1,
            R.id.lc_compasslist_custom_2, R.id.lc_compasslist_custom_3,
            R.id.lc_compasslist_custom_4, R.id.lc_compasslist_custom_5,
            R.id.lc_compasslist_custom_6, R.id.lc_compasslist_custom_7,
            R.id.lc_compasslist_custom_8, R.id.lc_compasslist_custom_9
    };

    private static final int[] DEL_IDS = {
            R.id.lc_compasslist_del_0, R.id.lc_compasslist_del_1,
            R.id.lc_compasslist_del_2, R.id.lc_compasslist_del_3,
            R.id.lc_compasslist_del_4, R.id.lc_compasslist_del_5,
            R.id.lc_compasslist_del_6, R.id.lc_compasslist_del_7,
            R.id.lc_compasslist_del_8, R.id.lc_compasslist_del_9
    };

    private SharedPreferences prefs;
    private CompassDialStore store;
    private LinearLayout gallery;
    private int selected;
    private String selectedCustom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("lc_prefs", MODE_PRIVATE);
        selected = prefs.getInt("dial_index", 0);
        selectedCustom = prefs.getString("dial_custom_path", "");

        if (!new FeatureFlags(this).isEnabled("f18")) {
            buildLegacy();
            return;
        }
        store = new CompassDialStore(this);
        buildGallery();
    }

    // ---------------- F-18：目录 ----------------

    private void buildGallery() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(24, 20, 16));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("罗盘盘面");
        title.setTextColor(Color.rgb(220, 190, 130));
        title.setTextSize(24f);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        Button importBtn = new Button(this);
        importBtn.setId(R.id.lc_compasslist_import);
        importBtn.setText(R.string.lc_compasslist_import);
        importBtn.setOnClickListener(v -> pickImage());
        root.addView(importBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        gallery = new LinearLayout(this);
        gallery.setOrientation(LinearLayout.VERTICAL);
        root.addView(gallery);

        setContentView(scroll);
        refreshGallery();
    }

    private void refreshGallery() {
        if (gallery == null) return;
        gallery.removeAllViews();

        gallery.addView(header(getString(R.string.lc_compasslist_builtin) + "（5）"));
        for (int i = 0; i < THUMBS.length; i++) {
            gallery.addView(builtInRow(i));
        }

        List<CompassDialStore.Item> imported = store.list();
        gallery.addView(header(getString(R.string.lc_compasslist_imported) + "（" + imported.size() + "）"));
        if (imported.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setId(R.id.lc_compasslist_empty);
            empty.setText(R.string.lc_compasslist_empty);
            empty.setTextColor(Color.rgb(150, 130, 100));
            empty.setTextSize(14f);
            empty.setPadding(0, dp(8), 0, dp(8));
            gallery.addView(empty);
            return;
        }
        for (int i = 0; i < imported.size(); i++) {
            gallery.addView(importedRow(imported.get(i), i));
        }
    }

    private View header(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(17f);
        tv.setTextColor(Color.rgb(235, 228, 215));
        tv.setPadding(0, dp(18), 0, dp(6));
        return tv;
    }

    private View builtInRow(int index) {
        boolean current = (selectedCustom == null || selectedCustom.isEmpty()) && selected == index;
        LinearLayout row = rowContainer();
        row.setId(index < ITEM_IDS.length ? ITEM_IDS[index] : View.NO_ID);
        if (current) row.setBackgroundColor(Color.argb(60, 220, 190, 130));

        ImageView thumb = new ImageView(this);
        int size = dp(56);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.rightMargin = dp(16);
        thumb.setLayoutParams(lp);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setImageResource(THUMBS[index]);
        row.addView(thumb);

        TextView label = new TextView(this);
        label.setText(CompassData.COMPASS_TYPES[index] + (current ? "（当前）" : ""));
        label.setTextSize(16f);
        label.setTextColor(Color.rgb(235, 228, 215));
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> selectBuiltIn(index));
        return row;
    }

    private View importedRow(final CompassDialStore.Item item, final int index) {
        boolean current = item.path.equals(selectedCustom);
        LinearLayout row = rowContainer();
        row.setId(index < CUSTOM_IDS.length ? CUSTOM_IDS[index] : View.NO_ID);
        if (current) row.setBackgroundColor(Color.argb(60, 220, 190, 130));

        ImageView thumb = new ImageView(this);
        int size = dp(56);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.rightMargin = dp(16);
        thumb.setLayoutParams(lp);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bmp = decodeSampled(item.thumb.isEmpty() ? item.path : item.thumb, 256);
        if (bmp != null) thumb.setImageBitmap(bmp);
        row.addView(thumb);

        TextView label = new TextView(this);
        label.setText(item.name + (current ? "（当前）" : ""));
        label.setTextSize(16f);
        label.setTextColor(Color.rgb(235, 228, 215));
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView del = new TextView(this);
        del.setId(index < DEL_IDS.length ? DEL_IDS[index] : View.NO_ID);
        del.setText(R.string.lc_compasslist_delete);
        del.setTextSize(14f);
        del.setTextColor(Color.rgb(220, 120, 110));
        del.setPadding(dp(12), dp(6), dp(4), dp(6));
        del.setOnClickListener(v -> confirmDelete(item));
        row.addView(del);

        row.setOnClickListener(v -> selectCustom(item));
        return row;
    }

    private LinearLayout rowContainer() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        return row;
    }

    private void selectBuiltIn(int index) {
        prefs.edit().putInt("dial_index", index).remove("dial_custom_path").apply();
        selected = index;
        selectedCustom = "";
        Toast.makeText(this, getString(R.string.lc_compasslist_selected) + " " + CompassData.COMPASS_TYPES[index],
                Toast.LENGTH_SHORT).show();
        finish();
    }

    private void selectCustom(CompassDialStore.Item item) {
        prefs.edit().putInt("dial_index", 0).putString("dial_custom_path", item.path).apply();
        selected = 0;
        selectedCustom = item.path;
        Toast.makeText(this, getString(R.string.lc_compasslist_selected) + " " + item.name,
                Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete(final CompassDialStore.Item item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.lc_compasslist_delete_title)
                .setMessage(R.string.lc_compasslist_delete_msg)
                .setPositiveButton(R.string.lc_compasslist_delete, (d, w) -> delete(item))
                .setNegativeButton(R.string.lc_compasslist_cancel, null)
                .show();
    }

    private void delete(CompassDialStore.Item item) {
        store.remove(item);
        try {
            new File(item.path).delete();
            if (!item.thumb.isEmpty()) new File(item.thumb).delete();
        } catch (Throwable ignored) {
        }
        if (item.path.equals(selectedCustom)) {
            prefs.edit().remove("dial_custom_path").putInt("dial_index", 0).apply();
            selectedCustom = "";
            selected = 0;
        }
        refreshGallery();
    }

    // ---------------- 导入 ----------------

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_PICK_IMAGE);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.lc_compasslist_import_fail, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            importFromUri(uri);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.lc_compasslist_import_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void importFromUri(Uri uri) throws Exception {
        File dir = new File(getFilesDir(), "compass_dials");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("mkdir failed");
        // C-01：单文件 ≤20MB、累计 ≤200MB，超限直接拒绝，不写盘。
        long declared = querySize(uri);
        if (declared > MAX_FILE_BYTES) {
            Toast.makeText(this, R.string.lc_compasslist_too_large, Toast.LENGTH_LONG).show();
            return;
        }
        long total = dirBytes(dir);
        long remaining = MAX_TOTAL_BYTES - total;
        if (remaining <= 0 || (declared > 0 && declared > remaining)) {
            Toast.makeText(this, R.string.lc_compasslist_no_space, Toast.LENGTH_LONG).show();
            return;
        }
        String stamp = String.valueOf(System.currentTimeMillis());

        File raw = new File(dir, "dial_" + stamp + ".img");
        long written = 0;
        try {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(raw)) {
                if (in == null) throw new Exception("open failed");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    written += n;
                    if (written > MAX_FILE_BYTES || written > remaining) {
                        throw new Exception("too large");
                    }
                    out.write(buf, 0, n);
                }
            }
        } catch (Exception e) {
            raw.delete();
            Toast.makeText(this, R.string.lc_compasslist_too_large, Toast.LENGTH_LONG).show();
            return;
        }

        // 生成长边 ≤2048 的预览，避免列表/盘面解码时 OOM。
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(raw.getAbsolutePath(), bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_THUMB) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(raw.getAbsolutePath(), opts);
        String thumbPath = "";
        if (bmp != null) {
            File thumb = new File(dir, "thumb_" + stamp + ".jpg");
            try (FileOutputStream out = new FileOutputStream(thumb)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            thumbPath = thumb.getAbsolutePath();
        }

        String name = displayName(uri);
        if (name == null || name.trim().isEmpty()) name = "导入盘面 " + stamp;
        store.add(new CompassDialStore.Item(name, raw.getAbsolutePath(), thumbPath));
        refreshGallery();
        Toast.makeText(this, getString(R.string.lc_compasslist_selected) + " " + name,
                Toast.LENGTH_SHORT).show();
    }

    private long querySize(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return -1;
    }

    private long dirBytes(File dir) {
        long total = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) total += f.length();
        }
        return total;
    }

    private String displayName(Uri uri) {        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private Bitmap decodeSampled(String path, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0) return null;
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------- flag 关闭：旧四组列表（当前行为） ----------------

    private void buildLegacy() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(24, 20, 16));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32), dp(32), dp(32), dp(32));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("罗盘盘面");
        title.setTextColor(Color.rgb(220, 190, 130));
        title.setTextSize(24f);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        addLegacyGroup(root, "自定义罗盘",
                "上传自定义盘面：原版为本地 + 服务端目录，此处暂未实现（有意省略）", false);
        addLegacyGroup(root, "常用罗盘", null, true);
        addLegacyGroup(root, "简易罗盘",
                "需联网加载盘面目录（原版来自 GET api/compass/list，离线不可穷举）", false);
        addLegacyGroup(root, "综合罗盘",
                "需联网加载盘面目录（原版来自 GET api/compass/list，离线不可穷举）", false);

        setContentView(scroll);
    }

    private void addLegacyGroup(LinearLayout root, String name, String placeholder, boolean enabled) {
        TextView header = new TextView(this);
        header.setText(name);
        header.setTextSize(18f);
        header.setTextColor(enabled ? Color.rgb(235, 228, 215) : Color.rgb(120, 112, 100));
        header.setPadding(0, dp(24), 0, dp(8));
        root.addView(header);

        if (!enabled) {
            TextView ph = new TextView(this);
            ph.setText(placeholder);
            ph.setTextSize(13f);
            ph.setTextColor(Color.rgb(120, 112, 100));
            ph.setPadding(0, dp(4), 0, dp(12));
            root.addView(ph);
            return;
        }

        for (int i = 0; i < CompassData.COMPASS_TYPES.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setBackgroundColor(idx == selected
                    ? Color.argb(60, 220, 190, 130) : Color.TRANSPARENT);

            ImageView thumb = new ImageView(this);
            int size = dp(48);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = dp(16);
            thumb.setLayoutParams(lp);
            thumb.setImageResource(THUMBS[i % THUMBS.length]);
            row.addView(thumb);

            TextView label = new TextView(this);
            label.setText(CompassData.COMPASS_TYPES[i] + (idx == selected ? "（当前）" : ""));
            label.setTextSize(16f);
            label.setTextColor(Color.rgb(235, 228, 215));
            row.addView(label);

            row.setOnClickListener(v -> {
                prefs.edit().putInt("dial_index", idx).remove("dial_custom_path").apply();
                Toast.makeText(this, "已选择 " + CompassData.COMPASS_TYPES[idx],
                        Toast.LENGTH_SHORT).show();
                finish();
            });
            root.addView(row);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
