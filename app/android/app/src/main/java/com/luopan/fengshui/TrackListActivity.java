package com.luopan.fengshui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * F-15 历史轨迹列表（免费、无门槛；记录态不换页）。
 *
 * 1:1 取值（原版取证 `lc-gap-evidence/probe/F15-tracklist.png` / `F15-track-setting.png`）：
 * - 列表页标题 **`历史轨迹`**（不是「轨迹记录」）；
 * - 空态 = 插画（蓝星球 + 小人）+ 文案 **`暂无历史轨迹记录`**；
 * - 整页底色 `#F0F3F7`；
 * - 设置面板：标题 `轨迹设置`，分区 `权限设置`，四行原文
 *   `后台定位`（开关，默认关）/ `忽略省电优化`（去设置）/ `后台运行权限`（状态）/ `通知栏权限`（状态）。
 *
 * 结构：记录卡（开始/停止 + 记录中状态）→ 未完成卡（继续/结束保存/丢弃）→ 列表（查看/删除）。
 * LC 增量（非原版）：面板底部「记录参数」三项（采样间隔/最小位移/高精度定位），原版面板没有这三项，
 * 保留是为了让方案 §4 的可调采样在真机上可改；原版四行权限行的位置/文案不变。
 */
public class TrackListActivity extends LcFeatureActivity {

