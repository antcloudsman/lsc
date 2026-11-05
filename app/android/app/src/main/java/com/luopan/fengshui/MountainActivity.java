package com.luopan.fengshui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F-06 看山脉（LC地图 新建页，2026-11-05）。
 *
 * 口径：免费开放、无门槛；数据全部来自免费源——
 *   山名点 = 天地图地名搜索「分类 260203（山）」；
 *   地形/海拔/山脊 = 本机 DEM（map.html 的 Terrarium 链路）；
 *   山脊高亮 = DEM 曲率派生（近似，不是官方山脉边界）。
 *
 * 页面 = 全屏 3D 地图（map.html）+ 天地图山名点标 + 点选详情面板。
 * AT id：lc_mountain_back/title/content/status/panel/name/desc/ridge/refresh/center/close/empty。
 */
public class MountainActivity extends LcFeatureActivity {

    private static final double DEF_LAT = 39.9042;
    private static final double DEF_LNG = 116.4074;

    /** 初始中心：原版启动器 `MountainActivity$a.b(Context, double, double)` 本来带中心点，
     *  这里读 `lc_lat`/`lc_lng`（缺失或越界才回退北京）；1:1 对齐，不是新增需求。 */
    private double startLat = DEF_LAT;
    private double startLng = DEF_LNG;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private boolean disabled;
    private boolean ridgeOn;
    /** 省码分类为空、退回视野搜索时置 true，状态栏要说明降级原因（不冒充「没数据」）。 */
    private boolean fallbackUsed;

    private WebView web;
    private TextView statusView;
    private LinearLayout panel;
    private TextView nameView;
    private TextView descView;

