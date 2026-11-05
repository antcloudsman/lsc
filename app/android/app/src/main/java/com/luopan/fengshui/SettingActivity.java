package com.luopan.fengshui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 地图设置页（原版真身是 {@code ui.pages.setting.MapSettingActivity}，不是死代码 SettingActivity）。
 * 类名沿用我们自己的 SettingActivity，字段与语义照 MapSettingActivity。
 *
 * 依据 docs/gap-modules/MapSettingActivity.md：
 * - 标题「地图设置」，返回 = finish（§1 $e.f / $e.g）；
 * - 内容顺序：地图滤镜（模式 常规/自然/高亮/自定义 + 地图效果预览）→ 6 个开关 → 坐标系 → 经纬度显示格式（§1 表）；
 * - 开关文案与默认值、滤镜三组预设、枚举 code/display、持久化键全部照该文档 §2–§4；
 * - SwitchItem 视觉：白底、水平 12dp / 垂直 11dp、15sp Medium #FF23272B、右侧 Switch（§1 J0）。
 *
 * 未做（等证据，不编）：
 * - 参数面板 5 个滑杆：量程与「自定义」嵌套未闭合（§1 末），滑杆量程未取证；
 * - 地图效果预览：依赖 LC地图 的栅格参数通道（rasterBrightnessMin/Max…），先留带 id 的占位区；
 * - 入口：原版在抽屉第 1 项「地图设置」（§4），等 K-01 抽屉落地再挂；AT 可 am start 直达。
 *
 * 持久化：MapSettingsStore（prefs 名 "MapManager"，键/默认值照原版 MMKV）。
 */
public class SettingActivity extends Activity {

    private static final int[] ROW_IDS = {
            R.id.lc_setting_row_0, R.id.lc_setting_row_1, R.id.lc_setting_row_2,
            R.id.lc_setting_row_3, R.id.lc_setting_row_4, R.id.lc_setting_row_5
    };

    private static final int[] SWITCH_IDS = {
            R.id.lc_setting_switch_0, R.id.lc_setting_switch_1, R.id.lc_setting_switch_2,
            R.id.lc_setting_switch_3, R.id.lc_setting_switch_4, R.id.lc_setting_switch_5
    };

    private static final int[] MODE_IDS = {
            R.id.lc_setting_filter_mode_0, R.id.lc_setting_filter_mode_1,
            R.id.lc_setting_filter_mode_2, R.id.lc_setting_filter_mode_3
    };

    private static final int[] MODE_LABELS = {
            R.string.lc_setting_filter_common, R.string.lc_setting_filter_nature,
            R.string.lc_setting_filter_highlight, R.string.lc_setting_filter_custom
    };

