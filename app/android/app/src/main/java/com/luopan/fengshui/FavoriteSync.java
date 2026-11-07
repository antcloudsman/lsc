package com.luopan.fengshui;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * M4 收藏点云同步（本地优先 + 离线队列）。
 *
 * 顺序固定为「先推后拉」：
 * 1. 推：本地 `sync_state != clean` 的行逐条 `POST /v2/userCollectPoint/save`，成功记 `remote_id/rev`；
 *    失败的行**保留在待同步队列**，下次再推（`save` 带 `rev` 走乐观锁，409 时用 `data.server` 的 rev 收敛）；
 * 2. 拉：`GET /v2/userCollectPoint/list?since=<cursor>&limit=200`，按 `remote_id` upsert，cursor 存本地 prefs。
 *
 * 只在已登录时同步；未登录返回「未登录」，不抛异常。调用方放后台线程。
 */
final class FavoriteSync {

    private static final String PREF = "lc_sync";
    private static final String K_CURSOR = "collect_cursor";

    private FavoriteSync() {}

    /** @return 人类可读摘要（UI 直接展示）；失败抛异常由调用方展示错误。 */
    static String sync(Context context) throws Exception {
        String token = LcSession.token(context);
        if (token == null) return "未登录，未同步";
        GapDb db = new GapDb(context);

        // ---- 先推删除墓碑（方向一：本机删 → 服务端也删）----
        int deleted = 0, deleteFailed = 0;
        for (InterestPoint p : db.listDeletePending()) {
            long remote = db.remoteIdOf(p.id);
            try {
                if (remote > 0) LcApi.deletePoint(token, remote);
                db.purgeInterest(p.id);   // 服务端删成功（或本来就没远端行）才本地物理删
                deleted++;
            } catch (LcApi.ApiError e) {
                if (e.code == 404) {
                    // 服务端已无此行：视为删除完成，不留幽灵
                    db.purgeInterest(p.id);
                    deleted++;
                } else {
                    deleteFailed++;
                    android.util.Log.w("LCSYNC", "delete failed id=" + p.id + " code=" + e.code);
                }
            } catch (Throwable t) {
                deleteFailed++;
                android.util.Log.w("LCSYNC", "delete net id=" + p.id + " " + t.getClass().getSimpleName());
            }
        }

        int pushed = 0, failed = 0, conflicts = 0;
        for (InterestPoint p : db.listPendingInterest()) {
            try {
                JSONObject body = new JSONObject();
                long remote = db.remoteIdOf(p.id);
                // 幂等键随行持久化：崩在「save 成功、还没写回 remote_id」这条缝里，
                // 下次重推用同一 key，服务端撞键返回同一行 → 不会重复建点（§27 判据 A/B）。
                String idemKey = db.clientKeyOf(p.id);
                if (remote > 0) {
                    body.put("id", remote);
                    int rev = db.revOf(p.id);
                    if (rev > 0) body.put("rev", rev);
                }
                body.put("name", p.name == null ? "" : p.name);
                body.put("address", "");
                body.put("latitude", p.lat);
                body.put("longitude", p.lng);
                body.put("des", desOf(p.sittingDeg, p.facingDeg));
                body.put("images", "[]");
                body.put("icon", "default");
                body.put("style", "0");
                body.put("overlay", 1);
                body.put("dbId", 0);
                body.put("createTime", iso(p.createdAt));
                JSONObject data = LcApi.savePoint(token, body, idemKey);
                JSONObject point = data == null ? null : data.optJSONObject("point");
                long id = point == null ? remote : point.optLong("id", remote);
                int rev = point == null ? 0 : point.optInt("rev", 0);
                db.markInterestSynced(p.id, id, rev);
                pushed++;
            } catch (LcApi.ApiError e) {
                if (e.code == 409 && e.data != null) {
                    JSONObject server = e.data.optJSONObject("server");
                    if (server != null) {
                        db.markInterestSynced(p.id, server.optLong("id", 0), server.optInt("rev", 0));
                        conflicts++;
                        continue;
                    }
                }
                failed++;
                android.util.Log.w("LCSYNC", "push failed id=" + p.id + " code=" + e.code);
            } catch (Throwable t) {
                failed++;
                android.util.Log.w("LCSYNC", "push net id=" + p.id + " " + t.getClass().getSimpleName());
            }
        }

        int cursor = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(K_CURSOR, 0);
        int pulled = 0, maxRev = cursor, restored = 0;

        JSONObject data = LcApi.listPoints(token, cursor, 200);
        JSONArray items = data == null ? null : data.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.optJSONObject(i);
                if (o == null) continue;
                long id = o.optLong("id", 0);
                if (id <= 0) continue;
                int rev = o.optInt("rev", 0);

                // 坐向从服务端 `des`（「坐X° 向Y°」）解析还原；解析不到才回落 0/180 并记日志。
                double[] sf = parseSitFacing(o.optString("des", null));
                if (sf != null) restored++;
                db.upsertRemoteInterest(id, o.optString("name", ""),
                        o.optDouble("latitude", 0), o.optDouble("longitude", 0),
                        rev, parseIso(o.optString("createTime", null)),
                        sf == null ? 0 : sf[0], sf == null ? 180 : sf[1]);
                maxRev = Math.max(maxRev, rev);
                pulled++;
            }
        }
        // 方向二：远端已删 → 本机跟随删。
        // 注意：增量拉取只返回 rev>cursor 的行，拿它当「全量存在集」会把老点全误删。
        // 所以单独拉一次全量 id（since=0）做对账；limit=200，超过时记日志说明对账不完整。
        int purgedRemote = 0;
        try {
            JSONObject all = LcApi.listPoints(token, 0, 200);
            JSONArray allItems = all == null ? null : all.optJSONArray("items");
            if (allItems != null) {
                java.util.Set<Long> live = new java.util.HashSet<>();
                for (int i = 0; i < allItems.length(); i++) {
                    JSONObject o = allItems.optJSONObject(i);
                    if (o != null && o.optLong("id", 0) > 0) live.add(o.optLong("id", 0));
                }
                if (allItems.length() >= 200) {
                    android.util.Log.w("LCSYNC", "full list hit limit 200, reconcile may be partial");
                }
                for (InterestPoint local : db.listInterest()) {
                    long remote = db.remoteIdOf(local.id);
                    if (remote <= 0 || live.contains(remote)) continue;
                    db.purgeInterest(local.id);
                    purgedRemote++;
                }
            }
        } catch (Throwable t) {
            // 对账失败不影响推送/拉取结果，只记日志，不误删。
            android.util.Log.w("LCSYNC", "reconcile skipped: " + t.getClass().getSimpleName());
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(K_CURSOR, maxRev).apply();
        android.util.Log.d("LCSYNC", "push=" + pushed + " failed=" + failed + " conflict=" + conflicts
                + " pull=" + pulled + " cursor=" + maxRev + " del=" + deleted + " delFail=" + deleteFailed
                + " restoredSitFacing=" + restored + " purgedRemote=" + purgedRemote);
        return "同步完成：上传 " + pushed + "，拉取 " + pulled
                + (deleted > 0 ? ("，删除 " + deleted) : "")
                + (purgedRemote > 0 ? ("，跟随远端删除 " + purgedRemote) : "")
                + (failed > 0 ? ("，失败 " + failed + "（已留在队列）") : "")
                + (deleteFailed > 0 ? ("，删除失败 " + deleteFailed + "（已留墓碑）") : "")
                + (conflicts > 0 ? ("，冲突收敛 " + conflicts) : "");
    }

    /** 坐向的线上表示（与服务端 `des` 同格式，逆向/审核验 §27 已验可完整往返）。 */
    static String desOf(double sittingDeg, double facingDeg) {
        return "坐" + Math.round(sittingDeg) + "° 向" + Math.round(facingDeg) + "°";
    }

    /** 解析「坐X° 向Y°」；不匹配返回 null（调用方回落默认值并记日志，不静默当成功）。 */
    static double[] parseSitFacing(String des) {
        if (des == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("坐\\s*(-?\\d+(?:\\.\\d+)?)\\s*°\\s*向\\s*(-?\\d+(?:\\.\\d+)?)\\s*°")
                .matcher(des);
        if (!m.find()) return null;
        try {
            double sit = Double.parseDouble(m.group(1));
            double face = Double.parseDouble(m.group(2));
            return new double[]{((sit % 360) + 360) % 360, ((face % 360) + 360) % 360};
        } catch (Throwable t) {
            return null;
        }
    }

    private static String iso(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    private static long parseIso(String s) {
        if (s == null || s.isEmpty()) return System.currentTimeMillis();
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s).getTime();
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }
}

