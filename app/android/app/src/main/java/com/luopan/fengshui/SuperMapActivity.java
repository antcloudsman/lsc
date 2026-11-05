package com.luopan.fengshui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * F-02 超清地图（LC地图 新建页，2026-11-05）。
 *
 * 无门槛实现：**本地拼接**，不是云端超分任务。
 *  - 数据：天地图 `img` 的 **z18 原生最高级**影像瓦片（2026-11-05 实测北京 z18 返回 200）；
 *  - 缓存：Java 侧磁盘缓存（cacheDir/tdt_img18/x_y.jpg）+ map.html 内存缓存（enableTileCache）；
 *  - 拼接：当前视野覆盖的 z18 瓦片 → 一张大图（最多 6×6）；可选 2× 双三次放大；
 *  - 进度：ProgressBar 与文案只按**实际完成下载的瓦片数**推进，不造假进度，也不假装在跑云端任务。
 *  - failure 段：有瓦片失败就进失败态并给失败数量，可重试；已下载的瓦片留在缓存里。
 *
 * AT id：lc_supermap_back/title/content/empty/progress_panel/progress/progress_text/fail/
 *        generate/upscale/close/result。
 */
public class SuperMapActivity extends LcFeatureActivity {

    private static final String TAG = "LCSUPERMAP";
    private static final String KEY = MapKeys.TDT;
    private static final String[] SUB = {"t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7"};
    private static final int TILE_Z = 18;          // 天地图 img 原生最高级
    private static final int MAX_TILES = 36;       // 6×6：单边 1536px，2× 放大后 3072px
    private static final int MIN_ZOOM = 15;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    private boolean disabled;
    private boolean running;

    private WebView web;
    private View emptyView;
    private LinearLayout progressPanel;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView failView;
    private CheckBox upscaleBox;
    private Button generateBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disabled = !new FeatureFlags(this).isEnabled("f02");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String title() {
        return getString(R.string.lc_supermap_title);
    }

