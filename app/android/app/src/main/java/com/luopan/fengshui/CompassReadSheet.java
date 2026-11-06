package com.luopan.fengshui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * R2-013「智能读盘」——原版是**白色底部 sheet**，不是深色 AlertDialog。
 *
 * 原版形态（`docs/fidelity/orig/w3-sheet.png` 逐项对照）：
 * - 白色圆角底部 sheet，顶部圆角；标题「智能读盘」居中、右上「×」关闭；
 * - 前两行是「向北 x° → 离宅」「坐南 y° → 坐x山 向y山」，右值是普通深色字；
 * - 其余行：左侧圈层名（灰），右侧「点击查看 ›」（蓝色 + 前置钻石图标）；
 * - 共 **14 行**（对应 14 个圈层），可滚动。
 *
 * 数据仍来自 mechanics.json 的 rings[].labels（CompassData.ringText），与原版
 * getFaceText/getBackText 同源；「点击查看」行点开后给出向/坐（原版 VIP 行在这里
 * 按 D-006/T-15 有意解除付费墙，登记为「有意解除」，不是缺口）。
 */
public final class CompassReadSheet {

    private static final int WHITE = Color.rgb(255, 255, 255);
    private static final int INK = Color.rgb(51, 51, 51);
    private static final int INK_GRAY = Color.rgb(120, 120, 120);
    private static final int BLUE = Color.rgb(0, 90, 158);
    private static final int LINE = Color.rgb(232, 232, 232);
    private static final int GOLD = Color.rgb(240, 170, 60);

    private CompassReadSheet() {}

    public static void show(Activity activity, float heading) {
        show(activity, heading, null);
    }

    /** @param plateLine 三盘行（人盘/天盘），由 MainActivity 计算后传入；null 则不显示。 */
    public static void show(Activity activity, float heading, String plateLine) {
        float h = SensorMath.normalize(heading);
        float back = SensorMath.normalize(heading + 180f);
        CompassData.Reading r = CompassData.read(h);
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setId(R.id.lc_read_sheet);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(WHITE);
        bg.setCornerRadii(new float[]{40, 40, 40, 40, 0, 0, 0, 0});
        sheet.setBackground(bg);

        // 标题行：智能读盘（居中）+ 右上 ×
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(p(activity, 16), p(activity, 14), p(activity, 16), p(activity, 10));
        TextView title = new TextView(activity);
        title.setId(R.id.lc_read_title);
        title.setText("智能读盘");
        title.setTextSize(20f);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(activity);
        close.setId(R.id.lc_read_close);
        close.setText("×");
        close.setTextSize(24f);
        close.setTextColor(INK_GRAY);
        close.setPadding(p(activity, 8), 0, 0, 0);
        titleRow.addView(close);
        sheet.addView(titleRow);
        sheet.addView(divider(activity));

        ScrollView scroll = new ScrollView(activity);
        scroll.setId(R.id.lc_read_list);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        // 第 1 行：向北 x° → 离宅（原版右值是普通深色字，无「点击查看」）
        addRow(activity, list, String.format(java.util.Locale.US, "向北 %.1f°", h),
                "离宅", false, null);
        // 第 2 行：坐南 y° → 坐x山 向y山
        addRow(activity, list, String.format(java.util.Locale.US, "坐南 %.1f°", back),
                String.format(java.util.Locale.US, "坐%s山 向%s山",
                        CompassData.MOUNTAINS[r.sitting], CompassData.MOUNTAINS[r.mountain]), false, null);
        // 三盘行（R4b 口径）：人盘 = 地盘+15°、天盘 = 地盘。数据由 MainActivity.plateLine 传入，
        // 那边是 plate() 的在役调用点（numeric PlatesRegressionTest 解析）。
        if (plateLine != null && !plateLine.isEmpty()) {
            addRow(activity, list, "三盘", plateLine, false, null);
        }

        // 第 3–16 行：14 个圈层，每行「点击查看 ›」（原版逐字）
        for (int t = 1; t <= 14; t++) {
            String name = clean(CompassData.RINGS[t - 1].text);
            final int tt = t;
            addRow(activity, list, name, "点击查看 ›", true, v -> {
                String face = clean(CompassData.ringText(tt, h));
                String sit = clean(CompassData.ringText(tt, back));
                if (v instanceof TextView) {
                    ((TextView) v).setText("向：" + face + "　坐：" + sit);
                }
            });
        }
        sheet.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(sheet);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.9f);
            lp.dimAmount = 0.35f;
            w.setAttributes(lp);
        }
        android.util.Log.d("LCREAD", "sheet white rows=" + list.getChildCount());
    }

    private static void addRow(Activity a, LinearLayout list, String name, String right,
                               boolean clickable, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(p(a, 16), p(a, 8), p(a, 16), p(a, 8));

        TextView nameView = new TextView(a);
        nameView.setText(name);
        nameView.setTextSize(16f);
        nameView.setTextColor(INK_GRAY);
        row.addView(nameView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView rightView = new TextView(a);
        if (clickable) {
            // 原版：钻石图标 + 蓝字「点击查看 ›」。
            // 钻石必须**单独一个控件**：拼进同一个 TextView 会让节点文本变成「◆  点击查看 ›」，
            // 判据要的「点击查看 ›」就不再逐字（第一版就是这么错的）。
            LinearLayout tail = new LinearLayout(a);
            tail.setOrientation(LinearLayout.HORIZONTAL);
            tail.setGravity(Gravity.CENTER_VERTICAL);
            TextView gem = new TextView(a);
            gem.setText("◆");
            gem.setTextSize(14f);
            gem.setTextColor(GOLD);
            gem.setPadding(0, 0, p(a, 6), 0);
            tail.addView(gem);
            rightView.setText(right);
            rightView.setTextColor(BLUE);
            rightView.setOnClickListener(onClick);
            tail.addView(rightView);
            row.addView(tail);
            list.addView(row);
            list.addView(divider(a));
            return;
        }
        rightView.setText(right);
        rightView.setTextColor(INK);
        rightView.setTextSize(16f);
        rightView.setGravity(Gravity.END);
        row.addView(rightView);
        list.addView(row);
        list.addView(divider(a));
        return;
    }

    /** 旧实现保留占位（未使用）。 */
    private static void addRowLegacy(Activity a, LinearLayout list, String name, String right,
                                     boolean clickable, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(p(a, 16), p(a, 8), p(a, 16), p(a, 8));

        TextView nameView = new TextView(a);
        nameView.setText(name);
        nameView.setTextSize(16f);
        nameView.setTextColor(INK_GRAY);
        row.addView(nameView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView rightView = new TextView(a);
        rightView.setText(right);
        rightView.setTextColor(clickable ? BLUE : INK);
        rightView.setTextSize(16f);
        rightView.setGravity(Gravity.END);
        row.addView(rightView);
        list.addView(row);
        list.addView(divider(a));
    }

    private static View divider(Activity a) {
        View v = new View(a);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, p(a, 1))));
        return v;
    }

    private static int p(Activity a, int dp) {
        return Math.round(dp * a.getResources().getDisplayMetrics().density);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace("\u200c", "");
    }
}
