package com.luopan.fengshui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * M4 账号登录/注册页 —— **本项目新增**，原版没有账号密码登录（原版是手机号验证码 / 一键 / 荣耀 / 微信，
 * 见 D-029/030/031，本轮只做入口占位）。
 *
 * 验收口径（产品 §5-6）：只判「新增功能可用 + 不与原版入口混淆 + 页面标明新增」，**不按原版 1:1 判**。
 *
 * 控件：账号 / 密码（注册时多一个昵称）/ 登录·注册切换 / 提交 / 微信占位（不可用）。
 * token 由 {@link LcSession} 用 Keystore AES/GCM 加密存储；登录成功回传 RESULT_OK。
 */
public class LoginActivity extends Activity {

    public static final int REQ = 9101;

    private boolean registerMode;
    private TextView tabLogin, tabRegister, submit, status, wx;
    private EditText username, password, displayName;
    private LinearLayout displayRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setId(R.id.lc_login_root);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(26, 22, 18));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(20, 24, 20, 16);
        TextView back = new TextView(this);
        back.setId(R.id.lc_login_back);
        back.setText("返回");
        back.setTextSize(15f);
        back.setTextColor(Color.rgb(235, 228, 215));
        back.setPadding(0, 12, 32, 12);
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = new TextView(this);
        title.setId(R.id.lc_login_title);
        title.setText("账号登录（本项目新增）");
        title.setTextSize(18f);
        title.setTextColor(Color.rgb(235, 228, 215));
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(24, 8, 24, 32);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabLogin = tab("登录", true);
        tabRegister = tab("注册", false);
        tabs.addView(tabLogin, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tabs.addView(tabRegister, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(tabs);

        body.addView(label("账号（3–32 位，字母/数字/._-）"));
        username = input("请输入账号", InputType.TYPE_CLASS_TEXT);
        username.setId(R.id.lc_login_username);
        body.addView(username);

        body.addView(label("密码（8–128 位）"));
        password = input("请输入密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setId(R.id.lc_login_password);
        body.addView(password);

        displayRow = new LinearLayout(this);
        displayRow.setOrientation(LinearLayout.VERTICAL);
        displayRow.addView(label("昵称（可选，注册后可在账户设置里改）"));
        displayName = input("请输入昵称", InputType.TYPE_CLASS_TEXT);
        displayName.setId(R.id.lc_login_display);
        displayRow.addView(displayName);
        displayRow.setVisibility(ViewGroup.GONE);
        body.addView(displayRow);

        submit = new TextView(this);
        submit.setId(R.id.lc_login_submit);
        submit.setText("登录");
        submit.setTextSize(17f);
        submit.setTextColor(Color.WHITE);
        submit.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(50, 90, 182));
        bg.setCornerRadius(12f);
        submit.setBackground(bg);
        submit.setPadding(0, 24, 0, 24);
        submit.setOnClickListener(v -> doSubmit());
        body.addView(submit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setId(R.id.lc_login_status);
        status.setTextSize(13f);
        status.setTextColor(Color.rgb(200, 190, 170));
        status.setPadding(0, 16, 0, 16);
        body.addView(status);

        wx = new TextView(this);
        wx.setId(R.id.lc_login_wechat);
        wx.setText("微信登录");
        wx.setTextSize(14f);
        wx.setTextColor(Color.rgb(150, 130, 100));
        wx.setGravity(Gravity.CENTER);
        wx.setPadding(0, 20, 0, 20);
        wx.setOnClickListener(v -> unavailable("微信登录", "D-031",
                "微信登录需微信开放平台登记包名+签名，本项目本包无法通过校验，本轮不实现。"));
        body.addView(wx);

        // D-029 / D-030：原版登录入口占位。文案取自 DEX 字符串池逐字
        // （docs/fidelity/m4/login-strings-classes06.txt：`验证码登录`、`一键登录`、`荣耀登录`、`更多登录方式`、`无法使用荣耀登录`）。
        // 只做占位 + 不可用说明，不假装可用（总监 M4 Stage 4 派单）。
        TextView more = new TextView(this);
        more.setId(R.id.lc_login_more_title);
        more.setText("更多登录方式");
        more.setTextSize(13f);
        more.setTextColor(Color.rgb(200, 190, 170));
        more.setGravity(Gravity.CENTER);
        more.setPadding(0, 8, 0, 8);
        body.addView(more);

        body.addView(placeholder("验证码登录", R.id.lc_login_sms, "D-029",
                "后续可通过手机号验证码快速登录~"));
        body.addView(placeholder("一键登录", R.id.lc_login_onetap, "D-030",
                "一键登录需运营商号码认证，本项目无法接入"));
        body.addView(placeholder("荣耀登录", R.id.lc_login_honor, "D-030",
                "无法使用荣耀登录"));

        TextView note = new TextView(this);
        note.setId(R.id.lc_login_note);
        note.setText("说明：本页为本项目新增的账号密码登录，不是原版页面。原版的手机号验证码 / 一键登录 / 荣耀账号 / 微信登录只做入口占位，登记为 D-029 / D-030 / D-031，本轮不实现。");
        note.setTextSize(12f);
        note.setTextColor(Color.rgb(150, 130, 100));
        note.setPadding(0, 8, 0, 0);
        body.addView(note);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        tabLogin.setOnClickListener(v -> setMode(false));
        tabRegister.setOnClickListener(v -> setMode(true));
        setMode(false);
    }

    private TextView tab(String text, boolean active) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 20, 0, 20);
        t.setTextColor(active ? Color.rgb(235, 228, 215) : Color.rgb(150, 130, 100));
        return t;
    }

    private void setMode(boolean register) {
        registerMode = register;
        tabLogin.setTextColor(register ? Color.rgb(150, 130, 100) : Color.rgb(235, 228, 215));
        tabRegister.setTextColor(register ? Color.rgb(235, 228, 215) : Color.rgb(150, 130, 100));
        displayRow.setVisibility(register ? ViewGroup.VISIBLE : ViewGroup.GONE);
        submit.setText(register ? "注册并登录" : "登录");
        status.setText("");
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13f);
        t.setTextColor(Color.rgb(200, 190, 170));
        t.setPadding(0, 20, 0, 8);
        return t;
    }

    /** 原版登录入口占位（不可用）：文案逐字，点击只在状态行说明「不可用 + DI 编号」，不跳转、不假装可用。 */
    private TextView placeholder(String text, int id, String di, String why) {
        TextView t = new TextView(this);
        t.setId(id);
        t.setText(text);
        t.setTextSize(15f);
        t.setTextColor(Color.rgb(150, 130, 100));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 18, 0, 18);
        t.setBackgroundColor(Color.rgb(34, 30, 26));
        t.setOnClickListener(v -> unavailable(text, di, why));
        return t;
    }

    private void unavailable(String name, String di, String why) {
        status.setTextColor(Color.rgb(200, 170, 120));
        status.setText(name + "：" + why + "（" + di + "）");
        android.util.Log.d("LCLOGIN", "placeholder " + name + " " + di);
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setHintTextColor(Color.rgb(150, 130, 100));
        e.setTextColor(Color.rgb(235, 228, 215));
        e.setTextSize(15f);
        e.setSingleLine(true);
        return e;
    }

    private void doSubmit() {
        final String u = username.getText().toString().trim();
        final String p = password.getText().toString();
        final String d = displayName.getText().toString().trim();
        if (u.length() < 3 || u.length() > 32 || !u.matches("[A-Za-z0-9._-]+")) {
            status.setText("账号需 3–32 位，只能包含字母、数字、. _ -");
            return;
        }
        if (p.length() < 8 || p.length() > 128) {
            status.setText("密码需 8–128 位");
            return;
        }
        submit.setEnabled(false);
        status.setText(registerMode ? "注册中…" : "登录中…");
        final boolean reg = registerMode;
        new Thread(() -> {
            try {
                JSONObject data = reg ? LcApi.register(u, p, d) : LcApi.login(u, p);
                JSONObject user = data == null ? null : data.optJSONObject("user");
                String token = data == null ? null : data.optString("token", null);
                String exp = data == null ? null : data.optString("expires_at", null);
                if (token == null || token.isEmpty()) throw new Exception("服务端未返回 token");
                LcSession.save(this, token, user, exp);
                android.util.Log.d("LCLOGIN", (reg ? "register" : "login") + " ok uid="
                        + (user == null ? "?" : user.optInt("uid", -1)));
                runOnUiThread(() -> {
                    status.setText("登录成功");
                    setResult(RESULT_OK, new Intent());
                    finish();
                });
            } catch (LcApi.ApiError e) {
                fail("失败：" + e.getMessage() + "（code " + e.code + "）");
            } catch (Throwable t) {
                fail("网络不可用：无法连接本地服务（请确认已 adb reverse tcp:8787 tcp:8787）");
                android.util.Log.w("LCLOGIN", "network: " + t.getClass().getSimpleName());
            }
        }, "lc-login").start();
    }

    private void fail(String msg) {
        runOnUiThread(() -> {
            submit.setEnabled(true);
            status.setText(msg);
        });
    }
}


