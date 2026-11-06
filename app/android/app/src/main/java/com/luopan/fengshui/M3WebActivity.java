package com.luopan.fengshui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * M3：远端 WebView 整页载体（原版 `WebActivity` 形态）。
 *
 * 逆向实测（07:33）：关于我们里点《用户协议》打开的是**应用内 WebActivity（WebView）**，
 * 不是外部浏览器（备案编号那条才是外部 Chrome）。所以帮助中心/用户协议/协议链接都走这个整页载体，
 * 而不是对话框里塞 WebView。
 *
 * URL 由调用方传入（常量集中在 M3PageActivity 一处，出处记 fidelity 文档）：
 * 帮助中心 = ApiHelper.HELP_CENTER、用户协议 = ApiHelper.USER_AGREEMENT。
 * 加载中与失败都有可见文案，不白屏（D-023）。
 */
public class M3WebActivity extends Activity {

    public static final String EXTRA_TITLE = "m3_web_title";
    public static final String EXTRA_URL = "m3_web_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_TITLE);
        String url = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_URL);

        LinearLayout root = new LinearLayout(this);
        root.setId(R.id.lc_web_root);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(255, 255, 255));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(20, 24, 20, 16);
        bar.setBackgroundColor(Color.rgb(250, 250, 250));
        TextView back = new TextView(this);
        back.setId(R.id.lc_web_back);
        back.setText("返回");
        back.setTextSize(15f);
        back.setTextColor(Color.rgb(40, 40, 40));
        back.setPadding(0, 12, 32, 12);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView titleView = new TextView(this);
        titleView.setId(R.id.lc_web_title);
        titleView.setText(title);
        titleView.setTextSize(17f);
        titleView.setTextColor(Color.rgb(40, 40, 40));
        bar.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);

        final TextView status = new TextView(this);
        status.setId(R.id.lc_web_status);
        status.setText("加载中…");
        status.setTextSize(12f);
        status.setTextColor(Color.rgb(120, 120, 120));
        status.setPadding(20, 0, 20, 8);
        root.addView(status);

        WebView web = new WebView(this);
        web.setId(R.id.lc_web_view);
        web.setBackgroundColor(Color.WHITE);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String u) {
                status.setText("");
            }

            @Override
            public void onReceivedError(WebView v, int c, String d, String u) {
                status.setText("加载失败（D-023：原版依赖远端，离线时同样不可用）");
            }
        });
        if (url != null && !url.isEmpty()) web.loadUrl(url);
        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        android.util.Log.d("LCM3WEB", "title=" + title + " url=" + url);
    }
}
