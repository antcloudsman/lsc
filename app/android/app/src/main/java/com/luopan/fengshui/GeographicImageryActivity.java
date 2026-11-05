package com.luopan.fengshui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F-16 地理影像（LC地图 新建页，2026-11-05）。
 *
 * 原版是自建地理摄影库（含摄影署名，会员专享）；LC 用**公开图库替代**：
 *   Bing 每日壁纸 `https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=zh-CN`
 *   （2026-11-05 实测 200，含 startdate / urlbase / copyright，署名在 copyright 里）。
 * 免费、无 key、无门槛；页面保留「署名」一行；Bing 不提供坐标，所以「地图」按钮置灰标注「无坐标」。
 * 下载保存到应用私有目录（getExternalFilesDir），不申请存储权限；分享走系统文本分享。
 *
 * AT id：lc_geo_back/title/content/status/date/image/title_text/credit/prev/next/download/share/map。
 */
public class GeographicImageryActivity extends LcFeatureActivity {

    private static final String API =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=zh-CN";

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private boolean disabled;

    private static final class Item {
        String date = "";
        String url = "";
        String title = "";
        String credit = "";
    }

    private final List<Item> items = new ArrayList<>();
    private int index;
    private Bitmap current;

    private TextView statusView;
    private TextView dateView;
    private TextView titleView;
    private TextView creditView;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disabled = !new FeatureFlags(this).isEnabled("f16");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String title() {
        return getString(R.string.lc_geo_title);
    }

    @Override
    protected int backId() {
        return R.id.lc_geo_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_geo_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_geo_content;
    }

