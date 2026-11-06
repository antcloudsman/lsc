package com.luopan.fengshui;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F-05 数据叠加（LC地图 新建页，2026-11-05）。
 *
 * 原版：`MainActivity` 内浮层「数据叠加」，四页签（行政区域 / 地形地貌 / 水文水系 / 自然保护），
 *       图层来自 21at / tile.xinzhi.space 私有瓦片。**不照搬**。
 * LC：只对得上公开源的图层照做，其余置灰并写原因（不建空壳、不放假图层）。
 *   - 点位类：天地图分类搜索（河 260204 / 湖 260205 / 公园 180304,180305 /
 *     风景名胜 180400 / 港口码头 230108 / 机场 230109），走 map.html setOverlayLayer；
 *   - 内置类：路网（F-04 vec）、等高线（DEM）、山脊（DEM 曲率）、地形晕渲（ter 底图）；
 *   - 每层独立开关 + 点位层透明度 0–1；状态存 SharedPreferences "lc_overlays"。
 * 底部常显数据来源与许可；不出现会员角标/付费门。
 *
 * AT id：lc_overlay_back/title/content/tabs/list/note/all_on/all_off +
 *        lc_overlay_switch_<id>、lc_overlay_opacity_<id>。
 */
public class OverlayActivity extends LcFeatureActivity {

    private static final String PREFS = "lc_overlays";
    private static final double DEF_LAT = 39.9042;
    private static final double DEF_LNG = 116.4074;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final class Layer {
        final String id;
        final int name;
        final int tab;              // 0 行政 1 地形 2 水文 3 自然
        final boolean available;
        final int reason;
        final int[] cats;           // 点位分类码（空=非点位层）
        final String builtin;       // road / contour / ridge / terrain（空=点位层）
        final int switchId;
        final int opacityId;

        Layer(String id, int name, int tab, boolean available, int reason,
              int[] cats, String builtin, int switchId, int opacityId) {
            this.id = id;
            this.name = name;
            this.tab = tab;
            this.available = available;
            this.reason = reason;
            this.cats = cats;
            this.builtin = builtin;
            this.switchId = switchId;
            this.opacityId = opacityId;
        }
    }

