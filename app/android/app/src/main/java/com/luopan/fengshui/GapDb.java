package com.luopan.fengshui;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 缺失模块的本地存储（收藏点 + 工具管理）。
 *
 * <h3>选型</h3>
 * 列表型数据（收藏点、16 工具顺序/启用）用 SQLite；标量开关沿用项目既有
 * {@code lc_prefs}（SharedPreferences），避免两套风格混用。
 *
 * <h3>表结构 v1</h3>
 * <pre>
 * interest(id INTEGER PK AUTOINCREMENT,
 *          name TEXT NOT NULL DEFAULT '',
 *          lat REAL NOT NULL, lng REAL NOT NULL,
 *          sitting_deg REAL NOT NULL, facing_deg REAL NOT NULL,
 *          created_at INTEGER NOT NULL)
 *   收藏点：坐标 + 当前坐/向（度，0..360）；created_at 毫秒，列表按它倒序。
 *
 * tool(key TEXT PK, sort INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1)
 *   16 个地图工具的顺序与启用状态；key 见 ToolCatalog，首次建库按原版
 *   orig-more.xml 的顺序播种（sort 0..15，enabled=1）。
 * </pre>
 *
 * <h3>迁移策略</h3>
 * {@link #VERSION} 每次改表 +1，在 {@link #onUpgrade} 的 switch 里按
 * 旧版本号顺序补 ALTER/CREATE；不写"删库重建"，避免用户收藏丢失。
 * 当前 v1，无历史版本。
 */
class GapDb extends SQLiteOpenHelper {

    static final String NAME = "lc_gap.db";
    static final int VERSION = 1;

    static final String T_INTEREST = "interest";
    static final String T_TOOL = "tool";

    GapDb(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_INTEREST + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL DEFAULT '',"
                + "lat REAL NOT NULL,"
                + "lng REAL NOT NULL,"
                + "sitting_deg REAL NOT NULL,"
                + "facing_deg REAL NOT NULL,"
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE " + T_TOOL + " ("
                + "key TEXT PRIMARY KEY,"
                + "sort INTEGER NOT NULL,"
                + "enabled INTEGER NOT NULL DEFAULT 1)");
        seedTools(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 逐版本迁移，禁止删表重建（收藏点不可丢）。
        for (int v = oldVersion; v < newVersion; v++) {
            switch (v) {
                // case 1:
                //     db.execSQL("ALTER TABLE " + T_INTEREST + " ADD COLUMN ...");
                //     break;
                default:
                    break;
            }
        }
    }

    /** 首次建库：按原版「地图工具」面板顺序播种 16 项。 */
    private static void seedTools(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            for (int i = 0; i < ToolCatalog.COUNT; i++) {
                ContentValues v = new ContentValues();
                v.put("key", ToolCatalog.KEYS[i]);
                v.put("sort", i);
                v.put("enabled", 1);
                db.insertWithOnConflict(T_TOOL, null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ---------------- 收藏点 ----------------

    long insertInterest(String name, double lat, double lng,
                        double sittingDeg, double facingDeg) {
        ContentValues v = new ContentValues();
        v.put("name", name == null ? "" : name);
        v.put("lat", lat);
        v.put("lng", lng);
        v.put("sitting_deg", sittingDeg);
        v.put("facing_deg", facingDeg);
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert(T_INTEREST, null, v);
    }

    List<InterestPoint> listInterest() {
        List<InterestPoint> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(T_INTEREST, null, null, null,
                null, null, "created_at DESC, id DESC")) {
            while (c.moveToNext()) {
                out.add(new InterestPoint(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("name")),
                        c.getDouble(c.getColumnIndexOrThrow("lat")),
                        c.getDouble(c.getColumnIndexOrThrow("lng")),
                        c.getDouble(c.getColumnIndexOrThrow("sitting_deg")),
                        c.getDouble(c.getColumnIndexOrThrow("facing_deg")),
                        c.getLong(c.getColumnIndexOrThrow("created_at"))));
            }
        }
        return out;
    }

    boolean deleteInterest(long id) {
        return getWritableDatabase().delete(T_INTEREST, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    int countInterest() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + T_INTEREST, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ---------------- 工具管理 ----------------

    /** 按本地 sort 升序返回 16 项；label 来自 ToolCatalog（不落库，避免文案漂移）。 */
    List<ToolState> listTools() {
        List<ToolState> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(T_TOOL, null, null, null,
                null, null, "sort ASC, key ASC")) {
            while (c.moveToNext()) {
                String key = c.getString(c.getColumnIndexOrThrow("key"));
                out.add(new ToolState(key, ToolCatalog.labelOf(key),
                        c.getInt(c.getColumnIndexOrThrow("sort")),
                        c.getInt(c.getColumnIndexOrThrow("enabled")) != 0));
            }
        }
        return out;
    }

    /** 启用/停用；停用的工具不进「地图工具」面板。 */
    void setToolEnabled(String key, boolean enabled) {
        ContentValues v = new ContentValues();
        v.put("enabled", enabled ? 1 : 0);
        getWritableDatabase().update(T_TOOL, v, "key=?", new String[]{key});
    }

    /** 按传入顺序重排（不在目录里的 key 忽略；未传的保持原样，不丢项）。 */
    void saveToolOrder(List<String> keys) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                if (!ToolCatalog.contains(key)) continue;
                ContentValues v = new ContentValues();
                v.put("sort", i);
                db.update(T_TOOL, v, "key=?", new String[]{key});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 恢复原版默认顺序（16 项全启用），用于「重置」类操作。 */
    void resetTools() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < ToolCatalog.COUNT; i++) {
                ContentValues v = new ContentValues();
                v.put("sort", i);
                v.put("enabled", 1);
                int n = db.update(T_TOOL, v, "key=?", new String[]{ToolCatalog.KEYS[i]});
                if (n == 0) {
                    v.put("key", ToolCatalog.KEYS[i]);
                    db.insert(T_TOOL, null, v);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
