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

        // 加载中 / 失败两态都必须可见（D-023：不白屏）；失败时给「重试」，不假装加载成功
        final TextView status = new TextView(this);
        status.setId(R.id.lc_web_status);
        status.setText("加载中…");
        status.setTextSize(12f);
        status.setTextColor(Color.rgb(120, 120, 120));
        status.setPadding(20, 0, 20, 8);
        root.addView(status);

        final TextView retry = new TextView(this);
        retry.setId(R.id.lc_web_retry);
        retry.setText("重试");
        retry.setTextSize(13f);
        retry.setTextColor(Color.rgb(50, 90, 182));
        retry.setPadding(20, 0, 20, 8);
        retry.setVisibility(android.view.View.GONE);
        root.addView(retry);

        final WebView web = new WebView(this);
        web.setId(R.id.lc_web_view);
        web.setBackgroundColor(Color.WHITE);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        final boolean[] failed = {false};
        retry.setOnClickListener(v -> {
            failed[0] = false;
            retry.setVisibility(android.view.View.GONE);
            status.setText("加载中…");
            status.setTextColor(Color.rgb(120, 120, 120));
            web.reload();
        });
        web.setWebViewClient(new WebViewClient() {
            private void fail(String why, String u) {
                failed[0] = true;
                status.setText("加载失败：" + why + "\n原版同样依赖远端服务（D-023），离线时两边都不可用；可点「重试」。");
                status.setTextColor(Color.rgb(180, 60, 60));
                status.setVisibility(android.view.View.VISIBLE);
                retry.setVisibility(android.view.View.VISIBLE);
                android.util.Log.d("LCM3WEB", "load fail url=" + u + " why=" + why);
            }

            @Override
            public void onPageStarted(WebView v, String u, android.graphics.Bitmap f) {
                status.setVisibility(android.view.View.VISIBLE);
                status.setText("加载中…");
                status.setTextColor(Color.rgb(120, 120, 120));
                retry.setVisibility(android.view.View.GONE);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                if (failed[0]) return;   // 失败态保留，不被 onPageFinished 清掉
                status.setText("");
                status.setVisibility(android.view.View.GONE);
            }

            @Override
            public void onReceivedError(WebView v, android.webkit.WebResourceRequest req,
                                        android.webkit.WebResourceError err) {
                if (req != null && !req.isForMainFrame()) return;
                fail(err == null ? "未知错误" : String.valueOf(err.getDescription()),
                        req == null ? null : req.getUrl().toString());
            }

            @Override
            public void onReceivedError(WebView v, int c, String d, String u) {
                fail(d == null ? ("code " + c) : d, u);
            }

            @Override
            public void onReceivedHttpError(WebView v, android.webkit.WebResourceRequest req,
                                            android.webkit.WebResourceResponse r) {
                if (req == null || !req.isForMainFrame()) return;
                fail("HTTP " + (r == null ? "?" : String.valueOf(r.getStatusCode())),
                        req.getUrl().toString());
            }
        });
        if (url != null && !url.isEmpty()) web.loadUrl(url);
        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        android.util.Log.d("LCM3WEB", "title=" + title + " url=" + url);
    }
}
