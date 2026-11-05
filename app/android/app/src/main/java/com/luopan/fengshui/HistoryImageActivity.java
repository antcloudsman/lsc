package com.luopan.fengshui;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F-10 历史影像（LC地图 新建页，2026-11-05）。
 *
 * 免费边界（照实写、不设门）：
 *  - 原版是 **Google Earth 历年影像**（dex: GoogleHistoryHelper / DBRootV5 / QTree），
 *    属于境外私有服务，不能合规接入；
 *  - 免费近似 = **Esri Wayback**（公开历年影像瓦片，时相列表来自
 *    `config.maptiles.arcgis.com/waybackconfig.json`，2026-11-05 实测 198 个时相、
 *    瓦片 200 + `Access-Control-Allow-Origin: *`）；
 *    来源/许可：Esri、Maxar、Earthstar Geographics 等（Esri 服务条款，自用展示需标注来源）；
 *  - 另一条近似路 Sentinel-2（Copernicus）需注册下载、不是在线瓦片，本页不接；
 *  - 因此页面 = Esri Wayback 时相浏览（更早/较新/今日）+ 本机快照（PNG，应用私有目录）。
 *
 * AT id：lc_history_back/title/content/status/note/date/prev/next/today/snapshot/list。
 */
public class HistoryImageActivity extends LcFeatureActivity {

    private static final String TAG = "LCHISTORY";

    private static final String CONFIG =
            "https://s3-us-west-2.amazonaws.com/config.maptiles.arcgis.com/waybackconfig.json";
    private static final double DEF_LAT = 39.9042;
    private static final double DEF_LNG = 116.4074;

    /** 初始中心：`lc_lat`/`lc_lng`（缺失/越界回退北京）；与 F-06/F-07 同一约定。
     *  原版 `EXTRA_GEO_POINT` 的 JSON 格式待逆向确认，确认后可在这里补一次解析。 */
    private double startLat = DEF_LAT;
    private double startLng = DEF_LNG;

    private static final Pattern DATE = Pattern.compile("Wayback (\\d{4}-\\d{2}-\\d{2})");

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final class Release {
        final String id;
        final String date;

        Release(String id, String date) {
            this.id = id;
            this.date = date;
        }
    }

    private boolean disabled;
    private final List<Release> releases = new ArrayList<>();
    private int idx;

    private WebView web;
    private TextView statusView;
    private TextView dateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disabled = !new FeatureFlags(this).isEnabled("f10");
        startLat = LcExtras.d(getIntent(), "lc_lat", DEF_LAT);
        startLng = LcExtras.d(getIntent(), "lc_lng", DEF_LNG);
        if (Double.isNaN(startLat) || Double.isNaN(startLng)
                || startLat < -85 || startLat > 85 || startLng < -180 || startLng > 180) {
            startLat = DEF_LAT;
            startLng = DEF_LNG;
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String title() {
        return getString(R.string.lc_history_title);
    }

    @Override
    protected int backId() {
        return R.id.lc_history_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_history_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_history_content;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onPageReady(FrameLayout content) {
        if (disabled) {
            Toast.makeText(this, R.string.lc_weather_disabled, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.setBackgroundColor(0xFF181410);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("setBase('img');debugSetView(" + startLat + ","
                        + startLng + ",11,null,null);", null);
                loadReleases();
            }
        });
        web.loadUrl("file:///android_asset/map.html#uimode=map");
        content.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusView = new TextView(this);
        statusView.setId(R.id.lc_history_status);
        statusView.setTextSize(12f);
        statusView.setTextColor(0xFFF0E0BC);
        statusView.setBackgroundColor(0xCC201A14);
        statusView.setPadding(dp(12), dp(6), dp(12), dp(6));
        statusView.setText(R.string.lc_history_loading);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        slp.leftMargin = dp(8);
        slp.rightMargin = dp(8);
        content.addView(statusView, slp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(0xE6201A14);
        bottom.setPadding(dp(10), dp(8), dp(10), dp(8));

        dateView = new TextView(this);
        dateView.setId(R.id.lc_history_date);
        dateView.setTextSize(18f);
        dateView.setTextColor(0xFFF0E0BC);
        bottom.addView(dateView);

        TextView note = new TextView(this);
        note.setId(R.id.lc_history_note);
        note.setTextSize(11f);
        note.setTextColor(0xFFB9A88C);
        /* MD-02：`lc_history_attribution`（影像来源：Esri Wayback · © Esri / Maxar / Earthstar）
         * 之前是死文案——页面只展示了长说明。现在把来源署名放在第一行常显，
         * 与地图右下角 getAttribution() 的角注同一口径；第二行仍是替代说明。 */
        note.setText(getString(R.string.lc_history_attribution) + "\n"
                + getString(R.string.lc_history_note));
        bottom.addView(note);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(8), 0, 0);
        Button prev = new Button(this);
        prev.setId(R.id.lc_history_prev);
        prev.setText(R.string.lc_history_prev);
        prev.setAllCaps(false);
        prev.setTextSize(12f);
        prev.setOnClickListener(v -> move(1));   /* 列表按时间倒序：更早 = index+1 */
        row1.addView(prev);
        Button next = new Button(this);
        next.setId(R.id.lc_history_next);
        next.setText(R.string.lc_history_next);
        next.setAllCaps(false);
        next.setTextSize(12f);
        next.setOnClickListener(v -> move(-1));
        row1.addView(next);
        Button today = new Button(this);
        today.setId(R.id.lc_history_today);
        today.setText(R.string.lc_history_today);
        today.setAllCaps(false);
        today.setTextSize(12f);
        today.setOnClickListener(v -> showCurrent());
        row1.addView(today);
        bottom.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button snap = new Button(this);
        snap.setId(R.id.lc_history_snapshot);
        snap.setText(R.string.lc_history_snapshot);
        snap.setAllCaps(false);
        snap.setTextSize(12f);
        snap.setOnClickListener(v -> takeSnapshot());
        row2.addView(snap);
        Button list = new Button(this);
        list.setId(R.id.lc_history_list);
        list.setText(R.string.lc_history_list);
        list.setAllCaps(false);
        list.setTextSize(12f);
        list.setOnClickListener(v -> showSnapshots());
        row2.addView(list);
        bottom.addView(row2);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        content.addView(bottom, blp);
    }

    /* ---------------- Wayback 时相 ---------------- */

    private void loadReleases() {
        new Thread(() -> {
            List<Release> list = new ArrayList<>();
            try {
                JSONObject root = new JSONObject(LcHttp.get(CONFIG));
                java.util.Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String id = keys.next();
                    if (!isSafeReleaseId(id)) continue;      /* MD-11：只收纯数字释放号 */
                    JSONObject o = root.optJSONObject(id);
                    if (o == null) continue;
                    Matcher m = DATE.matcher(o.optString("itemTitle", ""));
                    if (m.find()) list.add(new Release(id, m.group(1)));
                }
                list.sort((a, b) -> b.date.compareTo(a.date));
            } catch (Throwable ignored) {
                // 失败交给 UI 提示
            }
            final List<Release> out = list;
            MAIN.post(() -> {
                releases.clear();
                releases.addAll(out);
                if (releases.isEmpty()) {
                    statusView.setText(R.string.lc_history_fail);
                    return;
                }
                idx = 0;
                applyWayback();
            });
        }).start();
    }