    private final TextView[] modeChips = new TextView[4];
    private MapSettingsStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new MapSettingsStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.lc_setting_bg));
        root.addView(LcUi.toolbarLight(this, R.id.lc_setting_back, R.id.lc_setting_title,
                getString(R.string.lc_setting_title)));

        ScrollView scroll = LcUi.pageScroll(this, R.id.lc_setting_list);
        scroll.setBackgroundColor(getColor(R.color.lc_setting_bg));
        LinearLayout list = new LinearLayout(this);
        list.setId(R.id.lc_setting_groups);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, LcUi.dp(this, 24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        buildFilter(list);
        buildSwitches(list);
        buildDropdowns(list);

        setContentView(root);
    }

    // ---------------- 地图滤镜（模式 + 预览） ----------------

    private void buildFilter(LinearLayout list) {
        list.addView(sectionTitle(getString(R.string.lc_setting_filter)));

        TextView modeLabel = new TextView(this);
        modeLabel.setText(R.string.lc_setting_filter_mode);
        modeLabel.setTextSize(15f);
        modeLabel.setTextColor(getColor(R.color.lc_setting_text));
        modeLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        modeLabel.setPadding(LcUi.dp(this, 12), LcUi.dp(this, 11), LcUi.dp(this, 12), 0);
        list.addView(modeLabel);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(LcUi.dp(this, 12), LcUi.dp(this, 8), LcUi.dp(this, 12), LcUi.dp(this, 4));
        for (int i = 0; i < MODE_IDS.length; i++) {
            final int mode = i;
            TextView chip = new TextView(this);
            chip.setId(MODE_IDS[i]);
            chip.setText(MODE_LABELS[i]);
            chip.setTextSize(15f);
            chip.setTextColor(getColor(R.color.lc_setting_text));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, LcUi.dp(this, 7), 0, LcUi.dp(this, 7));
            chip.setOnClickListener(v -> selectMode(mode));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(LcUi.dp(this, 2), 0, LcUi.dp(this, 2), 0);
            chips.addView(chip, lp);
            modeChips[i] = chip;
        }
        list.addView(chips);
        refreshModeChips(store.colorMode());

        TextView previewLabel = new TextView(this);
        previewLabel.setText(R.string.lc_setting_filter_preview);
        previewLabel.setTextSize(15f);
        previewLabel.setTextColor(getColor(R.color.lc_setting_text));
        previewLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        previewLabel.setPadding(LcUi.dp(this, 12), LcUi.dp(this, 11), LcUi.dp(this, 12), LcUi.dp(this, 4));
        list.addView(previewLabel);

        // 预览画布：原版由 MapWebController 按当前栅格参数渲染；等 LC地图 的桥接通道。
        View preview = new View(this);
        preview.setId(R.id.lc_setting_filter_preview);
        preview.setBackgroundColor(getColor(R.color.lc_setting_preview_bg));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LcUi.dp(this, 180));
        plp.setMargins(LcUi.dp(this, 12), 0, LcUi.dp(this, 12), LcUi.dp(this, 8));
        list.addView(preview, plp);
    }

    private void selectMode(int mode) {
        store.selectColorMode(mode);
        refreshModeChips(mode);
    }

    private void refreshModeChips(int mode) {
        for (int i = 0; i < modeChips.length; i++) {
            TextView chip = modeChips[i];
            if (chip == null) continue;
            boolean on = i == mode;
            chip.setBackgroundColor(on
                    ? getColor(R.color.lc_setting_chip_selected)
                    : getColor(android.R.color.transparent));
            chip.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    // ---------------- 6 个开关（顺序与默认值照文档 §1 / §4） ----------------

    private void buildSwitches(LinearLayout list) {
        addSwitchRow(list, 0, R.string.lc_setting_show_road,
                store.showRoad(), b -> store.setShowRoad(b));
        addSwitchRow(list, 1, R.string.lc_setting_allow_rotate,
                store.isRotate(), b -> store.setIsRotate(b));
        addSwitchRow(list, 2, R.string.lc_setting_back_last,
                store.backToLastMapCenter(), b -> store.setBackToLastMapCenter(b));
        addSwitchRow(list, 3, R.string.lc_setting_keep_screen_on,
                store.keepScreenOn(), b -> store.setKeepScreenOn(b));
        addSwitchRow(list, 4, R.string.lc_setting_show_3d_control,
                store.show3dControl(), b -> store.setShow3dControl(b));
        addSwitchRow(list, 5, R.string.lc_setting_show_center_cross,
                store.showMapCenterCross(), b -> store.setShowMapCenterCross(b));
    }

    private void addSwitchRow(LinearLayout parent, int index, int labelRes,
                              boolean checked, Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setId(ROW_IDS[index]);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(getColor(R.color.lc_setting_bg));
        row.setPadding(LcUi.dp(this, 12), LcUi.dp(this, 11),
                LcUi.dp(this, 12), LcUi.dp(this, 11));

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(15f);
        label.setTextColor(getColor(R.color.lc_setting_text));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setId(SWITCH_IDS[index]);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((v, isChecked) -> onChange.accept(isChecked));
        row.addView(sw, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 行点击 = 切开关（与 Android 开关行一致的手感）
        row.setOnClickListener(v -> sw.toggle());
        parent.addView(row);
        childDivider(parent);
    }

    // ---------------- 2 个下拉（坐标系 / 经纬度显示格式） ----------------

    private void buildDropdowns(LinearLayout list) {
        int crs = MapSettingsStore.crsIndex(store.crsCode());
        if (crs < 0) crs = 0;
        addDropdownRow(list, R.id.lc_setting_crs, R.id.lc_setting_crs_value,
                getString(R.string.lc_setting_crs), MapSettingsStore.CRS_DISPLAY, crs,
                idx -> store.setCrsCode(MapSettingsStore.CRS_CODES[idx]));

        int fmt = MapSettingsStore.formatIndex(store.lngLatFormatCode());
        if (fmt < 0) fmt = 0;
        addDropdownRow(list, R.id.lc_setting_format, R.id.lc_setting_format_value,
                getString(R.string.lc_setting_lnglat_format), MapSettingsStore.FORMAT_DISPLAY, fmt,
                idx -> store.setLngLatFormatCode(MapSettingsStore.FORMAT_CODES[idx]));
    }

    private void addDropdownRow(LinearLayout parent, int rowId, int valueId,
                                String label, String[] displays, int selected,
                                IntConsumer onPick) {
        final int[] current = {selected};

        LinearLayout row = new LinearLayout(this);
        row.setId(rowId);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(getColor(R.color.lc_setting_bg));
        row.setPadding(LcUi.dp(this, 12), LcUi.dp(this, 11),
                LcUi.dp(this, 12), LcUi.dp(this, 11));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(15f);
        labelView.setTextColor(getColor(R.color.lc_setting_text));
        labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setId(valueId);
        value.setText(displays[current[0]]);
        value.setTextSize(15f);
        value.setTextColor(getColor(R.color.lc_setting_text));
        row.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(label)
                .setSingleChoiceItems(displays, current[0], (d, which) -> {
                    current[0] = which;
                    value.setText(displays[which]);
                    onPick.accept(which);
                    d.dismiss();
                })
                .show());

        parent.addView(row);
        childDivider(parent);
    }

    // ---------------- 小工具 ----------------

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16f);
        t.setTextColor(getColor(R.color.lc_setting_text));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(LcUi.dp(this, 12), LcUi.dp(this, 16), LcUi.dp(this, 12), LcUi.dp(this, 4));
        return t;
    }

    private void childDivider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.lc_setting_divider));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, LcUi.dp(this, 0.5f)));
        lp.setMargins(LcUi.dp(this, 12), 0, LcUi.dp(this, 12), 0);
        parent.addView(line, lp);
    }
}
