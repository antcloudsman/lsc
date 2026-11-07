package com.luopan.fengshui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * R2-001：品牌启动页（原版 `SplashActivity` 同构）。
 *
 * 素材全部来自 APK（`docs/fidelity/assets/brand/`，由逆向 `9e934e7` 入库）：
 * - `ic_splash_bg.webp` 1500×3248 —— 雪山湖面背景；
 * - `ic_splash_logo.webp` 780×476 —— 已含「3D实景」图标 + 标题「三维山水全景地图」+ 副标题
 *   「实时交互的真实三维地形观察工具」，因此**页面不再拼文字**，避免字体/字号与原版不一致。
 *
 * 时长按「未确认」处理：原版 Splash 在探针里 60 s 停住、`am start -W` 直接 ANR
 * （`docs/fidelity/lc-captures.md` §4），真实停留时长取不到；这里固定 1500 ms 后进主界面，
 * 属判据允许的 DI 决策，不冒充「与原版 ±100ms 一致」。
 */
public class SplashActivity extends Activity {

    /** 停留时长（ms）：原版不可测，取 1.5 s 保证品牌页可见且不拖慢冷启。 */
    private static final long HOLD_MS = 1500L;

    /** logo 尺寸/位置：按原版 `_launch-probe.png` 像素对齐（标题带 y731–779、x402–824，宽 422）。 */
    private static final int LOGO_WIDTH = 660;
    private static final int LOGO_TOP = 313;

    private boolean moved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setId(R.id.lc_splash_root);
        root.setBackgroundColor(Color.WHITE);

        ImageView bg = new ImageView(this);
        bg.setId(R.id.lc_splash_bg);
        bg.setImageResource(R.drawable.lc_splash_bg);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView logo = new ImageView(this);
        logo.setId(R.id.lc_splash_logo);
        logo.setImageResource(R.drawable.lc_splash_logo);
        logo.setContentDescription(getString(R.string.app_name));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LOGO_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = LOGO_TOP;
        root.addView(logo, lp);

        setContentView(root);
        // 原版启动页是全屏图（`orig/_launch-probe.png` 顶边无状态栏黑带）；这里隐藏系统栏对齐。
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        android.util.Log.d("LCSPLASH", "show logo top=" + LOGO_TOP + " w=" + LOGO_WIDTH + " hold=" + HOLD_MS);
        new Handler(Looper.getMainLooper()).postDelayed(this::goMain, HOLD_MS);
    }

    private void goMain() {
        if (moved) return;
        moved = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        goMain();
    }
}


