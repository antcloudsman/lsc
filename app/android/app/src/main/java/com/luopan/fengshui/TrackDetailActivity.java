package com.luopan.fengshui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** F-15 轨迹详情：四项统计 + 起止/点数/分段 + 回放/导出 GPX/删除。 */
public class TrackDetailActivity extends LcFeatureActivity {

    private TrackStore store;
    private TrackMeta meta;
    private String trackId;

    @Override
    protected int backId() {
        return R.id.lc_track_detail_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_track_detail_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_track_list;
    }

    @Override
    protected String title() {
        return getString(R.string.lc_track_detail_title);
    }

    @Override
    protected void onPageReady(FrameLayout content) {
        store = new TrackStore(this);
        trackId = getIntent().getStringExtra("track_id");
        meta = trackId == null ? null : store.loadMeta(trackId);
        if (meta == null) {
            Toast.makeText(this, R.string.lc_track_delete_msg, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.lc_setting_bg));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(root);

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        root.addView(cards);
        addStat(cards, R.id.lc_track_detail_distance,
                getString(R.string.lc_track_detail_distance_label),
                String.format(Locale.US, "%.2f", meta.distanceM / 1000.0), "km");
        addStat(cards, R.id.lc_track_detail_duration,
                getString(R.string.lc_track_detail_duration_label),
                TrackListActivity.fmtDuration(meta.durationSec), "");
        addStat(cards, R.id.lc_track_detail_avg_speed,
                getString(R.string.lc_track_detail_avg_label),
                meta.avgKmh > 0 ? String.format(Locale.US, "%.1f", meta.avgKmh) : "—", "km/h");
        addStat(cards, R.id.lc_track_detail_max_speed,
                getString(R.string.lc_track_detail_max_label),
                meta.maxKmh > 0 ? String.format(Locale.US, "%.1f", meta.maxKmh) : "—", "km/h");

        TextView metaView = new TextView(this);
        metaView.setId(R.id.lc_track_detail_meta);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        metaView.setText(String.format(Locale.US, "起 %s · 止 %s · 点数 %d · 分段 %d · 采样 %ds",
                fmt.format(new Date(meta.startedAt)),
                meta.stoppedAt > 0 ? fmt.format(new Date(meta.stoppedAt)) : "—",
                meta.points, Math.max(1, meta.segments), Math.max(1, meta.intervalMs / 1000)));
        metaView.setTextSize(12f);
        metaView.setTextColor(getColor(R.color.lc_label_gray));
        metaView.setPadding(dp(4), dp(10), dp(4), dp(10));
        root.addView(metaView);

        Button replay = new Button(this);
        replay.setId(R.id.lc_track_detail_replay);
        replay.setText(R.string.lc_track_detail_replay);
        replay.setOnClickListener(v -> openReplay());
        root.addView(replay, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button export = new Button(this);
        export.setId(R.id.lc_track_detail_export);
        export.setText(R.string.lc_track_detail_export);
        export.setOnClickListener(v -> exportGpx());
        root.addView(export, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button delete = new Button(this);
        delete.setId(R.id.lc_track_detail_delete);
        delete.setText(R.string.lc_track_detail_delete);
        delete.setOnClickListener(v -> confirmDelete());
        root.addView(delete, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void addStat(LinearLayout parent, int id, String label, String value, String unit) {
        LinearLayout row = new LinearLayout(this);
        row.setId(id);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(getColor(R.color.lc_toolmgr_bg));
        row.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(14f);
        l.setTextColor(getColor(R.color.lc_setting_text));
        row.addView(l, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(unit.isEmpty() ? value : value + " " + unit);
        v.setTextSize(18f);
        v.setTextColor(getColor(R.color.lc_setting_text));
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        row.addView(v);
        parent.addView(row);
    }

    private void openReplay() {
        // 回放页在下一笔（F-15 第二笔）接入地图桥；本笔先如实提示，不锁功能。
        Toast.makeText(this, "回放页下一笔接入", Toast.LENGTH_SHORT).show();
    }

    private void exportGpx() {
        List<TrackPoint> points = store.loadPoints(trackId);
        if (points.isEmpty()) {
            Toast.makeText(this, R.string.lc_track_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = store.exportGpx(trackId, points);
        if (uri == null) {
            Toast.makeText(this, R.string.lc_compasslist_import_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/gpx+xml");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.lc_track_detail_export)));
        } catch (Throwable ignored) {
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.lc_track_delete_msg)
                .setPositiveButton(R.string.lc_track_detail_delete, (d, w) -> {
                    store.delete(trackId);
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton(R.string.lc_compasslist_cancel, null)
                .show();
    }
}
