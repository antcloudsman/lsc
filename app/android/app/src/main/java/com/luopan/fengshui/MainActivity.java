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
import android.widget.SeekBar;
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
    /** R2-026：地图态左上「当前地名」（原版 青海湖/哀牢山）。 */
    private TextView placeView;
    private WebView compassMapView;
    /** K-01: 罗盘浮层是否展开——同一页面两种表现，不是两个页面。 */
    private boolean compassMode = false;
    private View leftColumn;
    private View leftCompassColumn;
    private View rightColumn;
    private View rightCompassColumn;
    private View compassToolBar;
    private Button hideButton;
    /** K-02：顶部栏 返回 / 搜索 / 图层（原版罗盘态四项：返回·搜索·图层·隐藏，见 bugs.md:264）。 */
    private Button topBackButton;
    private Button topSearchButton;
    private Button topLayerButton;
    private TextView attrView;

    private SensorManager sensorManager;
    private Sensor rotationVector;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];
    private long lastRingLog = 0L;
    /** D-005: single 3D pick marker (overwritten on every pick, cleared by pick-clear). */
    private MapMarker mapMarker;
    /** R2-004: POI 详情面板（挂在立向点上，固定高 443px）。 */
    private LinearLayout poiPanel;
    private MapMarker poiMarker;
    /** R2-030: 山脉改为地图内图层（半屏面板），不跳 Activity；数据同 MountainActivity：天地图地名搜索 分类 260203（山）。 */
    private LinearLayout mountainLayer;
    private boolean mountainLayerOn = false;
    private int mountainFetchSeq = 0;
    /** R2-006: 罗盘「隐藏」只隐盘面/指针/工具列，保留读数条与坐标，不退出罗盘态。 */
    private boolean compassHidden;
    /** R2-014: 收藏点地图态（右列 2 项 + 底部「添加点」），列表降为子页。 */
    private boolean favoriteMode;
    private LinearLayout rightFavoriteColumn;
    private LinearLayout favoriteBottomBar;
    /** R2-006: 罗盘态坐标条（隐藏态也要保留，原版 116.4074，39.9042）。 */
    private TextView coordView;
    /** R2-014：收藏点地图态的比例尺（原版 [79,2510][150,2550] 4km）。 */
    private TextView scaleView;
    /** R2-018: 左上抽屉（9 项 + 版本号）。 */
    /** R2-007: 海拔查询在地图内浮层（不跳 Activity）。 */
    private LinearLayout elevPanel;
    private boolean elevationMode;
    /** R2-025：调试广播注入的搜索词（仅 debug 包，供中文关键词验证）。 */
    private String pendingSearch;
    /** R2-025：调试广播可指定 tab（0 境内/1 境外/2 行政区域/3 收藏点），供复验确定性入口。 */
    private int pendingSearchTab = 0;
    private MapMarker elevPoint;
    private LinearLayout drawerPanel;
    private Button menuButton;
    /** last raw (un-calibrated) azimuth, for the master-switch recompute. */
    private float lastRaw = 0f;

    /** 收藏点「点选跳地图」：待应用的视点（map.html setView）。 */
    private double jumpLat = Double.NaN;
    private double jumpLng = Double.NaN;
    /** 地图页是否已加载完（决定 setView 立即调还是等 onPageFinished）。 */
    private boolean mapPageReady = false;
    /** 冷启时地图未就绪的 uiMode 目标，onPageFinished 后补发（见 applyMapUiMode）。 */
    private String pendingUiMode;

    /** F-04 路网：原版 MapManager.showRoad 默认 true；状态持久化在 MapManager prefs。 */
    private boolean roadNetOn = true;

    /** F-17（LC 自建）：罗盘显示参数（底盘透明度 / 天干地支辅助线）。 */
    private CompassDisplayStore compassDisplay;

    // F-12 测量 / F-13 画笔：地图内交互态（不新建 Activity）
    private boolean measureMode = false;
    private boolean drawMode = false;
    private View measureBar;
    private View drawBar;
    private TextView measureResultView;
    private int drawColorIndex = 1;
    private int drawWidthIndex = 1;
    // F-12 测量：具名模式 + 屏幕中心打点 + 风水方位角 + 单位切换。
    private boolean measureAreaMode = false;
    private int measureUnitIndex = 0;
    private double bearingSitting = 180;
    private double bearingFacing = 0;
    private double lastMeasureD = 0;
    private double lastMeasureA = 0;
    private Button measureUnitButton;
    private final java.util.ArrayList<double[]> measureCenters = new java.util.ArrayList<>();
    /** 原版精确 5 色（默认红=index1）；粗细 2/4/6（默认 4=index1）。 */
    private static final String[] DRAW_COLORS = {
            "#2E75FE", "#FF0000", "#36B44B", "#F9C30F", "#000000"};
    private static final int[] DRAW_WIDTHS = {2, 4, 6};
    private final android.os.Handler measureHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable measurePoll = new Runnable() {
        @Override
        public void run() {
            if (!measureMode || compassMapView == null) return;
            compassMapView.evaluateJavascript(
                    "(function(){var m=getState().measure;return JSON.stringify({d:m.distanceM,a:m.areaM2});})()",
                    v -> {
                        try {
                            org.json.JSONObject o = new org.json.JSONObject(decodeJsString(v));
                            lastMeasureD = o.optDouble("d", 0);
                            lastMeasureA = o.optDouble("a", 0);
                            refreshMeasureReadout();
                        } catch (Throwable ignored) {
                        }
                    });
            measureHandler.postDelayed(this, 500);
        }
    };

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
        roadNetOn = getSharedPreferences("MapManager", MODE_PRIVATE)
                .getBoolean("showRoad", true);
        // F-13：画笔颜色/粗细记忆（原版 MMKV；LC 用独立 prefs，不与 lc_compass 混用）。
        android.content.SharedPreferences paintPrefs = getSharedPreferences("lc_paint", MODE_PRIVATE);
        drawColorIndex = Math.max(0, Math.min(DRAW_COLORS.length - 1,
                paintPrefs.getInt("colorIndex", 1)));
        drawWidthIndex = Math.max(0, Math.min(DRAW_WIDTHS.length - 1,
                paintPrefs.getInt("widthIndex", 1)));
        declination = prefs.getFloat("declination", 0f);
        trueNorth = prefs.getBoolean("true_north", false);
        calibrationDone = prefs.getBoolean("calibration_done", false);
        p01Enabled = prefs.getBoolean("p0_1_enabled", true);

        compassTab = buildSinglePage();
        content.addView(compassTab);
        compassDisplay = new CompassDisplayStore(this);
        applyCompassDisplay();

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
        handleMapJump(getIntent());
    }

    // ---------------- 收藏点跳地图（缺口线，K-01 单页布局重挂） ----------------

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleMapJump(intent);
    }

    /**
     * 收藏点列表点击 → 带 lat/lng 拉起本页：退出罗盘浮层、调 map.html 既有 setView。
     * 单页布局下没有 Tab 可切；地图页未就绪时先挂起，等 onPageFinished 再应用。
     */
    private void handleMapJump(android.content.Intent intent) {
        if (intent == null) return;
        double lat = MapJump.doubleExtra(intent, MapJump.EXTRA_LAT);
        double lng = MapJump.doubleExtra(intent, MapJump.EXTRA_LNG);
        if (Double.isNaN(lat) || Double.isNaN(lng)) return;
        // C-07：MainActivity 是 exported launcher，越界坐标直接丢弃。
        if (lat < -85 || lat > 85 || lng < -180 || lng > 180) return;
        jumpLat = lat;
        jumpLng = lng;
        if (compassMode) setCompassMode(false);
        applyPendingMapView();
    }

    /** 地图页已就绪且有待跳视点时才执行；执行后清空，避免重复跳。 */
    private void applyPendingMapView() {
        if (compassMapView == null || !mapPageReady
                || Double.isNaN(jumpLat) || Double.isNaN(jumpLng)) return;
        String code = String.format(java.util.Locale.US, "setView(%.6f,%.6f,%d)",
                jumpLat, jumpLng, MapJump.ZOOM);
        jumpLat = Double.NaN;
        jumpLng = Double.NaN;
        js(code);
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
            case "search": {
                pendingSearch = it.getStringExtra("value");
                pendingSearchTab = it.getIntExtra("tab", 0);
                android.util.Log.d("LCSEARCH", "debug search=" + pendingSearch + " tab=" + pendingSearchTab);
                runOnUiThread(this::showSearchDialog);
                break;
            }
            case "place":
                // R2-026：量取用确定性入口（地图移动后刷新一次左上地名）
                refreshPlaceName();
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
        // R2-003：读数面板**只在罗盘态**出现（默认地图态，故建好即隐）。
        readingView.setVisibility(View.GONE);

        // R2-026：地图态左上「当前地名」。原版 orig/map-beijing-z12.xml 该 TextView = [327,194][468,273]
        // （青海湖，141×79，14sp 量级），在 ≡ 之后、搜索之前，而不是读数块。
        placeView = new TextView(this);
        placeView.setId(R.id.lc_g1_place);
        placeView.setTextColor(Color.rgb(235, 228, 215));
        placeView.setTextSize(14f);
        placeView.setGravity(android.view.Gravity.CENTER);
        placeView.setBackgroundColor(Color.TRANSPARENT);
        placeView.setMaxLines(1);
        placeView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams pvp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, 79);
        pvp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        pvp.leftMargin = 327;
        pvp.topMargin = 194;
        frame.addView(placeView, pvp);

        // R2-006：坐标条（罗盘态与隐藏态都保留；原版 hidden 态 116.4074，39.9042）
        coordView = new TextView(this);
        coordView.setId(R.id.lc_coord_text);
        coordView.setText("");
        coordView.setTextColor(Color.rgb(235, 228, 215));
        coordView.setTextSize(10f);
        coordView.setPadding(dp(8), dp(2), dp(8), dp(2));
        coordView.setBackgroundColor(Color.argb(140, 24, 20, 16));
        coordView.setVisibility(View.GONE);
        FrameLayout.LayoutParams cvp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cvp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        cvp.topMargin = 300;
        cvp.leftMargin = 8;
        frame.addView(coordView, cvp);

        scaleView = new TextView(this);
        scaleView.setId(R.id.lc_scale_text);
        scaleView.setText("");
        scaleView.setTextColor(Color.rgb(235, 228, 215));
        scaleView.setTextSize(10f);
        scaleView.setPadding(dp(8), dp(2), dp(8), dp(2));
        scaleView.setBackgroundColor(Color.argb(140, 24, 20, 16));
        scaleView.setVisibility(View.GONE);
        FrameLayout.LayoutParams scvp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        scvp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        scvp.topMargin = 360;
        scvp.leftMargin = 8;
        frame.addView(scaleView, scvp);

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

        // 左列 8（README §4 顺序）。K-01 布局对齐：原版是**两段锚定**——上组 2 个（全地图工具/截屏，
        // 间距 280）与下组 6 个（超清优化…更多，间距 186），不是单列竖直居中。
        // 锚点 = 原版**框顶**（内容帧 2578）；上组两项原版 dump 无框，按其标签中心反推（474−117.5 / 754−117.5）。
        leftColumn = buildAnchoredColumn(new String[]{
                "全地图工具", "截屏", "超清优化", "海拔查询", "罗盘", "路网", "等高线", "更多"},
                new float[]{356.5f, 636.5f, 1294f, 1480f, 1666f, 1852f, 2038f, 2224f},
                new int[]{R.id.lc_g1_mleft1, R.id.lc_g1_mleft2, R.id.lc_g1_mleft3, R.id.lc_g1_mleft4,
                        R.id.lc_g1_mleft5, R.id.lc_g1_mleft6, R.id.lc_g1_mleft7, R.id.lc_g1_mleft8}, 26);
        FrameLayout.LayoutParams lcp = new FrameLayout.LayoutParams(
                COL_BTN_W, FrameLayout.LayoutParams.MATCH_PARENT);
        lcp.gravity = android.view.Gravity.START | android.view.Gravity.TOP;
        lcp.leftMargin = COL_MARGIN_M_LEFT;
        frame.addView(leftColumn, lcp);

        // 罗盘模式左列 6（R2-010）：原版标签中心 y=1408.5 起、步距 186 → 框顶 1291 起、步距 186。
        // 证据 docs/fidelity/orig/tool-compass-page.xml：框 [27,1291][185,1449]、[27,1477][185,1635]…
        leftCompassColumn = buildAnchoredColumn(new String[]{
                "截屏", "等高线", "罗盘复位", "罗盘锁定", "读盘", "贴地"},
                new float[]{1291f, 1477f, 1663f, 1849f, 2035f, 2221f},
                new int[]{R.id.lc_g1_cleft1, R.id.lc_g1_cleft2, R.id.lc_g1_cleft3,
                        R.id.lc_g1_cleft4, R.id.lc_g1_cleft5, R.id.lc_g1_cleft6}, 26);
        leftCompassColumn.setVisibility(View.GONE);
        FrameLayout.LayoutParams lccp = new FrameLayout.LayoutParams(
                COL_BTN_W, FrameLayout.LayoutParams.MATCH_PARENT);
        lccp.gravity = android.view.Gravity.START | android.view.Gravity.TOP;
        lccp.leftMargin = COL_MARGIN_C_LEFT;
        frame.addView(leftCompassColumn, lccp);

        // 右列 10（README §4）。同样是两段：上组 5 个文字钮（图层…探索，间距 186，框顶 534 起）、
        // 下组 5 个原版大图标位（157×153，框顶 1631/1784/1937/2090/2242）。
        rightColumn = buildAnchoredColumn(new String[]{
                "图层", "叠加", "山脉", "水系", "探索", "指北针", "+", "−", "定位", "3D"},
                new float[]{534f, 720f, 906f, 1092f, 1278f, 1631f, 1784f, 1937f, 2090f, 2242f},
                new int[]{R.id.lc_g1_mright1, R.id.lc_g1_mright2, R.id.lc_g1_mright3, R.id.lc_g1_mright4,
                        R.id.lc_g1_mright5, R.id.lc_g1_mright6, R.id.lc_g1_mright7, R.id.lc_g1_mright8,
                        R.id.lc_g1_mright9, R.id.lc_g1_mright10}, 26);
        FrameLayout.LayoutParams rcp = new FrameLayout.LayoutParams(
                COL_BTN_W, FrameLayout.LayoutParams.MATCH_PARENT);
        rcp.gravity = android.view.Gravity.END | android.view.Gravity.TOP;
        rcp.rightMargin = COL_MARGIN_M_RIGHT;
        frame.addView(rightColumn, rcp);

        // 罗盘模式右列 4（R2-011 + P1-33-C）：原版框 [1044,367][1202,525] 起、步距 184。
        // P1-33-C：这四枚在原版里是**开关控件**（药丸 + 状态底色），不是图标——
        // 几何实测（orig/tool-compass-page.png，逐枚取 bbox）：
        //   轨道 107×54 居中、距框顶 33；旋钮 48×45；开态蓝底 144×144（四边内缩 7）；
        //   开=白轨 + 蓝钮靠右，关=灰轨(142,142,142) + 白钮靠左；默认 地图=开、指针开关=开、其余关。
        // 标签中心仍在框顶 +108.5（33+54 之后再留 2px），与 G1 判词一致。
        rightCompassColumn = buildAnchoredSwitchColumn(new String[]{"地图", "实景", "坐南朝北", "指针开关"},
                new float[]{367f, 551f, 735f, 919f},
                new int[]{R.id.lc_g1_cright1, R.id.lc_g1_cright2, R.id.lc_g1_cright3, R.id.lc_g1_cright4});
        rightCompassColumn.setVisibility(View.GONE);
        FrameLayout.LayoutParams rccp = new FrameLayout.LayoutParams(
                COL_BTN_W, FrameLayout.LayoutParams.MATCH_PARENT);
        rccp.gravity = android.view.Gravity.END | android.view.Gravity.TOP;
        rccp.rightMargin = COL_MARGIN_C_RIGHT;
        frame.addView(rightCompassColumn, rccp);

        // 顶部栏（K-02）：原版罗盘态是 返回 / 搜索 / 图层 / 隐藏 四项（`docs/qa/bugs.md:264` 的原版
        // 真机事实，取证 a407d41；`orig-05-compass.png` 顶部同一排右侧三枚，左侧一枚返回箭头），
        // 地图态只有 搜索（`orig-main.xml` 搜索 [1002,194][1097,273]）。此前这里只有一枚「隐藏」，
        // 结果罗盘态既没有可见的返回入口，也没有任何途径打开底图源——即 K-02。
        topBackButton = newToolButton("返回");
        topBackButton.setTextSize(10f);
        topBackButton.setVisibility(View.GONE);
        topBackButton.setOnClickListener(v -> setCompassMode(false));
        FrameLayout.LayoutParams tbp = new FrameLayout.LayoutParams(dp(52), dp(34));
        tbp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        tbp.topMargin = dp(57);
        tbp.leftMargin = dp(8);
        frame.addView(topBackButton, tbp);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topSearchButton = newToolButton("搜索");
        topSearchButton.setTextSize(10f);
        topSearchButton.setOnClickListener(v -> showSearchDialog());
        topLayerButton = newToolButton("图层");
        topLayerButton.setTextSize(10f);
        topLayerButton.setVisibility(View.GONE);
        topLayerButton.setOnClickListener(v -> onToolClick("图层"));
        hideButton = newToolButton("隐藏");
        hideButton.setTextSize(10f);
        hideButton.setVisibility(View.GONE);
        hideButton.setOnClickListener(v -> toggleCompassHidden());
        topBar.addView(topSearchButton, new LinearLayout.LayoutParams(dp(52), dp(34)));
        topBar.addView(topLayerButton, new LinearLayout.LayoutParams(dp(52), dp(34)));
        topBar.addView(hideButton, new LinearLayout.LayoutParams(dp(52), dp(34)));
        FrameLayout.LayoutParams barp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        barp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        barp.topMargin = dp(57);
        barp.rightMargin = dp(4);
        frame.addView(topBar, barp);

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

        // R2-018：左上抽屉入口（原版 (97,202) 打开）+ 抽屉面板
        menuButton = newToolButton("≡");
        menuButton.setId(R.id.lc_menu_button);
        menuButton.setTextSize(16f);
        menuButton.setOnClickListener(v -> showDrawer());
        FrameLayout.LayoutParams mbp = new FrameLayout.LayoutParams(dp(40), dp(34));
        mbp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        mbp.topMargin = dp(57);
        mbp.leftMargin = dp(6);
        frame.addView(menuButton, mbp);

        drawerPanel = buildDrawer();
        drawerPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(300), FrameLayout.LayoutParams.MATCH_PARENT);
        dlp.gravity = android.view.Gravity.START;
        frame.addView(drawerPanel, dlp);

        // R2-014：收藏点地图态右列（2 项）+ 底部「添加点」
        rightFavoriteColumn = buildFavoriteColumn();
        rightFavoriteColumn.setVisibility(View.GONE);
        FrameLayout.LayoutParams rfc = new FrameLayout.LayoutParams(dp(76), FrameLayout.LayoutParams.WRAP_CONTENT);
        rfc.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
        rfc.rightMargin = dp(2);
        frame.addView(rightFavoriteColumn, rfc);

        favoriteBottomBar = buildFavoriteBottomBar();
        favoriteBottomBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams fbb = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        fbb.gravity = android.view.Gravity.BOTTOM;
        fbb.bottomMargin = navBarInsetPx();
        frame.addView(favoriteBottomBar, fbb);

        applyTrustState();
        return frame;
    }

    /**
     * 参考内容帧高度。原版 dump（`evidence/original-apk/orig-main.xml`）与本文具的 dump 根节点都是
     * `[0,0][1228,2578]`：1228×2700 物理屏的下方 122px 是导航栏，不在应用窗口内，所以原版那组
     * 「y=474 / 754 / 1412 … 2342」本身就是内容帧坐标。比例分母因此取 2578 而非 2700：
     * 若按 2700 归一，本机「更多」会被放到 2342/2700×2578=2236，系统偏 −106px，直接超出 P-02
     * 的 ±45px 容差。换分辨率时按内容帧高度等比缩放（同构）。
     */
    private static final float REF_CONTENT_H = 2578f;

    // ---- G1：工具钮体量取自原版框（R2-033） ----
    // 原版左列可点框 [27,1291][185,1449] = **158×158**（docs/fidelity/orig/tool-compass-page.xml），
    // 标签 [71,1389][142,1428] = 71×39、框顶到标签顶 98px、标签底到框底 21px。
    // 台账里写的「195×128」是**我方**旧体量 dp(58)×dp(38) @540dpi = 195.75×128.25；
    // 把 docs/fidelity/orig/*.xml 全扫一遍没有任何 195×128 节点，所以按原版 158×158 做。
    private static final int COL_BTN_W = 158;
    private static final int COL_BTN_H = 158;
    private static final int COL_ICON = 72;          // 原版图标位 ~79×72
    private static final int COL_ICON_TOP = 26;      // 框顶→图标顶
    private static final int COL_LABEL_H = 39;       // 标签文字行高（原版 39px）
    private static final int COL_LABEL_BOTTOM = 21;  // 标签底→框底
    // 左/右列的横向边距按各自页面的原版框取：罗盘态 27/26（框 [27..185] / [1044..1202]），
    // 地图态 33/33（框 [33..191] / [1037..1195]）。
    private static final int COL_MARGIN_C_LEFT = 27;
    private static final int COL_MARGIN_C_RIGHT = 26;
    private static final int COL_MARGIN_M_LEFT = 33;
    private static final int COL_MARGIN_M_RIGHT = 33;

    private Button newToolButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(9f);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setBackgroundColor(Color.argb(120, 24, 20, 16));
        b.setTextColor(Color.rgb(235, 228, 215));
        b.setOnClickListener(v -> onToolClick(label));
        return b;
    }

    /**
     * 按原版**框顶**锚定的一列（单位 = REF_CONTENT_H 帧像素）。
     * 用框顶而不是标签中心：左列标签据框顶 117.5px、右列 108.5px（原版两列不同），
     * 用中心锚会把标签整体推掉 38.5px（(158−39)/2−… 的差），这正是「锚点看起来对、标签还偏」的来源。
     */
    private View buildAnchoredColumn(String[] labels, float[] refTop, int[] ids, int iconTop) {
        RatioColumn col = new RatioColumn(this, refTop, COL_BTN_H);
        for (int i = 0; i < labels.length; i++) {
            View b = newColumnButton(labels[i], iconTop);
            if (ids != null && i < ids.length) b.setId(ids[i]);
            col.addView(b);
        }
        return col;
    }

    // ---------------- P1-33-C：罗盘右列 4 枚开关控件 ----------------
    // 语义取证：docs/fidelity/P1-33C-switch-semantics.md（逆向 dac2954/d9d4ec9）
    //   - 都是「切换型」，checked 来自 controller 状态；
    //   - 地图/实景 是**三态互斥**（地图模式 / 实景模式 / 无地图模式），开一个要关另一个；
    //   - 坐南朝北、指针开关 各一个布尔；
    //   - 不持久化（代码里没有 SharedPreferences/MMKV）：**内存态，重启回默认**。
    // 默认值按原版截图像素（审核验 §11.1 4 倍放大）：地图=开、指针开关=开、实景=关、坐南朝北=关。
    private static final int MAP_MODE_MAP = 0;
    private static final int MAP_MODE_REALITY = 1;
    private static final int MAP_MODE_NONE = 2;
    private int compassMapMode = MAP_MODE_MAP;   // 内存态
    private boolean southNorthOn = false;        // 内存态
    private boolean pointerOn = true;            // 内存态
    private final java.util.Map<String, SwitchGlyph> switchGlyphs = new java.util.HashMap<>();

    /** 开关初始状态（与截图默认一致）。 */
    private boolean switchStateOf(String label) {
        switch (label) {
            case "地图": return compassMapMode == MAP_MODE_MAP;
            case "实景": return compassMapMode == MAP_MODE_REALITY;
            case "坐南朝北": return southNorthOn;
            case "指针开关": return pointerOn;
            default: return false;
        }
    }

    /** 点一下 = 切换状态（取证结论），随后仍走原有动作，不改 K-01 行为。 */
    private void toggleSwitchState(String label) {
        switch (label) {
            case "地图":
                compassMapMode = compassMapMode == MAP_MODE_MAP ? MAP_MODE_NONE : MAP_MODE_MAP;
                break;
            case "实景":
                compassMapMode = compassMapMode == MAP_MODE_REALITY ? MAP_MODE_NONE : MAP_MODE_REALITY;
                break;
            case "坐南朝北":
                southNorthOn = !southNorthOn;
                break;
            case "指针开关":
                pointerOn = !pointerOn;
                break;
            default:
                break;
        }
        // 三态互斥：一次点击后刷新全部四枚的开态
        for (java.util.Map.Entry<String, SwitchGlyph> e : switchGlyphs.entrySet()) {
            boolean on = switchStateOf(e.getKey());
            e.getValue().setOn(on);
            if (e.getValue().getParent() instanceof LinearLayout) {
                ((LinearLayout) e.getValue().getParent()).setBackground(
                        on ? switchBackgroundOn() : chipBackground());
            }
        }
        android.util.Log.d("LCSWITCH", "toggle " + label + " mapMode=" + compassMapMode
                + " southNorth=" + southNorthOn + " pointer=" + pointerOn);
    }

    /** 开态按钮底：原版蓝 (50,90,182)、bbox 144×144（四边内缩 7）、圆角 15。 */
    private android.graphics.drawable.Drawable switchBackgroundOn() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        g.setColor(Color.rgb(50, 90, 182));
        g.setCornerRadius(15f);
        return new android.graphics.drawable.InsetDrawable(g, 7, 7, 7, 7);
    }

    /** 按原版框顶锚定的一列**开关**按钮（罗盘右列 4 枚）。 */
    private View buildAnchoredSwitchColumn(String[] labels, float[] refTop, int[] ids) {
        RatioColumn col = new RatioColumn(this, refTop, COL_BTN_H);
        for (int i = 0; i < labels.length; i++) {
            View b = newSwitchButton(labels[i]);
            if (ids != null && i < ids.length) b.setId(ids[i]);
            col.addView(b);
        }
        return col;
    }

    /**
     * P1-33-C 开关按钮：158×158 框 + 药丸轨道（107×54，居中，距框顶 33）+ 小字（中心距框顶 108.5）。
     * 开=蓝底 + 白轨 + 蓝钮靠右；关=深色底 + 灰轨(142,142,142) + 白钮靠左。
     */
    private View newSwitchButton(String label) {
        boolean on = switchStateOf(label);
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        b.setBackground(on ? switchBackgroundOn() : chipBackground());
        b.setPadding(0, 0, 0, 0);
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription(label);
        SwitchGlyph glyph = new SwitchGlyph(this, on);
        switchGlyphs.put(label, glyph);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(107, 54);
        gp.topMargin = 33;
        b.addView(glyph, gp);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11f);
        t.setTextColor(Color.rgb(235, 228, 215));
        t.setGravity(android.view.Gravity.CENTER);
        t.setMaxLines(1);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, COL_LABEL_H);
        tp.topMargin = 2;                       // 33+54+2 = 89 → 标签中心 108.5
        tp.bottomMargin = COL_BTN_H - 89 - COL_LABEL_H;
        b.addView(t, tp);
        b.setOnClickListener(v -> {
            try {
                toggleSwitchState(label);
            } catch (Throwable ex) {
                android.util.Log.e("LCSWITCH", "toggle failed " + label, ex);
            }
            onToolClick(label);                 // 原有动作保持不变（K-01 行为不动）
        });
        return b;
    }

    /** 药丸开关图形：轨道 107×54 + 旋钮 45（关=白钮靠左、开=蓝钮靠右）。 */
    private static final class SwitchGlyph extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private boolean on;

        SwitchGlyph(android.content.Context c, boolean on) {
            super(c);
            this.on = on;
        }

        void setOn(boolean v) {
            if (on != v) {
                on = v;
                invalidate();
            }
        }

        @Override
        protected void onDraw(android.graphics.Canvas cv) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float r = h / 2f;
            paint.setColor(on ? Color.WHITE : Color.rgb(142, 142, 142));
            cv.drawRoundRect(0, 0, w, h, r, r, paint);
            float d = h - 9f;
            float cx = on ? w - r : r;
            paint.setColor(on ? Color.rgb(50, 90, 182) : Color.WHITE);
            cv.drawCircle(cx, h / 2f, d / 2f, paint);
        }
    }

    /**
     * R2-033（判词撤回后重做）：按钮底 = **深色半透明圆角方块**。
     * 原版实测（docs/fidelity/orig/tool-compass-page.png，可点框 [27,1291][185,1449]）：
     * - 绘制区 [33,1297]…[179,1443] ≈ 146×146，即框内**四周内缩 6px**；圆角 ≈ 15px；
     * - 内部两处采样 (40,1305)-(60,1320)=(11.9,25.8,15.0)、(50,1428)-(160,1442)=(11.8,23.8,14.1)；
     * - 同带卫星底图 (2,1350)-(24,1400)=(81.1,118.7,77.6)。
     *
     * 参数取**底板固有值**，不按我方底图反解：
     * 由 (1−a)×底图 ≈ 合成值 的绿通道 25.8/118.7 ≈ 0.22 → **a ≈ 0.78**，C ≈ 黑。
     * 这里用 a=0.80 / C=(0,0,0)（204/255）。注意：合成像素会随底图变——
     * 我方默认是天地图地形（浅色），合成会比原版亮；那是**底图差异（D-021）**，
     * 不是按钮底缺陷。判据比对时须在**卫星底图**下采（同类型底图才可比）。
     */
    private android.graphics.drawable.Drawable chipBackground() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        g.setColor(Color.argb(204, 0, 0, 0));
        g.setCornerRadius(15f);
        return new android.graphics.drawable.InsetDrawable(g, 6, 6, 6, 6);
    }

    /**
     * G1/R2-033：原版形态的工具钮 = 图标 + 小字、158×158 框（深色圆角底）。
     * 图标位固定 72×72，图标顶距取 26（左列/地图列，标签中心距框顶 117.5）或 17（罗盘右列，108.5）；
     * 没有图标的项留同尺寸占位，保证标签几何一致——缺图标自画图形等于造假，宁可留空并列明缺口。
     */
    private View newColumnButton(String label, int iconTop) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        b.setBackground(chipBackground());
        // InsetDrawable 会把它自己的 6px 内缩上报成 View 的 padding，从而把图标与标签整体推下/推右 6px
        // （实测标签从 1389 变 1395，正好落进采样带里）。这里显式归零，让底图内缩只影响绘制、不影响内容几何。
        b.setPadding(0, 0, 0, 0);
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription(label);
        int iconRes = toolIcon(label);
        View icon;
        if (iconRes != 0) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageResource(iconRes);
            // 原版工具列图标是**白色**线稿；A-02 导出的矢量按深色填充入库，
            // 这里只改填充色、不改形状（形状仍来自原版资源，不是手绘）。
            iv.setColorFilter(Color.WHITE);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            icon = iv;
        } else {
            icon = new View(this);   // 占位，不画假图标
        }
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(COL_ICON, COL_ICON);
        ip.topMargin = iconTop;
        b.addView(icon, ip);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11f);
        t.setTextColor(Color.rgb(235, 228, 215));
        t.setGravity(android.view.Gravity.CENTER);
        t.setMaxLines(1);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, COL_LABEL_H);
        tp.bottomMargin = COL_BTN_H - iconTop - COL_ICON - COL_LABEL_H;
        b.addView(t, tp);
        b.setOnClickListener(v -> onToolClick(label));
        return b;
    }

    /**
     * 工具列图标：**原版矢量/位图**，取自 A-02/A-04 入库件
     * （`docs/fidelity/assets/icons/drawer/svg_*.xml`、`assets/tools/ic_*.png`，资源 id 见 assets/INDEX.md）。
     * 形状不改，只做白色着色。P1-33-A 补的 7 确认 + 3 候选（实景/坐南朝北/指针开关 名字直配、
     * smali 里右列按钮无 R$drawable 引用，按候选先用，以截图确认为准）。
     * 仍未覆盖的只有「地图」一项：原版该按钮是 Compose 内联绘制、无独立 drawable
     * （检索清单 `docs/fidelity/assets-P133A.md`，逆向 edcfc5d）——留 72×72 空占位，
     * 不自绘、不拿近似图标顶替。
     */
    private int toolIcon(String label) {
        switch (label) {
            case "截屏": return R.drawable.lc_ic_scropy;
            case "超清优化": return R.drawable.lc_ic_super_map_old;
            case "海拔查询": return R.drawable.lc_ic_altitude;
            case "罗盘": return R.drawable.lc_ic_compass;
            case "路网": return R.drawable.lc_ic_road;
            case "等高线": return R.drawable.lc_ic_isoline;
            case "更多": return R.drawable.lc_ic_more;
            case "图层": return R.drawable.lc_ic_layer;
            case "叠加": return R.drawable.lc_ic_overlay;
            case "山脉": return R.drawable.lc_ic_mountain;
            case "水系": return R.drawable.lc_ic_water;
            case "探索": return R.drawable.lc_ic_discovery;
            case "定位": return R.drawable.lc_ic_locate;
            case "+": return R.drawable.lc_ic_zoom_in;
            case "−": return R.drawable.lc_ic_zoom_out;
            // P1-33-A 补入（逆向 edcfc5d）
            case "全地图工具": return R.drawable.lc_ic_tool_folder;
            case "罗盘复位": return R.drawable.lc_ic_compass_reset;
            case "罗盘锁定": return R.drawable.lc_ic_compass_lock;
            case "读盘": return R.drawable.lc_ic_read_compass;
            case "贴地": return R.drawable.lc_ic_tiedi;
            case "3D": return R.mipmap.lc_ic_3d;
            case "指北针": return R.mipmap.lc_ic_north;
            // 以下三项：**已撤回**（审核验 86a027e 对照图证实语义不符）。
            // 原版右列这四枚不是图标，而是**有开/关两态的开关控件**（蓝=开、灰=关 + 文字）：
            // 地图=蓝(开)、实景=灰(关)、坐南朝北=灰(关)、指针开关=蓝(开)。
            // 早期我按「名字直配」接过 svg_street/svg_surround/svg_compass_switch，
            // 渲染出来是人形/立方体箭头/左右箭头，与原版开关图形不符，已撤掉，留占位等形态口径。
            case "实景":
            case "坐南朝北":
            case "指针开关":
            case "地图": return 0;
            default: return 0;
        }
    }

    /** 按「框顶在原版内容帧里的像素位置」摆放子 View 的容器；宽高由父布局给定（MATCH_PARENT）。 */
    private static final class RatioColumn extends android.view.ViewGroup {
        private final float[] refTop;
        private final int itemH;

        RatioColumn(android.content.Context c, float[] refTop, int itemH) {
            super(c);
            this.refTop = refTop;
            this.itemH = itemH;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            int h = MeasureSpec.getSize(hSpec);
            setMeasuredDimension(w, h);
            int cw = Math.max(0, w - getPaddingLeft() - getPaddingRight());
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(
                        MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(itemH, MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int h = b - t;
            // G1/R2-003：锚点一律按**内容帧 2578** 换算。本 Activity 拿的是整窗 2700（沉浸式，
            // 内容画到导航栏下方），若按 2700 换算就是 2700/2578 = 1.0473 的整列系统性放大
            // （实测 474→496、2342→2453）。我方窗口原点与原版内容帧原点同为屏幕 (0,0)，
            // 所以窗口高于内容帧时应当**直接取 2578** 做绝对锚定，而不是按窗口高缩放。
            int frameH = Math.min(h, (int) REF_CONTENT_H);

            int w = Math.max(0, r - l - getPaddingLeft() - getPaddingRight());
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                int chH = ch.getMeasuredHeight();
                float ref = i < refTop.length ? refTop[i] : 0f;
                int top = Math.round(ref * frameH / REF_CONTENT_H);
                top = Math.max(0, Math.min(top, h - chH));
                ch.layout(getPaddingLeft(), top, getPaddingLeft() + w, top + chH);
            }
        }
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
    /** F-04：路网图层开关；写入 MapManager.showRoad（与设置页「显示路网」同键），并真正调 setRoadNet。 */
    private void toggleRoadNet() {
        roadNetOn = !roadNetOn;
        getSharedPreferences("MapManager", MODE_PRIVATE).edit()
                .putBoolean("showRoad", roadNetOn).apply();
        js("setRoadNet(" + roadNetOn + ")");
        toast("路网：" + (roadNetOn ? "开" : "关"));
    }

    private void js(String code) {        if (compassMapView == null) return;
        compassMapView.evaluateJavascript("try{" + code + "}catch(e){}", null);
    }

    /**
     * 打开带中心点的功能页（F-06 山脉 / F-07 水系）：把当前地图中心作为 lc_lat/lc_lng 传入，
     * 对齐原版 MountainActivity$a.b(Context, lat, lng) 的语义。取不到中心时退回不带 extras。
     */
    private void openFeatureAtCenter(Class<?> cls) {
        if (compassMapView == null) {
            startActivity(new android.content.Intent(this, cls));
            return;
        }
        compassMapView.evaluateJavascript(
                "(function(){var s=getState();return s.lat+','+s.lng;})()",
                v -> {
                    double lat = Double.NaN;
                    double lng = Double.NaN;
                    String[] parts = decodeJsString(v).split(",");
                    if (parts.length == 2) {
                        try {
                            lat = Double.parseDouble(parts[0].trim());
                            lng = Double.parseDouble(parts[1].trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    android.content.Intent i = new android.content.Intent(this, cls);
                    if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                        i.putExtra(MapJump.EXTRA_LAT, lat);
                        i.putExtra(MapJump.EXTRA_LNG, lng);
                    }
                    startActivity(i);
                });
    }

    // ---------------- F-17（LC 自建，非 1:1）：罗盘显示 ----------------

    /**
     * 地图侧 uiMode 下发（compass / map）。
     *
     * 冷启时 map.html 还没 load 完，`js()` 里的 evaluateJavascript 会被 try/catch 静默吞掉；
     * 原实现靠 onPageFinished 重放一次，这里再补一个 pendingUiMode：未就绪时先记下，
     * 页面就绪后再发，避免「浮层已开、地图仍停在 full 态」的不同步。
     */
    private void applyMapUiMode(String mode) {
        if (compassMapView == null) return;
        if (!mapPageReady) {
            pendingUiMode = mode;
            android.util.Log.d("LCCOMPASS", "uiMode deferred: " + mode + " (map not ready)");
            return;
        }
        pendingUiMode = null;
        android.util.Log.d("LCCOMPASS", "uiMode -> " + mode);
        js("setUiMode('" + mode + "')");
    }

    /** 应用落盘值；flag 关闭时回退默认（100% / 天支线关），不出现任何控件。 */
    private void applyCompassDisplay() {
        if (compassView == null || compassDisplay == null) return;
        boolean on = new FeatureFlags(this).isEnabled("f17");
        int alpha = on ? compassDisplay.alphaPercent() : 100;
        boolean tianzhi = on && compassDisplay.tianzhiLines();
        android.util.Log.d("LCCOMPASS", "apply display f17=" + on + " alpha=" + alpha + " tianzhi=" + tianzhi);
        compassView.setDialAlpha(alpha / 100f);
        compassView.setTianzhiLines(tianzhi);
    }

    /** 罗盘工具条「透明度 / 天支线」打开的 LC 自建面板：滑杆 + 开关，即时生效并持久化。 */
    private void showCompassDisplayPanel() {
        if (compassDisplay == null) compassDisplay = new CompassDisplayStore(this);
        final android.app.AlertDialog[] ref = new android.app.AlertDialog[1];

        LinearLayout panel = new LinearLayout(this);
        panel.setId(R.id.lc_compass_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(10), dp(18), dp(8));

        TextView title = new TextView(this);
        title.setText(R.string.lc_compass_display_title);
        title.setTextSize(16f);
        title.setTextColor(Color.rgb(235, 228, 215));
        panel.addView(title);

        // 透明度：0–100%，只作用底盘 alpha。
        LinearLayout alphaRow = new LinearLayout(this);
        alphaRow.setOrientation(LinearLayout.HORIZONTAL);
        alphaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        alphaRow.setPadding(0, dp(10), 0, 0);
        TextView alphaLabel = new TextView(this);
        alphaLabel.setText(R.string.lc_compass_alpha);
        alphaLabel.setTextSize(14f);
        alphaLabel.setTextColor(Color.rgb(235, 228, 215));
        alphaRow.addView(alphaLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView alphaValue = new TextView(this);
        alphaValue.setId(R.id.lc_compass_alpha_value);
        alphaValue.setTextSize(14f);
        alphaValue.setTextColor(Color.rgb(220, 190, 130));
        alphaValue.setText(compassDisplay.alphaPercent() + "%");
        alphaRow.addView(alphaValue);
        panel.addView(alphaRow);

        // 下限 30%（不提供 0%）：0 会让整盘消失且会被持久化，下次冷启直接空盘。
        // minSdk 24 没有 SeekBar.setMin，用「进度 = 实际值 - 下限」换算。
        final int minAlpha = CompassDisplayStore.MIN_ALPHA;
        final SeekBar slider = new SeekBar(this);
        slider.setId(R.id.lc_compass_alpha_slider);
        slider.setMax(100 - minAlpha);
        slider.setProgress(compassDisplay.alphaPercent() - minAlpha);
        panel.addView(slider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int percent = minAlpha + progress;
                alphaValue.setText(percent + "%");
                if (compassView != null) compassView.setDialAlpha(percent / 100f);
                compassDisplay.setAlphaPercent(percent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });

        // 天干地支辅助线：默认关。
        LinearLayout tRow = new LinearLayout(this);
        tRow.setOrientation(LinearLayout.HORIZONTAL);
        tRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tRow.setPadding(0, dp(8), 0, dp(8));
        TextView tLabel = new TextView(this);
        tLabel.setText(R.string.lc_compass_tianzhi);
        tLabel.setTextSize(14f);
        tLabel.setTextColor(Color.rgb(235, 228, 215));
        tRow.addView(tLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LcPillSwitch sw = new LcPillSwitch(this);
        sw.setId(R.id.lc_compass_tianzhi_toggle);
        sw.setChecked(compassDisplay.tianzhiLines());
        tRow.addView(sw, new LinearLayout.LayoutParams(
                LcUi.dp(this, 46.2f), LcUi.dp(this, 23.1f)));
        sw.setOnCheckedChangeListener((v, checked) -> {
            compassDisplay.setTianzhiLines(checked);
            android.util.Log.d("LCCOMPASS", "panel tianzhi toggle -> " + checked + " (persisted)");
            if (compassView != null) compassView.setTianzhiLines(checked);
        });
        panel.addView(tRow);

        Button close = new Button(this);
        close.setId(R.id.lc_compass_close);
        close.setText(R.string.lc_compass_close);
        close.setOnClickListener(v -> {
            if (ref[0] != null) ref[0].dismiss();
        });
        panel.addView(close);

        ref[0] = new android.app.AlertDialog.Builder(this)
                .setView(panel)
                .create();
        ref[0].show();
    }

    private void onToolClick(String label) {
        switch (label) {
            case "罗盘": setCompassMode(true); break;
            case "全地图工具":
                // F-01：默认开 → 工具管理页（原版是付费分发页，LC 不实现）；关掉回退旧 stub。
                if (new FeatureFlags(this).isEnabled("f01")) {
                    startActivity(new android.content.Intent(this, ToolsManagerActivity.class));
                } else {
                    toast(label + "：原版为工具子页，本步未实现");
                }
                break;
            case "水系":
                // F-07：右列入「水系」→ WaterActivity（默认开，无门槛）；关掉回退旧 stub。
                if (new FeatureFlags(this).isEnabled("f07")) {
                    openFeatureAtCenter(WaterActivity.class);
                } else {
                    toast(label + "：原版为工具子页，本步未实现");
                }
                break;
            case "探索":
                // F-08：右列「探索」→ DiscoveryActivity（默认开，无门槛）；关掉回退旧 stub。
                if (new FeatureFlags(this).isEnabled("f08")) {
                    startActivity(new android.content.Intent(this, DiscoveryActivity.class));
                } else {
                    toast(label + "：原版为工具子页，本步未实现");
                }
                break;
            case "叠加":
                // F-05：右列「叠加」→ OverlayActivity（默认开，无门槛）；关掉回退旧 stub。
                if (new FeatureFlags(this).isEnabled("f05")) {
                    startActivity(new android.content.Intent(this, OverlayActivity.class));
                } else {
                    toast(label + "：原版为工具子页，本步未实现");
                }
                break;
            case "超清优化":
                // F-02：左列「超清优化」→ SuperMapActivity（默认开，无门槛）；关掉回退旧 stub。
                if (new FeatureFlags(this).isEnabled("f02")) {
                    startActivity(new android.content.Intent(this, SuperMapActivity.class));
                } else {
                    toast(label + "：原版为工具子页，本步未实现");
                }
                break;
            case "海拔查询":
                // F-03：左列「海拔查询」→ ElevationActivity（默认开，无门槛）；关掉回退旧 stub。
                if (new FeatureFlags(this).isEnabled("f03")) {
                    enterElevationMode();
                } else {
                    toast(label + "：原版为工具子页，本步未实现");
                }
                break;
            case "测量":
                // F-12：更多 → 测量（地图内交互，再点退出）；默认开，关掉回退旧 stub。
                if (new FeatureFlags(this).isEnabled("f12")) {
                    toggleMeasure();
                } else {
                    toast(label + "：原版为工具子页，本步未实现");
                }
                break;
            case "画笔":
                // F-13：更多 → 画笔（地图内交互，再点退出）；默认开，关掉回退旧 stub。
                if (new FeatureFlags(this).isEnabled("f13")) {
                    toggleDraw();
                } else {
                    toast(label + "：原版为工具子页，本步未实现");
                }
                break;
            case "截屏": captureMap(); break;
            case "山脉":
                // R2-030：原版点「山脉」不跳页，山名落在地图上。LC 改为地图内半屏图层（前台仍是 MainActivity）。
                // 数据沿用 MountainActivity 的免费源：天地图地名搜索「分类 260203（山）」；关掉 f06 才回退旧 stub。
                if (new FeatureFlags(this).isEnabled("f06")) {
                    toggleMountainLayer();
                } else {
                    toast(label + "：原版为工具子页，本步未实现");
                }
                break;
            case "读盘":
                if (compassView != null) CompassReadSheet.show(this, compassView.getHeading());
                break;
            case "知识": showKnowledgeSheet(); break;
            case "更多": showMoreSheet(); break;
            case "图层": js("openSources()"); break;
            case "等高线": js("toggleContour()"); break;
            case "路网":
                // F-04：左列「路网」= 路网图层开关（默认开，持久化 MapManager.showRoad）。
                if (new FeatureFlags(this).isEnabled("f04")) {
                    toggleRoadNet();
                } else {
                    toast(label + "：原版为工具子页，本步未实现");
                }
                break;
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
            // F-17 的两个入口：透明度 / 天支线。
            // 注意：这两个按钮只存在于罗盘模式的底部工具条（buildCompassToolBar 仅在 setCompassMode(true) 时可见），
            // 不在罗盘态点这个位置点到的是地图——AT 复验前先确认已在罗盘浮层。
            case "透明度":
                if (new FeatureFlags(this).isEnabled("f17")) {
                    showCompassDisplayPanel();
                } else {
                    toast("透明度：待 measurements.md（face alpha 实测）");
                }
                break;
            case "天支线":
                if (new FeatureFlags(this).isEnabled("f17")) {
                    showCompassDisplayPanel();
                } else {
                    toast("天支线：圈层开关，待 step-3");
                }
                break;
            default: toast(label + "：原版为工具子页，本步未实现");
        }
    }

    /** K-01：罗盘＝同一页面的浮层，切的是「表现」不是页面。 */
    private void setCompassMode(boolean on) {
        if (on) {
            // 进罗盘态前先退出测量/画笔态，避免浮条留在浮层上。
            exitMeasure();
            exitDraw();
        }
        compassMode = on;
        if (on && favoriteMode) {
            // 进罗盘态先退出收藏点地图态（不再回调 setFavoriteMode，避免递归）。
            favoriteMode = false;
            if (rightFavoriteColumn != null) rightFavoriteColumn.setVisibility(View.GONE);
            if (favoriteBottomBar != null) favoriteBottomBar.setVisibility(View.GONE);
        }
        if (!on && compassHidden) {
            compassHidden = false;
            if (hideButton != null) hideButton.setText("隐藏");
        }
        if (!on) hidePoiPanel();
        if (compassView != null) compassView.setVisibility(on ? View.VISIBLE : View.GONE);
        if (leftColumn != null) leftColumn.setVisibility(on ? View.GONE : View.VISIBLE);
        if (rightColumn != null) rightColumn.setVisibility(on ? View.GONE : View.VISIBLE);
        if (leftCompassColumn != null) leftCompassColumn.setVisibility(on ? View.VISIBLE : View.GONE);
        if (rightCompassColumn != null) rightCompassColumn.setVisibility(on ? View.VISIBLE : View.GONE);
        if (compassToolBar != null) compassToolBar.setVisibility(on ? View.VISIBLE : View.GONE);
        if (hideButton != null) hideButton.setVisibility(on ? View.VISIBLE : View.GONE);
        if (topBackButton != null) topBackButton.setVisibility(on ? View.VISIBLE : View.GONE);
        if (topLayerButton != null) topLayerButton.setVisibility(on ? View.VISIBLE : View.GONE);
        // R2-003：读数面板只在罗盘态出现（含隐藏子态）；R2-026：地图态左上改显当前地名。
        if (readingView != null) readingView.setVisibility(on ? View.VISIBLE : View.GONE);
        if (placeView != null) placeView.setVisibility(on ? View.GONE : View.VISIBLE);
        if (!on) refreshPlaceName();
        applyMapUiMode(on ? "compass" : "map");
        applyCompassHiddenVisuals();
        updateReading();
    }

    // ---------------- R2-006：隐藏只隐盘面 ----------------

    /**
     * R2-026：刷新地图态左上地名。原版该处的「青海湖 / 哀牢山」是短地名，
     * 不是长地址；天地图逆地理的 `addressComponent.poi`（青海湖实测 = "青海湖"）正好是这一形态，
     * 所以优先 poi、其次 address，都空才退回 formatted_address。
     */
    private void refreshPlaceName() {
        if (compassMapView == null || placeView == null) return;
        compassMapView.evaluateJavascript("(function(){var s=getState();return s.lat+','+s.lng;})()", c -> {
            String[] p = decodeJsString(c).split(",");
            if (p.length != 2) return;
            final double lat, lng;
            try {
                lat = Double.parseDouble(p[0].trim());
                lng = Double.parseDouble(p[1].trim());
            } catch (NumberFormatException e) {
                return;
            }
            new Thread(() -> {
                final String name = reversePlaceName(lat, lng);
                runOnUiThread(() -> {
                    if (placeView == null || name.isEmpty()) return;
                    placeView.setText(name);
                    android.util.Log.d("LCPLACE", "place=" + name
                            + " center=" + lat + "," + lng);
                });
            }, "lc-place").start();
        });
    }

    /** R2-026：逆地理取短地名（poi → address → formatted_address）。失败返回空串，不编造。 */
    private static String reversePlaceName(double lat, double lng) {
        try {
            String post = "{\"lon\":" + lng + ",\"lat\":" + lat + ",\"ver\":1}";
            String url = "https://api.tianditu.gov.cn/geocoder?postStr="
                    + java.net.URLEncoder.encode(post, "UTF-8") + "&type=geocode&tk=" + MapKeys.TDT;
            org.json.JSONObject root = new org.json.JSONObject(LcHttp.get(url));
            org.json.JSONObject result = root.optJSONObject("result");
            if (result == null) return "";
            org.json.JSONObject ac = result.optJSONObject("addressComponent");
            if (ac != null) {
                String poi = ac.optString("poi", "");
                if (!poi.isEmpty()) return poi;
                String addr = ac.optString("address", "");
                if (!addr.isEmpty()) return addr;
            }
            return result.optString("formatted_address", "");
        } catch (Throwable t) {
            android.util.Log.e("LCPLACE", "reverse failed", t);
            return "";
        }
    }

    /** 坐标条：取地图中心 getState()，格式与原版 hidden 态一致（全角逗号）。 */
    private void updateCoordText() {
        if (compassMapView == null || coordView == null) return;
        compassMapView.evaluateJavascript("(function(){var s=getState();return s.lat+','+s.lng;})()", c -> {
            String v = decodeJsString(c);
            String[] p = v == null ? new String[0] : v.split(",");
            if (p.length != 2) return;
            try {
                double lat = Double.parseDouble(p[0].trim());
                double lng = Double.parseDouble(p[1].trim());
                // R2-025：顺手更新中心缓存，搜索不再为取中心点等一次 JS 往返
                mapCenterLat = lat;
                mapCenterLng = lng;
                coordView.setText(String.format(java.util.Locale.US, "%.4f，%.4f", lng, lat));
            } catch (NumberFormatException ignored) {
            }
        });
    }

    /**
     * 比例尺：按地图 zoom/纬度算 150px 对应的实际距离，取整档显示（原版固定态是 4km）。
     * 参考 156543.03392 m/px @ zoom0/equator，256px 瓦片。
     */
    private void updateScaleText() {
        if (compassMapView == null || scaleView == null) return;
        compassMapView.evaluateJavascript(
                "(function(){var s=getState();return s.lat+','+s.zoom;})()", c -> {
                    String v = decodeJsString(c);
                    String[] p = v == null ? new String[0] : v.split(",");
                    if (p.length != 2) return;
                    try {
                        double lat = Double.parseDouble(p[0].trim());
                        double zoom = Double.parseDouble(p[1].trim());
                        double mpp = 156543.03392 * Math.cos(Math.toRadians(lat)) / Math.pow(2, zoom);
                        double meters = mpp * 150.0;
                        String label;
                        if (meters >= 1000) {
                            double km = meters / 1000.0;
                            double nice = km >= 8 ? Math.round(km / 5.0) * 5.0
                                    : km >= 4 ? Math.round(km / 2.0) * 2.0 : Math.max(1, Math.round(km));
                            label = String.format(java.util.Locale.US, "%.0fkm", nice);
                        } else {
                            label = String.format(java.util.Locale.US, "%.0fm",
                                    Math.max(10, Math.round(meters / 10.0) * 10.0));
                        }
                        scaleView.setText(label);
                    } catch (NumberFormatException ignored) {
                    }
                });
    }

    private void toggleCompassHidden() {
        if (!compassMode) return;
        compassHidden = !compassHidden;
        if (hideButton != null) hideButton.setText(compassHidden ? "显示" : "隐藏");
        applyCompassHiddenVisuals();
        updateReading();   // R2-006：隐藏态读数条切单行「坐X.X° 向Y.Y°」
        android.util.Log.d("LCCOMPASS", "hidden=" + compassHidden);
    }

    private void setCompassHidden(boolean hidden) {
        compassHidden = hidden;
        if (hideButton != null) hideButton.setText(hidden ? "显示" : "隐藏");
        applyCompassHiddenVisuals();
    }

    /** 隐藏态：盘面/指针/左右工具列/底栏全部消失；读数条与坐标由 readingView 常驻保留。 */
    private void applyCompassHiddenVisuals() {
        boolean showCompass = compassMode && !compassHidden;
        if (compassView != null) compassView.setVisibility(showCompass ? View.VISIBLE : View.GONE);
        if (leftCompassColumn != null) leftCompassColumn.setVisibility(showCompass ? View.VISIBLE : View.GONE);
        if (rightCompassColumn != null) rightCompassColumn.setVisibility(showCompass ? View.VISIBLE : View.GONE);
        if (compassToolBar != null) compassToolBar.setVisibility(showCompass ? View.VISIBLE : View.GONE);
        if (hideButton != null) hideButton.setVisibility(compassMode ? View.VISIBLE : View.GONE);
        if (topBackButton != null) topBackButton.setVisibility(compassMode ? View.VISIBLE : View.GONE);
        if (topLayerButton != null) topLayerButton.setVisibility(compassMode ? View.VISIBLE : View.GONE);
        if (menuButton != null) menuButton.setVisibility(compassMode ? View.GONE : View.VISIBLE);
        if (coordView != null) coordView.setVisibility(compassMode || favoriteMode ? View.VISIBLE : View.GONE);
        if (compassMode) updateCoordText();
    }

    // ---------------- R2-014：收藏点地图态 ----------------

    private void setFavoriteMode(boolean on) {
        if (on) {
            exitMeasure();
            exitDraw();
            if (compassMode) setCompassMode(false);
            hidePoiPanel();
        }
        favoriteMode = on;
        if (rightFavoriteColumn != null) rightFavoriteColumn.setVisibility(on ? View.VISIBLE : View.GONE);
        if (favoriteBottomBar != null) favoriteBottomBar.setVisibility(on ? View.VISIBLE : View.GONE);
        // R2-014：收藏点地图态补比例尺与坐标条（原版 [79,2510][150,2550] 4km + 116.4074，39.9042）。
        if (coordView != null) coordView.setVisibility(on || compassMode ? View.VISIBLE : View.GONE);
        if (scaleView != null) scaleView.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            updateCoordText();
            updateScaleText();
        }
        if (leftColumn != null) leftColumn.setVisibility(on ? View.GONE : (compassMode ? View.GONE : View.VISIBLE));
        if (rightColumn != null) rightColumn.setVisibility(on ? View.GONE : (compassMode ? View.GONE : View.VISIBLE));
        android.util.Log.d("LCFAV", "favoriteMode=" + on);
    }

    /** 收藏列表：地图态的子页，返回后仍在收藏点地图态。 */
    private void openFavoriteList() {
        startActivity(new android.content.Intent(this, InterestListActivity.class));
    }

    /** 「添加点」：用当前地图中心建收藏点（原版底部大按钮）。 */
    private void addFavoriteAtCenter() {
        if (compassMapView == null) return;
        compassMapView.evaluateJavascript("(function(){var s=getState();return s.lat+','+s.lng;})()", c -> {
            String v = decodeJsString(c);
            String[] parts = v == null ? new String[0] : v.split(",");
            if (parts.length != 2) {
                toast("地图未就绪，稍后再试");
                return;
            }
            try {
                double lat = Double.parseDouble(parts[0].trim());
                double lng = Double.parseDouble(parts[1].trim());
                android.content.Intent i = new android.content.Intent(this, SaveInterestActivity.class);
                i.putExtra(MapJump.EXTRA_LAT, lat);
                i.putExtra(MapJump.EXTRA_LNG, lng);
                i.putExtra(SaveInterestActivity.EXTRA_SITTING, bearingSitting);
                i.putExtra(SaveInterestActivity.EXTRA_FACING, bearingFacing);
                startActivity(i);
            } catch (NumberFormatException e) {
                toast("地图未就绪，稍后再试");
            }
        });
    }

    // ---------------- R2-018：左上抽屉（9 项 + 版本号） ----------------

    private static final String[] DRAWER_ITEMS = {
            "地图设置", "账户设置", "投诉反馈", "关于我们", "联系我们",
            "推荐给好友", "开具发票", "帮助中心", "用户协议"};

    private void showDrawer() {
        if (drawerPanel == null) drawerPanel = buildDrawer();
        drawerPanel.setVisibility(View.VISIBLE);
        drawerPanel.bringToFront();
        android.util.Log.d("LCDRAWER", "open");
    }

    /** R2-018：推荐给好友 = 系统分享面板（真实动作，不造内容）。 */
    private void shareApp() {
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(android.content.Intent.EXTRA_TEXT, "三维山水全景地图（LC）");
        startActivity(android.content.Intent.createChooser(send, "推荐给好友"));
    }

    private void hideDrawer() {
        if (drawerPanel != null) drawerPanel.setVisibility(View.GONE);
    }

    private LinearLayout buildFavoriteColumn() {
        LinearLayout col = new LinearLayout(this);
        col.setId(R.id.lc_fav_column);
        col.setOrientation(LinearLayout.VERTICAL);
        Button list = newToolButton("收藏列表");
        list.setId(R.id.lc_fav_list);
        list.setTextSize(11f);
        list.setOnClickListener(v -> openFavoriteList());
        Button contour = newToolButton("等高线");
        contour.setId(R.id.lc_fav_contour);
        contour.setTextSize(11f);
        contour.setOnClickListener(v -> js("toggleContour()"));
        col.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        col.addView(contour, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        return col;
    }

    private LinearLayout buildFavoriteBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setId(R.id.lc_fav_bottom);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Color.argb(170, 24, 20, 16));
        Button add = newToolButton("添加点");
        add.setId(R.id.lc_fav_add);
        add.setTextSize(13f);
        add.setOnClickListener(v -> addFavoriteAtCenter());
        bar.addView(add, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        return bar;
    }

    private LinearLayout buildDrawer() {
        LinearLayout panel = new LinearLayout(this);
        panel.setId(R.id.lc_drawer_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.argb(248, 26, 22, 18));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView close = new TextView(this);
        close.setText("← 返回");
        close.setTextSize(12f);
        close.setTextColor(Color.rgb(220, 190, 130));
        close.setPadding(dp(12), dp(12), dp(12), dp(12));
        close.setOnClickListener(v -> hideDrawer());
        header.addView(close);
        panel.addView(header);

        for (String label : DRAWER_ITEMS) {
            Button item = new Button(this);
            item.setText(label);
            item.setAllCaps(false);
            item.setTextSize(13f);
            item.setTextColor(Color.rgb(235, 228, 215));
            item.setBackgroundColor(Color.TRANSPARENT);
            item.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            item.setPadding(dp(16), 0, dp(8), 0);
            item.setOnClickListener(v -> {
                hideDrawer();
                // R2-018：9 项每项都到达真实目标，不允许 toast 占位。
                if ("地图设置".equals(label)) {
                    startActivity(new android.content.Intent(this, SettingActivity.class));
                } else if ("推荐给好友".equals(label)) {
                    shareApp();
                } else {
                    android.content.Intent info = new android.content.Intent(this, InfoPageActivity.class);
                    info.putExtra(InfoPageActivity.EXTRA_TITLE, label);
                    startActivity(info);
                }
            });
            panel.addView(item, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        }

        TextView version = new TextView(this);
        version.setId(R.id.lc_drawer_version);
        version.setText("三维山水全景地图V 1.6.1");
        version.setTextSize(10f);
        version.setTextColor(Color.rgb(150, 130, 100));
        version.setPadding(dp(16), dp(18), dp(8), dp(8));
        panel.addView(version);
        return panel;
    }

    // ---------------- R2-004：POI 详情面板 ----------------

    /** 面板固定高 443px（原版底部卡片 2135–2578）。 */
    private static final int POI_PANEL_H_PX = 443;

    /**
     * 内容帧之外的底部占位（窗口 2700 − 内容帧 2578 = 122px，见 REF_CONTENT_H 注释）。
     * 沉浸式下 navigationBars inset 取到 0，所以直接用实测高度差，保证面板落在 [2135,2578]。
     */
    private int navBarInsetPx() {
        try {
            if (content != null && content.getHeight() > 0) {
                return Math.max(0, content.getHeight() - (int) REF_CONTENT_H);
            }
        } catch (Exception ignored) {
        }
        return 122;
    }

    // ---------------- R2-007：海拔查询地图内浮层 ----------------

    private void enterElevationMode() {
        setFavoriteMode(false);
        hidePoiPanel();
        elevationMode = true;
        js("setBase('img');lc_elev_query(true);");
        toast("点地图任意位置查询海拔");
        android.util.Log.d("LCELEV", "mode on");
    }

    private void showElevationPanel(MapMarker m) {
        if (m == null) return;
        elevPoint = m;
        if (elevPanel == null) elevPanel = buildElevationPanel();
        if (elevPanel.getParent() == null && content != null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.BOTTOM;
            lp.bottomMargin = navBarInsetPx();
            content.addView(elevPanel, lp);
        }
        elevPanel.setVisibility(View.VISIBLE);
        refreshElevationText();
        if (m.elevM == null && compassMapView != null) {
            // 2D 点选事件不带高程：回查 map.html 的 elevAt(lat,lng)（与 ElevationActivity 同一 DEM 源）。
            compassMapView.evaluateJavascript(String.format(java.util.Locale.US,
                    "(function(){try{var e=elevAt(%.6f,%.6f);return e===null?null:e;}catch(err){return null;}})()",
                    m.lat, m.lng), c -> {
                String v = decodeJsString(c);
                if (v == null || v.isEmpty() || "null".equals(v)) return;
                try {
                    double e = Double.parseDouble(v.trim());
                    elevPoint = new MapMarker(m.lng, m.lat, e, m.headingAtPick, "dem");
                    refreshElevationText();
                } catch (NumberFormatException ignored) {
                }
            });
        }
        android.util.Log.d("LCELEV", "show " + m);
    }

    private void hideElevationPanel() {
        if (elevPanel != null) elevPanel.setVisibility(View.GONE);
        elevationMode = false;
        js("lc_elev_query(false)");
    }

    /**
     * 含氧量：原版本地公式 20.95 − 0.0016×海拔（AltitudeToolsController.r，d.smali 263-314，
     * 常量 20.95/0.0016 见 294/288 行），1:1 照抄，不加「估算」字样。
     * 气压：原版取服务端实时值（api/weather/getPressure），本地无公式，本行仍是本地估算并标「估算」，
     * 是否接源或挂 DEP-3 由总监裁定。
     */
    private void refreshElevationText() {
        MapMarker m = elevPoint;
        if (m == null || elevPanel == null) return;
        double elev = m.elevM == null ? Double.NaN : m.elevM;
        TextView head = elevPanel.findViewById(R.id.lc_elev_value);
        TextView oxy = elevPanel.findViewById(R.id.lc_elev_o2);
        TextView baro = elevPanel.findViewById(R.id.lc_elev_pressure);
        if (head != null) {
            head.setText(Double.isNaN(elev)
                    ? String.format(java.util.Locale.US, "海拔 —    纬度:%.6f 经度:%.6f", m.lat, m.lng)
                    : String.format(java.util.Locale.US, "海拔 %.1f m（本地 DEM）    纬度:%.6f 经度:%.6f", elev, m.lat, m.lng));
        }
        if (oxy != null) {
            oxy.setText(Double.isNaN(elev) ? "含氧量 —"
                    : String.format(java.util.Locale.US, "含氧量 %.2f %%", 20.95 - 0.0016 * elev));
        }
        if (baro != null) {
            double hPa = 1013.25 * Math.pow(1 - 2.25577e-5 * (Double.isNaN(elev) ? 0 : elev), 5.25588);
            baro.setText(Double.isNaN(elev) ? "气压 —"
                    : String.format(java.util.Locale.US, "气压 %.2f 千帕（本地估算·数据源差异 DI）", hPa / 10.0));
        }
    }

    private LinearLayout buildElevationPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setId(R.id.lc_elev_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.argb(242, 26, 22, 18));
        panel.setPadding(dp(14), dp(8), dp(14), dp(8));
        TextView head = new TextView(this);
        head.setId(R.id.lc_elev_value);
        head.setTextSize(14f);
        head.setTextColor(Color.rgb(235, 228, 215));
        panel.addView(head);
        TextView oxy = new TextView(this);
        oxy.setId(R.id.lc_elev_o2);
        oxy.setTextSize(12f);
        oxy.setTextColor(Color.rgb(200, 190, 170));
        panel.addView(oxy);
        TextView baro = new TextView(this);
        baro.setId(R.id.lc_elev_pressure);
        baro.setTextSize(12f);
        baro.setTextColor(Color.rgb(200, 190, 170));
        panel.addView(baro);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button mark = new Button(this);
        mark.setId(R.id.lc_elev_mark);
        mark.setText("打点");
        mark.setAllCaps(false);
        mark.setTextSize(12f);
        mark.setOnClickListener(v -> {
            if (elevPoint == null) return;
            js(String.format(java.util.Locale.US, "try{dropMarkerAt(%.6f,%.6f)}catch(e){}", elevPoint.lat, elevPoint.lng));
            toast("已打点");
            android.util.Log.d("LCELEV", "mark " + elevPoint.lat + "," + elevPoint.lng);
        });
        Button close = new Button(this);
        close.setId(R.id.lc_elev_close);
        close.setText("关闭");
        close.setAllCaps(false);
        close.setTextSize(12f);
        close.setOnClickListener(v -> hideElevationPanel());
        actions.addView(mark, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(close, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(actions);
        return panel;
    }

    private void showPoiPanel(MapMarker m) {
        if (m == null) return;
        poiMarker = m;
        if (poiPanel == null) poiPanel = buildPoiPanel();
        if (poiPanel.getParent() == null && content != null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, POI_PANEL_H_PX);
            lp.gravity = android.view.Gravity.BOTTOM;
            // 窗口是 2700 高、内容帧只到 2578：底部 122px 在导航栏下，面板会被裁掉。
            // 用导航栏 inset 做下边距，面板才真正落在 [2135,2578]（高 443）。
            lp.bottomMargin = navBarInsetPx();
            content.addView(poiPanel, lp);
        }
        poiPanel.setVisibility(View.VISIBLE);
        refreshPoiText();
        android.util.Log.d("LCPOI", "show " + m);
    }

    private void hidePoiPanel() {
        if (poiPanel != null) poiPanel.setVisibility(View.GONE);
    }

    // ---------------- R2-030 山脉：地图内半屏图层（不跳 Activity） ----------------

    /** 点一次开、再点一次关；关闭按钮走 hideMountainLayer()，前台始终是 MainActivity。 */
    private void toggleMountainLayer() {
        if (mountainLayerOn) {
            hideMountainLayer();
        } else {
            showMountainLayer();
        }
    }

    private void showMountainLayer() {
        if (mountainLayer == null) mountainLayer = buildMountainLayer();
        if (mountainLayer.getParent() == null && content != null) {
            // 半屏：内容帧高 2578，面板取 1290（约一半），底部留导航栏 inset
            int h = Math.round(getResources().getDisplayMetrics().heightPixels * 0.48f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, h);
            lp.gravity = android.view.Gravity.BOTTOM;
            lp.bottomMargin = navBarInsetPx();
            content.addView(mountainLayer, lp);
        }
        mountainLayer.setVisibility(View.VISIBLE);
        mountainLayerOn = true;
        TextView status = mountainLayer.findViewById(R.id.lc_mtn_status);
        if (status != null) status.setText("视野内山名：查询中…（天地图地名搜索 · 分类 260203 山）");
        LinearLayout list = mountainLayer.findViewById(R.id.lc_mtn_list);
        if (list != null) list.removeAllViews();
        fetchMountainNamesForLayer();
        android.util.Log.d("LCMTN", "layer on");
    }

    private void hideMountainLayer() {
        if (mountainLayer != null) mountainLayer.setVisibility(View.GONE);
        mountainLayerOn = false;
        android.util.Log.d("LCMTN", "layer off");
    }

    private LinearLayout buildMountainLayer() {
        LinearLayout panel = new LinearLayout(this);
        panel.setId(R.id.lc_mtn_layer);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.argb(242, 26, 22, 18));
        panel.setPadding(dp(14), dp(8), dp(14), dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this);
        title.setId(R.id.lc_mtn_title);
        title.setText("看山脉");
        title.setTextSize(15f);
        title.setTextColor(Color.rgb(235, 228, 215));
        head.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button close = new Button(this);
        close.setId(R.id.lc_mtn_close);
        close.setText("关闭");
        close.setAllCaps(false);
        close.setTextSize(11f);
        close.setOnClickListener(v -> hideMountainLayer());
        head.addView(close);
        panel.addView(head, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(this);
        status.setId(R.id.lc_mtn_status);
        status.setText("视野内山名：查询中…（天地图地名搜索 · 分类 260203 山）");
        status.setTextSize(11f);
        status.setTextColor(Color.rgb(170, 162, 150));
        panel.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setId(R.id.lc_mtn_list);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return panel;
    }

    /** 取当前地图中心（与 openFeatureAtCenter 同一取法），再按 MountainActivity 的链路查山名。 */
    private void fetchMountainNamesForLayer() {
        if (compassMapView == null) {
            queryMountainNamesForLayer(39.9042, 116.4074);
            return;
        }
        compassMapView.evaluateJavascript(
                "(function(){var s=getState();return s.lat+','+s.lng;})()",
                v -> {
                    double lat = 39.9042, lng = 116.4074;
                    String[] parts = decodeJsString(v).split(",");
                    if (parts.length == 2) {
                        try {
                            lat = Double.parseDouble(parts[0].trim());
                            lng = Double.parseDouble(parts[1].trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    queryMountainNamesForLayer(lat, lng);
                });
    }

    private void queryMountainNamesForLayer(final double lat, final double lng) {
        final int seq = ++mountainFetchSeq;
        new Thread(() -> {
            java.util.List<MapPoints.Point> pts = null;
            String note = "";
            try {
                String province = MapPoints.provinceCode(lat, lng);
                pts = MapPoints.keywordNear("山", lat, lng, 10, 120);
                if (pts == null || pts.isEmpty()) {
                    pts = MapPoints.category(MapKeys.TYPE_MOUNTAIN, province, 120);
                    note = "（视野关键词无结果，改用省级分类）";
                }
            } catch (Exception e) {
                note = "（天地图检索失败：" + e.getClass().getSimpleName() + "）";
            }
            final java.util.List<MapPoints.Point> out = pts;
            final String fnote = note;
            runOnUiThread(() -> {
                if (seq != mountainFetchSeq || !mountainLayerOn) return;
                renderMountainRowsForLayer(out, fnote);
            });
        }).start();
    }

    private void renderMountainRowsForLayer(java.util.List<MapPoints.Point> pts, String note) {
        if (mountainLayer == null) return;
        TextView status = mountainLayer.findViewById(R.id.lc_mtn_status);
        LinearLayout list = mountainLayer.findViewById(R.id.lc_mtn_list);
        if (list == null) return;
        list.removeAllViews();
        int n = pts == null ? 0 : pts.size();
        if (status != null) {
            status.setText(String.format(java.util.Locale.US,
                    "视野内山名 %d 个%s（天地图地名搜索 · 分类 260203 山）", n, note));
        }
        if (n == 0) {
            TextView empty = new TextView(this);
            empty.setText("视野内暂无山名（天地图检索无结果）");
            empty.setTextSize(12f);
            empty.setTextColor(Color.rgb(200, 192, 180));
            list.addView(empty);
            android.util.Log.d("LCMTN", "rows=0 empty");
            return;
        }
        int show = Math.min(n, 40);
        for (int i = 0; i < show; i++) {
            MapPoints.Point p = pts.get(i);
            TextView row = new TextView(this);
            row.setText((i + 1) + ". " + (p.name == null ? "山" : p.name));
            row.setTextSize(12f);
            row.setTextColor(Color.rgb(232, 226, 214));
            row.setPadding(0, dp(6), 0, dp(6));
            final double rlat = p.lat, rlng = p.lng;
            row.setOnClickListener(v -> {
                if (compassMapView != null) {
                    compassMapView.evaluateJavascript(String.format(java.util.Locale.US,
                            "setView(%.6f,%.6f,null)", rlat, rlng), null);
                }
                hideMountainLayer();
            });
            list.addView(row);
        }
        android.util.Log.d("LCMTN", "rows=" + n + " shown=" + show + " note=" + note);
    }

    private String poiWgsLine(MapMarker m) {
        return String.format(java.util.Locale.US, "WGS84:%.4f，%.4f", m.lng, m.lat);
    }

    private void refreshPoiText() {
        final MapMarker m = poiMarker;
        if (m == null || poiPanel == null) return;
        TextView meta = poiPanel.findViewById(R.id.lc_poi_meta);
        TextView wgs = poiPanel.findViewById(R.id.lc_poi_wgs);
        if (wgs != null) wgs.setText(poiWgsLine(m));
        if (meta != null) {
            String elev = m.elevM == null ? "未知" : (Math.round(m.elevM) + " m");
            meta.setText("距我 " + poiDistanceText(m) + "    海拔 " + elev);
        }
        new Thread(() -> {
            String addr = null;
            try {
                addr = MapPoints.reverseAddress(m.lat, m.lng);
            } catch (Exception ignored) {
            }
            final String a = addr;
            runOnUiThread(() -> {
                if (poiPanel == null) return;
                TextView name = poiPanel.findViewById(R.id.lc_poi_name);
                if (name != null) {
                    name.setText(a == null || a.trim().isEmpty()
                            ? "名称：逆地理不可用（DEP-3）" : a.trim());
                }
            });
        }).start();
    }

    /** 距我：优先系统最后已知位置；拿不到就写「—」，不臆造距离。 */
    private String poiDistanceText(MapMarker m) {
        try {
            android.location.LocationManager lm =
                    (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null || checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return "—";
            }
            android.location.Location best = null;
            for (String p : new String[]{android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER}) {
                android.location.Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best == null) return "—";
            double km = MapPoints.distanceKm(best.getLatitude(), best.getLongitude(), m.lat, m.lng);
            return km < 1 ? String.format(java.util.Locale.US, "%.0f m", km * 1000)
                    : String.format(java.util.Locale.US, "%.1f km", km);
        } catch (Exception e) {
            return "—";
        }
    }

    private LinearLayout buildPoiPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setId(R.id.lc_poi_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.argb(242, 26, 22, 18));
        panel.setPadding(dp(14), dp(8), dp(14), dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = new TextView(this);
        name.setId(R.id.lc_poi_name);
        name.setText("名称：查询中…");
        name.setTextSize(14f);
        name.setTextColor(Color.rgb(235, 228, 215));
        head.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button clear = new Button(this);
        clear.setId(R.id.lc_poi_clear);
        clear.setText("清除标记");
        clear.setAllCaps(false);
        clear.setTextSize(11f);
        clear.setOnClickListener(v -> {
            js("clearMarkers()");
            hidePoiPanel();
            mapMarker = null;
            poiMarker = null;
        });
        head.addView(clear);
        panel.addView(head);

        TextView meta = new TextView(this);
        meta.setId(R.id.lc_poi_meta);
        meta.setTextSize(12f);
        meta.setTextColor(Color.rgb(200, 190, 170));
        panel.addView(meta);

        TextView wgs = new TextView(this);
        wgs.setId(R.id.lc_poi_wgs);
        wgs.setTextSize(12f);
        wgs.setTextColor(Color.rgb(200, 190, 170));
        panel.addView(wgs);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = new Button(this);
        copy.setId(R.id.lc_poi_copy);
        copy.setText("复制经纬度");
        copy.setAllCaps(false);
        copy.setTextSize(11f);
        copy.setOnClickListener(v -> copyPoiLatLng());
        Button fav = new Button(this);
        fav.setId(R.id.lc_poi_fav);
        fav.setText("收藏地点");
        fav.setAllCaps(false);
        fav.setTextSize(11f);
        fav.setOnClickListener(v -> savePoiAsFavorite());
        Button nav = new Button(this);
        nav.setId(R.id.lc_poi_nav);
        nav.setText("导航");
        nav.setAllCaps(false);
        nav.setTextSize(11f);
        nav.setOnClickListener(v -> navigateToPoi());
        actions.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(fav, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(nav, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(actions);
        return panel;
    }

    private void copyPoiLatLng() {
        if (poiMarker == null) return;
        String text = String.format(java.util.Locale.US, "%.4f,%.4f", poiMarker.lng, poiMarker.lat);
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("经纬度", text));
        toast("已复制经纬度 " + text);
        android.util.Log.d("LCPOI", "copy " + text);
    }

    private void savePoiAsFavorite() {
        if (poiMarker == null) return;
        android.content.Intent i = new android.content.Intent(this, SaveInterestActivity.class);
        i.putExtra(MapJump.EXTRA_LAT, poiMarker.lat);
        i.putExtra(MapJump.EXTRA_LNG, poiMarker.lng);
        i.putExtra(SaveInterestActivity.EXTRA_SITTING, bearingSitting);
        i.putExtra(SaveInterestActivity.EXTRA_FACING, bearingFacing);
        startActivity(i);
    }

    private void navigateToPoi() {
        if (poiMarker == null) return;
        startActivity(MapJump.intent(this, poiMarker.lat, poiMarker.lng));
    }

    // ---------------- F-12 测量 / F-13 画笔（地图内交互，底部浮条） ----------------

    private void toggleMeasure() {
        if (measureMode) exitMeasure(); else enterMeasure();
    }

    private void enterMeasure() {
        exitDraw();
        measureMode = true;
        measureAreaMode = false;
        measureUnitIndex = 0;
        measureCenters.clear();
        bearingSitting = 180;
        bearingFacing = 0;
        js("lc_measure_start('distance')");
        if (measureBar == null) measureBar = buildMeasureBar();
        if (measureBar.getParent() == null) addBottomBar(measureBar, 122);
        refreshMeasureReadout();
        measureHandler.removeCallbacks(measurePoll);
        measureHandler.post(measurePoll);
    }

    /** 退出测量：原版 API 只有 lc_measure_clear 能关闭测量态，退出即清点。 */
    private void exitMeasure() {
        measureMode = false;
        measureHandler.removeCallbacks(measurePoll);
        js("lc_measure_clear()");
        if (measureBar != null && measureBar.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) measureBar.getParent()).removeView(measureBar);
        }
        measureBar = null;
        measureResultView = null;
        measureUnitButton = null;
        measureCenters.clear();
        bearingSitting = 180;
        bearingFacing = 0;
    }

    /**
     * 屏幕中心打点：拖图把中心对到目标后点「添加点」，调地图 lc_measure_addCenter（不吸附）。
     * lc_measure_addCenter 已在主线的 map.html 里（1c54aa3，早于本分支基点），无需探测回退。
     */
    private void addMeasurePoint() {
        if (compassMapView == null) return;
        js("lc_measure_addCenter()");
        compassMapView.evaluateJavascript(
                "(function(){var s=getState();return s.lat+','+s.lng;})()",
                c -> {
                    String[] parts = decodeJsString(c).split(",");
                    if (parts.length == 2) {
                        try {
                            measureCenters.add(new double[]{
                                    Double.parseDouble(parts[0].trim()),
                                    Double.parseDouble(parts[1].trim())});
                            updateMeasureBearing();
                        } catch (NumberFormatException ignored) {
                        }
                    }
                });
    }

    private void undoMeasurePoint() {
        js("lc_measure_undo()");
        if (!measureCenters.isEmpty()) measureCenters.remove(measureCenters.size() - 1);
        updateMeasureBearing();
    }

    private void switchMeasureMode(boolean area) {
        measureAreaMode = area;
        measureUnitIndex = 0;
        measureCenters.clear();
        bearingSitting = 180;
        bearingFacing = 0;
        js("lc_measure_start('" + (area ? "area" : "distance") + "')");
        refreshMeasureReadout();
    }

    private void updateMeasureBearing() {
        if (measureCenters.size() >= 2) {
            double[] a = measureCenters.get(measureCenters.size() - 2);
            double[] b = measureCenters.get(measureCenters.size() - 1);
            bearingFacing = initialBearing(a[0], a[1], b[0], b[1]);
            bearingSitting = (bearingFacing + 180) % 360;
        } else {
            bearingSitting = 180;
            bearingFacing = 0;
        }
        refreshMeasureReadout();
    }

    private static double initialBearing(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private void refreshMeasureReadout() {
        if (measureResultView == null) return;
        String bear = String.format(java.util.Locale.US, "坐%.1f° 向%.1f°", bearingSitting, bearingFacing);
        measureResultView.setText(bear + " · " + formatMeasureValue());
        if (measureUnitButton != null) measureUnitButton.setText(unitLabel());
    }

    private String formatMeasureValue() {
        if (measureAreaMode) {
            double a = lastMeasureA;
            if (measureUnitIndex == 1) return String.format(java.util.Locale.US, "%.1f 亩", a / 666.6667);
            if (measureUnitIndex == 2) return String.format(java.util.Locale.US, "%.2f 公顷", a / 10000.0);
            if (a < 1_000_000) return String.format(java.util.Locale.US, "%.0f ㎡", a);
            return String.format(java.util.Locale.US, "%.2f km²", a / 1_000_000.0);
        }
        double d = lastMeasureD;
        if (measureUnitIndex == 1) return String.format(java.util.Locale.US, "%.2f nmi", d / 1852.0);
        if (d < 1000) return String.format(java.util.Locale.US, "%.0f m", d);
        return String.format(java.util.Locale.US, "%.2f km", d / 1000.0);
    }

    private String unitLabel() {
        if (!measureAreaMode) return measureUnitIndex == 0 ? "SI" : "NMI";
        return measureUnitIndex == 0 ? "SI" : (measureUnitIndex == 1 ? "MU" : "HA");
    }

    private View buildMeasureBar() {
        LinearLayout panel = new LinearLayout(this);
        panel.setId(R.id.lc_measure_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.argb(210, 24, 20, 16));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(barButton(R.string.lc_measure_mode_distance, R.id.lc_measure_mode_distance,
                v -> switchMeasureMode(false)));
        row1.addView(barButton(R.string.lc_measure_mode_area, R.id.lc_measure_mode_area,
                v -> switchMeasureMode(true)));
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(barButton(R.string.lc_measure_add, R.id.lc_measure_add,
                v -> addMeasurePoint()));
        row2.addView(barButton(R.string.lc_measure_undo, R.id.lc_measure_undo,
                v -> undoMeasurePoint()));
        row2.addView(barButton(R.string.lc_measure_done, R.id.lc_measure_done, v -> {
            toast("测量完成");
            exitMeasure();
        }));
        row2.addView(barButton(R.string.lc_measure_back, R.id.lc_measure_back,
                v -> exitMeasure()));
        panel.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setGravity(android.view.Gravity.CENTER_VERTICAL);
        measureResultView = new TextView(this);
        measureResultView.setId(R.id.lc_measure_readout);
        measureResultView.setTextSize(12f);
        measureResultView.setTextColor(Color.rgb(235, 228, 215));
        measureResultView.setPadding(dp(8), 0, dp(4), 0);
        row3.addView(measureResultView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        measureUnitButton = new Button(this);
        measureUnitButton.setId(R.id.lc_measure_unit);
        measureUnitButton.setTextSize(11f);
        measureUnitButton.setOnClickListener(v -> {
            int n = measureAreaMode ? 3 : 2;
            measureUnitIndex = (measureUnitIndex + 1) % n;
            refreshMeasureReadout();
        });
        row3.addView(measureUnitButton, new LinearLayout.LayoutParams(dp(72), dp(40)));
        panel.addView(row3);
        return panel;
    }

    private void toggleDraw() {
        if (drawMode) exitDraw(); else enterDraw();
    }

    private void enterDraw() {
        exitMeasure();
        drawMode = true;
        js("lc_draw_start()");
        js("lc_draw_style('" + DRAW_COLORS[drawColorIndex] + "'," + DRAW_WIDTHS[drawWidthIndex] + ")");
        if (drawBar == null) drawBar = buildDrawBar();
        if (drawBar.getParent() == null) addBottomBar(drawBar);
    }

    private void exitDraw() {
        drawMode = false;
        if (compassMapView != null) js("lc_draw_end()");
        if (drawBar != null && drawBar.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) drawBar.getParent()).removeView(drawBar);
        }
        drawBar = null;
    }

    private View buildDrawBar() {
        LinearLayout bar = bottomBar(R.id.lc_draw_panel);
        bar.addView(barButton(R.string.lc_draw_color, R.id.lc_draw_color, v -> {
            drawColorIndex = (drawColorIndex + 1) % DRAW_COLORS.length;
            getSharedPreferences("lc_paint", MODE_PRIVATE).edit()
                    .putInt("colorIndex", drawColorIndex).apply();
            js("lc_draw_style('" + DRAW_COLORS[drawColorIndex] + "',0)");
            toast("颜色 " + DRAW_COLORS[drawColorIndex]);
        }));
        bar.addView(barButton(R.string.lc_draw_width, R.id.lc_draw_width, v -> {
            drawWidthIndex = (drawWidthIndex + 1) % DRAW_WIDTHS.length;
            getSharedPreferences("lc_paint", MODE_PRIVATE).edit()
                    .putInt("widthIndex", drawWidthIndex).apply();
            js("lc_draw_style(''," + DRAW_WIDTHS[drawWidthIndex] + ")");
            toast("粗细 " + DRAW_WIDTHS[drawWidthIndex]);
        }));
        bar.addView(barButton(R.string.lc_draw_undo, R.id.lc_draw_undo,
                v -> js("lc_draw_undo()")));
        bar.addView(barButton(R.string.lc_draw_clear, R.id.lc_draw_clear,
                v -> js("lc_draw_clear()")));
        bar.addView(barButton(R.string.lc_draw_export, R.id.lc_draw_export,
                v -> captureMap()));
        return bar;
    }

    private LinearLayout bottomBar(int id) {
        LinearLayout bar = new LinearLayout(this);
        bar.setId(id);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.argb(210, 24, 20, 16));
        return bar;
    }

    private Button barButton(int textRes, int id, View.OnClickListener l) {
        Button b = new Button(this);
        b.setId(id);
        b.setText(textRes);
        b.setTextSize(12f);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        b.setLayoutParams(lp);
        return b;
    }

    private void addBottomBar(View bar) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(44));
        lp.gravity = android.view.Gravity.BOTTOM;
        lp.bottomMargin = dp(56); // 底部信息条之上，避免遮挡 trust row
        content.addView(bar, lp);
    }

    /** F-12 三行测量条用：指定高度、贴底（靠「返回/完成」退出，不依赖左列按钮）。 */
    private void addBottomBar(View bar, int heightDp) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(heightDp));
        lp.gravity = android.view.Gravity.BOTTOM;
        lp.bottomMargin = 0;
        content.addView(bar, lp);
    }

    /** R2-015：更多面板网格 4 行的 id（原版行顶 1540/1836/2132/2428）。 */    private static final int[] MORE_ROW_IDS = {
            R.id.lc_more_row0, R.id.lc_more_row1, R.id.lc_more_row2, R.id.lc_more_row3
    };

    /** 「更多」＝底部面板（README §6），不是新页面。 */
    private void showMoreSheet() {
        final FeatureFlags flags = new FeatureFlags(this);
        final String[] tools = {"罗盘", "超清地图", "看山脉", "看水系", "海拔查询", "天气", "历史影像",
                "等高线", "收藏点", "测量", "画笔", "钓点", "轨迹记录", "路网", "地理影像", "超清优化"};
        LinearLayout grid = new LinearLayout(this);
        grid.setId(R.id.lc_more_grid);
        grid.setOrientation(LinearLayout.VERTICAL);
        // 原版网格：4 列 ×296，行高 231，行顶 1540/1836/2132（步距 296，行间空 65），左起 23。
        grid.setPadding(23, 0, 21, 0);
        LinearLayout row = null;
        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        for (int i = 0; i < tools.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setId(MORE_ROW_IDS[i / 4]);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 231);
                if (i > 0) rlp.topMargin = 65;
                grid.addView(row, rlp);
            }
            final String t = tools[i];
            // 原版格内：图标 158×158（格顶 13、水平居中）+ 文字行 54（图标下 6）。
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            cell.setContentDescription(t);
            cell.setClickable(true);
            cell.setFocusable(true);
            android.widget.ImageView cellIcon = new android.widget.ImageView(this);
            cellIcon.setImageResource(ToolCatalog.ICONS[i]);
            cellIcon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams cip = new LinearLayout.LayoutParams(158, 158);
            cip.topMargin = 13;
            cell.addView(cellIcon, cip);
            TextView cellLabel = new TextView(this);
            cellLabel.setText(t);
            cellLabel.setTextSize(16f);
            cellLabel.setTextColor(Color.rgb(235, 228, 215));
            cellLabel.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 54);
            clp.topMargin = 6;
            cell.addView(cellLabel, clp);
            cell.setOnClickListener(v -> {
                // 选中即关面板：测量/画笔等页内动作若让 dialog 留在上面会挡住地图与浮条。
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                if ("罗盘".equals(t)) {
                    setCompassMode(true);
                } else if ("看山脉".equals(t) && flags.isEnabled("f06")) {
                    // F-06：更多 → 看山脉 → MountainActivity（默认开；关掉回退旧 stub）
                    openFeatureAtCenter(MountainActivity.class);
                } else if ("看山脉".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("看水系".equals(t) && flags.isEnabled("f07")) {
                    // F-07：更多 → 看水系 → WaterActivity（默认开；关掉回退旧 stub）
                    openFeatureAtCenter(WaterActivity.class);
                } else if ("看水系".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("历史影像".equals(t) && flags.isEnabled("f10")) {
                    // F-10：更多 → 历史影像 → HistoryImageActivity（默认开；关掉回退旧 stub）
                    startActivity(new android.content.Intent(this, HistoryImageActivity.class));
                } else if ("历史影像".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("天气".equals(t) && flags.isEnabled("f09")) {
                    // F-09：更多 → 天气 → WeatherActivity（默认开；关掉回退旧 stub）
                    startActivity(new android.content.Intent(this, WeatherActivity.class));
                } else if ("天气".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if (("超清地图".equals(t) || "超清优化".equals(t))
                        && flags.isEnabled("f02")) {
                    // F-02：更多 → 超清地图 / 超清优化 → SuperMapActivity（默认开；关掉回退旧 stub）
                    startActivity(new android.content.Intent(this, SuperMapActivity.class));
                } else if ("超清地图".equals(t) || "超清优化".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("海拔查询".equals(t) && flags.isEnabled("f03")) {
                    // F-03：更多 → 海拔查询 → ElevationActivity（默认开；关掉回退旧 stub）
                    enterElevationMode();
                } else if ("海拔查询".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("测量".equals(t) && flags.isEnabled("f12")) {
                    // F-12：更多 → 测量 → 地图内测量态（再点退出）
                    toggleMeasure();
                } else if ("测量".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("钓点".equals(t) && flags.isEnabled("f14")) {
                    // F-14：更多 → 钓点 → FishingActivity（默认开；关掉回退旧 stub）
                    startActivity(new android.content.Intent(this, FishingActivity.class));
                } else if ("钓点".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("路网".equals(t) && flags.isEnabled("f04")) {
                    // F-04：更多 → 路网 = 路网图层开关（默认开，持久化）
                    toggleRoadNet();
                } else if ("路网".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("轨迹记录".equals(t) && flags.isEnabled("f15")) {
                    // F-15：更多 → 轨迹记录 → TrackListActivity（默认开；关掉回退旧 stub）
                    startActivity(new android.content.Intent(this, TrackListActivity.class));
                } else if ("轨迹记录".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("画笔".equals(t) && flags.isEnabled("f13")) {
                    // F-13：更多 → 画笔 → 地图内绘制态（再点退出）
                    toggleDraw();
                } else if ("画笔".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("地理影像".equals(t) && flags.isEnabled("f16")) {
                    // F-16：更多 → 地理影像 → GeographicImageryActivity（默认开；关掉回退旧 stub）
                    startActivity(new android.content.Intent(this, GeographicImageryActivity.class));
                } else if ("地理影像".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else if ("收藏点".equals(t) && flags.isEnabled("f11")) {
                    // R2-014：收藏点回归地图态（右列 2 项 + 底部「添加点」），列表降为子页。
                    setFavoriteMode(true);
                } else if ("收藏点".equals(t)) {
                    toast(t + "：原版为工具子页，本步未实现");
                } else {
                    toast(t + "：原版为工具子页，本步未实现");
                }
            });
            row.addView(cell, new LinearLayout.LayoutParams(296, 231));
        }

        // 标题栏：左「地图工具」+ 右「编辑」。原版头高 164（编辑可点区 [1020,1350][1182,1514]，
        // 文字 [1079,1393][1182,1472]），标题文字 [46,1393][234,1472] —— 左边距 46、右边距 46。
        // R2-015：原版头部**没有**「功能开关（调试）」，该项已删（调试入口不再放在 1:1 页面上）。
        LinearLayout header = new LinearLayout(this);
        header.setId(R.id.lc_more_header);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(46, 0, 46, 0);

        TextView sheetTitle = new TextView(this);
        sheetTitle.setId(R.id.lc_more_title);
        sheetTitle.setText("地图工具");
        sheetTitle.setTextSize(20f);
        sheetTitle.setTextColor(Color.rgb(235, 228, 215));
        header.addView(sheetTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView editEntry = new TextView(this);
        editEntry.setId(R.id.lc_more_edit);
        editEntry.setText("编辑");
        editEntry.setTextSize(16f);
        editEntry.setTextColor(Color.rgb(235, 228, 215));
        editEntry.setPadding(dp(6), dp(6), 0, dp(6));
        header.addView(editEntry);

        // 「设置」保留为辅助入口（原版此处在抽屉「地图设置」；我们抽屉同样可达，
        // 保留这一枚是为了不切断已有的自动化路径，属入口冗余、不是页面差异）。
        TextView settingsEntry = new TextView(this);
        settingsEntry.setId(R.id.lc_settings_entry);
        settingsEntry.setText("设置");
        settingsEntry.setTextSize(12f);
        settingsEntry.setTextColor(Color.rgb(220, 190, 130));
        settingsEntry.setPadding(dp(8), dp(6), dp(8), dp(6));
        header.addView(settingsEntry);

        LinearLayout panel = new LinearLayout(this);
        panel.setId(R.id.lc_more_sheet);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.rgb(24, 20, 16));
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 164));
        // 网格区：原版行顶 1540 起，到 sheet 底 2578 只放得下 3 行，第 4 行需滚动。
        android.widget.ScrollView gridScroll = new android.widget.ScrollView(this);
        gridScroll.addView(grid);
        // 原版首行格顶 1540（头到 1514 之后还留 26px），这里补同量，行顶 1540/1836/2132 才逐项对上。
        LinearLayout.LayoutParams gsp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        gsp.topMargin = 26;
        panel.addView(gridScroll, gsp);

        // 原版 sheet = [0,1350][1228,2578]：顶 1350、高 1228、满宽；上方 0..1350 是遮罩。
        // 用 TOP 重力 + y 绝对偏移定位（我方窗口原点与内容帧原点同为屏幕 (0,0)），
        // 不能用 BOTTOM 重力——本窗高 2700，BOTTOM 会把顶边推到 1472，正好差导航栏那 122px。
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialogRef[0] = dialog;
        settingsEntry.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new android.content.Intent(this, SettingActivity.class));
        });
        editEntry.setOnClickListener(v -> {
            dialog.dismiss();
            toast("编辑：原版为网格编辑态，本步未实现");
        });
        dialog.show();
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // dialog 窗口默认的 y 原点在系统栏之内（实测 y=1350 落到了 1472，正好差 122px），
            // 加 FLAG_LAYOUT_IN_SCREEN 后 y 才是**屏幕绝对坐标**，与原版 dump 的 2578 帧对齐。
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = android.view.Gravity.TOP;
            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = 1228;
            lp.y = 1350;
            lp.dimAmount = 0.35f;
            w.setAttributes(lp);
        }
        android.util.Log.d("LCMORE", "sheet top=1350 h=1228 rows=" + ((tools.length + 3) / 4)
                + " cell=" + 296 + "x" + 231);
    }

    /** 知识＝弹层（原版是点圈层文字直达，不做 Tab）。 */
    /** 知识条目（标题 + 正文）：知识弹层与顶部栏「搜索」共用同一份数据。 */
    private java.util.List<String[]> knowledgeEntries() {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        rows.add(new String[]{"盘面（5 种，全部免费）", String.join("\n", CompassData.COMPASS_TYPES)});
        rows.add(new String[]{"圈层（14 种读数）", String.join("\n", CompassData.READ_TYPES)});
        for (int i = 0; i < CompassData.KNOWLEDGE_TITLES.length; i++) {
            rows.add(new String[]{CompassData.KNOWLEDGE_TITLES[i], CompassData.KNOWLEDGE_BODIES[i]});
        }
        return rows;
    }

    /**
     * 顶部栏「搜索」（K-02）。
     *
     * 原版搜索语义 = **地名 / 景观 POI 检索**（世纪空间底图服务侧的 POI 查询）。
     * 本实现 = **盘面 / 圈层 / 知识条目检索**（数据来自 CompassData，按关键字过滤标题与正文）。
     * 二者不是同一个数据域：我们没有世纪空间的 POI 源，因此属**等价替代（数据源不同）**，
     * 不是「有意差异」；也不假装有 POI——写一个假 POI 搜索才是造假。底图能力不受此改动影响。
     */
    // ---------------- R2-025：地图搜索（4 tab + 历史 chip + 坐标四系统） ----------------

    private static final String[] SEARCH_TABS = {"境内", "境外", "行政区域", "收藏点"};
    private static final String PREFS_SEARCH = "lc_search";
    private static final String KEY_HISTORY = "history";

    /**
     * R2-025 看门狗：MapPoints 的 HttpURLConnection 没有设 connect/read 超时（默认 0 = 无限等），
     * 请求一旦卡住，UI 会永远停在「检索中…」，而且 tab= 日志是在响应之后才打的，
     * 现场表现就是「点了 tab 之后再也没有任何输出」。UI 侧必须兜住。
     */
    private static final long SEARCH_TIMEOUT_MS = 10000;

    /** R2-025：地图中心缓存。点 tab 立即重跑，不等 WebView 的异步回调。 */
    private double mapCenterLat = 39.9042, mapCenterLng = 116.4074;

    /** R2-025：搜索弹层单实例。每次广播都新开一个会让 dialog 叠加（实测三个叠一起）。 */
    private android.app.AlertDialog searchDlg;

    /**
     * R2-025：按 tab 分派检索（0 境内 / 1 境外 / 2 行政区域）。
     * 行政区域解析不出省码时返回 unavailable 结果（不编省码、不假装有数据）。
     */
    private MapPoints.SearchResult runTabSearch(int tab, String kw, double lat, double lng) {
        if (tab == 1) {
            return MapPoints.searchGlobal(kw, 20);
        }
        if (tab == 2) {
            String p9 = MapPoints.resolveProvince9(kw, lat, lng);
            if (p9 == null || p9.isEmpty()) {
                return new MapPoints.SearchResult(MapPoints.SearchResult.UNAVAILABLE,
                        "province-code unresolved", new java.util.ArrayList<>(), 0);
            }
            return MapPoints.searchInProvince(kw, p9, 20);
        }
        return MapPoints.searchNear(kw, lat, lng, 12, 20);
    }

    /** R2-025：后台刷新地图中心缓存（fire-and-forget，不阻塞任何点击路径）。 */
    private void refreshMapCenterCache() {
        if (compassMapView == null) return;
        compassMapView.evaluateJavascript("(function(){var s=getState();return s.lat+','+s.lng;})()", c -> {
            String[] p = decodeJsString(c).split(",");
            if (p.length != 2) return;
            try {
                mapCenterLat = Double.parseDouble(p[0].trim());
                mapCenterLng = Double.parseDouble(p[1].trim());
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private void showSearchDialog() {
        // R2-025：单实例。重复广播曾叠出三个 dialog，点按落在哪一层不直观，必先关旧的。
        if (searchDlg != null && searchDlg.isShowing()) {
            android.util.Log.d("LCSEARCH", "dismiss previous dialog");
            searchDlg.dismiss();
        }
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.setBackgroundColor(Color.rgb(24, 20, 16));

        // 1) 四个 tab
        final LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] tabViews = new TextView[SEARCH_TABS.length];
        final int[] active = {0};
        final Runnable[] searchRef = new Runnable[1];
        for (int i = 0; i < SEARCH_TABS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setId(i == 0 ? R.id.lc_search_tab0 : i == 1 ? R.id.lc_search_tab1 : i == 2 ? R.id.lc_search_tab2 : R.id.lc_search_tab3);
            t.setText(SEARCH_TABS[i]);
            t.setTextSize(13f);
            t.setGravity(android.view.Gravity.CENTER);
            t.setPadding(dp(6), dp(8), dp(6), dp(8));
            t.setOnClickListener(v -> {
                active[0] = idx;
                for (int k = 0; k < tabViews.length; k++) {
                    tabViews[k].setTextColor(k == idx ? Color.rgb(235, 228, 215)
                            : Color.rgb(150, 130, 100));
                    tabViews[k].setBackgroundColor(k == idx ? Color.rgb(60, 50, 38) : Color.TRANSPARENT);
                }
                // R2-025：切 tab 立即按该 tab 重跑（收藏点 tab 不依赖输入）
                android.util.Log.d("LCSEARCH", "tab-click idx=" + idx + " active=" + active[0]
                        + " ref=" + (searchRef[0] != null));
                try {
                    if (searchRef[0] != null) searchRef[0].run();
                    else android.util.Log.e("LCSEARCH", "tab-click ref null: 搜索未执行");
                } catch (Throwable ex) {
                    // 点击回调里抛异常会被 UI 线程吞成「点了没反应」，这里必须显性化
                    android.util.Log.e("LCSEARCH", "tab rerun failed idx=" + idx, ex);
                }
            });
            tabViews[i] = t;
            tabs.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        tabViews[0].setTextColor(Color.rgb(235, 228, 215));
        tabViews[0].setBackgroundColor(Color.rgb(60, 50, 38));
        box.addView(tabs);

        // 2) 输入框
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setId(R.id.lc_search_input);
        input.setHint("输入城市/地点，或经纬度（116.4074,39.9042）");
        input.setTextColor(Color.rgb(235, 228, 215));
        input.setHintTextColor(Color.rgb(150, 130, 100));
        box.addView(input);

        // 3) 历史 chip
        final LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(chips);

        // 4) 结果区
        final android.widget.ListView lv = new android.widget.ListView(this);
        lv.setId(R.id.lc_search_results);
        lv.setBackgroundColor(Color.rgb(24, 20, 16));
        lv.setDividerHeight(0);
        final java.util.List<String> rows = new java.util.ArrayList<>();
        final android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, rows) {
            @Override
            public android.view.View getView(int pos, android.view.View cv, android.view.ViewGroup parent) {
                TextView tv = (TextView) super.getView(pos, cv, parent);
                tv.setTextColor(Color.rgb(235, 228, 215));
                tv.setTextSize(15f);
                tv.setPadding(dp(16), 0, dp(16), 0);
                // R2-025 判据：坐标四行行间距 186px（原版 map-jump-beijing 四行中心间距）
                tv.setHeight(186);
                tv.setGravity(android.view.Gravity.CENTER_VERTICAL);
                return tv;
            }
        };
        lv.setAdapter(adapter);
        box.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 186 * 4));

        final LinearLayout.LayoutParams lpOk = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        box.setLayoutParams(lpOk);

        final android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];

        final Runnable refreshChips = () -> {
            chips.removeAllViews();
            for (String h : loadSearchHistory()) {
                TextView c = new TextView(this);
                c.setText(h);
                c.setTextSize(11f);
                c.setTextColor(Color.rgb(200, 190, 170));
                c.setBackgroundColor(Color.rgb(48, 40, 30));
                c.setPadding(dp(10), dp(4), dp(10), dp(4));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                clp.rightMargin = dp(6);
                c.setLayoutParams(clp);
                c.setOnClickListener(v -> input.setText(h));
                chips.addView(c);
            }
        };
        refreshChips.run();
        if (pendingSearch != null) {
            // 先切到广播指定的 tab，再注入关键词（顺序决定按哪个 tab 检索）
            int t0 = Math.max(0, Math.min(3, pendingSearchTab));
            active[0] = t0;
            for (int k = 0; k < tabViews.length; k++) {
                tabViews[k].setTextColor(k == t0 ? Color.rgb(235, 228, 215) : Color.rgb(150, 130, 100));
                tabViews[k].setBackgroundColor(k == t0 ? Color.rgb(60, 50, 38) : Color.TRANSPARENT);
            }
            input.setText(pendingSearch);
            input.setSelection(input.getText().length());
            pendingSearch = null;
            pendingSearchTab = 0;
        }

        final Runnable runSearch = () -> {
            String q = input.getText().toString().trim();
            android.util.Log.d("LCSEARCH", "run kw=[" + q + "] active=" + active[0]
                    + " mapView=" + (compassMapView != null));
            rows.clear();
            double[] ll = CoordConv.parsePair(q);
            if (ll != null) {
                // R2-025：坐标输入给 GCJ02 / WGS84 / BD09 / CGCS2000 四行（本地换算，不依赖配额）
                // R2-025：坐标换算走数据层 CoordConv（单一实现），UI 只排版
                double[] gcj = CoordConv.wgs84ToGcj02(ll[0], ll[1]);
                double[] bd = CoordConv.gcj02ToBd09(gcj[0], gcj[1]);
                rows.add(String.format(java.util.Locale.US, "GCJ02     %.6f,%.6f", gcj[1], gcj[0]));
                rows.add(String.format(java.util.Locale.US, "WGS84     %.6f,%.6f", ll[1], ll[0]));
                rows.add(String.format(java.util.Locale.US, "BD09      %.6f,%.6f", bd[1], bd[0]));
                rows.add(String.format(java.util.Locale.US, "CGCS2000  %.6f,%.6f（与 WGS84 等价）", ll[1], ll[0]));
                if (!q.isEmpty()) rememberSearch(q);
                refreshChips.run();
            } else if (active[0] == 3) {
                // 收藏点 tab：本机数据，不需要关键词、不需要网络
                java.util.List<InterestPoint> pts = new GapDb(this).listInterest();
                if (pts.isEmpty()) {
                    rows.add("暂无收藏点（可在地图收藏点态「添加点」）");
                } else {
                    for (InterestPoint ip : pts) {
                        rows.add(String.format(java.util.Locale.US, "%s  %.6f,%.6f",
                                ip.name == null || ip.name.isEmpty() ? "收藏点" : ip.name, ip.lng, ip.lat));
                    }
                }
                adapter.notifyDataSetChanged();
            } else if (q.isEmpty()) {
                android.util.Log.d("LCSEARCH", "run skip: 关键词为空 tab=" + active[0]);
                rows.add("输入城市/地点，或直接粘贴经纬度");
            } else {
                // R2-025：切 tab / 改关键词都必须**立即**重跑。
                // 中心点走 Java 侧缓存（refreshMapCenterCache 维护），不把重跑门控在一次异步 JS 往返上：
                // 旧实现在 getState() 回调里才发请求，回调不返回时现场就是「点了 tab 之后毫无动静」。
                final String kw = q;
                final int tab = active[0];
                final double flat = mapCenterLat, flng = mapCenterLng;
                rows.add("检索中…");
                adapter.notifyDataSetChanged();
                rememberSearch(q);
                refreshChips.run();
                refreshMapCenterCache();
                android.util.Log.d("LCSEARCH", "search start kw=[" + kw + "] tab=" + tab
                        + " center=" + flat + "," + flng);
                new Thread(() -> {
                    java.util.List<String> out = new java.util.ArrayList<>();
                    final MapPoints.SearchResult[] rb = new MapPoints.SearchResult[1];
                    final Throwable[] eb = new Throwable[1];
                    try {
                        Thread w = new Thread(() -> {
                            try {
                                rb[0] = runTabSearch(tab, kw, flat, flng);
                            } catch (Throwable t) {
                                eb[0] = t;
                            }
                        }, "lc-search-" + tab);
                        w.start();
                        w.join(SEARCH_TIMEOUT_MS);
                    } catch (InterruptedException ignored) {
                    }
                    MapPoints.SearchResult r = rb[0];
                    Throwable err = eb[0];
                    if (err != null) {
                        android.util.Log.e("LCSEARCH", "search failed tab=" + tab, err);
                        out.add("检索服务不可用（" + err.getClass().getSimpleName() + "）");
                    } else if (r == null) {
                        android.util.Log.d("LCSEARCH", "tab=" + tab + " code=timeout ms=" + SEARCH_TIMEOUT_MS);
                        out.add("检索服务不可用（请求超时 " + (SEARCH_TIMEOUT_MS / 1000) + " 秒）");
                    } else {
                        android.util.Log.d("LCSEARCH", "tab=" + tab + " code=" + r.code + " total=" + r.total
                                + " first=" + (r.points.isEmpty() ? "" : r.points.get(0).name));
                        if (MapPoints.SearchResult.OK.equals(r.code) || MapPoints.SearchResult.EMPTY.equals(r.code)) {
                            if (r.points.isEmpty()) {
                                out.add("未找到相关地点（可试试城市名或坐标）");
                            } else {
                                for (MapPoints.Point pt : r.points) {
                                    // 全站口径「经度,纬度」（与原版一致；parseCoordPair 两种都认）
                                    out.add(pt.name + "  " + String.format(java.util.Locale.US, "%.6f,%.6f", pt.lng, pt.lat));
                                }
                            }
                        } else if (MapPoints.SearchResult.RATE_LIMITED.equals(r.code)) {
                            out.add("请求过快（HTTP 429），稍后再试");
                        } else if (MapPoints.SearchResult.QUOTA.equals(r.code)) {
                            out.add("配额不足（" + r.detail + "）");
                        } else if (MapPoints.SearchResult.KEY_INVALID.equals(r.code)) {
                            out.add("检索服务不可用（key 非法/未授权）");
                        } else {
                            out.add("检索服务不可用（" + r.detail + "）");
                        }
                    }
                    final java.util.List<String> res = out;
                    runOnUiThread(() -> {
                        rows.clear();
                        rows.addAll(res);
                        adapter.notifyDataSetChanged();
                        android.util.Log.d("LCSEARCH", "ui rows=" + rows.size() + " first="
                                + (rows.isEmpty() ? "" : rows.get(0)));
                    });
                }).start();
            }
            adapter.notifyDataSetChanged();
        };

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) { runSearch.run(); }
        });
        lv.setOnItemClickListener((p, v, pos, id) -> {
            String row = rows.get(pos);
            String[] parts = row.trim().split("\\s+");
            if (parts.length >= 2) {
                double[] ll2 = parseCoordPair(parts[parts.length - 1].replace("（与", "")
                        .replace("WGS84", "").replace("等价）", ""));
                if (ll2 == null) {
                    String last = parts[parts.length - 1];
                    if (last.contains(",")) ll2 = parseCoordPair(last.split(",")[0] + "," + last.split(",")[1]);
                }
                if (ll2 != null) {
                    js(String.format(java.util.Locale.US, "try{setView(%.6f,%.6f,zoom)}catch(e){}", ll2[0], ll2[1]));
                    if (dlg[0] != null) dlg[0].dismiss();
                }
            }
        });
        searchRef[0] = runSearch;
        runSearch.run();

        dlg[0] = new android.app.AlertDialog.Builder(this)
                .setTitle("搜索")
                .setView(box)
                .setNegativeButton("关闭", null)
                .create();
        dlg[0].show();
        // R2-025：搜索弹层不许被误触外部关掉。键盘弹起时弹层会上移，点 tab 很容易落到外面，
        // 现场表现同样是「点了 tab 就没了」。关闭只留「关闭」按钮与 BACK。
        dlg[0].setCanceledOnTouchOutside(false);
        searchDlg = dlg[0];
        android.util.Log.d("LCSEARCH", "tabs=" + SEARCH_TABS.length);
    }

    /** "lng,lat" / "lat,lng" → [lat,lng]；无法解析返回 null。 */
    private double[] parseCoordPair(String q) {
        if (q == null) return null;
        String s = q.replace("，", ",").trim();
        String[] p = s.split(",");
        if (p.length != 2) return null;
        try {
            double a = Double.parseDouble(p[0].trim());
            double b = Double.parseDouble(p[1].trim());
            // 经度在前（原版口径 116.4074,39.9042）
            if (Math.abs(a) <= 180 && Math.abs(b) <= 90) return new double[]{b, a};
            if (Math.abs(a) <= 90 && Math.abs(b) <= 180) return new double[]{a, b};
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private java.util.List<String> loadSearchHistory() {
        String raw = getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE).getString(KEY_HISTORY, "");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String x : raw.split("\\n")) if (!x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    private void rememberSearch(String q) {
        java.util.List<String> h = loadSearchHistory();
        h.remove(q);
        h.add(0, q);
        while (h.size() > 4) h.remove(h.size() - 1);
        getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE).edit()
                .putString(KEY_HISTORY, String.join("\n", h)).apply();
    }

    /** WGS84 → GCJ02（公开偏移算法，中国境内加偏）。 */
    private static double[] wgs2gcj(double lat, double lng) {
        double a = 6378245.0, ee = 0.00669342162296594323;
        if (lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271) return new double[]{lat, lng};
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - ee * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * Math.PI);
        dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{lat + dLat, lng + dLng};
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }

    /** GCJ02 → BD09。 */
    private static double[] gcj2bd(double lat, double lng) {
        double x = lng, y = lat;
        double z = Math.sqrt(x * x + y * y) + 0.00002 * Math.sin(y * Math.PI);
        double theta = Math.atan2(y, x) + 0.000003 * Math.cos(x * Math.PI);
        return new double[]{z * Math.sin(theta) + 0.006, z * Math.cos(theta) + 0.0065};
    }
    private void showKnowledgeSheet() {
        final java.util.List<String[]> rows = knowledgeEntries();
        final java.util.List<String> titles = new java.util.ArrayList<>();
        final java.util.List<String> bodies = new java.util.ArrayList<>();
        for (String[] r : rows) {
            titles.add(r[0]);
            bodies.add(r[1]);
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
            if ("elev".equals(type)) {
                runOnUiThread(() -> showElevationPanel(marker));
                return;
            }
            if ("pick".equals(type) || "marker".equals(type)) {
                // R2-004：3D pick 与 2D 点图都保留立向点，并把 POI 面板挂上去。
                mapMarker = marker;
                // 海拔模式下的点图/3D 点选一律走海拔浮层（不弹 POI 面板）。
                runOnUiThread(() -> {
                    if (elevationMode) showElevationPanel(marker); else showPoiPanel(marker);
                });
            } else {
                mapMarker = null;
                runOnUiThread(this::hidePoiPanel);
            }
        }), "LCAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // A10：页面重载（location.reload()）会把 JS 侧 uiMode 复位成默认值，而原生浮层仍在。
                // 这里不能恒发 'map'，必须按原生当前态重放，否则出现「浮层开着、页面却是 map 态」的不同步。
                // 重载后地图侧的 heading/坐向也丢了，立即重推一次（传感器下一帧还会再推）。
                updateReading();
                if (view == compassMapView) {
                    mapPageReady = true;
                    // uiMode 按原生当前态重放（若冷启时点过罗盘，这里有 pendingUiMode 兜底补发）。
                    applyMapUiMode(compassMode ? "compass" : "map");
                    applyPendingMapView();
                    // F-04：页面重载后按持久化状态重放路网开关。
                    view.evaluateJavascript("try{setRoadNet(" + roadNetOn + ")}catch(e){}", null);
                    // R2-026：地图就绪后补一次左上地名（罗盘态不显示，不必取）。
                    if (!compassMode) refreshPlaceName();
                }
            }
        });
        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
        // 主地图必须带网页端 key（MapAsset 拼 ?tk=<webKey>），裸 map.html 拿不到瓦片。
        // 原先裸地址没有 hash，这里 hash 传 null 保持行为不变。
        webView.loadUrl(MapAsset.url(null));
        return webView;
    }

    /**
     * 截图：罗盘页整屏（底图 + 罗盘 + 面板）。PixelCopy 优先（View.draw 抓 WebGL 必空），
     * 黑图/失败才退到 WebView canvas 的 toDataURL。D-009：存相册 Pictures/LC/、不写 GPS EXIF、
     * 文件名「坐向_时间」、保存后走系统分享。
     */
    private void captureMap() {
        final View target = compassTab;
        android.util.Log.d("LCCAP", "captureMap target=" + (target == null ? "null" : target.getWidth() + "x" + target.getHeight())
                + " webview=" + (compassMapView != null));
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
                    android.util.Log.d("LCCAP", "pixelCopy result=" + result + " blank=" + looksBlank(bmp));
                    if (result == android.view.PixelCopy.SUCCESS && !looksBlank(bmp)) {
                        saveCompassShot(composeAttribution(bmp));
                    } else {
                        android.util.Log.d("LCCAP", "pixelCopy unusable -> captureViaJs");
                        captureViaJs();
                    }
                }, new android.os.Handler(android.os.Looper.getMainLooper()));
            } catch (Throwable t) {
                android.util.Log.e("LCCAP", "pixelCopy threw -> captureViaJs", t);
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
                        int len = value == null ? -1 : value.length();
                        android.util.Log.d("LCCAP", "captureViaJs dataUrl len=" + len);
                        if (value == null || value.length() < 32) {
                            android.util.Log.w("LCCAP", "empty canvas dataURL");
                            toast("截图失败：地图未渲染");
                            return;
                        }
                        byte[] raw = android.util.Base64.decode(
                                value.substring(1, value.length() - 1), android.util.Base64.DEFAULT);
                        android.graphics.Bitmap map = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
                        if (map == null) {
                            android.util.Log.w("LCCAP", "decodeByteArray null, raw=" + raw.length);
                            toast("截图失败：解码图片失败");
                            return;
                        }
                        saveCompassShot(composeAttribution(map));
                    } catch (Throwable t) {
                        android.util.Log.e("LCCAP", "captureViaJs failed", t);
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
        android.util.Log.d("LCCAP", "save name=" + name + " size=" + bmp.getWidth() + "x" + bmp.getHeight());
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.net.Uri uri = null;
            android.content.ContentResolver cr = getContentResolver();
            try {
                // 标准相册落盘：先 IS_PENDING=1 占位 → 写流 → 清 0，避免媒体库扫到半截文件。
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
                v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/LC");
                v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
                uri = cr.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                android.util.Log.d("LCCAP", "insert -> " + uri);
                if (uri == null) throw new java.io.IOException("MediaStore insert returned null");
                java.io.OutputStream opened = cr.openOutputStream(uri);
                if (opened == null) throw new java.io.IOException("openOutputStream returned null");
                try (java.io.OutputStream os = opened) {
                    if (!bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)) {
                        throw new java.io.IOException("PNG compress failed");
                    }
                }
                android.content.ContentValues done = new android.content.ContentValues();
                done.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                cr.update(uri, done, null, null);
                android.util.Log.d("LCCAP", "saved -> " + uri);
                // 成功先给可查的提示，再走分享；分享失败不影响「已保存」这个事实。
                toast("已保存 Pictures/LC/" + name + ".png");
                shareShotSafely(uri, name);
                return;
            } catch (Throwable t) {
                android.util.Log.e("LCCAP", "MediaStore save failed", t);
                if (uri != null) {
                    try {
                        cr.delete(uri, null, null);
                    } catch (Throwable ignored) {
                    }
                }
                // 落到兜底目录，保证「失败有提示、成功有可查文件」。
            }
        }
        saveShotToFallback(bmp, name);
    }

    /** 兜底：相册写入失败时写应用外部私有目录，并提示完整路径（用户可在文件管理器找到）。 */
    private void saveShotToFallback(android.graphics.Bitmap bmp, String name) {
        try {
            java.io.File dir = new java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "LC");
            if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("mkdirs failed: " + dir);
            java.io.File out = new java.io.File(dir, name + ".png");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                if (!bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)) {
                    throw new java.io.IOException("PNG compress failed");
                }
            }
            android.util.Log.d("LCCAP", "fallback saved -> " + out.getAbsolutePath());
            toast("已保存 " + out.getAbsolutePath());
        } catch (Throwable t) {
            android.util.Log.e("LCCAP", "fallback save failed", t);
            toast("保存失败: " + t.getMessage());
        }
    }

    /** 分享单独兜底：失败只提示，不再被外层 catch 误报成「保存失败」。 */
    private void shareShotSafely(android.net.Uri uri, String name) {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(i, "分享 " + name));
        } catch (Throwable t) {
            android.util.Log.e("LCCAP", "share failed (image already saved)", t);
            toast("分享面板不可用，图片已保存");
        }
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
        if (compassHidden && compassMode) {
            // R2-006 内容判据：隐藏态读数条只显示单行「坐X.X° 向Y.Y°」（原版 [105,397][350,436] 的内容）；
            // 位置随 R2-003，不在这里强对。向/坐取当前盘面坐向。
            readingView.setText(String.format(java.util.Locale.US, "坐%.1f° 向%.1f°",
                    (double) bearingSitting, (double) bearingFacing));
            if (statusView != null) statusView.setText("");
            return;
        }
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
    protected void onDestroy() {
        // C-05：主页面 WebView 也要销毁（新增页都已补，主页面此前漏着）。
        measureHandler.removeCallbacks(measurePoll);
        if (compassMapView != null) {
            try {
                compassMapView.removeJavascriptInterface("LCAndroid");
                compassMapView.destroy();
            } catch (Throwable ignored) {
            }
            compassMapView = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveFullscreen();
        registerDebugBridge();
        if (compassView != null && prefs != null) {
            // K-01：默认盘面改 0（线稿盘）。原版从地图点「罗盘」进入时就是线稿透出态
            // （orig-06-read.png）；1–4 号是整圆不透明的彩色皮肤（alpha opaque_frac=0.785），
            // 会让地图在盘内透不出来，不能做默认。「切换」仍在 0–4 之间循环
            // （CompassListActivity 写回 idx=0..4，无钳位）。
            int dial = prefs.getInt("dial_index", 0);
            // F-18：本机导入盘面优先（文件不存在时回退内置 dial_index）。
            String customDial = prefs.getString("dial_custom_path", "");
            compassView.setDialType(dial);
            if (customDial != null && !customDial.isEmpty()
                    && new java.io.File(customDial).exists()) {
                compassView.setArtworkPath(customDial);
            } else {
                compassView.setArtwork(dial);
            }
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
        if (favoriteMode) {
            setFavoriteMode(false);
            return;
        }
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