    @Override
    protected int backId() {
        return R.id.lc_supermap_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_supermap_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_supermap_content;
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
                view.evaluateJavascript(
                        "setBase('img');enableTileCache(true);debugSetView(39.9042,116.4074,16,null,null);",
                        null);
            }
        });
        web.loadUrl("file:///android_asset/map.html#uimode=map");
        content.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        /* 底部控制区：空 / 进度 / 失败 三段互斥 */
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(0xE6201A14);
        bottom.setPadding(dp(12), dp(10), dp(12), dp(10));

        emptyView = new LinearLayout(this);
        ((LinearLayout) emptyView).setOrientation(LinearLayout.VERTICAL);
        TextView empty = new TextView(this);
        empty.setId(R.id.lc_supermap_empty);
        empty.setTextSize(11f);
        empty.setTextColor(0xFFB9A88C);
        empty.setText(R.string.lc_supermap_empty);
        ((LinearLayout) emptyView).addView(empty);

        LinearLayout genRow = new LinearLayout(this);
        genRow.setOrientation(LinearLayout.HORIZONTAL);
        genRow.setPadding(0, dp(6), 0, 0);
        upscaleBox = new CheckBox(this);
        upscaleBox.setId(R.id.lc_supermap_upscale);
        upscaleBox.setText(R.string.lc_supermap_upscale);
        upscaleBox.setTextSize(12f);
        upscaleBox.setTextColor(0xFFF0E0BC);
        genRow.addView(upscaleBox);
        generateBtn = new Button(this);
        generateBtn.setId(R.id.lc_supermap_generate);
        generateBtn.setText(R.string.lc_supermap_generate);
        generateBtn.setAllCaps(false);
        generateBtn.setTextSize(12f);
        generateBtn.setOnClickListener(v -> generate());
        genRow.addView(generateBtn);
        ((LinearLayout) emptyView).addView(genRow);
        bottom.addView(emptyView);

        progressPanel = new LinearLayout(this);
        progressPanel.setId(R.id.lc_supermap_progress_panel);
        progressPanel.setOrientation(LinearLayout.VERTICAL);
        progressPanel.setVisibility(View.GONE);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setId(R.id.lc_supermap_progress);
        progressPanel.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));
        progressText = new TextView(this);
        progressText.setId(R.id.lc_supermap_progress_text);
        progressText.setTextSize(11f);
        progressText.setTextColor(0xFFB9A88C);
        progressText.setPadding(0, dp(4), 0, dp(4));
        progressPanel.addView(progressText);
        Button close = new Button(this);
        close.setId(R.id.lc_supermap_close);
        close.setText(R.string.lc_supermap_close);
        close.setAllCaps(false);
        close.setTextSize(12f);
        close.setOnClickListener(v -> cancel());
        progressPanel.addView(close);
        bottom.addView(progressPanel);

        failView = new TextView(this);
        failView.setId(R.id.lc_supermap_fail);
        failView.setTextSize(12f);
        failView.setTextColor(0xFFFF9B7A);
        failView.setVisibility(View.GONE);
        bottom.addView(failView);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = android.view.Gravity.BOTTOM;
        content.addView(bottom, blp);
    }

    /* ---------------- 生成 ---------------- */

    private void generate() {
        if (running) return;
        web.evaluateJavascript("JSON.stringify(getState())", stateVal -> {
            double lat0 = Double.NaN, lng0 = Double.NaN;
            int zoom0 = 0;
            try {
                String v = stateVal;
                if (v != null && v.length() >= 2 && v.startsWith("\"")) {
                    v = v.substring(1, v.length() - 1).replace("\\\"", "\"");
                }
                JSONObject st = new JSONObject(v);
                lat0 = st.optDouble("lat", Double.NaN);
                lng0 = st.optDouble("lng", Double.NaN);
                zoom0 = st.optInt("zoom", 0);
            } catch (Throwable ignored) {
                // 走失败提示
            }
            final double lat = lat0, lng = lng0;
            final int zoom = zoom0;
            if (Double.isNaN(lat) || Double.isNaN(lng)) {
                failView.setText(getString(R.string.lc_supermap_fail, 0));
                return;
            }
            if (zoom < MIN_ZOOM) {
                Toast.makeText(this, getString(R.string.lc_supermap_need_zoom, zoom),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            web.evaluateJavascript("JSON.stringify({w:mapEl.clientWidth,h:mapEl.clientHeight})",
                    sizeVal -> {
                        int w0 = 400, h0 = 800;
                        try {
                            String v = sizeVal;
                            if (v != null && v.length() >= 2 && v.startsWith("\"")) {
                                v = v.substring(1, v.length() - 1).replace("\\\"", "\"");
                            }
                            JSONObject o = new JSONObject(v);
                            w0 = o.optInt("w", 400);
                            h0 = o.optInt("h", 800);
                        } catch (Throwable ignored) {
                            // 用默认尺寸
                        }
                        final int cw = w0, ch = h0;
                        startStitch(lat, lng, cw, ch, upscaleBox.isChecked());
                    });
        });
    }

    private void startStitch(final double lat, final double lng, final int cssW, final int cssH,
                             final boolean upscale) {
        running = true;
        emptyView.setVisibility(View.GONE);
        failView.setVisibility(View.GONE);
        progressPanel.setVisibility(View.VISIBLE);

        /* 视野覆盖的 z18 瓦片数（CSS px ≈ 屏幕像素的 1/dpr，先按 1px 计，够用） */
        int needCols = Math.min(6, Math.max(1, (int) Math.ceil(cssW / 256.0) + 1));
        int needRows = Math.min(6, Math.max(1, (int) Math.ceil(cssH / 256.0) + 1));
        if (upscale) { needCols = Math.min(4, needCols); needRows = Math.min(4, needRows); }
        final int cols = needCols, rows = needRows;
        final int total = cols * rows;

        progressBar.setMax(total);
        progressBar.setProgress(0);
        progressText.setText(getString(R.string.lc_supermap_progress, 0, total));

        final double cx = lon2tx(lng, TILE_Z), cy = lat2ty(lat, TILE_Z);
        final int x0 = (int) Math.floor(cx - cols / 2.0);
        final int y0 = (int) Math.floor(cy - rows / 2.0);

        final Bitmap out = Bitmap.createBitmap(cols * 256, rows * 256, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(out);
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final AtomicInteger done = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicInteger cached = new AtomicInteger();
        final List<Throwable> errors = new ArrayList<>();
        final Object lock = new Object();

        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                final int ti = i, tj = j;
                final int tx = x0 + i, ty = y0 + j;
                POOL.execute(() -> {
                    if (!running) return;
                    Bitmap bmp = null;
                    try {
                        File cache = tileFile(tx, ty);
                        if (cache.exists()) {
                            bmp = BitmapFactory.decodeFile(cache.getAbsolutePath());
                            if (bmp != null) cached.incrementAndGet();
                        }
                        if (bmp == null) {
                            byte[] bytes = httpBytes(tileUrl(tx, ty));
                            bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (bmp != null) writeCache(cache, bytes);
                        }
                    } catch (Throwable t) {
                        synchronized (errors) { errors.add(t); }
                    }
                    if (bmp != null) {
                        synchronized (lock) {
                            canvas.drawBitmap(bmp, ti * 256f, tj * 256f, paint);
                        }
                        bmp.recycle();
                    } else {
                        failed.incrementAndGet();
                    }
                    final int d = done.incrementAndGet();
                    MAIN.post(() -> {
                        progressBar.setProgress(d);
                        progressText.setText(getString(R.string.lc_supermap_progress, d, total));
                        if (d == total) finishStitch(out, upscale, failed.get(), cached.get(), total);
                    });
                });
            }
        }
    }

    private void finishStitch(Bitmap out, boolean upscale, int failed, int cached, int total) {
        if (!running) {
            out.recycle();
            return;
        }
        running = false;
        if (failed > 0) {
            out.recycle();
            progressPanel.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            failView.setVisibility(View.VISIBLE);
            failView.setText(getString(R.string.lc_supermap_fail, failed));
            return;
        }
        progressText.setText(getString(R.string.lc_supermap_stitching, total));
        final Bitmap source = out;
        new Thread(() -> {
            Bitmap result = source;
            if (upscale) {
                MAIN.post(() -> progressText.setText(R.string.lc_supermap_upscaling));
                result = bicubic2x(source);
            }
            final Bitmap finalBmp = result;
            MAIN.post(() -> saveAndShow(finalBmp, cached, total));
        }).start();
    }

    /**
     * 落盘并展示。**保存失败必须走失败态**（审查 C-08）：
     * 原先 catch 里只弹个 Toast，紧接着照样写「已生成…」并弹结果窗——保存失败却报成功。
     * 现在失败时：隐藏进度、回到空态 + 失败行写明原因，不进结果窗、不写「已生成」；
     * 已下载的瓦片仍在磁盘缓存里（本方法不删缓存），可直接重试。
     */
    private void saveAndShow(Bitmap bmp, int cached, int total) {
        progressPanel.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        String path = "";
        try {
            File dir = new File(getExternalFilesDir(null), "supermap");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("无法创建目录 " + dir.getAbsolutePath());
            }
            File f = new File(dir, "super_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(f);
            try {
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                    throw new IllegalStateException("PNG 编码失败");
                }
                fos.flush();
            } finally {
                fos.close();
            }
            if (!f.exists() || f.length() <= 0) {
                throw new IllegalStateException("文件为空 " + f.getAbsolutePath());
            }
            path = f.getAbsolutePath();
        } catch (Throwable t) {
            Log.w(TAG, "save fail : " + t);
            if (!bmp.isRecycled()) bmp.recycle();
            failView.setVisibility(View.VISIBLE);
            failView.setText(getString(R.string.lc_supermap_save_fail_detail,
                    String.valueOf(t.getMessage()), total, cached));
            return;
        }
        final String p = path;
        progressText.setText(getString(R.string.lc_supermap_done,
                bmp.getWidth(), bmp.getHeight(), total, cached));
        showResult(bmp, p);
    }

    private void showResult(Bitmap bmp, String path) {
        ImageView iv = new ImageView(this);
        iv.setId(R.id.lc_supermap_result);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(iv);
        new AlertDialog.Builder(this)
                .setTitle(path.isEmpty() ? getString(R.string.lc_supermap_title)
                        : getString(R.string.lc_supermap_saved, path))
                .setView(sv)
                .setPositiveButton(R.string.lc_supermap_close, null)
                .show();
    }

    private void cancel() {
        running = false;
        progressPanel.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.lc_supermap_close, Toast.LENGTH_SHORT).show();
    }

    /* ---------------- 瓦片与几何 ---------------- */

    private String tileUrl(int x, int y) {
        String sub = SUB[(((x + y) % 8) + 8) % 8];
        return "https://" + sub + ".tianditu.gov.cn/img_w/wmts?SERVICE=WMTS&REQUEST=GetTile"
                + "&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles"
                + "&TILEMATRIX=" + TILE_Z + "&TILEROW=" + y + "&TILECOL=" + x + "&tk=" + KEY;
    }

    private File tileFile(int x, int y) {
        File d = new File(getCacheDir(), "tdt_img18");
        if (!d.exists()) d.mkdirs();
        return new File(d, x + "_" + y + ".jpg");
    }

    private static void writeCache(File f, byte[] bytes) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        } catch (Throwable ignored) {
            // 缓存写失败不影响结果
        }
    }

    /**
     * 瓦片下载：走 {@link LcHttp#bytes}，与 JSON/图片请求共用 UA、超时和 **8 MB 字节上限**（审查 C-03）。
     * 原先这里自带一份 HttpURLConnection + 无上限 ByteArrayOutputStream，是第二个实现。
     */
    private static byte[] httpBytes(String url) throws Exception {
        return LcHttp.bytes(url);
    }

    static double lon2tx(double lon, int z) {
        return (lon + 180.0) / 360.0 * Math.pow(2, z);
    }

    static double lat2ty(double lat, int z) {
        double r = Math.toRadians(lat);
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * Math.pow(2, z);
    }

    /** 双三次（Catmull-Rom）2× 放大：不依赖系统缩放，明确“算出来的像素”。 */
    static Bitmap bicubic2x(Bitmap src) {
        int sw = src.getWidth(), sh = src.getHeight();
        int dw = sw * 2, dh = sh * 2;
        Bitmap dst = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
        int[] sp = new int[sw * sh];
        src.getPixels(sp, 0, sw, 0, 0, sw, sh);
        int[] dp = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            double sy = (y + 0.5) / 2.0 - 0.5;
            int iy = (int) Math.floor(sy);
            for (int x = 0; x < dw; x++) {
                double sx = (x + 0.5) / 2.0 - 0.5;
                int ix = (int) Math.floor(sx);
                double r = 0, g = 0, b = 0, a = 0, wsum = 0;
                for (int m = -1; m <= 2; m++) {
                    int yy = Math.max(0, Math.min(sh - 1, iy + m));
                    double wy = cubic(sy - (iy + m));
                    for (int n = -1; n <= 2; n++) {
                        int xx = Math.max(0, Math.min(sw - 1, ix + n));
                        double w = cubic(sx - (ix + n)) * wy;
                        int c = sp[yy * sw + xx];
                        r += ((c >> 16) & 0xFF) * w;
                        g += ((c >> 8) & 0xFF) * w;
                        b += (c & 0xFF) * w;
                        a += ((c >>> 24) & 0xFF) * w;
                        wsum += w;
                    }
                }
                if (wsum == 0) wsum = 1;
                int rv = clamp(r / wsum), gv = clamp(g / wsum), bv = clamp(b / wsum), av = clamp(a / wsum);
                dp[y * dw + x] = (av << 24) | (rv << 16) | (gv << 8) | bv;
            }
        }
        dst.setPixels(dp, 0, dw, 0, 0, dw, dh);
        src.recycle();
        return dst;
    }

    private static double cubic(double t) {
        double x = Math.abs(t);
        if (x <= 1) return 1 - 2.5 * x * x + 1.5 * x * x * x;
        if (x < 2) return 2 - 4 * x + 2.5 * x * x - 0.5 * x * x * x;
        return 0;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    @Override
    protected void onDestroy() {
        running = false;
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
