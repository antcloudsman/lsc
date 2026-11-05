package com.luopan.fengshui;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 轨迹元数据（meta.json）：列表只读它；stats 为四项统计缓存。 */
final class TrackMeta {

    String id;
    long startedAt;
    long stoppedAt;
    String status = "recording"; // recording | finished
    int lastSeq;
    long intervalMs = 1000;
    double minDistanceM = 3;
    int segments = 1;
    int points;
    double distanceM;
    double durationSec;
    double avgKmh;
    double maxKmh;
    final JSONArray samplingChanges = new JSONArray();

    static TrackMeta create(String id) {
        TrackMeta m = new TrackMeta();
        m.id = id;
        m.startedAt = System.currentTimeMillis();
        return m;
    }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("startedAt", startedAt);
            o.put("stoppedAt", stoppedAt);
            o.put("status", status);
            o.put("lastSeq", lastSeq);
            o.put("intervalMs", intervalMs);
            o.put("minDistanceM", minDistanceM);
            o.put("segments", segments);
            o.put("points", points);
            o.put("distanceM", distanceM);
            o.put("durationSec", durationSec);
            o.put("avgKmh", avgKmh);
            o.put("maxKmh", maxKmh);
            o.put("samplingChanges", samplingChanges);
        } catch (JSONException ignored) {
        }
        return o;
    }

    static TrackMeta fromJson(JSONObject o) {
        TrackMeta m = new TrackMeta();
        m.id = o.optString("id", "");
        m.startedAt = o.optLong("startedAt", 0L);
        m.stoppedAt = o.optLong("stoppedAt", 0L);
        m.status = o.optString("status", "finished");
        m.lastSeq = o.optInt("lastSeq", 0);
        m.intervalMs = o.optLong("intervalMs", 1000);
        m.minDistanceM = o.optDouble("minDistanceM", 3);
        m.segments = o.optInt("segments", 1);
        m.points = o.optInt("points", 0);
        m.distanceM = o.optDouble("distanceM", 0);
        m.durationSec = o.optDouble("durationSec", 0);
        m.avgKmh = o.optDouble("avgKmh", 0);
        m.maxKmh = o.optDouble("maxKmh", 0);
        return m;
    }
}
