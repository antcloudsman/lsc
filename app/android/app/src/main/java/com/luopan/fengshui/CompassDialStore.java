package com.luopan.fengshui;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * F-18 本机导入盘面的元数据（LC 自建，本地优先，无服务端）。
 *
 * 独立 prefs "lc_compass_dials"，避免与 MapManager（地图设置）和 lc_compass（F-17 显示）混用。
 * 只存本机导入项；内置 5 幅来自 drawable-nodpi（dial_full_0..4），不进本表。
 */
final class CompassDialStore {

    static final String PREFS = "lc_compass_dials";
    static final String K_IMPORTED = "imported";

    static final class Item {
        final String name;
        final String path;
        final String thumb;

        Item(String name, String path, String thumb) {
            this.name = name;
            this.path = path;
            this.thumb = thumb;
        }
    }

    private final SharedPreferences p;

    CompassDialStore(Context c) {
        p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<Item> list() {
        List<Item> out = new ArrayList<>();
        String raw = p.getString(K_IMPORTED, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String path = o.optString("path", "");
                if (path.isEmpty() || !new File(path).exists()) continue;
                out.add(new Item(o.optString("name", "盘面"), path, o.optString("thumb", "")));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    void add(Item item) {
        List<Item> items = list();
        items.add(item);
        save(items);
    }

    void remove(Item item) {
        List<Item> items = list();
        List<Item> keep = new ArrayList<>();
        for (Item i : items) {
            if (!i.path.equals(item.path)) keep.add(i);
        }
        save(keep);
    }

    private void save(List<Item> items) {
        JSONArray a = new JSONArray();
        for (Item i : items) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", i.name);
                o.put("path", i.path);
                o.put("thumb", i.thumb);
                a.put(o);
            } catch (JSONException ignored) {
            }
        }
        p.edit().putString(K_IMPORTED, a.toString()).apply();
    }
}
