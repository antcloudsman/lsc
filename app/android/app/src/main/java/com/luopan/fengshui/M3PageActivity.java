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
            {"帮助中心", "原版为在线帮助（远端 WebView），离线时无法加载正文（D-023）。"},
            {"用户协议", "原版为远端 WebView（正文由第三方托管），离线时无法加载（D-023）。"},
            {"联系我们", "本页文案含原版域名，按 copy 门禁暂挂起，等裁定后接入（原文见 docs/fidelity/m3-text/contact.txt）。"},
    };

    /**
     * 远端 WebView 页。URL 只在此处出现一次，出处记在 fidelity 文档里（不进 values/、不散落）：
     * 帮助中心 = ApiHelper.HELP_CENTER（yuque 组织 wiki）；用户协议 = ApiHelper.USER_AGREEMENT。
     * 两者都是原版的远端行为；离线/失败时页面必须可见标注（D-023）。
     */
    private static final String HELP_URL = "https://xinzhispace.yuque.com/org-wiki-xinzhispace-zrccm1/mh7qvx?#";
    private static final String AGREEMENT_URL = "https://agreement.yuan9.cn/agreement/swssqjdt/yhxyb.html";
    /** 隐私政策：原版 WebActivity.i.b(ctx, "privacy.html", "隐私政策") —— 与用户协议同一 base。 */
    private static final String PRIVACY_URL = "https://agreement.yuan9.cn/agreement/swssqjdt/privacy.html";
    /** 备案编号：原版 ACTION_VIEW 走**外部浏览器**（ApiHelper 常量 https://beian.miit.gov.cn/）。 */
    private static final String BEIAN_URL = "https://beian.miit.gov.cn/";

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
        // 三页有专属结构（不是纯文本列表）：投诉反馈两级、推荐给好友分享面板、账户设置未登录态
        if ("投诉反馈".equals(page)) {
            buildComplaint(list);
        } else if ("推荐给好友".equals(page)) {
            buildShare(list);
        } else if ("账户设置".equals(page)) {
            buildAccount(list);
        } else if ("关于我们".equals(page)) {
            buildAbout(list, lines);
        } else if ("联系我们".equals(page)) {
            buildContact(list, lines);
        } else if (webUrlOf(page) != null) {
            // 帮助中心 / 用户协议：原版就是远端 WebView，正文只由 WebView 呈现，不再叠一份本地文本
            android.util.Log.d("LCM3", page + " -> webview only");
        } else {
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
        }
        if (lines.size() <= 1) {
            // 文案未接入（如 copy 门禁挂起的页）：不留白，显式说明，不臆造内容
            TextView empty = new TextView(this);
            empty.setId(R.id.lc_m3_empty);
            empty.setText(needNote(page));
            empty.setTextSize(14f);
            empty.setTextColor(Color.rgb(200, 190, 170));
            empty.setPadding(0, 16, 0, 16);
            list.addView(empty);
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 帮助中心 / 用户协议：原版就是远端 WebView，直接加载同一 URL；加载失败/离线时页面内已有 D-023 标注。
        String webUrl = webUrlOf(page);
        if (webUrl != null) {
            android.webkit.WebView web = new android.webkit.WebView(this);
            web.setId(R.id.lc_m3_web);
            web.setBackgroundColor(Color.WHITE);
            android.webkit.WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            web.setWebViewClient(new android.webkit.WebViewClient() {
                @Override
                public void onReceivedError(android.webkit.WebView v, int errorCode, String desc, String failingUrl) {
                    android.util.Log.d("LCM3", "web error " + errorCode + " " + desc);
                }
            });
            web.loadUrl(webUrl);
            root.addView(web, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            android.util.Log.d("LCM3", "web url=" + webUrl);
        }

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

    /**
     * 投诉反馈（M3-02）：原版是**两级页面** ——
     * 一级列表：`问题反馈` / `订单投诉` 两行 + 右上「我的」；
     * 二级表单（点「问题反馈」）：`申请原因` / `联系方式`+hint / `问题描述`+hint / `问题截图` / 静态提示行 / `提交`。
     * 文案取自 `docs/fidelity/m3/complaint-form.xml`（逆向 4e8a10c 更正后的逐字版本）。
     * 空提交校验原版未取证（需真发请求，未授权）→ 这里做**本地必填校验**，提示只用已取证的静态提示语，
     * 提交动作按 D-022 明确「未接入」，不伪造成功。
     */
    private void buildComplaint(LinearLayout list) {
        // 一级：列表
        addRow(list, "问题反馈", "", true, v -> {
            list.removeAllViews();
            buildComplaintForm(list);
        });
        addRow(list, "订单投诉", "未选择任何订单", false, null);
    }

    private void buildComplaintForm(LinearLayout list) {
        addLabel(list, "申请原因");
        final android.widget.EditText reason = addInput(list, "");
        addLabel(list, "联系方式");
        final android.widget.EditText contact = addInput(list, "请输入您的联系方式");
        addLabel(list, "问题描述");
        final android.widget.EditText desc = addInput(list, "请输入详细问题描述，以便我们提供更好的服务");
        addLabel(list, "问题截图");
        android.widget.ImageView addImg = new android.widget.ImageView(this);
        addImg.setId(R.id.lc_m3_add_image);
        addImg.setImageResource(R.mipmap.lc_add_image);
        list.addView(addImg, new LinearLayout.LayoutParams(120, 120));
        // 原版静态提示（逐字，非校验提示）
        TextView tip = new TextView(this);
        tip.setId(R.id.lc_m3_tip);
        tip.setText("确认提交的信息准确无误，如果提交错误信息可能导致问题无法有效解决");
        tip.setTextSize(13f);
        tip.setTextColor(Color.rgb(200, 190, 170));
        tip.setPadding(0, 24, 0, 16);
        list.addView(tip);
        // 提交：本地必填校验；通过了也不发请求（D-022）
        TextView submit = new TextView(this);
        submit.setId(R.id.lc_m3_submit);
        submit.setText("提交");
        submit.setTextSize(17f);
        submit.setTextColor(Color.WHITE);
        submit.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(50, 90, 182));
        bg.setCornerRadius(12f);
        submit.setBackground(bg);
        submit.setPadding(0, 24, 0, 24);
        submit.setOnClickListener(v -> {
            if (reason.getText().toString().trim().isEmpty()
                    || contact.getText().toString().trim().isEmpty()
                    || desc.getText().toString().trim().isEmpty()) {
                tip.setText("请先填写申请原因、联系方式与问题描述");
                android.util.Log.d("LCM3", "complaint validate fail");
                return;
            }
            tip.setText("提交未接入（D-022）：原版会向服务端提交，本项目不发送请求");
            android.util.Log.d("LCM3", "complaint submit blocked by D-022");
        });
        list.addView(submit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** 推荐给好友（M3-05）：原版是**底部分享面板** —— 选择要分享到的平台 + 微信 / 微信朋友圈 / 取消分享，2 枚原版图标。 */
    private void buildShare(LinearLayout list) {
        addLabel(list, "选择要分享到的平台");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 24, 0, 24);
        row.addView(shareItem(R.mipmap.lc_share_wx, "微信", "com.tencent.mm"));
        row.addView(shareItem(R.mipmap.lc_share_circle, "微信朋友圈", "com.tencent.mm"));
        list.addView(row);
        TextView cancel = new TextView(this);
        cancel.setId(R.id.lc_m3_share_cancel);
        cancel.setText("取消分享");
        cancel.setTextSize(15f);
        cancel.setTextColor(Color.rgb(200, 190, 170));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, 24, 0, 24);
        cancel.setOnClickListener(v -> finish());
        list.addView(cancel);
    }

    private View shareItem(int iconRes, String label, String pkg) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, 8, 48, 8);
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageResource(iconRes);
        item.addView(iv, new LinearLayout.LayoutParams(164, 164));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14f);
        tv.setTextColor(Color.rgb(235, 228, 215));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 12, 0, 0);
        item.addView(tv);
        item.setOnClickListener(v -> {
            // 真实分享动作：原作者走分享 Intent；这里调系统分享面板（目标包存在时直达）
            android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(android.content.Intent.EXTRA_TEXT, "三维山水全景地图（LC）");
            send.setPackage(pkg);
            try {
                startActivity(send);
            } catch (Throwable t) {
                startActivity(android.content.Intent.createChooser(send, "推荐给好友"));
            }
            android.util.Log.d("LCM3", "share " + label);
        });
        return item;
    }

    /**
     * 账户设置（M3-01）：原版登出态不可得（不可逆），按**未登录态**实现，
     * 字符串只允许已取证的 `您当前未登录` / `立即登录`；登录链路按 D-022 标注。
     */
    private void buildAccount(LinearLayout list) {
        TextView avatar = new TextView(this);
        avatar.setId(R.id.lc_m3_avatar);
        avatar.setText("您当前未登录");
        avatar.setTextSize(17f);
        avatar.setTextColor(Color.rgb(235, 228, 215));
        avatar.setPadding(0, 16, 0, 16);
        list.addView(avatar);
        TextView login = new TextView(this);
        login.setId(R.id.lc_m3_login);
        login.setText("立即登录");
        login.setTextSize(16f);
        login.setTextColor(Color.rgb(220, 190, 130));
        login.setPadding(0, 8, 0, 24);
        login.setOnClickListener(v -> {
            TextView note = new TextView(this);
            note.setText("登录需服务端账号体系，当前未接入（D-022）");
            note.setTextSize(13f);
            note.setTextColor(Color.rgb(200, 190, 170));
            list.addView(note);
            android.util.Log.d("LCM3", "account login blocked by D-022");
        });
        list.addView(login);
    }

    private void addLabel(LinearLayout list, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setTextColor(Color.rgb(200, 190, 170));
        tv.setPadding(0, 20, 0, 8);
        list.addView(tv);
    }

    private android.widget.EditText addInput(LinearLayout list, String hint) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.rgb(150, 130, 100));
        et.setTextColor(Color.rgb(235, 228, 215));
        et.setTextSize(15f);
        list.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private void addRow(LinearLayout list, String name, String right, boolean clickable, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 24, 0, 24);
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(17f);
        tv.setTextColor(Color.rgb(235, 228, 215));
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!right.isEmpty()) {
            TextView r = new TextView(this);
            r.setText(right);
            r.setTextSize(14f);
            r.setTextColor(Color.rgb(150, 130, 100));
            row.addView(r);
        }
        if (clickable) row.setOnClickListener(onClick);
        list.addView(row);
    }

    /**
     * 联系我们（M3-04）：原生页，三条逐字文案 `邮箱地址` / `account@xinzhi.space` / `复制邮箱`。
     * 原版 `复制邮箱` 的行为 = 把邮箱复制到剪贴板；这里做同一动作（不弹假成功提示）。
     */
    private void buildContact(LinearLayout list, List<String> lines) {
        String email = null;
        for (String s : lines) {
            if (s.contains("@")) email = s.trim();
        }
        final String mail = email;
        for (int i = 1; i < lines.size(); i++) {
            String text = lines.get(i);
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(15f);
            tv.setTextColor(Color.rgb(235, 228, 215));
            tv.setPadding(0, 12, 0, 12);
            if ("复制邮箱".equals(text) && mail != null) {
                tv.setId(R.id.lc_m3_copy_mail);
                tv.setTextColor(Color.rgb(120, 170, 240));
                tv.setOnClickListener(v -> {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("邮箱", mail));
                    android.util.Log.d("LCM3", "copy mail ok");
                });
            }
            list.addView(tv);
        }
    }

    /**
     * 关于我们（M3-03）：原生页 + 正文内联两个链接。
     * 链接 bounds 原版 `[0,0]`（未布局）——按裁定记「bounds 未确认，链接目标已确认」，
     * 不做自定义排版凑位置：链接就是正文里的那两行文字，点击打开对应 WebView。
     * `《用户协议》` 用已确认的 USER_AGREEMENT；`《隐私政策》` 的 SECRECY_POLICY 逆向只给了 `…/privacy.html`，
     * **未给全 URL**，因此点它只显示「URL 待确认」，不去猜域名。
     */
    private void buildAbout(LinearLayout list, List<String> lines) {
        for (int i = 1; i < lines.size(); i++) {
            final String text = lines.get(i);
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(15f);
            tv.setPadding(0, 12, 0, 12);
            if (text.contains("《用户协议》")) {
                tv.setTextColor(Color.rgb(120, 170, 240));
                // 逆向：AboutActivity$b$a$c → WebActivity.i.b(ctx, "yhxyb.html", "用户协议")，应用内 WebView
                tv.setOnClickListener(v -> showWeb("用户协议", AGREEMENT_URL));
            } else if (text.contains("《隐私政策》")) {
                tv.setTextColor(Color.rgb(120, 170, 240));
                // 逆向：…$d → WebActivity.i.b(ctx, "privacy.html", "隐私政策")，同一载体；URL = 同一 base + privacy.html
                tv.setOnClickListener(v -> showWeb("隐私政策", PRIVACY_URL));
            } else if (text.startsWith("APP备案编号")) {
                // 逆向：beian.miit.gov.cn 走 ACTION_VIEW → **外部浏览器**（不是应用内 WebView）
                tv.setTextColor(Color.rgb(120, 170, 240));
                tv.setOnClickListener(v -> {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(BEIAN_URL)));
                    } catch (Throwable t) {
                        android.util.Log.e("LCM3", "beian open failed", t);
                    }
                });
            } else if ("测绘资质".equals(text)) {
                // 逆向：本项可点，行为待确认（他正在 dump 该页）——先标注，不臆造跳转
                tv.setOnClickListener(v -> {
                    tv.setText("测绘资质：行为待确认");
                    tv.setTextColor(Color.rgb(200, 190, 170));
                });
            } else {
                tv.setTextColor(Color.rgb(235, 228, 215));
            }
            list.addView(tv);
        }
    }

    /** 打开一个远端 WebView（标题 + 页面），失败时页码内可见提示，不白屏。 */
    /** 打开整页 WebView 载体（原版 WebActivity 形态）。 */
    private void showWeb(String title, String url) {
        android.content.Intent wi = new android.content.Intent(this, M3WebActivity.class);
        wi.putExtra(M3WebActivity.EXTRA_TITLE, title);
        wi.putExtra(M3WebActivity.EXTRA_URL, url);
        startActivity(wi);
    }

    /** 帮助中心 / 用户协议的原版远端 URL（其余页返回 null）。 */    private static String webUrlOf(String page) {
        if ("帮助中心".equals(page)) return HELP_URL;
        if ("用户协议".equals(page)) return AGREEMENT_URL;
        return null;
    }

    /** 文案未接入时的显式说明（不臆造内容）。 */
    private static String needNote(String page) {        if ("联系我们".equals(page)) {
            return "本页原文含原版域名，按 copy 门禁挂起，待裁定后接入。";
        }
        if ("用户协议".equals(page)) {
            return "本页为远端 WebView（正文由第三方托管），离线或未接入时无正文。";
        }
        if ("帮助中心".equals(page)) {
            return "本页为远端 WebView（在线帮助），离线时无正文。";
        }
        return "本页文案待接入。";
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
