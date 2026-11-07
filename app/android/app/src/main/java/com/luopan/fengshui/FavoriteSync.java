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

        int pushed = 0, failed = 0, conflicts = 0;
        for (InterestPoint p : db.listPendingInterest()) {
            try {
                JSONObject body = new JSONObject();
                long remote = db.remoteIdOf(p.id);
                if (remote > 0) {
                    body.put("id", remote);
                    int rev = db.revOf(p.id);
                    if (rev > 0) body.put("rev", rev);
                }
                body.put("name", p.name == null ? "" : p.name);
                body.put("address", "");
                body.put("latitude", p.lat);
                body.put("longitude", p.lng);
                body.put("des", "坐" + Math.round(p.sittingDeg) + "° 向" + Math.round(p.facingDeg) + "°");
                body.put("images", "[]");
                body.put("icon", "default");
                body.put("style", "0");
                body.put("overlay", 1);
                body.put("dbId", 0);
                body.put("createTime", iso(p.createdAt));
                JSONObject data = LcApi.savePoint(token, body);
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
        int pulled = 0, maxRev = cursor;
        JSONObject data = LcApi.listPoints(token, cursor, 200);
        JSONArray items = data == null ? null : data.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.optJSONObject(i);
                if (o == null) continue;
                long id = o.optLong("id", 0);
                if (id <= 0) continue;
                int rev = o.optInt("rev", 0);
                db.upsertRemoteInterest(id, o.optString("name", ""),
                        o.optDouble("latitude", 0), o.optDouble("longitude", 0),
                        rev, parseIso(o.optString("createTime", null)));
                maxRev = Math.max(maxRev, rev);
                pulled++;
            }
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(K_CURSOR, maxRev).apply();
        android.util.Log.d("LCSYNC", "push=" + pushed + " failed=" + failed + " conflict=" + conflicts
                + " pull=" + pulled + " cursor=" + maxRev);
        return "同步完成：上传 " + pushed + "，拉取 " + pulled
                + (failed > 0 ? ("，失败 " + failed + "（已留在队列）") : "")
                + (conflicts > 0 ? ("，冲突收敛 " + conflicts) : "");
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
