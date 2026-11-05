package com.luopan.fengshui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 缺失模块页面共用的小部件（标题栏 / 滚动容器 / 分组标题 / 开关行）。
 *
 * 约定：本类不放任何业务文案，标题与条目文字全部由调用方从 strings_gap.xml 传入；
 * 证据未落地时传空串，不按想象填写。
 */
final class LcUi {

    private LcUi() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 标题栏：左返回（可点，finish）+ 标题（weight 1）。 */
    static LinearLayout toolbar(Activity a, int backId, int titleId, String title) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(a.getColor(R.color.panel));

        TextView back = new TextView(a);
        back.setId(backId);
        back.setText(R.string.lc_back);
        back.setTextSize(22f);
        back.setTextColor(a.getColor(R.color.text_primary));
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        back.setOnClickListener(v -> a.finish());
        bar.addView(back, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView t = new TextView(a);
        t.setId(titleId);
        t.setText(title);
        t.setTextSize(18f);
        t.setTextColor(a.getColor(R.color.gold));
        t.setPadding(dp(a, 8), 0, dp(a, 8), 0);
        bar.addView(t, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    /** 整页滚动容器，背景与主界面一致。 */
    static ScrollView pageScroll(Activity a, int scrollId) {
        ScrollView scroll = new ScrollView(a);
        scroll.setId(scrollId);
        scroll.setBackgroundColor(a.getColor(R.color.bg));
        return scroll;
    }

    /** 分组标题（证据未落地时 text 传空串）。 */
    static TextView groupHeader(Activity a, int id, String text) {
        TextView header = new TextView(a);
        header.setId(id);
        header.setText(text);
        header.setTextSize(13f);
        header.setTextColor(a.getColor(R.color.text_secondary));
        header.setPadding(dp(a, 20), dp(a, 18), dp(a, 20), dp(a, 6));
        return header;
    }

    /** 开关行：行 id 供 AT 定位，开关 id 供 AT 读取/切换状态。 */
    static LinearLayout switchRow(Activity a, int rowId, int switchId,
                                  String label, boolean checked) {
        LinearLayout row = new LinearLayout(a);
        row.setId(rowId);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(a, 48));
        row.setPadding(dp(a, 20), dp(a, 4), dp(a, 12), dp(a, 4));
        row.setBackgroundColor(a.getColor(R.color.panel));

        TextView tv = new TextView(a);
        tv.setText(label);
        tv.setTextSize(16f);
        tv.setTextColor(a.getColor(R.color.text_primary));
        row.addView(tv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(a);
        sw.setId(switchId);
        sw.setChecked(checked);
        row.addView(sw, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** 行点击 = 切换开关（原版开关行的通用手感；条目行为仍以证据为准）。 */
    static void bindRowToggle(View row, CompoundButton button) {
        row.setOnClickListener(v -> button.setChecked(!button.isChecked()));
    }

    /** 空态文案（证据落地后由页面传入真实文案）。 */
    static TextView emptyView(Activity a, int id, String text) {
        TextView tv = new TextView(a);
        tv.setId(id);
        tv.setText(text);
        tv.setTextColor(a.getColor(R.color.text_secondary));
        tv.setTextSize(14f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(a, 24), dp(a, 48), dp(a, 24), dp(a, 48));
        return tv;
    }

    /** 页面标准三件套：标题栏 + 滚动列表；返回整页根布局。 */
    static LinearLayout page(Activity a, String title, int backId, int titleId, int scrollId) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(a.getColor(R.color.bg));
        root.addView(toolbar(a, backId, titleId, title));
        root.addView(pageScroll(a, scrollId), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    /** 纯色占位（给未落地条目占位，避免出现假文案）。 */
    static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        return v;
    }

    /** 分隔线。 */
    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Color.argb(40, 235, 228, 215));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.5f))));
        return v;
    }
}
