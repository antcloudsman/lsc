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
    static final int VERSION = 4;

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
                + "created_at INTEGER NOT NULL,"
                + "remote_id INTEGER NOT NULL DEFAULT 0,"
                + "rev INTEGER NOT NULL DEFAULT 0,"
                + "sync_state TEXT NOT NULL DEFAULT 'pending',"
                + "client_key TEXT NOT NULL DEFAULT '',"
                + "delete_pending INTEGER NOT NULL DEFAULT 0,"
                + "owner_uid INTEGER NOT NULL DEFAULT 0)");
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
                case 1:
                    // M4 v2：收藏点云同步状态（本地优先 + 离线队列）
                    db.execSQL("ALTER TABLE " + T_INTEREST + " ADD COLUMN remote_id INTEGER NOT NULL DEFAULT 0");
                    db.execSQL("ALTER TABLE " + T_INTEREST + " ADD COLUMN rev INTEGER NOT NULL DEFAULT 0");
                    db.execSQL("ALTER TABLE " + T_INTEREST + " ADD COLUMN sync_state TEXT NOT NULL DEFAULT 'pending'");
                    break;
                case 2:
                    // M4 v3：幂等键（重推不重复建点）+ 删除墓碑（远端删本地补推）
                    db.execSQL("ALTER TABLE " + T_INTEREST + " ADD COLUMN client_key TEXT NOT NULL DEFAULT ''");
                    db.execSQL("ALTER TABLE " + T_INTEREST + " ADD COLUMN delete_pending INTEGER NOT NULL DEFAULT 0");
                    break;
                case 3:
                    // M4 v4：行级账号归属。跨账号同步时用它把「不属于当前账号的行」整行排除，
                    // 否则换号后全量对账会把上一账号的本地数据当成「远端已删」清掉（测试实测丢了 3 条）。
                    db.execSQL("ALTER TABLE " + T_INTEREST + " ADD COLUMN owner_uid INTEGER NOT NULL DEFAULT 0");
                    break;
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

    /** 新建本地收藏点：`ownerUid` 在创建时刻写死，避免「拉回/新建的行 owner_uid=0 被下次同步认领给别的账号」。 */
    long insertInterest(String name, double lat, double lng,
                        double sittingDeg, double facingDeg, int ownerUid) {
        ContentValues v = new ContentValues();
        v.put("owner_uid", ownerUid);
        v.put("name", name == null ? "" : name);
        v.put("lat", lat);
        v.put("lng", lng);
        v.put("sitting_deg", sittingDeg);
        v.put("facing_deg", facingDeg);
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert(T_INTEREST, null, v);
    }

    /**
     * 界面列表：**只显示当前账号的行**（审核验 §33）。
     *
     * 不加账号条件会造成跨账号破坏性副作用：在 B 的会话里看到并删掉 A 的行 → 墓碑因 `owner_uid=A`
     * 进不了 `listDeletePending(B)`、挂在本地不推 → 等 A 下次同步取到它，把 A 服务端的数据真删了。
     * 用户以为自己删的是 B 的。
     *
     * `owner_uid=0` 的孤儿行（升级前存量、归属未知）**不显示**：它们不可判断归属，
     * 显示出来点删除就会落入上面那条链路。等正确账号登录、或后续版本人工处理。
     */
    List<InterestPoint> listInterest(int uid) {
        List<InterestPoint> out = new ArrayList<>();
        // delete_pending=1 是「已删、待推服务端」的墓碑：列表不再显示，但同步要用它补推删除。
        String where = "delete_pending=0";
        String[] args = null;
        if (uid > 0) {
            where += " AND owner_uid=?";
            args = new String[]{String.valueOf(uid)};
        }
        try (Cursor c = getReadableDatabase().query(T_INTEREST, null, where, args,
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

    // ---------------- 收藏点云同步（M4：本地优先 + 离线队列） ----------------

    /**
     * 待同步（新建未推 / 推送失败）的本地收藏点；sync_state<>'clean' 即离线队列。
     * `ownerUid>0` 时只取「属于该账号或尚未归属（owner_uid=0）」的行：
     * 换账号后上一账号的行留在本地，但绝不能推进当前账号（那是串数据）。
     */
    List<InterestPoint> listPendingInterest(int ownerUid) {
        List<InterestPoint> out = new ArrayList<>();
        String where = "sync_state<>'clean' AND delete_pending=0";
        String[] args = null;
        if (ownerUid > 0) {
            // 只推本账号的行。不再放行 owner_uid=0：无归属行可能是别的账号的存量数据（remote_id>0），
            // 推进当前账号就是串数据（审核验 §30）。无归属且未同步过的行已由 adoptUnownedRows 认领。
            where += " AND owner_uid=?";
            args = new String[]{String.valueOf(ownerUid)};
        }
        try (Cursor c = getReadableDatabase().query(T_INTEREST, null, where, args, null, null, "id ASC")) {
            while (c.moveToNext()) out.add(row(c));
        }
        return out;
    }

    List<InterestPoint> listPendingInterest() {
        return listPendingInterest(0);
    }

    /** 某行归属的账号 uid（0 = 尚未归属）。 */
    int ownerUidOf(long localId) {
        try (Cursor c = getReadableDatabase().query(T_INTEREST, new String[]{"owner_uid"},
                "id=?", new String[]{String.valueOf(localId)}, null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** 绑定账号时把「无归属」的存量行认领给该账号；已有归属的不动（可能是别的账号的）。 */
    /**
     * 绑定账号时只认领「从未同步过」的行（`owner_uid=0 AND remote_id=0`）。
     *
     * **不能认领 `remote_id>0` 的无归属行**：这类行是升级/旧版本留下的，它在某个账号的服务端已经存在，
     * 本地无从判断主人。自动认领 = 猜，猜错就会让新账号把这些行当自己的 → 对账时「本账号 list 里没有」
     * 被整批 purge（审核验 §30 实测路径）。这类行一律「既不推也不对账」，切回正确账号时人工/后续版本处理。
     */
    int adoptUnownedRows(int uid) {
        ContentValues v = new ContentValues();
        v.put("owner_uid", uid);
        return getWritableDatabase().update(T_INTEREST, v, "owner_uid=0 AND remote_id=0", null);
    }

    /**
     * 按「服务端声明」认领幽灵行（审核验 §33.2 / 服务端提案）：只处理 `owner_uid=0 AND remote_id>0` 的行，
     * 且**仅当该 remote_id 出现在当前账号的 list 里**才认领 —— 这是服务端在说「这个 id 是我的」，不是本地猜。
     * 不在集合里的保持 `owner_uid=0`（不推、不对账、不删），等它真正的主人登录或 M5 tombstone 处理。
     *
     * <p><b>限制（别当全量认领）</b>：调用方目前只取**第一页 `limit=200`**；`count >= 200` 时
     * `FavoriteSync` 整轮跳过对账与认领 —— 是**安全侧失败**（不误写、不误删、不动数据），只是功能不完整。
     * 所以本方法实际覆盖「≤200 条」。**翻页枚举与 tombstone（`list` 回 `deleted=1`）属 M5**，
     * 两者要一起做才有意义：没有 tombstone 时无法区分「属于别人」与「远端已删」，宁可留幽灵行。
     *
     * @param liveIds 当前账号 `GET list` 返回的 id 集合（当前实现仅为第一页）
     * @return 认领行数
     */
    int adoptByRemoteIds(int uid, java.util.Set<Long> liveIds) {
        if (uid <= 0 || liveIds == null || liveIds.isEmpty()) return 0;
        int adopted = 0;
        java.util.List<Long> orphanIds = new ArrayList<>();
        java.util.List<Long> orphanRemote = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(T_INTEREST, new String[]{"id", "remote_id"},
                "owner_uid=0 AND remote_id>0", null, null, null, "id ASC")) {
            while (c.moveToNext()) {
                orphanIds.add(c.getLong(0));
                orphanRemote.add(c.getLong(1));
            }
        }
        for (int i = 0; i < orphanIds.size(); i++) {
            if (!liveIds.contains(orphanRemote.get(i))) continue;
            ContentValues v = new ContentValues();
            v.put("owner_uid", uid);
            adopted += getWritableDatabase().update(T_INTEREST, v, "id=?",
                    new String[]{String.valueOf(orphanIds.get(i))});
        }
        return adopted;
    }
    /** 幂等键：每行一个稳定 UUID，首次同步时生成并持久化，重推沿用它 → 服务端撞键返回同一行，不会重复建点。 */
    String clientKeyOf(long localId) {
        try (Cursor c = getReadableDatabase().query(T_INTEREST, new String[]{"client_key"},
                "id=?", new String[]{String.valueOf(localId)}, null, null, null)) {
            if (c.moveToFirst()) {
                String k = c.getString(0);
                if (k != null && !k.isEmpty()) return k;
            }
        }
        String key = java.util.UUID.randomUUID().toString().replace("-", "");
        ContentValues v = new ContentValues();
        v.put("client_key", key);
        getWritableDatabase().update(T_INTEREST, v, "id=?", new String[]{String.valueOf(localId)});
        return key;
    }

    /** 删除墓碑：本地先软删（delete_pending=1），推送成功后再物理删，避免「本地删了远端还在」复活。 */
    void markInterestDeletePending(long localId) {
        ContentValues v = new ContentValues();
        v.put("delete_pending", 1);
        getWritableDatabase().update(T_INTEREST, v, "id=?", new String[]{String.valueOf(localId)});
    }

    /**
     * 待推删除的墓碑（`delete_pending=1`）。
     *
     * 与 {@link #listPendingInterest(int)} **区别对待**：删除队列放行 `owner_uid=0` 的孤儿行。
     * 理由：孤儿行（升级前存量、`remote_id>0`、归属未知）按设计「不推不认领」，于是它永远匹配不到当前账号；
     * 如果删除也卡住，用户删了以后墓碑推不出去 → 服务端那行还在 → 别的设备复活、本机留死行（审核验 §32）。
     * **删除不会串数据**：这里只发 `id`，服务端按 token 判归属，不是自己的返 404，而 FavoriteSync 已把 404 当作删除完成。
     */
    List<InterestPoint> listDeletePending(int ownerUid) {
        List<InterestPoint> out = new ArrayList<>();
        String where = "delete_pending=1";
        String[] args = null;
        if (ownerUid > 0) {
            where += " AND (owner_uid=0 OR owner_uid=?)";
            args = new String[]{String.valueOf(ownerUid)};
        }
        try (Cursor c = getReadableDatabase().query(T_INTEREST, null, where, args, null, null, "id ASC")) {
            while (c.moveToNext()) out.add(row(c));
        }
        return out;
    }

    List<InterestPoint> listDeletePending() {
        return listDeletePending(0);
    }

    void purgeInterest(long localId) {
        getWritableDatabase().delete(T_INTEREST, "id=?", new String[]{String.valueOf(localId)});
    }

    /** 远端已删（本地有 remote_id 但服务端 list 里没有）→ 本地跟随删除。 */
    long localIdByRemote(long remoteId) {
        try (Cursor c = getReadableDatabase().query(T_INTEREST, new String[]{"id"},
                "remote_id=? AND sync_state='clean' AND delete_pending=0",
                new String[]{String.valueOf(remoteId)}, null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    long remoteIdOf(long localId) {        try (Cursor c = getReadableDatabase().query(T_INTEREST, new String[]{"remote_id"},
                "id=?", new String[]{String.valueOf(localId)}, null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    int revOf(long localId) {
        try (Cursor c = getReadableDatabase().query(T_INTEREST, new String[]{"rev"},
                "id=?", new String[]{String.valueOf(localId)}, null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    void markInterestSynced(long localId, long remoteId, int rev) {
        ContentValues v = new ContentValues();
        v.put("remote_id", remoteId);
        v.put("rev", rev);
        v.put("sync_state", "clean");
        getWritableDatabase().update(T_INTEREST, v, "id=?", new String[]{String.valueOf(localId)});
    }

    /** 拉取落地：按 remote_id 更新或插入；不覆盖本地待同步行（让本地先推）。 */
    long upsertRemoteInterest(long remoteId, String name, double lat, double lng, int rev, long createdAtMs) {
        return upsertRemoteInterest(remoteId, name, lat, lng, rev, createdAtMs, 0, 180, 0);
    }

    /**
     * 拉取落地（带坐向）：坐向由服务端 `des`（「坐X° 向Y°」）解析后传入，还原原值，
     * 不再硬编码 0/180 —— 否则换设备/重装后所有收藏点坐向全错（审客户端 §4b）。
     */
    long upsertRemoteInterest(long remoteId, String name, double lat, double lng,
                              int rev, long createdAtMs, double sittingDeg, double facingDeg,
                              int ownerUid) {
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor c = db.query(T_INTEREST, new String[]{"id", "sync_state"},
                "remote_id=?", new String[]{String.valueOf(remoteId)}, null, null, null)) {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                if ("clean".equals(c.getString(1))) {
                    ContentValues v = new ContentValues();
                    v.put("name", name == null ? "" : name);
                    v.put("lat", lat);
                    v.put("lng", lng);
                    v.put("rev", rev);
                    // 更新分支也补写 owner_uid：否则「升级前拉回来、owner_uid=0」的行永远无归属，
                    // 下次换账号仍会被认领、进而被本账号 list 判成「远端已删」purge（审核验 §31③）。
                    if (ownerUid > 0) v.put("owner_uid", ownerUid);
                    db.update(T_INTEREST, v, "id=?", new String[]{String.valueOf(id)});
                }
                return id;
            }
        }
        ContentValues v = new ContentValues();
        v.put("name", name == null ? "" : name);
        v.put("lat", lat);
        v.put("lng", lng);
        v.put("sitting_deg", sittingDeg);
        v.put("facing_deg", facingDeg);
        v.put("created_at", createdAtMs);
        v.put("remote_id", remoteId);
        v.put("rev", rev);
        v.put("sync_state", "clean");
        v.put("owner_uid", ownerUid);
        return db.insert(T_INTEREST, null, v);
    }

    private static InterestPoint row(Cursor c) {
        return new InterestPoint(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getDouble(c.getColumnIndexOrThrow("lat")),
                c.getDouble(c.getColumnIndexOrThrow("lng")),
                c.getDouble(c.getColumnIndexOrThrow("sitting_deg")),
                c.getDouble(c.getColumnIndexOrThrow("facing_deg")),
                c.getLong(c.getColumnIndexOrThrow("created_at")));
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










