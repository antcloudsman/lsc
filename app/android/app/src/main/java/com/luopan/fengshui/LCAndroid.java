package com.luopan.fengshui;

import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/**
 * D-005 bridge: {@code window.LCAndroid.onEvent(String json)}.
 *
 *   {"type":"pick","lng":..,"lat":..,"elev":..,"heading":..,"src":"3d"}
 *   {"type":"pick-clear"}
 *
 * Validation keeps the contract honest: ranges checked, {@code elev} may be absent or
 * null but never a 0 sentinel, unknown types ignored (logged).
 */
public class LCAndroid {

    public interface Listener {
        void onMapEvent(String type, MapMarker marker);
    }

    private final Listener listener;

    public LCAndroid(Listener listener) {
        this.listener = listener;
    }

    @JavascriptInterface
    public void onEvent(String json) {
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            String type = o.optString("type", "");
            if ("pick-clear".equals(type)) {
                Log.d("LCPICK", "pick-clear");
                listener.onMapEvent("pick-clear", null);
                return;
            }
            if (!"pick".equals(type)) {
                Log.w("LCPICK", "ignore type=" + type);
                return;
            }
            double lng = o.optDouble("lng", Double.NaN);
            double lat = o.optDouble("lat", Double.NaN);
            if (Double.isNaN(lng) || Double.isNaN(lat)
                    || lng < -180.0 || lng > 180.0 || lat < -90.0 || lat > 90.0) {
                Log.w("LCPICK", "reject out-of-range " + json);
                return;
            }
            Double elev = null;
            if (o.has("elev") && !o.isNull("elev")) {
                double e = o.optDouble("elev", Double.NaN);
                if (!Double.isNaN(e)) elev = e;
            }
            float heading = (float) o.optDouble("heading", 0.0);
            heading = ((heading % 360f) + 360f) % 360f;
            String src = o.optString("src", "");
            MapMarker marker = new MapMarker(lng, lat, elev, heading, src);
            Log.d("LCPICK", "pick " + marker);
            listener.onMapEvent("pick", marker);
        } catch (Throwable t) {
            Log.w("LCPICK", "bad json: " + json, t);
        }
    }
}
