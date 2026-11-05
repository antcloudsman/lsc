package com.luopan.fengshui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

/**
 * 工具管理页骨架（原版 ui.pages.ToolsManagerActivity，MODULES.md 第 71 行）。
 *
 * 入口：原版「更多 → 地图工具」面板右上角「编辑」（证据 orig-more.xml 第 3 行）；
 * 16 项工具的名称与顺序见 ToolCatalog（取自 orig-more.xml）。
 * 增删/排序的具体交互（拖拽？勾选？）等 docs/gap-modules/tools.md，禁止按想象做。
 * AT 可用 `am start -n com.luopan.fengshui/.ToolsManagerActivity` 直达。
 */
public class ToolsManagerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(LcUi.page(this, getString(R.string.lc_toolmgr_title),
                R.id.lc_toolmgr_back, R.id.lc_toolmgr_title, R.id.lc_toolmgr_list));

        // ---- 证据未落地：列表为空；16 项数据已就绪（ToolCatalog + GapDb 默认排序）----
        // 替换方式：按 docs/gap-modules/tools.md 的交互，渲染 lc_toolmgr_item_0..15。
    }
}
