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
    /** K-01: 单页根视图（沿用旧字段名，截图与 PixelCopy 路径不变）。 */
    private View compassTab;
    /** 底部信息条（罗盘工具条 + 角注 + 口径），composeAttribution 依赖它的 top。 */
    private View bottomPanel;
    private String lastAttribution = "";
    private CompassView compassView;
    private TextView readingView;
    private WebView compassMapView;
    /** K-01: 罗盘浮层是否展开——同一页面两种表现，不是两个页面。 */
    private boolean compassMode = false;
    private View leftColumn;
    private View leftCompassColumn;
    private View rightColumn;
    private View rightCompassColumn;
    private View compassToolBar;
    private Button hideButton;
    private TextView attrView;

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
        applyImmersiveFullscreen();

        content = findViewById(R.id.content);

        prefs = getSharedPreferences("lc_prefs", MODE_PRIVATE);
        declination = prefs.getFloat("declination", 0f);
        trueNorth = prefs.getBoolean("true_north", false);
        calibrationDone = prefs.getBoolean("calibration_done", false);
        p01Enabled = prefs.getBoolean("p0_1_enabled", true);

        compassTab = buildSinglePage();
        content.addView(compassTab);

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

    /**
     * K-01 几何：原版是沉浸全屏（红线贯穿 y=0–2699、圆心＝屏幕中心）。
     * 非沉浸时内容区被状态栏下压 126px，浮层圆心实测偏到 (612,1414) 而不是 (614,1350)。
     * 只动窗口/视图层，不碰 map.html。
     */
    private void applyImmersiveFullscreen() {
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
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
            case "url": {
                String url = it.getStringExtra("value");
                if (url != null && compassMapView != null) {
                    // K-01 单页：只有一个 WebView
                    compassMapView.loadUrl(url);
                }
                break;
            }
            case "js": {
                String code = it.getStringExtra("value");
                if (code != null && compassMapView != null) {
                    // ValueCallback -> logcat，便于断言「注入真的执行了」
                    compassMapView.evaluateJavascript(code,
                            v -> android.util.Log.d("LCJS", "map -> " + v));
                }
                break;
            }
            case "capture":
                android.util.Log.d("LCDEBUG", "capture requested");
                captureMap();
                break;
            case "declination": {
                float v = it.getFloatExtra("value", 0f);
                declination = SensorMath.clampDeclination(v);
                prefs.edit().putFloat("declination", declination).apply();
                if (declButton != null) {
                    declButton.setText(String.format(java.util.Locale.US, "偏角%+.1f°", declination));
                }
                updateReading();
                android.util.Log.d("LCDEBUG", "declination=" + declination);
                break;
            }
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
        // B-02: 只在 debuggable 包内注册。release 包经 FLAG_DEBUGGABLE 直接返回，调试广播接口不可达。
        if (!isDebuggable()) return;
        if (receiverRegistered) return;
        try {
            debugBridge = new DebugBridge(this::handleDebugCommand);
            android.content.IntentFilter f = new android.content.IntentFilter(DebugBridge.ACTION);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                // 保留 RECEIVER_EXPORTED 是刻意的：AT 依赖 `adb shell am broadcast`，非导出收不到 shell 广播。
                // 安全边界是上一行的 isDebuggable() 门槛（release 不注册），不是导出标志。
                registerReceiver(debugBridge, f, android.content.Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(debugBridge, f);
            }
            receiverRegistered = true;
            android.util.Log.d("LCDEBUG", "bridge registered sdk=" + android.os.Build.VERSION.SDK_INT
                    + " action=" + DebugBridge.ACTION);
        } catch (Throwable t) {
            android.util.Log.e("LCDEBUG", "bridge register FAILED", t);
        }
    }

    private void unregisterDebugBridge() {
        if (!receiverRegistered || debugBridge == null) return;
        unregisterReceiver(debugBridge);
        receiverRegistered = false;
    }

    /**
     * K-01 单页：全屏地图 + 左列 8 + 右列 10；罗盘是**同一页面的浮层**（默认隐藏）。
     * 原版没有底部导航（证据：docs/evidence/original-apk/README.md §4/§5）。
     */
    private View buildSinglePage() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(24, 20, 16));

        // Base map (底图). Keep the reference: 轮 C 截图与坐向推送都指向这个 WebView.
        compassMapView = createMapWebView();
        frame.addView(compassMapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 罗盘浮层：圆心＝屏幕中心；直径与 face alpha 待 measurements.md（见本笔「待跑」块）。
        compassView = new CompassView(this);
        compassView.setOnHeadingChangeListener(h -> updateReading());
        // 直径按 measurements.md 实测：orig-06-read.png 外径 906 px / 1228 = 73.78%
        // （900–910 区间，独立复算一致）。+16 抵消 CompassView 内 r = min(cx,cy) - 8f 的收缩，
        // 使绘制外径 = 0.7378 屏宽（1228 → 906），不再用旧的 0.733（900）。
        int csize = Math.round(getResources().getDisplayMetrics().widthPixels * 0.7378f) + 16;
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(csize, csize);
        cp.gravity = android.view.Gravity.CENTER;
        compassView.setVisibility(View.GONE);
        frame.addView(compassView, cp);
        // K-01 z 序与触摸（验收 P-08 必读）：浮层之后又加了 读数/状态/左列/罗盘左列/右列/隐藏/底部条，
        // 它们都在浮层之上；其中工具列与「隐藏」是交互层，会正常吃掉落在按钮矩形内的触摸。
        // 圆盘外径 0.733W ≈ 900px@1228，水平方向圆外 50px 已压到左右工具列上，
        // 所以「圆外 50px 可平移」的验收点必须取**垂直方向**（如 (614,800)），否则失败原因会是工具列而不是罗盘。

        // 顶部读数（原版罗盘模式左上「坐180° 向0°」）+ P0-1 状态条
        readingView = new TextView(this);
        readingView.setId(R.id.reading_text);
        readingView.setOnLongClickListener(v -> { countHiddenTap(); return true; });
        // 原版：点圈层文字直达知识页（C2）；这里点读数行打开知识弹层。
        readingView.setOnClickListener(v -> showKnowledgeSheet());
        readingView.setTextColor(Color.rgb(235, 228, 215));
        readingView.setTextSize(11f);
        readingView.setPadding(dp(8), dp(4), dp(8), dp(4));
        readingView.setBackgroundColor(Color.argb(140, 24, 20, 16));
        FrameLayout.LayoutParams rvp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        rvp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        rvp.leftMargin = dp(4);
        rvp.topMargin = dp(26);
        frame.addView(readingView, rvp);

        statusView = new TextView(this);
        statusView.setId(R.id.sensor_status);
        statusView.setTextColor(Color.rgb(180, 170, 150));
        statusView.setTextSize(10f);
        statusView.setClickable(false);   // 纯展示层，不拦截触摸（浮层之上的非交互层一律不消费事件）
        statusView.setFocusable(false);
        statusView.setPadding(dp(8), dp(2), dp(8), dp(2));
        FrameLayout.LayoutParams svp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        svp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        svp.leftMargin = dp(4);
        svp.topMargin = dp(4);
        frame.addView(statusView, svp);

        // 左列 8（README §4 顺序）
        leftColumn = buildColumn(new String[]{
                "全地图工具", "截屏", "超清优化", "海拔查询", "罗盘", "路网", "等高线", "更多"});
        FrameLayout.LayoutParams lcp = new FrameLayout.LayoutParams(
                dp(58), FrameLayout.LayoutParams.WRAP_CONTENT);
        lcp.gravity = android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL;
        lcp.leftMargin = dp(2);
        frame.addView(leftColumn, lcp);

        // 罗盘模式左列 6（README §5）
        leftCompassColumn = buildColumn(new String[]{
                "截屏", "等高线", "罗盘复位", "罗盘锁定", "读盘", "贴地"});
        leftCompassColumn.setVisibility(View.GONE);
        FrameLayout.LayoutParams lccp = new FrameLayout.LayoutParams(
                dp(58), FrameLayout.LayoutParams.WRAP_CONTENT);
        lccp.gravity = android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL;
        lccp.leftMargin = dp(2);
        frame.addView(leftCompassColumn, lccp);

        // 右列 10（README §4）
        rightColumn = buildColumn(new String[]{
                "图层", "叠加", "山脉", "水系", "探索", "指北针", "+", "−", "定位", "3D"});
        FrameLayout.LayoutParams rcp = new FrameLayout.LayoutParams(
                dp(58), FrameLayout.LayoutParams.WRAP_CONTENT);
        rcp.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
        rcp.rightMargin = dp(2);
        frame.addView(rightColumn, rcp);

        // 罗盘模式右列 4（README §5）
        rightCompassColumn = buildColumn(new String[]{"地图", "实景", "坐南朝北", "指针开关"});
        rightCompassColumn.setVisibility(View.GONE);
        FrameLayout.LayoutParams rccp = new FrameLayout.LayoutParams(
                dp(58), FrameLayout.LayoutParams.WRAP_CONTENT);
        rccp.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
        rccp.rightMargin = dp(2);
        frame.addView(rightCompassColumn, rccp);

        // 「隐藏」：罗盘模式下回到地图态（原版顶部栏同名入口）
        hideButton = new Button(this);
        hideButton.setText("隐藏");
        hideButton.setTextSize(10f);
        hideButton.setVisibility(View.GONE);
        hideButton.setOnClickListener(v -> setCompassMode(false));
        FrameLayout.LayoutParams hbp = new FrameLayout.LayoutParams(dp(52), dp(34));
        hbp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        hbp.topMargin = dp(4);
        hbp.rightMargin = dp(4);
        frame.addView(hideButton, hbp);

        // 底部：罗盘工具条（5 项，仅罗盘模式）+ 角注 + P0-1 口径
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(Color.argb(170, 24, 20, 16));
        compassToolBar = buildCompassToolBar();
        compassToolBar.setVisibility(View.GONE);
        bottom.addView(compassToolBar);

        attrView = new TextView(this);
        attrView.setText("数据来自天地图地形（等价替代原版世纪空间）· 审图号：未确认");
        attrView.setTextColor(Color.rgb(150, 130, 100));
        attrView.setTextSize(9f);
        attrView.setPadding(dp(10), dp(1), dp(10), dp(1));
        bottom.addView(attrView);

        trustRow = buildTrustRow();
        bottom.addView(trustRow);
        bottomPanel = bottom;
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bp.gravity = android.view.Gravity.BOTTOM;
        frame.addView(bottom, bp);

        applyTrustState();
        return frame;
    }

    /** 一列工具按钮（左/右、地图/罗盘四套共用）。 */
    private LinearLayout buildColumn(String[] labels) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        for (String label : labels) {
            Button b = new Button(this);
            b.setText(label);
            b.setTextSize(9f);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setPadding(0, 0, 0, 0);
            b.setBackgroundColor(Color.argb(120, 24, 20, 16));
            b.setTextColor(Color.rgb(235, 228, 215));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(38));
            lp.setMargins(0, dp(2), 0, dp(2));
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> onToolClick(label));
            col.addView(b);
        }
        return col;
    }

    /** 底部罗盘工具条（README §5 的 5 项，非页面导航）。 */
    private LinearLayout buildCompassToolBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.argb(190, 24, 20, 16));
        for (String label : new String[]{"切换", "旋转", "缩放", "透明度", "天支线"}) {
            Button b = new Button(this);
            b.setText(label);
            b.setTextSize(10f);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setTextColor(Color.rgb(220, 190, 130));
            b.setOnClickListener(v -> onToolClick(label));
            bar.addView(b, new LinearLayout.LayoutParams(0, dp(38), 1f));
        }
        return bar;
    }

    /** P0-1 口径条（真北/磁北 + 偏角），从旧底部面板迁到单页底部信息条。 */
    private LinearLayout buildTrustRow() {
        LinearLayout trust = new LinearLayout(this);
        trust.setOrientation(LinearLayout.HORIZONTAL);
        final Button modeButton = new Button(this);
        modeButton.setText(trueNorth ? "真北" : "磁北");
        modeButton.setTextSize(10f);
        modeButton.setMinHeight(0);
        modeButton.setMinimumHeight(0);
        modeButton.setOnClickListener(v -> {
            trueNorth = !trueNorth;
            prefs.edit().putBoolean("true_north", trueNorth).apply();
            modeButton.setText(trueNorth ? "真北" : "磁北");
            updateReading();
        });
        declButton = new Button(this);
        declButton.setTextSize(10f);
        declButton.setMinHeight(0);
        declButton.setMinimumHeight(0);
        declButton.setText(String.format(java.util.Locale.US, "偏角%+.1f°", declination));
        declButton.setOnClickListener(v -> showDeclinationDialog(modeButton));
        trust.addView(modeButton, new LinearLayout.LayoutParams(0, dp(32), 1f));
        trust.addView(declButton, new LinearLayout.LayoutParams(0, dp(32), 1f));
        return trust;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** 所有地图侧调用统一走这里：先判函数存在，避免 map.html 版本落后时抛错。 */
    private void js(String code) {
        if (compassMapView == null) return;
        compassMapView.evaluateJavascript("try{" + code + "}catch(e){}", null);
    }

    private void onToolClick(String label) {
        switch (label) {
            case "罗盘": setCompassMode(true); break;
            case "截屏": captureMap(); break;
            case "读盘":
                if (compassView != null) CompassReadSheet.show(this, compassView.getHeading());
                break;
            case "知识": showKnowledgeSheet(); break;
            case "更多": showMoreSheet(); break;
            case "图层": js("openSources()"); break;
            case "等高线": js("toggleContour()"); break;
            case "贴地": js("toggleGround()"); break;
            case "定位": js("locate()"); break;
            case "3D": js("toggle3D()"); break;
            case "+": js("zoomIn()"); break;
            case "−": js("zoomOut()"); break;
            case "指北针": js("toggleNeedle()"); break;
            case "地图": js("setBase('vec')"); break;
            case "实景": js("setBase('img')"); break;
            case "坐南朝北": js("toggleSitNorth()"); break;
            case "指针开关":
                // 两针分离（map-bridge §10.3）：地图模式管 #needle；罗盘模式管浮层自绘北针。
                if (compassMode) {
                    boolean on = compassView != null && !compassView.isShowPointer();
                    if (compassView != null) compassView.setShowPointer(on);
                    toast(on ? "罗盘北针：开" : "罗盘北针：关");
                } else {
                    js("toggleNeedle()");
                }
                break;
            case "罗盘复位":
                if (compassView != null) compassView.reset();
                break;
            case "罗盘锁定":
                // B-05：锁定＝显式冻结开关（原版左列独立项），与「拖动/翻坐向」彻底分开。
                if (compassView != null) {
                    boolean lock = compassView.isFollowingSensor();
                    compassView.setFollowSensor(!lock);
                    toast(lock ? "罗盘已锁定（传感器不再更新）" : "罗盘已解锁（跟随传感器）");
                }
                break;
            case "切换":
                startActivity(new android.content.Intent(this, CompassListActivity.class));
                break;
            case "旋转": js("rotateBearing(15)"); break;
            case "缩放": js("zoomIn()"); break;
            case "透明度": toast("透明度：待 measurements.md（face alpha 实测）"); break;
            case "天支线": toast("天支线：圈层开关，待 step-3"); break;
            default: toast(label + "：原版为工具子页，本步未实现");
        }
    }

    /** K-01：罗盘＝同一页面的浮层，切的是「表现」不是页面。 */
    private void setCompassMode(boolean on) {
        compassMode = on;
        if (compassView != null) compassView.setVisibility(on ? View.VISIBLE : View.GONE);
        if (leftColumn != null) leftColumn.setVisibility(on ? View.GONE : View.VISIBLE);
        if (rightColumn != null) rightColumn.setVisibility(on ? View.GONE : View.VISIBLE);
        if (leftCompassColumn != null) leftCompassColumn.setVisibility(on ? View.VISIBLE : View.GONE);
        if (rightCompassColumn != null) rightCompassColumn.setVisibility(on ? View.VISIBLE : View.GONE);
        if (compassToolBar != null) compassToolBar.setVisibility(on ? View.VISIBLE : View.GONE);
        if (hideButton != null) hideButton.setVisibility(on ? View.VISIBLE : View.GONE);
        js(on ? "setUiMode('compass')" : "setUiMode('map')");
        updateReading();
    }

    /** 「更多」＝底部面板（README §6），不是新页面。 */
    private void showMoreSheet() {
        final String[] tools = {"罗盘", "超清地图", "看山脉", "看水系", "海拔查询", "天气", "历史影像",
                "等高线", "收藏点", "测量", "画笔", "钓点", "轨迹记录", "路网", "地理影像", "超清优化"};
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout row = null;
        for (int i = 0; i < tools.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));
            }
            final String t = tools[i];
            Button b = new Button(this);
            b.setText(t);
            b.setTextSize(10f);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setOnClickListener(v -> {
                if ("罗盘".equals(t)) setCompassMode(true);
                else toast(t + "：原版为工具子页，本步未实现");
            });
            row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("地图工具")
                .setView(grid)
                .setNegativeButton("关闭", null)
                .show();
    }

    /** 知识＝弹层（原版是点圈层文字直达，不做 Tab）。 */
    private void showKnowledgeSheet() {
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
                tv.setTextSize(15f);
                tv.setPadding(20, 16, 20, 16);
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
        new android.app.AlertDialog.Builder(this)
                .setTitle("知识")
                .setView(lv)
                .setNegativeButton("关闭", null)
                .show();
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
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // K-01：默认 full 保持不变；只有真机页面加载完成后才切 map（无头 AT 不调则不切）。
                js("setUiMode('map')");
            }
        });
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
        // BUG-005: #srcInfo is covered by the bottom panel on device; composite the
        // current source + review number into the saved image instead of moving map.html.
        if (compassMapView != null) {
            compassMapView.evaluateJavascript(
                    "(function(){var e=document.getElementById('srcInfo');return e?e.textContent:'';})()",
                    v -> { lastAttribution = decodeJsString(v); doCapture(); });
        } else {
            doCapture();
        }
    }

    /** JSON-quoted JS string -> plain text. */
    private static String decodeJsString(String v) {
        if (v == null || v.length() < 2) return "";
        String s = v.substring(1, v.length() - 1);
        s = s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\n", " ");
        return s.trim();
    }

    private void doCapture() {
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
                        saveCompassShot(composeAttribution(bmp));
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
                        saveCompassShot(composeAttribution(map));
                    } catch (Throwable t) {
                        toast("截图失败: " + t.getMessage());
                    }
                });
    }

    /** BUG-005: draw "底图/审图号" into the image, above the bottom panel. */
    private android.graphics.Bitmap composeAttribution(android.graphics.Bitmap bmp) {
        String text = lastAttribution == null ? "" : lastAttribution.trim();
        if (text.isEmpty()) return bmp;
        try {
            android.graphics.Bitmap out = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
            android.graphics.Canvas c = new android.graphics.Canvas(out);
            float d = getResources().getDisplayMetrics().density;
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setColor(android.graphics.Color.rgb(220, 190, 130));
            p.setTextSize(12f * d);
            float tw = p.measureText(text);
            int panelTop = bottomPanel != null ? bottomPanel.getTop() : out.getHeight() - (int) (340 * d);
            float x = out.getWidth() - tw - 12 * d;
            float y = panelTop - 12 * d;
            if (y < 40 * d) y = 40 * d;
            android.graphics.Paint bg = new android.graphics.Paint();
            bg.setColor(android.graphics.Color.argb(170, 0, 0, 0));
            c.drawRect(x - 8 * d, y - 14 * d, out.getWidth(), y + 6 * d, bg);
            c.drawText(text, x, y, p);
            android.util.Log.d("LCCAP", "attribution drawn: " + text);
            return out;
        } catch (Throwable t) {
            android.util.Log.e("LCCAP", "attribution draw failed", t);
            return bmp;
        }
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
            // B-05：冻结/偏移状态必须可见，避免用户拿到过期读数还不知道。
            String modeText = "";
            if (compassView != null) {
                if (!compassView.isFollowingSensor()) modeText = " · 锁定";
                else if (compassView.getHeadingOffset() != 0f) modeText = " · 偏移";
            }
            statusView.setText(declText + accText + tiltText + modeText);
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

        // K-01 单页：只有一个 WebView，坐向只推一份
        String face = CompassData.MOUNTAINS[r.mountain] + "山";
        String sit = CompassData.MOUNTAINS[r.sitting] + "山";
        if (compassMapView != null) {
            compassMapView.evaluateJavascript("setHeading(" + r.heading + ")", null);
            compassMapView.evaluateJavascript("setSitFace('" + face + "','" + sit + "')", null);
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
        applyImmersiveFullscreen();
        registerDebugBridge();
        if (compassView != null && prefs != null) {
            int dial = prefs.getInt("dial_index", 1);
            compassView.setDialType(dial);
            compassView.setArtwork(dial);
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
        // K-01：罗盘是浮层，返回键先退出浮层，再退页面（原版「返回/隐藏」同义）。
        if (compassMode) {
            setCompassMode(false);
            return;
        }
        if (compassMapView != null && compassMapView.canGoBack()) {
            compassMapView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
