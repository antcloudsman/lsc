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
    private View knowledgeTab;

    private SensorManager sensorManager;
    private Sensor rotationVector;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        content = findViewById(R.id.content);
        Button tabCompass = findViewById(R.id.tab_compass);
        Button tabMap = findViewById(R.id.tab_map);
        Button tabKnowledge = findViewById(R.id.tab_knowledge);

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
    }

    private View buildCompassTab() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(24, 20, 16));

        // Base map (底图).
        WebView map = createMapWebView();
        frame.addView(map, new FrameLayout.LayoutParams(
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

        LinearLayout dials = new LinearLayout(this);
        dials.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < CompassData.COMPASS_TYPES.length; i++) {
            final int idx = i;
            Button d = new Button(this);
            d.setText(CompassData.COMPASS_TYPES[i]);
            d.setTextSize(12f);
            d.setAllCaps(false);
            d.setOnClickListener(v -> compassView.setDialType(idx));
            dials.addView(d, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        panel.addView(dials);

        readingView = new TextView(this);
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

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        pp.gravity = android.view.Gravity.BOTTOM;
        frame.addView(panel, pp);

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

    /** Capture the map WebView (地图+罗盘) as a PNG into the app's files dir. */
    private void captureMap() {
        if (mapView == null) return;
        try {
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(mapView.getWidth(), mapView.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            mapView.draw(c);
            java.io.File dir = new java.io.File(getExternalFilesDir(null), "shots");
            if (!dir.exists()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, "map_" + System.currentTimeMillis() + ".png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            android.widget.Toast.makeText(this, "已保存 " + out.getName(), android.widget.Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            android.widget.Toast.makeText(this, "截图失败: " + t.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
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

    private void showTab(int index) {
        compassTab.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        mapView.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        knowledgeTab.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    }

    private void updateReading() {
        if (compassView == null || readingView == null) return;
        CompassData.Reading r = CompassData.read(compassView.getHeading());
        readingView.setText(
                String.format("度数 %.2f°  %s%s  五行%s\n地盘 %s山  人盘 %s山  天盘 %s山  坐 %s山\n分金 %s山%s·%s  七十二龙 %s山%s·%s\n六十龙 %s·%s  后天卦 %s",
                        r.heading, r.jian, CompassData.MOUNTAINS[r.mountain],
                        CompassData.WUXING[r.mountain],
                        CompassData.MOUNTAINS[r.mountain],
                        CompassData.MOUNTAINS[plate(r.heading + 7.5f)],
                        CompassData.MOUNTAINS[plate(r.heading - 7.5f + 15f)],
                        CompassData.MOUNTAINS[r.sitting],
                        CompassData.MOUNTAINS[r.mountain], r.fenjin, r.fenjinNayin,
                        CompassData.MOUNTAINS[r.mountain], r.dragon72, r.dragon72Nayin,
                        r.dragon60Name, r.dragon60Nayin,
                        CompassData.HOUTIAN[r.mountain]));

        // Push heading + sit/face to the map WebView (坐向 overlay).
        if (mapView != null) {
            String face = CompassData.MOUNTAINS[r.mountain] + "山";
            String sit = CompassData.MOUNTAINS[r.sitting] + "山";
            mapView.evaluateJavascript("setHeading(" + r.heading + ")", null);
            mapView.evaluateJavascript("setSitFace('" + face + "','" + sit + "')", null);
        }
    }

    private static int plate(float h) {
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
        if (sensorManager != null && rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            float azimuth = normalize((float) Math.toDegrees(orientation[0]));
            compassView.setSensorHeading(azimuth);
            updateReading();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // no-op
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
