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

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F-03 海拔查询（LC地图 新建页，2026-11-05）。
 *
 * 原版是主界面内的「海拔模式」+ 底部卡（海拔/地址/经纬度/含氧量/气压/打点），
 * 高程来自商业图源地形。LC 免费实现：
 *   - 高程：现有 DEM（Terrarium）经 map.html 的 `elevAt` 采样，取不到返回 null；
 *   - 打点：`setElevQuery(true)` 后点图，事件 `elev` 回传 {lat,lng,elev}；
 *   - 地址：天地图逆地理（免费 key，个人配额）；
 *   - 含氧量/气压：按高程自算，UI 明标「估算」，公式写在代码里。
 * 无会员门、无次数限制；入口由 LC客户端 在左列/更多面板挂（本页不改 MainActivity）。
 *
 * AT id：lc_elev_back/title/content/status/empty/panel/lat/lng/value/address/o2/pressure/close。
 */
public class ElevationActivity extends LcFeatureActivity {

    private static final double DEF_LAT = 39.9042;
    private static final double DEF_LNG = 116.4074;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private boolean disabled;
    private double startLat = DEF_LAT;
    private double startLng = DEF_LNG;

    private WebView web;
    private TextView emptyView;
    private LinearLayout panel;
    private TextView latView;
    private TextView lngView;
    private TextView valueView;
    private TextView addressView;
    private TextView o2View;
    private TextView pressureView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disabled = !new FeatureFlags(this).isEnabled("f03");
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
        return getString(R.string.lc_elev_title);
    }

    @Override
    protected int backId() {
        return R.id.lc_elev_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_elev_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_elev_content;
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
                view.evaluateJavascript("setBase('img');lc_elev_query(true);", null);
            }
        });
        web.loadUrl(MapAsset.url("#in3d&uimode=map&lat=" + startLat
                + "&lng=" + startLng + "&z=12"));
        content.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = new TextView(this);
        emptyView.setId(R.id.lc_elev_empty);
        emptyView.setTextSize(12f);
        emptyView.setTextColor(0xFFF0E0BC);
        emptyView.setBackgroundColor(0xCC201A14);
        emptyView.setPadding(dp(12), dp(6), dp(12), dp(6));
        emptyView.setText(R.string.lc_elev_hint);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(8);
        elp.leftMargin = dp(8);
        elp.rightMargin = dp(8);
        content.addView(emptyView, elp);

        panel = new LinearLayout(this);
        panel.setId(R.id.lc_elev_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF2201A14);
        panel.setPadding(dp(14), dp(10), dp(14), dp(10));
        panel.setVisibility(View.GONE);

        latView = line(R.id.lc_elev_lat);
        lngView = line(R.id.lc_elev_lng);
        valueView = line(R.id.lc_elev_value);
        addressView = line(R.id.lc_elev_address);
        o2View = line(R.id.lc_elev_o2);
        pressureView = line(R.id.lc_elev_pressure);
        Button close = new Button(this);
        close.setId(R.id.lc_elev_close);
        close.setText(R.string.lc_elev_close);
        close.setAllCaps(false);
        close.setTextSize(12f);
        close.setOnClickListener(v -> panel.setVisibility(View.GONE));
        panel.addView(close);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.BOTTOM;
        content.addView(panel, plp);
    }

    private TextView line(int id) {
        TextView tv = new TextView(this);
        tv.setId(id);
        tv.setTextSize(13f);
        tv.setTextColor(0xFFF0E0BC);
        tv.setPadding(0, dp(2), 0, dp(2));
        panel.addView(tv);
        return tv;
    }

    /* ---------------- 事件 ---------------- */

    private class JsSink {
        @JavascriptInterface
        public void onEvent(String json) {
            MAIN.post(() -> handle(json));
        }
    }

    private void handle(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!"elev".equals(o.optString("type", ""))) return;
            double lat = o.optDouble("lat", 0);
            double lng = o.optDouble("lng", 0);
            // M-06：has+isNull 只证明键在；值能不能解析要看 optDouble 的结果（NaN = 未知）
        boolean hasElev = !Double.isNaN(o.optDouble("elev", Double.NaN));
            double elev = hasElev ? o.optDouble("elev", Double.NaN) : 0;

            emptyView.setText(R.string.lc_elev_panel);
            latView.setText(getString(R.string.lc_elev_lat,
                    String.format(Locale.US, "%.6f", lat)));
            lngView.setText(getString(R.string.lc_elev_lng,
                    String.format(Locale.US, "%.6f", lng)));
            if (hasElev) {
                valueView.setText(getString(R.string.lc_elev_value,
                        String.format(Locale.US, "%.1f", elev)));
                o2View.setText(getString(R.string.lc_elev_o2,
                        String.format(Locale.US, "%.2f", 20.9 - 0.0004 * elev)));
                double p = 1013.25 * Math.pow(1 - 2.25577e-5 * elev, 5.25588);
                pressureView.setText(getString(R.string.lc_elev_pressure,
                        String.format(Locale.US, "%.1f", p)));
            } else {
                valueView.setText(R.string.lc_elev_none);
                o2View.setText("");
                pressureView.setText("");
            }
            addressView.setText(getString(R.string.lc_elev_address,
                    getString(R.string.lc_elev_unknown)));
            panel.setVisibility(View.VISIBLE);

            final double flat = lat, flng = lng;
            POOL.execute(() -> {
                String addr = MapPoints.reverseAddress(flat, flng);
                if (addr.isEmpty()) return;
                MAIN.post(() -> addressView.setText(getString(R.string.lc_elev_address, addr)));
            });
        } catch (Throwable ignored) {
            // 事件坏了不崩
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
