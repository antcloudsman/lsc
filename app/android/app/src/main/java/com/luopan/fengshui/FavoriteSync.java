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
        final int uid = LcSession.uid(context);
        if (uid <= 0) return "未登录（缺账号信息），未同步";
        GapDb db = new GapDb(context);
        // 账号隔离：把尚未归属的存量行认领给当前账号；已有归属的行不动。
        int adopted = db.adoptUnownedRows(uid);
        if (adopted > 0) android.util.Log.d("LCSYNC", "adopted unowned rows=" + adopted + " uid=" + uid);

        // ---- 先推删除墓碑（方向一：本机删 → 服务端也删）----
        int deleted = 0, deleteFailed = 0;
        for (InterestPoint p : db.listDeletePending(uid)) {
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
        for (InterestPoint p : db.listPendingInterest(uid)) {
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
                        sf == null ? 0 : sf[0], sf == null ? 180 : sf[1], uid);
                maxRev = Math.max(maxRev, rev);
                pulled++;
            }
        }
        // 方向二：远端已删 → 本机跟随删。
        // 注意：增量拉取只返回 rev>cursor 的行，拿它当「全量存在集」会把老点全误删，
        // 所以单独拉一次全量 id（since=0）做对账。
        // 删数据不可逆 → 命中 limit（可能被截断）时**整段跳过对账、一条都不删**，只记日志；
        // 宁可延迟同步（旧数据晚消失），也不能把「没返回」误当成「已删除」。
        // 服务端返回 tombstone（deleted=1 也回）后可去掉这个上限，登记为 M5 要求。
        int purgedRemote = 0;
        boolean reconcileSkipped = false;
        try {
            JSONObject all = LcApi.listPoints(token, 0, 200);
            JSONArray allItems = all == null ? null : all.optJSONArray("items");
            if (allItems != null) {
                if (allItems.length() >= 200) {
                    reconcileSkipped = true;
                    android.util.Log.w("LCSYNC", "reconcile skipped (limit): full list returned "
                            + allItems.length() + ", refusing to purge");
                } else {
                    java.util.Set<Long> live = new java.util.HashSet<>();
                    for (int i = 0; i < allItems.length(); i++) {
                        JSONObject o = allItems.optJSONObject(i);
                        if (o != null && o.optLong("id", 0) > 0) live.add(o.optLong("id", 0));
                    }
                    // 幽灵行消解（审核验 §33.2 + 服务端提案）：`owner_uid=0 && remote_id>0` 的升级存量行，
                    // 归属不由本地猜，由**服务端回答**——该 remote_id 出现在本账号的 list 里，
                    // 就是「这个 id 是我的」，据此认领；不在就保持 0、不推不对账。
                    // 复用这次请求，零额外流量。**限制**：这是第一页 `limit=200`；`count>=200` 时整轮跳过（不认领、不删），
                    // 因为「没取全」与「不属于我」客户端区分不了。翻页 + tombstone 属 M5（见 GapDb.adoptByRemoteIds 注释）。
                    int adoptedByRemote = db.adoptByRemoteIds(uid, live);
                    if (adoptedByRemote > 0) {
                        android.util.Log.d("LCSYNC", "adopted by remote ids=" + adoptedByRemote + " uid=" + uid);
                    }
                    for (InterestPoint local : db.listInterest(uid)) {
                        long remote = db.remoteIdOf(local.id);
                        if (remote <= 0 || live.contains(remote)) continue;
                        // 账号隔离：只有「属于当前账号」的行才允许进远端已删判定。
                        // 上一账号的行（owner_uid 不等）绝不能用本账号的 list 去判「已删」——那是误删本地真实数据。
                        int owner = db.ownerUidOf(local.id);
                        if (owner != uid) continue;
                        db.purgeInterest(local.id);
                        purgedRemote++;
                    }
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
                + (reconcileSkipped ? "，远端删除对账已跳过（超 200 条）" : "")
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





