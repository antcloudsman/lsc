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

    /** R2-027/R2-032：web→native 的 UI 事件（无坐标），字段契约由总监 2026-11-07 定死。 */
    public interface UiListener {
        /**
         * @param type {@code attribution} 或 {@code src-panel}
         * @param o    原始对象：{@code {"type":"attribution","text":"..."}} /
         *             {@code {"type":"src-panel","open":true|false}}
         */
        void onUiEvent(String type, JSONObject o);
    }

    private final Listener listener;
    private UiListener uiListener;

    public LCAndroid(Listener listener) {
        this.listener = listener;
    }

    public void setUiListener(UiListener l) {
        this.uiListener = l;
    }

    @JavascriptInterface
    public void onEvent(String json) {
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            String type = o.optString("type", "");
            if ("attribution".equals(type) || "src-panel".equals(type)
                    || "groundstate".equals(type)) {
                // 无坐标事件：不走 pick 的范围校验，直接透传给 MainActivity。
                // M8：groundstate 带 {ok, diag, reason, renderer, ground}，供原生决定贴地显隐。
                Log.d("LCUI", type + " " + json);
                if (uiListener != null) uiListener.onUiEvent(type, o);
                return;
            }
            if ("pick-clear".equals(type)) {
                Log.d("LCPICK", "pick-clear");
                listener.onMapEvent("pick-clear", null);
                return;
            }
            if (!"pick".equals(type) && !"marker".equals(type) && !"elev".equals(type)) {
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
