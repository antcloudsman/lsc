package com.luopan.fengshui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 地图设置页（原版真身 ui.pages.setting.MapSettingActivity）。类名沿用我们的 SettingActivity。
 *
 * 依据：
 * - 结构/语义/默认值：docs/gap-modules/MapSettingActivity.md（§1 内容顺序、§2 预设、§3 枚举、§4 MMKV）；
 * - 参数面板量程/粒度：逆向 f93ddbb（H0 三参 ×100 于 h0.b、setter ÷100；亮度 0..1、饱和/对比 -1..1、
 *   色相 -180..180；显示 %.1f / %.0f），仅「自定义」态组合（C0 的 L0("自定义",3,…) → AnimatedVisibility）；
 * - 视觉尺寸/颜色：lc-gap-evidence/nav/mapsetting/INDEX.md 真机实测
 *   （页面白 #FFFFFF、区块间隔 #F0F3F7 39px、标题栏 52dp、开关 156×78px、行距 157px、
 *    chip 245×97px、预览 1148×636px、选中蓝 #1C45A5、开关蓝 #2E53AC、关闭灰 #C0C4CC）。
 *
 * 未做（等证据，不编）：
 * - 参数区说明文字：只取证到第一行全文，其余四行 DEX 里是省略状态，等全文再补；
 * - 地图效果预览内容：需要 LC地图 的栅格参数通道，先留同尺寸占位；
 * - 入口：原版抽屉第 1 项「地图设置」（真机行中心 y=866），等 K-01 抽屉落地再挂。
 *
 * 仍属推断（INDEX 明说未量）：精确 sp 字号、字体族、圆角半径。相关处标 TODO(待定)。
 */
public class SettingActivity extends Activity {

    private static final float ROW_H_DP = 46.5f;
    private static final float CHIP_W_DP = 72.6f;
    private static final float CHIP_H_DP = 28.7f;

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

    private static final int[] PARAM_IDS = {
            R.id.lc_setting_param_0, R.id.lc_setting_param_1, R.id.lc_setting_param_2,
            R.id.lc_setting_param_3, R.id.lc_setting_param_4
    };

    private static final int[] PARAM_VALUE_IDS = {
            R.id.lc_setting_param_value_0, R.id.lc_setting_param_value_1,
            R.id.lc_setting_param_value_2, R.id.lc_setting_param_value_3,
            R.id.lc_setting_param_value_4
    };

    private final TextView[] modeChips = new TextView[4];
    private LinearLayout paramsPanel;
    private final TextView[] paramValues = new TextView[5];
    private final SeekBar[] paramSliders = new SeekBar[5];
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
        root.addView(LcUi.blockGap(this));

        ScrollView scroll = LcUi.pageScroll(this, R.id.lc_setting_list);
        scroll.setBackgroundColor(getColor(R.color.lc_setting_bg));
        LinearLayout list = new LinearLayout(this);
        list.setId(R.id.lc_setting_groups);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        buildFilter(list);
        buildSwitches(list);
        buildDropdowns(list);
        list.addView(spacer(24));

