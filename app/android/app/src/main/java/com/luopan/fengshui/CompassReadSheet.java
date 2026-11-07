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
        titleRow.setPadding(p(activity, 16), p(activity, 10), p(activity, 16), p(activity, 8));
        TextView title = new TextView(activity);
        title.setId(R.id.lc_read_title);
        title.setText("智能读盘");
        title.setTextSize(18f);
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

        // R2-013 订正（审核验 2026-11-07 像素扫描 `orig/w3-sheet.png`）：
        // 原版 sheet 就是 **14 个圈层行**，前 3 行直接显示向/坐值，后 11 行「点击查看 ›」；
        // 没有「向北 x°」「坐南 y°」「三盘」这三行（那是上一版按对话框形态加的，作废）。
        for (int t = 1; t <= 14; t++) {
            String name = clean(CompassData.RINGS[t - 1].text);
            final int tt = t;
            if (t <= 3) {
                // B 档：前 3 行显示具体向/坐值（原版 `先天八卦_卦位→向：坤 坐：乾` 等）。
                String face = clean(CompassData.ringText(t, h));
                String sit = clean(CompassData.ringText(t, back));
                addRow(activity, list, name, "向：" + face + "　坐：" + sit, false, null);
            } else {
                addRow(activity, list, name, "点击查看 ›", true, v -> {
                    String face = clean(CompassData.ringText(tt, h));
                    String sit = clean(CompassData.ringText(tt, back));
                    if (v instanceof TextView) {
                        ((TextView) v).setText("向：" + face + "　坐：" + sit);
                    }
                });
            }
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
            // R2-013-C：原版 sheet 顶边 = 1011（审核验像素扫描；上一版 380，差 −631）。
            // 屏幕 2700 高，按「顶边 1011」反算 sheet 高 ≈ 1799（本机型实测标定，换机型需重标）。
            int screenH = activity.getResources().getDisplayMetrics().heightPixels;
            lp.height = Math.max(Math.round(screenH * 0.5f), screenH - 889);
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
        row.setPadding(p(a, 16), p(a, 11), p(a, 16), p(a, 11));

        TextView nameView = new TextView(a);
        nameView.setText(name);
        nameView.setTextSize(16f);
        nameView.setTextColor(INK_GRAY);
        row.addView(nameView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView rightView = new TextView(a);
        if (clickable) {
            // 原版：金色钻石 VIP 徽标 + 蓝字「点击查看 ›」。
            // 素材 = APK 的 `mipmap-xxxhdpi/ic_compass_vip.webp`（132×112，逆向 `2026-11-07` 全量位图比对
            // 误差 13.41 vs 次名 19.97 定位）；原版真实绘制框 76×65 px（a11y 的 74×54 是被裁框）。
            // 之前用字符「◆」不是原版形态，R2-013-A 已改为此素材。
            LinearLayout tail = new LinearLayout(a);
            tail.setOrientation(LinearLayout.HORIZONTAL);
            tail.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.ImageView gem = new android.widget.ImageView(a);
            gem.setId(R.id.lc_read_badge);
            gem.setImageResource(R.drawable.lc_compass_vip);
            gem.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams gemLp = new LinearLayout.LayoutParams(77, 65);
            gemLp.rightMargin = p(a, 6);
            tail.addView(gem, gemLp);
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
        row.setPadding(p(a, 16), p(a, 11), p(a, 16), p(a, 11));

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


