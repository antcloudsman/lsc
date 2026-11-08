package com.luopan.fengshui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * 功能子页统一骨架（18 项复用）。
 *
 * 视觉沿用设置页那套（真机截图 INDEX 标定）：52dp 白底标题栏 + 左返回 ‹ + 16sp 加粗居中标题；
 * 可选 11.6dp #F0F3F7 区块间隔；内容区白底、占满剩余高度。
 *
 * 约定：
 * - 子类通过 {@link #title()} / {@link #backId()} / {@link #titleId()} / {@link #contentId()}
 *   提供文案与稳定 id（前缀 lc_<feature>_*），在 {@link #onPageReady(FrameLayout)} 里填内容；
 * - 不含任何锁/权限/未开通组件（无门槛原则）；
 * - 导出只在 debug 变体（D-011），主题用 Theme.Luopan.Light。
 */
public abstract class LcFeatureActivity extends Activity {

    protected FrameLayout content;

    /** 测试钩子（仅 debug）：把 JS 投进当前前台子页的 WebView，供二级页事件构造。 */
    private final DebugInject debugInject = new DebugInject();

    @Override
    protected void onResume() {
        super.onResume();
        DebugInject.register(this, debugInject);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(debugInject);
        } catch (Throwable ignored) {
        }
        DebugInject.detach();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.lc_setting_bg));
        root.addView(LcUi.toolbarLight(this, backId(), titleId(), title()));
        if (hasBlockGap()) root.addView(LcUi.blockGap(this));

        content = new FrameLayout(this);
        content.setId(contentId());
        content.setBackgroundColor(getColor(R.color.lc_setting_bg));
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        onPageReady(content);
    }

    /** 标题栏与内容区的稳定 id（子类实现，命名 lc_<feature>_*）。 */
    protected abstract int backId();

    protected abstract int titleId();

    protected abstract int contentId();

    protected abstract String title();

    /** 是否在标题栏下加 11.6dp 区块间隔；默认不加。 */
    protected boolean hasBlockGap() {
        return false;
    }

    /** 子类填充内容（content 已是白底 FrameLayout）。 */
    protected abstract void onPageReady(FrameLayout content);

    protected int dp(float v) {
        return LcUi.dp(this, v);
    }
}
