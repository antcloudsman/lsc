package com.luopan.fengshui;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 工具管理的本地存储（原版 ToolsManager / MMKV 实例 id = "ToolsManager"）。
 *
 * 口径照 docs/gap-modules/ToolsManagerActivity.md §3：
 * - 生效 key {@code toolsJson2}，值为 JSON 数组，元素是工具中文名（原版按 getText 存）；
 * - 首次启动默认首页 5 项：超清优化、海拔查询、罗盘、路网、等高线；
 * - 泄漏 key {@code toolsJson} 不再写入（原版流程也不写）。
 *
 * 值用中文名而不是 key，是为了和原版 JSON 同构（将来做同设备迁移可直接读）。
 * 目录外的名字（改版/脏数据）读取时丢弃，写回时只保留目录内项。
 */
final class ToolsManagerStore {

    static final String PREFS = "ToolsManager";
    static final String K_TOOLS_JSON2 = "toolsJson2";

    static final String[] DEFAULT_NOW = {"超清优化", "海拔查询", "罗盘", "路网", "等高线"};

    private final SharedPreferences p;

    ToolsManagerStore(Context c) {
        p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 首页展示功能（有序）；未写过/写坏/过滤后为空 → 默认 5 项。 */
    List<String> nowMenu() {
        List<String> out = new ArrayList<>();
        String json = p.getString(K_TOOLS_JSON2, "");
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) {
                    String name = a.optString(i, "");
                    if (ToolCatalog.containsLabel(name) && !out.contains(name)) out.add(name);
                }
            } catch (JSONException ignored) {
                // 坏数据按默认处理，不覆盖用户文件以外的状态
            }
        }
        if (out.isEmpty()) out.addAll(Arrays.asList(DEFAULT_NOW));
        return out;
    }

    void saveNowMenu(List<String> labels) {
        JSONArray a = new JSONArray();
        for (String name : labels) {
            if (ToolCatalog.containsLabel(name) && !contains(a, name)) a.put(name);
        }
        p.edit().putString(K_TOOLS_JSON2, a.toString()).apply();
    }

    private static boolean contains(JSONArray a, String name) {
        for (int i = 0; i < a.length(); i++) {
            if (name.equals(a.optString(i, ""))) return true;
        }
        return false;
    }
}
