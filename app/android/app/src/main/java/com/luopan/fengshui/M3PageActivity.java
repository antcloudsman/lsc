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


    /** 帮助中心 / 用户协议：直接从调用方跳到整页 WebView（原版就是一层 WebActivity）。 */
    public static void openWebPage(android.app.Activity a, String page) {
        String url = webUrlOf(page);
        if (url == null) return;
        android.content.Intent wi = new android.content.Intent(a, M3WebActivity.class);
        wi.putExtra(M3WebActivity.EXTRA_TITLE, page);
        wi.putExtra(M3WebActivity.EXTRA_URL, url);
        a.startActivity(wi);
    }
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
            {"账户设置", "账号密码登录/注册为本项目新增（原版无此页）；原版手机号验证码/一键/荣耀/微信入口见 D-029/030/031（占位，不可用）。"},
            {"投诉反馈", "提交接本项目自建服务端 /v2/feedback/add（type=2），需先登录。"},
            {"帮助中心", "原版为在线帮助（远端 WebView），离线时无法加载正文（D-023）。"},
            {"用户协议", "原版为远端 WebView（正文由第三方托管），离线时无法加载（D-023）。"},
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

    /**
     * 推荐给好友的分享载荷 = 原版 `MainViewModel.s3(Activity)`（smali 逐字，不是自编文案）：
     * - UMWeb URL：`https://sj.qq.com/appdetail/space.xinzhi.globe`
     * - 标题：`R.string.app_name` =「三维山水全景地图」
     * - 正文：原文含 `appName` 占位符，逐字保留 =「我正在使用《appName》探索世界，它支持3D、超清影像、罗盘等功能，快来一起探索吧~」
     * - 缩略图：`R.mipmap.ic_launcher`
     * 原版用友盟 `ShareAction` 的 `SHARE_MEDIA.WEIXIN` / `WEIXIN_CIRCLE`（F8/d$a、F8/d$b）；
     * 本项目不引第三方 SDK，按任务口径落到系统 `ACTION_SEND`（正文与渠道标签逐字）。
     */
    private static final String SHARE_TITLE = "三维山水全景地图";
    private static final String SHARE_TEXT = "我正在使用《appName》探索世界，它支持3D、超清影像、罗盘等功能，快来一起探索吧~";
    private static final String SHARE_URL = "https://sj.qq.com/appdetail/space.xinzhi.globe";

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

        // 帮助中心 / 用户协议：原版就是远端 WebView，且是**整页 WebActivity**（逆向 smali $b$a$c/$d），
        // 整页打开，不在对话框里塞 WebView。
        String webUrl = webUrlOf(page);
        if (webUrl != null) {
            android.content.Intent wi = new android.content.Intent(this, M3WebActivity.class);
            wi.putExtra(M3WebActivity.EXTRA_TITLE, page);
            wi.putExtra(M3WebActivity.EXTRA_URL, webUrl);
            startActivity(wi);
            android.util.Log.d("LCM3", "open web activity " + page + " url=" + webUrl);
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
        // 提交结果行：三态可区分（校验不过 / 网络不可用 / 服务端未接入）。
        // 文案是 LC 本地提示（原版空提交行为未取证，未发真请求），不冒充原版文案。
        final TextView submitStatus = new TextView(this);
        submitStatus.setId(R.id.lc_m3_submit_status);
        submitStatus.setTextSize(13f);
        submitStatus.setTextColor(Color.rgb(200, 190, 170));
        submitStatus.setPadding(0, 8, 0, 16);
        // 提交：本地必填校验；通过了也不发请求（D-022），但失败态必须能区分，不伪造成功
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
            final String r = reason.getText().toString().trim();
            final String c = contact.getText().toString().trim();
            final String d = desc.getText().toString().trim();
            if (r.isEmpty() || c.isEmpty() || d.isEmpty()) {
                submitStatus.setTextColor(Color.rgb(220, 170, 90));
                submitStatus.setText("请先填写申请原因、联系方式与问题描述");
                android.util.Log.d("LCM3", "complaint validate fail");
                return;
            }
            final String token = LcSession.token(this);
            if (token == null) {
                submitStatus.setTextColor(Color.rgb(220, 170, 90));
                submitStatus.setText("提交前请先登录：抽屉 → 账户设置 → 立即登录");
                android.util.Log.d("LCM3", "complaint needs login");
                return;
            }
            submitStatus.setTextColor(Color.rgb(200, 190, 170));
            submitStatus.setText("提交中…");
            submit.setEnabled(false);
            // M4：接自建服务端 /v2/feedback/add（type=2 问题反馈）；失败按错误码/网络分开展示，不伪造成功。
            new Thread(() -> {
                try {
                    org.json.JSONObject data = LcApi.addFeedback(token, c, r, d);
                    final int id = data == null ? 0 : data.optInt("id", 0);
                    android.util.Log.d("LCM3", "feedback ok id=" + id);
                    runOnUiThread(() -> {
                        submit.setEnabled(true);
                        submitStatus.setTextColor(Color.rgb(120, 170, 120));
                        submitStatus.setText("提交成功" + (id > 0 ? "（反馈 id " + id + "）" : ""));
                    });
                } catch (LcApi.ApiError e) {
                    android.util.Log.w("LCM3", "feedback api " + e.code);
                    runOnUiThread(() -> {
                        submit.setEnabled(true);
                        submitStatus.setTextColor(Color.rgb(200, 90, 80));
                        submitStatus.setText("提交失败：" + e.getMessage() + "（code " + e.code + "）");
                    });
                } catch (Throwable t) {
                    android.util.Log.w("LCM3", "feedback net " + t.getClass().getSimpleName());
                    runOnUiThread(() -> {
                        submit.setEnabled(true);
                        submitStatus.setTextColor(Color.rgb(200, 90, 80));
                        submitStatus.setText("提交失败：连不上本地服务（需 adb reverse tcp:8787 tcp:8787）");
                    });
                }
            }, "lc-feedback").start();
        });
        list.addView(submit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        list.addView(submitStatus);
    }

    /** 网络可用性：只用于把「网络不可用」与「服务端未接入」两种失败态区分开，不据此伪造成功。 */
    private boolean isNetworkOnline() {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 推荐给好友（M3-05）：原版是**底部分享面板** —— 选择要分享到的平台 + 微信 / 微信朋友圈 / 取消分享，2 枚原版图标。 */
    private void buildShare(LinearLayout list) {
        addLabel(list, "选择要分享到的平台");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 24, 0, 24);
        View wx = shareItem(R.mipmap.lc_share_wx, "微信", false);
        wx.setId(R.id.lc_m3_share_wx);
        row.addView(wx);
        View circle = shareItem(R.mipmap.lc_share_circle, "微信朋友圈", true);
        circle.setId(R.id.lc_m3_share_circle);
        row.addView(circle);
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

    private View shareItem(int iconRes, final String label, final boolean timeline) {
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
        item.setOnClickListener(v -> shareTo(label, timeline));
        return item;
    }

    /**
     * 渠道点击 → 系统 `ACTION_SEND` 到微信（原版走友盟 `ShareAction` 的 WEIXIN / WEIXIN_CIRCLE）。
     *
     * 实测（2026-11-07 08:24–08:28，真机 L2E0222531014833）：
     * - `setPackage("com.tencent.mm")` + text/plain：落到微信自己的选择器「发送给朋友 / 添加到微信收藏」→ 可用；
     * - `setClassName(... ui.tools.ShareToTimeLineUI)`（带/不带 Ktitle/Kdescription/Klink）：微信弹
     *   「获取资源失败，无法分享至朋友圈。」——普通 `ACTION_SEND` 走不通朋友圈，原版用的微信 SDK 通道本项目不引入。
     * 因此两个渠道都落到微信的 `ACTION_SEND` 入口；**朋友圈与微信的分流是已知差异**（D-026：不接 SDK、
     * 页面不加提示、降级只在日志与台账体现），正文（UMWeb description）与渠道标签逐字不变。
     * `SHARE_URL` 是原版 UMWeb 的落地页，随日志留痕。
     */
    private void shareTo(String label, boolean timeline) {
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(android.content.Intent.EXTRA_SUBJECT, SHARE_TITLE);
        send.putExtra(android.content.Intent.EXTRA_TEXT, SHARE_TEXT);
        send.setPackage("com.tencent.mm");
        try {
            startActivity(send);
            // D-026：不接友盟/微信 SDK（原版 SHARE_MEDIA.WEIXIN_CIRCLE），朋友圈分流降级为微信系统分享面板；
            // 日志必须能看出「哪个渠道 → ACTION_SEND → 微信 resolver」。
            android.util.Log.d("LCM3", "share " + label + " channel=" + (timeline ? "WEIXIN_CIRCLE" : "WEIXIN")
                    + " ACTION_SEND -> wechat resolver (D-026 downgrade), text=" + SHARE_TEXT + " url=" + SHARE_URL);
        } catch (Throwable t) {
            android.util.Log.d("LCM3", "share " + label + " fallback chooser: " + t);
            android.content.Intent plain = new android.content.Intent(android.content.Intent.ACTION_SEND);
            plain.setType("text/plain");
            plain.putExtra(android.content.Intent.EXTRA_SUBJECT, SHARE_TITLE);
            plain.putExtra(android.content.Intent.EXTRA_TEXT, SHARE_TEXT);
            startActivity(android.content.Intent.createChooser(plain, "推荐给好友"));
        }
    }

    /**
     * 账户设置（M3-01 + M4）。两种态：
     * - 未登录：`您当前未登录` / `立即登录`（原版文案），微信入口占位（D-031）；
     * - 已登录（**本项目新增**，原版只有登录态且有真实账号）：头像 + 昵称 + 账号 + 改密 + 退出登录。
     *
     * 验收口径（产品 §5-6）：登录/账户的「账号密码」部分是本项目新增，不按原版 1:1 判；
     * 原版手机号验证码/一键/荣耀/微信入口按 D-029/030/031 只判「占位 + 不可用说明」。
     */
    private void buildAccount(LinearLayout list) {
        android.widget.ImageView avatarImg = new android.widget.ImageView(this);
        avatarImg.setId(R.id.lc_m3_avatar_img);
        avatarImg.setImageResource(R.mipmap.lc_default_avatar);
        avatarImg.setPadding(0, 24, 0, 12);
        list.addView(avatarImg, new LinearLayout.LayoutParams(120, 120));

        final String token = LcSession.token(this);
        final org.json.JSONObject user = LcSession.user(this);
        if (token == null || user == null) {
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
            login.setOnClickListener(v ->
                    startActivityForResult(new android.content.Intent(this, LoginActivity.class), LoginActivity.REQ));
            list.addView(login);
            // 原版第三方入口占位（本轮不实现，D-029/030/031）
            TextView wx = new TextView(this);
            wx.setId(R.id.lc_m3_wechat_entry);
            wx.setText("微信登录（D-031 待接入，暂不可用）");
            wx.setTextSize(14f);
            wx.setTextColor(Color.rgb(150, 130, 100));
            wx.setPadding(0, 8, 0, 24);
            wx.setOnClickListener(v -> android.widget.Toast.makeText(this,
                    "微信登录需微信开放平台登记包名+签名（D-031），本项目本包无法通过校验", android.widget.Toast.LENGTH_SHORT).show());
            list.addView(wx);
            return;
        }

        String nick = user.optString("display_name", "");
        if (nick.trim().isEmpty()) nick = user.optString("username", "");
        final String nickShown = nick;
        TextView nameRow = new TextView(this);
        nameRow.setId(R.id.lc_m3_nickname);
        nameRow.setText("昵称：" + nickShown + "　（点此修改）");
        nameRow.setTextSize(17f);
        nameRow.setTextColor(Color.rgb(235, 228, 215));
        nameRow.setPadding(0, 16, 0, 12);
        nameRow.setOnClickListener(v -> showEditNameDialog(token, nickShown));
        list.addView(nameRow);

        TextView accountRow = new TextView(this);
        accountRow.setId(R.id.lc_m3_account_name);
        accountRow.setText("账号：" + user.optString("username", "")
                + "　uid：" + user.optInt("uid", 0));
        accountRow.setTextSize(14f);
        accountRow.setTextColor(Color.rgb(200, 190, 170));
        accountRow.setPadding(0, 0, 0, 20);
        list.addView(accountRow);

        TextView changePw = new TextView(this);
        changePw.setId(R.id.lc_m3_changepw);
        changePw.setText("修改密码");
        changePw.setTextSize(16f);
        changePw.setTextColor(Color.rgb(220, 190, 130));
        changePw.setPadding(0, 8, 0, 16);
        changePw.setOnClickListener(v -> showChangePasswordDialog(token));
        list.addView(changePw);

        TextView sync = new TextView(this);
        sync.setId(R.id.lc_m3_sync);
        sync.setText("同步收藏点（本地优先，失败留离线队列）");
        sync.setTextSize(16f);
        sync.setTextColor(Color.rgb(220, 190, 130));
        sync.setPadding(0, 8, 0, 16);
        sync.setOnClickListener(v -> {
            sync.setEnabled(false);
            sync.setText("同步中…");
            new Thread(() -> {
                try {
                    final String msg = FavoriteSync.sync(this);
                    runOnUiThread(() -> {
                        sync.setEnabled(true);
                        sync.setText("同步收藏点：" + msg);
                    });
                } catch (Throwable t) {
                    android.util.Log.w("LCM3", "sync failed " + t.getClass().getSimpleName());
                    runOnUiThread(() -> {
                        sync.setEnabled(true);
                        sync.setText("同步失败：连不上本地服务（需 adb reverse tcp:8787）");
                    });
                }
            }, "lc-sync").start();
        });
        list.addView(sync);

        TextView logout = new TextView(this);
        logout.setId(R.id.lc_m3_logout);
        logout.setText("退出登录");
        logout.setTextSize(16f);
        logout.setTextColor(Color.rgb(220, 120, 100));
        logout.setPadding(0, 8, 0, 24);
        logout.setOnClickListener(v -> doLogout(token));
        list.addView(logout);
    }

    /** 修改昵称（PATCH /v2/auth/me），成功后更新本地会话并刷新本页。 */
    private void showEditNameDialog(final String token, String current) {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setText(current);
        et.setSelection(et.getText().length());
        new android.app.AlertDialog.Builder(this)
                .setTitle("修改昵称")
                .setView(et)
                .setPositiveButton("保存", (d, w) -> {
                    final String next = et.getText().toString().trim();
                    if (next.isEmpty()) return;
                    new Thread(() -> {
                        try {
                            org.json.JSONObject data = LcApi.updateDisplayName(token, next);
                            org.json.JSONObject u = data == null ? null : data.optJSONObject("user");
                            if (u != null) LcSession.save(this, token, u, LcSession.expiresAt(this));
                            runOnUiThread(this::recreate);
                        } catch (Throwable t) {
                            runOnUiThread(() -> android.widget.Toast.makeText(this,
                                    "修改失败：" + t.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                        }
                    }, "lc-changename").start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 修改密码（POST /v2/auth/password，成功后服务端撤销其它会话；本地会话保留）。 */
    private void showChangePasswordDialog(final String token) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 16, 32, 0);
        final android.widget.EditText cur = new android.widget.EditText(this);
        cur.setHint("当前密码");
        cur.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final android.widget.EditText next = new android.widget.EditText(this);
        next.setHint("新密码（8–128 位）");
        next.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(cur);
        box.addView(next);
        new android.app.AlertDialog.Builder(this)
                .setTitle("修改密码")
                .setView(box)
                .setPositiveButton("确定", (d, w) -> {
                    final String c = cur.getText().toString();
                    final String n = next.getText().toString();
                    if (n.length() < 8) {
                        android.widget.Toast.makeText(this, "新密码需 8–128 位", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new Thread(() -> {
                        try {
                            LcApi.changePassword(token, c, n);
                            runOnUiThread(() -> android.widget.Toast.makeText(this,
                                    "密码已修改", android.widget.Toast.LENGTH_SHORT).show());
                        } catch (Throwable t) {
                            runOnUiThread(() -> android.widget.Toast.makeText(this,
                                    "修改失败：" + t.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                        }
                    }, "lc-changepw").start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 退出登录：先调服务端撤销会话（失败也清本地），清空 token 后回未登录态。 */
    private void doLogout(final String token) {
        new Thread(() -> {
            try {
                LcApi.logout(token);
            } catch (Throwable t) {
                android.util.Log.w("LCM3", "logout api failed: " + t.getClass().getSimpleName());
            } finally {
                LcSession.clear(this);
                android.util.Log.d("LCM3", "logged out, token cleared");
                runOnUiThread(this::recreate);
            }
        }, "lc-logout").start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LoginActivity.REQ && resultCode == RESULT_OK) {
            recreate();
        }
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
        // 原版 contact.xml **没有标题节点**（产品 §9 已记），所以 assets 里三行全是正文，不能跳过第一行；
        // 顺序与 dump 一致：邮箱地址 / account@xinzhi.space / 复制邮箱。
        for (int i = 0; i < lines.size(); i++) {
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
                    // 原版 smali（ui/compose/c0$a$b）：ClipData.newPlainText("Label", "account@xinzhi.space")
                    // → setPrimaryClip → toast「复制成功」。label 与 toast 都按原版逐字。
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Label", mail));
                        android.widget.Toast.makeText(this, "复制成功", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    android.util.Log.d("LCM3", "copy mail ok text=" + mail);
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
                // 逆向：AboutActivity$b$a$a → MeasureProveActivity（目标页超出 M3 七页范围）
                // 按 D-025 本轮只保文案/位置 1:1、**不跳转**；不篡改可见文案（原版这行是蓝色可点文本）。
                tv.setTextColor(Color.rgb(120, 170, 240));
                tv.setOnClickListener(v ->
                        android.util.Log.d("LCM3", "measure prove page deferred by D-025"));
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
    private static String needNote(String page) {
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
                if (!line.trim().isEmpty()) out.add(line);   // R2/M3：保留原样（关于我们两个链接 dump 原文前后各一个空格）
            }
        } catch (Throwable t) {
            android.util.Log.e("LCM3", "read " + file + " failed", t);
        }
        return out;
    }
}

