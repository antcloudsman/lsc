package com.luopan.fengshui;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * F-15 轨迹回放（地图内动画，不做视频）。
 *
 * 桥（LC地图 b58b2e6）：
 * lc_track_load(segments) / lc_track_progress(ratio) / lc_track_play(rate) /
 * lc_track_pause() / lc_track_follow(on) / lc_track_clear()；getState().track。
 * 文案禁词：不出现视频/会员字样；无门槛、无锁。
 */
public class TrackReplayActivity extends LcFeatureActivity {

    private WebView map;
    private boolean ready = false;
    private boolean playing = false;
    private boolean follow = true;
    private int rate = 1;
    private boolean dragging = false;
    private String segmentsJson = "[]";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Button playButton;
    private Button rateButton;
    private SeekBar slider;
    private TextView timeView;
    private TrackMeta meta;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (ready && map != null) {
                map.evaluateJavascript("JSON.stringify(getState().track||{})", v -> {
                    try {
                        String s = decode(v);
                        JSONObject o = new JSONObject(s);
                        boolean atEnd = o.optBoolean("atEnd", false);
                        double speed = o.optDouble("speedKmh", 0);
                        double progress = o.optDouble("progress", Double.NaN);
                        if (!Double.isNaN(progress) && !dragging) {
                            slider.setProgress((int) Math.round(progress * 1000));
                        }
                        timeView.setText(String.format(Locale.US, "%s · %.1f km/h",
                                meta != null ? TrackListActivity.fmtDuration(meta.durationSec) : "",
                                speed));
                        if (atEnd && playing) setPlaying(false);
                    } catch (Throwable ignored) {
                    }
                });
            }
            handler.postDelayed(this, 400);
        }
    };

    @Override
    protected int backId() {
        return R.id.lc_track_replay_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_track_replay_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_track_replay_map;
    }

    @Override
    protected String title() {
        return getString(R.string.lc_track_replay_title);
    }

    @Override
    protected void onPageReady(FrameLayout content) {
        String id = getIntent().getStringExtra("track_id");
        TrackStore store = new TrackStore(this);
        meta = id == null ? null : store.loadMeta(id);
        List<TrackPoint> points = id == null ? new ArrayList<>() : store.loadPoints(id);
        segmentsJson = buildSegments(points);

        map = new WebView(this);
        map.setBackgroundColor(Color.rgb(24, 20, 16));
        WebSettings ws = map.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        map.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                ready = true;
                view.evaluateJavascript("try{lc_track_load(" + segmentsJson + ");lc_track_follow(true);}catch(e){}", null);
            }
        });
        map.loadUrl("file:///android_asset/map.html#in3d");
        content.addView(map, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 顶部：跟随开关
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Color.argb(190, 24, 20, 16));
        top.setPadding(dp(12), dp(6), dp(12), dp(6));
        TextView followLabel = new TextView(this);
        followLabel.setText(R.string.lc_track_replay_follow);
        followLabel.setTextSize(12f);
        followLabel.setTextColor(Color.rgb(235, 228, 215));
        top.addView(followLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LcPillSwitch followSwitch = new LcPillSwitch(this);
        followSwitch.setId(R.id.lc_track_replay_follow);
        followSwitch.setChecked(true);
        followSwitch.setOnCheckedChangeListener((v, c) -> {
            follow = c;
            js("lc_track_follow(" + c + ")");
        });
        top.addView(followSwitch, new LinearLayout.LayoutParams(
                LcUi.dp(this, 46.2f), LcUi.dp(this, 23.1f)));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP;
        tlp.topMargin = dp(4);
        content.addView(top, tlp);

        // 底部：播放/时间轴/倍速/图例
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(Color.argb(200, 24, 20, 16));
        bottom.setPadding(dp(10), dp(6), dp(10), dp(10));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        playButton = new Button(this);
        playButton.setId(R.id.lc_track_replay_play);
        playButton.setText("▶︎");
        playButton.setTextSize(12f);
        playButton.setOnClickListener(v -> togglePlay());
        controls.addView(playButton, new LinearLayout.LayoutParams(dp(48), dp(40)));

        slider = new SeekBar(this);
        slider.setId(R.id.lc_track_replay_slider);
        slider.setMax(1000);
        controls.addView(slider, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) js("lc_track_progress(" + (progress / 1000.0) + ")");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
            }
        });

        rateButton = new Button(this);
        rateButton.setId(R.id.lc_track_replay_speed);
        rateButton.setText(rate + "×");
        rateButton.setTextSize(12f);
        rateButton.setOnClickListener(v -> {
            rate = rate == 1 ? 2 : (rate == 2 ? 4 : (rate == 4 ? 8 : 1));
            rateButton.setText(rate + "×");
            if (playing) js("lc_track_play(" + rate + ")");
        });
        controls.addView(rateButton, new LinearLayout.LayoutParams(dp(56), dp(40)));
        bottom.addView(controls);

        timeView = new TextView(this);
        timeView.setId(R.id.lc_track_replay_time);
        timeView.setTextSize(12f);
        timeView.setTextColor(Color.rgb(235, 228, 215));
        bottom.addView(timeView);

        TextView legend = new TextView(this);
        legend.setId(R.id.lc_track_replay_legend);
        legend.setText(R.string.lc_track_replay_legend);
        legend.setTextSize(10f);
        legend.setTextColor(Color.rgb(180, 190, 200));
        bottom.addView(legend);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        content.addView(bottom, blp);

        handler.post(poll);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(poll);
        if (map != null) {
            try {
                map.evaluateJavascript("lc_track_clear()", null);
            } catch (Throwable ignored) {
            }
            map.destroy();
            map = null;
        }
        super.onDestroy();
    }

    private void togglePlay() {
        setPlaying(!playing);
    }

    private void setPlaying(boolean on) {
        playing = on;
        playButton.setText(on ? "❚❚" : "▶︎");
        js(on ? "lc_track_play(" + rate + ")" : "lc_track_pause()");
    }

    private void js(String code) {
        if (map == null) return;
        map.evaluateJavascript("try{" + code + "}catch(e){}", null);
    }

    private static String decode(String v) {
        if (v == null) return "{}";
        String s = v.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return s;
    }

    /** 桥入参：[[{lat,lng,t}…]…]，按 seg 分组，跨段不连线。 */
    private static String buildSegments(List<TrackPoint> points) {
        JSONArray segs = new JSONArray();
        JSONArray cur = null;
        int seg = Integer.MIN_VALUE;
        for (TrackPoint p : points) {
            if (p.seg != seg) {
                cur = new JSONArray();
                segs.put(cur);
                seg = p.seg;
            }
            JSONObject o = new JSONObject();
            try {
                o.put("lat", p.lat);
                o.put("lng", p.lng);
                o.put("t", p.t);
            } catch (Throwable ignored) {
            }
            cur.put(o);
        }
        return segs.toString();
    }
}
