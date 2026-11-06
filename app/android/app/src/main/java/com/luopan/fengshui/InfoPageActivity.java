package com.luopan.fengshui;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 抽屉 DI 项的统一落点页（R2-018）。
 *
 * 原版那 8 项（账户设置/投诉反馈/关于我们/联系我们/开具发票/帮助中心/用户协议/…）都是 WebView 子页，
 * 内容本轮未取证。这里只写事实：标题沿用抽屉项名，正文一句「原版为独立 WebView 子页、内容未取证」，
 * 不编造条款/联系方式/发票流程。它的作用是让 9 项都**真正到达一个 Activity**，而不是弹 toast。
 */
public class InfoPageActivity extends LcFeatureActivity {

    static final String EXTRA_TITLE = "lc_info_title";

    @Override
    protected int backId() {
        return R.id.lc_info_back;
    }

    @Override
    protected int titleId() {
        return R.id.lc_info_title;
    }

    @Override
    protected int contentId() {
        return R.id.lc_info_content;
    }

    @Override
    protected String title() {
        String t = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_TITLE);
        return t == null || t.isEmpty() ? "信息" : t;
    }

    @Override
    protected void onPageReady(FrameLayout content) {
        TextView body = new TextView(this);
        body.setId(R.id.lc_info_body);
        body.setText("原版为独立 WebView 子页，内容本轮未取证（DI 登记），不编造页面内容。");
        body.setTextSize(13f);
        body.setTextColor(Color.rgb(90, 96, 106));
        body.setGravity(Gravity.START);
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        content.addView(body, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
