package com.luopan.fengshui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 天地图地名搜索（免费、需 key）：F-06 山脉 / F-07 水系 的点位来源。
 *
 * 实测（2026-11-05）：
 *  - 分类搜索 `queryType=13&dataTypes=260203`（山）山东 117 条；
 *    `260204`（河流）北京 933 条；`260205`（湖泊）全国 54229 条；
 *  - 逆地理 `geocoder` 可取 `addressComponent.province_code`（9 位国标码），
 *    用于把「当前视野」收敛到一个省的分类检索；
 *  - 返回的是 POI 点（名称/地址/经纬度），**不是山脉面或水系线/面**，
 *    线/面要素天地图公开服务没有（见 docs/features/map-free-sources.md）。
 */
final class MapPoints {

    static final class Point {
        final String name;
        final String address;
        final double lat;
        final double lng;

        Point(String name, String address, double lat, double lng) {
            this.name = name;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
        }
    }

    /** 逆地理取省码；失败返回 null（调用方回退到视野内搜索）。 */
    static String provinceCode(double lat, double lng) {
        try {
            String post = "{\"lon\":" + lng + ",\"lat\":" + lat + ",\"ver\":1}";
            String url = "https://api.tianditu.gov.cn/geocoder?postStr="
                    + URLEncoder.encode(post, "UTF-8") + "&type=geocode&tk=" + MapKeys.TDT;
            JSONObject root = new JSONObject(LcHttp.get(url));
            JSONObject comp = root.optJSONObject("result") == null ? null
                    : root.getJSONObject("result").optJSONObject("addressComponent");
            if (comp == null) return null;
            String code = comp.optString("province_code", "");
            return code.isEmpty() ? null : code;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 分类搜索（queryType=13）。多分类用英文逗号。 */
    static List<Point> category(String dataTypes, String specify, int count) {
        try {
            String post = "{\"queryType\":13,\"start\":0,\"count\":" + Math.min(300, Math.max(1, count))
                    + ",\"specify\":\"" + specify + "\",\"dataTypes\":\"" + dataTypes + "\"}";
            return query(post);
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /** 视野内搜索（queryType=2）：省码缺失时的回退；keyWord 可用「山」「河」「湖」。 */
    static List<Point> keywordNear(String keyword, double lat, double lng, int level, int count) {
        try {
            double d = 0.6;
            String bound = (lng - d) + "," + (lat - d) + "," + (lng + d) + "," + (lat + d);
            String post = "{\"keyWord\":\"" + keyword + "\",\"level\":" + level
                    + ",\"mapBound\":\"" + bound + "\",\"queryType\":2,\"start\":0,\"count\":"
                    + Math.min(300, Math.max(1, count)) + "}";
            return query(post);
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private static List<Point> query(String post) throws Exception {
        String url = "https://api.tianditu.gov.cn/v2/search?postStr="
                + URLEncoder.encode(post, "UTF-8") + "&type=query&tk=" + MapKeys.TDT;
        JSONObject root = new JSONObject(LcHttp.get(url));
        List<Point> out = new ArrayList<>();
        JSONArray pois = root.optJSONArray("pois");
        if (pois == null) return out;
        for (int i = 0; i < pois.length(); i++) {
            JSONObject o = pois.optJSONObject(i);
            if (o == null) continue;
            String ll = o.optString("lonlat", "");
            int comma = ll.indexOf(',');
            if (comma <= 0) continue;
            try {
                double lng = Double.parseDouble(ll.substring(0, comma).trim());
                double lat = Double.parseDouble(ll.substring(comma + 1).trim());
                out.add(new Point(o.optString("name", ""), o.optString("address", ""), lat, lng));
            } catch (NumberFormatException ignored) {
                // 单条坏数据跳过
            }
        }
        return out;
    }

    /** 逆地理取地址（F-03 底部卡用）；失败返回空串。 */
    static String reverseAddress(double lat, double lng) {
        try {
            String post = "{\"lon\":" + lng + ",\"lat\":" + lat + ",\"ver\":1}";
            String url = "https://api.tianditu.gov.cn/geocoder?postStr="
                    + URLEncoder.encode(post, "UTF-8") + "&type=geocode&tk=" + MapKeys.TDT;
            JSONObject root = new JSONObject(LcHttp.get(url));
            JSONObject result = root.optJSONObject("result");
            return result == null ? "" : result.optString("formatted_address", "");
        } catch (Throwable t) {
            return "";
        }
    }

    /** 球面距离（km），用于 F-07 的「距当前位置」。 */
    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    private MapPoints() {
    }
}
