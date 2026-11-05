package com.luopan.fengshui;

import android.app.Activity;
import android.content.ClipData;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具管理页（原版 ui.pages.ToolsManagerActivity）。
 *
 * 依据 docs/gap-modules/ToolsManagerActivity.md：
 * - §1 两段式：卡片「首页展示功能」（说明 + 可拖拽网格）+ 卡片「全部功能」（网格）；
 * - §1 视觉：页面底 #F0F3F7、卡片白、内边距 12dp、顶部 10dp、两卡间距 10dp、标题 Bold #23272B、
 *   说明 #657389、网格 4 列 / 间距 10dp / 网格内边距 10dp；
 * - §1 顶栏 title=null（只有返回箭头）；
 * - §2 点击：全部功能点未添加项 → 加入首页；首页项点击 → 移除；首页项长按拖拽 → 排序；
 * - §2 上限：(屏高 - 350dp) / 56dp，非 VIP 再 -1；超限 toast「当前展示功能过多，请移除部分功能后尝试」；
 * - §3 持久化：prefs 名 "ToolsManager"、key "toolsJson2"、JSON 中文名数组、默认 5 项；
 * - 图标：原版 mipmap/ic_tool_*，已从解码资源取入；角标 svg_menu_add / svg_menu_added。
 *
 * 与原版差异（如实记录）：
 * - LC 无账户/会员体系（D-001 去商业），上限按「非 VIP」计算（即 cap-1）；
 * - 文字排序拖拽为原生 drag 实现，手感与 Compose reorderable 不完全一致，等真机截图再调。
 */
public class ToolsManagerActivity extends Activity {

    private static final int[] ITEM_IDS = {
            R.id.lc_toolmgr_item_0, R.id.lc_toolmgr_item_1, R.id.lc_toolmgr_item_2,
            R.id.lc_toolmgr_item_3, R.id.lc_toolmgr_item_4, R.id.lc_toolmgr_item_5,
            R.id.lc_toolmgr_item_6, R.id.lc_toolmgr_item_7, R.id.lc_toolmgr_item_8,
            R.id.lc_toolmgr_item_9, R.id.lc_toolmgr_item_10, R.id.lc_toolmgr_item_11,
            R.id.lc_toolmgr_item_12, R.id.lc_toolmgr_item_13, R.id.lc_toolmgr_item_14,
            R.id.lc_toolmgr_item_15
    };

    private static final int[] NOW_IDS = {
            R.id.lc_toolmgr_now_0, R.id.lc_toolmgr_now_1, R.id.lc_toolmgr_now_2,
            R.id.lc_toolmgr_now_3, R.id.lc_toolmgr_now_4, R.id.lc_toolmgr_now_5,
            R.id.lc_toolmgr_now_6, R.id.lc_toolmgr_now_7, R.id.lc_toolmgr_now_8,
            R.id.lc_toolmgr_now_9, R.id.lc_toolmgr_now_10, R.id.lc_toolmgr_now_11,
            R.id.lc_toolmgr_now_12, R.id.lc_toolmgr_now_13, R.id.lc_toolmgr_now_14,
            R.id.lc_toolmgr_now_15
    };