    private static final Layer[] LAYERS = {
            new Layer("admin_face", R.string.lc_overlay_admin_face, 0, false,
                    R.string.lc_overlay_no_admin, null, null, R.id.lc_overlay_switch_admin_face, 0),
            new Layer("admin_line", R.string.lc_overlay_admin_line, 0, false,
                    R.string.lc_overlay_no_admin, null, null, R.id.lc_overlay_switch_admin_line, 0),
            new Layer("road", R.string.lc_overlay_road, 0, true, 0, null, "road",
                    R.id.lc_overlay_switch_road, R.id.lc_overlay_opacity_road),
            new Layer("railway", R.string.lc_overlay_railway, 0, false,
                    R.string.lc_overlay_no_rail, null, null, R.id.lc_overlay_switch_railway, 0),

            new Layer("contour", R.string.lc_overlay_contour, 1, true, 0, null, "contour",
                    R.id.lc_overlay_switch_contour, R.id.lc_overlay_opacity_contour),
            new Layer("ridge", R.string.lc_overlay_ridge, 1, true, 0, null, "ridge",
                    R.id.lc_overlay_switch_ridge, R.id.lc_overlay_opacity_ridge),
            new Layer("terrain", R.string.lc_overlay_terrain, 1, true, 0, null, "terrain",
                    R.id.lc_overlay_switch_terrain, R.id.lc_overlay_opacity_terrain),

            new Layer("river", R.string.lc_overlay_river, 2, true, 0,
                    new int[]{260204}, null, R.id.lc_overlay_switch_river, R.id.lc_overlay_opacity_river),
            new Layer("lake", R.string.lc_overlay_lake, 2, true, 0,
                    new int[]{260205}, null, R.id.lc_overlay_switch_lake, R.id.lc_overlay_opacity_lake),
            new Layer("water_line", R.string.lc_overlay_water_line, 2, false,
                    R.string.lc_overlay_no_water_line, null, null, R.id.lc_overlay_switch_water_line, 0),

            new Layer("park", R.string.lc_overlay_park, 3, true, 0,
                    new int[]{180304, 180305}, null, R.id.lc_overlay_switch_park, R.id.lc_overlay_opacity_park),
            new Layer("scenic", R.string.lc_overlay_scenic, 3, true, 0,
                    new int[]{180400}, null, R.id.lc_overlay_switch_scenic, R.id.lc_overlay_opacity_scenic),
            new Layer("port", R.string.lc_overlay_port, 3, true, 0,
                    new int[]{230108}, null, R.id.lc_overlay_switch_port, R.id.lc_overlay_opacity_port),
            new Layer("airport", R.string.lc_overlay_airport, 3, true, 0,
                    new int[]{230109}, null, R.id.lc_overlay_switch_airport, R.id.lc_overlay_opacity_airport),
            new Layer("heritage", R.string.lc_overlay_heritage, 3, false,
                    R.string.lc_overlay_no_heritage, null, null, R.id.lc_overlay_switch_heritage, 0),
            new Layer("volcano", R.string.lc_overlay_volcano, 3, false,
                    R.string.lc_overlay_no_volcano, null, null, R.id.lc_overlay_switch_volcano, 0),
            new Layer("vegetation", R.string.lc_overlay_vegetation, 3, false,
                    R.string.lc_overlay_no_vegetation, null, null, R.id.lc_overlay_switch_vegetation, 0),
            new Layer("silk_road", R.string.lc_overlay_silk_road, 3, false,
                    R.string.lc_overlay_no_silk, null, null, R.id.lc_overlay_switch_silk_road, 0),
            new Layer("ten_line", R.string.lc_overlay_ten_line, 3, false,
                    R.string.lc_overlay_no_ten, null, null, R.id.lc_overlay_switch_ten_line, 0)
    };

    private boolean disabled;
    private double startLat = DEF_LAT;
    private double startLng = DEF_LNG;
    private int tab;

