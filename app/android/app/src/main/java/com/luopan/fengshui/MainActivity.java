package com.luopan.fengshui;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private FrameLayout content;
    private View compassTab;
    private CompassView compassView;
    private TextView readingView;
    private WebView mapView;
    private WebView compassMapView;
    private View knowledgeTab;

    private SensorManager sensorManager;
    private Sensor rotationVector;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private long lastRingLog = 0L;
    /** D-005: single 3D pick marker (overwritten on every pick, cleared by pick-clear). */
    private MapMarker mapMarker;
    /** last raw (un-calibrated) azimuth, for the master-switch recompute. */
    private float lastRaw = 0f;

    // P0-1 reading trust
    private android.widget.TextView statusView;
    private android.content.SharedPreferences prefs;
    private float declination = 0f;
    private boolean trueNorth = false;
    private boolean calibrationDone = false;
    private DebugBridge debugBridge;
    private Float injectedHeading = null;
    private Float injectedPitch = null;
    private Float injectedRoll = null;
    private Integer injectedAccuracy = null;
    private int sensorAccuracy = 3;
    private boolean receiverRegistered = false;
    private android.widget.Button declButton;
    private android.widget.LinearLayout trustRow;
    private boolean p01Enabled = true;
    private int hiddenTapCount = 0;
    private long lastHiddenTap = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        content = findViewById(R.id.content);
        Button tabCompass = findViewById(R.id.tab_compass);
        Button tabMap = findViewById(R.id.tab_map);
        Button tabKnowledge = findViewById(R.id.tab_knowledge);

        prefs = getSharedPreferences("lc_prefs", MODE_PRIVATE);
        declination = prefs.getFloat("declination", 0f);
        trueNorth = prefs.getBoolean("true_north", false);
        calibrationDone = prefs.getBoolean("calibration_done", false);
        p01Enabled = prefs.getBoolean("p0_1_enabled", true);

        compassTab = buildCompassTab();
        mapView = buildMapTab();
        knowledgeTab = buildKnowledgeTab();

        content.addView(compassTab);
        content.addView(mapView);
        content.addView(knowledgeTab);

        tabCompass.setOnClickListener(v -> showTab(0));
        tabMap.setOnClickListener(v -> showTab(1));
        tabKnowledge.setOnClickListener(v -> showTab(2));
        showTab(0);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        // Request location permission for the map's 定位 button.
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        if (p01Enabled && !calibrationDone) showCalibrationGuide();
        applyTrustState();
    }

    /** P0-1 RT-010: master switch — off means no status bar / trust row / 天池. */
    private void applyTrustState() {
        if (statusView != null) statusView.setVisibility(p01Enabled ? View.VISIBLE : View.GONE);
        if (trustRow != null) trustRow.setVisibility(p01Enabled ? View.VISIBLE : View.GONE);
        if (compassView != null) compassView.setTrustEnabled(p01Enabled);
        // RT-010: master switch off means the raw heading path, exactly the baseline.
        float raw = injectedHeading != null ? injectedHeading : lastRaw;
        float h = p01Enabled
                ? SensorMath.applyDeclination(raw, declination, trueNorth)
                : SensorMath.normalize(raw);
        if (compassView != null) compassView.setSensorHeading(h);
    }

    /** P0-0 minimal entry: long-press the reading text 7 times opens the switch. */
    private void showHiddenSwitch() {
        final android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("读数可信包（P0-1）");
        cb.setChecked(p01Enabled);
        new android.app.AlertDialog.Builder(this)
                .setTitle("隐藏开关")
                .setView(cb)
                .setPositiveButton("保存", (d, w) -> {
                    p01Enabled = cb.isChecked();
                    prefs.edit().putBoolean("p0_1_enabled", p01Enabled).apply();
                    applyTrustState();
                    updateReading();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void countHiddenTap() {
        long now = System.currentTimeMillis();
        if (now - lastHiddenTap > 1500) hiddenTapCount = 0;
        lastHiddenTap = now;
        if (++hiddenTapCount >= 7) {
            hiddenTapCount = 0;
            showHiddenSwitch();
        }
    }

    /** P0-1 RT-009: first-entry calibration guide, 跳过 persists calibration_done. */
    private void showCalibrationGuide() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("校准罗盘")
                .setMessage("首次使用请按 8 字晃动手机校准。也可以先跳过，随时可在罗盘页重试。")
                .setPositiveButton("开始校准", (d, w) -> {
                    calibrationDone = true;
                    prefs.edit().putBoolean("calibration_done", true).apply();
                    android.widget.Toast.makeText(this, "请按 8 字晃动手机", android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("跳过", (d, w) -> {
                    calibrationDone = true;
                    prefs.edit().putBoolean("calibration_done", true).apply();
                })
                .show();
    }

    /** P0-1 S2/S8 debug bridge. */
    private void handleDebugCommand(String cmd, android.content.Intent it) {
        switch (cmd) {
            case "heading":
                injectedHeading = it.getFloatExtra("value", 0f);
                if (compassView != null) {
                    compassView.setSensorHeading(p01Enabled
                            ? SensorMath.applyDeclination(injectedHeading, declination, trueNorth)
                            : SensorMath.normalize(injectedHeading));
                }
                break;
            case "accuracy":
                injectedAccuracy = it.getIntExtra("value", 3);
                break;
            case "tilt":
                injectedPitch = it.getFloatExtra("pitch", 0f);
                injectedRoll = it.getFloatExtra("roll", 0f);
                break;
            case "reset":
                injectedHeading = null;
                injectedPitch = null;
                injectedRoll = null;
                injectedAccuracy = null;
                break;
            default:
                return;
        }
        updateReading();
    }

    private void registerDebugBridge() {
        if (receiverRegistered) return;
        debugBridge = new DebugBridge(this::handleDebugCommand);
        android.content.IntentFilter f = new android.content.IntentFilter(DebugBridge.ACTION);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(debugBridge, f, android.content.Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(debugBridge, f);
        }
        receiverRegistered = true;
    }

    private void unregisterDebugBridge() {
        if (!receiverRegistered || debugBridge == null) return;
        unregisterReceiver(debugBridge);
        receiverRegistered = false;
    }

    private View buildCompassTab() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(24, 20, 16));

        // Base map (底图). Keep the reference: 轮 C 截图与坐向推送都指向这个 WebView.
        compassMapView = createMapWebView();
        frame.addView(compassMapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Floating compass overlay (罗盘盖在底图上).
        compassView = new CompassView(this);
        compassView.setOnHeadingChangeListener(h -> updateReading());
        int csize = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(csize, csize);
        cp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        cp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        frame.addView(compassView, cp);

        // Bottom panel: dial types + reading + actions.
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.argb(200, 24, 20, 16));

        // D5: 盘面列表入口；P0-2: 读盘（14 类全量，与原版弹层同构）
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button dialsBtn = new Button(this);
        dialsBtn.setText("盘面（4 组 · 常用 5）");
        dialsBtn.setTextSize(12f);
        dialsBtn.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, CompassListActivity.class)));
        Button readBtn = new Button(this);
        readBtn.setText("读盘");
        readBtn.setTextSize(12f);
        readBtn.setOnClickListener(v -> {
            if (compassView != null) CompassReadSheet.show(this, compassView.getHeading());
        });
        nav.addView(dialsBtn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f));
        nav.addView(readBtn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(nav);

        readingView = new TextView(this);
        readingView.setId(R.id.reading_text);
        readingView.setOnLongClickListener(v -> { countHiddenTap(); return true; });
        readingView.setTextColor(Color.rgb(235, 228, 215));
        readingView.setTextSize(14f);
        readingView.setPadding(24, 8, 24, 4);
        panel.addView(readingView);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button reset = new Button(this);
        reset.setText(R.string.north_reset);
        reset.setOnClickListener(v -> compassView.reset());
        Button flip = new Button(this);
        flip.setText(R.string.sitting_flip);
        flip.setOnClickListener(v ->
                compassView.setHeadingManually(normalize(compassView.getHeading() + 180f)));
        Button shot = new Button(this);
        shot.setText("截图");
        shot.setOnClickListener(v -> captureMap());
        buttons.addView(reset, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        buttons.addView(flip, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        buttons.addView(shot, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(buttons);

        // P0-1: 口径切换 + 磁偏角 + 状态条
        android.widget.LinearLayout trust = new android.widget.LinearLayout(this);
        trust.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        final android.widget.Button modeButton = new android.widget.Button(this);
        modeButton.setText(trueNorth ? "真北" : "磁北");
        modeButton.setTextSize(12f);
        modeButton.setOnClickListener(v -> {
            trueNorth = !trueNorth;
            prefs.edit().putBoolean("true_north", trueNorth).apply();
            modeButton.setText(trueNorth ? "真北" : "磁北");
            updateReading();
        });
        declButton = new android.widget.Button(this);
        declButton.setTextSize(12f);
        declButton.setText(String.format(java.util.Locale.US, "偏角%+.1f°", declination));
        declButton.setOnClickListener(v -> showDeclinationDialog(modeButton));
        trust.addView(modeButton, new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        trust.addView(declButton, new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        trustRow = trust;
        panel.addView(trust);

        statusView = new android.widget.TextView(this);
        statusView.setId(R.id.sensor_status);
        statusView.setTextColor(Color.rgb(180, 170, 150));
        statusView.setTextSize(12f);
        statusView.setPadding(24, 0, 24, 8);
        panel.addView(statusView);

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        pp.gravity = android.view.Gravity.BOTTOM;
        frame.addView(panel, pp);

        applyTrustState();
        return frame;
    }

    private WebView createMapWebView() {
        WebView webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(24, 20, 16));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setGeolocationEnabled(true);
        webView.addJavascriptInterface(new LCAndroid((type, marker) -> {
            if ("pick".equals(type)) {
                mapMarker = marker;
            } else {
                mapMarker = null;
            }
        }), "LCAndroid");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
        webView.loadUrl("file:///android_asset/map.html");
        return webView;
    }

    /**
     * 截图：罗盘页整屏（底图 + 罗盘 + 面板）。PixelCopy 优先（View.draw 抓 WebGL 必空），
     * 黑图/失败才退到 WebView canvas 的 toDataURL。D-009：存相册 Pictures/LC/、不写 GPS EXIF、
     * 文件名「坐向_时间」、保存后走系统分享。
     */
    private void captureMap() {
        final View target = compassTab;
        if (target == null || target.getWidth() == 0 || target.getHeight() == 0) {
            toast("截图失败：罗盘页未就绪");
            return;
        }
        final int w = target.getWidth();
        final int h = target.getHeight();
        final android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                int[] loc = new int[2];
                target.getLocationInWindow(loc);
                android.graphics.Rect rect = new android.graphics.Rect(loc[0], loc[1], loc[0] + w, loc[1] + h);
                android.view.PixelCopy.request(getWindow(), rect, bmp, result -> {
                    if (result == android.view.PixelCopy.SUCCESS && !looksBlank(bmp)) {
                        saveCompassShot(bmp);
                    } else {
                        captureViaJs();
                    }
                }, new android.os.Handler(android.os.Looper.getMainLooper()));
            } catch (Throwable t) {
                captureViaJs();
            }
        } else {
            captureViaJs();
        }
    }

    /** WebGL 抓不到时 PixelCopy 会得到整片黑；采样地图可见的左侧边缘判断。 */
    private boolean looksBlank(android.graphics.Bitmap bmp) {
        int[] xs = {(int) (bmp.getWidth() * 0.03f), (int) (bmp.getWidth() * 0.5f)};
        int[] ys = {(int) (bmp.getHeight() * 0.35f), (int) (bmp.getHeight() * 0.55f)};
        for (int x : xs) {
            for (int y : ys) {
                int c = bmp.getPixel(x, y);
                if (((c >> 16) & 0xff) + ((c >> 8) & 0xff) + (c & 0xff) > 24) return false;
            }
        }
        return true;
    }

    /** 退路：只拿得到地图 canvas（不含罗盘覆盖层），黑图时保证至少有图可存。 */
    private void captureViaJs() {
        if (compassMapView == null) {
            toast("截图失败：底图未就绪");
            return;
        }
        compassMapView.evaluateJavascript(
                "(function(){var c=document.querySelector('canvas');return c?c.toDataURL('image/png'):'';})()",
                value -> {
                    try {
                        if (value == null || value.length() < 32) {
                            toast("截图失败：地图未渲染");
                            return;
                        }
                        byte[] raw = android.util.Base64.decode(
                                value.substring(1, value.length() - 1), android.util.Base64.DEFAULT);
                        android.graphics.Bitmap map = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
                        if (map == null) {
                            toast("截图失败：解码图片失败");
                            return;
                        }
                        saveCompassShot(map);
                    } catch (Throwable t) {
                        toast("截图失败: " + t.getMessage());
                    }
                });
    }

    private void saveCompassShot(android.graphics.Bitmap bmp) {
        CompassData.Reading r = CompassData.read(compassView == null ? 0f : compassView.getHeading());
        String name = "坐向" + CompassData.MOUNTAINS[r.sitting] + "向" + CompassData.MOUNTAINS[r.mountain]
                + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
                v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/LC");
                android.net.Uri uri = getContentResolver()
                        .insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new java.io.IOException("MediaStore insert failed");
                try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os);
                }
                shareShot(uri, name);
            } else {
                java.io.File dir = new java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "LC");
                if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("mkdirs failed");
                java.io.File out = new java.io.File(dir, name + ".png");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
                }
                toast("已保存 " + out.getName());
            }
        } catch (Throwable t) {
            toast("保存失败: " + t.getMessage());
        }
    }

    private void shareShot(android.net.Uri uri, String name) {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
        i.setType("image/png");
        i.putExtra(android.content.Intent.EXTRA_STREAM, uri);
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(android.content.Intent.createChooser(i, "分享 " + name));
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private WebView buildMapTab() {
        return createMapWebView();
    }

    private View buildKnowledgeTab() {
        final java.util.List<String> titles = new java.util.ArrayList<>();
        final java.util.List<String> bodies = new java.util.ArrayList<>();
        titles.add("盘面（5 种，全部免费）");
        bodies.add(String.join("\n", CompassData.COMPASS_TYPES));
        titles.add("圈层（14 种读数）");
        bodies.add(String.join("\n", CompassData.READ_TYPES));
        for (int i = 0; i < CompassData.KNOWLEDGE_TITLES.length; i++) {
            titles.add(CompassData.KNOWLEDGE_TITLES[i]);
            bodies.add(CompassData.KNOWLEDGE_BODIES[i]);
        }

        android.widget.ListView lv = new android.widget.ListView(this);
        lv.setBackgroundColor(Color.rgb(24, 20, 16));
        lv.setDividerHeight(0);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, titles) {
            @Override
            public android.view.View getView(int pos, android.view.View cv, android.view.ViewGroup parent) {
                android.widget.TextView tv = (android.widget.TextView) super.getView(pos, cv, parent);
                tv.setTextColor(Color.rgb(235, 228, 215));
                tv.setTextSize(16f);
                tv.setPadding(24, 20, 24, 20);
                return tv;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, pos, id) ->
                new android.app.AlertDialog.Builder(this)
                        .setTitle(titles.get(pos))
                        .setMessage(bodies.get(pos))
                        .setPositiveButton("确定", null)
                        .show());
        return lv;
    }

    private void showDeclinationDialog(android.widget.Button modeButton) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format(java.util.Locale.US, "%.1f", declination));
        new android.app.AlertDialog.Builder(this)
                .setTitle("磁偏角（-30° ~ +30°）")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    try {
                        declination = SensorMath.clampDeclination(Float.parseFloat(input.getText().toString()));
                    } catch (Throwable t) {
                        declination = 0f;
                    }
                    prefs.edit().putFloat("declination", declination).apply();
                    declButton.setText(String.format(java.util.Locale.US, "偏角%+.1f°", declination));
                    updateReading();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTab(int index) {
        compassTab.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        mapView.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        knowledgeTab.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    }

    private void updateReading() {
        if (compassView == null || readingView == null) return;
        CompassData.Reading r = CompassData.read(compassView.getHeading());
        readingView.setText(
                String.format("度数 %.2f°  %s  %s\n地盘 %s山  人盘 %s山  天盘 %s山  坐 %s山\n分金 %s  七十二龙 %s\n六十龙 %s  后天卦 %s  先天卦 %s",
                        r.heading, r.jianText, CompassData.MOUNTAINS[r.mountain],
                        CompassData.MOUNTAINS[r.mountain],
                        CompassData.MOUNTAINS[plate(r.heading + 15f)],
                        CompassData.MOUNTAINS[plate(r.heading)],
                        CompassData.MOUNTAINS[r.sitting],
                        clean(r.fenjinText), clean(r.dragon72Text),
                        clean(r.dragon60Text), clean(r.houtianText), clean(r.xiantianText)));

        // P0-1 status bar: 口径 + 精度 + 倾斜
        if (statusView != null) {
            int acc = injectedAccuracy != null ? injectedAccuracy : sensorAccuracy;
            float tilt = 0f;
            if (injectedPitch != null) tilt = SensorMath.tiltDegrees(injectedPitch, injectedRoll);
            String accText = acc >= 3 ? "" : (acc == 2 ? " · 精度中" : " · 精度低");
            String tiltText = tilt > 8f ? String.format(java.util.Locale.US, " · 请端平(%.0f°)", tilt) : "";
            String declText = trueNorth
                    ? String.format(java.util.Locale.US, "真北%+.1f°", declination)
                    : "磁北";
            statusView.setText(declText + accText + tiltText);
            statusView.setTextColor(acc <= 1 ? Color.rgb(230, 200, 90) : Color.rgb(180, 170, 150));
        }

        // AT-B2 §2.4: structured debug line, max 2/s (sensor runs at UI rate).
        if (isDebuggable() && System.currentTimeMillis() - lastRingLog > 500) {
            lastRingLog = System.currentTimeMillis();
            for (int t = 1; t <= 14; t++) {
                int idx = CompassData.ringIndex(t, r.heading);
                android.util.Log.d("LCRING", "type=" + t
                        + " h=" + String.format(java.util.Locale.US, "%.2f", r.heading)
                        + " i=" + idx
                        + " center=" + String.format(java.util.Locale.US, "%.2f",
                                CompassData.ringCenter(t, idx))
                        + " text=" + CompassData.RINGS[t - 1].labels[idx]);
            }
        }

        // Push heading + sit/face to BOTH map WebViews: the compass tab's base map
        // (B-17) and the map tab. Injection goes through this same path, so
        // `am broadcast ... heading` must reach the map's bearing (坐南朝北).
        String face = CompassData.MOUNTAINS[r.mountain] + "山";
        String sit = CompassData.MOUNTAINS[r.sitting] + "山";
        for (WebView wv : new WebView[]{compassMapView, mapView}) {
            if (wv == null) continue;
            wv.evaluateJavascript("setHeading(" + r.heading + ")", null);
            wv.evaluateJavascript("setSitFace('" + face + "','" + sit + "')", null);
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace("\u200c", "");
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static int plate(float h) {
        // Numeric literal on purpose: tests/numeric/source_parse.py evaluates this
        // expression; 轮 B moves it to CompassData.MOUNTAIN_STEP together with LC测试.
        float x = h % 360f;
        if (x < 0f) x += 360f;
        return ((int) Math.floor(x / 15f)) % 24;
    }

    private static float normalize(float d) {
        float x = d % 360f;
        if (x < 0f) x += 360f;
        return x;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerDebugBridge();
        if (compassView != null && prefs != null) {
            compassView.setDialType(prefs.getInt("dial_index", 1));
        }
        if (sensorManager != null && rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        unregisterDebugBridge();
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // AT-P0-1: 注入态冻结传感器管线，否则每帧 setText 让 uiautomator 永远拿不到 idle。
        if (injectedHeading != null) return;
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            float raw = normalize((float) Math.toDegrees(orientation[0]));
            lastRaw = raw;
            if (injectedHeading != null) raw = injectedHeading;
            // P0-1 S5: every ring uses H; master switch off falls back to raw (RT-010).
            float h = p01Enabled
                    ? SensorMath.applyDeclination(raw, declination, trueNorth)
                    : SensorMath.normalize(raw);
            compassView.setSensorHeading(h);
            updateReading();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        this.sensorAccuracy = accuracy;
        updateReading();
    }

    @Override
    public void onBackPressed() {
        if (mapView != null && mapView.canGoBack()) {
            mapView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
