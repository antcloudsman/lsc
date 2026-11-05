package com.luopan.fengshui;

import android.app.Activity;
import android.os.Bundle;

/**
 * 收藏点列表页骨架（原版 ui.pages.interest.InterestListActivity，MODULES.md 第 94 行）。
 *
 * 入口：原版「更多 → 地图工具」第 9 项「收藏点」（orig-more.xml 第 12 行）。
 * 列表行样式/排序/空态文案等 docs/gap-modules/interest.md；数据已由 GapDb 提供。
 * AT 可用 `am start -n com.luopan.fengshui/.InterestListActivity` 直达。
 */
public class InterestListActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(LcUi.page(this, getString(R.string.lc_interest_title),
                R.id.lc_interest_back, R.id.lc_interest_title, R.id.lc_interest_list));

        // ---- 证据未落地：列表为空；GapDb.listInterest() 已可读 ----
    }
}
