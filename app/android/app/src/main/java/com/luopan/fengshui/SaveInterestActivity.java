package com.luopan.fengshui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

/**
 * 保存收藏点页骨架（原版 ui.pages.interest.SaveInterestActivity，MODULES.md 第 95 行）。
 *
 * 内容（保存当前坐向 + 坐标）由总监定；字段文案/按钮位置等 docs/gap-modules/interest.md，
 * 证据未落地前不放输入框，避免做出与原版不一致的表单。
 * AT 可用 `am start -n com.luopan.fengshui/.SaveInterestActivity` 直达。
 */
public class SaveInterestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        root.addView(LcUi.toolbar(this, R.id.lc_save_back, R.id.lc_save_title,
                getString(R.string.lc_save_title)));

        LinearLayout form = new LinearLayout(this);
        form.setId(R.id.lc_save_form);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(LcUi.dp(this, 20), LcUi.dp(this, 20),
                LcUi.dp(this, 20), LcUi.dp(this, 20));
        root.addView(form, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- 证据未落地：表单为空；插入数据走 GapDb.insertInterest(...) ----
        setContentView(root);
    }
}