    private void applyWayback() {
        if (releases.isEmpty() || web == null) return;
        Release r = releases.get(Math.max(0, Math.min(idx, releases.size() - 1)));
        if (!isSafeReleaseId(r.id)) {                 /* 双保险：拼 JS 前再判一次 */
            Log.w(TAG, "bad release id, skip: " + r.id);
            statusView.setText(R.string.lc_history_fail);
            return;
        }
        web.evaluateJavascript("setWayback(true,'" + r.id + "')", null);
        dateView.setText(r.date);
        statusView.setText(getString(R.string.lc_history_source, r.date));
    }

    /**
     * MD-11：Wayback 释放号来自外部 `waybackconfig.json`，只接受**纯数字**（实测 198 个时相
     * 都是 5 位，例如 43291 / 63116 / 26334）。数字长度上限 8 位足够覆盖未来编号。
     * 不合规的 id 直接丢弃，绝不拼进 evaluateJavascript 或 img src；map.html 侧还有第二道同规则校验。
     */
    private static boolean isSafeReleaseId(String id) {
        return id != null && id.matches("\\d{1,8}");
    }

    private void move(int delta) {
        if (releases.isEmpty()) return;
        int next = idx + delta;
        if (next < 0 || next >= releases.size()) {
            Toast.makeText(this, R.string.lc_history_no_more, Toast.LENGTH_SHORT).show();
            return;
        }
        idx = next;
        applyWayback();
    }

    private void showCurrent() {
        if (web == null) return;
        web.evaluateJavascript("setWayback(false)", null);
        dateView.setText("");
        statusView.setText(R.string.lc_history_current);
    }

    /* ---------------- 快照 ---------------- */

    private File snapshotDir() {
        File d = new File(getExternalFilesDir(null), "snapshots");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private void takeSnapshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, R.string.lc_history_save_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        View root = getWindow().getDecorView();
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) {
            Toast.makeText(this, R.string.lc_history_save_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        final Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        PixelCopy.request(getWindow(), bmp, result -> {
            if (result == PixelCopy.SUCCESS) save(bmp);
            else Toast.makeText(this, R.string.lc_history_save_fail, Toast.LENGTH_SHORT).show();
        }, new Handler(Looper.getMainLooper()));
    }

    private void save(Bitmap bmp) {
        try {
            String name = "snap_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date()) + ".png";
            File f = new File(snapshotDir(), name);
            FileOutputStream out = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            bmp.recycle();
            Toast.makeText(this, getString(R.string.lc_history_saved, name),
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, R.string.lc_history_save_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSnapshots() {
        File[] files = snapshotDir().listFiles((d, n) -> n.endsWith(".png"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, R.string.lc_history_empty, Toast.LENGTH_LONG).show();
            return;
        }
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparingLong(File::lastModified).reversed());
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) names[i] = list.get(i).getName();
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.lc_history_list)
                .setItems(names, (dlg, which) -> preview(list.get(which)))
                .setNegativeButton(R.string.lc_history_close, null)
                .show();
    }

    private void preview(File f) {
        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (bmp == null) return;
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(iv);
        new android.app.AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setView(sv)
                .setPositiveButton(R.string.lc_history_close, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
