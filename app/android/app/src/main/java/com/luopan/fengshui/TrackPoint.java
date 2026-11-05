package com.luopan.fengshui;

import org.json.JSONException;
import org.json.JSONObject;

/** 轨迹点（JSONL 一行）。t=毫秒时间戳；acc=水平精度米；spd=设备速度 m/s（无则 -1）；seg=分段号。 */
final class TrackPoint {

    final long t;
    final double lat;
    final double lng;
    final float acc;
    final float spd;
    final int seg;
    final String src;

    TrackPoint(long t, double lat, double lng, float acc, float spd, int seg, String src) {
        this.t = t;
        this.lat = lat;
        this.lng = lng;
        this.acc = acc;
        this.spd = spd;
        this.seg = seg;
        this.src = src == null ? "" : src;
    }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("t", t);
            o.put("lat", lat);
            o.put("lng", lng);
            o.put("acc", (double) acc);
            o.put("spd", (double) spd);
            o.put("seg", seg);
            o.put("src", src);
        } catch (JSONException ignored) {
        }
        return o;
    }

    static TrackPoint fromJson(JSONObject o) {
        return new TrackPoint(
                o.optLong("t", 0L),
                o.optDouble("lat", 0),
                o.optDouble("lng", 0),
                (float) o.optDouble("acc", 0),
                (float) o.optDouble("spd", -1),
                o.optInt("seg", 0),
                o.optString("src", ""));
    }

    static TrackPoint fromLine(String line) {
        try {
            return fromJson(new JSONObject(line));
        } catch (JSONException e) {
            return null;
        }
    }
}
