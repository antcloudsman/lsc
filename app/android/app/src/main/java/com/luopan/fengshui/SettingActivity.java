package com.luopan.fengshui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * 设置页骨架（原版 ui.pages.setting.SettingActivity，class 见 MODULES.md 第 70 行）。
 *
 * 当前只搭结构：标题栏 + 分组容器 + 一个开关项占位。
 * 分组数量/顺序/文案/开关默认值一律等 docs/gap-modules/setting.md，禁止按想象补。
 * 入口未挂（原版入口是运行时动态导航，LC逆向用 Frida 抓 Activity.start 后再接）。
 * AT 可用 `am start -n com.luopan.fengshui/.SettingActivity` 直达。
 */
public class SettingActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        root.addView(LcUi.toolbar(this, R.id.lc_setting_back, R.id.lc_setting_title,
                getString(R.string.lc_setting_title)));

        ScrollView scroll = LcUi.pageScroll(this, R.id.lc_setting_list);
        LinearLayout groups = new LinearLayout(this);
        groups.setId(R.id.lc_setting_groups);
        groups.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(groups);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- 证据未落地：仅一个空文案分组 + 一个空文案开关占位 ----
        // 替换方式：按 docs/gap-modules/setting.md 的分组/条目顺序，用
        // LcUi.groupHeader(...) / LcUi.switchRow(...) 逐条生成，id 按 _0.._N 递增。
        groups.addView(LcUi.groupHeader(this, R.id.lc_setting_group_0, ""));
        LinearLayout row = LcUi.switchRow(this, R.id.lc_setting_row_0,
                R.id.lc_setting_switch_0, "", false);
        LcUi.bindRowToggle(row, (android.widget.CompoundButton) row.getChildAt(1));
        groups.addView(row);

        setContentView(root);
    }
}