        setContentView(root);
    }

    // ---------------- 地图滤镜（模式 + 预览） ----------------

    private void buildFilter(LinearLayout list) {
        list.addView(sectionTitle(getString(R.string.lc_setting_filter)));

        TextView modeLabel = grayLabel(getString(R.string.lc_setting_filter_mode));
        list.addView(modeLabel, padLp(12, 14, 12, 6));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(LcUi.dp(this, 11.7f), 0, LcUi.dp(this, 11.7f), 0);
        for (int i = 0; i < MODE_IDS.length; i++) {
            final int mode = i;
            TextView chip = new TextView(this);
            chip.setId(MODE_IDS[i]);
            chip.setText(MODE_LABELS[i]);
            // 实测 chip 文字字高 39px；sp 未精确量，暂按 13sp。
            chip.setTextSize(13f);
            chip.setGravity(Gravity.CENTER);
            chip.setTextColor(getColor(R.color.lc_blue));
            chip.setOnClickListener(v -> selectMode(mode));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LcUi.dp(this, CHIP_W_DP), LcUi.dp(this, CHIP_H_DP));
            lp.setMargins(i == 0 ? 0 : LcUi.dp(this, 8.35f), 0,
                    LcUi.dp(this, 8.35f), 0);
            chips.addView(chip, lp);
            modeChips[i] = chip;
        }
        list.addView(chips);
        refreshModeChips(store.colorMode());

        buildParams(list);
        paramsPanel.setVisibility(store.colorMode() == MapSettingsStore.MODE_CUSTOM
                ? View.VISIBLE : View.GONE);

        TextView previewLabel = grayLabel(getString(R.string.lc_setting_filter_preview));
        list.addView(previewLabel, padLp(12, 14, 12, 6));

        // 原版 1148×636px ≈ 340×188.5dp；内容待 LC地图 栅格参数通道接入。
        FrameLayout preview = new FrameLayout(this);
        preview.setId(R.id.lc_setting_filter_preview);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(getColor(R.color.lc_setting_preview_bg));
        // TODO(待定): 圆角半径未量，暂用 8dp。
        pbg.setCornerRadius(LcUi.dp(this, 8f));
        preview.setBackground(pbg);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LcUi.dp(this, 188.5f));
        plp.setMargins(LcUi.dp(this, 12), 0, LcUi.dp(this, 12), 0);
        list.addView(preview, plp);

        list.addView(spacer(12));
        list.addView(LcUi.blockGap(this));
    }

    private void selectMode(int mode) {
        store.selectColorMode(mode);
        refreshModeChips(mode);
        if (paramsPanel != null) {
            paramsPanel.setVisibility(mode == MapSettingsStore.MODE_CUSTOM
                    ? View.VISIBLE : View.GONE);
            if (mode == MapSettingsStore.MODE_CUSTOM) refreshParams();
        }
    }

    private void refreshModeChips(int mode) {
        for (int i = 0; i < modeChips.length; i++) {
            TextView chip = modeChips[i];
            if (chip == null) continue;
            boolean on = i == mode;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(LcUi.dp(this, CHIP_H_DP / 2f));
            if (on) {
                bg.setColor(getColor(R.color.lc_blue));
            } else {
                bg.setColor(getColor(R.color.lc_setting_bg));
                bg.setStroke(Math.max(1, LcUi.dp(this, 0.6f)),
                        getColor(R.color.lc_chip_border));
            }
            chip.setBackground(bg);
            chip.setTextColor(on ? Color.WHITE : getColor(R.color.lc_setting_text));
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
    }

    // ---------------- 参数面板（仅「自定义」展开；量程/粒度按逆向字节码 f93ddbb） ----------------
    // 量程：亮度 min/max 0..1；饱和/对比 -1..1；色相 -180..180。
    // 粒度：写回处 round（亮度/饱和/对比 ×100→/100，色相 1°）；滑杆本身不设 step（原版 H0 无 steps 参数）。

    private void buildParams(LinearLayout list) {
        paramsPanel = new LinearLayout(this);
        paramsPanel.setId(R.id.lc_setting_params_panel);
        paramsPanel.setOrientation(LinearLayout.VERTICAL);
        paramsPanel.setBackgroundColor(getColor(R.color.lc_setting_bg));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(LcUi.dp(this, 12), LcUi.dp(this, 14),
                LcUi.dp(this, 12), LcUi.dp(this, 2));

        TextView title = grayLabel(getString(R.string.lc_setting_params));
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView reset = new TextView(this);
        reset.setId(R.id.lc_setting_param_reset);
        reset.setText("↺ " + getString(R.string.lc_setting_params_reset));
        reset.setTextSize(14f);
        reset.setTextColor(getColor(R.color.lc_blue));
        reset.setPadding(LcUi.dp(this, 8), LcUi.dp(this, 4),
                LcUi.dp(this, 8), LcUi.dp(this, 4));
        reset.setOnClickListener(v -> {
            store.resetRasterToHighlight();
            refreshParams();
        });
        header.addView(reset);
        paramsPanel.addView(header);

        addParamRow(0, R.string.lc_setting_param_brightness_min, 0f, 1f, false);
        addParamRow(1, R.string.lc_setting_param_brightness_max, 0f, 1f, false);
        addParamRow(2, R.string.lc_setting_param_saturation, -1f, 1f, false);
        addParamRow(3, R.string.lc_setting_param_contrast, -1f, 1f, false);
        addParamRow(4, R.string.lc_setting_param_hue, -180f, 180f, true);

        list.addView(paramsPanel);
        refreshParams();
    }

    private void addParamRow(int index, int labelRes, float min, float max, boolean integer) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(LcUi.dp(this, 12), LcUi.dp(this, 6),
                LcUi.dp(this, 12), LcUi.dp(this, 2));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = rowLabel(getString(labelRes));
        top.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setId(PARAM_VALUE_IDS[index]);
        value.setTextSize(15f);
        value.setTextColor(getColor(R.color.lc_setting_text));
        top.addView(value);
        row.addView(top);

        SeekBar sb = new SeekBar(this);
        sb.setId(PARAM_IDS[index]);
        sb.setMax(1000);
        sb.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                float v = min + (max - min) * progress / 1000f;
                float rounded = integer ? Math.round(v) : MapSettingsStore.round2(v);
                value.setText(formatParam(rounded, integer));
                writeParam(index, rounded);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        row.addView(sb);

        paramValues[index] = value;
        paramSliders[index] = sb;
        paramsPanel.addView(row);
    }

    private void refreshParams() {
        float[] values = {
                store.rasterBrightnessMin(), store.rasterBrightnessMax(),
                store.rasterSaturation(), store.rasterContrast(), store.rasterHueRotate()
        };
        float[] mins = {0f, 0f, -1f, -1f, -180f};
        float[] maxs = {1f, 1f, 1f, 1f, 180f};
        boolean[] ints = {false, false, false, false, true};
        for (int i = 0; i < 5; i++) {
            if (paramSliders[i] == null) continue;
            float v = Math.max(mins[i], Math.min(maxs[i], values[i]));
            int progress = Math.round((v - mins[i]) / (maxs[i] - mins[i]) * 1000f);
            paramSliders[i].setProgress(progress);
            paramValues[i].setText(formatParam(v, ints[i]));
        }
    }

    private void writeParam(int index, float v) {
        switch (index) {
            case 0:
                store.setRasterBrightnessMin(v);
                break;
            case 1:
                store.setRasterBrightnessMax(v);
                break;
            case 2:
                store.setRasterSaturation(v);
                break;
            case 3:
                store.setRasterContrast(v);
                break;
            case 4:
                store.setRasterHueRotate(v);
                break;
            default:
                break;
        }
    }

    private static String formatParam(float v, boolean integer) {
        return String.format(Locale.US, integer ? "%.0f" : "%.1f", v);
    }

    // ---------------- 6 个开关（顺序/默认值见 MapSettingActivity.md §1/§4） ----------------

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
        LinearLayout row = rowContainer();
        row.setId(ROW_IDS[index]);

        TextView label = rowLabel(getString(labelRes));
        row.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LcPillSwitch sw = new LcPillSwitch(this);
        sw.setId(SWITCH_IDS[index]);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((v, isChecked) -> onChange.accept(isChecked));
        row.addView(sw, new LinearLayout.LayoutParams(
                LcUi.dp(this, 46.2f), LcUi.dp(this, 23.1f)));

        row.setOnClickListener(v -> sw.toggle());
        parent.addView(row);
        parent.addView(rowDivider());
    }

    // ---------------- 2 个下拉（坐标系 / 经纬度显示格式） ----------------

    private void buildDropdowns(LinearLayout list) {
        int crs = MapSettingsStore.crsIndex(store.crsCode());
        if (crs < 0) crs = 0;
        addDropdownRow(list, R.id.lc_setting_crs, R.id.lc_setting_crs_value,
                getString(R.string.lc_setting_crs), getString(R.string.lc_setting_pick_crs),
                MapSettingsStore.CRS_DISPLAY, crs,
                idx -> store.setCrsCode(MapSettingsStore.CRS_CODES[idx]));

        int fmt = MapSettingsStore.formatIndex(store.lngLatFormatCode());
        if (fmt < 0) fmt = 0;
        addDropdownRow(list, R.id.lc_setting_format, R.id.lc_setting_format_value,
                getString(R.string.lc_setting_lnglat_format),
                getString(R.string.lc_setting_pick_format),
                MapSettingsStore.FORMAT_DISPLAY, fmt,
                idx -> store.setLngLatFormatCode(MapSettingsStore.FORMAT_CODES[idx]));
    }

    private void addDropdownRow(LinearLayout parent, int rowId, int valueId,
                                String label, String pickerTitle, String[] displays,
                                int selected, IntConsumer onPick) {
        final int[] current = {selected};

        LinearLayout row = rowContainer();
        row.setId(rowId);

        TextView labelView = rowLabel(label);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setId(valueId);
        value.setText(displays[current[0]]);
        value.setTextSize(15f);
        value.setTextColor(getColor(R.color.lc_setting_text));
        row.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextSize(18f);
        chevron.setTextColor(getColor(R.color.lc_chevron));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = LcUi.dp(this, 6f);
        row.addView(chevron, clp);

        row.setOnClickListener(v -> showPicker(pickerTitle, displays, current[0], which -> {
            current[0] = which;
            value.setText(displays[which]);
            onPick.accept(which);
        }));

        parent.addView(row);
        parent.addView(rowDivider());
    }

    /**
     * 底部选择弹层（真机截图 18/19：标题「请选择坐标系」+ 右上 ✕ + 分隔条 + 居中选项）。
     * 选中项用蓝字 #1C45A5。
     */
    private void showPicker(String title, String[] options, int selected, IntConsumer onPick) {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.lc_setting_bg));
        // TODO(待定): 弹层顶部圆角未量，暂用 16dp。
        float r = LcUi.dp(this, 16f);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(LcUi.dp(this, 18), LcUi.dp(this, 18),
                LcUi.dp(this, 14), LcUi.dp(this, 14));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(16f);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleView.setTextColor(getColor(R.color.lc_setting_text));
        header.addView(titleView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(18f);
        close.setTextColor(getColor(R.color.lc_setting_text));
        close.setPadding(LcUi.dp(this, 8), LcUi.dp(this, 4),
                LcUi.dp(this, 8), LcUi.dp(this, 4));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close);
        sheet.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.lc_block_gap));
        sheet.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, LcUi.dp(this, 1f))));

        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            TextView option = new TextView(this);
            option.setText(options[i]);
            option.setTextSize(18f);
            option.setGravity(Gravity.CENTER);
            option.setTextColor(i == selected
                    ? getColor(R.color.lc_blue) : getColor(R.color.lc_setting_text));
            option.setOnClickListener(v -> {
                onPick.accept(idx);
                dialog.dismiss();
            });
            sheet.addView(option, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LcUi.dp(this, 47f)));

            if (i < options.length - 1) {
                View line = rowDivider();
                sheet.addView(line);
            }
        }
        sheet.addView(spacer(20));

        dialog.setContentView(sheet);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.3f);
        }
        dialog.show();
        if (w != null) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            w.setAttributes(lp);
        }
    }

    // ---------------- 小工具 ----------------

    private LinearLayout rowContainer() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(getColor(R.color.lc_setting_bg));
        row.setPadding(LcUi.dp(this, 12), 0, LcUi.dp(this, 12), 0);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LcUi.dp(this, ROW_H_DP)));
        return row;
    }

    private TextView rowLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15f);
        label.setTextColor(getColor(R.color.lc_setting_text));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return label;
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        // 实测字高 48px ≈ 16sp 加粗；sp 未精确量（INDEX 注明）。
        t.setTextSize(16f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setTextColor(getColor(R.color.lc_setting_text));
        t.setPadding(LcUi.dp(this, 12), LcUi.dp(this, 13), LcUi.dp(this, 12), 0);
        return t;
    }

    private TextView grayLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        // TODO(待定): 灰色小标题 sp 未量，暂按 14sp。
        t.setTextSize(14f);
        t.setTextColor(getColor(R.color.lc_label_gray));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private LinearLayout.LayoutParams padLp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(LcUi.dp(this, l), LcUi.dp(this, t), LcUi.dp(this, r), LcUi.dp(this, b));
        return lp;
    }

    private View rowDivider() {
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.lc_block_gap));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, LcUi.dp(this, 1f)));
        lp.setMargins(LcUi.dp(this, 12), 0, LcUi.dp(this, 12), 0);
        line.setLayoutParams(lp);
        return line;
    }

    private View spacer(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LcUi.dp(this, dp)));
        return v;
    }
}
