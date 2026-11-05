package com.luopan.fengshui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
 * F-14 钓点（LC地图 新建页，2026-11-05）。
 *
 * 原版：私有 fish_tiles pbf + 付费钓点库（50000+ 条，会员专享）——数据不可搬运。
 * LC 免费替代（照 F-14 doc §6.2）：
 *   1) 天地图公开水域点位：河流 260204 + 湖泊 260205（分类搜索），按名称推断类型；
 *   2) 用户本机自建钓点：存 SharedPreferences（"lc_fish"），可加/删，无服务器、无 UGC 上传；
 *   3) 图层自绘：圆形水滴 + 单字（库/河/塘/黑）+ 分色 + 地名（map.html setFishLayer）。
 * 页面固定标注「非原版付费底库 · 审图号：未确认」，不照抄 GS(2026)1219。
 *
 * AT id：lc_fish_back/title/content/status/note/add/refresh/empty/close。
 */
public class FishingActivity extends LcFeatureActivity {

    private static final double DEF_LAT = 39.9042;
    private static final double DEF_LNG = 116.4074;
    private static final String PREFS = "lc_fish";
    private static final String KEY_POINTS = "points";

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private boolean disabled;
    private double startLat = DEF_LAT;
    private double startLng = DEF_LNG;

    private WebView web;
    private TextView statusView;
    private int waterCount;
    private int userCount;

    private final List<JSONObject> userPoints = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disabled = !new FeatureFlags(this).isEnabled("f14");
        startLat = LcExtras.d(getIntent(), "lc_lat", DEF_LAT);
        startLng = LcExtras.d(getIntent(), "lc_lng", DEF_LNG);
        if (Double.isNaN(startLat) || Double.isNaN(startLng)
                || startLat < -85 || startLat > 85 || startLng < -180 || startLng > 180) {
            startLat = DEF_LAT;
            startLng = DEF_LNG;
        }
        loadUserPoints();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String title() {
        return getString(R.string.lc_fish_title);
    }

