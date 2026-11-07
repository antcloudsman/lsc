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

    // M6 第 4 条：四页转浅色主题。取值为原版截图像素统计（docs/fidelity/m6/client-four-pages-diff.md §0.3）：
    // 页面底 #F0F3F7 占 50–88%、白卡 #FFFFFF、主字色 #23272B、次字 (142,142,142)、链接蓝 (44,102,214)。
    private static final int LIGHT_BG = 0xFFF0F3F7;
    private static final int CARD_WHITE = 0xFFFFFFFF;
    private static final int TEXT_MAIN = 0xFF23272B;
    private static final int TEXT_SUB = 0xFF8D9299;   // 审客户端实测 (141,146,153)
    private static final int TEXT_HINT = 0xFF9C9C9C;
    private static final int ACCENT = 0xFF1C45A5;     // 审客户端实测 (28,69,165)
    private static final int LINK = 0xFF1C45A5;
    private static final int DANGER = 0xFFE93B4D;
    private static final int DIVIDER = 0xFFE6E8EB;

    /** M6 第 4 条：账户页「退出登录」原版贴底（父节点 [39,2512][1189,2578]），
     *  必须挂在 ScrollView 之外，否则会随内容滚走。 */
    private View bottomFixedButton;

    /** px -> dp。原版几何都是设备像素（720dpi 屏、density 3.375），实现必须按 dp 写。 */
    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }


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
        // M6 第 4 条：原版四页窗口**画到屏底 2700**、且没有导航栏（四页 PNG 底部 2630..2699 单色 #F0F3F7、
        // 零图标；状态栏仍在，深色图标 y≈44..75）。我方窗口只到 2578，红条被切在 2578 —— 这不是高度差，
        // 是两种窗口。只改本 Activity：主界面（罗盘 922 / r / 地图已验收）一律不动。
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        root.setId(R.id.lc_m3_root);
        root.setOrientation(LinearLayout.VERTICAL);
        // 全屏布局后内容从 y=0 起，标题会顶到状态栏里：把状态栏高度补成顶部内距
        // （原版标题 y 154 = 状态栏 122 + 32）。
        int statusBar = 0;
        int sbId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (sbId > 0) statusBar = getResources().getDimensionPixelSize(sbId);
        // 原版 bar 顶在 y=0（返回钮 y115），不能在 root 上补状态栏内距，否则整体下移 24+
        root.setBackgroundColor(LIGHT_BG);

        // 标题栏：返回 + 页名（原版标题逐字）
        // M6 第 4 条：标题栏改 FrameLayout —— 返回贴左、标题**居中且 wrap_content**（原版宽 216）、我的贴右。
        // 用 LinearLayout 时标题靠 weight 撑满，dump 宽度永远是整栏，Δ 表必挂（测试实测 ΔR=+465）。
        android.widget.FrameLayout bar = new android.widget.FrameLayout(this);
        // 标题栏高度固定（原版正文从 y=265 起、状态栏 122 -> 143），
        // 否则「我的」158 高会把 bar 撑高、把标题一起推下去（实测标题 +31 就是这条）。

        bar.setPadding(dp(12), 115, dp(12), dp(5));   // P=115：与「返回 y115 / 我的 y115」同顶（审核验按导数反推）
        bar.setClipChildren(false);   // 「我的」底部到 272，超过定高 265，不关裁剪只有 150 高
        TextView back = new TextView(this);
        back.setId(R.id.lc_m3_back);
        back.setText("返回");
        back.setTextSize(15f);
        back.setTextColor(TEXT_MAIN);
        back.setPadding(0, dp(4), dp(10), dp(4));
        back.setOnClickListener(v -> finish());
        android.widget.FrameLayout.LayoutParams backLp =
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        backLp.gravity = Gravity.START | Gravity.TOP;   // 居中无解：返回 y115 与 我的 y115 需同一 P
        bar.addView(back, backLp);
        TextView title = new TextView(this);
        title.setId(R.id.lc_m3_title);
        title.setText(page);
        title.setTextSize(16f);   // 原版标题 216 宽 = 四字各 54px ≈ 16sp（18sp 会画成 240）
        title.setTextColor(TEXT_MAIN);
        android.widget.FrameLayout.LayoutParams titleLp =
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        titleLp.topMargin = 39;   // 原版 154 - bar 顶 115
        titleLp.height = 79;   // 原版标题高 79（四页一致），wrap 只有 76
        title.setIncludeFontPadding(false);
        bar.addView(title, titleLp);

        // M6 第 4 条：投诉反馈一级页右上「我的」[1056,115][1213,272] = **158×158**、
        // complaint.xml 里 `clickable=true` + `focusable=true`（dump 只支持“可点”，不支持“不可点”）。
        // 跳转目标未取证 → 按裁定做成**可点 + 明确的未接入提示**（与 D-031 微信入口同口径），
        // 不许假可点、也不许静默无响应。
        if ("投诉反馈".equals(page)) {
            TextView mineBtn = new TextView(this);
            mineBtn.setId(R.id.lc_m3_mine);
            mineBtn.setText("我的");
            mineBtn.setTextSize(17f);
            mineBtn.setTextColor(TEXT_MAIN);
            mineBtn.setGravity(Gravity.CENTER);
            mineBtn.setClickable(true);
            mineBtn.setOnClickListener(v -> android.widget.Toast.makeText(this,
                    "「我的」跳转目标未取证（D-034），本轮未接入",
                    android.widget.Toast.LENGTH_SHORT).show());
            android.widget.FrameLayout.LayoutParams mineLp =
                    new android.widget.FrameLayout.LayoutParams(157, 157);
            mineLp.gravity = Gravity.END | Gravity.TOP;
            mineLp.topMargin = 0;   // 原版 y115 = bar 顶
            mineLp.rightMargin = 15;
            // X 补偿：rightMargin 落在 bar 右内距 dp(12)=41 里，右缘会退到 1172；
            // 41 是有效补偿（审核验第七轮确认），Y 那个 -31 才是残留，已删。
            mineBtn.setTranslationX(41f);
            bar.addView(mineBtn, mineLp);
        }
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 265));

        // 依赖标注（有则显示）
        // M6 第 4 条：原版「投诉反馈」没有这行自造说明，它会把握手位整体推下 141px（审审核验实测），
        // 按 1:1 去掉；依赖说明只留在日志里，不占版面。
        String dep = ("投诉反馈".equals(page) || "账户设置".equals(page)) ? null : dependencyNote(page);
        if (dep != null) {
            TextView note = new TextView(this);
            note.setId(R.id.lc_m3_dep_note);
            note.setText(dep);
            note.setTextSize(12f);
            note.setTextColor(ACCENT);
            note.setPadding(dp(12), dp(1), dp(12), dp(5));
            root.addView(note);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.lc_m3_scroll);
        LinearLayout list = new LinearLayout(this);
        list.setId(R.id.lc_m3_body);
        list.setOrientation(LinearLayout.VERTICAL);
        // 顶距 +33 只对投诉页（首行 320 -> 353）；账户页 body 起点不同，不能一起推
        // 顶距：投诉 33（320->353）、账户 74（339-265）；两者 body 起点不同
        list.setPadding(dp(12),
                "账户设置".equals(page) ? 74 : ("投诉反馈".equals(page) ? 33 : 0),
                dp(12), dp(10));
        List<String> lines = readPage(page);
        // 三页有专属结构（不是纯文本列表）：投诉反馈两级、推荐给好友分享面板、账户设置未登录态
        if ("投诉反馈".equals(page)) {
            buildComplaint(list);
        } else if ("推荐给好友".equals(page)) {
            buildShare(list);
        } else if ("账户设置".equals(page)) {
            buildAccount(list);
        } else if ("关于我们".equals(page)) {
            // M6 第 4 条：about 是**绝对分布**（154/995/1090/2331/2489/2496），
            // ScrollView 流式布局必然整体偏高（测试实测 -700..-1950px）。
            // 这里改成屏幕坐标系绝对定位，纵坐标一律取 dump 原值。
            buildAboutAbsolute(root, lines);
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
                tv.setTextColor(TEXT_MAIN);
                tv.setPadding(0, dp(4), 0, dp(4));
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
            empty.setTextColor(TEXT_SUB);
            empty.setPadding(0, dp(5), 0, dp(5));
            list.addView(empty);
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // M6 第 4 条：账户页贴底红按钮（1150x66 由列表内距折出，这里固定在最底部，不随滚动）
        if (bottomFixedButton != null) {
            android.widget.FrameLayout bar2 = new android.widget.FrameLayout(this);
            android.graphics.drawable.GradientDrawable lbg = new android.graphics.drawable.GradientDrawable();
            lbg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            lbg.setColor(0xFFE93B4D);
            lbg.setCornerRadius(12f);
            bar2.setBackground(lbg);
            bar2.setClickable(true);
            android.widget.FrameLayout.LayoutParams txtLp =
                    new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
            txtLp.gravity = Gravity.CENTER;
            bar2.addView(bottomFixedButton, txtLp);
            LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 124);
            blp2.leftMargin = 39;
            blp2.rightMargin = 39;
            blp2.bottomMargin = 64;
            root.addView(bar2, blp2);
        }

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
        // 投诉一级页步进 = 180px（原版 问题反馈 353 → 订单投诉 533）；账户页是 163，两者不同，
        // 所以不能用一个全局行高，按页覆盖。
        LinearLayout r1 = addRow(list, "问题反馈", "", true, v -> {
            list.removeAllViews();
            buildComplaintForm(list);
        });
        // pitch = minHeight + 分隔高度(3)：目标步进 180 -> minHeight 177
        if (r1 != null) r1.setMinimumHeight(177);
        LinearLayout r2 = addRow(list, "订单投诉", "未选择任何订单", false, null);
        if (r2 != null) r2.setMinimumHeight(177);
    }

    private void buildComplaintForm(LinearLayout list) {
        addLabel(list, "申请原因");
        final android.widget.EditText reason = addInput(list, "", R.id.lc_m3_reason);
        addLabel(list, "联系方式");
        final android.widget.EditText contact = addInput(list, "请输入您的联系方式", R.id.lc_m3_contact);
        addLabel(list, "问题描述");
        final android.widget.EditText desc = addInput(list,
                "请输入详细问题描述，以便我们提供更好的服务", R.id.lc_m3_desc);
        // 键盘弹起时把当前焦点字段滚进可视区（原版表单同样是滚动容器 + resize）。
        // M4 E2E：不加这段时第三个框会被键盘挡住，脚本按坐标点会落回第一个框。
        // 注意 list 的直接父级才是 ScrollView（root → scroll → list），不能取 getParent().getParent()。
        android.view.ViewParent p = list.getParent();
        final ScrollView formScroll = (p instanceof ScrollView) ? (ScrollView) p : null;
        android.view.View.OnFocusChangeListener scrollToFocus = (v, hasFocus) -> {
            if (hasFocus && formScroll != null) {
                formScroll.postDelayed(() -> formScroll.smoothScrollTo(0, v.getTop()), 250);
            }
        };
        reason.setOnFocusChangeListener(scrollToFocus);
        contact.setOnFocusChangeListener(scrollToFocus);
        desc.setOnFocusChangeListener(scrollToFocus);
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
        tip.setTextColor(TEXT_SUB);
        tip.setPadding(0, dp(7), 0, dp(5));
        list.addView(tip);
        // 提交结果行：三态可区分（校验不过 / 网络不可用 / 服务端未接入）。
        // 文案是 LC 本地提示（原版空提交行为未取证，未发真请求），不冒充原版文案。
        final TextView submitStatus = new TextView(this);
        submitStatus.setId(R.id.lc_m3_submit_status);
        submitStatus.setTextSize(13f);
        submitStatus.setTextColor(TEXT_SUB);
        submitStatus.setPadding(0, dp(2), 0, dp(5));
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
        submit.setPadding(0, dp(7), 0, dp(7));
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
            submitStatus.setTextColor(TEXT_SUB);
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
        row.setPadding(0, dp(12), 0, dp(12));
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
        cancel.setTextColor(TEXT_SUB);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(7), 0, dp(7));
        cancel.setOnClickListener(v -> finish());
        list.addView(cancel);
    }

    private View shareItem(int iconRes, final String label, final boolean timeline) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(2), dp(14), dp(2));
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageResource(iconRes);
        item.addView(iv, new LinearLayout.LayoutParams(164, 164));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14f);
        tv.setTextColor(TEXT_MAIN);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(4), 0, 0);
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
        avatarImg.setPadding(0, dp(7), 0, dp(4));
        // 头像延后：原版顺序是「昵称 339 -> 头像 502」，先记录引用，等昵称行之后插入
        final android.widget.ImageView avatarRowImg = avatarImg;

        final String token = LcSession.token(this);
        final org.json.JSONObject user = LcSession.user(this);
        if (token == null || user == null) {
            TextView avatar = new TextView(this);
            avatar.setId(R.id.lc_m3_avatar);
            avatar.setText("您当前未登录");
            avatar.setTextSize(17f);
            avatar.setTextColor(TEXT_MAIN);
            avatar.setPadding(0, dp(5), 0, dp(5));
            list.addView(avatar);
            TextView login = new TextView(this);
            login.setId(R.id.lc_m3_login);
            login.setText("立即登录");
            login.setTextSize(16f);
            login.setTextColor(ACCENT);
            login.setPadding(0, dp(2), 0, dp(7));
            login.setOnClickListener(v ->
                    startActivityForResult(new android.content.Intent(this, LoginActivity.class), LoginActivity.REQ));
            list.addView(login);
            // 原版四个登录入口占位（D-029/030/031）：文案取自 DEX 字符串池逐字
            // （docs/fidelity/m4/login-strings-classes06.txt），点击只给不可用说明，不假装可用。
            TextView boundPhone = new TextView(this);
            boundPhone.setId(R.id.lc_m3_bind_phone);
            boundPhone.setText("绑定手机");
            boundPhone.setTextSize(14f);
            boundPhone.setTextColor(TEXT_HINT);
            boundPhone.setPadding(0, dp(2), 0, dp(2));
            boundPhone.setOnClickListener(v -> android.widget.Toast.makeText(this,
                    "绑定手机号需短信验证码服务（D-029），本轮不实现", android.widget.Toast.LENGTH_SHORT).show());
            list.addView(boundPhone);

            TextView oneTap = new TextView(this);
            oneTap.setId(R.id.lc_m3_onetap);
            oneTap.setText("一键登录");
            oneTap.setTextSize(14f);
            oneTap.setTextColor(TEXT_HINT);
            oneTap.setPadding(0, dp(2), 0, dp(2));
            oneTap.setOnClickListener(v -> android.widget.Toast.makeText(this,
                    "一键登录需运营商号码认证（D-030），本轮不实现", android.widget.Toast.LENGTH_SHORT).show());
            list.addView(oneTap);

            TextView wx = new TextView(this);
            wx.setId(R.id.lc_m3_wechat_entry);
            wx.setText("微信登录");
            wx.setTextSize(14f);
            wx.setTextColor(TEXT_HINT);
            wx.setPadding(0, dp(2), 0, dp(7));
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
        nameRow.setTextColor(TEXT_MAIN);
        nameRow.setPadding(0, dp(5), 0, dp(4));
        nameRow.setOnClickListener(v -> showEditNameDialog(token, nickShown));
        addAccountRow(list, nameRow);
        // 头像：160 行内放 120x120，垂直居中
        LinearLayout avatarRow = new LinearLayout(this);
        avatarRow.setOrientation(LinearLayout.VERTICAL);
        avatarRow.setBackgroundColor(CARD_WHITE);
        avatarRow.setMinimumHeight(160);
        avatarRow.setGravity(Gravity.CENTER_VERTICAL);
        avatarRow.addView(avatarRowImg, new LinearLayout.LayoutParams(120, 120));
        list.addView(avatarRow);
        {
            View dv = new View(this);
            dv.setBackgroundColor(0xFFF0F3F7);
            LinearLayout.LayoutParams dlp0 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 3);
            dlp0.leftMargin = 39; dlp0.rightMargin = 39;
            list.addView(dv, dlp0);
        }

        TextView accountRow = new TextView(this);
        accountRow.setId(R.id.lc_m3_account_name);
        accountRow.setText("账号：" + user.optString("username", "")
                + "　uid：" + user.optInt("uid", 0));
        accountRow.setTextSize(14f);
        accountRow.setTextColor(TEXT_SUB);
        accountRow.setPadding(0, 0, 0, dp(6));
        addAccountRow(list, accountRow);

        TextView changePw = new TextView(this);
        changePw.setId(R.id.lc_m3_changepw);
        changePw.setText("修改密码");
        changePw.setTextSize(16f);
        changePw.setTextColor(ACCENT);
        changePw.setPadding(0, dp(2), 0, dp(5));
        changePw.setOnClickListener(v -> showChangePasswordDialog(token));
        addAccountRow(list, changePw);

        TextView sync = new TextView(this);
        sync.setId(R.id.lc_m3_sync);
        sync.setText("同步收藏点（本地优先，失败留离线队列）");
        sync.setTextSize(16f);
        sync.setTextColor(ACCENT);
        sync.setPadding(0, dp(2), 0, dp(5));
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
        addAccountRow(list, sync);

        // M6 第 4 条：退出登录 = **红底按钮**，不是红字。
        // 几何取 dump 父节点 [39,2512][1189,2578] = 1150x66（文本节点 183x37 只是字）；
        // 颜色只能靠像素：#E93B4D。列表左右内距 dp(12)≈40px，MATCH_PARENT 恰好 1148 ≈ 1150。
        TextView logout = new TextView(this);
        logout.setId(R.id.lc_m3_logout);
        logout.setText("退出登录");
        logout.setTextSize(13.5f);   // 原版 183 宽 = 4×45.75px ≈ 13.5sp
        logout.setTextColor(android.graphics.Color.WHITE);
        logout.setGravity(Gravity.CENTER);
        // 文字节点必须只有字那么宽（原版 183），红底宽度在**容器**上：
        // 之前把背景直接设在文字节点上，dump 里文字宽度=整条 1150（测试实测 ΔR=+483）。
        logout.setBackground(null);
        android.graphics.drawable.GradientDrawable logoutBg = new android.graphics.drawable.GradientDrawable();
        logoutBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        logoutBg.setColor(0xFFE93B4D);
        logoutBg.setCornerRadius(12f);   // 像素实测圆角 12px（首/末行内缩 12）
        logout.setBackground(logoutBg);
        logout.setPadding(0, 0, 0, 0);   // 去掉不对称内距：上2/下7 会把字心推高约 2.5px（审核验实测字心 2565 vs 2572）
        logout.setOnClickListener(v -> doLogout(token));
        // 贴底：挂到 root，不放进 ScrollView（原版 y 2512..2578 固定底部）
        bottomFixedButton = logout;
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
        tv.setTextColor(TEXT_SUB);
        tv.setPadding(0, dp(6), 0, dp(2));
        list.addView(tv);
    }

    private android.widget.EditText addInput(LinearLayout list, String hint, int id) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setId(id);
        et.setHint(hint);
        et.setHintTextColor(TEXT_HINT);
        et.setTextColor(TEXT_MAIN);
        et.setTextSize(15f);
        et.setSingleLine(false);
        list.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return et;
    }


    /** M6 第 4 条：账户页行容器 —— 与 addRow 同一套几何：minHeight 160 + 3px 分隔 = 163 步进。
     *  原版账户页 6 行步进 163（339/502/665/828/991/1154）；我方自有行落进同一几何，不判文案。 */
    private void addAccountRow(LinearLayout list, View content) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(CARD_WHITE);
        row.setMinimumHeight(160);
        row.setGravity(Gravity.TOP);   // 原版行节点高 79、内容顶端对齐；居中会把文字推下 42
        row.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 79));
        list.addView(row);
        View divider = new View(this);
        divider.setBackgroundColor(0xFFF0F3F7);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 3);
        dlp.leftMargin = 39;
        dlp.rightMargin = 39;
        list.addView(divider, dlp);
    }

    private LinearLayout addRow(LinearLayout list, String name, String right, boolean clickable, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 24, 0, 24);
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(15f);   // 原版「问题反馈」204 宽 = 4×51px ≈ 15sp（17sp 会画成 228）
        tv.setTextColor(TEXT_MAIN);
        row.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (!right.isEmpty()) {
            // 中间垫一个 weight=1 的空白，把右值推到行尾（左标签不再撑满整行）
            View gapView = new View(this);
            row.addView(gapView, new LinearLayout.LayoutParams(0, 1, 1f));
            TextView r = new TextView(this);
            r.setText(right);
            r.setTextSize(14f);
            r.setTextColor(TEXT_HINT);
            row.addView(r);
        }
        if (clickable) row.setOnClickListener(onClick);
        // M6 第 4 条：原版行是白卡上的条目（底 #F0F3F7 + 白卡 + 1px 分隔），行高 48dp。
        row.setBackgroundColor(CARD_WHITE);
        // pitch = minHeight + 分隔高度(3)：账户页目标步进 163 -> minHeight 160
        // （此前设 163 会让 pitch=166，第 6 行落到 1169，比原版 1154 多 15px）
        row.setMinimumHeight(160);
        list.addView(row);
        // 原版分隔 = 3px、色 #F0F3F7（与页面底同色）、左右内缩 39px（审客户端 quantify）
        View divider = new View(this);
        divider.setBackgroundColor(0xFFF0F3F7);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 3);
        dlp.leftMargin = 39;
        dlp.rightMargin = 39;
        list.addView(divider, dlp);
        return row;
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
            tv.setTextColor(TEXT_MAIN);
            // M6 第 4 条：原版这两页正文**居中**（dump 三维山水全景地图 [399,995][829,1074]、
            // 邮箱地址 [506,1199][722,1278] 的中心都在 614 = 屏半宽），不是左对齐。
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, 12, 0, 12);
            if ("复制邮箱".equals(text) && mail != null) {
                tv.setId(R.id.lc_m3_copy_mail);
                tv.setTextColor(LINK);
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
     * M6 第 4 条：关于我们 —— **按 dump 的绝对纵坐标**摆放（屏幕坐标系）。
     * dump：三维山水全景地图 y995、重庆新知空间科技有限公司 y1090、
     * 测绘资质 y2331、v1.6.1 y2489、APP备案编号 y2496。
     * 根容器已按 statusBar 加了顶部内距，所以这里减掉该内距，落点才等于 dump 的 y。
     * 《用户协议》《隐私政策》在原版 dump 里是 [0,0][0,0]（排版在窗口帧外），
     * 照原版**不参与可见布局**；真实坐标待 about-links.png 取证后再落，不臆造。
     */
    private void buildAboutAbsolute(LinearLayout root, List<String> lines) {
        int statusBar = 0;
        int sbId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (sbId > 0) statusBar = getResources().getDimensionPixelSize(sbId);

        android.widget.FrameLayout stage = new android.widget.FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        int[] ys = {995, 1090, 2331, 2489, 2496};
        for (int k = 0; k < ys.length; k++) {
            String text = null;
            for (String line : lines) {
                if (line.trim().isEmpty() || line.trim().equals("关于我们")) continue;
                if (line.contains("《")) continue;         // 两链接原版未参与布局
                if (k == 0 && line.contains("三维山水全景地图")) text = line;
                if (k == 1 && line.contains("重庆新知空间")) text = line;
                if (k == 2 && line.contains("测绘资质")) text = line;
                if (k == 3 && line.contains("v1.6.1")) text = line;
                if (k == 4 && line.startsWith("APP备案编号")) text = line;
            }
            if (text == null) continue;
            TextView tv = new TextView(this);
            tv.setText(text.trim());
            // M6 第 4 条：字号按**字形 ink** 反算（不是节点框）：
            // 三维山水 422/8/3.375≈15.6 → 16sp；重庆新知 516/12/3.375≈12.7 → 13sp；
            // 测绘资质 209/4/3.375≈15.5sp；v1.6.1 ink 高 25 ≈ 10.5sp；备案编号 ink 621×31 单行。
            // 备案编号按总监估算 10.5sp（8 中文 + 18 拉丁：8×35.4 + 18×17.7 ≈ 601，落在 621±20）；v1.6.1 同档，
            // 这是**估算值**，装好后按 dump 实测回填，不拿估算当结论。
            // 备案编号按实测回填：10.5sp 实测宽 638，目标 621 -> 10.5×621/638 ≈ 10.2sp
            float[] sps = {16f, 13f, 15.5f, 10.5f, 10.3f};
            tv.setTextSize(sps[Math.min(k, sps.length - 1)]);
            tv.setSingleLine(true);   // 五项在原版都是单行（备案编号此前被折成两行，宽 330）

            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(TEXT_MAIN);
            if (k == 2) {
                // 测绘资质在原版是蓝色可点（点去 MeasureProve，D-025 本轮不跳转）
                tv.setTextColor(LINK);
                tv.setOnClickListener(v ->
                        android.util.Log.d("LCM3", "measure prove page deferred by D-025"));
            } else if (k == 4) {
                tv.setTextColor(LINK);
                tv.setOnClickListener(v -> {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(BEIAN_URL)));
                    } catch (Throwable t) {
                        android.util.Log.e("LCM3", "beian open failed", t);
                    }
                });
            }
            // 框高按 dump 的有效值（y2 不是 2578 的三个才有意义）：三维/重庆 79、测绘资质 157；
            // 文字在框内垂直居中（原版 ink 只有 49 高，框 157 并不是字高）。
            int[] frameH = {79, 79, 157, 0, 0};
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    frameH[k] > 0 ? frameH[k] : android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            tv.setTag(ys[k]);
            stage.addView(tv, lp);
        }
        stage.post(() -> {
            int off = stage.getTop();
            if (off <= 0) return;
            for (int idx = 0; idx < stage.getChildCount(); idx++) {
                View ch = stage.getChildAt(idx);
                Object tag = ch.getTag();
                if (!(tag instanceof Integer)) continue;
                android.widget.FrameLayout.LayoutParams lp2 =
                        (android.widget.FrameLayout.LayoutParams) ch.getLayoutParams();
                lp2.topMargin = Math.max(0, ((Integer) tag) - off);
                ch.setLayoutParams(lp2);
            }
        });
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
            tv.setGravity(Gravity.CENTER);   // M6 第 4 条：原版关于我们正文居中
            tv.setPadding(0, 12, 0, 12);
            if (text.contains("《用户协议》")) {
                tv.setTextColor(LINK);
                // 逆向：AboutActivity$b$a$c → WebActivity.i.b(ctx, "yhxyb.html", "用户协议")，应用内 WebView
                tv.setOnClickListener(v -> showWeb("用户协议", AGREEMENT_URL));
            } else if (text.contains("《隐私政策》")) {
                tv.setTextColor(LINK);
                // 逆向：…$d → WebActivity.i.b(ctx, "privacy.html", "隐私政策")，同一载体；URL = 同一 base + privacy.html
                tv.setOnClickListener(v -> showWeb("隐私政策", PRIVACY_URL));
            } else if (text.startsWith("APP备案编号")) {
                // 逆向：beian.miit.gov.cn 走 ACTION_VIEW → **外部浏览器**（不是应用内 WebView）
                tv.setTextColor(LINK);
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
                tv.setTextColor(LINK);
                tv.setOnClickListener(v ->
                        android.util.Log.d("LCM3", "measure prove page deferred by D-025"));
            } else {
                tv.setTextColor(TEXT_MAIN);
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