    private double selLat, selLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disabled = !new FeatureFlags(this).isEnabled("f06");
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
        return getString(R.string.lc_mountain_title);
    }

    @Override
    protected int backId() {
        return R.id.lc_mountain_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_mountain_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_mountain_content;
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
        web.addJavascriptInterface(new JsSink(), "LCAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                MAIN.postDelayed(() -> loadMountains(startLat, startLng), 900);
            }
        });
        web.loadUrl("file:///android_asset/map.html#in3d&uimode=map&lat=" + startLat
                + "&lng=" + startLng + "&z=9");
        content.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        /* 顶部状态条 */
        statusView = new TextView(this);
        statusView.setId(R.id.lc_mountain_status);
        statusView.setTextSize(12f);
        statusView.setTextColor(0xFFF0E0BC);
        statusView.setBackgroundColor(0xCC201A14);
        statusView.setPadding(dp(12), dp(6), dp(12), dp(6));
        statusView.setText(R.string.lc_mountain_loading);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        slp.leftMargin = dp(8);
        slp.rightMargin = dp(96);
        content.addView(statusView, slp);

        /* 右上：山脊 / 刷新 */
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setGravity(Gravity.END);
        Button ridge = new Button(this);
        ridge.setId(R.id.lc_mountain_ridge);
        ridge.setText(R.string.lc_mountain_ridge);
        ridge.setAllCaps(false);
        ridge.setTextSize(12f);
        ridge.setOnClickListener(v -> toggleRidge());
        tools.addView(ridge);
        Button refresh = new Button(this);
        refresh.setId(R.id.lc_mountain_refresh);
        refresh.setText(R.string.lc_mountain_refresh);
        refresh.setAllCaps(false);
        refresh.setTextSize(12f);
        refresh.setOnClickListener(v -> refreshFromView());
        tools.addView(refresh);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP | Gravity.END;
        tlp.topMargin = dp(8);
        tlp.rightMargin = dp(8);
        content.addView(tools, tlp);

        /* 底部详情面板 */
        panel = new LinearLayout(this);
        panel.setId(R.id.lc_mountain_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF2201A14);
        panel.setPadding(dp(14), dp(10), dp(14), dp(10));
        panel.setVisibility(View.GONE);

        nameView = new TextView(this);
        nameView.setId(R.id.lc_mountain_name);
        nameView.setTextSize(17f);
        nameView.setTextColor(0xFFF0E0BC);
        panel.addView(nameView);

        descView = new TextView(this);
        descView.setId(R.id.lc_mountain_desc);
        descView.setTextSize(13f);
        descView.setTextColor(0xFFB9A88C);
        descView.setPadding(0, dp(4), 0, dp(8));
        panel.addView(descView);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button center = new Button(this);
        center.setId(R.id.lc_mountain_center);
        center.setText(R.string.lc_mountain_center);
        center.setAllCaps(false);
        center.setTextSize(12f);
        center.setOnClickListener(v -> web.evaluateJavascript(
                "debugSetView(" + selLat + "," + selLng + ",Math.max(zoom,11),null,null)", null));
        actions.addView(center);
        Button close = new Button(this);
        close.setId(R.id.lc_mountain_close);
        close.setText(R.string.lc_mountain_close);
        close.setAllCaps(false);
        close.setTextSize(12f);
        close.setOnClickListener(v -> panel.setVisibility(View.GONE));
        actions.addView(close);
        panel.addView(actions);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.BOTTOM;
        content.addView(panel, plp);
    }

    /* ---------------- 数据 ---------------- */

    private void loadMountains(double lat, double lng) {
        statusView.setText(R.string.lc_mountain_loading);
        POOL.execute(() -> {
            String province = MapPoints.provinceCode(lat, lng);
            boolean fb = false;
            List<MapPoints.Point> pts;
            if (province == null) {
                pts = MapPoints.keywordNear("山", lat, lng, 10, 120);
                fb = true;
            } else {
                pts = MapPoints.category(MapKeys.TYPE_MOUNTAIN, province, 120);
                if (pts.isEmpty()) {
                    pts = MapPoints.keywordNear("山", lat, lng, 10, 120);
                    fb = true;
                }
            }
            final List<MapPoints.Point> out = pts;
            final boolean usedFb = fb;
            MAIN.post(() -> {
                fallbackUsed = usedFb;
                applyPoints(out);
            });
        });
    }

    private void applyPoints(List<MapPoints.Point> pts) {
        if (isFinishing() || web == null) return;
        if (pts.isEmpty()) {
            statusView.setText(R.string.lc_mountain_empty);
            web.evaluateJavascript("setOverlay('mountain', [])", null);
            return;
        }
        try {
            JSONArray arr = new JSONArray();
            for (MapPoints.Point p : pts) {
                JSONObject o = new JSONObject();
                o.put("name", p.name);
                o.put("address", p.address);
                o.put("lat", p.lat);
                o.put("lng", p.lng);
                arr.put(o);
            }
            web.evaluateJavascript("setOverlay('mountain', " + arr + ")", null);
            statusView.setText(getString(R.string.lc_mountain_count, pts.size())
                    + (fallbackUsed ? getString(R.string.lc_mountain_fallback) : ""));
        } catch (Throwable t) {
            statusView.setText(R.string.lc_mountain_fail);
        }
    }

    private void refreshFromView() {
        web.evaluateJavascript("JSON.stringify(getState())", value -> {
            double lat = startLat, lng = startLng;
            try {
                String v = value;
                if (v != null && v.length() >= 2 && v.startsWith("\"")) {
                    v = v.substring(1, v.length() - 1).replace("\\\"", "\"");
                }
                JSONObject st = new JSONObject(v);
                lat = st.optDouble("lat", startLat);
                lng = st.optDouble("lng", startLng);
            } catch (Throwable ignored) {
                // 取不到就用默认视野
            }
            loadMountains(lat, lng);
        });
    }

    private void toggleRidge() {
        ridgeOn = !ridgeOn;
        web.evaluateJavascript("setRidge(" + ridgeOn + ")", null);
    }

    /* ---------------- JS 桥 ---------------- */

    private class JsSink {
        @JavascriptInterface
        public void onEvent(String json) {
            MAIN.post(() -> handleEvent(json));
        }
    }

    private void handleEvent(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String type = o.optString("type", "");
            if ("overlay".equals(type)) {
                int n = o.optInt("count", 0);
                if (n > 0) statusView.setText(getString(R.string.lc_mountain_count, n));
            } else if ("overlay-pick".equals(type)) {
                selLat = o.optDouble("lat", 0);
                selLng = o.optDouble("lng", 0);
                nameView.setText(o.optString("name", ""));
                String addr = o.optString("address", "");
                StringBuilder sb = new StringBuilder();
                if (!addr.isEmpty()) sb.append(addr).append("\n");
                if (o.has("elev") && !o.isNull("elev")) {
                    sb.append(getString(R.string.lc_mountain_elev,
                            String.valueOf(Math.round(o.optDouble("elev", 0)))));
                } else {
                    sb.append(getString(R.string.lc_mountain_elev_unknown));
                }
                descView.setText(sb.toString());
                panel.setVisibility(View.VISIBLE);
            }
        } catch (Throwable ignored) {
            // 桥数据坏了不崩页面
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("LCAndroid");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
