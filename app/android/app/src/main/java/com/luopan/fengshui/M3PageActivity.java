package com.luopan.fengshui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * M3 Stage 2：抽屉七子页骨架（1:1 数据源 = 原版 dump 逐字文本）。
 *
 * 每页内容来自 `docs/fidelity/m3/<page>.xml` 抽取的可见文本，落到
 * `assets/m3/<page>.txt`（一行一个原版文本节点，顺序与 dump 一致）。
 * **文案不做任何改写**：第一行是页面标题（原版标题节点），其余按原序渲染。
 *
 * 服务端强依赖（D-022）按页在页内显式标注，不在页面里假装可用：
 * - 账户设置（昵称/头像/绑定手机/绑定微信/注销账户/退出登录）
 * - 投诉反馈（问题反馈/订单投诉 的提交）
 * 帮助中心原版是 **WebView 远端 URL**（ApiHelper.HELP_CENTER），网络不可用时页面内说明。
 */
public class M3PageActivity extends Activity {

    public static final String EXTRA_PAGE = "m3_page";

    /** 页名 → assets 文件名。 */
    private static final String[][] PAGES = {
            {"关于我们", "about"},
            {"账户设置", "account-setting"},
            {"投诉反馈", "complaint"},
            {"联系我们", "contact"},
            {"帮助中心", "help"},
            {"用户协议", "agreement"},
            {"推荐给好友", "recommend"},
    };

    /** 需要标注服务端依赖的页（D-022）。 */
    private static final String[][] DEPENDENCIES = {
            {"账户设置", "本页的登录/绑定/注销需服务端账号体系支持，当前未接入（D-022）——以下为原版界面文案。"},
            {"投诉反馈", "本页的提交需服务端工单接口，当前未接入（D-022）——以下为原版界面文案。"},
            {"帮助中心", "原版为在线帮助（远端 WebView），离线时无法加载正文（D-022）。"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String page = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PAGE);
        if (page == null) page = "关于我们";

        LinearLayout root = new LinearLayout(this);
        root.setId(R.id.lc_m3_root);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(26, 22, 18));

        // 标题栏：返回 + 页名（原版标题逐字）
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(20, 24, 20, 16);
        TextView back = new TextView(this);
        back.setId(R.id.lc_m3_back);
        back.setText("返回");
        back.setTextSize(15f);
        back.setTextColor(Color.rgb(235, 228, 215));
        back.setPadding(0, 12, 32, 12);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = new TextView(this);
        title.setId(R.id.lc_m3_title);
        title.setText(page);
        title.setTextSize(18f);
        title.setTextColor(Color.rgb(235, 228, 215));
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);

        // 依赖标注（有则显示）
        String dep = dependencyNote(page);
        if (dep != null) {
            TextView note = new TextView(this);
            note.setId(R.id.lc_m3_dep_note);
            note.setText(dep);
            note.setTextSize(12f);
            note.setTextColor(Color.rgb(220, 190, 130));
            note.setPadding(20, 4, 20, 16);
            root.addView(note);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.lc_m3_scroll);
        LinearLayout list = new LinearLayout(this);
        list.setId(R.id.lc_m3_body);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(20, 0, 20, 32);
        List<String> lines = readPage(page);
        // 第一行是标题（已在标题栏显示），正文从第二行起
        for (int i = 1; i < lines.size(); i++) {
            TextView tv = new TextView(this);
            tv.setText(lines.get(i));
            tv.setTextSize(15f);
            tv.setTextColor(Color.rgb(235, 228, 215));
            tv.setPadding(0, 12, 0, 12);
            list.addView(tv);
            android.util.Log.d("LCM3", page + " line " + i + " = " + lines.get(i));
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        android.util.Log.d("LCM3", "page=" + page + " lines=" + lines.size()
                + " dep=" + (dep != null));
    }

    private static String dependencyNote(String page) {
        for (String[] d : DEPENDENCIES) {
            if (d[0].equals(page)) return d[1];
        }
        return null;
    }

    /** 读 assets/m3/<file>.txt（一行一个原版文本节点）。 */
    private List<String> readPage(String page) {
        String file = null;
        for (String[] p : PAGES) {
            if (p[0].equals(page)) file = p[1];
        }
        List<String> out = new ArrayList<>();
        if (file == null) return out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                getAssets().open("m3/" + file + ".txt"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) out.add(line.trim());
            }
        } catch (Throwable t) {
            android.util.Log.e("LCM3", "read " + file + " failed", t);
        }
        return out;
    }
}
