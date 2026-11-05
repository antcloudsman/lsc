package com.luopan.fengshui;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 18 项缺失功能的开关（调试/回退用途，D-001）。
 *
 * 口径（docs/features/00-PLAN.md §4.0 + 00-UI-ROUTES.md §5）：
 * - 默认全开：功能本身无门槛、免费开放，开关不影响可用性；
 * - 关闭 = 回退到旧 stub 行为，仅用于定位回归，不出现任何「未开通/升级」提示；
 * - 存储独立：SharedPreferences "lc_features"，键 enabled_f01..f18，布尔。
 */
final class FeatureFlags {

    static final String PREFS = "lc_features";

    static final String[] IDS = {
            "f01", "f02", "f03", "f04", "f05", "f06", "f07", "f08", "f09",
            "f10", "f11", "f12", "f13", "f14", "f15", "f16", "f17", "f18"
    };

    /** 与 00-PLAN §1 的 18 项一一对应（F-xx 顺序）。 */
    static final String[] NAMES = {
            "全地图工具", "超清优化", "海拔查询", "路网", "叠加", "看山脉",
            "看水系", "探索", "天气", "历史影像", "收藏点", "测量",
            "画笔", "钓点", "轨迹记录", "地理影像", "透明度/天支线", "盘面目录"
    };

    private final SharedPreferences p;

    FeatureFlags(Context c) {
        p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isEnabled(String id) {
        return p.getBoolean("enabled_" + id, true);
    }

    void setEnabled(String id, boolean enabled) {
        p.edit().putBoolean("enabled_" + id, enabled).apply();
    }

    int enabledCount() {
        int n = 0;
        for (String id : IDS) {
            if (isEnabled(id)) n++;
        }
        return n;
    }
}