    private ToolsManagerStore store;
    private List<String> now;
    private int cap;
    private LinearLayout nowGrid;
    private LinearLayout allGrid;
    private int dragFrom = -1;
    private int dragTo = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ToolsManagerStore(this);
        now = store.nowMenu();
        cap = capOf(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.lc_toolmgr_bg));
        // 原版顶栏 title=null：只有返回箭头
        root.addView(LcUi.toolbarLight(this, R.id.lc_toolmgr_back, R.id.lc_toolmgr_title,
                getString(R.string.lc_toolmgr_title)));

        ScrollView scroll = LcUi.pageScroll(this, R.id.lc_toolmgr_list);
        scroll.setBackgroundColor(getColor(R.color.lc_toolmgr_bg));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, LcUi.dp(this, 10), 0, LcUi.dp(this, 20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(buildNowCard());
        content.addView(spacer(10));
        content.addView(buildAllCard());

        setContentView(root);
        refreshGrids();
    }

    /**
     * 原版上限：cap = (screenHeight - dip(350)) / dip(56)，非 VIP 再 -1（ToolsManagerActivity.md §2）。
     * LC 无账户体系，按非 VIP 计算。
     */
    private static int capOf(Activity a) {
        int screenH = a.getResources().getDisplayMetrics().heightPixels;
        int cap = (screenH - LcUi.dp(a, 350)) / Math.max(1, LcUi.dp(a, 56));
        cap -= 1; // 非 VIP
        return Math.max(1, cap);
    }

    // ---------------- 卡片 ----------------

    private View buildNowCard() {
        LinearLayout card = card();
        card.addView(cardTitle(getString(R.string.lc_toolmgr_now_title)));
        card.addView(cardDesc(getString(R.string.lc_toolmgr_desc)));
        nowGrid = new LinearLayout(this);
        nowGrid.setOrientation(LinearLayout.VERTICAL);
        nowGrid.setPadding(LcUi.dp(this, 10), LcUi.dp(this, 6),
                LcUi.dp(this, 10), LcUi.dp(this, 2));
        card.addView(nowGrid);
        return card;
    }

    private View buildAllCard() {
        LinearLayout card = card();
        card.addView(cardTitle(getString(R.string.lc_toolmgr_all_title)));
        allGrid = new LinearLayout(this);
        allGrid.setOrientation(LinearLayout.VERTICAL);
        allGrid.setPadding(LcUi.dp(this, 10), LcUi.dp(this, 8),
                LcUi.dp(this, 10), LcUi.dp(this, 2));
        card.addView(allGrid);
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(getColor(R.color.lc_toolmgr_card));
        card.setPadding(LcUi.dp(this, 12), LcUi.dp(this, 12),
                LcUi.dp(this, 12), LcUi.dp(this, 12));
        return card;
    }

    private TextView cardTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16f);
        t.setTextColor(getColor(R.color.lc_setting_text));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setPadding(LcUi.dp(this, 4), 0, LcUi.dp(this, 4), 0);
        return t;
    }

    private TextView cardDesc(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12f);
        t.setTextColor(getColor(R.color.lc_toolmgr_desc));
        t.setLineSpacing(LcUi.dp(this, 2), 1f);
        t.setPadding(LcUi.dp(this, 4), LcUi.dp(this, 8), LcUi.dp(this, 4), LcUi.dp(this, 4));
        return t;
    }

    private View spacer(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LcUi.dp(this, dp)));
        return v;
    }

    // ---------------- 网格 ----------------

    private void refreshGrids() {
        if (nowGrid == null || allGrid == null) return;
        nowGrid.removeAllViews();
        allGrid.removeAllViews();
        fillGrid(nowGrid, now, true);
        fillGrid(allGrid, java.util.Arrays.asList(ToolCatalog.LABELS), false);
    }

    private void fillGrid(LinearLayout grid, List<String> items, boolean isNow) {
        for (int start = 0; start < items.size(); start += 4) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 4; c++) {
                int index = start + c;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(LcUi.dp(this, 5), LcUi.dp(this, 5),
                        LcUi.dp(this, 5), LcUi.dp(this, 5));
                if (index < items.size()) {
                    row.addView(buildCell(items.get(index), index, isNow), lp);
                } else {
                    row.addView(new View(this), lp);
                }
            }
            grid.addView(row);
        }
    }

    private View buildCell(String label, int position, boolean isNow) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setId(isNow ? NOW_IDS[Math.min(position, NOW_IDS.length - 1)]
                : ITEM_IDS[Math.max(0, Math.min(ToolCatalog.indexOfLabel(label), ITEM_IDS.length - 1))]);

        FrameLayout iconWrap = new FrameLayout(this);
        ImageView icon = new ImageView(this);
        int iconSize = LcUi.dp(this, 40);
        icon.setLayoutParams(new FrameLayout.LayoutParams(iconSize, iconSize));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int res = ToolCatalog.iconOfLabel(label);
        if (res != 0) icon.setImageResource(res);
        iconWrap.addView(icon);

        if (!isNow) {
            ImageView badge = new ImageView(this);
            int badgeSize = LcUi.dp(this, 16);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(badgeSize, badgeSize);
            blp.gravity = Gravity.TOP | Gravity.END;
            badge.setLayoutParams(blp);
            badge.setImageResource(now.contains(label)
                    ? R.drawable.svg_menu_added
                    : R.drawable.svg_menu_add);
            iconWrap.addView(badge);
        }
        cell.addView(iconWrap, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(12f);
        name.setTextColor(getColor(R.color.lc_setting_text));
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setPadding(0, LcUi.dp(this, 4), 0, 0);
        cell.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (isNow) {
            cell.setOnClickListener(v -> removeNow(label));
            cell.setOnLongClickListener(v -> {
                dragFrom = position;
                dragTo = position;
                ClipData data = ClipData.newPlainText("tool", label);
                v.startDragAndDrop(data, new View.DragShadowBuilder(v), null, 0);
                return true;
            });
            cell.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return true;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        dragTo = position;
                        return true;
                    case DragEvent.ACTION_DROP:
                        if (dragFrom >= 0 && dragTo >= 0 && dragFrom != dragTo) {
                            moveNow(dragFrom, dragTo);
                        }
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        dragFrom = -1;
                        dragTo = -1;
                        return true;
                    default:
                        return true;
                }
            });
        } else {
            cell.setOnClickListener(v -> addNow(label));
        }
        return cell;
    }

    // ---------------- 增删排序 ----------------

    private void addNow(String label) {
        if (now.contains(label)) return;
        if (now.size() >= cap) {
            Toast.makeText(this, R.string.lc_toolmgr_cap_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        now.add(label);
        store.saveNowMenu(now);
        refreshGrids();
    }

    private void removeNow(String label) {
        if (now.remove(label)) {
            store.saveNowMenu(now);
            refreshGrids();
        }
    }

    private void moveNow(int from, int to) {
        if (from < 0 || to < 0 || from >= now.size() || to >= now.size()) return;
        List<String> next = new ArrayList<>(now);
        String item = next.remove(from);
        next.add(to, item);
        now.clear();
        now.addAll(next);
        store.saveNowMenu(now);
        refreshGrids();
    }
}
