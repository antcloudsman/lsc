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

    /**
     * R2-025：带降级态的地名/POI 检索结果。
     * 天地图实测（2026-11-06）：正常 = HTTP 200 + status.infocode=1000；连续紧连请求会出 HTTP 429；
     * 业务错误码（键值/权限/配额）以 3 开头（301xxx）。UI 必须能区分「无结果」与「不可用」，
     * 不许把失败画成空白。
     */
    static final class SearchResult {
        static final String OK = "ok";
        static final String EMPTY = "empty";
        static final String RATE_LIMITED = "rate-limited";   // HTTP 429
        static final String QUOTA = "quota";                 // 业务错误码 3xxxxx（配额/权限类）
        static final String KEY_INVALID = "key-invalid";     // 301013：key 非法/未授权（不是配额，措辞要分开）
        static final String UNAVAILABLE = "unavailable";     // 其它 HTTP/超时/解析失败

        final String code;
        final String detail;
        final List<Point> points;
        final int total;

        SearchResult(String code, String detail, List<Point> points, int total) {
            this.code = code;
            this.detail = detail == null ? "" : detail;
            this.points = points == null ? new ArrayList<>() : points;
            this.total = total;
        }

        boolean ok() {
            return OK.equals(code) || EMPTY.equals(code);
        }
    }

    /** R2-025 境内：中心点 ±0.6° 视野内关键词检索（queryType=2 + mapBound）。 */
    static SearchResult searchNear(String keyword, double lat, double lng, int level, int count) {
        double d = 0.6;
        String bound = (lng - d) + "," + (lat - d) + "," + (lng + d) + "," + (lat + d);
        String post = "{\"keyWord\":\"" + esc(keyword) + "\",\"level\":" + level
                + ",\"mapBound\":\"" + bound + "\",\"queryType\":2,\"start\":0,\"count\":"
                + Math.min(300, Math.max(1, count)) + "}";
        return execute(post);
    }

    /**
     * R2-025 境外：不带国内的窄视野，用全球范围边界（-180,-85,180,85）+ queryType=2。
     * 实测（2026-11-06）：东京塔 → total=1、139.745709,35.657869；故宫 → total=1283（境内同词 443）。
     * 注：不带 mapBound 会 HTTP 400（天地图 queryType=2 必填 mapBound），所以这里用全球边界等价实现。
     */
    static SearchResult searchGlobal(String keyword, int count) {
        String post = "{\"keyWord\":\"" + esc(keyword) + "\",\"level\":12"
                + ",\"mapBound\":\"-180,-85,180,85\",\"queryType\":2,\"start\":0,\"count\":"
                + Math.min(300, Math.max(1, count)) + "}";
        return execute(post);
    }

    /**
     * R2-025 行政区域：queryType=12 + specify=省码（9 位，形如 156110000）。
     * 实测：故宫 → total=446；「朝阳」在北京(156110000)=196417/首条朝阳区，在上海(156310000)=168/首条朝阳，
     * 即 specify 确实把结果收窄到该省。
     */
    static SearchResult searchInProvince(String keyword, String provinceCode9, int count) {
        String code = provinceCode9 == null ? "" : provinceCode9.trim();
        if (code.isEmpty()) return new SearchResult(SearchResult.UNAVAILABLE, "province-code empty", null, 0);
        String post = "{\"keyWord\":\"" + esc(keyword) + "\",\"level\":12,\"queryType\":12,\"start\":0,\"count\":"
                + Math.min(300, Math.max(1, count)) + ",\"specify\":\"" + esc(code) + "\"}";
        return execute(post);
    }

    /**
     * 行政区域用：把关键词解析成省码（9 位）。先按当前视野搜一次取首条坐标，再逆地理取 province_code；
     * 都失败就退回用当前中心点算省码。解析不出返回 null（调用方按降级态处理，不编造）。
     */
    static String resolveProvince9(String keyword, double lat, double lng) {
        SearchResult near = searchNear(keyword, lat, lng, 12, 5);
        if (near.ok() && !near.points.isEmpty()) {
            Point p = near.points.get(0);
            String code = provinceCode(p.lat, p.lng);
            if (code != null && !code.isEmpty()) return code;
        }
        return provinceCode(lat, lng);
    }

    /** R2-025 通用检索：queryType 由调用方给（1 普通 / 2 视野 / 3 周边 …），见天地图 v2/search。 */
    static SearchResult searchByQuery(String post) {
        return execute(post);
    }

    static SearchResult execute(String post) {
        try {
            String url = "https://api.tianditu.gov.cn/v2/search?postStr="
                    + URLEncoder.encode(post, "UTF-8") + "&type=query&tk=" + MapKeys.TDT;
            JSONObject root = new JSONObject(LcHttp.get(url));
            JSONObject st = root.optJSONObject("status");
            String info = st == null ? "" : st.optString("infocode", "");
            String desc = st == null ? "" : st.optString("cndesc", "");
            if (!info.isEmpty() && !"1000".equals(info)) {
                String code;
                if ("301013".equals(info)) {
                    code = SearchResult.KEY_INVALID;      // key 非法/未授权，别当成配额不足
                } else if (info.startsWith("3")) {
                    code = SearchResult.QUOTA;
                } else {
                    code = SearchResult.UNAVAILABLE;
                }
                return new SearchResult(code, "infocode=" + info + " " + desc, new ArrayList<>(), 0);
            }
            List<Point> pts = parsePois(root);
            int total = root.optInt("count", pts.size());
            return new SearchResult(pts.isEmpty() ? SearchResult.EMPTY : SearchResult.OK,
                    "infocode=" + (info.isEmpty() ? "1000" : info), pts, total);
        } catch (Throwable t) {
            String m = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            String code = m.contains("HTTP 429") ? SearchResult.RATE_LIMITED : SearchResult.UNAVAILABLE;
            return new SearchResult(code, m, new ArrayList<>(), 0);
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<Point> parsePois(JSONObject root) {
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