    private static final int REQ_LOCATION = 6101;
    private static final int[] ITEM_IDS = {
            R.id.lc_track_item_0, R.id.lc_track_item_1, R.id.lc_track_item_2,
            R.id.lc_track_item_3, R.id.lc_track_item_4, R.id.lc_track_item_5,
            R.id.lc_track_item_6, R.id.lc_track_item_7, R.id.lc_track_item_8,
            R.id.lc_track_item_9
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TrackStore store;
    private SharedPreferences settings;
    private TextView recordingView;
    private Button startButton;
    private Button stopButton;
    private LinearLayout ongoingCard;
    private LinearLayout listBox;
    private LinearLayout emptyBox;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateRecordingCard();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected int backId() {
        return R.id.lc_track_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_track_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_track_list;
    }

    @Override
    protected String title() {
        return getString(R.string.lc_track_title);
    }

    @Override
    protected void onPageReady(FrameLayout content) {
        store = new TrackStore(this);
        settings = getSharedPreferences("lc_track", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.lc_track_page_bg));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(root);

        // 记录设置入口
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView settingsBtn = new TextView(this);
        settingsBtn.setId(R.id.lc_track_settings);
        settingsBtn.setText(R.string.lc_track_settings);
        settingsBtn.setTextSize(14f);
        settingsBtn.setTextColor(getColor(R.color.lc_blue));
        settingsBtn.setPadding(0, dp(8), 0, dp(8));
        settingsBtn.setOnClickListener(v -> showSettingsSheet());
        topRow.addView(settingsBtn);
        root.addView(topRow);

        // 记录卡
        LinearLayout recordCard = new LinearLayout(this);
        recordCard.setOrientation(LinearLayout.VERTICAL);
        recordCard.setBackgroundColor(getColor(R.color.lc_toolmgr_card));
        recordCard.setPadding(dp(16), dp(12), dp(16), dp(12));

        recordingView = new TextView(this);
        recordingView.setId(R.id.lc_track_recording);
        recordingView.setTextSize(15f);
        recordingView.setTextColor(getColor(R.color.lc_setting_text));
        recordCard.addView(recordingView);

        LinearLayout recordButtons = new LinearLayout(this);
        recordButtons.setOrientation(LinearLayout.HORIZONTAL);
        startButton = new Button(this);
        startButton.setId(R.id.lc_track_start);
        startButton.setText(R.string.lc_track_start);
        startButton.setOnClickListener(v -> startRecording());
        stopButton = new Button(this);
        stopButton.setId(R.id.lc_track_stop);
        stopButton.setText(R.string.lc_track_stop);
        stopButton.setOnClickListener(v -> stopRecording());
        recordButtons.addView(startButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        recordButtons.addView(stopButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        recordCard.addView(recordButtons);
        root.addView(recordCard);

        // 未完成卡（内容在 refresh 里填）
        ongoingCard = new LinearLayout(this);
        ongoingCard.setId(R.id.lc_track_ongoing);
        ongoingCard.setOrientation(LinearLayout.VERTICAL);
        ongoingCard.setBackgroundColor(getColor(R.color.lc_toolmgr_bg));
        ongoingCard.setPadding(dp(16), dp(10), dp(16), dp(10));
        ongoingCard.setVisibility(View.GONE);
        root.addView(ongoingCard);

        // 列表
        listBox = new LinearLayout(this);
        listBox.setId(R.id.lc_track_list);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);

        // 空态：插画（蓝星球 + 小人）+ `暂无历史轨迹记录`（原版取证 F15-tracklist.png）
        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.setPadding(0, dp(48), 0, dp(24));

        ImageView emptyArt = new ImageView(this);
        emptyArt.setId(R.id.lc_track_empty_illustration);
        emptyArt.setImageResource(R.drawable.lc_track_empty_planet);
        emptyBox.addView(emptyArt, new LinearLayout.LayoutParams(dp(120), dp(120)));

        TextView emptyText = new TextView(this);
        emptyText.setId(R.id.lc_track_empty);
        emptyText.setText(R.string.lc_track_empty);
        emptyText.setTextColor(getColor(R.color.lc_label_gray));
        emptyText.setTextSize(14f);
        emptyText.setPadding(0, dp(12), 0, 0);
        emptyBox.addView(emptyText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(emptyBox);

        content.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        refreshList();
        handler.post(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) refreshList();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    private void updateRecordingCard() {
        if (recordingView == null) return;
        boolean recording = TrackRecorderService.isRecording();
        long sec = TrackRecorderService.elapsedMs() / 1000;
        if (recording) {
            recordingView.setText(String.format(Locale.US, "%s %02d:%02d:%02d · %.2f km",
                    getString(R.string.lc_track_recording), sec / 3600, (sec % 3600) / 60, sec % 60,
                    TrackRecorderService.distanceM() / 1000.0));
        } else {
            recordingView.setText("");
        }
        startButton.setVisibility(recording ? View.GONE : View.VISIBLE);
        stopButton.setVisibility(recording ? View.VISIBLE : View.GONE);
    }

    private void refreshList() {
        if (listBox == null) return;
        updateRecordingCard();
        listBox.removeAllViews();

        // 未完成卡
        TrackMeta recording = store.findRecording();
        ongoingCard.removeAllViews();
        if (recording != null && !TrackRecorderService.isRecording()) {
            ongoingCard.setVisibility(View.VISIBLE);
            int count = 0;
            double dist = recording.distanceM;
            List<TrackPoint> pts = store.loadPoints(recording.id);
            count = pts.size();
            if (dist <= 0) {
                TrackStats.Result r = TrackStats.compute(pts);
                dist = r.distanceM;
            }
            TextView title = new TextView(this);
            title.setText(String.format(Locale.US, "%s（%d 点 · %.0f 米）",
                    getString(R.string.lc_track_ongoing), count, dist));
            title.setTextSize(15f);
            title.setTextColor(getColor(R.color.lc_setting_text));
            ongoingCard.addView(title);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button resume = new Button(this);
            resume.setId(R.id.lc_track_resume);
            resume.setText(R.string.lc_track_resume);
            resume.setOnClickListener(v -> resumeRecording(recording.id));
            Button finish = new Button(this);
            finish.setId(R.id.lc_track_finish);
            finish.setText(R.string.lc_track_finish);
            finish.setOnClickListener(v -> finishRecording(recording.id));
            Button discard = new Button(this);
            discard.setId(R.id.lc_track_discard);
            discard.setText(R.string.lc_track_discard);
            discard.setOnClickListener(v -> confirmDiscard(recording.id));
            row.addView(resume, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(finish, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(discard, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            ongoingCard.addView(row);
        } else {
            ongoingCard.setVisibility(View.GONE);
        }

        List<TrackMeta> metas = store.list();
        int shown = 0;
        for (TrackMeta m : metas) {
            if ("recording".equals(m.status)) continue;
            listBox.addView(buildItem(m, shown));
            shown++;
        }
        emptyBox.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private View buildItem(final TrackMeta m, int index) {
        LinearLayout row = new LinearLayout(this);
        if (index < ITEM_IDS.length) row.setId(ITEM_IDS[index]);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(getColor(R.color.lc_toolmgr_bg));
        row.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView head = new TextView(this);
        head.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(m.startedAt)));
        head.setTextSize(15f);
        head.setTextColor(getColor(R.color.lc_setting_text));
        head.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(head);

        TextView summary = new TextView(this);
        summary.setText(String.format(Locale.US, "总里程 %.2f km · 总用时 %s · 平均 %.1f km/h · 最高 %s",
                m.distanceM / 1000.0, fmtDuration(m.durationSec),
                m.avgKmh, m.maxKmh > 0 ? String.format(Locale.US, "%.1f km/h", m.maxKmh) : "—"));
        summary.setTextSize(12f);
        summary.setTextColor(getColor(R.color.lc_label_gray));
        summary.setPadding(0, dp(4), 0, dp(6));
        row.addView(summary);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button view = new Button(this);
        view.setText(R.string.lc_track_item_view);
        view.setOnClickListener(v -> openDetail(m.id));
        Button del = new Button(this);
        del.setText(R.string.lc_track_item_delete);
        del.setOnClickListener(v -> confirmDelete(m.id));
        actions.addView(view, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(del, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(actions);
        return row;
    }

    static String fmtDuration(double sec) {
        long s = Math.max(0, Math.round(sec));
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    // ---------------- 记录控制 ----------------

    private void startRecording() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            Toast.makeText(this, R.string.lc_track_permission, Toast.LENGTH_LONG).show();
            return;
        }
        String id = TrackStore.newId();
        Intent i = new Intent(this, TrackRecorderService.class)
                .setAction(TrackRecorderService.ACTION_START)
                .putExtra(TrackRecorderService.EXTRA_ID, id);
        startTrackService(i);
        refreshList();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        }
    }

    private void stopRecording() {
        startTrackService(new Intent(this, TrackRecorderService.class)
                .setAction(TrackRecorderService.ACTION_STOP));
        handler.postDelayed(this::refreshList, 800);
    }

    private void resumeRecording(String id) {
        startTrackService(new Intent(this, TrackRecorderService.class)
                .setAction(TrackRecorderService.ACTION_RESUME)
                .putExtra(TrackRecorderService.EXTRA_ID, id));
        refreshList();
    }

    private void finishRecording(String id) {
        TrackMeta m = store.loadMeta(id);
        if (m == null) return;
        List<TrackPoint> pts = store.loadPoints(id);
        TrackStats.Result r = TrackStats.compute(pts);
        m.status = "finished";
        m.stoppedAt = System.currentTimeMillis();
        m.points = r.points;
        m.distanceM = r.distanceM;
        m.durationSec = r.movingSec;
        m.avgKmh = r.avgKmh;
        m.maxKmh = r.maxKmh;
        m.segments = Math.max(1, r.segments);
        store.saveMeta(m);
        refreshList();
    }

    private void confirmDiscard(final String id) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.lc_track_discard_msg)
                .setPositiveButton(R.string.lc_track_discard, (d, w) -> {
                    store.delete(id);
                    refreshList();
                })
                .setNegativeButton(R.string.lc_compasslist_cancel, null)
                .show();
    }

    private void startTrackService(Intent i) {
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void openDetail(String id) {
        Intent i = new Intent(this, TrackDetailActivity.class);
        i.putExtra("track_id", id);
        startActivity(i);
    }

    private void confirmDelete(final String id) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.lc_track_delete_msg)
                .setPositiveButton(R.string.lc_track_item_delete, (d, w) -> {
                    store.delete(id);
                    refreshList();
                })
                .setNegativeButton(R.string.lc_compasslist_cancel, null)
                .show();
    }

    // ---------------- 轨迹设置面板（1:1 权限设置四行 + LC 增量记录参数） ----------------

    private void showSettingsSheet() {
        LinearLayout panel = new LinearLayout(this);
        panel.setId(R.id.lc_track_settings_sheet);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(10), dp(18), dp(8));

        TextView title = new TextView(this);
        title.setId(R.id.lc_track_settings_title);
        title.setText(R.string.lc_track_settings);
        title.setTextSize(16f);
        title.setTextColor(getColor(R.color.lc_setting_text));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panel.addView(title);

        // 分区：权限设置（四行原文见 F15-track-setting.png）
        TextView permTitle = new TextView(this);
        permTitle.setId(R.id.lc_track_perm_title);
        permTitle.setText(R.string.lc_track_perm_title);
        permTitle.setTextSize(12f);
        permTitle.setTextColor(getColor(R.color.lc_label_gray));
        permTitle.setPadding(0, dp(8), 0, dp(2));
        panel.addView(permTitle);

        // 行1：后台定位（右侧开关，默认关）。
        // 原版此开关申请「始终允许后台定位」；LC 用前台服务记录，manifest 不声明
        // ACCESS_BACKGROUND_LOCATION（方案 §3：不申请持续定位门），所以开关只反映系统授权状态，
        // 点它跳应用详情由用户自己决定，不替用户申请权限。
        LinearLayout bgLocRow = permRow(R.string.lc_track_perm_bg_location,
                R.id.lc_track_settings_bg_location);
        LcPillSwitch bgLoc = new LcPillSwitch(this);
        bgLoc.setChecked(hasBackgroundLocation());
        bgLoc.setOnCheckedChangeListener((v, c) -> {
            v.setChecked(hasBackgroundLocation()); // 状态以系统授权为准，不由开关自转
            openAppDetails();
        });
        bgLocRow.addView(bgLoc, new LinearLayout.LayoutParams(dp(46.2f), dp(23.1f)));
        panel.addView(bgLocRow);

        // 行2：忽略省电优化（按钮「去设置」）
        LinearLayout batteryRow = permRow(R.string.lc_track_perm_battery,
                R.id.lc_track_settings_battery);
        TextView batteryGo = statusButton(R.id.lc_track_settings_battery_go,
                getString(R.string.lc_track_go_settings), true, v -> requestIgnoreBattery());
        batteryRow.addView(batteryGo);
        panel.addView(batteryRow);

        // 行3：后台运行权限（状态按钮，点开应用详情）
        LinearLayout bgRunRow = permRow(R.string.lc_track_perm_bg_run,
                R.id.lc_track_settings_bg_run);
        boolean bgRunOn = ignoringBatteryOptimizations();
        bgRunRow.addView(statusButton(R.id.lc_track_settings_bg_run,
                stateText(bgRunOn), bgRunOn, v -> openAppDetails()));
        panel.addView(bgRunRow);

        // 行4：通知栏权限（状态按钮，点开通知设置）
        LinearLayout notifyRow = permRow(R.string.lc_track_perm_notification,
                R.id.lc_track_settings_notification);
        boolean notifyOn = notificationsEnabled();
        notifyRow.addView(statusButton(R.id.lc_track_settings_notification,
                stateText(notifyOn), notifyOn, v -> openNotificationSettings()));
        panel.addView(notifyRow);

        // 以下为 LC 增量：原版面板没有「记录参数」，保留可调采样（方案 §4）
        TextView paramsTitle = new TextView(this);
        paramsTitle.setId(R.id.lc_track_params_title);
        paramsTitle.setText(R.string.lc_track_params_title);
        paramsTitle.setTextSize(12f);
        paramsTitle.setTextColor(getColor(R.color.lc_label_gray));
        paramsTitle.setPadding(0, dp(14), 0, dp(2));
        panel.addView(paramsTitle);

        final TextView intervalBtn = new TextView(this);
        intervalBtn.setId(R.id.lc_track_settings_interval);
        intervalBtn.setTextSize(14f);
        intervalBtn.setTextColor(getColor(R.color.lc_setting_text));
        intervalBtn.setPadding(0, dp(10), 0, dp(10));
        long curInterval = settings.getLong("intervalMs", 1000);
        intervalBtn.setText(getString(R.string.lc_track_settings_interval) + "：" + (curInterval / 1000) + "s");
        intervalBtn.setOnClickListener(v -> {
            long cur = settings.getLong("intervalMs", 1000);
            long next = cur == 1000 ? 2000 : (cur == 2000 ? 5000 : 1000);
            settings.edit().putLong("intervalMs", next).apply();
            intervalBtn.setText(getString(R.string.lc_track_settings_interval) + "：" + (next / 1000) + "s");
        });
        panel.addView(intervalBtn);

        final TextView distBtn = new TextView(this);
        distBtn.setId(R.id.lc_track_settings_distance);
        distBtn.setTextSize(14f);
        distBtn.setTextColor(getColor(R.color.lc_setting_text));
        distBtn.setPadding(0, dp(6), 0, dp(10));
        float curDist = settings.getFloat("minDistanceM", 3f);
        distBtn.setText(getString(R.string.lc_track_settings_distance) + "：" + (int) curDist + "m");
        distBtn.setOnClickListener(v -> {
            float cur = settings.getFloat("minDistanceM", 3f);
            float next = cur == 3f ? 5f : (cur == 5f ? 10f : 3f);
            settings.edit().putFloat("minDistanceM", next).apply();
            distBtn.setText(getString(R.string.lc_track_settings_distance) + "：" + (int) next + "m");
        });
        panel.addView(distBtn);

        LinearLayout highRow = new LinearLayout(this);
        highRow.setOrientation(LinearLayout.HORIZONTAL);
        highRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView highLabel = new TextView(this);
        highLabel.setText(R.string.lc_track_settings_high_accuracy);
        highLabel.setTextSize(14f);
        highLabel.setTextColor(getColor(R.color.lc_setting_text));
        highRow.addView(highLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LcPillSwitch high = new LcPillSwitch(this);
        high.setId(R.id.lc_track_settings_high_accuracy);
        high.setChecked(settings.getBoolean("highAccuracy", true));
        high.setOnCheckedChangeListener((v, c) -> settings.edit().putBoolean("highAccuracy", c).apply());
        highRow.addView(high, new LinearLayout.LayoutParams(dp(46.2f), dp(23.1f)));
        panel.addView(highRow);

        TextView note = new TextView(this);
        note.setId(R.id.lc_track_settings_note);
        note.setText(R.string.lc_track_settings_note);
        note.setTextSize(12f);
        note.setTextColor(getColor(R.color.lc_label_gray));
        note.setPadding(0, dp(8), 0, dp(4));
        panel.addView(note);

        final AlertDialog[] ref = new AlertDialog[1];
        Button close = new Button(this);
        close.setId(R.id.lc_track_settings_close);
        close.setText(R.string.lc_track_close);
        close.setOnClickListener(v -> {
            if (ref[0] != null) ref[0].dismiss();
        });
        panel.addView(close);

        ref[0] = new AlertDialog.Builder(this).setView(panel).create();
        ref[0].show();
    }

    /** 权限行：左标签（带 id）+ 右侧控件由调用方追加。 */
    private LinearLayout permRow(int labelRes, int labelId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView label = new TextView(this);
        label.setId(labelId);
        label.setText(labelRes);
        label.setTextSize(14f);
        label.setTextColor(getColor(R.color.lc_setting_text));
        row.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private TextView statusButton(int id, String text, boolean on, View.OnClickListener click) {
        TextView v = new TextView(this);
        v.setId(id);
        v.setText(text);
        v.setTextSize(13f);
        v.setTextColor(on ? getColor(R.color.lc_blue) : getColor(R.color.lc_label_gray));
        v.setPadding(dp(8), dp(5), dp(8), dp(5));
        v.setOnClickListener(click);
        return v;
    }

    private String stateText(boolean on) {
        return getString(on ? R.string.lc_track_perm_on : R.string.lc_track_perm_off);
    }

    /** 后台定位授权状态（manifest 未声明该权限，正常恒为未授予）。 */
    private boolean hasBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        return checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ignoringBatteryOptimizations() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean notificationsEnabled() {
        android.app.NotificationManager nm =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm == null || nm.areNotificationsEnabled();
    }

    private void requestIgnoreBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable ignored) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable ignored2) {
            }
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Throwable ignored) {
        }
    }

    private void openNotificationSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Throwable ignored) {
        }
    }
}
