package com.luopan.fengshui;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 功能开关（调试/回退）页。入口 = 「更多」面板标题栏「功能开关（调试）」。
 *
 * 18 行，顺序 F-01…F-18；每行显示「F-xx 名称」+ 当前状态 + 开关。
 * 默认全开（FeatureFlags 缺省 true）；关闭只回退旧 stub 行为，不出现任何「未开通/升级」文案。
 */
public class FeatureFlagsActivity extends LcFeatureActivity {

    private static final int[] ROW_IDS = {
            R.id.lc_feature_row_f01, R.id.lc_feature_row_f02, R.id.lc_feature_row_f03,
            R.id.lc_feature_row_f04, R.id.lc_feature_row_f05, R.id.lc_feature_row_f06,
            R.id.lc_feature_row_f07, R.id.lc_feature_row_f08, R.id.lc_feature_row_f09,
            R.id.lc_feature_row_f10, R.id.lc_feature_row_f11, R.id.lc_feature_row_f12,
            R.id.lc_feature_row_f13, R.id.lc_feature_row_f14, R.id.lc_feature_row_f15,
            R.id.lc_feature_row_f16, R.id.lc_feature_row_f17, R.id.lc_feature_row_f18
    };

    private static final int[] STATE_IDS = {
            R.id.lc_feature_state_f01, R.id.lc_feature_state_f02, R.id.lc_feature_state_f03,
            R.id.lc_feature_state_f04, R.id.lc_feature_state_f05, R.id.lc_feature_state_f06,
            R.id.lc_feature_state_f07, R.id.lc_feature_state_f08, R.id.lc_feature_state_f09,
            R.id.lc_feature_state_f10, R.id.lc_feature_state_f11, R.id.lc_feature_state_f12,
            R.id.lc_feature_state_f13, R.id.lc_feature_state_f14, R.id.lc_feature_state_f15,
            R.id.lc_feature_state_f16, R.id.lc_feature_state_f17, R.id.lc_feature_state_f18
    };

    private static final int[] SWITCH_IDS = {
            R.id.lc_feature_switch_f01, R.id.lc_feature_switch_f02, R.id.lc_feature_switch_f03,
            R.id.lc_feature_switch_f04, R.id.lc_feature_switch_f05, R.id.lc_feature_switch_f06,
            R.id.lc_feature_switch_f07, R.id.lc_feature_switch_f08, R.id.lc_feature_switch_f09,
            R.id.lc_feature_switch_f10, R.id.lc_feature_switch_f11, R.id.lc_feature_switch_f12,
            R.id.lc_feature_switch_f13, R.id.lc_feature_switch_f14, R.id.lc_feature_switch_f15,
            R.id.lc_feature_switch_f16, R.id.lc_feature_switch_f17, R.id.lc_feature_switch_f18
    };

    private FeatureFlags flags;

    @Override
    protected int backId() {
        return R.id.lc_featureflag_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_featureflag_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_featureflag_list;
    }

    @Override
    protected String title() {
        return getString(R.string.lc_featureflag_title);
    }

    @Override
    protected void onPageReady(FrameLayout content) {
        flags = new FeatureFlags(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(24));
        scroll.addView(list);

        for (int i = 0; i < FeatureFlags.IDS.length; i++) {
            list.addView(buildRow(i));
            list.addView(divider());
        }
        content.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private View buildRow(int index) {
        final String id = FeatureFlags.IDS[index];

        LinearLayout row = new LinearLayout(this);
        row.setId(ROW_IDS[index]);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(getColor(R.color.lc_setting_bg));
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46.5f)));

        TextView label = new TextView(this);
        label.setText(String.format("F-%02d %s", index + 1, FeatureFlags.NAMES[index]));
        label.setTextSize(15f);
        label.setTextColor(getColor(R.color.lc_setting_text));
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView state = new TextView(this);
        state.setId(STATE_IDS[index]);
        state.setTextSize(12f);
        state.setTextColor(getColor(R.color.lc_label_gray));
        state.setPadding(0, 0, dp(8), 0);
        row.addView(state);

        LcPillSwitch sw = new LcPillSwitch(this);
        sw.setId(SWITCH_IDS[index]);
        sw.setChecked(flags.isEnabled(id));
        row.addView(sw, new LinearLayout.LayoutParams(dp(46.2f), dp(23.1f)));

        updateState(state, sw.isChecked());
        sw.setOnCheckedChangeListener((v, checked) -> {
            flags.setEnabled(id, checked);
            updateState(state, checked);
        });
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    private void updateState(TextView state, boolean on) {
        state.setText(on ? R.string.lc_feature_on : R.string.lc_feature_off);
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.lc_block_gap));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1f)));
        lp.setMargins(dp(12), 0, dp(12), 0);
        line.setLayoutParams(lp);
        return line;
    }
}