    @Override
    protected void onPageReady(FrameLayout content) {
        if (disabled) {
            Toast.makeText(this, R.string.lc_weather_disabled, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.lc_setting_bg));
        root.setPadding(dp(12), dp(8), dp(12), dp(16));

        dateView = new TextView(this);
        dateView.setId(R.id.lc_geo_date);
        dateView.setTextSize(22f);
        dateView.setTextColor(getColor(R.color.lc_setting_text));
        root.addView(dateView);

        statusView = new TextView(this);
        statusView.setId(R.id.lc_geo_status);
        statusView.setTextSize(11f);
        statusView.setTextColor(getColor(R.color.lc_label_gray));
        statusView.setPadding(0, dp(2), 0, dp(8));
        statusView.setText(R.string.lc_geo_loading);
        root.addView(statusView);

        imageView = new ImageView(this);
        imageView.setId(R.id.lc_geo_image);
        imageView.setAdjustViewBounds(true);
        imageView.setBackgroundColor(0xFF111111);
        root.addView(imageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

        titleView = new TextView(this);
        titleView.setId(R.id.lc_geo_title_text);
        titleView.setTextSize(16f);
        titleView.setTextColor(getColor(R.color.lc_setting_text));
        titleView.setPadding(0, dp(10), 0, dp(2));
        root.addView(titleView);

        creditView = new TextView(this);
        creditView.setId(R.id.lc_geo_credit);
        creditView.setTextSize(12f);
        creditView.setTextColor(getColor(R.color.lc_label_gray));
        creditView.setPadding(0, 0, 0, dp(8));
        root.addView(creditView);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = new Button(this);
        prev.setId(R.id.lc_geo_prev);
        prev.setText("‹");
        prev.setOnClickListener(v -> move(-1));
        row1.addView(prev);
        Button next = new Button(this);
        next.setId(R.id.lc_geo_next);
        next.setText("›");
        next.setOnClickListener(v -> move(1));
        row1.addView(next);
        Button today = new Button(this);
        today.setText(R.string.lc_geo_today);
        today.setAllCaps(false);
        today.setOnClickListener(v -> {
            index = 0;
            render();
        });
        row1.addView(today);
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button download = new Button(this);
        download.setId(R.id.lc_geo_download);
        download.setText(R.string.lc_geo_download);
        download.setAllCaps(false);
        download.setOnClickListener(v -> download());
        row2.addView(download);
        Button share = new Button(this);
        share.setId(R.id.lc_geo_share);
        share.setText(R.string.lc_geo_share);
        share.setAllCaps(false);
        share.setOnClickListener(v -> share());
        row2.addView(share);
        Button map = new Button(this);
        map.setId(R.id.lc_geo_map);
        map.setText(R.string.lc_geo_map);
        map.setAllCaps(false);
        map.setEnabled(false);
        row2.addView(map);
        root.addView(row2);

        TextView source = new TextView(this);
        source.setText(R.string.lc_geo_source);
        source.setTextSize(11f);
        source.setTextColor(getColor(R.color.lc_label_gray));
        source.setPadding(0, dp(10), 0, 0);
        root.addView(source);

        content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loadList();
    }

    private void loadList() {
        statusView.setText(R.string.lc_geo_loading);
        POOL.execute(() -> {
            List<Item> list = new ArrayList<>();
            String err = null;
            try {
                JSONObject root = new JSONObject(LcHttp.get(API));
                JSONArray arr = root.optJSONArray("images");
                for (int i = 0; arr != null && i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    Item it = new Item();
                    String d = o.optString("startdate", "");
                    if (d.length() == 8) it.date = d.substring(0, 4) + "/" + d.substring(4, 6)
                            + "/" + d.substring(6, 8);
                    it.url = "https://www.bing.com" + o.optString("urlbase", "") + "_1920x1080.jpg";
                    String copy = o.optString("copyright", "");
                    int p = copy.indexOf(" (");
                    if (p > 0) {
                        it.title = copy.substring(0, p).trim();
                        it.credit = copy.substring(p + 2).replace(")", "").trim();
                    } else {
                        it.title = copy;
                        it.credit = "";
                    }
                    list.add(it);
                }
            } catch (Throwable t) {
                err = "fail";
            }
            final List<Item> out = list;
            final String e = err;
            MAIN.post(() -> {
                items.clear();
                items.addAll(out);
                if (items.isEmpty()) {
                    statusView.setText(R.string.lc_geo_fail);
                } else {
                    statusView.setText(R.string.lc_geo_source);
                    render();
                }
            });
        });
    }

    private void move(int delta) {
        int next = index + delta;
        if (next < 0 || next >= items.size()) {
            Toast.makeText(this, R.string.lc_geo_no_more, Toast.LENGTH_SHORT).show();
            return;
        }
        index = next;
        render();
    }

    private void render() {
        if (items.isEmpty()) return;
        Item it = items.get(Math.max(0, Math.min(index, items.size() - 1)));
        dateView.setText(it.date);
        titleView.setText(it.title);
        creditView.setText(it.credit);
        final String url = it.url;
        POOL.execute(() -> {
            Bitmap bmp = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", "Luopan/1.0 (Android)");
                InputStream in = c.getInputStream();
                bmp = BitmapFactory.decodeStream(in);
                in.close();
                c.disconnect();
            } catch (Throwable ignored) {
                // 保持旧图
            }
            final Bitmap b = bmp;
            MAIN.post(() -> {
                if (b == null) return;
                if (current != null && !current.isRecycled()) current.recycle();
                current = b;
                imageView.setImageBitmap(b);
            });
        });
    }

    private void download() {
        if (current == null) {
            Toast.makeText(this, R.string.lc_geo_save_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(getExternalFilesDir(null), "geo");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "geo_" + items.get(index).date.replace("/", "") + ".png");
            FileOutputStream out = new FileOutputStream(f);
            current.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            Toast.makeText(this, getString(R.string.lc_geo_saved, f.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, R.string.lc_geo_save_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        if (items.isEmpty()) return;
        Item it = items.get(index);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, getString(R.string.lc_geo_share_text, it.date, it.credit));
        startActivity(Intent.createChooser(i, getString(R.string.lc_geo_share)));
    }

    @Override
    protected void onDestroy() {
        if (current != null && !current.isRecycled()) current.recycle();
        super.onDestroy();
    }
}
