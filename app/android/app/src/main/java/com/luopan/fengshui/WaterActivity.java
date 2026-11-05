package com.luopan.fengshui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F-07 看水系（LC地图 新建页，2026-11-05）。
 *
 * 免费数据：天地图分类搜索「河流 260204 / 湖泊 260205」→ 河湖名称点；
 *   底图用矢量/地形，图面本身有水系（栅格）。
 * **线/面缺口照实写**：天地图公开服务没有水系矢量图层，页面底部固定标注；
 *   不画假河道、不伪造面积。
 *
 * AT id：lc_water_back/title/content/status/panel/name/desc/refresh/center/close/empty/gap。
 */
public class WaterActivity extends LcFeatureActivity {

    private static final double DEF_LAT = 39.9042;
    private static final double DEF_LNG = 116.4074;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private boolean disabled;

    private WebView web;
    private TextView statusView;
    private TextView gapView;
    private LinearLayout panel;
    private TextView nameView;
    private TextView descView;

    private double selLat, selLng;
    private Location lastLoc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disabled = !new FeatureFlags(this).isEnabled("f07");
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String title() {
        return getString(R.string.lc_water_title);
    }

    @Override
    protected int backId() {
        return R.id.lc_water_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_water_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_water_content;
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
                MAIN.postDelayed(() -> loadWater(DEF_LAT, DEF_LNG), 900);
            }
        });
        web.loadUrl("file:///android_asset/map.html#in3d&uimode=map&lat=" + DEF_LAT
                + "&lng=" + DEF_LNG + "&z=9");
        content.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusView = new TextView(this);
        statusView.setId(R.id.lc_water_status);
        statusView.setTextSize(12f);
        statusView.setTextColor(0xFFF0E0BC);
        statusView.setBackgroundColor(0xCC201A14);
        statusView.setPadding(dp(12), dp(6), dp(12), dp(6));
        statusView.setText(R.string.lc_water_loading);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        slp.leftMargin = dp(8);
        slp.rightMargin = dp(96);
        content.addView(statusView, slp);

        Button refresh = new Button(this);
        refresh.setId(R.id.lc_water_refresh);
        refresh.setText(R.string.lc_water_refresh);
        refresh.setAllCaps(false);
        refresh.setTextSize(12f);
        refresh.setOnClickListener(v -> refreshFromView());
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP | Gravity.END;
        tlp.topMargin = dp(8);
        tlp.rightMargin = dp(8);
        content.addView(refresh, tlp);

        gapView = new TextView(this);
        gapView.setId(R.id.lc_water_gap);
        gapView.setTextSize(11f);
        gapView.setTextColor(0xFFB9A88C);
        gapView.setBackgroundColor(0xCC201A14);
        gapView.setPadding(dp(12), dp(6), dp(12), dp(6));
        gapView.setText(R.string.lc_water_gap);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.gravity = Gravity.BOTTOM;
        glp.bottomMargin = dp(4);
        content.addView(gapView, glp);

        panel = new LinearLayout(this);
        panel.setId(R.id.lc_water_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF2201A14);
        panel.setPadding(dp(14), dp(10), dp(14), dp(10));
        panel.setVisibility(View.GONE);

        nameView = new TextView(this);
        nameView.setId(R.id.lc_water_name);
        nameView.setTextSize(17f);
        nameView.setTextColor(0xFFF0E0BC);
        panel.addView(nameView);

        descView = new TextView(this);
        descView.setId(R.id.lc_water_desc);
        descView.setTextSize(13f);
        descView.setTextColor(0xFFB9A88C);
        descView.setPadding(0, dp(4), 0, dp(8));
        panel.addView(descView);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button center = new Button(this);
        center.setId(R.id.lc_water_center);
        center.setText(R.string.lc_water_center);
        center.setAllCaps(false);
        center.setTextSize(12f);
        center.setOnClickListener(v -> web.evaluateJavascript(
                "debugSetView(" + selLat + "," + selLng + ",Math.max(zoom,11),null,null)", null));
        actions.addView(center);
        Button close = new Button(this);
        close.setId(R.id.lc_water_close);
        close.setText(R.string.lc_water_close);
        close.setAllCaps(false);
        close.setTextSize(12f);
        close.setOnClickListener(v -> panel.setVisibility(View.GONE));
        actions.addView(close);
        panel.addView(actions);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.BOTTOM;
        plp.bottomMargin = dp(40);
        content.addView(panel, plp);
    }

    /* ---------------- 数据 ---------------- */

    private void loadWater(double lat, double lng) {
        statusView.setText(R.string.lc_water_loading);
        lastLoc = lastLocation();
        POOL.execute(() -> {
            String province = MapPoints.provinceCode(lat, lng);
            List<MapPoints.Point> pts = new ArrayList<>();
            if (province != null) {
                /* 实测：dataTypes 用逗号传多值会返回空（"260204,260205" → count=1 无 pois），
                 * 所以河流、湖泊各查一次再合并。 */
                pts.addAll(MapPoints.category(MapKeys.TYPE_RIVER, province, 80));
                pts.addAll(MapPoints.category(MapKeys.TYPE_LAKE, province, 40));
            }
            if (pts.isEmpty()) pts = keywordBoth(lat, lng);
            final List<MapPoints.Point> out = pts;
            MAIN.post(() -> applyPoints(out));
        });
    }

    private static List<MapPoints.Point> keywordBoth(double lat, double lng) {
        List<MapPoints.Point> all = new ArrayList<>();
        all.addAll(MapPoints.keywordNear("河", lat, lng, 10, 80));
        all.addAll(MapPoints.keywordNear("湖", lat, lng, 10, 80));
        return all;
    }

    private void applyPoints(List<MapPoints.Point> pts) {
        if (isFinishing() || web == null) return;
        if (pts.isEmpty()) {
            statusView.setText(R.string.lc_water_empty);
            web.evaluateJavascript("setOverlay('water', [])", null);
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
            web.evaluateJavascript("setOverlay('water', " + arr + ")", null);
            statusView.setText(getString(R.string.lc_water_count, pts.size()));
        } catch (Throwable t) {
            statusView.setText(R.string.lc_water_fail);
        }
    }

    private void refreshFromView() {
        web.evaluateJavascript("JSON.stringify(getState())", value -> {
            double lat = DEF_LAT, lng = DEF_LNG;
            try {
                String v = value;
                if (v != null && v.length() >= 2 && v.startsWith("\"")) {
                    v = v.substring(1, v.length() - 1).replace("\\\"", "\"");
                }
                JSONObject st = new JSONObject(v);
                lat = st.optDouble("lat", DEF_LAT);
                lng = st.optDouble("lng", DEF_LNG);
            } catch (Throwable ignored) {
                // 取不到就用默认视野
            }
            loadWater(lat, lng);
        });
    }

    private Location lastLocation() {
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return null;
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps == null) return net;
            if (net == null) return gps;
            return gps.getTime() >= net.getTime() ? gps : net;
        } catch (Throwable t) {
            return null;
        }
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
                if (n > 0) statusView.setText(getString(R.string.lc_water_count, n));
            } else if ("overlay-pick".equals(type)) {
                selLat = o.optDouble("lat", 0);
                selLng = o.optDouble("lng", 0);
                nameView.setText(o.optString("name", ""));
                StringBuilder sb = new StringBuilder();
                String addr = o.optString("address", "");
                if (!addr.isEmpty()) sb.append(addr).append("\n");
                if (o.has("elev") && !o.isNull("elev")) {
                    sb.append(getString(R.string.lc_water_elev,
                            String.valueOf(Math.round(o.optDouble("elev", 0)))));
                } else {
                    sb.append(getString(R.string.lc_water_elev_unknown));
                }
                if (lastLoc != null) {
                    double km = MapPoints.distanceKm(lastLoc.getLatitude(), lastLoc.getLongitude(),
                            selLat, selLng);
                    sb.append(" · ").append(getString(R.string.lc_water_distance,
                            String.format(java.util.Locale.US, "%.1f", km)));
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