    @Override
    protected int backId() {
        return R.id.lc_fish_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_fish_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_fish_content;
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
                view.evaluateJavascript("setBase('img');debugSetView(" + startLat + ","
                        + startLng + ",11,null,null);", null);
                loadWater(startLat, startLng);
            }
        });
        web.loadUrl("file:///android_asset/map.html#uimode=map");
        content.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusView = new TextView(this);
        statusView.setId(R.id.lc_fish_status);
        statusView.setTextSize(12f);
        statusView.setTextColor(0xFFF0E0BC);
        statusView.setBackgroundColor(0xCC201A14);
        statusView.setPadding(dp(12), dp(6), dp(12), dp(6));
        statusView.setText(R.string.lc_fish_loading);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        slp.leftMargin = dp(8);
        slp.rightMargin = dp(120);
        content.addView(statusView, slp);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        Button add = new Button(this);
        add.setId(R.id.lc_fish_add);
        add.setText(R.string.lc_fish_add);
        add.setAllCaps(false);
        add.setTextSize(12f);
        add.setOnClickListener(v -> addUserPoint());
        tools.addView(add);
        Button refresh = new Button(this);
        refresh.setId(R.id.lc_fish_refresh);
        refresh.setText(R.string.lc_fish_refresh);
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

        TextView note = new TextView(this);
        note.setId(R.id.lc_fish_note);
        note.setTextSize(10f);
        note.setTextColor(0xFFB9A88C);
        note.setBackgroundColor(0xCC201A14);
        note.setPadding(dp(10), dp(5), dp(10), dp(5));
        note.setText(R.string.lc_fish_note);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.gravity = Gravity.BOTTOM;
        content.addView(note, nlp);
    }

    /* ---------------- 数据 ---------------- */

    private void loadWater(double lat, double lng) {
        statusView.setText(R.string.lc_fish_loading);
        POOL.execute(() -> {
            String province = MapPoints.provinceCode(lat, lng);
            List<MapPoints.Point> pts = new ArrayList<>();
            if (province != null) {
                pts.addAll(MapPoints.category(MapKeys.TYPE_LAKE, province, 40));
                pts.addAll(MapPoints.category(MapKeys.TYPE_RIVER, province, 40));
            }
            if (pts.isEmpty()) {
                pts.addAll(MapPoints.keywordNear("湖", lat, lng, 10, 40));
                pts.addAll(MapPoints.keywordNear("河", lat, lng, 10, 40));
            }
            final List<MapPoints.Point> out = pts;
            MAIN.post(() -> apply(out));
        });
    }

    private void apply(List<MapPoints.Point> water) {
        if (isFinishing() || web == null) return;
        waterCount = water.size();
        userCount = userPoints.size();
        try {
            JSONArray arr = new JSONArray();
            for (MapPoints.Point p : water) {
                JSONObject o = new JSONObject();
                o.put("name", p.name);
                o.put("type", guessType(p.name));
                o.put("lat", p.lat);
                o.put("lng", p.lng);
                arr.put(o);
            }
            for (JSONObject u : userPoints) arr.put(u);
            web.evaluateJavascript("setFishLayer(true, " + arr + ")", null);
            statusView.setText(getString(R.string.lc_fish_count, waterCount, userCount));
        } catch (Throwable t) {
            statusView.setText(R.string.lc_fish_empty);
        }
    }

    private static String guessType(String name) {
        if (name == null) return "塘";
        if (name.contains("水库") || name.contains("库")) return "库";
        if (name.contains("河") || name.contains("江") || name.contains("溪")) return "河";
        if (name.contains("塘") || name.contains("湖") || name.contains("潭")) return "塘";
        return "塘";
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
                // 用默认视野
            }
            loadWater(lat, lng);
        });
    }

    /* ---------------- 自建钓点 ---------------- */

    private void loadUserPoints() {
        userPoints.clear();
        try {
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_POINTS, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) userPoints.add(o);
            }
        } catch (Throwable ignored) {
            // 坏数据当空
        }
    }

    private void saveUserPoints() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_POINTS, new JSONArray(userPoints).toString()).apply();
    }

    private void addUserPoint() {
        web.evaluateJavascript("JSON.stringify(getState())", value -> {
            try {
                String v = value;
                if (v != null && v.length() >= 2 && v.startsWith("\"")) {
                    v = v.substring(1, v.length() - 1).replace("\\\"", "\"");
                }
                JSONObject st = new JSONObject(v);
                double lat = st.optDouble("lat", Double.NaN);
                double lng = st.optDouble("lng", Double.NaN);
                if (Double.isNaN(lat)) {
                    Toast.makeText(this, R.string.lc_fish_need_view, Toast.LENGTH_SHORT).show();
                    return;
                }
                JSONObject o = new JSONObject();
                o.put("name", "自建钓点" + (userPoints.size() + 1));
                o.put("type", "塘");
                o.put("lat", lat);
                o.put("lng", lng);
                o.put("user", true);
                userPoints.add(o);
                saveUserPoints();
                Toast.makeText(this, getString(R.string.lc_fish_added, o.optString("name")),
                        Toast.LENGTH_SHORT).show();
                refreshFromView();
            } catch (Throwable t) {
                Toast.makeText(this, R.string.lc_fish_need_view, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /* ---------------- JS 桥 ---------------- */

    private class JsSink {
        @JavascriptInterface
        public void onEvent(String json) {
            MAIN.post(() -> handle(json));
        }
    }

    private void handle(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String type = o.optString("type", "");
            if ("fish".equals(type)) {
                int n = o.optInt("count", 0);
                if (n > 0) statusView.setText(getString(R.string.lc_fish_count, waterCount, userCount));
            } else if ("fish-pick".equals(type)) {
                showDetail(o);
            }
        } catch (Throwable ignored) {
            // 桥数据坏了不崩
        }
    }

    private void showDetail(JSONObject o) {
        final String name = o.optString("name", "");
        final double lat = o.optDouble("lat", 0);
        final double lng = o.optDouble("lng", 0);
        final String kind = o.optString("kind", "");
        boolean isUserFlag = false;
        for (JSONObject u : userPoints) {
            if (name.equals(u.optString("name"))
                    && Math.abs(u.optDouble("lat") - lat) < 1e-7
                    && Math.abs(u.optDouble("lng") - lng) < 1e-7) {
                isUserFlag = true;
                break;
            }
        }
        final boolean isUser = isUserFlag;

        LinearLayout panel = new LinearLayout(this);
        panel.setId(R.id.lc_fish_detail_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(10), dp(18), dp(8));

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(17f);
        title.setTextColor(Color.rgb(235, 228, 215));
        panel.addView(title);

        TextView badge = new TextView(this);
        badge.setId(R.id.lc_fish_detail_type);
        badge.setText(badgeText(kind) + " · " + (isUser
                ? getString(R.string.lc_fish_source_user)
                : getString(R.string.lc_fish_source_public)));
        badge.setTextSize(12f);
        badge.setTextColor(Color.rgb(220, 190, 130));
        badge.setPadding(0, dp(4), 0, dp(6));
        panel.addView(badge);

        final TextView address = new TextView(this);
        address.setId(R.id.lc_fish_detail_address);
        address.setText(isUser ? getString(R.string.lc_fish_source_user)
                : getString(R.string.lc_fish_source_public));
        address.setTextSize(12f);
        address.setTextColor(Color.rgb(180, 190, 200));
        address.setPadding(0, 0, 0, dp(8));
        panel.addView(address);

        // 天气/图片：桥事件暂无这两个字段，取不到就不显示（不造假）。
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button save = new Button(this);
        save.setId(R.id.lc_fish_detail_save);
        save.setText(R.string.lc_fish_detail_save);
        Button nav = new Button(this);
        nav.setId(R.id.lc_fish_detail_nav);
        nav.setText(R.string.lc_fish_detail_nav);
        Button close = new Button(this);
        close.setId(R.id.lc_fish_detail_close);
        close.setText(R.string.lc_fish_close);
        actions.addView(save, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(nav, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(close, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(actions);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(panel)
                .create();
        close.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            dialog.dismiss();
            Intent i = new Intent(this, SaveInterestActivity.class);
            i.putExtra(MapJump.EXTRA_LAT, lat);
            i.putExtra(MapJump.EXTRA_LNG, lng);
            i.putExtra(SaveInterestActivity.EXTRA_SITTING, 180.0);
            i.putExtra(SaveInterestActivity.EXTRA_FACING, 0.0);
            startActivity(i);
        });
        nav.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(MapJump.intent(this, lat, lng));
        });
        dialog.show();

        // 「距我」用当前地图中心近似（桥事件不含用户位置）；只算距离，不改事实文案。
        if (web != null) {
            web.evaluateJavascript("JSON.stringify(getState())", value -> {
                try {
                    String s = value;
                    if (s != null && s.startsWith("\"") && s.endsWith("\"")) {
                        s = s.substring(1, s.length() - 1).replace("\\\"", "\"");
                    }
                    JSONObject st = new JSONObject(s);
                    double d = TrackStats.haversine(st.optDouble("lat", lat),
                            st.optDouble("lng", lng), lat, lng);
                    address.setText((isUser ? getString(R.string.lc_fish_source_user)
                            : getString(R.string.lc_fish_source_public))
                            + " · " + getString(R.string.lc_fish_distance,
                            String.format(java.util.Locale.US, "%.2f", d / 1000.0)));
                } catch (Throwable ignored) {
                }
            });
        }
    }

    /** 类型徽标映射（原版码 303 库钓 / 308 河钓 / 309 黑坑 / 其它塘钓）。 */
    private static String badgeText(String kind) {
        if (kind == null) return "塘钓";
        if (kind.contains("黑")) return "黑坑(309)";
        if (kind.contains("库")) return "库钓(303)";
        if (kind.contains("河") || kind.contains("江") || kind.contains("溪")) return "河钓(308)";
        return "塘钓";
    }

    private void removeUserPoint(String name, double lat, double lng) {
        for (int i = userPoints.size() - 1; i >= 0; i--) {
            JSONObject u = userPoints.get(i);
            if (name.equals(u.optString("name"))
                    && Math.abs(u.optDouble("lat") - lat) < 1e-7
                    && Math.abs(u.optDouble("lng") - lng) < 1e-7) {
                userPoints.remove(i);
            }
        }
        saveUserPoints();
        Toast.makeText(this, R.string.lc_fish_removed, Toast.LENGTH_SHORT).show();
        refreshFromView();
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

