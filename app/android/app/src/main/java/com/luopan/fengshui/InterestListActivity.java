package com.luopan.fengshui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 收藏点列表（原版 ui.pages.interest.InterestListActivity，MODULES.md 第 94 行）。
 *
 * 本地最小闭环（语义由技术总监定，原版字段/排序等 docs/gap-modules/interest.md 落地后再对齐）：
 * 列表按 created_at 倒序 → 点击一项跳地图（setView 到该坐标）→ 长按确认删除。
 * 行格式：名称（空则「未命名」）/ 坐标 6 位小数 / 坐向 / 时间。
 */
public class InterestListActivity extends Activity {

    private static final int[] ROW_IDS = {
            R.id.lc_interest_item_0, R.id.lc_interest_item_1, R.id.lc_interest_item_2,
            R.id.lc_interest_item_3, R.id.lc_interest_item_4, R.id.lc_interest_item_5,
            R.id.lc_interest_item_6, R.id.lc_interest_item_7, R.id.lc_interest_item_8,
            R.id.lc_interest_item_9, R.id.lc_interest_item_10, R.id.lc_interest_item_11,
            R.id.lc_interest_item_12, R.id.lc_interest_item_13, R.id.lc_interest_item_14,
            R.id.lc_interest_item_15, R.id.lc_interest_item_16, R.id.lc_interest_item_17,
            R.id.lc_interest_item_18, R.id.lc_interest_item_19
    };

    private LinearLayout list;
    private GapDb db;
    private final SimpleDateFormat fmt =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new GapDb(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        root.addView(LcUi.toolbar(this, R.id.lc_interest_back, R.id.lc_interest_title,
                getString(R.string.lc_interest_title)));

        ScrollView scroll = LcUi.pageScroll(this, R.id.lc_interest_list);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, LcUi.dp(this, 4), 0, LcUi.dp(this, 16));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        reload();
    }

    @Override
    protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private void reload() {
        list.removeAllViews();
        List<InterestPoint> items = db.listInterest();
        if (items.isEmpty()) {
            list.addView(LcUi.emptyView(this, R.id.lc_interest_empty,
                    getString(R.string.lc_interest_empty)));
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            list.addView(buildRow(items.get(i), i));
        }
    }

    private View buildRow(InterestPoint p, int index) {
        LinearLayout row = new LinearLayout(this);
        // 前 20 行有稳定 id 供 AT 定位；更多行照常显示（无 id，不丢数据）。
        if (index < ROW_IDS.length) row.setId(ROW_IDS[index]);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(LcUi.dp(this, 20), LcUi.dp(this, 12),
                LcUi.dp(this, 20), LcUi.dp(this, 12));
        row.setBackgroundColor(getColor(R.color.panel));

        TextView name = new TextView(this);
        name.setText(p.name == null || p.name.isEmpty()
                ? getString(R.string.lc_interest_unnamed) : p.name);
        name.setTextSize(16f);
        name.setTextColor(getColor(R.color.text_primary));
        row.addView(name);

        TextView coords = new TextView(this);
        coords.setText(String.format(Locale.US, "%.6f, %.6f", p.lat, p.lng));
        coords.setTextSize(12f);
        coords.setTextColor(getColor(R.color.text_secondary));
        coords.setPadding(0, LcUi.dp(this, 4), 0, 0);
        row.addView(coords);

        TextView sit = new TextView(this);
        sit.setText(String.format(Locale.US, "坐%.1f° 向%.1f°", p.sittingDeg, p.facingDeg));
        sit.setTextSize(12f);
        sit.setTextColor(getColor(R.color.text_secondary));
        sit.setPadding(0, LcUi.dp(this, 2), 0, 0);
        row.addView(sit);

        TextView time = new TextView(this);
        time.setText(fmt.format(new Date(p.createdAt)));
        time.setTextSize(12f);
        time.setTextColor(getColor(R.color.text_secondary));
        time.setPadding(0, LcUi.dp(this, 2), 0, 0);
        row.addView(time);

        row.setOnClickListener(v ->
                startActivity(MapJump.intent(this, p.lat, p.lng)));
        row.setOnLongClickListener(v -> {
            confirmDelete(p);
            return true;
        });

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.addView(row);
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.panel));
        line.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, LcUi.dp(this, 0.5f))));
        wrap.addView(line);
        return wrap;
    }

    private void confirmDelete(InterestPoint p) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.lc_interest_delete_title)
                .setMessage(R.string.lc_interest_delete_msg)
                .setPositiveButton(R.string.lc_interest_delete_ok, (d, w) -> {
                    db.deleteInterest(p.id);
                    reload();
                })
                .setNegativeButton(R.string.lc_interest_cancel, null)
                .show();
    }
}