    private WebView web;
    private LinearLayout listBox;
    private final Map<String, List<MapPoints.Point>> cache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disabled = !new FeatureFlags(this).isEnabled("f05");
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
        return getString(R.string.lc_overlay_title);
    }

    @Override
    protected int backId() {
        return R.id.lc_overlay_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_overlay_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_overlay_content;
    }

    @SuppressLint("SetJavaScriptEnabled")
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
                restoreAll();
            }
        });
        web.loadUrl(MapAsset.url("#in3d&uimode=map&lat=" + startLat
                + "&lng=" + startLng + "&z=11"));
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setId(R.id.lc_overlay_tabs);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        int[] tabNames = {R.string.lc_overlay_tab_admin, R.string.lc_overlay_tab_terrain,
                R.string.lc_overlay_tab_water, R.string.lc_overlay_tab_nature};
        for (int i = 0; i < tabNames.length; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(tabNames[i]);
            b.setAllCaps(false);
            b.setTextSize(12f);
            b.setOnClickListener(v -> selectTab(idx));
            tabs.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setId(R.id.lc_overlay_list);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(12), dp(6), dp(12), dp(6));
        scroll.addView(listBox);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(12), dp(6), dp(12), dp(6));
        Button on = new Button(this);
        on.setId(R.id.lc_overlay_all_on);
        on.setText(R.string.lc_overlay_all_on);
        on.setAllCaps(false);
        on.setTextSize(12f);
        on.setOnClickListener(v -> setAll(true));
        bottom.addView(on);
        Button off = new Button(this);
        off.setId(R.id.lc_overlay_all_off);
        off.setText(R.string.lc_overlay_all_off);
        off.setAllCaps(false);
        off.setTextSize(12f);
        off.setOnClickListener(v -> setAll(false));
        bottom.addView(off);
        root.addView(bottom);

        TextView note = new TextView(this);
        note.setId(R.id.lc_overlay_note);
        note.setTextSize(10f);
        note.setTextColor(getColor(R.color.lc_label_gray));
        note.setPadding(dp(12), 0, dp(12), dp(8));
        note.setText(R.string.lc_overlay_note);
        root.addView(note);

        content.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        selectTab(0);
    }

    /* ---------------- UI ---------------- */

    private void selectTab(int idx) {
        tab = idx;
        renderTab();
    }

    private void renderTab() {
        listBox.removeAllViews();
        if (tab == 0) listBox.addView(buildAdminFilterRow());
        for (Layer l : LAYERS) {
            if (l.tab != tab) continue;
            listBox.addView(buildRow(l));
        }
    }

    /** R2-029：「行政区域筛选」入口——设定省码后，点位图层按该省检索；未设则跟随当前视野省份。 */
    private View buildAdminFilterRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(8));
        TextView tv = new TextView(this);
        tv.setId(R.id.lc_overlay_admin_filter);
        tv.setTextSize(12f);
        tv.setTextColor(getColor(R.color.lc_setting_text));
        String code = prefs().getString("admin_code", "");
        tv.setText(code == null || code.isEmpty()
                ? "行政区域筛选：跟随当前视野省份"
                : "行政区域筛选：省码 " + code);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button set = new Button(this);
        set.setId(R.id.lc_overlay_admin_filter_set);
        set.setText("设置");
        set.setAllCaps(false);
        set.setTextSize(11f);
        set.setOnClickListener(v -> showAdminFilterDialog());
        row.addView(set);
        return row;
    }

    private void showAdminFilterDialog() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("省份名（如 北京市）或 9 位省码");
        String cur = prefs().getString("admin_code", "");
        if (cur != null) et.setText(cur);
        new android.app.AlertDialog.Builder(this)
                .setTitle("行政区域筛选")
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    String v = et.getText().toString().trim();
                    if (v.isEmpty()) {
                        prefs().edit().remove("admin_code").apply();
                        reloadPointLayers();
                        return;
                    }
                    if (v.matches("\\d{9}")) {
                        saveAdminCode(v);
                        return;
                    }
                    new Thread(() -> {
                        String code = MapPoints.resolveProvince9(v, startLat, startLng);
                        runOnUiThread(() -> {
                            if (code == null || code.isEmpty()) {
                                Toast.makeText(this, "未解析出省码：" + v + "（未改动筛选）",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                saveAdminCode(code);
                            }
                        });
                    }).start();
                })
                .setNegativeButton("清除", (d, w) -> {
                    prefs().edit().remove("admin_code").apply();
                    reloadPointLayers();
                })
                .show();
    }

    private void saveAdminCode(String code) {
        prefs().edit().putString("admin_code", code).apply();
        Toast.makeText(this, "行政区域筛选：" + code, Toast.LENGTH_SHORT).show();
        renderTab();
        reloadPointLayers();
    }

    /** 已打开的点位图层按新省码重查；内置层（路网/等高线/山脊/地形）不受影响。 */
    private void reloadPointLayers() {
        renderTab();
        for (Layer l : LAYERS) {
            if (l.tab != tab || !l.available || l.cats == null) continue;
            if (!prefs().getBoolean("on_" + l.id, false)) continue;
            loadPoints(l);
        }
    }

    private View buildRow(Layer l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = new TextView(this);
        name.setText(l.name);
        name.setTextSize(15f);
        name.setTextColor(l.available ? getColor(R.color.lc_setting_text)
                : getColor(R.color.lc_label_gray));
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (l.available) {
            Switch sw = new Switch(this);
            sw.setId(l.switchId);
            sw.setChecked(prefs().getBoolean("on_" + l.id, false));
            sw.setOnCheckedChangeListener((b, checked) -> apply(l, checked));
            top.addView(sw);
        } else {
            // R2-029：无数据的行给一个显式禁用开关（enabled=false），不是「看起来能点」的空壳
            Switch off = new Switch(this);
            off.setId(l.switchId);
            off.setChecked(false);
            off.setEnabled(false);
            top.addView(off);
            TextView no = new TextView(this);
            no.setText("无数据");
            no.setTextSize(12f);
            no.setTextColor(getColor(R.color.lc_label_gray));
            no.setPadding(dp(6), 0, 0, 0);
            top.addView(no);
        }
        row.addView(top);

        if (!l.available) {
            TextView why = new TextView(this);
            why.setText(l.reason);
            why.setTextSize(11f);
            why.setTextColor(getColor(R.color.lc_label_gray));
            why.setPadding(0, dp(2), 0, 0);
            row.addView(why);
        } else if (l.cats != null) {
            SeekBar sb = new SeekBar(this);
            sb.setId(l.opacityId);
            sb.setMax(100);
            sb.setProgress(prefs().getInt("op_" + l.id, 100));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    pf().edit().putInt("op_" + l.id, progress).apply();
                    if (fromUser) web.evaluateJavascript(
                            "setOverlayOpacity('" + l.id + "'," + (progress / 100.0) + ")", null);
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                }
            });
            row.addView(sb);
        }
        return row;
    }

    /* ---------------- 状态 ---------------- */

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private SharedPreferences pf() {
        return prefs();
    }

    private void restoreAll() {
        for (Layer l : LAYERS) {
            if (l.available && prefs().getBoolean("on_" + l.id, false)) apply(l, true);
        }
    }

    private void apply(Layer l, boolean on) {
        prefs().edit().putBoolean("on_" + l.id, on).apply();
        if (l.builtin != null) {
            applyBuiltin(l, on);
            return;
        }
        List<MapPoints.Point> pts = cache.get(l.id);
        if (pts != null) {
            pushLayer(l, on, pts);
            return;
        }
        if (!on) {
            web.evaluateJavascript("setOverlayLayer('" + l.id + "',false,{})", null);
            return;
        }
        loadPoints(l);
    }

    private void applyBuiltin(Layer l, boolean on) {
        switch (l.builtin) {
            case "road":
                web.evaluateJavascript("setRoadNet(" + on + ")", null);
                break;
            case "contour":
                web.evaluateJavascript("setContour(" + on + ")", null);
                break;
            case "ridge":
                web.evaluateJavascript("setRidge(" + on + ")", null);
                break;
            case "terrain":
                web.evaluateJavascript("setBase('" + (on ? "ter" : "img") + "')", null);
                break;
            default:
                break;
        }
    }

    private void loadPoints(Layer l) {
        POOL.execute(() -> {
            // R2-029：行政区域筛选优先用用户设定的省码（prefs admin_code），未设则用当前中心点省份
            String province = prefs().getString("admin_code", "");
            if (province == null || province.isEmpty()) {
                province = MapPoints.provinceCode(startLat, startLng);
            }
            List<MapPoints.Point> pts = new ArrayList<>();
            if (province != null) {
                for (int code : l.cats) {
                    pts.addAll(MapPoints.category(String.valueOf(code), province, 80));
                }
            }
            final List<MapPoints.Point> out = pts;
            MAIN.post(() -> {
                cache.put(l.id, out);
                pushLayer(l, true, out);
            });
        });
    }

    private void pushLayer(Layer l, boolean on, List<MapPoints.Point> pts) {
        try {
            JSONArray arr = new JSONArray();
            for (MapPoints.Point p : pts) {
                JSONObject o = new JSONObject();
                o.put("name", p.name);
                o.put("lat", p.lat);
                o.put("lng", p.lng);
                arr.put(o);
            }
            double op = prefs().getInt("op_" + l.id, 100) / 100.0;
            web.evaluateJavascript("setOverlayLayer('" + l.id + "'," + on + ",{opacity:" + op
                    + ",items:" + arr + "})", null);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.lc_overlay_loading, Toast.LENGTH_SHORT).show();
        }
    }

    private void setAll(boolean on) {
        for (Layer l : LAYERS) {
            if (l.tab == tab && l.available) apply(l, on);
        }
        renderTab();
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
